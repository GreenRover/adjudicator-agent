package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TacticalRegressionTest {

    private void applyMove(Bitboard board, String moveStr) {
        String fromStr = moveStr.substring(0, 2).toUpperCase();
        String toStr = moveStr.substring(2, 4).toUpperCase();
        Square from = Square.valueOf(fromStr);
        Square to = Square.valueOf(toStr);

        List<Move> legalMoves = board.legalMoves();
        Move foundMove = null;
        for (Move m : legalMoves) {
            if (m.getFrom() == from && m.getTo() == to) {
                foundMove = m;
                break;
            }
        }
        
        if (foundMove == null) {
            throw new RuntimeException("Illegal move: " + moveStr + " on board " + board.getFen());
        }
        board.doMove(foundMove);
    }

    private void setupGame(Bitboard board, String[] moves) {
        for (String move : moves) {
            applyMove(board, move);
        }
    }

    @Test
    void testScenario1_MatePrevention() {
        // Game 336
        Bitboard board = new Bitboard();
        String[] moves = {
            "g1f3", "d7d5", "b1c3", "g8f6", "d2d3", "c8f5", "c1g5", "d5d4",
            "c3b5", "c7c5", "c2c3", "a7a6", "b5a3", "b8c6", "a3c4", "e7e6",
            "g5f6", "d8f6", "d1b3", "b7b5", "a2a4", // Corrected a1a4 -> a2a4
            "a8b8", "a4b5", "a6b5", "c4a5", "d4c3", "a5c6", "c3b2", "a1d1",
            "b8c8", "c6e5", "f5d3", "b3d3"
        };
        
        setupGame(board, moves);
        
        // DEBUG BLOCK
        Bitboard debugBoard = new Bitboard();
        debugBoard.loadFromFen(board.getFen());
        Move b5b4 = null;
        for (Move m : debugBoard.legalMoves()) {
            if (m.getFrom() == Square.B5 && m.getTo() == Square.B4) {
                b5b4 = m;
                break;
            }
        }
        if (b5b4 != null) {
            debugBoard.doMove(b5b4);
            System.out.println("DEBUG: Executed b5b4. White to move. FEN: " + debugBoard.getFen());
            boolean qd7Found = false;
            for (Move m : debugBoard.legalMoves()) {
                if (m.getFrom() == Square.D3 && m.getTo() == Square.D7) {
                    System.out.println("DEBUG: Found White Move: " + m);
                    debugBoard.doMove(m);
                    if (debugBoard.isMated()) {
                        System.out.println("DEBUG: This move is CHECKMATE.");
                    } else {
                        System.out.println("DEBUG: This move is NOT checkmate. Black can play: " + debugBoard.legalMoves());
                    }
                    debugBoard.undoMove();
                    qd7Found = true;
                }
            }
            if (!qd7Found) {
                 System.out.println("DEBUG: White CANNOT play Qd7. Legal moves: " + debugBoard.legalMoves());
            }
        } else {
            System.out.println("DEBUG: b5b4 is illegal for Black!");
        }
        // END DEBUG BLOCK
        
        System.out.println("Scenario 1 FEN: " + board.getFen());
        // REGRESSION TEST DOCUMENTATION:
        // Context: Game 336. Engine previously failed to see White's threat of Qd7# because
        // the search exploded due to aggressive check extensions, causing a timeout before
        // the mate was found.
        // Critical: This test ensures the search remains efficient enough to find simple tactical
        // threats. DO NOT MODIFY the setup or assertions unless the engine's search logic
        // fundamentally changes.

        Search search = new Search(board);
        int bestMoveEncoded = search.findBestMove(5000);
        
        int from = Bitboard.getFrom(bestMoveEncoded);
        int to = Bitboard.getTo(bestMoveEncoded);
        Square fromSq = Square.values()[from];
        Square toSq = Square.values()[to];
        String moveStr = fromSq.name().toLowerCase() + toSq.name().toLowerCase();
        
        System.out.println("Scenario 1 Best Move: " + moveStr);
        
        assertThat("Black MUST NOT play b5b4 which allows Qd7#", moveStr, not(is("b5b4")));
    }

    @Test
    void testScenario2_MateDefense() {
        // Game 332
        Bitboard board = new Bitboard();
        String[] moves = {
            "g1f3", "d7d5", "e2e3", "c8g4", "f1e2", "g4f3", "e2f3", "e7e5",
            "e1g1", "e5e4", "f3g4", "g8f6", "b1c3", "f8b4", "g4f5", "b4c3",
            "b2c3", "g7g6", "f5g4", "b8c6", "c1a3", "h7h5", "g4h3", "f6h7",
            "f2f4", "e4f3", "d1f3", "h7g5", "f3f4", "h5h4", "a1b1", "a8b8",
            "h3g4", "h4h3", "g2g3", "b7b5", // Corrected b2b5 -> b7b5
            "d2d4", "f7f5", "g4e2", "g5e4", "e2b5", "b8b5", "b1b5", "e4c3",
            "b5d5", "d8d5", "f1f2", "e8d7", "a3b2", "h8b8", "b2c1", "b8b1"
        };
        
        setupGame(board, moves);
        
        // DEBUG BLOCK
        Bitboard debugBoard = new Bitboard();
        debugBoard.loadFromFen(board.getFen());
        Move f2f1 = null;
        for (Move m : debugBoard.legalMoves()) {
            if (m.getFrom() == Square.F2 && m.getTo() == Square.F1) {
                f2f1 = m;
                break;
            }
        }
        if (f2f1 != null) {
            debugBoard.doMove(f2f1);
            System.out.println("DEBUG: Executed f2f1. Black to move. FEN: " + debugBoard.getFen());
            boolean qg2Found = false;
            for (Move m : debugBoard.legalMoves()) {
                 if (m.getTo() == Square.G2) {
                     System.out.println("DEBUG: Found Black Capture on g2: " + m);
                     debugBoard.doMove(m);
                     if (debugBoard.isMated()) {
                         System.out.println("DEBUG: This move is CHECKMATE.");
                     } else {
                         System.out.println("DEBUG: This move is NOT checkmate.");
                     }
                     debugBoard.undoMove();
                     qg2Found = true;
                 }
            }
             if (!qg2Found) {
                 System.out.println("DEBUG: Black CANNOT capture on g2.");
            }
        }
        // END DEBUG BLOCK
        
        System.out.println("Scenario 2 FEN: " + board.getFen());
        // REGRESSION TEST DOCUMENTATION:
        // Context: Game 332. Engine played f2f1, walking into Qg2#.
        // Reason: Similar search explosion or blindness to the opponent's immediate reply.
        // Critical: Keep this test to ensure the engine considers the opponent's forceful replies
        // even when not in check initially.

        Search search = new Search(board);
        int bestMoveEncoded = search.findBestMove(5000);
        
        int from = Bitboard.getFrom(bestMoveEncoded);
        int to = Bitboard.getTo(bestMoveEncoded);
        Square fromSq = Square.values()[from];
        Square toSq = Square.values()[to];
        String moveStr = fromSq.name().toLowerCase() + toSq.name().toLowerCase();
        
        System.out.println("Scenario 2 Best Move: " + moveStr);
        
        assertThat("White MUST NOT play f2f1 which allows Qg2#", moveStr, not(is("f2f1")));
    }

    @Test
    public void testMinorPieceMobility() {
        // --- Scenario A: Trapped Knight ---
        // White King on g1, blocked by own pawns g3/h2.
        // White Knight on h1 (completely trapped, 0 squares).
        // FEN: 7k/8/8/8/8/6P1/7P/6NK w - - 0 1
        Bitboard boardTrapped = new Bitboard();
        boardTrapped.loadFromFen("7k/8/8/8/8/6P1/7P/6NK w - - 0 1");

        // --- Scenario B: Active Knight ---
        // Same material. Knight on d4 (central, 8 squares).
        // FEN: 7k/8/8/8/3N4/6P1/7P/6K1 w - - 0 1
        Bitboard boardActive = new Bitboard();
        boardActive.loadFromFen("7k/8/8/8/3N4/6P1/7P/6K1 w - - 0 1");

        int scoreTrapped = Evaluator.evaluate(boardTrapped);
        int scoreActive = Evaluator.evaluate(boardActive);

        System.out.println("Score Trapped: " + scoreTrapped);
        System.out.println("Score Active: " + scoreActive);

        // Without mobility, the difference is negligible (just PeSTO square bonus).
        // With mobility, we expect a solid bonus (>25cp).
        assertThat("Active Knight should score higher due to mobility",
                scoreActive, greaterThan(scoreTrapped + 25));
    }

    /**
     * REGRESSION TEST: 7th Rank Pawn Blindness
     * * Issue: In Game 158, the engine ignored a pawn push to the 7th rank because it was
     * a "Quiet Move" (non-capture). The Late Move Reduction (LMR) logic pruned the search
     * depth, causing the engine to miss that the VERY NEXT move was a promotion.
     * * Objective: Ensure the search recognizes 'Pawn to 7th Rank' as a critical tactical
     * move that must NOT be reduced and should likely be extended.
     */
    @Test
    void testPawnPushTo7thDetection() {
        // FEN: White Pawn a6, King a1. Black King blocks path.
        // Winning/Drawing move is a6-a7 (force action).
        // Losing/Bad move is Ka1-b1 (delusion that a6 is safe).
        Bitboard board = new Bitboard();
        board.loadFromFen("k7/8/P7/8/8/8/8/K7 w - - 0 1");

        Search search = new Search(board);
        // Short search time to verify ordering/eval logic works quickly
        int bestMoveEncoded = search.findBestMove(1000);

        int from = Bitboard.getFrom(bestMoveEncoded);
        int to = Bitboard.getTo(bestMoveEncoded);
        String moveStr = Square.values()[from].toString().toLowerCase() + Square.values()[to].toString().toLowerCase();
        
        System.out.println("Engine played: " + moveStr);

        // Assert: Engine must play a6a7
        assertThat("Engine should prioritize pawn push a6a7 over passive king moves", 
            moveStr, is("a6a7"));
    }
}
