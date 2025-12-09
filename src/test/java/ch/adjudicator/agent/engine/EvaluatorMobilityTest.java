package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import ch.adjudicator.agent.engine.board.BoardInterface;
import ch.adjudicator.agent.engine.board.BoardStatus;
import ch.adjudicator.agent.engine.board.ChesslibBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EvaluatorMobilityTest {

    @Test
    public void testKingBlockingPenalty() {
        Evaluator evaluator = new Evaluator();

        // Scenario A: King on e2, Queen on d1, Bishop on f1 (Bad - blocking)
        BoardInterface boardA = new Bitboard();
        boardA.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPP1PPPP/RN1QKBNR w KQkq - 0 1");
        // Note: FEN above is close, let's just construct the specific position to be sure.
        // Actually, FEN is easier.
        // Board A: King e2, Q d1, B f1. Pawns d2, f2, e3? 
        // "King Blocking Penalty: Explicitly check if the King is on e2 (White) ... while own pieces are on d1/f1"
        // Let's make a FEN where King is on e2, Q on d1, B on f1.
        // 8/8/8/8/8/8/3PP3/RN1K1BNR w - - 0 1  (King on d1? No K is King).
        // Positions:
        // d1: Q, f1: B, e2: K. 
        // FEN: rnbqkbnr/pppppppp/8/8/8/8/PPP1PPPP/RN1QKBNR w KQkq - 0 1 is standard.
        // Let's move King to e2.
        // Standard: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
        // Modified A (Blocking): rnbqkbnr/pppppppp/8/8/8/4P3/PPPPKPPP/RNBQ1BNR w kq - 0 1
        // (Moved e2 pawn to e3, King to e2. Q is at d1, B is at f1 (standard start squares)).

        boardA.loadFromFen("rnbqkbnr/pppppppp/8/8/8/4P3/PPPPKPPP/RNBQ1BNR w kq - 0 1");
        BoardStatus statusA = new BoardStatus(boardA);
        int scoreA = evaluator.evaluate(statusA);

        // Scenario B: King on e1 (Standard, Good)
        BoardInterface boardB = new Bitboard();
        boardB.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        BoardStatus statusB = new BoardStatus(boardB);
        int scoreB = evaluator.evaluate(statusB);

        // We expect Score A to be significantly lower (penalty applied)
        // Since it's white, lower means worse? No, standard eval: Positive is good for White.
        // So Score A should be < Score B.
        // Actually, standard start is usually 0 or small positive.
        // If we penalize A, it should drop.

        assertTrue(scoreA < scoreB - 30, "King blocking own pieces should be penalized significantly. A=" + scoreA + ", B=" + scoreB);
    }

    @Test
    public void testBishopMobility() {
        Evaluator evaluator = new Evaluator();

        // Scenario C: White Bishop on a1, blocked by pawns a2, b2.
        BoardInterface boardC = new Bitboard();
        // 8/8/8/8/8/8/PP6/B7 w - - 0 1 (Bishop a1, pawns a2, b2)
        boardC.loadFromFen("k7/8/8/8/8/8/PP6/B6K w - - 0 1");
        BoardStatus statusC = new BoardStatus(boardC);
        int scoreC = evaluator.evaluate(statusC);

        // Scenario D: White Bishop on c4, open lines.
        BoardInterface boardD = new Bitboard();
        // 8/8/8/8/2B5/8/PP6/7K w - - 0 1 (Bishop c4, pawns a2, b2 still there for material equality)
        boardD.loadFromFen("k7/8/8/8/2B5/8/PP6/7K w - - 0 1");
        BoardStatus statusD = new BoardStatus(boardD);
        int scoreD = evaluator.evaluate(statusD);

        System.out.println("Score C (Trapped Bishop): " + scoreC);
        System.out.println("Score D (Active Bishop): " + scoreD);

        assertTrue(scoreD > scoreC + 10, "Active Bishop should score higher than trapped Bishop. C=" + scoreC + ", D=" + scoreD);
    }
}
