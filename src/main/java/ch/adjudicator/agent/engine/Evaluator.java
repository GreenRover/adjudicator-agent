package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import ch.adjudicator.agent.engine.board.AttackLookups;
import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Side;

/**
 * PeSTO (Piece Square Tables Only) Evaluation.
 * Provides fast static evaluation using piece values and positional bonuses.
 */
public class Evaluator {
    // Piece values (centipawns)
    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;

    // Castling bonuses
    private static final int MG_CASTLING_RIGHT_BONUS = 45;
    private static final int EG_CASTLING_RIGHT_BONUS = 0;

    private static final int MG_HAS_CASTLED_BONUS = 100;
    private static final int EG_HAS_CASTLED_BONUS = 0;

    // King Safety
    private static final int MG_PAWN_SHIELD_BONUS = 10;
    private static final int MG_MISSING_PAWN_SHIELD_PENALTY = -20;
    private static final int MG_KING_CENTER_PENALTY = -50;
    // Attacker count: 0, 1, 2, 3, 4+
    private static final int[] MG_ATTACKER_PENALTY = {0, 0, -25, -75, -150};

    // Pawn Structure
    private static final int MG_ISOLATED_PAWN_PENALTY = -10;
    private static final int EG_ISOLATED_PAWN_PENALTY = -20;

    private static final int MG_DOUBLED_PAWN_PENALTY = -10;
    private static final int EG_DOUBLED_PAWN_PENALTY = -20;

    // Passed Pawn Bonus: {Rank 0..7}
    private static final int[] MG_PASSED_PAWN_BONUS = {0, 5, 10, 20, 35, 60, 100, 0};
    private static final int[] EG_PASSED_PAWN_BONUS = {0, 10, 20, 40, 80, 160, 240, 0};

    // Mobility
    private static final int MG_MOBILITY_WEIGHT = 10;
    private static final int EG_MOBILITY_WEIGHT = 10;

    // Centrality
    private static final int MG_CENTRALITY_BONUS = 15;
    private static final int EG_CENTRALITY_BONUS = 10;

    // Blocking (King safety) - mostly MG
    private static final int MG_BLOCKING_PENALTY = 50;
    private static final int EG_BLOCKING_PENALTY = 0;

    private static final int MG_KING_OPEN_FILE_PENALTY = -25;

    private static final long[] FILE_MASKS = {
            0x0101010101010101L, 0x0202020202020202L, 0x0404040404040404L, 0x0808080808080808L,
            0x1010101010101010L, 0x2020202020202020L, 0x4040404040404040L, 0x8080808080808080L
    };

    // Piece-Square Tables for Middle Game
    // Values are from white's perspective, need to flip for black

    private static final int[] MG_PAWN_TABLE = {
            0, 0, 0, 0, 0, 0, 0, 0,
            98, 134, 61, 95, 68, 126, 34, -11,
            -6, 7, 26, 31, 65, 56, 25, -20,
            -14, 13, 6, 21, 23, 12, 17, -23,
            -27, -2, -5, 12, 17, 6, 10, -25,
            -26, -4, -4, -10, 3, 3, 33, -12,
            -35, -1, -20, -23, -15, 24, 38, -22,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    private static final int[] MG_KNIGHT_TABLE = {
            -167, -89, -34, -49, 61, -97, -15, -107,
            -73, -41, 72, 36, 23, 62, 7, -17,
            -47, 60, 37, 65, 84, 129, 73, 44,
            -9, 17, 19, 53, 37, 69, 18, 22,
            -13, 4, 16, 13, 28, 19, 21, -8,
            -23, -9, 12, 10, 19, 17, 25, -16,
            -29, -53, -12, -3, -1, 18, -14, -19,
            -105, -21, -58, -33, -17, -28, -19, -23
    };

    private static final int[] MG_BISHOP_TABLE = {
            -29, 4, -82, -37, -25, -42, 7, -8,
            -26, 16, -18, -13, 30, 59, 18, -47,
            -16, 37, 43, 40, 35, 50, 37, -2,
            -4, 5, 19, 50, 37, 37, 7, -2,
            -6, 13, 13, 26, 34, 12, 10, 4,
            0, 15, 15, 15, 14, 27, 18, 10,
            4, 15, 16, 0, 7, 21, 33, 1,
            -33, -3, -14, -21, -13, -12, -39, -21
    };

    private static final int[] MG_ROOK_TABLE = {
            32, 42, 32, 51, 63, 9, 31, 43,
            27, 32, 58, 62, 80, 67, 26, 44,
            -5, 19, 26, 36, 17, 45, 61, 16,
            -24, -11, 7, 26, 24, 35, -8, -20,
            -36, -26, -12, -1, 9, -7, 6, -23,
            -45, -25, -16, -17, 3, 0, -5, -33,
            -44, -16, -20, -9, -1, 11, -6, -71,
            -19, -13, 1, 17, 16, 7, -37, -26
    };

    private static final int[] MG_QUEEN_TABLE = {
            -28, 0, 29, 12, -20, 44, 43, 45,
            -24, -39, -5, 1, -16, 57, 28, 54,
            -13, -17, 7, 8, 29, 56, 47, 57,
            -27, -27, -16, -16, -1, 17, -2, 1,
            -9, -26, -9, -10, -2, -4, 3, -3,
            -14, 2, -11, -2, -5, 2, 14, 5,
            -35, -8, 11, 2, 8, 15, -3, 1,
            -1, -18, -9, 10, -15, -25, -31, -50
    };

    private static final int[] MG_KING_TABLE = {
            -65, 23, 16, -15, 10, 20, 30, 13,
            29, -1, -20, -7, -50, -4, -38, -29,
            -9, 24, 2, -16, -20, 6, 22, -22,
            -17, -20, -12, -27, -30, -25, -14, -36,
            -49, -1, -27, -39, -46, -44, -33, -51,
            -14, -14, -22, -46, -44, -30, -15, -27,
            1, 7, -8, -64, -43, -16, 9, 8,
            -15, 36, 12, -54, 8, -28, 24, 14
    };

    // Endgame piece-square tables
    private static final int[] EG_PAWN_TABLE = {
            0, 0, 0, 0, 0, 0, 0, 0,
            178, 173, 158, 134, 147, 132, 165, 187,
            94, 100, 85, 67, 56, 53, 82, 84,
            32, 24, 13, 5, -2, 4, 17, 17,
            13, 9, -3, -7, -7, -8, 3, -1,
            4, 7, -6, 1, 0, -5, -1, -8,
            13, 8, 8, 10, 13, 0, 2, -7,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    private static final int[] EG_KNIGHT_TABLE = {
            -58, -38, -13, -28, -31, -27, -63, -99,
            -25, -8, -25, -2, -9, -25, -24, -52,
            -24, -20, 10, 9, -1, -9, -19, -41,
            -17, 3, 22, 22, 22, 11, 8, -18,
            -18, -6, 16, 25, 16, 17, 4, -18,
            -23, -3, -1, 15, 10, -3, -20, -22,
            -42, -20, -10, -5, -2, -20, -23, -44,
            -29, -51, -23, -15, -22, -18, -50, -64
    };

    private static final int[] EG_BISHOP_TABLE = {
            -14, -21, -11, -8, -7, -9, -17, -24,
            -8, -4, 7, -12, -3, -13, -4, -14,
            2, -8, 0, -1, -2, 6, 0, 4,
            -3, 9, 12, 9, 14, 10, 3, 2,
            -6, 3, 13, 19, 7, 10, -3, -9,
            -12, -3, 8, 10, 13, 3, -7, -15,
            -14, -18, -7, -1, 4, -9, -15, -27,
            -23, -9, -23, -5, -9, -16, -5, -17
    };

    private static final int[] EG_ROOK_TABLE = {
            13, 10, 18, 15, 12, 12, 8, 5,
            11, 13, 13, 11, -3, 3, 8, 3,
            7, 7, 7, 5, 4, -3, -5, -3,
            4, 3, 13, 1, 2, 1, -1, 2,
            3, 5, 8, 4, -5, -6, -8, -11,
            -4, 0, -5, -1, -7, -12, -8, -16,
            -6, -6, 0, 2, -9, -9, -11, -3,
            -9, 2, 3, -1, -5, -13, 4, -20
    };

    private static final int[] EG_QUEEN_TABLE = {
            -9, 22, 22, 27, 27, 19, 10, 20,
            -17, 20, 32, 41, 58, 25, 30, 0,
            -20, 6, 9, 49, 47, 35, 19, 9,
            3, 22, 24, 45, 57, 40, 57, 36,
            -18, 28, 19, 47, 31, 34, 39, 23,
            -16, -27, 15, 6, 9, 17, 10, 5,
            -22, -23, -30, -16, -16, -23, -36, -32,
            -33, -28, -22, -43, -5, -32, -20, -41
    };

    private static final int[] EG_KING_TABLE = {
            -74, -35, -18, -18, -11, 15, 4, -17,
            -12, 17, 14, 17, 17, 38, 23, 11,
            10, 17, 23, 15, 20, 45, 44, 13,
            -8, 22, 24, 27, 26, 33, 26, 3,
            -18, -4, 21, 24, 27, 23, 9, -11,
            -19, -3, 11, 21, 23, 16, 7, -9,
            -27, -11, 4, 13, 14, 4, -5, -17,
            -53, -34, -21, -11, -28, -14, -24, -43
    };


    private static final long[] KNIGHT_MOVES = new long[64];

    static {
        // Initialize Knight moves
        int[] offsets = {-17, -15, -10, -6, 6, 10, 15, 17};
        for (int i = 0; i < 64; i++) {
            long moves = 0;
            int r = i / 8;
            int c = i % 8;
            for (int offset : offsets) {
                int dest = i + offset;
                if (dest >= 0 && dest < 64) {
                    int dr = dest / 8;
                    int dc = dest % 8;
                    // Check if move is valid (max 2 squares distance in any direction)
                    if (Math.abs(dr - r) <= 2 && Math.abs(dc - c) <= 2) {
                        moves |= (1L << dest);
                    }
                }
            }
            KNIGHT_MOVES[i] = moves;
        }
    }

    /**
     * Evaluate position from white's perspective.
     * Positive score = white is better, negative = black is better.
     */
    public static int evaluate(Bitboard board) {
        // MG score in upper 32 bits, EG score in lower 32 bits
        long whiteScore = 0;
        long blackScore = 0;

        // Calculate phase based on material (0 = endgame, 256 = opening)
        int phase = calculatePhase(board);

        // Evaluate white pieces
        whiteScore = add(whiteScore, evaluatePieces(board, true));
        whiteScore = add(whiteScore, evaluateCastling(board, true));
        whiteScore = add(whiteScore, evaluateMobility(board, true));
        whiteScore = add(whiteScore, evaluateBlocking(board, true));
        whiteScore = add(whiteScore, evaluateKingSafety(board, true));
        whiteScore = add(whiteScore, evaluatePawnStructure(board, true));

        // Evaluate black pieces
        blackScore = add(blackScore, evaluatePieces(board, false));
        blackScore = add(blackScore, evaluateCastling(board, false));
        blackScore = add(blackScore, evaluateMobility(board, false));
        blackScore = add(blackScore, evaluateBlocking(board, false));
        blackScore = add(blackScore, evaluateKingSafety(board, false));
        blackScore = add(blackScore, evaluatePawnStructure(board, false));

        int mgScore = unpackMg(whiteScore) - unpackMg(blackScore);
        int egScore = unpackEg(whiteScore) - unpackEg(blackScore);

        // Interpolate between middlegame and endgame scores
        // Formula: (mg * phase + eg * (256 - phase)) / 256
        int score = (mgScore * phase + egScore * (256 - phase)) / 256;

        return (board.getSideToMove() == Side.WHITE) ? score : -score;
    }

    /**
     * Evaluate castling rights and status.
     */
    private static long evaluateCastling(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;

        CastleRight cr = board.getCastleRight(white ? Side.WHITE : Side.BLACK);
        boolean kRight = cr.equals(CastleRight.KING_SIDE) || cr.equals(CastleRight.KING_AND_QUEEN_SIDE);
        boolean qRight = cr.equals(CastleRight.QUEEN_SIDE) || cr.equals(CastleRight.KING_AND_QUEEN_SIDE);
        
        long king = white ? board.whiteKing : board.blackKing;

        // Castling rights bonus
        if (kRight) {
            mgScore += MG_CASTLING_RIGHT_BONUS;
            egScore += EG_CASTLING_RIGHT_BONUS;
        }
        if (qRight) {
            mgScore += MG_CASTLING_RIGHT_BONUS;
            egScore += EG_CASTLING_RIGHT_BONUS;
        }

        // Has castled bonus (approximate)
        if (!kRight && !qRight) {
            // Check if king is on castled squares
            int kingSq = Long.numberOfTrailingZeros(king);
            if (white) {
                if (kingSq == 6 || kingSq == 2) {
                    mgScore += MG_HAS_CASTLED_BONUS;
                    egScore += EG_HAS_CASTLED_BONUS;
                }
            } else {
                if (kingSq == 62 || kingSq == 58) {
                    mgScore += MG_HAS_CASTLED_BONUS;
                    egScore += EG_HAS_CASTLED_BONUS;
                }
            }
        }
        return pack(mgScore, egScore);
    }

    private static long evaluateKingSafety(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0; // King safety matters less in endgame

        long kingBitboard = white ? board.whiteKing : board.blackKing;
        long ownPawns = white ? board.whitePawns : board.blackPawns;

        int kingSq = Long.numberOfTrailingZeros(kingBitboard);
        if (kingSq < 64) {
            int file = kingSq % 8; // 0-7
            int rank = kingSq / 8; // 0-7

            // 1. Pawn Shield (Back rank only)
            boolean isBackRank = white ? (rank == 0) : (rank == 7);
            if (isBackRank) {
                if (file <= 2 || file >= 5) { // Files A-C or F-H
                    // Check pawns in front (Rank 2 for white, Rank 7 for black)
                    // We check 3 files: file-1, file, file+1
                    for (int f = Math.max(0, file - 1); f <= Math.min(7, file + 1); f++) {
                        int shieldSq = white ? (8 + f) : (48 + f);
                        if ((ownPawns & (1L << shieldSq)) != 0) {
                            mgScore += MG_PAWN_SHIELD_BONUS;
                        } else {
                            mgScore += MG_MISSING_PAWN_SHIELD_PENALTY;
                        }
                    }
                }
            }

            // 2. Center King Penalty (Middlegame)
            // Files D(3) or E(4)
            if (file == 3 || file == 4) {
                mgScore += MG_KING_CENTER_PENALTY;
            }

            // 3. Attacker Count
            // Get squares adjacent to king (King Zone)
            long kingZone = AttackLookups.KING_ATTACKS[kingSq];

            // Iterate enemy pieces to count attackers
            int attackerCount = 0;
            // Enemy pieces
            long enemyKnights = white ? board.blackKnights : board.whiteKnights;
            long enemyBishops = white ? board.blackBishops : board.whiteBishops;
            long enemyRooks = white ? board.blackRooks : board.whiteRooks;
            long enemyQueens = white ? board.blackQueens : board.whiteQueens;
            long enemyPawns = white ? board.blackPawns : board.whitePawns;

            long occupied = board.occupiedSquares;

            // Knights
            long knights = enemyKnights;
            while (knights != 0) {
                int sq = Long.numberOfTrailingZeros(knights);
                if ((AttackLookups.KNIGHT_ATTACKS[sq] & kingZone) != 0) attackerCount++;
                knights &= knights - 1;
            }

            // Bishops + Queens (Sliding)
            long bq = enemyBishops | enemyQueens;
            while (bq != 0) {
                int sq = Long.numberOfTrailingZeros(bq);
                if ((AttackLookups.getBishopAttacks(sq, occupied) & kingZone) != 0) attackerCount++;
                bq &= bq - 1;
            }

            // Rooks + Queens (Sliding)
            long rq = enemyRooks | enemyQueens;
            while (rq != 0) {
                int sq = Long.numberOfTrailingZeros(rq);
                if ((AttackLookups.getRookAttacks(sq, occupied) & kingZone) != 0) attackerCount++;
                rq &= rq - 1;
            }

            // Pawns
            // Enemy pawns attack capture squares.
            // If I am White, enemy is Black. Black pawns attack "South".
            int enemySideOrd = white ? 1 : 0; // 0=White, 1=Black
            long pawns = enemyPawns;
            while (pawns != 0) {
                int sq = Long.numberOfTrailingZeros(pawns);
                if ((AttackLookups.PAWN_ATTACKS[enemySideOrd][sq] & kingZone) != 0) attackerCount++;

                int pawnRank = sq / 8;
                int pawnFile = sq % 8;
                int distance = Math.max(Math.abs(pawnRank - rank), Math.abs(pawnFile - file));
                if (distance <= 2) {
                    mgScore -= 50; // Heavy penalty for enemy pawns near king
                }

                pawns &= pawns - 1;
            }

            if (attackerCount > 0) {
                int penaltyIndex = Math.min(attackerCount, MG_ATTACKER_PENALTY.length - 1);
                mgScore += MG_ATTACKER_PENALTY[penaltyIndex];
            }
        }

        return pack(mgScore, egScore);
    }

    /**
     * Calculate game phase (256 = opening, 0 = endgame).
     */
    private static int calculatePhase(Bitboard board) {
        int phase = 0;
        phase += Long.bitCount(board.whiteKnights | board.blackKnights);
        phase += Long.bitCount(board.whiteBishops | board.blackBishops);
        phase += Long.bitCount(board.whiteRooks | board.blackRooks) * 2;
        phase += Long.bitCount(board.whiteQueens | board.blackQueens) * 4;

        // Total material at start: 4 knights + 4 bishops + 4 rooks + 2 queens = 24
        // Scale to 256
        return Math.min(phase * 256 / 24, 256);
    }

    /**
     * Evaluate all pieces of one color.
     */
    private static long evaluatePieces(Bitboard board, boolean white) {
        long totalScore = 0;

        long pawns = white ? board.whitePawns : board.blackPawns;
        long knights = white ? board.whiteKnights : board.blackKnights;
        long bishops = white ? board.whiteBishops : board.blackBishops;
        long rooks = white ? board.whiteRooks : board.blackRooks;
        long queens = white ? board.whiteQueens : board.blackQueens;

        totalScore = add(totalScore, evaluatePieceType(pawns, PAWN_VALUE, white, MG_PAWN_TABLE, EG_PAWN_TABLE));
        totalScore = add(totalScore, evaluatePieceType(knights, KNIGHT_VALUE, white, MG_KNIGHT_TABLE, EG_KNIGHT_TABLE));
        totalScore = add(totalScore, evaluatePieceType(bishops, BISHOP_VALUE, white, MG_BISHOP_TABLE, EG_BISHOP_TABLE));
        totalScore = add(totalScore, evaluatePieceType(rooks, ROOK_VALUE, white, MG_ROOK_TABLE, EG_ROOK_TABLE));
        totalScore = add(totalScore, evaluatePieceType(queens, QUEEN_VALUE, white, MG_QUEEN_TABLE, EG_QUEEN_TABLE));

        // Bishop Pair Bonus
        if (Long.bitCount(bishops) >= 2) {
            totalScore = add(totalScore, pack(50, 50));
        }

        // Rook on Open File Bonus
        long r = rooks;
        while (r != 0) {
            int sq = Long.numberOfTrailingZeros(r);
            r &= r - 1;
            int file = sq % 8;
            long fileMask = 0x0101010101010101L << file;
            long friendlyPawns = white ? board.whitePawns : board.blackPawns;
            if ((friendlyPawns & fileMask) == 0) {
                totalScore = add(totalScore, pack(20, 20));
            }
        }

        // King position evaluation
        long king = white ? board.whiteKing : board.blackKing;
        if (king != 0) {
            int square = Long.numberOfTrailingZeros(king);
            int tableSquare = white ? (square ^ 56) : square; // Flip for white
            totalScore = add(totalScore, pack(MG_KING_TABLE[tableSquare], EG_KING_TABLE[tableSquare]));
        }

        return totalScore;
    }

    /**
     * Evaluate a specific piece type.
     */
    private static long evaluatePieceType(long bitboard, int value, boolean white, int[] mgTable, int[] egTable) {
        int mgScore = 0;
        int egScore = 0;

        while (bitboard != 0) {
            int square = Long.numberOfTrailingZeros(bitboard);
            int tableSquare = white ? (square ^ 56) : square; // Flip rank for white (Table is R8->R1)

            mgScore += value + mgTable[tableSquare];
            egScore += value + egTable[tableSquare];

            bitboard &= bitboard - 1; // Clear the lowest set bit
        }

        return pack(mgScore, egScore);
    }

    private static int popCount(long bitboard) {
        return Long.bitCount(bitboard);
    }

    private static long evaluateBlocking(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;

        long king = white ? board.whiteKing : board.blackKing;
        long pieces = white ? (board.whitePawns | board.whiteKnights | board.whiteBishops | board.whiteRooks | board.whiteQueens)
                : (board.blackPawns | board.blackKnights | board.blackBishops | board.blackRooks | board.blackQueens);

        // King Blocking Penalty
        if (white) {
            // Check if King is on e2 (index 12)
            if ((king & (1L << 12)) != 0) {
                // Check if own pieces are on d1 (3) or f1 (5)
                if ((pieces & (1L << 3)) != 0 || (pieces & (1L << 5)) != 0) {
                    mgScore -= MG_BLOCKING_PENALTY;
                }
            }
        } else {
            // Check if King is on e7 (index 52)
            if ((king & (1L << 52)) != 0) {
                // Check if own pieces are on d8 (59) or f8 (61)
                if ((pieces & (1L << 59)) != 0 || (pieces & (1L << 61)) != 0) {
                    mgScore -= MG_BLOCKING_PENALTY;
                }
            }
        }
        return pack(mgScore, egScore);
    }

    private static long evaluateMobility(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;

        long ownPieces = white ? (board.whitePawns | board.whiteKnights | board.whiteBishops | board.whiteRooks | board.whiteQueens | board.whiteKing)
                : (board.blackPawns | board.blackKnights | board.blackBishops | board.blackRooks | board.blackQueens | board.blackKing);

        // Knights
        long knights = white ? board.whiteKnights : board.blackKnights;
        while (knights != 0) {
            int sq = Long.numberOfTrailingZeros(knights);
            long attacks = KNIGHT_MOVES[sq] & ~ownPieces;
            int moves = popCount(attacks);

            mgScore += moves * MG_MOBILITY_WEIGHT;
            egScore += moves * EG_MOBILITY_WEIGHT;

            knights &= knights - 1;
        }

        // Bishops/Rooks Centrality
        long rooks = white ? board.whiteRooks : board.blackRooks;
        while (rooks != 0) {
            int sq = Long.numberOfTrailingZeros(rooks);
            int rank = sq / 8;
            int file = sq % 8;

            if (file == 3 || file == 4 || rank == 3 || rank == 4) {
                int centerBonus = 0;
                if (file == 3 || file == 4) centerBonus++;
                if (rank == 3 || rank == 4) centerBonus++;

                mgScore += centerBonus * MG_CENTRALITY_BONUS;
                egScore += centerBonus * EG_CENTRALITY_BONUS;
            }
            rooks &= rooks - 1;
        }

        long bishops = white ? board.whiteBishops : board.blackBishops;
        while (bishops != 0) {
            int sq = Long.numberOfTrailingZeros(bishops);
            int r = sq / 8;
            int c = sq % 8;

            boolean hitsD4 = (r - c == 0) || (r + c == 6);
            boolean hitsE4 = (r - c == -1) || (r + c == 7);
            boolean hitsD5 = (r - c == 1) || (r + c == 7);
            boolean hitsE5 = (r - c == 0) || (r + c == 8);

            int distinct = (hitsD4 ? 1 : 0) + (hitsE4 ? 1 : 0) + (hitsD5 ? 1 : 0) + (hitsE5 ? 1 : 0);

            mgScore += distinct * MG_CENTRALITY_BONUS;
            egScore += distinct * EG_CENTRALITY_BONUS;

            bishops &= bishops - 1;
        }

        return pack(mgScore, egScore);
    }

    private static long evaluatePawnStructure(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;

        long myPawns = white ? board.whitePawns : board.blackPawns;
        long enemyPawns = white ? board.blackPawns : board.whitePawns;

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
                int bonusRank = white ? rank : (7 - rank);
                mgScore += MG_PASSED_PAWN_BONUS[bonusRank];
                egScore += EG_PASSED_PAWN_BONUS[bonusRank];
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

    // --- Helper functions for packed scores ---

    private static long pack(int mg, int eg) {
        return (((long) mg) << 32) | (eg & 0xFFFFFFFFL);
    }

    private static int unpackMg(long score) {
        return (int) (score >>> 32);
    }

    private static int unpackEg(long score) {
        return (int) score;
    }

    private static long add(long score1, long score2) {
        int mg = unpackMg(score1) + unpackMg(score2);
        int eg = unpackEg(score1) + unpackEg(score2);
        return pack(mg, eg);
    }
}
