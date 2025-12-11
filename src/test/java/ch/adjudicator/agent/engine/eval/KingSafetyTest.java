package ch.adjudicator.agent.engine.eval;

import ch.adjudicator.agent.engine.board.Bitboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KingSafetyTest {

    @Test
    public void testPawnShieldBonus() {
        // White King on g1 (index 6).
        // Pawns on f2, g2, h2.
        Bitboard board = new Bitboard();
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"); // Start pos
        
        // In start pos, King is on e1. Let's move it to g1 and pawns to f2, g2, h2.
        // FEN with King on g1 and pawns shield:
        // "rnbqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1RK1 w kq - 0 1" (Castled short)
        board.loadFromFen("rnbqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1RK1 w kq - 0 1");
        
        long score = KingSafety.evaluate(board, true);
        int mg = (int) (score >>> 32);
        
        // Shield bonus: 3 files (f, g, h). King on g1 (file 6). Files checked: 5, 6, 7.
        // Pawns on f2, g2, h2 should be present.
        // Bonus per pawn shield: MG_PAWN_SHIELD_BONUS = 10.
        // 3 pawns = 30.
        
        // Wait, start pos pawns are on rank 2.
        // King on g1. Rank 0.
        // BackRank check = true.
        // Files 5, 6, 7 check.
        // Pawns at f2 (13), g2 (14), h2 (15).
        // They are present.
        
        // Also need to account for other penalties.
        // King on g1: file 6. Not center (3,4).
        // Open file penalty? Own pawns on file 6 mask? Yes, g2 is there.
        
        // So expected score roughly positive or at least not negative due to shield.
        
        assertTrue(mg > 0, "King with pawn shield should have positive MG safety score (or at least better than exposed)");
    }
    
    @Test
    public void testExposedKingPenalty() {
        // King on g1, no pawns in front.
        Bitboard board = new Bitboard();
        board.loadFromFen("rnbqkb1r/pppppppp/8/8/8/8/8/RNBQ1RK1 w kq - 0 1");
        
        long score = KingSafety.evaluate(board, true);
        int mg = (int) (score >>> 32);
        
        // Missing shield penalty for f, g, h.
        // 3 * MG_MISSING_PAWN_SHIELD_PENALTY (-20) = -60.
        // Open file penalty for g-file? No pawns on g-file -> -25.
        // Total -85.
        
        assertTrue(mg < -50, "Exposed king should have significant penalty");
    }
}
