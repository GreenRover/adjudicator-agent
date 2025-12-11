package ch.adjudicator.agent.engine.eval;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Piece;
import static ch.adjudicator.agent.engine.eval.PawnStructure.FILE_MASKS;

public class KingSafety {

    // King Safety Constants
    public static final int MG_PAWN_SHIELD_BONUS = 10;
    public static final int MG_MISSING_PAWN_SHIELD_PENALTY = -20;
    public static final int MG_KING_CENTER_PENALTY = -50;
    // Attacker count: 0, 1, 2, 3, 4+
    public static final int[] MG_ATTACKER_PENALTY = {0, 0, -25, -75, -150};
    public static final int MG_KING_OPEN_FILE_PENALTY = -25;

    public static long evaluate(Bitboard board, boolean white) {
        int mgScore = 0;
        int egScore = 0; // King safety matters less in endgame

        long kingBitboard = white ? board.getBitboard(Piece.WHITE_KING) : board.getBitboard(Piece.BLACK_KING);
        long ownPawns = white ? board.getBitboard(Piece.WHITE_PAWN) : board.getBitboard(Piece.BLACK_PAWN);

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

            // 3. King on Open File
            // Note: Relying on PawnStructure for FILE_MASKS as per instructions
            long fileMask = FILE_MASKS[file];
            if ((ownPawns & fileMask) == 0) {
                mgScore += MG_KING_OPEN_FILE_PENALTY;
            }
        }

        return pack(mgScore, egScore);
    }

    private static long pack(int mg, int eg) {
        return ((long) mg << 32) | (eg & 0xFFFFFFFFL);
    }
}
