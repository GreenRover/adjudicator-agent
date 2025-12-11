package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Baseline test to ensure that as we refactor for incremental evaluation,
 * we don't break the scoring logic.
 */
class IncrementalEvalTest {

    @Test
    void testConsistency() {
        verifyFen("Start Pos", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 0);
        verifyFen("KiwiPete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 56);
        verifyFen("Middlegame 1", "r1bqk2r/pp2bppp/2n5/3p4/3P4/5N2/PP2BPPP/R1BQ1RK1 w kq - 0 10", 132);
        verifyFen("Middlegame 2", "2r2rk1/1bqnbppp/p2p1n2/1p2p3/4P3/PNN1BP2/1PPQB1PP/3R1RK1 w - - 3 15", -65);
    }

    private void verifyFen(String name, String fen, int expectedScore) {
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        int score = Evaluator.evaluate(board);
        System.out.println("[DEBUG_LOG] " + name + " Score: " + score);
        assertThat(name + " score should match expected baseline", score, is(expectedScore));
    }
}
