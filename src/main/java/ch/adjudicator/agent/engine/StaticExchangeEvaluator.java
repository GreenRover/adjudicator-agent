package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.*;

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
    private static final Square[] SQUARES = Square.values();

    static {
        initAttacks();
    }

    private static void initAttacks() {
        for (int i = 0; i < 64; i++) {
            long pos = 1L << i;

            // Knight
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
    public static int see(Bitboard board, int move) {
        // No BoardStatus creation!

        int to = Bitboard.getTo(move);
        int from = Bitboard.getFrom(move);

        // Get value of captured piece
        Piece captured = board.getPieceAt(SQUARES[to]);
        int valCaptured = getValue(captured);

        // Value of attacking piece
        Piece attacker = board.getPieceAt(SQUARES[from]);
        int valAttacker = getValue(attacker);

        // Initial gain
        int[] gain = new int[32];
        int d = 0;
        gain[d] = valCaptured;

        valCaptured = valAttacker; // The attacker becomes the victim for the next capture

        long fromSet = 1L << from;
        long occupied = board.occupiedSquares;
        
        // Make the first move
        occupied ^= fromSet; // Remove attacker from origin
        occupied |= (1L << to); // Place on target
        
        // Next attacker side
        Side side = board.getSideToMove().flip();

        // Attacker piece type (current attacker on the square)
        PieceType attackerType;

        while (true) {
            d++;
            gain[d] = valCaptured - gain[d - 1]; // Score relative to current side

            if (Math.max(-gain[d - 1], gain[d]) < 0) break; 

            valCaptured = valAttacker;

            long lvaBit = getLeastValuableAttacker(board, to, side, occupied);

            if (lvaBit == 0) break;

            // Identify the attacker piece
            int attSq = Long.numberOfTrailingZeros(lvaBit);
            attackerType = getPieceTypeAt(board, attSq, side);
            valAttacker = getPieceValue(attackerType);

            // Remove attacker from occupied
            occupied ^= lvaBit;

            // Add X-ray attacks (sliders behind the mover)
            if ((lvaBit & (board.whiteRooks | board.whiteQueens | board.whiteBishops |
                    board.blackRooks | board.blackQueens | board.blackBishops)) != 0) {
                // Handled implicitly by next getLeastValuableAttacker call
            }

            side = side.flip();
        }

        // Propagate
        while (--d > 0) {
            gain[d - 1] = -Math.max(-gain[d - 1], gain[d]);
        }

        return gain[0];
    }

    private static long getLeastValuableAttacker(Bitboard bs, int sq, Side side, long occupied) {
        // Order: Pawn, Knight, Bishop, Rook, Queen, King
        
        // Pawns
        long pawns = (side == Side.WHITE) ? bs.whitePawns : bs.blackPawns;
        int sideIdx = (side == Side.WHITE) ? 0 : 1;
        long pawnAttacks = PAWN_ATTACKS[1 - sideIdx][sq];
        long matchingPawns = pawnAttacks & pawns & occupied;
        if (matchingPawns != 0) {
            return Long.lowestOneBit(matchingPawns);
        }

        // Knights
        long knights = (side == Side.WHITE) ? bs.whiteKnights : bs.blackKnights;
        long knightAttacks = KNIGHT_ATTACKS[sq] & knights & occupied;
        if (knightAttacks != 0) {
            return Long.lowestOneBit(knightAttacks);
        }

        // Bishops
        long bishops = (side == Side.WHITE) ? bs.whiteBishops : bs.blackBishops;
        long queens = (side == Side.WHITE) ? bs.whiteQueens : bs.blackQueens;
        long bishopQueens = bishops | queens;
        if (bishopQueens != 0) {
            long attacks = getBishopAttacks(sq, occupied) & bishopQueens & occupied;
            if (attacks != 0) return Long.lowestOneBit(attacks);
        }

        // Rooks
        long rooks = (side == Side.WHITE) ? bs.whiteRooks : bs.blackRooks;
        long rookQueens = rooks | queens;
        if (rookQueens != 0) {
            long attacks = getRookAttacks(sq, occupied) & rookQueens & occupied;
            if (attacks != 0) return Long.lowestOneBit(attacks);
        }

        // King
        long king = (side == Side.WHITE) ? bs.whiteKing : bs.blackKing;
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
        return switch (piece.getPieceType()) {
            case PAWN -> PAWN_VALUE;
            case KNIGHT -> KNIGHT_VALUE;
            case BISHOP -> BISHOP_VALUE;
            case ROOK -> ROOK_VALUE;
            case QUEEN -> QUEEN_VALUE;
            case KING -> KING_VALUE;
            default -> 0;
        };
    }

    private static int getPieceValue(PieceType type) {
        return switch (type) {
            case PAWN -> PAWN_VALUE;
            case KNIGHT -> KNIGHT_VALUE;
            case BISHOP -> BISHOP_VALUE;
            case ROOK -> ROOK_VALUE;
            case QUEEN -> QUEEN_VALUE;
            case KING -> KING_VALUE;
            default -> 0;
        };
    }

    private static PieceType getPieceTypeAt(Bitboard bs, int sq, Side side) {
        long bit = 1L << sq;
        if (side == Side.WHITE) {
            if ((bs.whitePawns & bit) != 0) return PieceType.PAWN;
            if ((bs.whiteKnights & bit) != 0) return PieceType.KNIGHT;
            if ((bs.whiteBishops & bit) != 0) return PieceType.BISHOP;
            if ((bs.whiteRooks & bit) != 0) return PieceType.ROOK;
            if ((bs.whiteQueens & bit) != 0) return PieceType.QUEEN;
            if ((bs.whiteKing & bit) != 0) return PieceType.KING;
        } else {
            if ((bs.blackPawns & bit) != 0) return PieceType.PAWN;
            if ((bs.blackKnights & bit) != 0) return PieceType.KNIGHT;
            if ((bs.blackBishops & bit) != 0) return PieceType.BISHOP;
            if ((bs.blackRooks & bit) != 0) return PieceType.ROOK;
            if ((bs.blackQueens & bit) != 0) return PieceType.QUEEN;
            if ((bs.blackKing & bit) != 0) return PieceType.KING;
        }
        return PieceType.NONE;
    }
}
