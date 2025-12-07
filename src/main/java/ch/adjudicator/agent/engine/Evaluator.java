package ch.adjudicator.agent.engine;

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
    private static final int CASTLING_RIGHT_BONUS = 45;
    private static final int HAS_CASTLED_BONUS = 50;
    
    // Piece-Square Tables for Middle Game
    // Values are from white's perspective, need to flip for black
    
    private static final int[] MG_PAWN_TABLE = {
        0,   0,   0,   0,   0,   0,   0,   0,
        98, 134,  61,  95,  68, 126,  34, -11,
        -6,   7,  26,  31,  65,  56,  25, -20,
        -14,  13,   6,  21,  23,  12,  17, -23,
        -27,  -2,  -5,  12,  17,   6,  10, -25,
        -26,  -4,  -4, -10,   3,   3,  33, -12,
        -35,  -1, -20, -23, -15,  24,  38, -22,
        0,   0,   0,   0,   0,   0,   0,   0
    };
    
    private static final int[] MG_KNIGHT_TABLE = {
        -167, -89, -34, -49,  61, -97, -15, -107,
        -73, -41,  72,  36,  23,  62,   7,  -17,
        -47,  60,  37,  65,  84, 129,  73,   44,
        -9,  17,  19,  53,  37,  69,  18,   22,
        -13,   4,  16,  13,  28,  19,  21,   -8,
        -23,  -9,  12,  10,  19,  17,  25,  -16,
        -29, -53, -12,  -3,  -1,  18, -14,  -19,
        -105, -21, -58, -33, -17, -28, -19,  -23
    };
    
    private static final int[] MG_BISHOP_TABLE = {
        -29,   4, -82, -37, -25, -42,   7,  -8,
        -26,  16, -18, -13,  30,  59,  18, -47,
        -16,  37,  43,  40,  35,  50,  37,  -2,
        -4,   5,  19,  50,  37,  37,   7,  -2,
        -6,  13,  13,  26,  34,  12,  10,   4,
        0,  15,  15,  15,  14,  27,  18,  10,
        4,  15,  16,   0,   7,  21,  33,   1,
        -33,  -3, -14, -21, -13, -12, -39, -21
    };
    
    private static final int[] MG_ROOK_TABLE = {
        32,  42,  32,  51,  63,   9,  31,  43,
        27,  32,  58,  62,  80,  67,  26,  44,
        -5,  19,  26,  36,  17,  45,  61,  16,
        -24, -11,   7,  26,  24,  35,  -8, -20,
        -36, -26, -12,  -1,   9,  -7,   6, -23,
        -45, -25, -16, -17,   3,   0,  -5, -33,
        -44, -16, -20,  -9,  -1,  11,  -6, -71,
        -19, -13,   1,  17,  16,   7, -37, -26
    };
    
    private static final int[] MG_QUEEN_TABLE = {
        -28,   0,  29,  12, -20,  44,  43,  45,
        -24, -39,  -5,   1, -16,  57,  28,  54,
        -13, -17,   7,   8,  29,  56,  47,  57,
        -27, -27, -16, -16,  -1,  17,  -2,   1,
        -9, -26,  -9, -10,  -2,  -4,   3,  -3,
        -14,   2, -11,  -2,  -5,   2,  14,   5,
        -35,  -8,  11,   2,   8,  15,  -3,   1,
        -1, -18,  -9,  10, -15, -25, -31, -50
    };
    
    private static final int[] MG_KING_TABLE = {
        -65,  23,  16, -15,  10,  20,  30,  13,
         29,  -1, -20,  -7, -50,  -4, -38, -29,
         -9,  24,   2, -16, -20,   6,  22, -22,
        -17, -20, -12, -27, -30, -25, -14, -36,
        -49,  -1, -27, -39, -46, -44, -33, -51,
        -14, -14, -22, -46, -44, -30, -15, -27,
        1,   7,  -8, -64, -43, -16,   9,   8,
        -15,  36,  12, -54,   8, -28,  24,  14
    };
    
    // Endgame piece-square tables
    private static final int[] EG_PAWN_TABLE = {
        0,   0,   0,   0,   0,   0,   0,   0,
        178, 173, 158, 134, 147, 132, 165, 187,
        94, 100,  85,  67,  56,  53,  82,  84,
        32,  24,  13,   5,  -2,   4,  17,  17,
        13,   9,  -3,  -7,  -7,  -8,   3,  -1,
        4,   7,  -6,   1,   0,  -5,  -1,  -8,
        13,   8,   8,  10,  13,   0,   2,  -7,
        0,   0,   0,   0,   0,   0,   0,   0
    };
    
    private static final int[] EG_KNIGHT_TABLE = {
        -58, -38, -13, -28, -31, -27, -63, -99,
        -25,  -8, -25,  -2,  -9, -25, -24, -52,
        -24, -20,  10,   9,  -1,  -9, -19, -41,
        -17,   3,  22,  22,  22,  11,   8, -18,
        -18,  -6,  16,  25,  16,  17,   4, -18,
        -23,  -3,  -1,  15,  10,  -3, -20, -22,
        -42, -20, -10,  -5,  -2, -20, -23, -44,
        -29, -51, -23, -15, -22, -18, -50, -64
    };
    
    private static final int[] EG_BISHOP_TABLE = {
        -14, -21, -11,  -8, -7,  -9, -17, -24,
        -8,  -4,   7, -12, -3, -13,  -4, -14,
        2,  -8,   0,  -1, -2,   6,   0,   4,
        -3,   9,  12,   9, 14,  10,   3,   2,
        -6,   3,  13,  19,  7,  10,  -3,  -9,
        -12,  -3,   8,  10, 13,   3,  -7, -15,
        -14, -18,  -7,  -1,  4,  -9, -15, -27,
        -23,  -9, -23,  -5, -9, -16,  -5, -17
    };
    
    private static final int[] EG_ROOK_TABLE = {
        13, 10, 18, 15, 12,  12,   8,   5,
        11, 13, 13, 11, -3,   3,   8,   3,
        7,  7,  7,  5,  4,  -3,  -5,  -3,
        4,  3, 13,  1,  2,   1,  -1,   2,
        3,  5,  8,  4, -5,  -6,  -8, -11,
        -4,  0, -5, -1, -7, -12,  -8, -16,
        -6, -6,  0,  2, -9,  -9, -11,  -3,
        -9,  2,  3, -1, -5, -13,   4, -20
    };
    
    private static final int[] EG_QUEEN_TABLE = {
        -9,  22,  22,  27,  27,  19,  10,  20,
        -17,  20,  32,  41,  58,  25,  30,   0,
        -20,   6,   9,  49,  47,  35,  19,   9,
        3,  22,  24,  45,  57,  40,  57,  36,
        -18,  28,  19,  47,  31,  34,  39,  23,
        -16, -27,  15,   6,   9,  17,  10,   5,
        -22, -23, -30, -16, -16, -23, -36, -32,
        -33, -28, -22, -43,  -5, -32, -20, -41
    };
    
    private static final int[] EG_KING_TABLE = {
        -74, -35, -18, -18, -11,  15,   4, -17,
        -12,  17,  14,  17,  17,  38,  23,  11,
        10,  17,  23,  15,  20,  45,  44,  13,
        -8,  22,  24,  27,  26,  33,  26,   3,
        -18,  -4,  21,  24,  27,  23,   9, -11,
        -19,  -3,  11,  21,  23,  16,   7,  -9,
        -27, -11,   4,  13,  14,   4,  -5, -17,
        -53, -34, -21, -11, -28, -14, -24, -43
    };
    
    // Mobility and Blocking constants
    private static final int MOBILITY_WEIGHT = 10;
    private static final int CENTRALITY_BONUS = 15;
    private static final int BLOCKING_PENALTY = 50;
    
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
    public static int evaluate(BoardStatus board) {
        int mgScore = 0;
        int egScore = 0;
        
        // Calculate phase based on material (0 = endgame, 256 = opening)
        int phase = calculatePhase(board);
        
        // Evaluate white pieces
        mgScore += evaluatePieces(board, true, true);
        egScore += evaluatePieces(board, true, false);
        mgScore += evaluateCastling(board, true);
        mgScore += evaluateMobility(board, true);
        mgScore += evaluateBlocking(board, true);
        
        // Evaluate black pieces
        mgScore -= evaluatePieces(board, false, true);
        egScore -= evaluatePieces(board, false, false);
        mgScore -= evaluateCastling(board, false);
        mgScore -= evaluateMobility(board, false);
        mgScore -= evaluateBlocking(board, false);
        
        // Interpolate between middlegame and endgame scores
        int score = (mgScore * phase + egScore * (256 - phase)) / 256;
        
        return board.isWhiteToMove() ? score : -score;
    }

    /**
     * Evaluate castling rights and status.
     */
    private static int evaluateCastling(BoardStatus board, boolean white) {
        int score = 0;
        boolean kRight = white ? board.isWhiteCastleKingSide() : board.isBlackCastleKingSide();
        boolean qRight = white ? board.isWhiteCastleQueenSide() : board.isBlackCastleQueenSide();
        long king = white ? board.getWhiteKing() : board.getBlackKing();

        // Castling rights bonus
        if (kRight) score += CASTLING_RIGHT_BONUS;
        if (qRight) score += CASTLING_RIGHT_BONUS;

        // Has castled bonus (approximate)
        if (!kRight && !qRight) {
            // Check if king is on castled squares
            int kingSq = Long.numberOfTrailingZeros(king);
            if (white) {
                if (kingSq == 6 || kingSq == 2) score += HAS_CASTLED_BONUS; // g1=6, c1=2
            } else {
                if (kingSq == 62 || kingSq == 58) score += HAS_CASTLED_BONUS; // g8=62, c8=58
            }
        }
        return score;
    }
    
    /**
     * Calculate game phase (256 = opening, 0 = endgame).
     */
    private static int calculatePhase(BoardStatus board) {
        int phase = 0;
        phase += Long.bitCount(board.getWhiteKnights() | board.getBlackKnights()) * 1;
        phase += Long.bitCount(board.getWhiteBishops() | board.getBlackBishops()) * 1;
        phase += Long.bitCount(board.getWhiteRooks() | board.getBlackRooks()) * 2;
        phase += Long.bitCount(board.getWhiteQueens() | board.getBlackQueens()) * 4;
        
        // Total material at start: 4 knights + 4 bishops + 4 rooks + 2 queens = 24
        // Scale to 256
        return Math.min(phase * 256 / 24, 256);
    }
    
    /**
     * Evaluate all pieces of one color.
     */
    private static int evaluatePieces(BoardStatus board, boolean white, boolean middlegame) {
        int score = 0;
        
        long pawns = white ? board.getWhitePawns() : board.getBlackPawns();
        long knights = white ? board.getWhiteKnights() : board.getBlackKnights();
        long bishops = white ? board.getWhiteBishops() : board.getBlackBishops();
        long rooks = white ? board.getWhiteRooks() : board.getBlackRooks();
        long queens = white ? board.getWhiteQueens() : board.getBlackQueens();
        
        score += evaluatePieceType(pawns, PAWN_VALUE, white, middlegame ? MG_PAWN_TABLE : EG_PAWN_TABLE);
        score += evaluatePieceType(knights, KNIGHT_VALUE, white, middlegame ? MG_KNIGHT_TABLE : EG_KNIGHT_TABLE);
        score += evaluatePieceType(bishops, BISHOP_VALUE, white, middlegame ? MG_BISHOP_TABLE : EG_BISHOP_TABLE);
        score += evaluatePieceType(rooks, ROOK_VALUE, white, middlegame ? MG_ROOK_TABLE : EG_ROOK_TABLE);
        score += evaluatePieceType(queens, QUEEN_VALUE, white, middlegame ? MG_QUEEN_TABLE : EG_QUEEN_TABLE);
        
        // King position evaluation
        long king = white ? board.getWhiteKing() : board.getBlackKing();
        if (king != 0) {
            int square = Long.numberOfTrailingZeros(king);
            int tableSquare = white ? square : (square ^ 56); // Flip for black
            score += middlegame ? MG_KING_TABLE[tableSquare] : EG_KING_TABLE[tableSquare];
        }
        
        return score;
    }
    
    /**
     * Evaluate a specific piece type.
     */
    private static int evaluatePieceType(long bitboard, int value, boolean white, int[] table) {
        int score = 0;
        
        while (bitboard != 0) {
            int square = Long.numberOfTrailingZeros(bitboard);
            int tableSquare = white ? square : (square ^ 56); // Flip rank for black
            
            score += value + table[tableSquare];
            
            bitboard &= bitboard - 1; // Clear the lowest set bit
        }
        
        return score;
    }

    private static int popCount(long bitboard) {
        return Long.bitCount(bitboard);
    }

    private static int evaluateBlocking(BoardStatus board, boolean white) {
        int score = 0;
        long king = white ? board.getWhiteKing() : board.getBlackKing();
        long pieces = white ? (board.getWhitePawns() | board.getWhiteKnights() | board.getWhiteBishops() | board.getWhiteRooks() | board.getWhiteQueens())
                            : (board.getBlackPawns() | board.getBlackKnights() | board.getBlackBishops() | board.getBlackRooks() | board.getBlackQueens());

        // King Blocking Penalty
        if (white) {
            // Check if King is on e2 (index 12)
            if ((king & (1L << 12)) != 0) {
                // Check if own pieces are on d1 (3) or f1 (5)
                if ((pieces & (1L << 3)) != 0 || (pieces & (1L << 5)) != 0) {
                    score -= BLOCKING_PENALTY;
                }
            }
        } else {
            // Check if King is on e7 (index 52)
            if ((king & (1L << 52)) != 0) {
                // Check if own pieces are on d8 (59) or f8 (61)
                if ((pieces & (1L << 59)) != 0 || (pieces & (1L << 61)) != 0) {
                    score -= BLOCKING_PENALTY;
                }
            }
        }
        return score;
    }

    private static int evaluateMobility(BoardStatus board, boolean white) {
        int score = 0;
        long ownPieces = white ? (board.getWhitePawns() | board.getWhiteKnights() | board.getWhiteBishops() | board.getWhiteRooks() | board.getWhiteQueens() | board.getWhiteKing())
                               : (board.getBlackPawns() | board.getBlackKnights() | board.getBlackBishops() | board.getBlackRooks() | board.getBlackQueens() | board.getBlackKing());
        
        // Knights
        long knights = white ? board.getWhiteKnights() : board.getBlackKnights();
        while (knights != 0) {
            int sq = Long.numberOfTrailingZeros(knights);
            long attacks = KNIGHT_MOVES[sq] & ~ownPieces;
            score += popCount(attacks) * MOBILITY_WEIGHT;
            knights &= knights - 1;
        }

        // Bishops/Rooks Centrality
        // Squares: d4(27), e4(28), d5(35), e5(36)
        // We give bonus if the piece controls any of these.
        // Simplified: Check if piece is on a line that intersects center.
        
        long rooks = white ? board.getWhiteRooks() : board.getBlackRooks();
        while (rooks != 0) {
            int sq = Long.numberOfTrailingZeros(rooks);
            int rank = sq / 8;
            int file = sq % 8;
            
            // d=3, e=4. Rank 4=3, Rank 5=4.
            if (file == 3 || file == 4 || rank == 3 || rank == 4) {
                 // Counts how many center squares it theoretically controls
                 // If on d-file, it controls d4 and d5 (2 squares)
                 // If on Rank 4, it controls d4 and e4 (2 squares)
                 // If on d4, it controls d4, d5, e4 (Wait, intersection?)
                 // Let's just give fixed bonus for "Centrality" if it attacks ANY center square?
                 // "Give a small bonus for distinct squares controlled"
                 
                 int controlled = 0;
                 if (file == 3) controlled += 2; // d4, d5
                 if (file == 4) controlled += 2; // e4, e5
                 if (rank == 3) controlled += 2; // d4, e4
                 if (rank == 4) controlled += 2; // d5, e5
                 
                 // If on d4 (file 3, rank 3): 2 + 2 = 4. Correct (d4, d5, d4, e4). d4 counted twice?
                 // "distinct squares".
                 // d4 controls: d4(self?), d5, e4...
                 // Actually, "control" usually means attacking. You don't attack your own square.
                 // But for this proxy, let's keep it simple.
                 // If on d-file, attacks d4, d5.
                 // If on rank 4, attacks d4, e4.
                 
                 // Let's count explicitly.
                 boolean hitsD4 = (file == 3 || rank == 3);
                 boolean hitsE4 = (file == 4 || rank == 3);
                 boolean hitsD5 = (file == 3 || rank == 4);
                 boolean hitsE5 = (file == 4 || rank == 4);
                 
                 int distinct = (hitsD4 ? 1 : 0) + (hitsE4 ? 1 : 0) + (hitsD5 ? 1 : 0) + (hitsE5 ? 1 : 0);
                 score += distinct * CENTRALITY_BONUS;
            }
            rooks &= rooks - 1;
        }
        
        long bishops = white ? board.getWhiteBishops() : board.getBlackBishops();
        while (bishops != 0) {
            int sq = Long.numberOfTrailingZeros(bishops);
            int r = sq / 8;
            int c = sq % 8;
            
            // Diagonals:
            // Main: r - c = const. Anti: r + c = const.
            // d4(3,3): r-c=0, r+c=6.
            // e4(3,4): r-c=-1, r+c=7.
            // d5(4,3): r-c=1, r+c=7.
            // e5(4,4): r-c=0, r+c=8.
            
            boolean hitsD4 = (r - c == 0) || (r + c == 6);
            boolean hitsE4 = (r - c == -1) || (r + c == 7);
            boolean hitsD5 = (r - c == 1) || (r + c == 7);
            boolean hitsE5 = (r - c == 0) || (r + c == 8);
            
            int distinct = (hitsD4 ? 1 : 0) + (hitsE4 ? 1 : 0) + (hitsD5 ? 1 : 0) + (hitsE5 ? 1 : 0);
            score += distinct * CENTRALITY_BONUS;

            bishops &= bishops - 1;
        }
        
        // Queens? Not requested, but usually Queens have mobility too.
        // Plan says "Bishops/Rooks: ...". It doesn't explicitly exclude Queens but usually Queens are handled similarly or separately.
        // I'll stick to Knights and Bishops/Rooks as requested.
        
        return score;
    }
}
