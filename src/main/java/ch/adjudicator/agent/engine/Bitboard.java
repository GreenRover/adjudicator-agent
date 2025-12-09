package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Bitboard implements BoardInterface {

    private final long[] pieces;
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

    private StateHistory[] history = new StateHistory[MAX_GAME_MOVES];
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
        pieces = new long[Piece.values().length];
        loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    private void clear() {
        Arrays.fill(pieces, 0L);
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
        long bit = 1L << sq.ordinal();
        pieces[piece.ordinal()] |= bit;

        if (piece.name().startsWith("WHITE")) {
            whitePieces |= bit;
        } else if (piece.name().startsWith("BLACK")) {
            blackPieces |= bit;
        }
        occupiedSquares |= bit;
    }

    public void removePiece(Square sq) {
        long bit = 1L << sq.ordinal();
        long mask = ~bit;
        for (int i = 0; i < pieces.length; i++) {
            pieces[i] &= mask;
        }
        whitePieces &= mask;
        blackPieces &= mask;
        occupiedSquares &= mask;
    }

    public Piece getPieceAt(Square sq) {
        return getPiece(sq);
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
                    Square sq = Square.values()[rank * 8 + file];
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
                Square sq = Square.values()[rank * 8 + file];
                Piece p = getPiece(sq);
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
        return 0;
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
            list.add(new Move(Square.values()[from], Square.values()[to], promoPiece));
        }
        return list;
    }

    @Override
    public boolean doMove(Move move) {
        // Find matching legal move to ensure validity?
        // For performance, assume valid or encode directly.
        // We need to encode the move.
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
            int move = history[historyPly - 1].move; // Move that was made
            // Reconstruct Move object
            int from = move & 0x3F;
            int to = (move >> 6) & 0x3F;
            int promo = (move >> 12) & 7;
            Piece promoPiece = Piece.NONE;
            // Note: sideToMove is currently the side AFTER the move.
            // unmakeMove reverts sideToMove.
            // But here we want to return the move that was just undone (made by the PREVIOUS sideToMove).
            // So if sideToMove is BLACK, the move was made by WHITE.
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
            return new Move(Square.values()[from], Square.values()[to], promoPiece);
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
        
        Piece movingPiece = getPiece(Square.values()[from]);
        Piece capturedPiece = getPiece(Square.values()[to]);
        
        boolean isEP = false;
        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) &&
            enPassantSquare != Square.NONE && to == enPassantSquare.ordinal()) {
            isEP = true;
            capturedPiece = (sideToMove == Side.WHITE) ? Piece.BLACK_PAWN : Piece.WHITE_PAWN;
        }
        
        state.capturedPiece = capturedPiece;
        historyPly++;
        
        removePiece(Square.values()[from]);
        
        if (capturedPiece != Piece.NONE) {
            if (isEP) {
                int capSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
                removePiece(Square.values()[capSq]);
            } else {
                removePiece(Square.values()[to]);
            }
        }
        
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
        putPiece(pieceToPlace, Square.values()[to]);
        
        if ((movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            if (to > from) { // Kingside
                int rFrom = from + 3; 
                int rTo = from + 1;
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePiece(Square.values()[rFrom]);
                putPiece(rook, Square.values()[rTo]);
            } else { // Queenside
                int rFrom = from - 4; 
                int rTo = from - 1;
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePiece(Square.values()[rFrom]);
                putPiece(rook, Square.values()[rTo]);
            }
        }
        
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
            enPassantSquare = Square.values()[epIndex];
        }
        
        if (capturedPiece != Piece.NONE || movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
        
        if (sideToMove == Side.BLACK) fullMoveNumber++;
        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;
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
        
        Piece movedPiece = getPiece(Square.values()[to]);
        if (promo != 0) {
            movedPiece = (sideToMove == Side.WHITE) ? Piece.WHITE_PAWN : Piece.BLACK_PAWN;
        }
        
        removePiece(Square.values()[to]);
        putPiece(movedPiece, Square.values()[from]);
        
        if (capturedPiece != Piece.NONE) {
             boolean isEP = false;
             if ((movedPiece == Piece.WHITE_PAWN || movedPiece == Piece.BLACK_PAWN) && 
                 state.enPassantSquare != Square.NONE &&
                 to == state.enPassantSquare.ordinal()) {
                 isEP = true;
             }
             if (isEP) {
                 int capSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
                 putPiece(capturedPiece, Square.values()[capSq]);
             } else {
                 putPiece(capturedPiece, Square.values()[to]);
             }
        }
        
        if ((movedPiece == Piece.WHITE_KING || movedPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            if (to > from) { 
                int rFrom = from + 3; 
                int rTo = from + 1;   
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePiece(Square.values()[rTo]);
                putPiece(rook, Square.values()[rFrom]);
            } else { 
                int rFrom = from - 4; 
                int rTo = from - 1;   
                Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                removePiece(Square.values()[rTo]);
                putPiece(rook, Square.values()[rFrom]);
            }
        }
    }
    
    public int generateLegalMoves(int[] moves) {
        int[] pseudo = new int[256];
        int count = generatePseudoLegalMoves(pseudo);
        int legalCount = 0;

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

            // Castling Special Case: Check 'from' (current) and 'mid' (path) squares
            if (kingSq != -1 && from == kingSq && Math.abs(to - from) == 2) {
                if (isSquareAttacked(from, enemy)) {
                    continue;
                }
                int mid = (from + to) / 2;
                if (isSquareAttacked(mid, enemy)) {
                    continue;
                }
            }

            makeMove(m);

            // After makeMove, sideToMove is flipped (now it's enemy's turn)
            // We check if OUR king is attacked by the enemy
            // Note: Our king might have moved, so we locate it again
            int myKingIdx = (us == Side.WHITE) ? Piece.WHITE_KING.ordinal() : Piece.BLACK_KING.ordinal();
            long myKing = pieces[myKingIdx];
            if (myKing != 0) {
                int kSq = Long.numberOfTrailingZeros(myKing);
                if (!isSquareAttacked(kSq, sideToMove)) {
                    moves[legalCount++] = m;
                }
            }
            unmakeMove(m);
        }
        return legalCount;
    }

    @Override
    public boolean doNullMove() {
        return false;
    }

    @Override
    public boolean isMated() {
        if (!isKingAttacked()) {
            return false;
        }
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
        long bit = 1L << square.ordinal();
        if ((occupiedSquares & bit) == 0) return Piece.NONE;

        for (Piece p : Piece.values()) {
            if (p == Piece.NONE) continue;
            if ((pieces[p.ordinal()] & bit) != 0) {
                return p;
            }
        }
        return Piece.NONE;
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
                // Check promotion
                if (nextRank == promoRank) {
                    addPromoMoves(moveList, index, sq, forwardSq);
                    index += 4;
                } else {
                    moveList[index++] = encodeMove(sq, forwardSq, 0);
                    // 2. Double Push
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
                        // En Passant
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
        moveList[index] = encodeMove(from, to, 1); // Knight
        moveList[index+1] = encodeMove(from, to, 2); // Bishop
        moveList[index+2] = encodeMove(from, to, 3); // Rook
        moveList[index+3] = encodeMove(from, to, 4); // Queen
    }

    public static int encodeMove(int from, int to, int promo) {
        return from | (to << 6) | (promo << 12);
    }
}
