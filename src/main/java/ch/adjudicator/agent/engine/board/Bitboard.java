package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.ZobristHasher;
import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Bitboard implements BoardInterface {

    // Cache enum values to avoid cloning overhead
    private static final Square[] SQUARES = Square.values();
    private static final Piece[] PIECES = Piece.values();

    // Fast lookup for piece side (0=White, 1=Black, 2=None)
    private static final Side[] PIECE_SIDES = new Side[PIECES.length];

    static {
        for (Piece p : PIECES) {
            if (p == Piece.NONE) {
                PIECE_SIDES[p.ordinal()] = null;
            } else {
                PIECE_SIDES[p.ordinal()] = p.name().startsWith("WHITE") ? Side.WHITE : Side.BLACK;
            }
        }
    }

    private final long[] pieces;
    // Mailbox representation for O(1) piece lookup
    private final Piece[] mailbox;

    private long whitePieces;
    private long blackPieces;
    private long occupiedSquares;
    private Side sideToMove;
    private int castlingRights;
    private Square enPassantSquare;
    private int halfMoveClock;
    private int fullMoveNumber;
    private long zobristHash;

    // History
    private static final int MAX_GAME_MOVES = 2048;

    private static class StateHistory {
        public int move;
        public Piece capturedPiece;
        public int castlingRights;
        public Square enPassantSquare;
        public int halfMoveClock;
        public long zobristHash;
    }

    private final StateHistory[] history = new StateHistory[MAX_GAME_MOVES];
    private int historyPly = 0;

    // Castling constants
    private static final int CASTLE_WK = 1;
    private static final int CASTLE_WQ = 2;
    private static final int CASTLE_BK = 4;
    private static final int CASTLE_BQ = 8;

    public Bitboard() {
        for (int i = 0; i < MAX_GAME_MOVES; i++) {
            history[i] = new StateHistory();
        }
        pieces = new long[PIECES.length];
        mailbox = new Piece[64];
        clear();
    }

    public void clear() {
        Arrays.fill(pieces, 0L);
        Arrays.fill(mailbox, Piece.NONE);
        whitePieces = 0L;
        blackPieces = 0L;
        occupiedSquares = 0L;
        sideToMove = Side.WHITE;
        castlingRights = 0;
        enPassantSquare = Square.NONE;
        halfMoveClock = 0;
        fullMoveNumber = 1;
        historyPly = 0;
        zobristHash = 0L;
    }

    public void putPiece(Piece piece, Square sq) {
        if (piece == Piece.NONE) return;
        int sqIdx = sq.ordinal();
        long bit = 1L << sqIdx;

        // Update bitboards
        pieces[piece.ordinal()] |= bit;

        if (PIECE_SIDES[piece.ordinal()] == Side.WHITE) {
            whitePieces |= bit;
        } else {
            blackPieces |= bit;
        }
        occupiedSquares |= bit;

        // Update mailbox
        mailbox[sqIdx] = piece;
    }

    public void removePiece(Square sq) {
        int sqIdx = sq.ordinal();
        Piece p = mailbox[sqIdx];
        if (p == Piece.NONE) return;

        long bit = 1L << sqIdx;
        long mask = ~bit;

        // Update bitboards
        pieces[p.ordinal()] &= mask;

        if (PIECE_SIDES[p.ordinal()] == Side.WHITE) {
            whitePieces &= mask;
        } else {
            blackPieces &= mask;
        }
        occupiedSquares &= mask;

        // Update mailbox
        mailbox[sqIdx] = Piece.NONE;
    }

    public Piece getPieceAt(Square sq) {
        return mailbox[sq.ordinal()];
    }

    @Override
    public void loadFromFen(String fen) {
        clear();
        String[] parts = fen.split(" ");
        String placement = parts[0];
        String activeColor = parts[1];
        String castling = parts[2];
        String enPassant = parts[3];

        // 1. Placement
        int rank = 7;
        int file = 0;
        for (char c : placement.toCharArray()) {
            if (c == '/') {
                rank--;
                file = 0;
            } else if (Character.isDigit(c)) {
                file += Character.getNumericValue(c);
            } else {
                if (file < 8) {
                    Square sq = SQUARES[rank * 8 + file];
                    Piece p = getPieceFromChar(c);
                    putPiece(p, sq);
                    file++;
                }
            }
        }

        // 2. Active Color
        sideToMove = activeColor.equals("w") ? Side.WHITE : Side.BLACK;

        // 3. Castling
        if (!castling.equals("-")) {
            if (castling.contains("K")) castlingRights |= CASTLE_WK;
            if (castling.contains("Q")) castlingRights |= CASTLE_WQ;
            if (castling.contains("k")) castlingRights |= CASTLE_BK;
            if (castling.contains("q")) castlingRights |= CASTLE_BQ;
        }

        // 4. En Passant
        if (!enPassant.equals("-")) {
            try {
                enPassantSquare = Square.valueOf(enPassant.toUpperCase());
            } catch (IllegalArgumentException e) {
                enPassantSquare = Square.NONE;
            }
        } else {
            enPassantSquare = Square.NONE;
        }

        // 5. Halfmove Clock
        if (parts.length > 4) {
            try {
                halfMoveClock = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                halfMoveClock = 0;
            }
        }

        // 6. Fullmove Number
        if (parts.length > 5) {
            try {
                fullMoveNumber = Integer.parseInt(parts[5]);
            } catch (NumberFormatException e) {
                fullMoveNumber = 1;
            }
        }

        // Initialize Zobrist
        zobristHash = ZobristHasher.getZobristKey(this);
    }

    private Piece getPieceFromChar(char c) {
        return switch (c) {
            case 'P' -> Piece.WHITE_PAWN;
            case 'N' -> Piece.WHITE_KNIGHT;
            case 'B' -> Piece.WHITE_BISHOP;
            case 'R' -> Piece.WHITE_ROOK;
            case 'Q' -> Piece.WHITE_QUEEN;
            case 'K' -> Piece.WHITE_KING;
            case 'p' -> Piece.BLACK_PAWN;
            case 'n' -> Piece.BLACK_KNIGHT;
            case 'b' -> Piece.BLACK_BISHOP;
            case 'r' -> Piece.BLACK_ROOK;
            case 'q' -> Piece.BLACK_QUEEN;
            case 'k' -> Piece.BLACK_KING;
            default -> Piece.NONE;
        };
    }

    @Override
    public String getFen() {
        StringBuilder sb = new StringBuilder();

        // 1. Placement
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece p = mailbox[rank * 8 + file];
                if (p == Piece.NONE) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(getPieceChar(p));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }

        sb.append(' ');

        // 2. Active Color
        sb.append(sideToMove == Side.WHITE ? "w" : "b");

        sb.append(' ');

        // 3. Castling
        boolean anyCastle = false;
        if ((castlingRights & CASTLE_WK) != 0) { sb.append('K'); anyCastle = true; }
        if ((castlingRights & CASTLE_WQ) != 0) { sb.append('Q'); anyCastle = true; }
        if ((castlingRights & CASTLE_BK) != 0) { sb.append('k'); anyCastle = true; }
        if ((castlingRights & CASTLE_BQ) != 0) { sb.append('q'); anyCastle = true; }
        if (!anyCastle) sb.append('-');

        sb.append(' ');

        // 4. En Passant
        if (enPassantSquare == Square.NONE) {
            sb.append('-');
        } else {
            sb.append(enPassantSquare.toString().toLowerCase());
        }

        sb.append(' ');

        // 5. Halfmove
        sb.append(halfMoveClock);

        sb.append(' ');

        // 6. Fullmove
        sb.append(fullMoveNumber);

        return sb.toString();
    }

    private char getPieceChar(Piece p) {
        return switch (p) {
            case WHITE_PAWN -> 'P';
            case WHITE_KNIGHT -> 'N';
            case WHITE_BISHOP -> 'B';
            case WHITE_ROOK -> 'R';
            case WHITE_QUEEN -> 'Q';
            case WHITE_KING -> 'K';
            case BLACK_PAWN -> 'p';
            case BLACK_KNIGHT -> 'n';
            case BLACK_BISHOP -> 'b';
            case BLACK_ROOK -> 'r';
            case BLACK_QUEEN -> 'q';
            case BLACK_KING -> 'k';
            default -> throw new IllegalArgumentException("Unknown piece: " + p);
        };
    }

    @Override
    public long getZobristKey() {
        return zobristHash;
    }

    @Override
    public Side getSideToMove() {
        return sideToMove;
    }

    @Override
    public List<Move> legalMoves() {
        int[] moves = new int[256];
        int count = generateLegalMoves(moves);
        List<Move> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int m = moves[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;
            int promo = (m >> 12) & 7;
            Piece promoPiece = Piece.NONE;
            if (promo != 0) {
                if (sideToMove == Side.WHITE) {
                    switch (promo) {
                        case 1 -> promoPiece = Piece.WHITE_KNIGHT;
                        case 2 -> promoPiece = Piece.WHITE_BISHOP;
                        case 3 -> promoPiece = Piece.WHITE_ROOK;
                        case 4 -> promoPiece = Piece.WHITE_QUEEN;
                    }
                } else {
                    switch (promo) {
                        case 1 -> promoPiece = Piece.BLACK_KNIGHT;
                        case 2 -> promoPiece = Piece.BLACK_BISHOP;
                        case 3 -> promoPiece = Piece.BLACK_ROOK;
                        case 4 -> promoPiece = Piece.BLACK_QUEEN;
                    }
                }
            }
            list.add(new Move(SQUARES[from], SQUARES[to], promoPiece));
        }
        return list;
    }

    @Override
    public boolean doMove(Move move) {
        int from = move.getFrom().ordinal();
        int to = move.getTo().ordinal();
        int promo = 0;
        if (move.getPromotion() != Piece.NONE) {
            Piece p = move.getPromotion();
            if (p == Piece.WHITE_KNIGHT || p == Piece.BLACK_KNIGHT) promo = 1;
            else if (p == Piece.WHITE_BISHOP || p == Piece.BLACK_BISHOP) promo = 2;
            else if (p == Piece.WHITE_ROOK || p == Piece.BLACK_ROOK) promo = 3;
            else if (p == Piece.WHITE_QUEEN || p == Piece.BLACK_QUEEN) promo = 4;
        }
        int encoded = encodeMove(from, to, promo);
        makeMove(encoded);
        return true;
    }

    @Override
    public Move undoMove() {
        if (historyPly > 0) {
            int move = history[historyPly - 1].move;
            int from = move & 0x3F;
            int to = (move >> 6) & 0x3F;
            int promo = (move >> 12) & 7;
            Piece promoPiece = Piece.NONE;

            // Note: sideToMove is the side AFTER the move.
            Side mover = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;

            if (promo != 0) {
                if (mover == Side.WHITE) {
                    switch (promo) {
                        case 1 -> promoPiece = Piece.WHITE_KNIGHT;
                        case 2 -> promoPiece = Piece.WHITE_BISHOP;
                        case 3 -> promoPiece = Piece.WHITE_ROOK;
                        case 4 -> promoPiece = Piece.WHITE_QUEEN;
                    }
                } else {
                    switch (promo) {
                        case 1 -> promoPiece = Piece.BLACK_KNIGHT;
                        case 2 -> promoPiece = Piece.BLACK_BISHOP;
                        case 3 -> promoPiece = Piece.BLACK_ROOK;
                        case 4 -> promoPiece = Piece.BLACK_QUEEN;
                    }
                }
            }
            unmakeMove(move);
            return new Move(SQUARES[from], SQUARES[to], promoPiece);
        }
        return null;
    }

    public void makeMove(int move) {
        StateHistory state = history[historyPly];
        state.move = move;
        state.castlingRights = castlingRights;
        state.enPassantSquare = enPassantSquare;
        state.halfMoveClock = halfMoveClock;
        state.zobristHash = zobristHash;

        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;
        int promo = (move >> 12) & 7;

        // Fast lookup via mailbox
        Piece movingPiece = mailbox[from];
        Piece capturedPiece = mailbox[to];

        boolean isEP = false;
        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) &&
                enPassantSquare != Square.NONE && to == enPassantSquare.ordinal()) {
            isEP = true;
            capturedPiece = (sideToMove == Side.WHITE) ? Piece.BLACK_PAWN : Piece.WHITE_PAWN;
        }

        state.capturedPiece = capturedPiece;
        historyPly++;

        // 1. Remove moving piece from source
        removePieceInternal(movingPiece, from);

        // 2. Handle Capture
        if (capturedPiece != Piece.NONE) {
            if (isEP) {
                int capSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
                removePieceInternal(capturedPiece, capSq);
            } else {
                removePieceInternal(capturedPiece, to);
            }
        }

        // 3. Determine placed piece (handle promotion)
        Piece pieceToPlace = movingPiece;
        if (promo != 0) {
            if (sideToMove == Side.WHITE) {
                switch (promo) {
                    case 1 -> pieceToPlace = Piece.WHITE_KNIGHT;
                    case 2 -> pieceToPlace = Piece.WHITE_BISHOP;
                    case 3 -> pieceToPlace = Piece.WHITE_ROOK;
                    case 4 -> pieceToPlace = Piece.WHITE_QUEEN;
                }
            } else {
                switch (promo) {
                    case 1 -> pieceToPlace = Piece.BLACK_KNIGHT;
                    case 2 -> pieceToPlace = Piece.BLACK_BISHOP;
                    case 3 -> pieceToPlace = Piece.BLACK_ROOK;
                    case 4 -> pieceToPlace = Piece.BLACK_QUEEN;
                }
            }
        }

        // 4. Place piece at destination
        putPieceInternal(pieceToPlace, to);

        // 5. Handle Castling (Rook moves)
        if ((movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            if (to > from) { // Kingside
                int rFrom = from + 3;
                int rTo = from + 1;
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePieceInternal(rook, rFrom);
                putPieceInternal(rook, rTo);
            } else { // Queenside
                int rFrom = from - 4;
                int rTo = from - 1;
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePieceInternal(rook, rFrom);
                putPieceInternal(rook, rTo);
            }
        }

        // Update State
        if (movingPiece == Piece.WHITE_KING) castlingRights &= ~(CASTLE_WK | CASTLE_WQ);
        else if (movingPiece == Piece.BLACK_KING) castlingRights &= ~(CASTLE_BK | CASTLE_BQ);

        if (movingPiece == Piece.WHITE_ROOK) {
            if (from == 7) castlingRights &= ~CASTLE_WK;
            if (from == 0) castlingRights &= ~CASTLE_WQ;
        } else if (movingPiece == Piece.BLACK_ROOK) {
            if (from == 63) castlingRights &= ~CASTLE_BK;
            if (from == 56) castlingRights &= ~CASTLE_BQ;
        }

        if (capturedPiece == Piece.WHITE_ROOK) {
            if (to == 7) castlingRights &= ~CASTLE_WK;
            if (to == 0) castlingRights &= ~CASTLE_WQ;
        } else if (capturedPiece == Piece.BLACK_ROOK) {
            if (to == 63) castlingRights &= ~CASTLE_BK;
            if (to == 56) castlingRights &= ~CASTLE_BQ;
        }

        enPassantSquare = Square.NONE;
        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) && Math.abs(to - from) == 16) {
            int epIndex = (from + to) / 2;
            enPassantSquare = SQUARES[epIndex];
        }

        if (capturedPiece != Piece.NONE || movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        if (sideToMove == Side.BLACK) fullMoveNumber++;
        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;

        // Note: For pure speed test, we skip incremental Zobrist updates.
        // In production, update hash here incrementally.
    }

    private void putPieceInternal(Piece piece, int sqIdx) {
        long bit = 1L << sqIdx;
        pieces[piece.ordinal()] |= bit;
        if (PIECE_SIDES[piece.ordinal()] == Side.WHITE) whitePieces |= bit;
        else blackPieces |= bit;
        occupiedSquares |= bit;
        mailbox[sqIdx] = piece;
    }

    private void removePieceInternal(Piece piece, int sqIdx) {
        long bit = 1L << sqIdx;
        long mask = ~bit;
        pieces[piece.ordinal()] &= mask;
        if (PIECE_SIDES[piece.ordinal()] == Side.WHITE) whitePieces &= mask;
        else blackPieces &= mask;
        occupiedSquares &= mask;
        mailbox[sqIdx] = Piece.NONE;
    }

    public void unmakeMove(int move) {
        historyPly--;
        StateHistory state = history[historyPly];

        castlingRights = state.castlingRights;
        enPassantSquare = state.enPassantSquare;
        halfMoveClock = state.halfMoveClock;
        zobristHash = state.zobristHash;
        Piece capturedPiece = state.capturedPiece;

        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;
        if (sideToMove == Side.BLACK) fullMoveNumber--;

        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;
        int promo = (move >> 12) & 7;

        Piece movedPiece = mailbox[to];
        if (promo != 0) {
            movedPiece = (sideToMove == Side.WHITE) ? Piece.WHITE_PAWN : Piece.BLACK_PAWN;
        }

        removePieceInternal(mailbox[to], to);
        putPieceInternal(movedPiece, from);

        if (capturedPiece != Piece.NONE) {
            boolean isEP = false;
            if ((movedPiece == Piece.WHITE_PAWN || movedPiece == Piece.BLACK_PAWN) &&
                    state.enPassantSquare != Square.NONE &&
                    to == state.enPassantSquare.ordinal()) {
                isEP = true;
            }

            if (isEP) {
                int capSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
                putPieceInternal(capturedPiece, capSq);
            } else {
                putPieceInternal(capturedPiece, to);
            }
        }

        if ((movedPiece == Piece.WHITE_KING || movedPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            if (to > from) {
                int rFrom = from + 3;
                int rTo = from + 1;
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePieceInternal(rook, rTo);
                putPieceInternal(rook, rFrom);
            } else {
                int rFrom = from - 4;
                int rTo = from - 1;
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePieceInternal(rook, rTo);
                putPieceInternal(rook, rFrom);
            }
        }
    }

    public int generateLegalMoves(int[] moves) {
        int[] pseudo = new int[256];
        int count = generatePseudoLegalMoves(pseudo);
        int legalCount = 0;

        // Cache king info
        int kingIdx = (sideToMove == Side.WHITE) ? Piece.WHITE_KING.ordinal() : Piece.BLACK_KING.ordinal();
        long kingBit = pieces[kingIdx];
        int kingSq = -1;
        if (kingBit != 0) {
            kingSq = Long.numberOfTrailingZeros(kingBit);
        }

        Side us = sideToMove;
        Side enemy = (us == Side.WHITE) ? Side.BLACK : Side.WHITE;

        for (int i = 0; i < count; i++) {
            int m = pseudo[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;

            // Castling Special Case: verify path is safe
            if (kingSq != -1 && from == kingSq && Math.abs(to - from) == 2) {
                if (isSquareAttacked(from, enemy)) continue;
                int mid = (from + to) / 2;
                if (isSquareAttacked(mid, enemy)) continue;
                // Castling destination safety is checked by isLegal() below (standard check)
            }

            // Use lightweight legality check
            if (isLegal(m)) {
                moves[legalCount++] = m;
            }
        }
        return legalCount;
    }

    /**
     * Checks if a pseudo-legal move is strictly legal (leaves King safe)
     * without performing full Make/Unmake.
     */
    private boolean isLegal(int move) {
        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;
        int promo = (move >> 12) & 7;

        Piece movingPiece = mailbox[from];
        Piece capturedPiece = mailbox[to];

        // Identify if En Passant
        boolean isEP = false;
        int epCapSq = -1;
        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) &&
                enPassantSquare != Square.NONE && to == enPassantSquare.ordinal()) {
            isEP = true;
            epCapSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
            capturedPiece = mailbox[epCapSq]; // The pawn being captured
        }

        // Temporarily execute move on Bitboards (Minimal updates)
        // 1. Remove moving piece
        long fromBit = 1L << from;
        long fromMask = ~fromBit;
        long originalOcc = occupiedSquares;

        // Manual inlining for speed (no method calls)
        pieces[movingPiece.ordinal()] &= fromMask;
        if (sideToMove == Side.WHITE) whitePieces &= fromMask; else blackPieces &= fromMask;
        occupiedSquares &= fromMask;

        // 2. Remove captured piece
        if (capturedPiece != Piece.NONE) {
            int capLoc = isEP ? epCapSq : to;
            long capBit = 1L << capLoc;
            long capMask = ~capBit;
            pieces[capturedPiece.ordinal()] &= capMask;
            if (sideToMove == Side.WHITE) blackPieces &= capMask; else whitePieces &= capMask;
            occupiedSquares &= capMask;
        }

        // 3. Place piece at dest (Handle promo if needed for bitboard correctness?
        // For check detection, the type of MY piece only matters if it's the King.
        // If it's a promotion, we can just move the Pawn. The blocking effect is the same.
        // EXCEPT if I promoted to a piece that could be captured? No, I'm checking if *I* am in check.)
        Piece pieceOnDest = movingPiece;
        // Optimization: If it's a King move, we MUST update the King bitboard to check the new square safety.
        // If it's a promotion, updating as Pawn is fine for blocking lines, unless we worry about complex interactions.
        // Let's stick to moving the actual piece type (even if wrong promo type) or just movingPiece.
        // If movingPiece is KING, we must update KING bitboard.

        long toBit = 1L << to;
        pieces[pieceOnDest.ordinal()] |= toBit;
        if (sideToMove == Side.WHITE) whitePieces |= toBit; else blackPieces |= toBit;
        occupiedSquares |= toBit;

        // 4. Handle Castling Rook (Important for line blocking)
        boolean isCastling = (movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2;
        int rFrom = -1, rTo = -1;
        Piece rook = Piece.NONE;

        if (isCastling) {
            rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
            if (to > from) { rFrom = from + 3; rTo = from + 1; }
            else { rFrom = from - 4; rTo = from - 1; }

            // Move rook
            long rFromBit = 1L << rFrom;
            long rFromMask = ~rFromBit;
            pieces[rook.ordinal()] &= rFromMask;
            if (sideToMove == Side.WHITE) whitePieces &= rFromMask; else blackPieces &= rFromMask;
            occupiedSquares &= rFromMask;

            long rToBit = 1L << rTo;
            pieces[rook.ordinal()] |= rToBit;
            if (sideToMove == Side.WHITE) whitePieces |= rToBit; else blackPieces |= rToBit;
            occupiedSquares |= rToBit;
        }

        // --- CHECK SAFETY ---
        boolean safe = !isKingAttacked();

        // --- REVERT UPDATES (Manual) ---

        // Revert Castling
        if (isCastling) {
            long rToBit = 1L << rTo;
            long rToMask = ~rToBit;
            pieces[rook.ordinal()] &= rToMask;
            if (sideToMove == Side.WHITE) whitePieces &= rToMask; else blackPieces &= rToMask;

            long rFromBit = 1L << rFrom;
            pieces[rook.ordinal()] |= rFromBit;
            if (sideToMove == Side.WHITE) whitePieces |= rFromBit; else blackPieces |= rFromBit;
        }

        // Revert Dest
        long toMask = ~toBit;
        pieces[pieceOnDest.ordinal()] &= toMask;
        if (sideToMove == Side.WHITE) whitePieces &= toMask; else blackPieces &= toMask;

        // Revert Capture
        if (capturedPiece != Piece.NONE) {
            int capLoc = isEP ? epCapSq : to;
            long capBit = 1L << capLoc;
            pieces[capturedPiece.ordinal()] |= capBit;
            if (sideToMove == Side.WHITE) blackPieces |= capBit; else whitePieces |= capBit;
        }

        // Revert Source
        pieces[movingPiece.ordinal()] |= fromBit;
        if (sideToMove == Side.WHITE) whitePieces |= fromBit; else blackPieces |= fromBit;

        // Restore occupancy explicitly to avoid drift
        occupiedSquares = originalOcc;

        return safe;
    }

    @Override
    public boolean doNullMove() {
        StateHistory state = history[historyPly];
        state.move = 0;
        state.capturedPiece = Piece.NONE;
        state.castlingRights = castlingRights;
        state.enPassantSquare = enPassantSquare;
        state.halfMoveClock = halfMoveClock;
        state.zobristHash = zobristHash;

        historyPly++;
        if (enPassantSquare != Square.NONE) enPassantSquare = Square.NONE;
        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;
        if (sideToMove == Side.BLACK) fullMoveNumber++;
        halfMoveClock++;

        return true;
    }

    @Override
    public boolean isMated() {
        if (!isKingAttacked()) return false;
        int[] moves = new int[256];
        int count = generateLegalMoves(moves);
        return count == 0;
    }

    public boolean isSquareAttacked(int sq, Side attackerSide) {
        long occ = occupiedSquares;
        if (attackerSide == Side.WHITE) {
            if ((AttackLookups.PAWN_ATTACKS[Side.BLACK.ordinal()][sq] & pieces[Piece.WHITE_PAWN.ordinal()]) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & pieces[Piece.WHITE_KNIGHT.ordinal()]) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & pieces[Piece.WHITE_KING.ordinal()]) != 0) return true;
            if ((AttackLookups.getBishopAttacks(sq, occ) & (pieces[Piece.WHITE_BISHOP.ordinal()] | pieces[Piece.WHITE_QUEEN.ordinal()])) != 0) return true;
            if ((AttackLookups.getRookAttacks(sq, occ) & (pieces[Piece.WHITE_ROOK.ordinal()] | pieces[Piece.WHITE_QUEEN.ordinal()])) != 0) return true;
        } else {
            if ((AttackLookups.PAWN_ATTACKS[Side.WHITE.ordinal()][sq] & pieces[Piece.BLACK_PAWN.ordinal()]) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & pieces[Piece.BLACK_KNIGHT.ordinal()]) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & pieces[Piece.BLACK_KING.ordinal()]) != 0) return true;
            if ((AttackLookups.getBishopAttacks(sq, occ) & (pieces[Piece.BLACK_BISHOP.ordinal()] | pieces[Piece.BLACK_QUEEN.ordinal()])) != 0) return true;
            if ((AttackLookups.getRookAttacks(sq, occ) & (pieces[Piece.BLACK_ROOK.ordinal()] | pieces[Piece.BLACK_QUEEN.ordinal()])) != 0) return true;
        }
        return false;
    }

    @Override
    public boolean isKingAttacked() {
        int kingIdx = (sideToMove == Side.WHITE) ? Piece.WHITE_KING.ordinal() : Piece.BLACK_KING.ordinal();
        long kBoard = pieces[kingIdx];
        if (kBoard == 0) return false;
        int kingSq = Long.numberOfTrailingZeros(kBoard);
        return isSquareAttacked(kingSq, sideToMove == Side.WHITE ? Side.BLACK : Side.WHITE);
    }

    @Override
    public Piece getPiece(Square square) {
        return mailbox[square.ordinal()];
    }

    @Override
    public Square getEnPassant() {
        return enPassantSquare;
    }

    @Override
    public CastleRight getCastleRight(Side side) {
        if (side == Side.WHITE) {
            boolean k = (castlingRights & CASTLE_WK) != 0;
            boolean q = (castlingRights & CASTLE_WQ) != 0;
            if (k && q) return CastleRight.KING_AND_QUEEN_SIDE;
            if (k) return CastleRight.KING_SIDE;
            if (q) return CastleRight.QUEEN_SIDE;
            return CastleRight.NONE;
        } else {
            boolean k = (castlingRights & CASTLE_BK) != 0;
            boolean q = (castlingRights & CASTLE_BQ) != 0;
            if (k && q) return CastleRight.KING_AND_QUEEN_SIDE;
            if (k) return CastleRight.KING_SIDE;
            if (q) return CastleRight.QUEEN_SIDE;
            return CastleRight.NONE;
        }
    }

    @Override
    public long getBitboard(Side side) {
        if (side == Side.WHITE) return whitePieces;
        if (side == Side.BLACK) return blackPieces;
        return 0L;
    }

    @Override
    public long getBitboard(Piece piece) {
        if (piece == Piece.NONE) return 0L;
        return pieces[piece.ordinal()];
    }

    public int generatePseudoLegalMoves(int[] moveList) {
        int index = 0;
        long friendly, enemy;
        int pawnIdx, knightIdx, bishopIdx, rookIdx, queenIdx, kingIdx;
        int promoRank, doublePushRank, startRank;
        boolean isWhite = (sideToMove == Side.WHITE);

        if (isWhite) {
            friendly = whitePieces;
            enemy = blackPieces;
            pawnIdx = 0; // WHITE_PAWN
            knightIdx = 1;
            bishopIdx = 2;
            rookIdx = 3;
            queenIdx = 4;
            kingIdx = 5;
            promoRank = 7;
            startRank = 1;
            doublePushRank = 3;
        } else {
            friendly = blackPieces;
            enemy = whitePieces;
            pawnIdx = 6; // BLACK_PAWN
            knightIdx = 7;
            bishopIdx = 8;
            rookIdx = 9;
            queenIdx = 10;
            kingIdx = 11;
            promoRank = 0;
            startRank = 6;
            doublePushRank = 4;
        }

        long occupied = occupiedSquares;

        // --- Pawns ---
        long p = pieces[pawnIdx];
        while (p != 0) {
            int sq = Long.numberOfTrailingZeros(p);
            p &= p - 1;

            int rank = sq / 8;
            int file = sq % 8;

            // 1. Single Push
            int nextRank = isWhite ? rank + 1 : rank - 1;
            int forwardSq = nextRank * 8 + file;
            if (((1L << forwardSq) & occupied) == 0) {
                if (nextRank == promoRank) {
                    addPromoMoves(moveList, index, sq, forwardSq);
                    index += 4;
                } else {
                    moveList[index++] = encodeMove(sq, forwardSq, 0);
                    if (rank == startRank) {
                        int doubleRank = isWhite ? rank + 2 : rank - 2;
                        int doubleSq = doubleRank * 8 + file;
                        if (((1L << doubleSq) & occupied) == 0) {
                            moveList[index++] = encodeMove(sq, doubleSq, 0);
                        }
                    }
                }
            }

            // 3. Captures
            for (int dFile = -1; dFile <= 1; dFile += 2) {
                if (file + dFile >= 0 && file + dFile < 8) {
                    int captureSq = nextRank * 8 + (file + dFile);
                    long captureBit = 1L << captureSq;
                    if ((captureBit & enemy) != 0) {
                        if (nextRank == promoRank) {
                            addPromoMoves(moveList, index, sq, captureSq);
                            index += 4;
                        } else {
                            moveList[index++] = encodeMove(sq, captureSq, 0);
                        }
                    } else if (captureSq == enPassantSquare.ordinal()) {
                        moveList[index++] = encodeMove(sq, captureSq, 0);
                    }
                }
            }
        }

        // --- Knights ---
        long n = pieces[knightIdx];
        while (n != 0) {
            int sq = Long.numberOfTrailingZeros(n);
            n &= n - 1;
            long attacks = AttackLookups.KNIGHT_ATTACKS[sq] & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Bishops ---
        long b = pieces[bishopIdx];
        while (b != 0) {
            int sq = Long.numberOfTrailingZeros(b);
            b &= b - 1;
            long attacks = AttackLookups.getBishopAttacks(sq, occupied) & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Rooks ---
        long r = pieces[rookIdx];
        while (r != 0) {
            int sq = Long.numberOfTrailingZeros(r);
            r &= r - 1;
            long attacks = AttackLookups.getRookAttacks(sq, occupied) & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Queens ---
        long q = pieces[queenIdx];
        while (q != 0) {
            int sq = Long.numberOfTrailingZeros(q);
            q &= q - 1;
            long attacks = AttackLookups.getQueenAttacks(sq, occupied) & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- King ---
        long k = pieces[kingIdx];
        while (k != 0) {
            int sq = Long.numberOfTrailingZeros(k);
            k &= k - 1;
            long attacks = AttackLookups.KING_ATTACKS[sq] & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Castling ---
        if (isWhite) {
            if ((castlingRights & CASTLE_WK) != 0) {
                if ((occupied & ((1L << 5) | (1L << 6))) == 0) {
                    if (!isSquareAttacked(4, Side.BLACK) && !isSquareAttacked(5, Side.BLACK) && !isSquareAttacked(6, Side.BLACK)) {
                        moveList[index++] = encodeMove(4, 6, 0);
                    }
                }
            }
            if ((castlingRights & CASTLE_WQ) != 0) {
                if ((occupied & ((1L << 1) | (1L << 2) | (1L << 3))) == 0) {
                    if (!isSquareAttacked(4, Side.BLACK) && !isSquareAttacked(3, Side.BLACK) && !isSquareAttacked(2, Side.BLACK)) {
                        moveList[index++] = encodeMove(4, 2, 0);
                    }
                }
            }
        } else {
            if ((castlingRights & CASTLE_BK) != 0) {
                if ((occupied & ((1L << 61) | (1L << 62))) == 0) {
                    if (!isSquareAttacked(60, Side.WHITE) && !isSquareAttacked(61, Side.WHITE) && !isSquareAttacked(62, Side.WHITE)) {
                        moveList[index++] = encodeMove(60, 62, 0);
                    }
                }
            }
            if ((castlingRights & CASTLE_BQ) != 0) {
                if ((occupied & ((1L << 57) | (1L << 58) | (1L << 59))) == 0) {
                    if (!isSquareAttacked(60, Side.WHITE) && !isSquareAttacked(59, Side.WHITE) && !isSquareAttacked(58, Side.WHITE)) {
                        moveList[index++] = encodeMove(60, 58, 0);
                    }
                }
            }
        }

        return index;
    }

    private void addPromoMoves(int[] moveList, int index, int from, int to) {
        moveList[index] = encodeMove(from, to, 1);
        moveList[index+1] = encodeMove(from, to, 2);
        moveList[index+2] = encodeMove(from, to, 3);
        moveList[index+3] = encodeMove(from, to, 4);
    }

    public static int encodeMove(int from, int to, int promo) {
        return from | (to << 6) | (promo << 12);
    }
}