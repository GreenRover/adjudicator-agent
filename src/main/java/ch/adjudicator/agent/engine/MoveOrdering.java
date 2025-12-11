package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;

/**
 * Advanced move ordering using the "Golden Standard Strategy":
 * 1. Hash Move (from Transposition Table)
 * 2. Winning Captures (MVV-LVA)
 * 3. Killer Moves
 * 4. Quiet Moves (History Heuristic)
 * 5. Losing Captures
 */
public class MoveOrdering {
    private static final int MAX_DEPTH = 64;
    private static final Square[] SQUARES = Square.values();

    // MVV-LVA: Most Valuable Victim - Least Valuable Aggressor
    private static final int[][] MVV_LVA_SCORES = new int[7][7];

    static {
        // Precompute MVV-LVA scores
        // Score = VictimValue * 100 - AggressorValue
        for (int victim = 0; victim < 7; victim++) {
            for (int aggressor = 0; aggressor < 7; aggressor++) {
                MVV_LVA_SCORES[victim][aggressor] = victim * 100 - aggressor;
            }
        }
    }

    // Killer moves: 2 per ply
    private final int[][] killerMoves = new int[MAX_DEPTH][2];
    // History heuristic: [from_square][to_square]
    private final int[][] historyScores = new int[64][64];
    // Transposition Table reference
    private final TranspositionTable transpositionTable;

    public MoveOrdering(TranspositionTable tt) {
        this.transpositionTable = tt;
        clearKillers();
        clearHistory();
    }

    /**
     * Clear all killer moves.
     */
    public void clearKillers() {
        for (int i = 0; i < MAX_DEPTH; i++) {
            killerMoves[i][0] = 0;
            killerMoves[i][1] = 0;
        }
    }

    /**
     * Clear history scores.
     */
    public void clearHistory() {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                historyScores[i][j] = 0;
            }
        }
    }

    /**
     * Decay history scores by dividing by 8.
     * To be called between moves.
     */
    public void decayHistory() {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                historyScores[i][j] /= 8;
            }
        }
    }

    /**
     * Reset move ordering data (killers and history).
     * To be called on new game.
     */
    public void reset() {
        clearKillers();
        clearHistory();
    }

    /**
     * Update killer move when a beta cutoff occurs on a quiet move.
     */
    public void updateKiller(int move, int ply) {
        if (ply >= MAX_DEPTH || move == 0) return;

        // Shift killers: move killer[0] to killer[1], new move to killer[0]
        if (move != killerMoves[ply][0]) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
    }

    /**
     * Update history score when a move causes a cutoff.
     */
    public void updateHistory(int move, int depth) {
        if (move == 0) return;
        int from = Bitboard.getFrom(move);
        int to = Bitboard.getTo(move);

        // Increment by depth squared * 10 (heavily weight deeper searches)
        historyScores[from][to] += depth * depth * 10;

        // Prevent overflow
        if (historyScores[from][to] > 1000000) {
            // Age all history scores
            for (int i = 0; i < 64; i++) {
                for (int j = 0; j < 64; j++) {
                    historyScores[i][j] /= 2;
                }
            }
        }
    }

    /**
     * Check if move is a killer move at this ply.
     */
    private boolean isKiller(int move, int ply) {
        if (ply >= MAX_DEPTH) return false;
        return move == killerMoves[ply][0] || move == killerMoves[ply][1];
    }

    /**
     * Get piece index for MVV-LVA array (0-6).
     */
    private int getPieceIndex(Piece piece) {
        if (piece == null || piece == Piece.NONE) return 0;

        return switch (piece.getPieceType()) {
            case PAWN -> 1;
            case KNIGHT -> 2;
            case BISHOP -> 3;
            case ROOK -> 4;
            case QUEEN -> 5;
            case KING -> 6;
            default -> 0;
        };
    }

    /**
     * Calculate MVV-LVA score for a capture.
     */
    private int getMvvLvaScore(Bitboard board, int move) {
        int from = Bitboard.getFrom(move);
        int to = Bitboard.getTo(move);
        Piece attacker = board.getPieceAt(SQUARES[from]);
        Piece victim = board.getPieceAt(SQUARES[to]);

        if (victim == Piece.NONE) {
            // En Passant
            return 105; // Pawn takes Pawn (100 + 5)
        }

        return MVV_LVA_SCORES[getPieceIndex(victim)][getPieceIndex(attacker)];
    }

    private boolean isPromotion(int move) {
        return Bitboard.getPromo(move) != 0;
    }

    /**
     * Rate a move according to the Golden Ordering Strategy.
     * Higher score = search first.
     */
    public int rateMove(Bitboard board, int move, int hashMove, int ply) {
        return rateMove(board, move, hashMove, ply, false);
    }

    public int rateMove(Bitboard board, int move, int hashMove, int ply, boolean qSearch) {
        // 1. Hash Move (from TT) - Highest priority
        if (move == hashMove) {
            return 20000000;
        }

        int score = 0;
        int from = Bitboard.getFrom(move);
        int to = Bitboard.getTo(move);
        Piece piece = board.getPieceAt(SQUARES[from]); // Get moving piece
        Piece victim = board.getPieceAt(SQUARES[to]);

        boolean isCapture = (victim != Piece.NONE) ||
            ( (piece == Piece.WHITE_PAWN || piece == Piece.BLACK_PAWN) &&
              (Math.abs(from - to) % 8 != 0) && victim == Piece.NONE );

        // 2. Captures - MVV-LVA scoring
        if (isCapture) {
            int mvvLva = getMvvLvaScore(board, move);

            // Winning captures (good trades)
            if (mvvLva >= 0) {
                score = 1000000 + mvvLva;
            } else {
                // Losing captures (bad trades) - search last
                score = mvvLva; // Negative score
            }
        }

        // 3. Promotions (treat as high-value captures)
        if (isPromotion(move)) {
            score = Math.max(score, 950000);
        }

        // --- ADDED: Dangerous Pawn Pushes (Rank 7) ---
        // Just below promotions/winning captures, but above killers
        if (piece.getPieceType() == com.github.bhlangonijr.chesslib.PieceType.PAWN) {
            int toRank = to / 8;
            // White to Rank 7 (index 6) OR Black to Rank 2 (index 1)
            if ((board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE && toRank == 6) ||
                (board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.BLACK && toRank == 1)) {
                score = Math.max(score, 850000);
            }
        }
        // --- END ADD ---

        if (qSearch) {
            return score;
        }

        // 4. Killer Moves
        if (isKiller(move, ply)) {
            if (move == killerMoves[ply][0]) {
                score += 900000;
            } else {
                score += 800000;
            }
        }

        // 5. Quiet Moves - History Heuristic
        if (!isCapture) {
            score += historyScores[from][to];
        }

        return score;
    }

    /**
     * Pick the best move from the remaining moves and swap it to currentIndex.
     * This is more efficient than sorting the entire list.
     */
    public void pickBestMove(Bitboard board, int[] moves, int count, int currentIndex, int hashMove, int ply) {
        pickBestMove(board, moves, count, currentIndex, hashMove, ply, false);
    }

    public void pickBestMove(Bitboard board, int[] moves, int count, int currentIndex, int hashMove, int ply, boolean qSearch) {
        if (currentIndex >= count) return;

        int bestScore = Integer.MIN_VALUE;
        int bestIndex = currentIndex;

        for (int i = currentIndex; i < count; i++) {
            int move = moves[i];
            int score = rateMove(board, move, hashMove, ply, qSearch);

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        if (bestIndex != currentIndex) {
            int temp = moves[currentIndex];
            moves[currentIndex] = moves[bestIndex];
            moves[bestIndex] = temp;
        }
    }

    public int getHashMove(long zobristHash) {
        if (transpositionTable == null) return 0;
        return transpositionTable.getBestMove(zobristHash);
    }
}
