package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaticExchangeEvaluatorTest {

    @Test
    public void testQueenTakesProtectedPawn() {
        // White Queen at d1, Black Pawn at d4 protected by Pawn at e5
        // 4k3/8/8/4p3/3p4/8/8/3Q4 w - - 0 1
        Board board = new Board();
        board.loadFromFen("4k3/8/8/4p3/3p4/8/8/3Q4 w - - 0 1");

        Move move = new Move(Square.D1, Square.D4); // QxP
        int score = StaticExchangeEvaluator.see(board, move);

        // Q(900) takes P(100). Then P(e5) takes Q.
        // Gain: 100 - 900 = -800.
        System.out.println("QxProtectedP: " + score);
        assertTrue(score < 0, "Queen taking protected pawn should be negative");
    }

    @Test
    public void testQueenTakesUndefendedPawn() {
        // White Queen at d1, Black Pawn at d4 undefended
        Board board = new Board();
        board.loadFromFen("4k3/8/8/8/3p4/8/8/3Q4 w - - 0 1");

        Move move = new Move(Square.D1, Square.D4); // QxP
        int score = StaticExchangeEvaluator.see(board, move);

        // Gain: 100.
        System.out.println("QxUndefendedP: " + score);
        assertTrue(score > 0, "Queen taking undefended pawn should be positive");
    }
}
