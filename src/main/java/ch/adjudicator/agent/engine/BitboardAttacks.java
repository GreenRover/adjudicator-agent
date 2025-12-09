package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Side;

public class BitboardAttacks {

    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[] KING_ATTACKS = new long[64];
    public static final long[][] PAWN_ATTACKS = new long[2][64];

    static {
        initKnightAttacks();
        initKingAttacks();
        initPawnAttacks();
    }

    private static void initKnightAttacks() {
        int[] moves = {-17, -15, -10, -6, 6, 10, 15, 17};
        for (int i = 0; i < 64; i++) {
            long attacks = 0;
            int file = i % 8;
            int rank = i / 8;
            for (int move : moves) {
                int target = i + move;
                if (target >= 0 && target < 64) {
                    int tFile = target % 8;
                    int tRank = target / 8;
                    // Check if jump is valid (max 2 files/ranks distance)
                    if (Math.abs(tFile - file) <= 2 && Math.abs(tRank - rank) <= 2) {
                        attacks |= (1L << target);
                    }
                }
            }
            KNIGHT_ATTACKS[i] = attacks;
        }
    }

    private static void initKingAttacks() {
        int[] moves = {-9, -8, -7, -1, 1, 7, 8, 9};
        for (int i = 0; i < 64; i++) {
            long attacks = 0;
            int file = i % 8;
            int rank = i / 8;
            for (int move : moves) {
                int target = i + move;
                if (target >= 0 && target < 64) {
                    int tFile = target % 8;
                    int tRank = target / 8;
                    // Check if step is valid (max 1 file/rank distance)
                    if (Math.abs(tFile - file) <= 1 && Math.abs(tRank - rank) <= 1) {
                        attacks |= (1L << target);
                    }
                }
            }
            KING_ATTACKS[i] = attacks;
        }
    }

    private static void initPawnAttacks() {
        // Index 0: WHITE, Index 1: BLACK
        for (int i = 0; i < 64; i++) {
            int file = i % 8;
            int rank = i / 8;
            
            // White Pawns (move up, +8)
            long whiteAttacks = 0;
            if (rank < 7) {
                // Capture Left (Up-Left): +7 (only if file > 0)
                if (file > 0) {
                    whiteAttacks |= (1L << (i + 7));
                }
                // Capture Right (Up-Right): +9 (only if file < 7)
                if (file < 7) {
                    whiteAttacks |= (1L << (i + 9));
                }
            }
            PAWN_ATTACKS[Side.WHITE.ordinal()][i] = whiteAttacks;

            // Black Pawns (move down, -8)
            long blackAttacks = 0;
            if (rank > 0) {
                // Capture Left (Down-Left relative to black? No, from board perspective)
                // Black moves down.
                // Down-Right (from white perspective) is -7.
                // Down-Left (from white perspective) is -9.
                
                // Let's think: Black pawn on B7 (idx 57, file 1).
                // Attacks A6 (idx 48) and C6 (idx 50).
                // 57 - 9 = 48.
                // 57 - 7 = 50.
                
                // -9 (File-1, Rank-1): Valid if file > 0
                if (file > 0) {
                    blackAttacks |= (1L << (i - 9));
                }
                // -7 (File+1, Rank-1): Valid if file < 7
                if (file < 7) {
                    blackAttacks |= (1L << (i - 7));
                }
            }
            PAWN_ATTACKS[Side.BLACK.ordinal()][i] = blackAttacks;
        }
    }

    public static long getRookAttacks(int square, long occupancy) {
        long attacks = 0;
        int r = square / 8;
        int f = square % 8;

        // North
        for (int i = r + 1; i < 8; i++) {
            long bit = 1L << (i * 8 + f);
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        // South
        for (int i = r - 1; i >= 0; i--) {
            long bit = 1L << (i * 8 + f);
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        // East
        for (int i = f + 1; i < 8; i++) {
            long bit = 1L << (r * 8 + i);
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        // West
        for (int i = f - 1; i >= 0; i--) {
            long bit = 1L << (r * 8 + i);
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        return attacks;
    }

    public static long getBishopAttacks(int square, long occupancy) {
        long attacks = 0;
        int r = square / 8;
        int f = square % 8;

        // North-East (+1, +1)
        for (int i = 1; r + i < 8 && f + i < 8; i++) {
            long bit = 1L << ((r + i) * 8 + (f + i));
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        // North-West (+1, -1)
        for (int i = 1; r + i < 8 && f - i >= 0; i++) {
            long bit = 1L << ((r + i) * 8 + (f - i));
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        // South-East (-1, +1)
        for (int i = 1; r - i >= 0 && f + i < 8; i++) {
            long bit = 1L << ((r - i) * 8 + (f + i));
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        // South-West (-1, -1)
        for (int i = 1; r - i >= 0 && f - i >= 0; i++) {
            long bit = 1L << ((r - i) * 8 + (f - i));
            attacks |= bit;
            if ((occupancy & bit) != 0) break;
        }
        return attacks;
    }
    
    // Helper to get Queen attacks as combination of Rook and Bishop
    public static long getQueenAttacks(int square, long occupancy) {
        return getRookAttacks(square, occupancy) | getBishopAttacks(square, occupancy);
    }
}
