package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BoardPerformanceTest {

    private static final int FEN_COUNT = 200;
    private static final int ITERATIONS = 10_000;

    @Test
    public void comparePerformance() {
        List<String> fens = generateRandomFens(FEN_COUNT);
        System.out.println("[DEBUG_LOG] Generated " + fens.size() + " FENs.");

        System.out.println("[DEBUG_LOG] Running Bitboard performance test...");
        runTest("Bitboard", new Bitboard(), fens);

        System.out.println("[DEBUG_LOG] Running ChesslibBoard performance test...");
        runTest("ChesslibBoard", new ChesslibBoard(), fens);
    }

    private void runTest(String name, BoardInterface board, List<String> fens) {
        // Warmup
        for (int i = 0; i < 10; i++) {
            for (String fen : fens) {
                board.loadFromFen(fen);
                board.legalMoves();
            }
        }

        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long startTime = System.nanoTime();
        long startMemory = getUsedMemory();

        for (String fen : fens) {
            board.loadFromFen(fen);
            for (int i = 0; i < ITERATIONS; i++) {
                board.legalMoves();
            }
        }

        long endTime = System.nanoTime();
        long endMemory = getUsedMemory();

        long duration = endTime - startTime;
        double seconds = duration / 1_000_000_000.0;

        System.out.println("[DEBUG_LOG] Results for " + name + ":");
        System.out.println("[DEBUG_LOG]   Time: " + String.format("%.4f", seconds) + " s");
        System.out.println("[DEBUG_LOG]   Memory used (approx delta): " + (endMemory - startMemory) / 1024 / 1024 + " MB");
        System.out.println("[DEBUG_LOG]   End Memory: " + endMemory / 1024 / 1024 + " MB");
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static List<String> generateRandomFens(int count) {
        List<String> fens = new ArrayList<>();
        BoardInterface board = new ChesslibBoard();
        Random random = new Random(12345); // Fixed seed for reproducibility

        while (fens.size() < count) {
            board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
            int movesToPlay = 10 + random.nextInt(40); // Play between 10 and 50 moves

            for (int i = 0; i < movesToPlay; i++) {
                List<Move> legalMoves = board.legalMoves();
                if (legalMoves.isEmpty()) {
                    break;
                }
                Move move = legalMoves.get(random.nextInt(legalMoves.size()));
                board.doMove(move);
            }
            fens.add(board.getFen());
        }
        return fens;
    }
}
