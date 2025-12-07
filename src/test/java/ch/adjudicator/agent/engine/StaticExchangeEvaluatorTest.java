package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StaticExchangeEvaluatorTest {

    @Test
    public void testQueenTakesProtectedPawn() {
        // White Queen at d1, Black Pawn at d4 protected by Pawn at e5
        // 4k3/8/8/4p3/3p4/8/8/3Q4 w - - 0 1
        Board board = new Board();
        board.loadFromFen("4k3/8/8/4p3/3p4/8/8/3Q4 w - - 0 1");
        
        Move move = new Move(Square.D1, Square.D4); // QxP
        int score = StaticExchangeEvaluator.see(board, move);
        
        // Q(900) takes P(100). Then P(e5) takes Q.
        // Gain: 100 - 900 = -800.
        System.out.println("QxProtectedP: " + score);
        assertTrue(score < 0, "Queen taking protected pawn should be negative");
    }

    @Test
    public void testQueenTakesUndefendedPawn() {
        // White Queen at d1, Black Pawn at d4 undefended
        Board board = new Board();
        board.loadFromFen("4k3/8/8/8/3p4/8/8/3Q4 w - - 0 1");
        
        Move move = new Move(Square.D1, Square.D4); // QxP
        int score = StaticExchangeEvaluator.see(board, move);
        
        // Gain: 100.
        System.out.println("QxUndefendedP: " + score);
        assertTrue(score > 0, "Queen taking undefended pawn should be positive");
    }

    @Test
    public void testComplexExchange() {
        // White: R(e1), B(e2)
        // Black: N(e4), P(d5) protecting N
        // Exchange on e4.
        // 4k3/8/8/3p4/4n3/8/4B3/4R3 w - - 0 1
        // White BxN(320). Black PxB(330). White RxP(100)? No, P is on d5.
        // Sequence: 
        // 1. Bxe4 (Captures N=320). Gain=[320].
        // 2. dxe4 (Captures B=330). Gain=[320, 330-320=10].
        // 3. Rxe4+ (Captures P=100). Gain=[320, 10, 100-10=90]. 
        // Wait, gain array logic in code:
        // d=0: valCaptured (N=320).
        // d=1: valCaptured (B=330) - gain[0](320) = 10? No.
        // My code: gain[d] = valCaptured - gain[d-1].
        // d=1 (Black recaptures): valCaptured is now B(330). gain[1] = 330 - 320 = 10.
        // d=2 (White recaptures): valCaptured is now P(100). gain[2] = 100 - 10 = 90.
        // Backprop:
        // gain[1] = -max(-10, 90) = -90.
        // gain[0] = -max(-320, -90) = 90.
        // Result: 90.
        // This means White wins material (N+P vs B = 320+100 - 330 = 90). Correct.
        
        Board board = new Board();
        board.loadFromFen("4k3/8/8/3p4/4n3/8/4B3/4R3 w - - 0 1");
        Move move = new Move(Square.E2, Square.E4);
        int score = StaticExchangeEvaluator.see(board, move);
        
        System.out.println("Complex Exchange: " + score);
        assertEquals(90, score, "Expected gain of 90 (N+P - B)");
    }
    
    @Test
    public void testXRay() {
        // White R at a1, Q at a2. Black R at a8.
        // P at a7? No, let's say capture on a6.
        // White Q(a2) takes P(a6). Black R(a8) takes Q. White R(a1) takes R.
        // r7/8/p7/8/8/8/Q7/R7 w - - 0 1
        Board board = new Board();
        board.loadFromFen("r7/8/p7/8/8/8/Q7/R7 w - - 0 1");
        Move move = new Move(Square.A2, Square.A6);
        
        // QxP (100).
        // RxQ (900). Gain = 900 - 100 = 800 (for Black).
        // RxR (500). Gain = 500 - 800 = -300 (for White).
        // Backprop:
        // gain[2] = -300.
        // gain[1] = -max(-800, -300) = 300? No.
        // gain[1] is score for Black.
        // My code: gain[d] is score relative to side to move at depth d.
        // d=0 (White moves): 100.
        // d=1 (Black moves): 900 - 100 = 800.
        // d=2 (White moves): 500 - 800 = -300.
        // Backprop:
        // d=2: gain[1] = -max(-800, -300) = 300. (Black chooses between standing pat (-800 relative to him? No. gain[1] is his score if he captures? No).
        
        // Let's trace backprop.
        // gain[d] is the score if we STOP at depth d (after capture d).
        // gain[0] = 100 (White has P).
        // gain[1] = 800 (Black has Q-P = 800).
        // gain[2] = -300 (White has R-Q+P = 500-900+100 = -300).
        
        // d=2: Black decides at d=1 whether to capture (go to d=2) or stand pat (stay at d=1).
        // Black wants to MAXIMIZE his score (gain[1]).
        // If he captures, outcome is -gain[2] (relative to him)?
        // Wait, standard SEE formula:
        // gain[d] = val - gain[d-1]. This is relative score.
        // If I stop at d, my score is gain[d].
        // If I continue to d+1, my score is -gain[d+1]?
        // Correct.
        
        // Backprop:
        // gain[d-1] = -max(-gain[d-1], gain[d])
        // d=2: gain[1] = -max(-800, -300) = 300.
        // Meaning: Black compares -800 (standing pat for him? No, -gain[1] is -800. So standing pat gives 800? No.)
        
        // Let's check logic:
        // `gain[d] = valCaptured - gain[d-1]`
        // gain[0] = 100. (Score for White)
        // gain[1] = 900 - 100 = 800. (Score for Black)
        // gain[2] = 500 - 800 = -300. (Score for White)
        
        // Propagate:
        // `gain[d-1] = -max(-gain[d-1], gain[d])`
        // d=2:
        // gain[1] = -max(-800, -300).
        // max(-800, -300) = -300.
        // gain[1] = 300.
        // Interpretation:
        // gain[1] was 800 (Black's score).
        // Option 1: Stand pat. Score for Black is 800.
        // Option 2: Capture. Score for White becomes -300. So Score for Black is 300?
        // Wait. If White score is -300, Black score is +300.
        // So Black compares 800 vs 300. He prefers 800 (Stand pat).
        // So he should NOT capture.
        // But my formula `gain[d-1] = -max(-gain[d-1], gain[d])` gave 300.
        // Why?
        // `-gain[d-1]` = -800.
        // `gain[d]` = -300.
        // max(-800, -300) = -300.
        // Result 300.
        // It selected the capture path (300) instead of stand pat (800).
        // The signs seem inverted for `stand pat`.
        
        // Standard SEE:
        // The value in `gain` array is "score achieved so far".
        // `gain[d]` is the score for the side that just moved.
        // So `gain[1]` (800) is Black's score.
        // At d=1, Black has moved.
        // Now it's White's turn (d=2).
        // If White captures, score becomes `gain[2]` (-300) for White.
        // Which means +300 for Black.
        // Black compares `gain[1]` (800) vs "Result of d=2" (+300).
        // Black chooses 800.
        
        // My code: `gain[d-1] = -max(-gain[d-1], gain[d])`
        // At d=2 (White just moved), we are deciding for Black (at d=1).
        // Black compares:
        //  - Standing pat at d=1: Score is gain[1] = 800.
        //  - Allowing capture at d=2: Score for White is gain[2] = -300.
        //    So score for Black is -(-300) = 300.
        // We want min(-score_for_opponent) or max(score_for_me).
        // `gain` array stores score for the side that just moved.
        // So gain[1] is Black's score.
        // gain[2] is White's score.
        // Black wants max(gain[1], -gain[2]).
        // max(800, 300) = 800.
        // So correct propagated value at d=1 should be 800.
        
        // My formula: `gain[d-1] = -max(-gain[d-1], gain[d])`
        // d=2: gain[1] = -max(-800, -300) = 300.  <-- WRONG.
        
        // Let's re-verify the formula.
        // `gain` array usually stores "Material gain for side to move".
        // Reference: https://www.chessprogramming.org/Static_Exchange_Evaluation
        // "gain[d] = value - gain[d-1]"
        // "If (gain[d] < 0) break" ?
        // Propagate:
        // "gain[d-1] = -max(-gain[d-1], gain[d])"
        
        // Let's check signs.
        // d=0: 100. (White)
        // d=1: 800. (Black)
        // d=2: -300. (White)
        
        // Backprop from d=2 to d=1:
        // We are at node 1 (Black just moved). White is considering to move to 2.
        // White wants to minimize Black's score? Or maximize White's score?
        // White chooses between Standing Pat (result is gain[1] for Black => -gain[1] for White?)
        // No, `gain` values are always from perspective of the side that just moved.
        // So gain[1] (800) is Black's advantage.
        // If White captures (to d=2), result is gain[2] (-300) for White.
        // White compares:
        //  - Stand pat: Score for White is -gain[1] = -800.
        //  - Capture: Score for White is gain[2] = -300.
        // White maximizes: max(-800, -300) = -300.
        // So White WILL capture.
        // So the score at d=1 should become -(-300) = 300 (for Black).
        // So Black ends up with 300 if White plays optimally.
        // (Wait, why did I think Black chooses? Black moved at d=1. Now it's White's turn.)
        // Yes, White chooses.
        // White chooses capture (-300) over stand pat (-800).
        // So the resulting value for node 1 (Black) is 300.
        
        // Now backprop from d=1 to d=0:
        // We are at node 0 (White just moved). Black is considering to move to 1.
        // Black compares:
        //  - Stand pat: Score for Black is -gain[0] = -100.
        //  - Capture: Score for Black is gain[1] (which is now 300).
        // Black maximizes: max(-100, 300) = 300.
        // So Black WILL capture.
        // Result for White (at d=0) is -300.
        
        // So final result should be -300.
        // My formula:
        // d=2: gain[1] = -max(-800, -300) = 300. (Correct so far)
        // d=1: gain[0] = -max(-100, 300) = -300. (Correct)
        
        // So my manual trace was confused, but formula seems correct for Minimax.
        // The scenario:
        // White QxP (White +100).
        // Black RxQ (Black +800).
        // White RxR (White -300).
        // White *should* play RxR because -300 is better than -800 (letting Black keep Q for P).
        // Black *should* play RxQ because +300 (after White recaptures) is better than -100 (letting White keep P).
        // So the sequence QxP, RxQ, RxR is forced.
        // Final score for White: -300.
        // Which is correct: White lost Q(900), got P(100)+R(500) = 600. Net -300.
        
        int score = StaticExchangeEvaluator.see(board, move);
        System.out.println("XRay Exchange: " + score);
        assertEquals(-300, score, "Expected -300 (Q vs P+R)");
    }
}
