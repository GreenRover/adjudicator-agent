package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.BoardInterface;
import ch.adjudicator.agent.engine.board.ChesslibBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchTest {
    @Test
    public void testNmpNodeReduction() {
        String fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3";
        int depth = 4;

        // Without NMP
        BoardInterface board1 = new ChesslibBoard();
        board1.loadFromFen(fen);
        Search search1 = new Search(board1);
        search1.setStopTime(System.currentTimeMillis() + 10000);
        search1.setEnableNmp(false);
        search1.searchRoot(depth);
        int nodes1 = search1.getNodesSearched();

        // With NMP
        BoardInterface board2 = new ChesslibBoard();
        board2.loadFromFen(fen);
        Search search2 = new Search(board2);
        search2.setStopTime(System.currentTimeMillis() + 10000);
        search2.setEnableNmp(true);
        search2.searchRoot(depth);
        int nodes2 = search2.getNodesSearched();

        System.out.println("[DEBUG_LOG] Nodes without NMP: " + nodes1);
        System.out.println("[DEBUG_LOG] Nodes with NMP: " + nodes2);
        System.out.println("[DEBUG_LOG] Reduction: " + (100.0 * (nodes1 - nodes2) / nodes1) + "%");

        assertTrue(nodes2 < nodes1, "NMP should reduce node count. Got " + nodes2 + " vs " + nodes1);
    }

    @Test
    public void testMateInOne() {
        String fen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";
        BoardInterface board = new ChesslibBoard();
        board.loadFromFen(fen);
        Search search = new Search(board);
        search.setStopTime(System.currentTimeMillis() + 5000);

        // Use findBestMove which runs iterative deepening
        com.github.bhlangonijr.chesslib.move.Move bestMove = search.findBestMove(1000);

        assertTrue(bestMove.toString().equals("h5f7"), "Should find mate in 1: Qh5xf7, found: " + bestMove);
    }
}
