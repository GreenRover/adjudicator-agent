package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.Bitboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BitboardPseudoLegalTest {

    @Test
    public void testStartPos() {
        Bitboard board = new Bitboard();
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        int[] moves = new int[256];
        int count = board.generatePseudoLegalMoves(moves);
        // 16 pawn moves (8 single, 8 double) + 4 knight moves = 20
        assertEquals(20, count, "Start position should have 20 pseudo-legal moves");
    }

    @Test
    public void testPawnCaptures() {
        Bitboard board = new Bitboard();
        // White Pawn on e4, Black Pawns on d5, f5. White to move.
        // e4 captures d5, f5. e4 pushes e5.
        // Total 3 moves for e4.
        // Kings on e1, e8.
        board.loadFromFen("4k3/8/8/3p1p2/4P3/8/8/4K3 w - - 0 1");
        int[] moves = new int[256];
        int count = board.generatePseudoLegalMoves(moves);
        // White King has 5 moves (d1, f1, d2, e2, f2).
        // White Pawn e4: capture d5, capture f5, push e5. (3 moves)
        // Total 8 moves.
        assertEquals(8, count);
    }
    
    @Test
    public void testEnPassant() {
        Bitboard board = new Bitboard();
        // White Pawn on e5. Black Pawn moves d7-d5. En passant square d6.
        // White to move. e5 can capture d6 (ep).
        // Rank 5: ... d5(p) e5(P) ... -> 3pP3
        board.loadFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        int[] moves = new int[256];
        int count = board.generatePseudoLegalMoves(moves);
        
        // Check if en passant move is generated.
        // e5 (rank 4, file 4) -> d6 (rank 5, file 3).
        // e5 is 4*8 + 4 = 36.
        // d6 is 5*8 + 3 = 43.
        int expectedMove = Bitboard.encodeMove(36, 43, 0);
        
        boolean found = false;
        for(int i=0; i<count; i++) {
            if (moves[i] == expectedMove) found = true;
        }
        assertTrue(found, "En Passant move should be generated");
    }

    @Test
    public void testPromotion() {
        Bitboard board = new Bitboard();
        // White Pawn on a7.
        board.loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        int[] moves = new int[256];
        int count = board.generatePseudoLegalMoves(moves);
        
        // a7 (rank 6, file 0) -> a8 (rank 7, file 0). 4 promotions.
        // King moves (5).
        // Total 9.
        assertEquals(9, count);
    }
}
