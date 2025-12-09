package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Arrays;
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

    // Castling constants
    private static final int CASTLE_WK = 1;
    private static final int CASTLE_WQ = 2;
    private static final int CASTLE_BK = 4;
    private static final int CASTLE_BQ = 8;

    public Bitboard() {
        pieces = new long[Piece.values().length];
        clear();
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
        return List.of();
    }

    @Override
    public boolean doMove(Move move) {
        return false;
    }

    @Override
    public Move undoMove() {
        return null;
    }

    @Override
    public boolean doNullMove() {
        return false;
    }

    @Override
    public boolean isMated() {
        return false;
    }

    @Override
    public boolean isKingAttacked() {
        return false;
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
}
