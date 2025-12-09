package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.*;

public class ZobristHasher {
    /**
     * Calculates the Zobrist Key (hash) for a given FEN string.
     * The hash is compatible with Polyglot opening books.
     *
     * @param board A current board state.
     * @return The 64-bit Zobrist hash key.
     */
    public static long getZobristKey(BoardInterface board) {
        long hash = 0L;

        // 1. Pieces
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            Piece piece = board.getPiece(sq);
            if (piece != Piece.NONE) {
                int polyglotPiece = getPolyglotPieceIndex(piece);
                int squareIndex = sq.getRank().ordinal() * 8 + sq.getFile().ordinal();

                if (polyglotPiece >= 0) {
                    hash ^= PolyglotConstants.RANDOM_NUMBERS[polyglotPiece * 64 + squareIndex];
                }
            }
        }

        // 2. Side to move
        if (board.getSideToMove() == Side.WHITE) {
            hash ^= PolyglotConstants.RANDOM_NUMBERS[780];
        }

        // 3. Castling
        CastleRight whiteCastle = board.getCastleRight(Side.WHITE);
        CastleRight blackCastle = board.getCastleRight(Side.BLACK);

        if (whiteCastle.equals(CastleRight.KING_SIDE) || whiteCastle.equals(CastleRight.KING_AND_QUEEN_SIDE)) {
            hash ^= PolyglotConstants.RANDOM_NUMBERS[768];
        }
        if (whiteCastle.equals(CastleRight.QUEEN_SIDE) || whiteCastle.equals(CastleRight.KING_AND_QUEEN_SIDE)) {
            hash ^= PolyglotConstants.RANDOM_NUMBERS[769];
        }
        if (blackCastle.equals(CastleRight.KING_SIDE) || blackCastle.equals(CastleRight.KING_AND_QUEEN_SIDE)) {
            hash ^= PolyglotConstants.RANDOM_NUMBERS[770];
        }
        if (blackCastle.equals(CastleRight.QUEEN_SIDE) || blackCastle.equals(CastleRight.KING_AND_QUEEN_SIDE)) {
            hash ^= PolyglotConstants.RANDOM_NUMBERS[771];
        }

        // 4. En Passant
        Square enPasSq = board.getEnPassant();
        if (enPasSq != Square.NONE) {
            int file = enPasSq.getFile().ordinal();
            int rank = enPasSq.getRank().ordinal(); // 0-based rank

            boolean pawnNearby = false;

            // En Passant hash is only applied if a pawn of the attacking side is in position to capture.
            if (rank == 2) { // Rank 3 (index 2) -> White moved P-e4. Attacker is Black on Rank 4 (index 3).
                if (file > 0 && isPieceAt(board, 3, file - 1, Piece.BLACK_PAWN)) pawnNearby = true;
                if (file < 7 && isPieceAt(board, 3, file + 1, Piece.BLACK_PAWN)) pawnNearby = true;
            } else if (rank == 5) { // Rank 6 (index 5) -> Black moved p-e5. Attacker is White on Rank 5 (index 4).
                if (file > 0 && isPieceAt(board, 4, file - 1, Piece.WHITE_PAWN)) pawnNearby = true;
                if (file < 7 && isPieceAt(board, 4, file + 1, Piece.WHITE_PAWN)) pawnNearby = true;
            }

            if (pawnNearby) {
                hash ^= PolyglotConstants.RANDOM_NUMBERS[772 + file];
            }
        }

        return hash;
    }

    /**
     * Calculates the Zobrist Key (hash) for a given FEN string.
     * The hash is compatible with Polyglot opening books.
     *
     * @param fen The standard FEN string
     * @return The 64-bit Zobrist hash key.
     */
    public static long getZobristKey(String fen) {
        BoardInterface board = new ChesslibBoard();
        board.loadFromFen(fen);
        return getZobristKey(board);
    }

    private static boolean isPieceAt(BoardInterface board, int rankIndex, int fileIndex, Piece target) {
        // Find square with given rank and file.
        // Assuming Square values are ordered or we can search. 
        // Efficient way: Square.values()[rank*8 + file] usually works for A1..H8.
        // Or iteration. For 64 squares, iteration is fast enough but direct access is better.
        // Let's safely find the square.
        for (Square sq : Square.values()) {
            if (sq != Square.NONE && sq.getRank().ordinal() == rankIndex && sq.getFile().ordinal() == fileIndex) {
                return board.getPiece(sq) == target;
            }
        }
        return false;
    }

    private static int getPolyglotPieceIndex(Piece p) {
        return switch (p) {
            case BLACK_PAWN -> 0;
            case WHITE_PAWN -> 1;
            case BLACK_KNIGHT -> 2;
            case WHITE_KNIGHT -> 3;
            case BLACK_BISHOP -> 4;
            case WHITE_BISHOP -> 5;
            case BLACK_ROOK -> 6;
            case WHITE_ROOK -> 7;
            case BLACK_QUEEN -> 8;
            case WHITE_QUEEN -> 9;
            case BLACK_KING -> 10;
            case WHITE_KING -> 11;
            default -> -1;
        };
    }

    public static String toHex(long key) {
        return String.format("%016x", key);
    }
}
