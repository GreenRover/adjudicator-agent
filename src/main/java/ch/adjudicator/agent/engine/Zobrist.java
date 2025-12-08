package ch.adjudicator.agent.engine;

import java.util.Random;

/**
 * Zobrist hashing for chess positions.
 * Provides incremental hash updates for fast position identification.
 */
public class Zobrist {
    // Piece type indices
    public static final int PAWN = 0;
    public static final int KNIGHT = 1;
    public static final int BISHOP = 2;
    public static final int ROOK = 3;
    public static final int QUEEN = 4;
    public static final int KING = 5;
    // Color indices
    public static final int WHITE = 0;
    public static final int BLACK = 1;
    // [piece type (0-5)][color (0-1)][square (0-63)]
    private static final long[][][] PIECE_KEYS = new long[6][2][64];
    // Castling rights: [white kingside, white queenside, black kingside, black queenside]
    private static final long[] CASTLING_KEYS = new long[4];
    // En passant file (0-7, file a-h)
    private static final long[] EN_PASSANT_KEYS = new long[8];
    // Side to move (black to move)
    private static final long BLACK_TO_MOVE_KEY;

    static {
        // Initialize with deterministic random numbers (for reproducibility)
        Random random = new Random(1070372L);

        // Initialize piece keys
        for (int piece = 0; piece < 6; piece++) {
            for (int color = 0; color < 2; color++) {
                for (int square = 0; square < 64; square++) {
                    PIECE_KEYS[piece][color][square] = random.nextLong();
                }
            }
        }

        // Initialize castling keys
        for (int i = 0; i < 4; i++) {
            CASTLING_KEYS[i] = random.nextLong();
        }

        // Initialize en passant keys
        for (int i = 0; i < 8; i++) {
            EN_PASSANT_KEYS[i] = random.nextLong();
        }

        // Initialize side to move key
        BLACK_TO_MOVE_KEY = random.nextLong();
    }

    /**
     * Get the Zobrist key for a piece on a square.
     */
    public static long pieceKey(int pieceType, int color, int square) {
        return PIECE_KEYS[pieceType][color][square];
    }

    /**
     * Get the Zobrist key for castling rights.
     *
     * @param index 0=white kingside, 1=white queenside, 2=black kingside, 3=black queenside
     */
    public static long castlingKey(int index) {
        return CASTLING_KEYS[index];
    }

    /**
     * Get the Zobrist key for en passant on a file.
     *
     * @param file 0-7 (file a-h)
     */
    public static long enPassantKey(int file) {
        return EN_PASSANT_KEYS[file];
    }

    /**
     * Get the Zobrist key for black to move.
     */
    public static long blackToMoveKey() {
        return BLACK_TO_MOVE_KEY;
    }
}
