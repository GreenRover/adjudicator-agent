package ch.adjudicator.agent.engine;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test class for PolyglotBook - validates reading and selecting moves from chess opening books.
 * 
 * Polyglot book format overview:
 * Each entry in the book file consists of 16 bytes with the following structure:
 * - 8 bytes: position key (long) - Zobrist hash identifying the chess position
 * - 2 bytes: move (short) - encoded move in Polyglot format
 * - 2 bytes: weight (short) - probability weight for move selection
 * - 4 bytes: learn (int) - learning data (not used in these tests)
 * 
 * Multiple entries can share the same position key, representing different possible moves
 * from that position. The weight determines the probability of selecting each move.
 */
class PolyglotBookTest {

    /**
     * Tests the basic functionality of finding and selecting the best move for a given position.
     * 
     * This test verifies:
     * 1. Multiple moves can be stored for the same position key
     * 2. The book correctly retrieves all moves for a given key
     * 3. getBestMove() returns one of the valid moves (weighted random selection)
     * 4. Moves are correctly associated with their respective position keys
     * 5. Unknown position keys return null
     */
    @Test
    void testGetBestMove() throws IOException {
        // Setup test data: Create two position keys with associated moves
        // Position keys are arbitrary Zobrist hashes representing chess positions
        long key1 = 0x1234567890ABCDEFL;
        int move1 = encodeMove(12, 28, 0); // e2e4 - pawn from square 12 to square 28
        int weight1 = 100;
        
        long key2 = 0x0FEDCBA987654321L;
        int move2 = encodeMove(52, 36, 0); // e7e5 - pawn from square 52 to square 36
        int weight2 = 50;

        // Create a mock Polyglot book file in memory by writing entries in the binary format
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            // Entry 1: Associate move1 with key1, high weight (100)
            // This represents a strong opening move for position key1
            dos.writeLong(key1);
            dos.writeShort(move1);
            dos.writeShort(weight1);
            dos.writeInt(0); // learn field (unused in this test)

            // Entry 2: Associate move2 with key1, very low weight (1)
            // This creates a second possible move for key1, but much less likely to be selected
            dos.writeLong(key1);
            dos.writeShort(move2);
            dos.writeShort(1); // Very low weight
            dos.writeInt(0);
            
            // Entry 3: Associate move2 with key2, medium weight (50)
            // This represents the only move for position key2
            dos.writeLong(key2);
            dos.writeShort(move2);
            dos.writeShort(weight2);
            dos.writeInt(0);
        }

        // Create the PolyglotBook instance from the mock binary data
        PolyglotBook book = new PolyglotBook(new ByteArrayInputStream(baos.toByteArray()));

        // Test 1: Verify that key1 has exactly 2 moves available
        // findMoves() should return all entries matching the position key
        List<PolyglotBook.BookEntry> moves1 = book.findMoves(key1);
        assertThat(moves1.size(), is(2));
        
        // Test 2: Verify getBestMove() returns a valid move for key1
        // Due to weighted random selection, it could be either move1 or move2
        // (though move1 with weight 100 is much more likely than move2 with weight 1)
        PolyglotBook.BookEntry best1 = book.getBestMove(key1);
        assertThat(best1, notNullValue());
        assertThat(best1.move == move1 || best1.move == move2, is(true));
        
        // Test 3: Verify getBestMove() for key2 returns the only available move (move2)
        PolyglotBook.BookEntry best2 = book.getBestMove(key2);
        assertThat(best2, notNullValue());
        assertThat(best2.move, is(move2));
        
        // Test 4: Verify that an unknown position key returns null
        // The book should gracefully handle positions not in the opening book
        assertThat(book.getBestMove(0x0000000000000000L), nullValue());
    }
    
    /**
     * Tests the weighted random selection algorithm used by getBestMove().
     * 
     * When multiple moves are available for a position, getBestMove() should select
     * moves with probability proportional to their weights. This test verifies that
     * the selection is approximately fair when two moves have equal weights.
     * 
     * The test uses a statistical approach:
     * - Creates two moves with equal weight (100 each) for the same position
     * - Calls getBestMove() 1000 times to build a distribution
     * - Verifies each move is selected roughly 50% of the time (allowing variance)
     * - Accepts counts between 400-600 out of 1000 (40%-60%) as statistically valid
     */
    @Test
    void testWeightedSelection() throws IOException {
         // Setup: Create a position with two moves having equal weights
         long key = 1L;
         int moveA = 10;
         int moveB = 20;
         
         // Build a mock book with two equally-weighted moves for the same position
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         try (DataOutputStream dos = new DataOutputStream(baos)) {
            // Entry 1: moveA with weight 100 (50% probability)
            dos.writeLong(key);
            dos.writeShort(moveA);
            dos.writeShort(100);
            dos.writeInt(0);
            
            // Entry 2: moveB with weight 100 (50% probability)
            dos.writeLong(key);
            dos.writeShort(moveB);
            dos.writeShort(100);
            dos.writeInt(0);
         }
         
         PolyglotBook book = new PolyglotBook(new ByteArrayInputStream(baos.toByteArray()));
         
         // Statistical test: Run getBestMove() 1000 times and count selections
         int countA = 0;
         int countB = 0;
         for (int i = 0; i < 1000; i++) {
             PolyglotBook.BookEntry entry = book.getBestMove(key);
             if (entry.move == moveA) countA++;
             else if (entry.move == moveB) countB++;
         }
         
         // Verify the distribution is approximately 50/50
         // With 1000 trials and equal weights, we expect ~500/500, but allow variance
         // Accepting 400-600 range accounts for normal statistical fluctuation
         assertThat("Distribution for A should be roughly equal, got " + countA, countA > 400 && countA < 600, is(true));
         assertThat("Distribution for B should be roughly equal, got " + countB, countB > 400 && countB < 600, is(true));
    }

    /**
     * Helper method to encode a chess move in Polyglot book format.
     * 
     * Polyglot move encoding uses a 16-bit integer with three bit fields:
     * - Bits 0-5 (6 bits): "to" square (0-63, where a1=0, h8=63)
     * - Bits 6-11 (6 bits): "from" square (0-63)
     * - Bits 12-14 (3 bits): promotion piece (0=none, 1=knight, 2=bishop, 3=rook, 4=queen)
     * 
     * Example: e2e4 would be encoded as to=28 (e4), from=12 (e2), promotion=0
     * 
     * @param from the source square (0-63)
     * @param to the destination square (0-63)
     * @param promotion the promotion piece type (0 for no promotion)
     * @return the encoded move as a 16-bit integer
     */
    private int encodeMove(int from, int to, int promotion) {
        return to | (from << 6) | (promotion << 12);
    }
}