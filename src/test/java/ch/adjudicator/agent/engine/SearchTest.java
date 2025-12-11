package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchTest {
    @Test
    @Disabled
    public void testNmpNodeReduction() {
        String fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3";
        int depth = 4;

        // Without NMP
        Bitboard board1 = new Bitboard();
        board1.loadFromFen(fen);
        Search search1 = new Search(board1);
        search1.setStopTime(System.currentTimeMillis() + 10000);
        search1.setEnableNmp(false);
        search1.searchRoot(depth);
        int nodes1 = search1.getNodesSearched();

        // With NMP
        Bitboard board2 = new Bitboard();
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
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        Search search = new Search(board);
        search.setStopTime(System.currentTimeMillis() + 5000);

        // Use findBestMove which runs iterative deepening
        int bestMoveInt = search.findBestMove(1000);
        com.github.bhlangonijr.chesslib.Square from = com.github.bhlangonijr.chesslib.Square.values()[Bitboard.getFrom(bestMoveInt)];
        com.github.bhlangonijr.chesslib.Square to = com.github.bhlangonijr.chesslib.Square.values()[Bitboard.getTo(bestMoveInt)];
        com.github.bhlangonijr.chesslib.move.Move bestMove = new com.github.bhlangonijr.chesslib.move.Move(from, to);

        assertEquals("h5f7", bestMove.toString(), "Should find mate in 1: Qh5xf7, found: " + bestMove);
    }
}
