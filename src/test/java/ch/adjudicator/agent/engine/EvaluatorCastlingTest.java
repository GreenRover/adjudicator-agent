package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EvaluatorCastlingTest {

    @Test
    public void testCastlingRightsValue() {
        // Create a board with castling rights for White ONLY
        // FEN: White can castle (KQ), Black cannot (-)
        Board boardWithRights = new Board();
        boardWithRights.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQ - 0 1");
        BoardStatus statusWithRights = new BoardStatus(boardWithRights);

        // Create a board without castling rights (same position)
        // FEN: Nobody can castle
        Board boardWithoutRights = new Board();
        boardWithoutRights.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1");
        BoardStatus statusWithoutRights = new BoardStatus(boardWithoutRights);

        int scoreWithRights = Evaluator.evaluate(statusWithRights);
        int scoreWithoutRights = Evaluator.evaluate(statusWithoutRights);

        System.out.println("Score with rights: " + scoreWithRights);
        System.out.println("Score without rights: " + scoreWithoutRights);

        // Expectation: Score with rights should be significantly higher
        // Now that we isolated White rights, score should increase by (45*2) = 90
        assertTrue(scoreWithRights > scoreWithoutRights + 20,
                "Position with castling rights should be valued higher than without");
    }

    @Test
    public void testBongcloudPenalty() {
        // Position A: King on e1, Pawn on e4. Castling rights intact for ALL.
        Board startBoard = new Board();
        // FEN 1: White king e1, White to move. Both sides can castle.
        startBoard.loadFromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1");
        BoardStatus startStatus = new BoardStatus(startBoard);
        int startScore = Evaluator.evaluate(startStatus);

        // Position B: King on e2, Pawn on e4. White lost rights. Black KEEPS rights.
        // FEN 2: White king e2, White to move. Black can castle (kq).
        Board bongcloudBoard = new Board();
        bongcloudBoard.loadFromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPPKPPP/RNBQ1BNR w kq - 1 2");
        BoardStatus bongcloudStatus = new BoardStatus(bongcloudBoard);
        int bongcloudScore = Evaluator.evaluate(bongcloudStatus);

        System.out.println("Start White Score (Ke1): " + startScore);
        System.out.println("Bongcloud White Score (Ke2): " + bongcloudScore);

        // Calculation with 45 bonus:
        // Start: White +90, Black +90. Net 0. King e1 (-56).
        // Bongcloud: White 0, Black +90. Net -90. King e2 (-8).
        // Net change from Start to Bongcloud: -90 (rights) + 48 (king) = -42.
        // So BongcloudScore should be approx StartScore - 42.

        assertTrue(bongcloudScore < startScore - 20,
                "Position with King on e2 (no castling) should be worse than King on e1 (castling available)");
    }
}
