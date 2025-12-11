package ch.adjudicator.agent.engine.eval;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Piece;

public class PawnStructure {

    public static final long[] FILE_MASKS = {
            0x0101010101010101L, 0x0202020202020202L, 0x0404040404040404L, 0x0808080808080808L,
            0x1010101010101010L, 0x2020202020202020L, 0x4040404040404040L, 0x8080808080808080L
    };

    public static final int MG_ISOLATED_PAWN_PENALTY = -10;
    public static final int EG_ISOLATED_PAWN_PENALTY = -20;

    public static final int MG_DOUBLED_PAWN_PENALTY = -10;
    public static final int EG_DOUBLED_PAWN_PENALTY = -20;

    // Passed Pawn Bonus: {Rank 0..7}
    public static final int[] MG_PASSED_PAWN_BONUS = {0, 5, 10, 20, 35, 60, 100, 0};
    public static final int[] EG_PASSED_PAWN_BONUS = {0, 10, 20, 40, 80, 160, 240, 0};

    public static long evaluate(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;

        long myPawns = white ? board.getBitboard(Piece.WHITE_PAWN) : board.getBitboard(Piece.BLACK_PAWN);
        long enemyPawns = white ? board.getBitboard(Piece.BLACK_PAWN) : board.getBitboard(Piece.WHITE_PAWN);
        long enemyKing = white ? board.getBitboard(Piece.BLACK_KING) : board.getBitboard(Piece.WHITE_KING);

        // Pawn Structure
        for (int file = 0; file < 8; file++) {
            long fileMask = FILE_MASKS[file];
            long pawnsInFile = myPawns & fileMask;

            if (pawnsInFile != 0) {
                // Doubled
                if (Long.bitCount(pawnsInFile) > 1) {
                    mgScore += MG_DOUBLED_PAWN_PENALTY;
                    egScore += EG_DOUBLED_PAWN_PENALTY;
                }

                // Isolated
                long leftMask = (file > 0) ? FILE_MASKS[file - 1] : 0;
                long rightMask = (file < 7) ? FILE_MASKS[file + 1] : 0;
                if (((myPawns & leftMask) == 0) && ((myPawns & rightMask) == 0)) {
                    mgScore += MG_ISOLATED_PAWN_PENALTY;
                    egScore += EG_ISOLATED_PAWN_PENALTY;
                }
            }
        }

        // Passed Pawns
        long tempPawns = myPawns;
        while (tempPawns != 0) {
            int sq = Long.numberOfTrailingZeros(tempPawns);
            int rank = sq / 8;
            int file = sq % 8;

            long frontSpan = 0L;
            if (white) {
                for (int r = rank + 1; r < 8; r++) {
                    frontSpan |= (1L << (r * 8 + file));
                    if (file > 0) frontSpan |= (1L << (r * 8 + file - 1));
                    if (file < 7) frontSpan |= (1L << (r * 8 + file + 1));
                }
            } else {
                for (int r = rank - 1; r >= 0; r--) {
                    frontSpan |= (1L << (r * 8 + file));
                    if (file > 0) frontSpan |= (1L << (r * 8 + file - 1));
                    if (file < 7) frontSpan |= (1L << (r * 8 + file + 1));
                }
            }

            if ((frontSpan & enemyPawns) == 0) {
                // It is a passed pawn!

                // --- CHECK: Blocked by Enemy King ---
                // Only penalized if:
                // 1. King is in the FRONT SPAN (blocking path or adjacent).
                // 2. Pawn is NOT connected (connected pawns can support each other).
                // Note: We use full frontSpan (rank+1..7 on file-1, file, file+1) to catch Kings 
                // that are adjacent but effectively blocking (e.g. on edge).
                boolean blockedByKing = (frontSpan & enemyKing) != 0;

                boolean connected = false;
                if (blockedByKing) {
                    // Check for connections (friendly pawns on adjacent files, rank +/- 1)
                    long neighborMask = 0;
                    if (file > 0) neighborMask |= FILE_MASKS[file - 1];
                    if (file < 7) neighborMask |= FILE_MASKS[file + 1];

                    long neighbors = myPawns & neighborMask;
                    while (neighbors != 0) {
                        int nSq = Long.numberOfTrailingZeros(neighbors);
                        int nRank = nSq / 8;
                        if (Math.abs(nRank - rank) <= 1) {
                            connected = true;
                            break;
                        }
                        neighbors &= neighbors - 1;
                    }
                }

                if (blockedByKing && !connected) {
                    // Blocked and isolated: Dead pawn
                    mgScore -= 100;
                    egScore -= 2000;
                } else {
                    int bonusRank = white ? rank : (7 - rank);
                    mgScore += MG_PASSED_PAWN_BONUS[bonusRank];
                    egScore += EG_PASSED_PAWN_BONUS[bonusRank];
                }
                // --- END CHECK ---
            }

            tempPawns &= tempPawns - 1;
        }

        // Enemy Passed Pawns (Danger Calculation)
        long tempEnemy = enemyPawns;
        while (tempEnemy != 0) {
            int sq = Long.numberOfTrailingZeros(tempEnemy);
            int rank = sq / 8;
            int file = sq % 8;

            long frontSpan = 0L;
            // Check if passed relative to ME (myPawns blocking?)
            // If I am White, Enemy is Black (moving down). Front is rank-1..0
            if (white) {
                // Enemy is Black
                for (int r = rank - 1; r >= 0; r--) {
                    frontSpan |= (1L << (r * 8 + file));
                    if (file > 0) frontSpan |= (1L << (r * 8 + file - 1));
                    if (file < 7) frontSpan |= (1L << (r * 8 + file + 1));
                }
            } else {
                // Enemy is White
                for (int r = rank + 1; r < 8; r++) {
                    frontSpan |= (1L << (r * 8 + file));
                    if (file > 0) frontSpan |= (1L << (r * 8 + file - 1));
                    if (file < 7) frontSpan |= (1L << (r * 8 + file + 1));
                }
            }

            if ((frontSpan & myPawns) == 0) {
                // It is an enemy passed pawn
                // Calculate relative rank for the enemy
                // If I am White, Enemy is Black. Rank 1 is promotion-1 (Relative Rank 6).
                // Relative Rank = (EnemyColor == White) ? rank : (7 - rank)
                // EnemyColor is !white
                int relativeRank = (!white) ? rank : (7 - rank);

                int penalty = 0;
                switch (relativeRank) {
                    case 6 -> penalty = -200; // Rank 7 (about to promote)
                    case 5 -> penalty = -50;  // Rank 6
                    case 4 -> penalty = -20;  // Rank 5
                    case 3 -> penalty = -10;  // Rank 4
                }

                mgScore += penalty;
                egScore += penalty;
            }

            tempEnemy &= tempEnemy - 1;
        }

        return pack(mgScore, egScore);
    }

    private static long pack(int mg, int eg) {
        return ((long) mg << 32) | (eg & 0xFFFFFFFFL);
    }
}
