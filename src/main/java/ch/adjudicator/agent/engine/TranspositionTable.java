package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.move.Move;

/**
 * Transposition Table for storing previously searched positions.
 * Uses Zobrist hashing to identify positions and stores best moves and scores.
 * Implements a Two-Tier replacement scheme: Deepest + Always Replace.
 */
public class TranspositionTable {
    private static final int DEFAULT_SIZE = 1 << 20; // 1 million entries (~40MB)

    private final TTEntry[] table;
    private final int sizeMask;

    public TranspositionTable() {
        this(DEFAULT_SIZE);
    }

    public TranspositionTable(int size) {
        // Ensure size is a power of 2
        int actualSize = 1;
        while (actualSize < size) {
            actualSize <<= 1;
        }

        // Two-Tier: 2 buckets per index, so 2 * actualSize
        this.table = new TTEntry[actualSize * 2];
        this.sizeMask = actualSize - 1;

        // Initialize all entries
        for (int i = 0; i < table.length; i++) {
            table[i] = new TTEntry();
        }
    }

    /**
     * Get the table index for a zobrist hash.
     * Returns the base index for the bucket (even number).
     */
    private int getIndex(long zobristHash) {
        return ((int) (zobristHash & sizeMask)) * 2;
    }

    /**
     * Store a position in the transposition table.
     * Two-Tier replacement: Deepest + Always Replace.
     */
    public void store(long zobristHash, Move bestMove, int score, int depth, int flag) {
        int index = getIndex(zobristHash);
        TTEntry deepEntry = table[index];
        TTEntry recentEntry = table[index + 1];

        // Strategy:
        // Slot 0 (deepEntry): Keeps the deepest search result seen so far for this bucket.
        // Slot 1 (recentEntry): Keeps the most recent search result (Always Replace).

        // If the new entry is deeper than or equal to the deepEntry, it takes the deep slot.
        // The old deepEntry is demoted to the recentEntry slot (to preserve it if it's different).
        if (depth >= deepEntry.depth) {
            if (deepEntry.zobristKey != 0 && deepEntry.zobristKey != zobristHash) {
                recentEntry.copyFrom(deepEntry);
            }
            deepEntry.store(zobristHash, bestMove, score, depth, flag);
        } else {
            // Otherwise, it goes to the recent slot (Always Replace)
            recentEntry.store(zobristHash, bestMove, score, depth, flag);
        }
    }

    /**
     * Probe the transposition table for a position.
     * Returns entry if found, or null.
     */
    public TTEntry probe(long zobristHash) {
        int index = getIndex(zobristHash);
        TTEntry deepEntry = table[index];
        TTEntry recentEntry = table[index + 1];

        // Check deep entry first
        if (deepEntry.isValid(zobristHash)) {
            // Check if recent entry is valid and somehow deeper (rare/collision case)
            if (recentEntry.isValid(zobristHash) && recentEntry.depth > deepEntry.depth) {
                return recentEntry;
            }
            return deepEntry;
        }

        // Check recent entry
        if (recentEntry.isValid(zobristHash)) {
            return recentEntry;
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
            table[i].depth = 0;
            table[i].score = 0;
            table[i].flag = TTEntry.EXACT;
        }
    }

    /**
     * Get the number of entries in the table.
     * Note: This returns the actual array size (2x capacity).
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

    public static class TTEntry {
        public static final int EXACT = 0;
        public static final int LOWER_BOUND = 1;
        public static final int UPPER_BOUND = 2;
        public volatile long zobristKey;
        public volatile Move bestMove;
        public volatile int score;
        public volatile int depth;
        public volatile int flag; // EXACT, LOWER_BOUND, UPPER_BOUND

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

        public void copyFrom(TTEntry other) {
            this.zobristKey = other.zobristKey;
            this.bestMove = other.bestMove;
            this.score = other.score;
            this.depth = other.depth;
            this.flag = other.flag;
        }
    }
}
