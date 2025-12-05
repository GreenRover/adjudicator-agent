package ch.adjudicator.agent.engine;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Polyglot opening book reader.
 * Reads .bin files and provides move suggestions based on Zobrist hash.
 */
public class PolyglotBook {
    private static final int ENTRY_SIZE = 16; // 8 bytes key + 2 bytes move + 2 bytes weight + 4 bytes learn
    
    private final List<BookEntry> entries;
    private final Random random;
    
    public static class BookEntry {
        public final long key;
        public final int move; // Encoded as: from | (to << 6) | (promotion << 12)
        public final int weight;
        
        public BookEntry(long key, int move, int weight) {
            this.key = key;
            this.move = move;
            this.weight = weight;
        }
        
        public int getFromSquare() {
            return move & 0x3F;
        }
        
        public int getToSquare() {
            return (move >> 6) & 0x3F;
        }
        
        public int getPromotion() {
            return (move >> 12) & 0x7;
        }
    }
    
    /**
     * Load a polyglot book from classpath resources.
     */
    public PolyglotBook(String resourcePath) throws IOException {
        this(openResource(resourcePath));
    }

    private static InputStream openResource(String resourcePath) throws IOException {
        InputStream is = PolyglotBook.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Book file not found: " + resourcePath);
        }
        return is;
    }

    /**
     * Load a polyglot book from an InputStream.
     */
    public PolyglotBook(InputStream is) throws IOException {
        this.entries = new ArrayList<>();
        this.random = new Random();

        try (DataInputStream dis = new DataInputStream(is)) {
            byte[] buffer = new byte[ENTRY_SIZE];

            while (dis.read(buffer) == ENTRY_SIZE) {
                ByteBuffer bb = ByteBuffer.wrap(buffer);

                long key = bb.getLong();
                int move = bb.getShort() & 0xFFFF;
                int weight = bb.getShort() & 0xFFFF;
                int learn = bb.getInt();

                entries.add(new BookEntry(key, move, weight));
            }
        }
    }
    
    /**
     * Find all book moves for a given position.
     */
    public List<BookEntry> findMoves(long zobristHash) {
        List<BookEntry> moves = new ArrayList<>();
        
        for (BookEntry entry : entries) {
            if (entry.key == zobristHash) {
                moves.add(entry);
            }
        }
        
        return moves;
    }
    
    /**
     * Get the best book move for a position (weighted random selection).
     */
    public BookEntry getBestMove(long zobristHash) {
        List<BookEntry> moves = findMoves(zobristHash);
        
        if (moves.isEmpty()) {
            return null;
        }
        
        // Calculate total weight
        int totalWeight = 0;
        for (BookEntry entry : moves) {
            totalWeight += entry.weight;
        }
        
        if (totalWeight == 0) {
            // If all weights are zero, pick randomly
            return moves.get(random.nextInt(moves.size()));
        }
        
        // Weighted random selection
        int randomValue = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (BookEntry entry : moves) {
            cumulative += entry.weight;
            if (randomValue < cumulative) {
                return entry;
            }
        }
        
        // Fallback (shouldn't reach here)
        return moves.get(moves.size() - 1);
    }
    
    /**
     * Convert polyglot move to Long Algebraic Notation (LAN).
     */
    public static String moveToLAN(BookEntry entry) {
        int from = entry.getFromSquare();
        int to = entry.getToSquare();
        int promotion = entry.getPromotion();
        
        String fromStr = squareToString(from);
        String toStr = squareToString(to);
        
        StringBuilder lan = new StringBuilder();
        lan.append(fromStr).append(toStr);
        
        // Add promotion piece if present
        if (promotion > 0) {
            char promoChar = switch (promotion) {
                case 1 -> 'n'; // Knight
                case 2 -> 'b'; // Bishop
                case 3 -> 'r'; // Rook
                case 4 -> 'q'; // Queen
                default -> 'q'; // Default to queen
            };
            lan.append(promoChar);
        }
        
        return lan.toString();
    }
    
    /**
     * Convert square index (0-63) to algebraic notation (e.g., "e2").
     */
    private static String squareToString(int square) {
        int file = square % 8;
        int rank = square / 8;
        
        char fileChar = (char) ('a' + file);
        char rankChar = (char) ('1' + rank);
        
        return "" + fileChar + rankChar;
    }
    
    public int size() {
        return entries.size();
    }
}
