package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.BoardInterface;
import ch.adjudicator.agent.engine.ChesslibBoard;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PerftTest {

    @Test
    public void testStartPosition() {
        BoardInterface board = new ChesslibBoard();
        long nodes = perft(board, 3);
        assertEquals(8902, nodes, "Start Position Depth 3 failed");
    }

    @Test
    public void testKiwiPete() {
        BoardInterface board = new ChesslibBoard();
        board.loadFromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        long nodes = perft(board, 3);
        assertEquals(97862, nodes, "KiwiPete Depth 3 failed");
    }

    @Test
    public void testPosition3() {
        BoardInterface board = new ChesslibBoard();
        board.loadFromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        long nodes = perft(board, 3);
        assertEquals(2812, nodes, "Position 3 Depth 3 failed");
    }

    private long perft(BoardInterface board, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        List<Move> moves = board.legalMoves();
        for (Move move : moves) {
            board.doMove(move);
            nodes += perft(board, depth - 1);
            board.undoMove();
        }
        return nodes;
    }
}
