package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitboardAttacksTest {

    private long getBit(Square sq) {
        return 1L << sq.ordinal();
    }

    private boolean isSet(long bitboard, Square sq) {
        return (bitboard & getBit(sq)) != 0;
    }

    @Test
    void testKnightAttacks() {
        // Test Knight on D4 (center)
        // D4 is index 3 + 3*8 = 27 (Files A=0..H=7. D=3. Ranks 1=0..8=7. 4=3.)
        // Wait. A1=0,0. D4=File 3, Rank 3.
        Square sq = Square.D4;
        long attacks = BitboardAttacks.KNIGHT_ATTACKS[sq.ordinal()];
        
        // Expected targets:
        // Rank +2: B6, F6
        // Rank +1: C6, E6 (Wait. +1 rank is rank 5)
        // D4 (3,3). 
        // +2 rank (5): F5(2,5) - Wait. 
        // Knight moves: +/- 1 file, +/- 2 rank OR +/- 2 file, +/- 1 rank.
        
        // D4 (3,3)
        // (2,5) C6? No C is file 2.
        // File 3-1=2 (C), Rank 3+2=5 (6). -> C6
        // File 3+1=4 (E), Rank 3+2=5 (6). -> E6
        // File 3-2=1 (B), Rank 3+1=4 (5). -> B5
        // File 3+2=5 (F), Rank 3+1=4 (5). -> F5
        // File 3-2=1 (B), Rank 3-1=2 (3). -> B3
        // File 3+2=5 (F), Rank 3-1=2 (3). -> F3
        // File 3-1=2 (C), Rank 3-2=1 (2). -> C2
        // File 3+1=4 (E), Rank 3-2=1 (2). -> E2
        
        assertTrue(isSet(attacks, Square.C6));
        assertTrue(isSet(attacks, Square.E6));
        assertTrue(isSet(attacks, Square.B5));
        assertTrue(isSet(attacks, Square.F5));
        assertTrue(isSet(attacks, Square.B3));
        assertTrue(isSet(attacks, Square.F3));
        assertTrue(isSet(attacks, Square.C2));
        assertTrue(isSet(attacks, Square.E2));
        
        assertEquals(8, Long.bitCount(attacks));

        // Test Corner A1
        attacks = BitboardAttacks.KNIGHT_ATTACKS[Square.A1.ordinal()];
        assertTrue(isSet(attacks, Square.B3));
        assertTrue(isSet(attacks, Square.C2));
        assertEquals(2, Long.bitCount(attacks));
    }

    @Test
    void testKingAttacks() {
        Square sq = Square.E1; // White King start
        long attacks = BitboardAttacks.KING_ATTACKS[sq.ordinal()];
        
        // E1 (4,0).
        // D1, F1
        // D2, E2, F2
        assertTrue(isSet(attacks, Square.D1));
        assertTrue(isSet(attacks, Square.F1));
        assertTrue(isSet(attacks, Square.D2));
        assertTrue(isSet(attacks, Square.E2));
        assertTrue(isSet(attacks, Square.F2));
        assertEquals(5, Long.bitCount(attacks));
        
        // Corner H8
        sq = Square.H8;
        attacks = BitboardAttacks.KING_ATTACKS[sq.ordinal()];
        assertTrue(isSet(attacks, Square.G8));
        assertTrue(isSet(attacks, Square.G7));
        assertTrue(isSet(attacks, Square.H7));
        assertEquals(3, Long.bitCount(attacks));
    }

    @Test
    void testPawnAttacks() {
        // White Pawn on E4
        Square sq = Square.E4;
        long attacks = BitboardAttacks.PAWN_ATTACKS[Side.WHITE.ordinal()][sq.ordinal()];
        // White captures Up-Left (D5) and Up-Right (F5)
        assertTrue(isSet(attacks, Square.D5));
        assertTrue(isSet(attacks, Square.F5));
        assertEquals(2, Long.bitCount(attacks));
        
        // White Pawn on A2
        sq = Square.A2;
        attacks = BitboardAttacks.PAWN_ATTACKS[Side.WHITE.ordinal()][sq.ordinal()];
        // Capture only B3
        assertTrue(isSet(attacks, Square.B3));
        assertFalse(isSet(attacks, Square.A3)); // Move, not capture
        assertEquals(1, Long.bitCount(attacks));
        
        // Black Pawn on E4 (moves down)
        // Captures D3 and F3
        sq = Square.E4;
        attacks = BitboardAttacks.PAWN_ATTACKS[Side.BLACK.ordinal()][sq.ordinal()];
        assertTrue(isSet(attacks, Square.D3));
        assertTrue(isSet(attacks, Square.F3));
        assertEquals(2, Long.bitCount(attacks));

        // Black Pawn on H7
        sq = Square.H7;
        attacks = BitboardAttacks.PAWN_ATTACKS[Side.BLACK.ordinal()][sq.ordinal()];
        // Captures G6
        assertTrue(isSet(attacks, Square.G6));
        assertEquals(1, Long.bitCount(attacks));
    }

    @Test
    void testRookAttacks() {
        // Rook on D4, no blockers
        long attacks = BitboardAttacks.getRookAttacks(Square.D4.ordinal(), 0L);
        // Full rank 4 and full file D (excluding D4 itself)
        // 7 squares on file + 7 squares on rank = 14
        assertEquals(14, Long.bitCount(attacks));
        assertTrue(isSet(attacks, Square.D1));
        assertTrue(isSet(attacks, Square.D8));
        assertTrue(isSet(attacks, Square.A4));
        assertTrue(isSet(attacks, Square.H4));
        
        // Blockers
        // Rook on D4. Blocker on D6.
        // Should attack D5 and D6. Should NOT attack D7, D8.
        long occupancy = getBit(Square.D6);
        attacks = BitboardAttacks.getRookAttacks(Square.D4.ordinal(), occupancy);
        
        assertTrue(isSet(attacks, Square.D5));
        assertTrue(isSet(attacks, Square.D6)); // Includes the blocker (capture)
        assertFalse(isSet(attacks, Square.D7));
        assertFalse(isSet(attacks, Square.D8));
        
        // Verify other directions still work
        assertTrue(isSet(attacks, Square.D2));
        assertTrue(isSet(attacks, Square.A4));
    }

    @Test
    void testBishopAttacks() {
        // Bishop on D4, no blockers
        // D4 (3,3).
        // NE: E5, F6, G7, H8 (4)
        // NW: C5, B6, A7 (3)
        // SE: E3, F2, G1 (3)
        // SW: C3, B2, A1 (3)
        // Total 13
        long attacks = BitboardAttacks.getBishopAttacks(Square.D4.ordinal(), 0L);
        assertEquals(13, Long.bitCount(attacks));
        assertTrue(isSet(attacks, Square.H8));
        assertTrue(isSet(attacks, Square.A7));
        assertTrue(isSet(attacks, Square.G1));
        assertTrue(isSet(attacks, Square.A1));
        
        // Blocker on F6
        long occupancy = getBit(Square.F6);
        attacks = BitboardAttacks.getBishopAttacks(Square.D4.ordinal(), occupancy);
        
        assertTrue(isSet(attacks, Square.E5));
        assertTrue(isSet(attacks, Square.F6)); // Hit blocker
        assertFalse(isSet(attacks, Square.G7)); // Blocked
        assertFalse(isSet(attacks, Square.H8)); // Blocked
    }
}
