package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.ZobristHasher;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;

public class FenParser {

    public static void load(Bitboard board, String fen) {
        board.clear();
        String[] parts = fen.split(" ");
        String placement = parts[0];
        String activeColor = parts[1];
        String castling = parts[2];
        String enPassant = parts[3];

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
                    Square sq = Bitboard.SQUARES[rank * 8 + file];
                    Piece p = getPieceFromChar(c);
                    board.putPiece(p, sq);
                    file++;
                }
            }
        }

        board.sideToMove = activeColor.equals("w") ? Side.WHITE : Side.BLACK;

        if (!castling.equals("-")) {
            if (castling.contains("K")) board.castlingRights |= Bitboard.CASTLE_WK;
            if (castling.contains("Q")) board.castlingRights |= Bitboard.CASTLE_WQ;
            if (castling.contains("k")) board.castlingRights |= Bitboard.CASTLE_BK;
            if (castling.contains("q")) board.castlingRights |= Bitboard.CASTLE_BQ;
        }

        if (!enPassant.equals("-")) {
            try {
                board.enPassantSquare = Square.valueOf(enPassant.toUpperCase());
            } catch (IllegalArgumentException e) {
                board.enPassantSquare = Square.NONE;
            }
        } else {
            board.enPassantSquare = Square.NONE;
        }

        if (parts.length > 4) {
            try {
                board.halfMoveClock = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                board.halfMoveClock = 0;
            }
        }

        if (parts.length > 5) {
            try {
                board.fullMoveNumber = Integer.parseInt(parts[5]);
            } catch (NumberFormatException e) {
                board.fullMoveNumber = 1;
            }
        }

        // Full Zobrist calculation for initial position
        board.zobristHash = ZobristHasher.getZobristKey(board);
        board.initPestoScores();
    }

    private static Piece getPieceFromChar(char c) {
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
}
