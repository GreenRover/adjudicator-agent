package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;

public class ZobristHasher {

    /**
     * Calculates the Zobrist Key (hash) for a given FEN string.
     * The hash is compatible with Polyglot opening books.
     *
     * @param fen The standard FEN string
     * @return The 64-bit Zobrist hash key.
     */
    public static long getZobristKey(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        long hash = 0L;

        // 1. Pieces
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            Piece piece = board.getPiece(sq);
            if (piece != Piece.NONE) {
                int polyglotPiece = getPolyglotPieceIndex(piece);
                // Polyglot square index: Rank * 8 + File (Rank 0-7, File 0-7)
                // A1 is 0, H8 is 63.
                int squareIndex = sq.getRank().ordinal() * 8 + sq.getFile().ordinal();
                
                if (polyglotPiece >= 0) {
                    hash ^= PolyglotConstants.RANDOM_NUMBERS[polyglotPiece * 64 + squareIndex];
                }
            }
        }

        String[] parts = fen.split(" ");
        
        // 2. Side to move
        String side = parts[1];
        if ("w".equals(side)) {
            hash ^= PolyglotConstants.RANDOM_NUMBERS[780];
        }

        // 3. Castling
        String castling = parts[2];
        if (castling.contains("K")) hash ^= PolyglotConstants.RANDOM_NUMBERS[768];
        if (castling.contains("Q")) hash ^= PolyglotConstants.RANDOM_NUMBERS[769];
        if (castling.contains("k")) hash ^= PolyglotConstants.RANDOM_NUMBERS[770];
        if (castling.contains("q")) hash ^= PolyglotConstants.RANDOM_NUMBERS[771];

        // 4. En Passant
        String enPas = parts[3];
        if (!"-".equals(enPas)) {
            char fileChar = enPas.charAt(0); 
            char rankChar = enPas.charAt(1); 
            int file = fileChar - 'a';
            int rank = rankChar - '1'; // 0-based rank of the EP target square
            
            boolean pawnNearby = false;
            
            // En Passant hash is only applied if a pawn of the attacking side is in position to capture.
            // If target is Rank 3 (index 2), it means White moved P-e4. Attacker is Black on Rank 4 (index 3).
            if (rank == 2) { 
                // Check Rank 4 (index 3) for Black Pawn
                if (file > 0 && isPieceAt(board, 3, file - 1, Piece.BLACK_PAWN)) pawnNearby = true;
                if (file < 7 && isPieceAt(board, 3, file + 1, Piece.BLACK_PAWN)) pawnNearby = true;
            } else if (rank == 5) { // Target Rank 6 (index 5)
                // If target is Rank 6, it means Black moved p-e5. Attacker is White on Rank 5 (index 4).
                if (file > 0 && isPieceAt(board, 4, file - 1, Piece.WHITE_PAWN)) pawnNearby = true;
                if (file < 7 && isPieceAt(board, 4, file + 1, Piece.WHITE_PAWN)) pawnNearby = true;
            }
            
            if (pawnNearby) {
                hash ^= PolyglotConstants.RANDOM_NUMBERS[772 + file];
            }
        }

        return hash;
    }

    private static boolean isPieceAt(Board board, int rankIndex, int fileIndex, Piece target) {
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
        switch(p) {
            case BLACK_PAWN: return 0;
            case WHITE_PAWN: return 1;
            case BLACK_KNIGHT: return 2;
            case WHITE_KNIGHT: return 3;
            case BLACK_BISHOP: return 4;
            case WHITE_BISHOP: return 5;
            case BLACK_ROOK: return 6;
            case WHITE_ROOK: return 7;
            case BLACK_QUEEN: return 8;
            case WHITE_QUEEN: return 9;
            case BLACK_KING: return 10;
            case WHITE_KING: return 11;
            default: return -1;
        }
    }

    public static String toHex(long key) {
        return String.format("%016x", key);
    }
}
