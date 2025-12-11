package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * This test verifies the engine understands that trapped pieces are worse than active pieces.
 */
class MobilityBonusTest {

    @Test
    void testTrappedVsActiveRook() {
        // Load Position A: White Rook on a1, blocked by pawns on a2/b1.
        // Note: Pawn on b1 is impossible for White, so using a Knight on b1 to block the rank.
        Bitboard boardA = new Bitboard();
        boardA.loadFromFen("7k/8/8/8/8/8/P7/RN5K w - - 0 1");

        // Load Position B: White Rook on d4, open files.
        // Maintaining material equality (Pawn a2, Knight b1 elsewhere or same? Let's keep Knight b1 to isolate Rook change)
        // If I move Knight to b1 in board B, it might block d4 slightly less or same?
        // Actually, let's keep the Knight on b1 in both to keep material identical and only move the Rook.
        Bitboard boardB = new Bitboard();
        boardB.loadFromFen("7k/8/8/8/3R4/8/P7/1N5K w - - 0 1");

        int scoreA = Evaluator.evaluate(boardA);
        int scoreB = Evaluator.evaluate(boardB);

        System.out.println("Score A (Trapped): " + scoreA);
        System.out.println("Score B (Active): " + scoreB);
        System.out.println("Difference: " + (scoreB - scoreA));

        // Assert that Evaluator.evaluate(boardB) is significantly higher than Evaluator.evaluate(boardA).
        // Defining "significantly" as at least a pawn's worth or substantial fraction?
        // The instructions say "significantly higher".
        // Let's assume at least 20cp difference.
        assertThat("Active Rook (B) should score significantly higher than Trapped Rook (A).",
                scoreB, greaterThan(scoreA + 20));
    }
}
