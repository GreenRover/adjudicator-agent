package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.BoardInterface;
import ch.adjudicator.agent.engine.board.BoardStatus;
import ch.adjudicator.agent.engine.board.ChesslibBoard;
import com.github.bhlangonijr.chesslib.Board;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EvaluatorStructureTest {

    @Test
    public void testPawnStructure() {
        // Position A: Connected passed pawns (d4, e4)
        // Black king far away
        BoardInterface boardA = new ChesslibBoard();
        boardA.loadFromFen("4k3/8/8/8/3PP3/8/8/4K3 w - - 0 1");
        BoardStatus statusA = new BoardStatus(boardA);
        int scoreA = Evaluator.evaluate(statusA);

        // Position B: Isolated pawns (a4, h4) - PeSTO might favor center pawns, so this might pass even without structure eval.
        // Let's use Doubled pawns to be sure.
        // Position B: Doubled pawns (a3, a4)
        BoardInterface boardB = new ChesslibBoard();
        boardB.loadFromFen("4k3/8/8/8/P7/P7/8/4K3 w - - 0 1");
        BoardStatus statusB = new BoardStatus(boardB);
        int scoreB = Evaluator.evaluate(statusB);

        System.out.println("Score A (Connected): " + scoreA);
        System.out.println("Score B (Doubled): " + scoreB);

        assertTrue(scoreA > scoreB, "Connected pawns should be valued higher than doubled pawns");

        // Position C: Isolated Pawn (d4) vs Connected (d4, e4)
        // Let's compare Isolated vs Normal
        // Isolated: d4, no c/e pawns.
        Board boardC = new Board();
        boardC.loadFromFen("4k3/8/8/8/3P4/8/8/4K3 w - - 0 1");
        // This is just one pawn.

        // Let's try to isolate one.
        // Pos D: a4, c4 (b-file open, so isolated?) No, a4 is isolated if no b-pawns. c4 is isolated if no b/d pawns.
        // "4k3/8/8/8/P1P5/8/8/4K3 w - - 0 1" -> a4 and c4 are both isolated.
        BoardInterface boardD = new ChesslibBoard();
        boardD.loadFromFen("4k3/8/8/8/P1P5/8/8/4K3 w - - 0 1");
        BoardStatus statusD = new BoardStatus(boardD);
        int scoreD = Evaluator.evaluate(statusD);

        // Pos E: Connected a4, b4
        BoardInterface boardE = new ChesslibBoard();
        boardE.loadFromFen("4k3/8/8/8/PP6/8/8/4K3 w - - 0 1");
        BoardStatus statusE = new BoardStatus(boardE);
        int scoreE = Evaluator.evaluate(statusE);

        System.out.println("Score E (Connected a/b): " + scoreE);
        System.out.println("Score D (Isolated a/c): " + scoreD);

        assertTrue(scoreE > scoreD, "Connected pawns should be better than isolated pawns");
    }

    @Test
    public void testPassedPawnBonus() {
        // Passed pawn on 6th rank vs 2nd rank
        BoardInterface boardA = new ChesslibBoard();
        boardA.loadFromFen("4k3/8/4P3/8/8/8/8/4K3 w - - 0 1"); // e6 passed

        BoardInterface boardB = new ChesslibBoard();
        boardB.loadFromFen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"); // e2 passed (start pos)

        BoardStatus statusA = new BoardStatus(boardA);
        BoardStatus statusB = new BoardStatus(boardB);

        int scoreA = Evaluator.evaluate(statusA);
        int scoreB = Evaluator.evaluate(statusB);

        System.out.println("Score Passed Rank 6: " + scoreA);
        System.out.println("Score Passed Rank 2: " + scoreB);

        assertTrue(scoreA > scoreB + 50, "Advanced passed pawn should have significant bonus");
    }
}
