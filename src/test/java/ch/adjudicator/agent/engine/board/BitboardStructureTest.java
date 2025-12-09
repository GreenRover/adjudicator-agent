package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitboardStructureTest {

    @Test
    void testPutAndGetPiece() {
        Bitboard board = new Bitboard();
        board.putPiece(Piece.WHITE_PAWN, Square.E2);
        assertEquals(Piece.WHITE_PAWN, board.getPiece(Square.E2));
        
        board.putPiece(Piece.BLACK_QUEEN, Square.D8);
        assertEquals(Piece.BLACK_QUEEN, board.getPiece(Square.D8));
        
        // Ensure other squares are empty
        assertEquals(Piece.NONE, board.getPiece(Square.E3));
    }

    @Test
    void testRemovePiece() {
        Bitboard board = new Bitboard();
        board.putPiece(Piece.WHITE_ROOK, Square.A1);
        assertEquals(Piece.WHITE_ROOK, board.getPiece(Square.A1));
        
        board.removePiece(Square.A1);
        assertEquals(Piece.NONE, board.getPiece(Square.A1));
    }

    @Test
    void testLoadFenPiecePlacement() {
        Bitboard board = new Bitboard();
        // Start position
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        
        assertEquals(Piece.WHITE_ROOK, board.getPiece(Square.A1));
        assertEquals(Piece.WHITE_KING, board.getPiece(Square.E1));
        assertEquals(Piece.BLACK_KING, board.getPiece(Square.E8));
        assertEquals(Piece.BLACK_PAWN, board.getPiece(Square.A7));
        assertEquals(Piece.NONE, board.getPiece(Square.E4));
    }

    @Test
    void testSideToMove() {
        Bitboard board = new Bitboard();
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        assertEquals(Side.WHITE, board.getSideToMove());
        
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1");
        assertEquals(Side.BLACK, board.getSideToMove());
    }

    @Test
    void testEnPassant() {
        Bitboard board = new Bitboard();
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        assertEquals(Square.E3, board.getEnPassant());
        
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        assertEquals(Square.NONE, board.getEnPassant());
    }

    @Test
    void testGetBitboard() {
        Bitboard board = new Bitboard();
        board.putPiece(Piece.WHITE_PAWN, Square.A2);
        board.putPiece(Piece.WHITE_PAWN, Square.B2);
        
        long whitePawns = board.getBitboard(Piece.WHITE_PAWN);
        assertTrue((whitePawns & (1L << Square.A2.ordinal())) != 0);
        assertTrue((whitePawns & (1L << Square.B2.ordinal())) != 0);
        
        long whitePieces = board.getBitboard(Side.WHITE);
        assertTrue((whitePieces & (1L << Square.A2.ordinal())) != 0);
        assertTrue((whitePieces & (1L << Square.B2.ordinal())) != 0);
        
        assertEquals(0, board.getBitboard(Side.BLACK));
    }
}
