package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;

public class StaticExchangeEvaluator {

    // Piece values matching Evaluator.java but accessible
    public static final int PAWN_VALUE = 100;
    public static final int KNIGHT_VALUE = 320;
    public static final int BISHOP_VALUE = 330;
    public static final int ROOK_VALUE = 500;
    public static final int QUEEN_VALUE = 900;
    public static final int KING_VALUE = 20000; // High value for king

    private static final long[] KNIGHT_ATTACKS = new long[64];
    private static final long[] KING_ATTACKS = new long[64];
    private static final long[][] PAWN_ATTACKS = new long[2][64]; // [color][square]

    static {
        initAttacks();
    }

    private static void initAttacks() {
        for (int i = 0; i < 64; i++) {
            long pos = 1L << i;

            // Knight
            long knight = 0L;
            if (((pos << 17) & 0xFEFEFEFEFEFEFEFEL) != 0) knight |= (pos << 17); // NO_EA
            if (((pos << 10) & 0xFCFCFCFCFCFCFCFCL) != 0) knight |= (pos << 10); // NO_WE
            if (((pos << 15) & 0x7F7F7F7F7F7F7F7FL) != 0) knight |= (pos << 15); // NO_WE (wait, check offsets)
            // Correct offsets:
            // NNE: +17 (not H)
            // ENE: +10 (not GH)
            // ESE: -6 (not GH)
            // SSE: -15 (not H)
            // SSW: -17 (not A)
            // WSW: -10 (not AB)
            // WNW: +6 (not AB)
            // NNW: +15 (not A)
            // My shifts above were a bit random. Let's do it properly using coordinates.
            int r = i / 8;
            int c = i % 8;
            int[] dr = {2, 1, -1, -2, -2, -1, 1, 2};
            int[] dc = {1, 2, 2, 1, -1, -2, -2, -1};
            for (int k = 0; k < 8; k++) {
                int nr = r + dr[k];
                int nc = c + dc[k];
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                    KNIGHT_ATTACKS[i] |= (1L << (nr * 8 + nc));
                }
            }

            // King
            int[] kr = {1, 1, 1, 0, 0, -1, -1, -1};
            int[] kc = {1, 0, -1, 1, -1, 1, 0, -1};
            for (int k = 0; k < 8; k++) {
                int nr = r + kr[k];
                int nc = c + kc[k];
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                    KING_ATTACKS[i] |= (1L << (nr * 8 + nc));
                }
            }

            // Pawn (captures)
            // White: +7, +9
            if (r < 7) { // Can capture upward
                if (c > 0) PAWN_ATTACKS[0][i] |= (1L << ((r + 1) * 8 + (c - 1))); // Capture left (NorthWest)
                if (c < 7) PAWN_ATTACKS[0][i] |= (1L << ((r + 1) * 8 + (c + 1))); // Capture right (NorthEast)
            }
            // Black: -7, -9
            if (r > 0) {
                if (c > 0) PAWN_ATTACKS[1][i] |= (1L << ((r - 1) * 8 + (c - 1))); // SouthWest
                if (c < 7) PAWN_ATTACKS[1][i] |= (1L << ((r - 1) * 8 + (c + 1))); // SouthEast
            }
        }
    }

    /**
     * Static Exchange Evaluation (SEE).
     *
     * @param board The board position.
     * @param move  The move to evaluate (capture).
     * @return Positive value if the capture is good (winning material), negative if bad, 0 if equal.
     */
    public static int see(BoardInterface board, Move move) {
        // Create bitboard representation
        // Note: Creating BoardStatus is O(64) loops. Might be optimization target.
        BoardStatus bs = new BoardStatus(board);

        Square toSq = move.getTo();
        Square fromSq = move.getFrom();
        int to = toSq.ordinal();
        int from = fromSq.ordinal();

        // Get value of captured piece
        Piece captured = board.getPiece(toSq);
        int valCaptured = getValue(captured);

        // Value of attacking piece
        Piece attacker = board.getPiece(fromSq);
        int valAttacker = getValue(attacker);

        // Initial gain
        int[] gain = new int[32];
        int d = 0;
        gain[d] = valCaptured;

        valCaptured = valAttacker; // The attacker becomes the victim for the next capture

        long fromSet = 1L << from;
        long occupied = getAllOccupied(bs);
        long mayXray = occupied; // Initially all occupied pieces can potentialy xray (except the mover handled below)

        // Make the first move
        occupied ^= fromSet; // Remove attacker from origin
        occupied |= (1L << to); // Place on target (it was already occupied by captured, but now by attacker)
        // Actually, for SEE we just care about occupancy changes. 
        // The target square 'to' is always occupied during the exchange.

        // Next attacker side
        Side side = board.getSideToMove().flip();

        // Attacker piece type (current attacker on the square)
        PieceType attackerType = attacker.getPieceType();

        while (true) {
            d++;
            gain[d] = valCaptured - gain[d - 1]; // Score relative to current side

            // The piece we just captured (in the recursive sense) is the one that moved there in previous step.
            // Wait, standard SEE formula:
            // gain[d] = value_of_piece_on_square - gain[d-1]
            // But 'value_of_piece_on_square' is the piece that *was* attacking and moved to the square.
            // So we use valAttacker from previous iteration.

            // Let's refine the loop structure.
            // gain[0] = val_victim
            // gain[1] = val_attacker1 - gain[0] (Score for side 1) -> This is wrong.
            // Correct:
            // gain[0] = capture_value
            // gain[1] = val_attacker - gain[0] ? No.

            // Standard:
            // gain[0] = val_victim
            // move: attacker captures victim.
            // value on square is now val_attacker.
            // next capture: new_attacker captures attacker.
            // gain[1] = val_attacker - gain[0] ? No.

            // It's:
            // score = val_captured
            // perform capture
            // score = val_captured - see(swap)

            // Array version:
            // gain[0] = val_captured
            // gain[1] = val_attacker - gain[0]  <-- If I stand pat, I got gain[0]. If you capture back, I lose gain[1]?
            // Not quite.

            if (Math.max(-gain[d - 1], gain[d]) < 0) break; // Pruning? No, full SEE usually needed or simple pruning.

            // Find least valuable attacker
            // We need to pass the *type* of the piece that is currently on the square to be captured next? 
            // No, the piece on the square is `attackerType` (from previous iteration).
            // Its value is `valAttacker`.

            // So we need to update `valCaptured` for the next iteration to be `valAttacker`.
            valCaptured = valAttacker;

            long lvaBit = getLeastValuableAttacker(bs, to, side, occupied);

            if (lvaBit == 0) break;

            // Identify the attacker piece
            int attSq = Long.numberOfTrailingZeros(lvaBit);
            attackerType = getPieceTypeAt(bs, attSq, side);
            valAttacker = getPieceValue(attackerType);

            // Remove attacker from occupied
            occupied ^= lvaBit;

            // Add X-ray attacks (sliders behind the mover)
            if ((lvaBit & (bs.getWhiteRooks() | bs.getWhiteQueens() | bs.getWhiteBishops() |
                    bs.getBlackRooks() | bs.getBlackQueens() | bs.getBlackBishops())) != 0) {
                // It was a slider? No, if the *moving* piece was anything, it might reveal a slider behind it.
                // We need to update attacks.
                // But `getLeastValuableAttacker` calculates attacks on the fly based on `occupied`.
                // So removing it from `occupied` is enough for the next call to `getLeastValuableAttacker` to see through it.
                // Yes, provided `getLeastValuableAttacker` uses the *current* `occupied` mask for sliding attacks.
            }

            side = side.flip();
        }

        // Propagate
        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }

        return gain[0];
    }

    private static long getLeastValuableAttacker(BoardStatus bs, int sq, Side side, long occupied) {
        // Order: Pawn, Knight, Bishop, Rook, Queen, King
        long attackers = 0;

        // Pawns
        long pawns = (side == Side.WHITE) ? bs.getWhitePawns() : bs.getBlackPawns();
        // Pawns attack capture: inverse of pawn move
        // White pawns attack sq from (sq-7, sq-9) if sq is on rank > 2?
        // We can use the precomputed PAWN_ATTACKS but we need inverse.
        // Actually, PAWN_ATTACKS[white][sq] gives squares attacked by white pawn at sq.
        // We want squares that contain a pawn that attacks sq.
        // If white pawn at X attacks sq, then black pawn at sq would attack X.
        // So attackers of sq (by side) = PAWN_ATTACKS[flip(side)][sq] & pawns.
        int sideIdx = (side == Side.WHITE) ? 0 : 1;
        long pawnAttacks = PAWN_ATTACKS[1 - sideIdx][sq];
        long matchingPawns = pawnAttacks & pawns & occupied;
        if (matchingPawns != 0) {
            return Long.lowestOneBit(matchingPawns);
        }

        // Knights
        long knights = (side == Side.WHITE) ? bs.getWhiteKnights() : bs.getBlackKnights();
        long knightAttacks = KNIGHT_ATTACKS[sq] & knights & occupied;
        if (knightAttacks != 0) {
            return Long.lowestOneBit(knightAttacks);
        }

        // Bishops
        long bishops = (side == Side.WHITE) ? bs.getWhiteBishops() : bs.getBlackBishops();
        long queens = (side == Side.WHITE) ? bs.getWhiteQueens() : bs.getBlackQueens();
        long bishopQueens = bishops | queens;
        if (bishopQueens != 0) {
            long attacks = getBishopAttacks(sq, occupied) & bishopQueens & occupied;
            if (attacks != 0) return Long.lowestOneBit(attacks);
        }

        // Rooks
        long rooks = (side == Side.WHITE) ? bs.getWhiteRooks() : bs.getBlackRooks();
        long rookQueens = rooks | queens;
        if (rookQueens != 0) {
            long attacks = getRookAttacks(sq, occupied) & rookQueens & occupied;
            if (attacks != 0) return Long.lowestOneBit(attacks);
        }

        // King
        long king = (side == Side.WHITE) ? bs.getWhiteKing() : bs.getBlackKing();
        long kingAttacks = KING_ATTACKS[sq] & king & occupied;
        if (kingAttacks != 0) {
            return Long.lowestOneBit(kingAttacks);
        }

        return 0;
    }

    // Simple Ray-casting for Sliders
    private static long getBishopAttacks(int sq, long occupied) {
        long attacks = 0;
        int r = sq / 8;
        int c = sq % 8;

        // NE
        for (int i = 1; r + i < 8 && c + i < 8; i++) {
            long bit = 1L << ((r + i) * 8 + (c + i));
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        // NW
        for (int i = 1; r + i < 8 && c - i >= 0; i++) {
            long bit = 1L << ((r + i) * 8 + (c - i));
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        // SE
        for (int i = 1; r - i >= 0 && c + i < 8; i++) {
            long bit = 1L << ((r - i) * 8 + (c + i));
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        // SW
        for (int i = 1; r - i >= 0 && c - i >= 0; i++) {
            long bit = 1L << ((r - i) * 8 + (c - i));
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        return attacks;
    }

    private static long getRookAttacks(int sq, long occupied) {
        long attacks = 0;
        int r = sq / 8;
        int c = sq % 8;

        // North
        for (int i = 1; r + i < 8; i++) {
            long bit = 1L << ((r + i) * 8 + c);
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        // South
        for (int i = 1; r - i >= 0; i++) {
            long bit = 1L << ((r - i) * 8 + c);
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        // East
        for (int i = 1; c + i < 8; i++) {
            long bit = 1L << (r * 8 + (c + i));
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        // West
        for (int i = 1; c - i >= 0; i++) {
            long bit = 1L << (r * 8 + (c - i));
            attacks |= bit;
            if ((occupied & bit) != 0) break;
        }
        return attacks;
    }

    private static int getValue(Piece piece) {
        if (piece == Piece.NONE) return 0;
        switch (piece.getPieceType()) {
            case PAWN:
                return PAWN_VALUE;
            case KNIGHT:
                return KNIGHT_VALUE;
            case BISHOP:
                return BISHOP_VALUE;
            case ROOK:
                return ROOK_VALUE;
            case QUEEN:
                return QUEEN_VALUE;
            case KING:
                return KING_VALUE;
            default:
                return 0;
        }
    }

    private static int getPieceValue(PieceType type) {
        switch (type) {
            case PAWN:
                return PAWN_VALUE;
            case KNIGHT:
                return KNIGHT_VALUE;
            case BISHOP:
                return BISHOP_VALUE;
            case ROOK:
                return ROOK_VALUE;
            case QUEEN:
                return QUEEN_VALUE;
            case KING:
                return KING_VALUE;
            default:
                return 0;
        }
    }

    private static long getAllOccupied(BoardStatus bs) {
        return bs.getWhitePawns() | bs.getWhiteKnights() | bs.getWhiteBishops() |
                bs.getWhiteRooks() | bs.getWhiteQueens() | bs.getWhiteKing() |
                bs.getBlackPawns() | bs.getBlackKnights() | bs.getBlackBishops() |
                bs.getBlackRooks() | bs.getBlackQueens() | bs.getBlackKing();
    }

    private static PieceType getPieceTypeAt(BoardStatus bs, int sq, Side side) {
        long bit = 1L << sq;
        if (side == Side.WHITE) {
            if ((bs.getWhitePawns() & bit) != 0) return PieceType.PAWN;
            if ((bs.getWhiteKnights() & bit) != 0) return PieceType.KNIGHT;
            if ((bs.getWhiteBishops() & bit) != 0) return PieceType.BISHOP;
            if ((bs.getWhiteRooks() & bit) != 0) return PieceType.ROOK;
            if ((bs.getWhiteQueens() & bit) != 0) return PieceType.QUEEN;
            if ((bs.getWhiteKing() & bit) != 0) return PieceType.KING;
        } else {
            if ((bs.getBlackPawns() & bit) != 0) return PieceType.PAWN;
            if ((bs.getBlackKnights() & bit) != 0) return PieceType.KNIGHT;
            if ((bs.getBlackBishops() & bit) != 0) return PieceType.BISHOP;
            if ((bs.getBlackRooks() & bit) != 0) return PieceType.ROOK;
            if ((bs.getBlackQueens() & bit) != 0) return PieceType.QUEEN;
            if ((bs.getBlackKing() & bit) != 0) return PieceType.KING;
        }
        return PieceType.NONE;
    }
}
