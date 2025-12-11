package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class AvoidRepeationTest {

    private Bitboard board;
    private Search search;

    @BeforeEach
    public void setup() {
        board = new Bitboard();
        search = new Search(board);
        setupRepetitionScenario();
    }

    private void setupRepetitionScenario() {
        // Moves: Ng1-f3, Ng8-f6, Nf3-g1, Nf6-g8
        String[] moves = { "g1f3", "g8f6", "f3g1", "f6g8" };
        for (String m : moves) {
             Move move = parseMove(m);
             if (move != null) {
                 board.doMove(move);
             } else {
                 throw new RuntimeException("Invalid move: " + m);
             }
        }
    }

    private Move parseMove(String lan) {
        List<Move> legalMoves = board.legalMoves();
        for (Move move : legalMoves) {
            if (move.toString().toLowerCase().equals(lan.toLowerCase())) {
                return move;
            }
        }
        return null;
    }

    @Test
    public void testAvoidDrawByRepetition() {
        // Run search
        int bestMoveInt = search.findBestMove(2000); 
        int score = search.getLastScore();

        int from = Bitboard.getFrom(bestMoveInt);
        int to = Bitboard.getTo(bestMoveInt);
        String bestMoveLan = Square.values()[from].toString().toLowerCase() + 
                             Square.values()[to].toString().toLowerCase();
        
        System.out.println("Best Move: " + bestMoveLan);
        System.out.println("Score: " + score);
        
        // Ng1-f3 would be the 3rd repetition.
        // Expectation: Engine avoids g1f3 because 0.00 < ~0.20 (opening advantage)
        // If the engine picks g1f3, it means it doesn't see the draw or values draw > opening.
        assertThat("Engine should not play for a draw in the opening", bestMoveLan, not(equalTo("g1f3")));
    }
}
