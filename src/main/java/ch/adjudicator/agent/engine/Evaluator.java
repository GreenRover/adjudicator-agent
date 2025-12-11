package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import ch.adjudicator.agent.engine.board.AttackLookups;
import ch.adjudicator.agent.engine.eval.KingSafety;
import ch.adjudicator.agent.engine.eval.PawnStructure;
import static ch.adjudicator.agent.engine.eval.EvaluationConstants.*;
import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;

/**
 * PeSTO (Piece Square Tables Only) Evaluation.
 * Provides fast static evaluation using piece values and positional bonuses.
 */
public class Evaluator {



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
        int mgScore = board.getMgPestoScore();
        int egScore = board.getEgPestoScore();

        // Calculate phase based on material (0 = endgame, 256 = opening)
        int phase = calculatePhase(board);

        long whiteAdditional = 0;
        long blackAdditional = 0;

        // Evaluate white pieces
        whiteAdditional = add(whiteAdditional, evaluateCastling(board, true));
        whiteAdditional = add(whiteAdditional, evaluateMobilityFast(board, true));
        whiteAdditional = add(whiteAdditional, evaluateBlocking(board, true));
        whiteAdditional = add(whiteAdditional, KingSafety.evaluate(board, true));
        whiteAdditional = add(whiteAdditional, PawnStructure.evaluate(board, true));

        // Evaluate black pieces
        blackAdditional = add(blackAdditional, evaluateCastling(board, false));
        blackAdditional = add(blackAdditional, evaluateMobilityFast(board, false));
        blackAdditional = add(blackAdditional, evaluateBlocking(board, false));
        blackAdditional = add(blackAdditional, KingSafety.evaluate(board, false));
        blackAdditional = add(blackAdditional, PawnStructure.evaluate(board, false));

        mgScore += unpackMg(whiteAdditional) - unpackMg(blackAdditional);
        egScore += unpackEg(whiteAdditional) - unpackEg(blackAdditional);

        // Interpolate between middlegame and endgame scores
        // Formula: (mg * phase + eg * (256 - phase)) / 256
        int score = (mgScore * phase + egScore * (256 - phase)) / 256;

        return (board.getSideToMove() == Side.WHITE) ? score : -score;
    }

    private static long evaluateMobilityFast(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;
        long occupied = board.getOccupiedSquares();
        long myPieces = board.getBitboard(white ? Side.WHITE : Side.BLACK);

        // --- ADD START: Knight Mobility ---
        long knights = white ? board.getBitboard(Piece.WHITE_KNIGHT) : board.getBitboard(Piece.BLACK_KNIGHT);
        while (knights != 0) {
            int sq = Long.numberOfTrailingZeros(knights);
            long attacks = AttackLookups.KNIGHT_ATTACKS[sq];
            // Count attacks that don't hit own pieces
            int mobility = Long.bitCount(attacks & ~myPieces);

            // Knights are worth slightly less per square than sliders
            mgScore += mobility * (MG_MOBILITY_WEIGHT - 5);
            egScore += mobility * (EG_MOBILITY_WEIGHT - 5);
            knights &= knights - 1;
        }
        // --- ADD END ---

        // --- ADD START: Bishop Mobility ---
        long bishops = white ? board.getBitboard(Piece.WHITE_BISHOP) : board.getBitboard(Piece.BLACK_BISHOP);
        while (bishops != 0) {
            int sq = Long.numberOfTrailingZeros(bishops);
            long attacks = AttackLookups.getBishopAttacks(sq, occupied);
            int mobility = Long.bitCount(attacks & ~myPieces);

            mgScore += mobility * MG_MOBILITY_WEIGHT;
            egScore += mobility * EG_MOBILITY_WEIGHT;
            bishops &= bishops - 1;
        }
        // --- ADD END ---

        long rooks = white ? board.getBitboard(Piece.WHITE_ROOK) : board.getBitboard(Piece.BLACK_ROOK);
        while (rooks != 0) {
            int sq = Long.numberOfTrailingZeros(rooks);
            long attacks = AttackLookups.getRookAttacks(sq, occupied);
            int mobility = Long.bitCount(attacks & ~myPieces);

            // Weight: use configured weight
            mgScore += mobility * MG_MOBILITY_WEIGHT;
            egScore += mobility * EG_MOBILITY_WEIGHT;

            rooks &= rooks - 1;
        }

        return pack(mgScore, egScore);
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
        
        long king = white ? board.getBitboard(Piece.WHITE_KING) : board.getBitboard(Piece.BLACK_KING);

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


    /**
     * Calculate game phase (256 = opening, 0 = endgame).
     */
    private static int calculatePhase(Bitboard board) {
        int phase = 0;
        phase += Long.bitCount(board.getBitboard(Piece.WHITE_KNIGHT) | board.getBitboard(Piece.BLACK_KNIGHT));
        phase += Long.bitCount(board.getBitboard(Piece.WHITE_BISHOP) | board.getBitboard(Piece.BLACK_BISHOP));
        phase += Long.bitCount(board.getBitboard(Piece.WHITE_ROOK) | board.getBitboard(Piece.BLACK_ROOK)) * 2;
        phase += Long.bitCount(board.getBitboard(Piece.WHITE_QUEEN) | board.getBitboard(Piece.BLACK_QUEEN)) * 4;

        // Total material at start: 4 knights + 4 bishops + 4 rooks + 2 queens = 24
        // Scale to 256
        return Math.min(phase * 256 / 24, 256);
    }


    private static int popCount(long bitboard) {
        return Long.bitCount(bitboard);
    }

    private static long evaluateBlocking(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0;

        long king = white ? board.getBitboard(Piece.WHITE_KING) : board.getBitboard(Piece.BLACK_KING);
        long pieces = white ? (board.getBitboard(Piece.WHITE_PAWN) | board.getBitboard(Piece.WHITE_KNIGHT) | board.getBitboard(Piece.WHITE_BISHOP) | board.getBitboard(Piece.WHITE_ROOK) | board.getBitboard(Piece.WHITE_QUEEN))
                : (board.getBitboard(Piece.BLACK_PAWN) | board.getBitboard(Piece.BLACK_KNIGHT) | board.getBitboard(Piece.BLACK_BISHOP) | board.getBitboard(Piece.BLACK_ROOK) | board.getBitboard(Piece.BLACK_QUEEN));

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

        long ownPieces = white ? (board.getBitboard(Piece.WHITE_PAWN) | board.getBitboard(Piece.WHITE_KNIGHT) | board.getBitboard(Piece.WHITE_BISHOP) | board.getBitboard(Piece.WHITE_ROOK) | board.getBitboard(Piece.WHITE_QUEEN) | board.getBitboard(Piece.WHITE_KING))
                : (board.getBitboard(Piece.BLACK_PAWN) | board.getBitboard(Piece.BLACK_KNIGHT) | board.getBitboard(Piece.BLACK_BISHOP) | board.getBitboard(Piece.BLACK_ROOK) | board.getBitboard(Piece.BLACK_QUEEN) | board.getBitboard(Piece.BLACK_KING));

        // Knights
        long knights = white ? board.getBitboard(Piece.WHITE_KNIGHT) : board.getBitboard(Piece.BLACK_KNIGHT);
        while (knights != 0) {
            int sq = Long.numberOfTrailingZeros(knights);
            long attacks = KNIGHT_MOVES[sq] & ~ownPieces;
            int moves = popCount(attacks);

            mgScore += moves * MG_MOBILITY_WEIGHT;
            egScore += moves * EG_MOBILITY_WEIGHT;

            knights &= knights - 1;
        }

        // Bishops/Rooks Centrality
        long rooks = white ? board.getBitboard(Piece.WHITE_ROOK) : board.getBitboard(Piece.BLACK_ROOK);
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

        long bishops = white ? board.getBitboard(Piece.WHITE_BISHOP) : board.getBitboard(Piece.BLACK_BISHOP);
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
    public static int calculateMgScoreFromScratch(Bitboard board) {
        int mgScore = 0;

        // White
        mgScore += calculateScoreFor(board.getBitboard(Piece.WHITE_PAWN), PAWN_VALUE, true, MG_PAWN_TABLE);
        mgScore += calculateScoreFor(board.getBitboard(Piece.WHITE_KNIGHT), KNIGHT_VALUE, true, MG_KNIGHT_TABLE);
        mgScore += calculateScoreFor(board.getBitboard(Piece.WHITE_BISHOP), BISHOP_VALUE, true, MG_BISHOP_TABLE);
        mgScore += calculateScoreFor(board.getBitboard(Piece.WHITE_ROOK), ROOK_VALUE, true, MG_ROOK_TABLE);
        mgScore += calculateScoreFor(board.getBitboard(Piece.WHITE_QUEEN), QUEEN_VALUE, true, MG_QUEEN_TABLE);
        mgScore += calculateScoreFor(board.getBitboard(Piece.WHITE_KING), 0, true, MG_KING_TABLE);

        // Black
        mgScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_PAWN), PAWN_VALUE, false, MG_PAWN_TABLE);
        mgScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_KNIGHT), KNIGHT_VALUE, false, MG_KNIGHT_TABLE);
        mgScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_BISHOP), BISHOP_VALUE, false, MG_BISHOP_TABLE);
        mgScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_ROOK), ROOK_VALUE, false, MG_ROOK_TABLE);
        mgScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_QUEEN), QUEEN_VALUE, false, MG_QUEEN_TABLE);
        mgScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_KING), 0, false, MG_KING_TABLE);

        return mgScore;
    }

    public static int calculateEgScoreFromScratch(Bitboard board) {
        int egScore = 0;

        // White
        egScore += calculateScoreFor(board.getBitboard(Piece.WHITE_PAWN), PAWN_VALUE, true, EG_PAWN_TABLE);
        egScore += calculateScoreFor(board.getBitboard(Piece.WHITE_KNIGHT), KNIGHT_VALUE, true, EG_KNIGHT_TABLE);
        egScore += calculateScoreFor(board.getBitboard(Piece.WHITE_BISHOP), BISHOP_VALUE, true, EG_BISHOP_TABLE);
        egScore += calculateScoreFor(board.getBitboard(Piece.WHITE_ROOK), ROOK_VALUE, true, EG_ROOK_TABLE);
        egScore += calculateScoreFor(board.getBitboard(Piece.WHITE_QUEEN), QUEEN_VALUE, true, EG_QUEEN_TABLE);
        egScore += calculateScoreFor(board.getBitboard(Piece.WHITE_KING), 0, true, EG_KING_TABLE);

        // Black
        egScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_PAWN), PAWN_VALUE, false, EG_PAWN_TABLE);
        egScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_KNIGHT), KNIGHT_VALUE, false, EG_KNIGHT_TABLE);
        egScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_BISHOP), BISHOP_VALUE, false, EG_BISHOP_TABLE);
        egScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_ROOK), ROOK_VALUE, false, EG_ROOK_TABLE);
        egScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_QUEEN), QUEEN_VALUE, false, EG_QUEEN_TABLE);
        egScore -= calculateScoreFor(board.getBitboard(Piece.BLACK_KING), 0, false, EG_KING_TABLE);

        return egScore;
    }

    private static int calculateScoreFor(long bitboard, int value, boolean white, int[] table) {
        int score = 0;
        while (bitboard != 0) {
            int square = Long.numberOfTrailingZeros(bitboard);
            int tableSquare = white ? (square ^ 56) : square;
            score += value + table[tableSquare];
            bitboard &= bitboard - 1;
        }
        return score;
    }
}
