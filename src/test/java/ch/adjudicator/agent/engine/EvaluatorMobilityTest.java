package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EvaluatorMobilityTest {

    @Test
    public void testKingBlockingPenalty() {
        // Scenario A: King on e2, Queen on d1, Bishop on f1 (Bad - blocking)
        Bitboard boardA = new Bitboard();
        // Modified A (Blocking): rnbqkbnr/pppppppp/8/8/8/4P3/PPPPKPPP/RNBQ1BNR w kq - 0 1
        boardA.loadFromFen("rnbqkbnr/pppppppp/8/8/8/4P3/PPPPKPPP/RNBQ1BNR w kq - 0 1");
        int scoreA = Evaluator.evaluate(boardA);

        // Scenario B: King on e1 (Standard, Good)
        Bitboard boardB = new Bitboard();
        boardB.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        int scoreB = Evaluator.evaluate(boardB);

        // We expect Score A to be significantly lower (penalty applied)
        assertTrue(scoreA < scoreB - 30, "King blocking own pieces should be penalized significantly. A=" + scoreA + ", B=" + scoreB);
    }

    @Test
    public void testBishopMobility() {
        // Scenario C: White Bishop on a1, blocked by pawns a2, b2.
        Bitboard boardC = new Bitboard();
        // 8/8/8/8/8/8/PP6/B6K w - - 0 1 (Bishop a1, pawns a2, b2)
        boardC.loadFromFen("k7/8/8/8/8/8/PP6/B6K w - - 0 1");
        int scoreC = Evaluator.evaluate(boardC);

        // Scenario D: White Bishop on c4, open lines.
        Bitboard boardD = new Bitboard();
        // 8/8/8/8/2B5/8/PP6/7K w - - 0 1 (Bishop c4, pawns a2, b2 still there for material equality)
        boardD.loadFromFen("k7/8/8/8/2B5/8/PP6/7K w - - 0 1");
        int scoreD = Evaluator.evaluate(boardD);

        System.out.println("Score C (Trapped Bishop): " + scoreC);
        System.out.println("Score D (Active Bishop): " + scoreD);

        assertTrue(scoreD > scoreC + 10, "Active Bishop should score higher than trapped Bishop. C=" + scoreC + ", D=" + scoreD);
    }
}
