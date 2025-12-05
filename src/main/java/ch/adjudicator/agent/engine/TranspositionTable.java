package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.move.Move;

/**
 * Transposition Table for storing previously searched positions.
 * Uses Zobrist hashing to identify positions and stores best moves and scores.
 */
public class TranspositionTable {
    private static final int DEFAULT_SIZE = 1 << 20; // 1 million entries (~40MB)
    
    private final TTEntry[] table;
    private final int sizeMask;
    
    public static class TTEntry {
        public volatile long zobristKey;
        public volatile Move bestMove;
        public volatile int score;
        public volatile int depth;
        public volatile int flag; // EXACT, LOWER_BOUND, UPPER_BOUND
        
        public static final int EXACT = 0;
        public static final int LOWER_BOUND = 1;
        public static final int UPPER_BOUND = 2;
        
        public TTEntry() {
            this.zobristKey = 0;
            this.bestMove = null;
            this.score = 0;
            this.depth = 0;
            this.flag = EXACT;
        }
        
        public void store(long key, Move move, int score, int depth, int flag) {
            this.zobristKey = key;
            this.bestMove = move;
            this.score = score;
            this.depth = depth;
            this.flag = flag;
        }
        
        public boolean isValid(long key) {
            return this.zobristKey == key;
        }
    }
    
    public TranspositionTable() {
        this(DEFAULT_SIZE);
    }
    
    public TranspositionTable(int size) {
        // Ensure size is a power of 2
        int actualSize = 1;
        while (actualSize < size) {
            actualSize <<= 1;
        }
        
        this.table = new TTEntry[actualSize];
        this.sizeMask = actualSize - 1;
        
        // Initialize all entries
        for (int i = 0; i < actualSize; i++) {
            table[i] = new TTEntry();
        }
    }
    
    /**
     * Get the table index for a zobrist hash.
     */
    private int getIndex(long zobristHash) {
        return (int) (zobristHash & sizeMask);
    }
    
    /**
     * Store a position in the transposition table.
     */
    public void store(long zobristHash, Move bestMove, int score, int depth, int flag) {
        int index = getIndex(zobristHash);
        TTEntry entry = table[index];
        
        // Replace if: new entry is deeper, or same depth but exact score
        // Race condition acceptable for performance
        if (depth >= entry.depth || flag == TTEntry.EXACT) {
            entry.store(zobristHash, bestMove, score, depth, flag);
        }
    }
    
    /**
     * Probe the transposition table for a position.
     * Returns entry if found, or null.
     */
    public TTEntry probe(long zobristHash) {
        int index = getIndex(zobristHash);
        TTEntry entry = table[index];
        
        if (entry.isValid(zobristHash)) {
            return entry;
        }
        
        return null;
    }
    
    /**
     * Get the best move from a previous search (if available).
     */
    public Move getBestMove(long zobristHash) {
        TTEntry entry = probe(zobristHash);
        return entry != null ? entry.bestMove : null;
    }
    
    /**
     * Clear the transposition table.
     */
    public void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i].zobristKey = 0;
            table[i].bestMove = null;
        }
    }
    
    /**
     * Get the number of entries in the table.
     */
    public int size() {
        return table.length;
    }
    
    /**
     * Get the fill rate of the table (for diagnostics).
     */
    public double getFillRate() {
        int filled = 0;
        for (TTEntry entry : table) {
            if (entry.zobristKey != 0) {
                filled++;
            }
        }
        return (double) filled / table.length;
    }
}
