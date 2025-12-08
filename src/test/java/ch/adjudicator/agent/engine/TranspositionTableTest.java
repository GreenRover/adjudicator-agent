package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TranspositionTableTest {

    @Test
    public void testStoreAndProbe() {
        TranspositionTable tt = new TranspositionTable(1024);
        long key = 12345L;
        Move move = new Move(Square.E2, Square.E4);
        int score = 100;
        int depth = 5;
        int flag = TranspositionTable.TTEntry.EXACT;

        tt.store(key, move, score, depth, flag);
        
        TranspositionTable.TTEntry entry = tt.probe(key);
        assertNotNull(entry);
        assertEquals(key, entry.zobristKey);
        assertEquals(move, entry.bestMove);
        assertEquals(score, entry.score);
        assertEquals(depth, entry.depth);
    }

    @Test
    public void testTwoTierBehavior() {
        TranspositionTable tt = new TranspositionTable(1024); // Size ensures no unexpected collisions
        
        // Use a key that maps to index 0 (assuming sizeMask works as expected)
        // Or just rely on one key.
        long key1 = 10L;
        Move move1 = new Move(Square.A2, Square.A3);
        
        // 1. Store Deep entry (depth 10)
        tt.store(key1, move1, 100, 10, TranspositionTable.TTEntry.EXACT);
        
        TranspositionTable.TTEntry entry = tt.probe(key1);
        assertEquals(10, entry.depth);
        
        // 2. Store Shallower entry (depth 5) - Same key
        // Should go to Recent slot, but Probe should return Deepest (10)
        tt.store(key1, move1, 100, 5, TranspositionTable.TTEntry.EXACT);
        
        entry = tt.probe(key1);
        assertEquals(10, entry.depth); // Should still return the deep one
        
        // 3. Store Deeper entry (depth 15) - Same key
        // Should update Deep slot
        tt.store(key1, move1, 100, 15, TranspositionTable.TTEntry.EXACT);
        
        entry = tt.probe(key1);
        assertEquals(15, entry.depth);
    }
    
    @Test
    public void testCollisionTwoTier() {
        TranspositionTable tt = new TranspositionTable(2); // Small size to force collision
        // Size 2 -> Actual size 2. Capacity 4 slots (2 buckets * 2). Mask 1.
        // Index is (key & 1) * 2.
        
        long key1 = 4L; // Index 0 (4 & 1 = 0)
        long key2 = 6L; // Index 0 (6 & 1 = 0) -> Collision
        
        Move move1 = new Move(Square.A2, Square.A3);
        Move move2 = new Move(Square.B2, Square.B3);
        
        // 1. Store Key1 at Depth 10.
        tt.store(key1, move1, 100, 10, TranspositionTable.TTEntry.EXACT);
        // Deep: Key1 (10). Recent: Key1 (10) [initially, maybe?] No, recent is empty or same.
        // In my logic: if depth >= 0, goes to Deep.
        
        assertEquals(10, tt.probe(key1).depth);
        
        // 2. Store Key2 at Depth 5.
        // Collision! depth 5 < depth 10.
        // Should go to Recent slot. Deep stays Key1.
        tt.store(key2, move2, 200, 5, TranspositionTable.TTEntry.EXACT);
        
        // Probe Key1 -> Should find Deep (Key1).
        TranspositionTable.TTEntry entry1 = tt.probe(key1);
        assertNotNull(entry1);
        assertEquals(key1, entry1.zobristKey);
        assertEquals(10, entry1.depth);
        
        // Probe Key2 -> Should find Recent (Key2).
        TranspositionTable.TTEntry entry2 = tt.probe(key2);
        assertNotNull(entry2);
        assertEquals(key2, entry2.zobristKey);
        assertEquals(5, entry2.depth);
        
        // 3. Store Key2 at Depth 20.
        // Collision! depth 20 > depth 10.
        // Should replace Deep (Key1 -> Key2).
        // Old Deep (Key1) should move to Recent?
        // My logic: if deepEntry.key != key, copy deep to recent.
        tt.store(key2, move2, 300, 20, TranspositionTable.TTEntry.EXACT);
        
        // Probe Key2 -> Found in Deep (20).
        entry2 = tt.probe(key2);
        assertEquals(20, entry2.depth);
        assertEquals(key2, entry2.zobristKey);
        
        // Probe Key1 -> Should be in Recent (preserved).
        entry1 = tt.probe(key1);
        assertNotNull(entry1);
        assertEquals(key1, entry1.zobristKey);
        assertEquals(10, entry1.depth);
    }
}
