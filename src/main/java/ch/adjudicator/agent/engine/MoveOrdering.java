package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Collections;
import java.util.List;

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
    
    // MVV-LVA: Most Valuable Victim - Least Valuable Aggressor
    private static final int[][] MVV_LVA_SCORES = new int[7][7];
    
    // Killer moves: 2 per ply
    private final Move[][] killerMoves = new Move[MAX_DEPTH][2];
    
    // History heuristic: [from_square][to_square]
    private final int[][] historyScores = new int[64][64];
    
    // Transposition Table reference
    private TranspositionTable transpositionTable;
    
    // Piece values for MVV-LVA
    private static final int PAWN_VALUE = 1;
    private static final int KNIGHT_VALUE = 3;
    private static final int BISHOP_VALUE = 3;
    private static final int ROOK_VALUE = 5;
    private static final int QUEEN_VALUE = 9;
    private static final int KING_VALUE = 100;
    
    static {
        // Precompute MVV-LVA scores
        // Score = VictimValue * 100 - AggressorValue
        for (int victim = 0; victim < 7; victim++) {
            for (int aggressor = 0; aggressor < 7; aggressor++) {
                MVV_LVA_SCORES[victim][aggressor] = victim * 100 - aggressor;
            }
        }
    }
    
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
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
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
     * Update killer move when a beta cutoff occurs on a quiet move.
     */
    public void updateKiller(Move move, int ply) {
        if (ply >= MAX_DEPTH) return;
        
        // Shift killers: move killer[0] to killer[1], new move to killer[0]
        if (!move.equals(killerMoves[ply][0])) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
    }
    
    /**
     * Update history score when a move causes a cutoff.
     */
    public void updateHistory(Move move, int depth) {
        int from = move.getFrom().ordinal();
        int to = move.getTo().ordinal();
        
        // Increment by depth squared (deeper searches are more valuable)
        historyScores[from][to] += depth * depth;
        
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
    private boolean isKiller(Move move, int ply) {
        if (ply >= MAX_DEPTH) return false;
        return move.equals(killerMoves[ply][0]) || move.equals(killerMoves[ply][1]);
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
    private int getMvvLvaScore(Board board, Move move) {
        Piece victim = board.getPiece(move.getTo());
        Piece aggressor = board.getPiece(move.getFrom());
        
        int victimIndex = getPieceIndex(victim);
        int aggressorIndex = getPieceIndex(aggressor);
        
        return MVV_LVA_SCORES[victimIndex][aggressorIndex];
    }
    
    /**
     * Check if move is a capture.
     */
    private boolean isCapture(Board board, Move move) {
        return board.getPiece(move.getTo()) != Piece.NONE;
    }
    
    /**
     * Check if move is a promotion.
     */
    private boolean isPromotion(Move move) {
        String moveStr = move.toString().toLowerCase();
        return moveStr.length() > 4;
    }
    
    /**
     * Rate a move according to the Golden Ordering Strategy.
     * Higher score = search first.
     */
    public int rateMove(Board board, Move move, Move hashMove, int ply, long zobristHash) {
        // 1. Hash Move (from TT) - Highest priority
        if (hashMove != null && move.equals(hashMove)) {
            return 2_000_000;
        }
        
        // 2. Captures - MVV-LVA scoring
        if (isCapture(board, move)) {
            int mvvLva = getMvvLvaScore(board, move);
            
            // Winning captures (good trades)
            if (mvvLva >= 0) {
                return 1_000_000 + mvvLva;
            } else {
                // Losing captures (bad trades) - search last
                return mvvLva; // Negative score
            }
        }
        
        // 3. Promotions (treat as high-value captures)
        if (isPromotion(move)) {
            return 950_000;
        }
        
        // 4. Killer Moves
        if (isKiller(move, ply)) {
            if (move.equals(killerMoves[ply][0])) {
                return 900_000;
            } else {
                return 800_000;
            }
        }
        
        // 5. Quiet Moves - History Heuristic
        int from = move.getFrom().ordinal();
        int to = move.getTo().ordinal();
        return historyScores[from][to];
    }
    
    /**
     * Pick the best move from the remaining moves and swap it to currentIndex.
     * This is more efficient than sorting the entire list.
     */
    public void pickBestMove(Board board, List<Move> moves, int currentIndex, Move hashMove, int ply, long zobristHash) {
        if (currentIndex >= moves.size()) return;
        
        int bestScore = Integer.MIN_VALUE;
        int bestIndex = currentIndex;
        
        for (int i = currentIndex; i < moves.size(); i++) {
            Move move = moves.get(i);
            int score = rateMove(board, move, hashMove, ply, zobristHash);
            
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        
        // Swap best move to current position
        if (bestIndex != currentIndex) {
            Collections.swap(moves, currentIndex, bestIndex);
        }
    }
    
    /**
     * Get the hash move from the transposition table.
     */
    public Move getHashMove(long zobristHash) {
        if (transpositionTable == null) return null;
        return transpositionTable.getBestMove(zobristHash);
    }
}
