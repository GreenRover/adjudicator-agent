package ch.adjudicator.agent.engine.board;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IsMatedTest {

    @ParameterizedTest
    @MethodSource("provideFens")
    void testIsMatedBitboard(String fen, boolean expectedMate) {
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);
        assertEquals(expectedMate, board.isMated(), "Failed for FEN: " + fen);
    }

    private static Stream<Object[]> provideFens() {
        List<Object[]> fens = new ArrayList<>();

        // --- Generate Mates (approx 64) ---
        // 1. White mates Black on Rank 8 (8 files)
        // Pattern: BK on Rank 8, WQ on Rank 7, WK on Rank 6. Same file.
        // FEN parts:
        // Rank 8: BK
        // Rank 7: WQ
        // Rank 6: WK
        // Ranks 5-1: 8
        // Side: b
        addVerticalMates(fens, "k", "Q", "K", "b", true);

        // 2. Black mates White on Rank 1 (8 files)
        // Pattern: WK on Rank 1, BQ on Rank 2, BK on Rank 3. Same file.
        // But we need to reverse the board order for FEN (Rank 8 first).
        // Rank 8-4: 8
        // Rank 3: BK (k)
        // Rank 2: BQ (q)
        // Rank 1: WK (K)
        // Side: w
        addInvertedVerticalMates(fens, "k", "q", "K", "w", true);
        
        // 3. White mates Black on Rank 1? (Less common, but possible if blocked. 
        // But let's stick to easy edge mates).
        // Let's do Rank 1 mate for Black (White King stuck on Rank 1).
        // Oh wait, I just did that (Item 2).
        
        // Let's do the reverse: White mates Black on Rank 1.
        // BK at Rank 1. WQ at Rank 2. WK at Rank 3.
        // Side: b
        addInvertedVerticalMates(fens, "K", "Q", "k", "b", true);
        
        // 4. Black mates White on Rank 8.
        // WK at Rank 8. BQ at Rank 7. BK at Rank 6.
        // Side: w
        addVerticalMates(fens, "K", "q", "k", "w", true);
        
        // That's 8 * 4 = 32 mates. 
        
        // Let's add diagonal mates or just shifts?
        // Let's add "Epaulette mate" style or simply different files/ranks.
        // Actually, let's use the file-based mates (King on 'a' file).
        // Pattern: King on a-file, Queen on b-file, King on c-file.
        // Ranks can shift 1-8?
        // King at a8 (corner).
        // Let's just generate more variations of the vertical ones but with non-mate status.

        // --- Generate Non-Mates (approx 64) ---
        // Use the same patterns but move the attacking Queen away or remove support.
        
        // 1. Same as Vertical Mates but move Queen back 1 rank (Rank 5 instead of 7? No, Rank 7 is Q).
        // Move Q to Rank 5.
        // Rank 8: BK
        // Rank 7: 8
        // Rank 6: WK
        // Rank 5: WQ
        // ...
        // Is it mate? No. King can move to Rank 7? 
        // BK at e8. WK at e6. WQ at e5.
        // WQ attacks e-file and diagonals.
        // BK can go to d8, f8?
        // WK at e6 attacks d7, e7, f7.
        // WQ at e5 attacks e6, e7, e8.
        // Does WQ attack d8? e5->d6->c7->b8. No.
        // Does WQ attack f8? e5->f6->g7->h8. No.
        // So d8 and f8 are safe (unless WK attacks them? WK e6 attacks d7, e7, f7. Not d8/f8).
        // So BK can move to d8/f8. Not mate.
        addVerticalNonMates(fens, "k", "Q", "K", "b", false); // Shift Q to Rank 5
        
        // 2. Black mates White on Rank 1 -> Non mate.
        // Move BQ to Rank 4.
        addInvertedVerticalNonMates(fens, "k", "q", "K", "w", false);

        // 3. White mates Black on Rank 1 -> Non mate.
        addInvertedVerticalNonMates(fens, "K", "Q", "k", "b", false);
        
        // 4. Black mates White on Rank 8 -> Non mate.
        addVerticalNonMates(fens, "K", "q", "k", "w", false);
        
        // This gives 32 mates and 32 non-mates.
        // We need 100 total.
        
        // Add specific mates.
        // Fool's mate
        fens.add(new Object[]{"rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 3", true});
        // Scholar's mate (Black mated)
        fens.add(new Object[]{"r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", true});
        
        // Add specific non-mates.
        // Start pos
        fens.add(new Object[]{"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", false});
        // Empty-ish board (Kings only) - Draw
        fens.add(new Object[]{"4k3/8/8/8/8/8/8/4K3 w - - 0 1", false});
        // Stalemate
        fens.add(new Object[]{"7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", false});
        
        // We need ~30 more.
        // Let's generate "Corner Mates" on the other axis (Horizontal).
        // King on 'h' file. Opponent Queen on 'g' file. Opponent King on 'f' file.
        // Iterate ranks 0-7.
        // White mates Black on h-file.
        // BK at h(rank). WQ at g(rank). WK at f(rank).
        // FEN construction needs to handle placing pieces on specific files in a row.
        addHorizontalMates(fens, "K", "Q", "k", "b", true); // WK(f), WQ(g), BK(h) -> "5KQk"
        
        // Horizontal Mates on A-file (White mates Black)
        // BK(a), WQ(b), WK(c) -> "kQK5"
        addHorizontalMatesInverse(fens, "k", "Q", "K", "b", true);

        // Horizontal Mates on H-file (Black mates White)
        // BK(f), BQ(g), WK(h) -> "5kqK"
        addHorizontalMates(fens, "k", "q", "K", "w", true);
        
        // And non-mates for these (move Q away).
        addHorizontalNonMates(fens, "K", "Q", "k", "b", false);
        
        // Non-mates for A-file (move Q to d-file? or just remove check)
        // "k1QK4" (Q at c, K at d?)
        // Let's use helper.
        addHorizontalNonMatesInverse(fens, "k", "Q", "K", "b", false);
        
        // Non-mates for H-file (Black attacking White)
        addHorizontalNonMates(fens, "k", "q", "K", "w", false);
        
        return fens.stream();
    }
    
    // Horizontal Mate on A-file (Pieces on a, b, c)
    // Pattern: Piece(a) + Piece(b) + Piece(c) + 5
    private static void addHorizontalMatesInverse(List<Object[]> fens, String aCol, String bCol, String cCol, String side, boolean expected) {
        for (int rank = 0; rank < 8; rank++) {
            StringBuilder sb = new StringBuilder();
            for (int r = 7; r >= 0; r--) {
                if (r == rank) {
                    sb.append(aCol).append(bCol).append(cCol).append("5");
                } else {
                    sb.append("8");
                }
                if (r > 0) sb.append("/");
            }
            sb.append(" ").append(side).append(" - - 0 1");
            fens.add(new Object[]{sb.toString(), expected});
        }
    }
    
    // Horizontal Non-Mate on A-file.
    // Move Q from b to d.
    // a: Target. b: Empty(1). c: Support. d: Q.
    // "k1K Q 4" ??
    // Pattern: a + 1 + c + d + 4.
    private static void addHorizontalNonMatesInverse(List<Object[]> fens, String aCol, String dCol, String cCol, String side, boolean expected) {
        for (int rank = 0; rank < 8; rank++) {
            StringBuilder sb = new StringBuilder();
            for (int r = 7; r >= 0; r--) {
                if (r == rank) {
                    sb.append(aCol).append("1").append(cCol).append(dCol).append("4");
                } else {
                    sb.append("8");
                }
                if (r > 0) sb.append("/");
            }
            sb.append(" ").append(side).append(" - - 0 1");
            fens.add(new Object[]{sb.toString(), expected});
        }
    }

    // Rank 8, 7, 6 setup
    private static void addVerticalMates(List<Object[]> fens, String r8, String r7, String r6, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
            String row8 = buildRow(i, r8);
            String row7 = buildRow(i, r7);
            String row6 = buildRow(i, r6);
            String fen = row8 + "/" + row7 + "/" + row6 + "/8/8/8/8/8 " + side + " - - 0 1";
            fens.add(new Object[]{fen, expected});
        }
    }
    
    // Rank 8, 7, ... 5? No, for non-mate we put Q on Rank 5.
    // Rank 8: King. Rank 7: Empty. Rank 6: Supporter. Rank 5: Queen.
    private static void addVerticalNonMates(List<Object[]> fens, String r8, String r5_Q, String r6_Support, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
            String row8 = buildRow(i, r8);
            String row6 = buildRow(i, r6_Support);
            String row5 = buildRow(i, r5_Q);
            String fen = row8 + "/8/" + row6 + "/" + row5 + "/8/8/8/8 " + side + " - - 0 1";
            fens.add(new Object[]{fen, expected});
        }
    }

    // Rank 3, 2, 1 setup (displayed as .../3/2/1 in FEN order 8..1, so we need 8/8/8/8/8/3/2/1)
    private static void addInvertedVerticalMates(List<Object[]> fens, String r3, String r2, String r1, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
            String row3 = buildRow(i, r3);
            String row2 = buildRow(i, r2);
            String row1 = buildRow(i, r1);
            String fen = "8/8/8/8/8/" + row3 + "/" + row2 + "/" + row1 + " " + side + " - - 0 1";
            fens.add(new Object[]{fen, expected});
        }
    }
    
    // Non-mate: Move piece from r2 to r4.
    // Rank 4: Piece. Rank 3: Piece. Rank 1: Piece.
    private static void addInvertedVerticalNonMates(List<Object[]> fens, String r3_Support, String r4_Q, String r1_Target, String side, boolean expected) {
        for (int i = 0; i < 8; i++) {
             // Wait, the logic in addInvertedVerticalMates was: r3, r2, r1.
             // Here we want: Target at r1. Support at r3. Q moved to r4.
             String row4 = buildRow(i, r4_Q);
             String row3 = buildRow(i, r3_Support);
             String row1 = buildRow(i, r1_Target);
             String fen = "8/8/8/8/" + row4 + "/" + row3 + "/8/" + row1 + " " + side + " - - 0 1";
             fens.add(new Object[]{fen, expected});
        }
    }
    
    // Horizontal Mate on H-file.
    // Pieces on f, g, h.
    // Loop ranks 0 to 7.
    // FEN: we need to construct each rank string.
    // If loop index is the rank, we put the pieces there. Otherwise "8".
    // Pattern: 5 + Support(f) + Queen(g) + King(h) -> "5KQk" (if white mating black).
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
    
    // Horizontal Non-Mate. Move Queen to 'e' file.
    // Pattern: 4 + Queen(e) + Support(f) + Empty(g) + King(h) -> "4Q K 1 k" ??
    // No, g-file empty.
    // e: Q. f: Support. g: empty (1). h: King.
    // "4" + e + f + "1" + h.
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
