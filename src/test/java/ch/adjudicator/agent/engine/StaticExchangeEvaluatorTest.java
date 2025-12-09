package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaticExchangeEvaluatorTest {

    @Test
    public void testQueenTakesProtectedPawn() {
        // White Queen at d1, Black Pawn at d4 protected by Pawn at e5
        // 4k3/8/8/4p3/3p4/8/8/3Q4 w - - 0 1
        Bitboard board = new Bitboard();
        board.loadFromFen("4k3/8/8/4p3/3p4/8/8/3Q4 w - - 0 1");

        int move = Bitboard.encodeMove(Square.D1.ordinal(), Square.D4.ordinal(), 0); // QxP
        int score = StaticExchangeEvaluator.see(board, move);

        // Q(900) takes P(100). Then P(e5) takes Q.
        // Gain: 100 - 900 = -800.
        System.out.println("QxProtectedP: " + score);
        assertTrue(score < 0, "Queen taking protected pawn should be negative");
    }
}
