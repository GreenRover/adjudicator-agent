package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that Quiescence Search handles check evasions correctly.
 * Specifically, it ensures the engine does not "Stand Pat" (return static eval)
 * when in check, as this leads to mate blindness.
 */
public class QuiescenceCheckTest {

    @Test
    public void testCheckEvasionInQSearch() throws Exception {
        // FEN: White K on a1, Q on a8. Black R on b2, R on e1.
        // White to move.
        // White is in check by Re1.
        // Rb2 controls the escape squares b1, b2.
        // Ka2 is attacked by Rb2.
        // So White is Mated.
        // White has a Queen (material advantage).
        // If QSearch ignores check evasion (and finds no loud moves), it will return static eval (~900).
        // Correct behavior: recognize mate (score < -30000 or similar, specifically alpha).
        
        String fen = "Q6Q/1r6/8/8/8/8/1r6/K3r3 w - - 0 1";
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);

        // Verify we are in check
        assertTrue(board.isKingAttacked(), "White King should be in check");

        Search search = new Search(board);
        search.setStopTime(System.currentTimeMillis() + 10000);
        
        // Use reflection to access private quiescence method
        Method quiescence = Search.class.getDeclaredMethod("quiescence", int.class, int.class, int.class, int.class);
        quiescence.setAccessible(true);

        int alpha = -30000;
        int beta = 30000;
        int ply = 0;
        int qsDepth = 0;

        int score = (int) quiescence.invoke(search, alpha, beta, ply, qsDepth);

        System.out.println("[DEBUG_LOG] QSearch Score: " + score);

        // If bug exists, it returns static eval ( > 0 because of 2 Queens).
        // If fixed, it detects Mate ( < -10000 ).
        assertTrue(score < -10000, "QSearch should detect mate. Score: " + score);
    }
}
