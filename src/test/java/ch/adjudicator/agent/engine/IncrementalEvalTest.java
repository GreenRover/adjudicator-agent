package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IncrementalEvalTest {

    @Test
    public void testConsistency() {
        Bitboard board = new Bitboard();

        // 1. Start pos
        String startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        board.loadFromFen(startFen);
        int startScore = Evaluator.evaluate(board);
        assertEquals(0, startScore);

        // 2. Middlegame
        String middleFen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        board.loadFromFen(middleFen);
        int middleScore = Evaluator.evaluate(board);
        assertEquals(56, middleScore);

        // 3. Endgame
        String endFen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
        board.loadFromFen(endFen);
        int endScore = Evaluator.evaluate(board);
        assertEquals(0, endScore);
    }

    @Test
    public void testIncrementalUpdates() {
        Bitboard board = new Bitboard();
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        // e2e4
        com.github.bhlangonijr.chesslib.move.Move move = new com.github.bhlangonijr.chesslib.move.Move(
            com.github.bhlangonijr.chesslib.Square.E2, 
            com.github.bhlangonijr.chesslib.Square.E4
        );
        board.doMove(move);

        int incrementalScore = Evaluator.evaluate(board);

        Bitboard refBoard = new Bitboard();
        refBoard.loadFromFen(board.getFen());
        int fullScore = Evaluator.evaluate(refBoard);

        assertEquals(fullScore, incrementalScore, "Score after move e2e4 should match full evaluation");

        board.undoMove();
        int undoneScore = Evaluator.evaluate(board);
        
        Bitboard startBoard = new Bitboard();
        startBoard.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        int startScore = Evaluator.evaluate(startBoard);
        
        assertEquals(startScore, undoneScore, "Score after undo should match start");
    }
}
