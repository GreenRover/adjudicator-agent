package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.Bitboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BitboardCastlingTest {

    @Test
    public void testCastlingAvailable() {
        Bitboard board = new Bitboard();
        board.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        int[] moves = new int[256];
        int count = board.generateLegalMoves(moves);
        
        boolean oo = false;
        boolean ooo = false;
        for (int i=0; i<count; i++) {
            int m = moves[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;
            if (from == 4 && to == 6) oo = true;
            if (from == 4 && to == 2) ooo = true;
        }
        assertTrue(oo, "White O-O should be available");
        assertTrue(ooo, "White O-O-O should be available");
    }

    @Test
    public void testCastlingBlockedByPathAttack() {
        Bitboard board = new Bitboard();
        // White King e1. Black Rook on d8 attacks d1.
        // O-O-O (e1->c1) passes through d1.
        board.loadFromFen("3r4/8/8/8/8/8/8/R3K3 w Q - 0 1");
        int[] moves = new int[256];
        int count = board.generateLegalMoves(moves);
        
        boolean ooo = false;
        for (int i=0; i<count; i++) {
            int m = moves[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;
            if (from == 4 && to == 2) ooo = true;
        }
        assertFalse(ooo, "White O-O-O should be blocked by attack on d1");
    }

    @Test
    public void testCastlingBlockedByCheck() {
        Bitboard board = new Bitboard();
        // White King e1. Black Rook e8. King in check.
        board.loadFromFen("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        int[] moves = new int[256];
        int count = board.generateLegalMoves(moves);
        
        boolean oo = false;
        boolean ooo = false;
        for (int i=0; i<count; i++) {
            int m = moves[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;
            if (from == 4 && to == 6) oo = true;
            if (from == 4 && to == 2) ooo = true;
        }
        assertFalse(oo, "White O-O blocked by check");
        assertFalse(ooo, "White O-O-O blocked by check");
    }
}
