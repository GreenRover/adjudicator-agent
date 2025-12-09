package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.Square;

public class AttackLookups {

    // 0: North, 1: South, 2: East, 3: West, 4: NE, 5: NW, 6: SE, 7: SW
    private static final long[][] RAYS = new long[64][8];

    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[] KING_ATTACKS = new long[64];
    public static final long[][] PAWN_ATTACKS = new long[2][64];

    static {
        initializeRays();
        initializeAttacks();
    }

    private static void initializeAttacks() {
        for (int sq = 0; sq < 64; sq++) {
            // Knight
            long k = 0;
            int r = sq / 8;
            int c = sq % 8;
            int[] dr = {-2, -2, -1, -1, 1, 1, 2, 2};
            int[] dc = {-1, 1, -2, 2, -2, 2, -1, 1};
            for (int i = 0; i < 8; i++) {
                int nr = r + dr[i];
                int nc = c + dc[i];
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                    k |= (1L << (nr * 8 + nc));
                }
            }
            KNIGHT_ATTACKS[sq] = k;

            // King
            long ki = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0) continue;
                    int nr = r + x;
                    int nc = c + y;
                    if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                        ki |= (1L << (nr * 8 + nc));
                    }
                }
            }
            KING_ATTACKS[sq] = ki;

            // Pawn (captures only)
            // White (moves "North" -> rank + 1)
            long wp = 0;
            if (r + 1 < 8) {
                if (c - 1 >= 0) wp |= (1L << ((r + 1) * 8 + (c - 1)));
                if (c + 1 < 8) wp |= (1L << ((r + 1) * 8 + (c + 1)));
            }
            PAWN_ATTACKS[0][sq] = wp;

            // Black (moves "South" -> rank - 1)
            long bp = 0;
            if (r - 1 >= 0) {
                if (c - 1 >= 0) bp |= (1L << ((r - 1) * 8 + (c - 1)));
                if (c + 1 < 8) bp |= (1L << ((r - 1) * 8 + (c + 1)));
            }
            PAWN_ATTACKS[1][sq] = bp;
        }
    }

    private static void initializeRays() {
        for (int sq = 0; sq < 64; sq++) {
            int r = sq / 8;
            int c = sq % 8;

            // North (+8)
            for (int i = r + 1; i < 8; i++) {
                RAYS[sq][0] |= (1L << (i * 8 + c));
            }
            // South (-8)
            for (int i = r - 1; i >= 0; i--) {
                RAYS[sq][1] |= (1L << (i * 8 + c));
            }
            // East (+1)
            for (int i = c + 1; i < 8; i++) {
                RAYS[sq][2] |= (1L << (r * 8 + i));
            }
            // West (-1)
            for (int i = c - 1; i >= 0; i--) {
                RAYS[sq][3] |= (1L << (r * 8 + i));
            }
            // NE (+9)
            for (int i = 1; r + i < 8 && c + i < 8; i++) {
                RAYS[sq][4] |= (1L << ((r + i) * 8 + (c + i)));
            }
            // NW (+7)
            for (int i = 1; r + i < 8 && c - i >= 0; i++) {
                RAYS[sq][5] |= (1L << ((r + i) * 8 + (c - i)));
            }
            // SE (-7)
            for (int i = 1; r - i >= 0 && c + i < 8; i++) {
                RAYS[sq][6] |= (1L << ((r - i) * 8 + (c + i)));
            }
            // SW (-9)
            for (int i = 1; r - i >= 0 && c - i >= 0; i++) {
                RAYS[sq][7] |= (1L << ((r - i) * 8 + (c - i)));
            }
        }
    }

    public static long getRookAttacks(int sq, long occupied) {
        long attacks = 0;

        // North
        long ray = RAYS[sq][0];
        long blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = Long.numberOfTrailingZeros(blocker); // Positive direction -> LSB
            attacks |= (ray ^ RAYS[bSq][0]);
        } else {
            attacks |= ray;
        }

        // South
        ray = RAYS[sq][1];
        blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = 63 - Long.numberOfLeadingZeros(blocker); // Negative direction -> MSB
            attacks |= (ray ^ RAYS[bSq][1]);
        } else {
            attacks |= ray;
        }

        // East
        ray = RAYS[sq][2];
        blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = Long.numberOfTrailingZeros(blocker); // Positive direction -> LSB
            attacks |= (ray ^ RAYS[bSq][2]);
        } else {
            attacks |= ray;
        }

        // West
        ray = RAYS[sq][3];
        blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = 63 - Long.numberOfLeadingZeros(blocker); // Negative direction -> MSB
            attacks |= (ray ^ RAYS[bSq][3]);
        } else {
            attacks |= ray;
        }

        return attacks;
    }

    public static long getBishopAttacks(int sq, long occupied) {
        long attacks = 0;

        // NE
        long ray = RAYS[sq][4];
        long blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = Long.numberOfTrailingZeros(blocker); // Positive direction -> LSB
            attacks |= (ray ^ RAYS[bSq][4]);
        } else {
            attacks |= ray;
        }

        // NW
        ray = RAYS[sq][5];
        blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = Long.numberOfTrailingZeros(blocker); // Positive direction -> LSB
            attacks |= (ray ^ RAYS[bSq][5]);
        } else {
            attacks |= ray;
        }

        // SE
        ray = RAYS[sq][6];
        blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = 63 - Long.numberOfLeadingZeros(blocker); // Negative direction -> MSB
            attacks |= (ray ^ RAYS[bSq][6]);
        } else {
            attacks |= ray;
        }

        // SW
        ray = RAYS[sq][7];
        blocker = ray & occupied;
        if (blocker != 0) {
            int bSq = 63 - Long.numberOfLeadingZeros(blocker); // Negative direction -> MSB
            attacks |= (ray ^ RAYS[bSq][7]);
        } else {
            attacks |= ray;
        }

        return attacks;
    }
    
    public static long getQueenAttacks(int sq, long occupied) {
        return getRookAttacks(sq, occupied) | getBishopAttacks(sq, occupied);
    }
    
    // Convenience method using Square enum
    public static long getRookAttacks(Square sq, long occupied) {
        return getRookAttacks(sq.ordinal(), occupied);
    }

    public static long getBishopAttacks(Square sq, long occupied) {
        return getBishopAttacks(sq.ordinal(), occupied);
    }

    public static long getQueenAttacks(Square sq, long occupied) {
        return getQueenAttacks(sq.ordinal(), occupied);
    }
}
