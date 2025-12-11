package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * This test acts as a regression safeguard for the Bitboard optimization,
 * ensuring putPiece and removePiece math matches Evaluator exactly.
 */
public class IncrementalSanityTest {

    @Test
    public void testRandomGameConsistency() {
        Bitboard board = new Bitboard();
        // Load initial position
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        Random random = new Random(12345L); // Fixed seed for reproducibility

        // Initial check
        int currentMg = board.getMgPestoScore();
        int expectedMg = Evaluator.calculateMgScoreFromScratch(board);
        assertThat("MG score mismatch at start", currentMg, is(expectedMg));
        
        int currentEg = board.getEgPestoScore();
        int expectedEg = Evaluator.calculateEgScoreFromScratch(board);
        assertThat("EG score mismatch at start", currentEg, is(expectedEg));

        for (int i = 0; i < 50; i++) {
            List<Move> moves = board.legalMoves();
            if (moves.isEmpty()) {
                break; // Game over
            }

            Move move = moves.get(random.nextInt(moves.size()));
            board.doMove(move);

            currentMg = board.getMgPestoScore();
            expectedMg = Evaluator.calculateMgScoreFromScratch(board);
            
            assertThat("MG score mismatch at move " + (i + 1) + " " + move, currentMg, is(expectedMg));

            currentEg = board.getEgPestoScore();
            expectedEg = Evaluator.calculateEgScoreFromScratch(board);
            
            assertThat("EG score mismatch at move " + (i + 1) + " " + move, currentEg, is(expectedEg));
        }
    }
}
