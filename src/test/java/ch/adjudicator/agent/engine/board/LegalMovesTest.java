package ch.adjudicator.agent.engine.board;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalMovesTest {

    @ParameterizedTest
    @MethodSource("provideFens")
    void testLegalMovesChesslib(String fen, boolean expectedHasMoves) {
        BoardInterface board = new ChesslibBoard();
        board.loadFromFen(fen);
        boolean hasMoves = !board.legalMoves().isEmpty();
        assertEquals(expectedHasMoves, hasMoves, "Failed for FEN: " + fen);
    }

    @ParameterizedTest
    @MethodSource("provideFens")
    void testLegalMovesBitboard(String fen, boolean expectedHasMoves) {
        BoardInterface board = new Bitboard();
        board.loadFromFen(fen);
        boolean hasMoves = !board.legalMoves().isEmpty();
        assertEquals(expectedHasMoves, hasMoves, "Failed for FEN: " + fen);
    }

    private static Stream<Object[]> provideFens() {
        List<Object[]> fens = new ArrayList<>();

        // --- Generate No Moves (Mates & Stalemates) ---
        
        // 1. Vertical Mates (White mates Black) -> No moves for Black
        addVerticalMates(fens, "k", "Q", "K", "b", false);

        // 2. Vertical Mates (Black mates White) -> No moves for White
        addInvertedVerticalMates(fens, "k", "q", "K", "w", false);
        
        // 3. Stalemate (Black to move, King trapped in corner by Queen, not in check)
        // White King at f7, White Queen at g6, Black King at h8.
        // WQ attacks h7, g7, g8, f7... 
        // BK at h8 cannot go to h7 (WQ), g8 (WQ), g7 (WQ).
        // Is BK in check? No (h8 not attacked by g6 Q directly? Diagonal g6-h7-? No. g6-h5. g6-f5. g6-f7. g6-h6? No. g6 attacks row 6, col g, and diagonals.
        // g6(Q) attacks: h7(diag), f5(diag), h5(diag)?
        // g6(row 6, col 7). h7(row 7, col 8). Delta (1,1). Yes.
        // Wait, g6 is not attacking h8?
        // g6 to h8 is (1, 2) knight jump? No. (1, 2) delta.
        // g (7) -> h (8). 6 -> 8. Delta x=1, y=2. Knight move. Q doesn't attack h8.
        // So Black King at h8 is NOT in check.
        // Can BK move?
        // h7 attacked by Q(g6).
        // g8 attacked by Q(g6)? No. g6->g8 (delta 0, 2). Yes, rook move.
        // g7 attacked by Q(g6).
        // So BK cannot move. Stalemate.
        fens.add(new Object[]{"7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", false}); // Stalemate (Wait, previously I used 5Q2, 6K1. Q at f7, K at g6).
        // Previous Stalemate FEN from IsMatedTest: "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"
        // Q at f7. K at g6. BK at h8.
        // f7 attacks g8, h7?
        // f7(Q) -> g8 (diag). Yes.
        // f7(Q) -> h7 (knight? 1,0. No side. 1,0 is Rook. f7-h7 is 2 squares? f-h is 2. 7-7 is 0. Horizontal. Yes).
        // So Q attacks h7.
        // Q attacks g7 (diag)? f7-g7 is 1,0? No. f-g is 1. 7-7 is 0.
        // Wait.
        // f7 attacks row 7.
        // BK at h8.
        // Moves: h7, g8, g7.
        // h7 (on rank 7) is attacked by Q(f7).
        // g7 (on rank 7) is attacked by Q(f7).
        // g8 (on rank 8). f7->g8 (diag). Yes.
        // So all squares attacked. BK not in check (f7 not attacking h8 - knight jump 2,1).
        // Correct.
        
        // --- Generate Has Moves (Normal positions) ---
        
        // 1. Non-Mates (White Q moved back) -> Has moves
        addVerticalNonMates(fens, "k", "Q", "K", "b", true);
        
        // 2. Start Pos
        fens.add(new Object[]{"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", true});
        
        // 3. Just King and King (Draw, but has moves)
        fens.add(new Object[]{"4k3/8/8/8/8/8/8/4K3 w - - 0 1", true});
        
        // 4. Horizontal Mates -> No Moves
        addHorizontalMates(fens, "K", "Q", "k", "b", false);
        
        // 5. Horizontal Non-Mates -> Has Moves
        addHorizontalNonMates(fens, "K", "Q", "k", "b", true);

        return fens.stream();
    }
    
    // Vertical Mate (No moves)
    private static void addVerticalMates(List<Object[]> fens, String r8, String r7, String r6, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
            String row8 = buildRow(i, r8);
            String row7 = buildRow(i, r7);
            String row6 = buildRow(i, r6);
            String fen = row8 + "/" + row7 + "/" + row6 + "/8/8/8/8/8 " + side + " - - 0 1";
            fens.add(new Object[]{fen, expected});
        }
    }
    
    // Vertical Non-Mate (Has moves)
    // Rank 8: King. Rank 5: Queen. Rank 6: Support.
    private static void addVerticalNonMates(List<Object[]> fens, String r8, String r5_Q, String r6_Support, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
            String row8 = buildRow(i, r8);
            String row6 = buildRow(i, r6_Support);
            String row5 = buildRow(i, r5_Q);
            String fen = row8 + "/8/" + row6 + "/" + row5 + "/8/8/8/8 " + side + " - - 0 1";
            fens.add(new Object[]{fen, expected});
        }
    }
    
    // Inverted Vertical Mate (No moves)
    private static void addInvertedVerticalMates(List<Object[]> fens, String r3, String r2, String r1, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
            String row3 = buildRow(i, r3);
            String row2 = buildRow(i, r2);
            String row1 = buildRow(i, r1);
            String fen = "8/8/8/8/8/" + row3 + "/" + row2 + "/" + row1 + " " + side + " - - 0 1";
            fens.add(new Object[]{fen, expected});
        }
    }
    
    // Horizontal Mate (No moves)
    private static void addHorizontalMates(List<Object[]> fens, String fCol, String gCol, String hCol, String side, boolean expected) {
        for (int rank = 0; rank < 8; rank++) {
            StringBuilder sb = new StringBuilder();
            for (int r = 7; r >= 0; r--) {
                if (r == rank) {
                    sb.append("5").append(fCol).append(gCol).append(hCol);
                } else {
                    sb.append("8");
                }
                if (r > 0) sb.append("/");
            }
            sb.append(" ").append(side).append(" - - 0 1");
            fens.add(new Object[]{sb.toString(), expected});
        }
    }
    
    // Horizontal Non-Mate (Has moves)
    private static void addHorizontalNonMates(List<Object[]> fens, String fCol, String eCol, String hCol, String side, boolean expected) {
        for (int rank = 0; rank < 8; rank++) {
            StringBuilder sb = new StringBuilder();
            for (int r = 7; r >= 0; r--) {
                if (r == rank) {
                    sb.append("4").append(eCol).append(fCol).append("1").append(hCol);
                } else {
                    sb.append("8");
                }
                if (r > 0) sb.append("/");
            }
            sb.append(" ").append(side).append(" - - 0 1");
            fens.add(new Object[]{sb.toString(), expected});
        }
    }

    private static String buildRow(int fileIndex, String charAtFile) {
        StringBuilder sb = new StringBuilder();
        if (fileIndex > 0) {
            sb.append(fileIndex);
        }
        sb.append(charAtFile);
        int remaining = 7 - fileIndex;
        if (remaining > 0) {
            sb.append(remaining);
        }
        return sb.toString();
    }
}
