package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

public class Game371RegressionTest {

    private Bitboard board;
    private Search search;

    @BeforeEach
    public void setup() {
        board = new Bitboard();
        search = new Search(board);
        setupGame();
    }

    private void setupGame() {
        String[] moves = {
            "e2e4", "b8c6", "d2d4", "g8f6", "d4d5", "c6e5", "f2f4", "e5g6", "e4e5", "f6g8",
            "b1c3", "e7e6", "d5e6", "f7e6", "a2a3", "f8c5", "g1h3", "g8e7", "c3e4", "c5b6",
            "c2c4", "a7a6", "c4c5", "b6a7", "f1d3", "b7b6", "d1h5", "c8b7", "h3g5", "e7f5",
            "g2g3", "f5e7", "g5h7", "d8c8", "e1g1", "e8d8", "e4f2", "g6f8", "h5f7", "f8h7",
            "f7g7", "h8g8", "g7h7", "b6c5", "f4f5", "e6f5", "d3c4", "d7d5", "e5d6", "c7d6",
            "c4g8", "c8c6", "h7h3", "c5c4", "g8d5", "c6d5", "c1e3", "a7e3", "h3h8", "d8c7"
        };

        for (String lan : moves) {
            Move move = parseMove(lan);
            if (move == null) {
                fail("Illegal move in setup: " + lan);
            }
            board.doMove(move);
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
    public void testAvoidMateBlunder() {
        System.out.println("FEN: " + board.getFen());

        // Search for 1 second (1000 ms)
        int bestMoveInt = search.findBestMove(1000);
        int score = search.getLastScore();
        
        // Decode move
        int from = Bitboard.getFrom(bestMoveInt);
        int to = Bitboard.getTo(bestMoveInt);
        Square fromSq = Square.values()[from];
        Square toSq = Square.values()[to];
        String bestMoveLan = fromSq.toString().toLowerCase() + toSq.toString().toLowerCase();

        System.out.println("Best Move: " + bestMoveLan);
        System.out.println("Score: " + score);

        // Assert Evaluation: The engine MUST recognize this is a lost position.
        assertThat("Score should be highly negative (lost position)", score, lessThan(-500));

        // Assert Move: The engine MUST NOT play h8a8 (Qxa8).
        assertThat("Engine blundered with Qxa8 allowing mate!", bestMoveLan, not(equalTo("h8a8")));
    }
}
