package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

class ImprovementVerificationTest {

    @Test
    void testPerformanceAndTimeoutPrevention() {
        // Based on Game 85
        Bitboard board = new Bitboard();
        // Complex middlegame FEN
        board.loadFromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");

        Search search = new Search(board);
        long startTime = System.currentTimeMillis();
        search.findBestMove(1000); // Allocate exactly 1000ms
        long duration = System.currentTimeMillis() - startTime;

        // Assertion 1: Time limit respect
        assertThat("Search took too long: " + duration + "ms (Limit: 1100ms)", duration, lessThan(1100L));

        // Assertion 2: Nodes searched (Performance check)
        // With Quiescence optimization, we expect high node count.
        // Assuming > 50k nodes for a 1s search on a modern machine is reasonable for a Java engine.
        // If this fails on a slow CI, we might need to adjust, but this verifies the generated code performs reasonably.
        int nodes = search.getNodesSearched();
        assertThat("Nodes searched (" + nodes + ") should be > 50,000 for 1s search to prove performance.", nodes, greaterThan(50000));
    }

    @Test
    void testTacticalAwarenessAndCheckEvasion() {
        // Based on Game 74
        Bitboard board = new Bitboard();
        // White King on g1, Black Queen on h2 checking. Black King on g3.
        // Only saving move is Kf1.
        board.loadFromFen("8/8/8/8/8/6k1/7q/6K1 w - - 0 1");

        Search search = new Search(board);
        // We give enough time to find the move, but it should be instant since it's the only legal move.
        // However, we want to ensure Quiescence search doesn't prune it or fail to see it.
        int bestMoveEncoded = search.findBestMove(1000);

        int from = Bitboard.getFrom(bestMoveEncoded);
        int to = Bitboard.getTo(bestMoveEncoded);

        // Expected move: g1 (6) to f1 (5)
        assertThat("Move should be from G1", from, is(Square.G1.ordinal()));
        assertThat("Move should be to F1 (Only escape)", to, is(Square.F1.ordinal()));
    }

    @Test
    void testEvaluationBishopPair() {
        // Verify Bishop Pair Bonus and Material Difference.
        // We compare two positions that are identical except for the piece types.
        // We place pieces on b2/g2 (Fianchetto-like) where Knights are less dominant than in center.
        
        // Board A: White has 2 Bishops on b2, g2.
        Bitboard boardA = new Bitboard();
        boardA.loadFromFen("k7/8/8/8/8/8/1B4B1/K7 w - - 0 1");

        // Board B: White has 2 Knights on b2, g2.
        Bitboard boardB = new Bitboard();
        boardB.loadFromFen("k7/8/8/8/8/8/1N4N1/K7 w - - 0 1");

        int scoreA = Evaluator.evaluate(boardA);
        int scoreB = Evaluator.evaluate(boardB);

        // Score A should be significantly higher than Score B.
        // Bishops (330*2) + Bonus (50) = 710 + PST (likely decent for fianchetto)
        // Knights (320*2) = 640 + PST (likely bad for edge)
        
        assertThat("Two Bishops (Board A) should score higher than Two Knights (Board B). ScoreA: " + scoreA + ", ScoreB: " + scoreB,
                scoreA, greaterThan(scoreB));
    }
}
