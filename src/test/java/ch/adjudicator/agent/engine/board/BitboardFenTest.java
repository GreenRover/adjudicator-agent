package ch.adjudicator.agent.engine.board;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BitboardFenTest {

    @Test
    void testFenRoundTripStartPos() {
        String startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        Bitboard board = new Bitboard();
        board.loadFromFen(startFen);
        assertEquals(startFen, board.getFen(), "Start position FEN mismatch");
    }

    @Test
    void testFenRoundTripE4() {
        // e4 played: White pawn on e4, en passant target on e3 (if double push), active color Black
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        assertEquals(fen, board.getFen(), "e4 position FEN mismatch");
    }

    @Test
    void testFenRoundTripCastling() {
        // Kings and Rooks only, various castling rights
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w kq - 5 10";
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        assertEquals(fen, board.getFen());
    }
    
    @Test
    void testFenRoundTripNoCastling() {
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1";
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        assertEquals(fen, board.getFen());
    }

    @Test
    void testFenComplex() {
        // Just a random messy position
        String fen = "r1b1k1nr/p2p1pNp/n2B4/1p1NP2P/6P1/3P1Q2/P1P1K3/q5b1 b kq - 0 1";
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        assertEquals(fen, board.getFen());
    }
}
