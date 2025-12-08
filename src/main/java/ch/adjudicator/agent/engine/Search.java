package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * Alpha-Beta search with Iterative Deepening, Quiescence Search,
 * Transposition Table, and Advanced Move Ordering.
 * This is the brain of the chess engine.
 */
public class Search {
    private static final int MAX_DEPTH = 50;
    private static final int INFINITY = 1000000;
    private static final int MATE_SCORE = 900000;

    private final Board board;
    // Advanced move ordering
    private final TranspositionTable transpositionTable;
    private final MoveOrdering moveOrdering;
    private long stopTime;
    private volatile boolean stopped;
    private Move bestMoveFound;
    private int nodesSearched;
    private int depthReached;
    private boolean enableNmp = true;

    public Search(Board board, TranspositionTable transpositionTable) {
        this.board = board;
        this.stopped = false;
        this.nodesSearched = 0;
        this.transpositionTable = transpositionTable;
        this.moveOrdering = new MoveOrdering(transpositionTable);
    }

    public Search(Board board) {
        this(board, new TranspositionTable());
    }

    /**
     * Find the best move using Iterative Deepening.
     *
     * @param allocatedTimeMs Time allocated for this search
     * @return Best move found
     */
    public Move findBestMove(long allocatedTimeMs) {
        stopTime = System.currentTimeMillis() + allocatedTimeMs;
        stopped = false;
        bestMoveFound = null;
        nodesSearched = 0;
        depthReached = 0;

        int numThreads = Runtime.getRuntime().availableProcessors();
        List<Search> helpers = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        if (numThreads > 1) {
            String fen = board.getFen();
            for (int i = 0; i < numThreads - 1; i++) {
                Board helperBoard = new Board();
                helperBoard.loadFromFen(fen);
                Search helper = new Search(helperBoard, transpositionTable);
                helper.setStopTime(stopTime);
                helpers.add(helper);

                Thread t = new Thread(helper::runHelper);
                t.start();
                threads.add(t);
            }
        }

        try {
            runIterativeDeepening(allocatedTimeMs);
        } finally {
            // Stop helpers
            for (Search helper : helpers) {
                helper.stop();
            }
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            // Aggregate nodes
            for (Search helper : helpers) {
                nodesSearched += helper.getNodesSearched();
            }
        }

        return bestMoveFound;
    }

    public void setStopTime(long stopTime) {
        this.stopTime = stopTime;
    }

    public void setEnableNmp(boolean enableNmp) {
        this.enableNmp = enableNmp;
    }

    public void stop() {
        this.stopped = true;
    }

    public void runHelper() {
        runIterativeDeepening(0); // 0 means relying on stopTime or external stop
    }

    private void runIterativeDeepening(long allocatedTimeMs) {
        // Iterative Deepening: search progressively deeper
        int score = 0;
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (stopped) {
                break;
            }

            int currentScore;
            if (depth == 1) {
                currentScore = searchRoot(depth, -INFINITY, INFINITY);
            } else {
                // Aspiration Windows
                int window = 50;
                int alpha = score - window;
                int beta = score + window;
                currentScore = searchRoot(depth, alpha, beta);

                if (currentScore <= alpha) {
                    // Fail Low
                    alpha = -INFINITY;
                    currentScore = searchRoot(depth, alpha, beta);
                }

                if (currentScore >= beta) {
                    // Fail High
                    beta = INFINITY;
                    currentScore = searchRoot(depth, alpha, beta);
                }
            }

            score = currentScore;

            // Check if we ran out of time
            if (stopped) {
                break;
            }

            // Update best move found at this depth
            // (bestMoveFound is updated during search)
            depthReached = depth;

            // If we found a mate, no need to search deeper
            if (Math.abs(score) > MATE_SCORE - 1000) {
                break;
            }

            // Check if we have enough time for next depth
            // Only for main thread (allocatedTimeMs > 0)
            if (allocatedTimeMs > 0) {
                long elapsed = System.currentTimeMillis();
                long remaining = stopTime - elapsed;

                // Rough heuristic: next depth takes ~3x longer
                if (remaining < (elapsed - (stopTime - allocatedTimeMs)) * 3) {
                    break;
                }
            } else {
                // Helpers check time too
                if (System.currentTimeMillis() >= stopTime) {
                    break;
                }
            }
        }
    }

    /**
     * Search from root position.
     */
    void searchRoot(int depth) {
        searchRoot(depth, -INFINITY, INFINITY);
    }

    int searchRoot(int depth, int alpha, int beta) {
        List<Move> moves = board.legalMoves();

        if (moves.isEmpty()) {
            return board.isMated() ? -MATE_SCORE : 0;
        }

        // Get Zobrist hash for current position
        long zobristHash = board.getZobristKey();

        // Get hash move from transposition table
        Move hashMove = moveOrdering.getHashMove(zobristHash);

        int bestScore = -INFINITY;
        Move localBestMove = null;
        int originalAlpha = alpha;

        // Use pickBestMove for advanced move ordering
        for (int i = 0; i < moves.size(); i++) {
            if (stopped) {
                break;
            }

            // Pick best remaining move and swap to position i
            moveOrdering.pickBestMove(board, moves, i, hashMove, 0);
            Move move = moves.get(i);

            board.doMove(move);
            int score = -alphaBeta(-beta, -alpha, depth - 1, 1);
            board.undoMove();

            if (stopped) {
                break;
            }

            if (score > bestScore) {
                bestScore = score;
                localBestMove = move;
                if (score > alpha) {
                    alpha = score;
                }

                if (score >= beta) {
                    break;
                }
            }
        }

        // Store result in transposition table
        if (!stopped && localBestMove != null) {
            bestMoveFound = localBestMove;
            int flag = TranspositionTable.TTEntry.EXACT;
            if (bestScore <= originalAlpha) {
                flag = TranspositionTable.TTEntry.UPPER_BOUND;
            } else if (bestScore >= beta) {
                flag = TranspositionTable.TTEntry.LOWER_BOUND;
            }
            transpositionTable.store(zobristHash, localBestMove, bestScore, depth, flag);
        }

        return bestScore;
    }

    /**
     * Alpha-Beta pruning search (Negamax framework).
     *
     * @param alpha Lower bound
     * @param beta  Upper bound
     * @param depth Remaining depth to search
     * @param ply   Distance from root (for killer moves)
     * @return Evaluation score
     */
    private int alphaBeta(int alpha, int beta, int depth, int ply) {
        // Check time limit periodically
        if ((nodesSearched & 2047) == 0) { // Check every 2048 nodes
            if (System.currentTimeMillis() >= stopTime) {
                stopped = true;
                return 0;
            }
        }

        nodesSearched++;

        // Get Zobrist hash for current position
        long zobristHash = board.getZobristKey();

        // Probe transposition table
        TranspositionTable.TTEntry ttEntry = transpositionTable.probe(zobristHash);
        if (ttEntry != null && ttEntry.depth >= depth) {
            // Use TT score if depth is sufficient
            if (ttEntry.flag == TranspositionTable.TTEntry.EXACT) {
                return ttEntry.score;
            } else if (ttEntry.flag == TranspositionTable.TTEntry.LOWER_BOUND) {
                alpha = Math.max(alpha, ttEntry.score);
            } else if (ttEntry.flag == TranspositionTable.TTEntry.UPPER_BOUND) {
                beta = Math.min(beta, ttEntry.score);
            }

            if (alpha >= beta) {
                return ttEntry.score;
            }
        }

        // Null Move Pruning
        if (enableNmp && depth >= 3 && !board.isKingAttacked()) {
            Side side = board.getSideToMove();
            // Check for non-pawn/non-king material to avoid zugzwang
            long pieces = board.getBitboard(side);
            long pawns = board.getBitboard(Piece.make(side, PieceType.PAWN));
            long kings = board.getBitboard(Piece.make(side, PieceType.KING));

            if ((pieces ^ pawns ^ kings) != 0) {
                board.doNullMove();
                int R = 2;
                // Search with null window and reduced depth
                int score = -alphaBeta(-beta, -beta + 1, depth - 1 - R, ply + 1);
                board.undoMove();

                if (stopped) return 0;

                if (score >= beta) {
                    return beta;
                }
            }
        }

        // Depth 0: switch to quiescence search
        if (depth <= 0) {
            return quiescence(alpha, beta);
        }

        List<Move> moves = board.legalMoves();

        // Terminal node (checkmate or stalemate)
        if (moves.isEmpty()) {
            if (board.isMated()) {
                return -MATE_SCORE + (MAX_DEPTH - depth); // Prefer faster mates
            }
            return 0; // Stalemate
        }

        // Get hash move from TT
        Move hashMove = ttEntry != null ? ttEntry.bestMove : null;

        int bestScore = -INFINITY;
        Move bestMove = null;
        int originalAlpha = alpha;

        boolean inCheck = board.isKingAttacked();

        // Use pickBestMove for advanced move ordering
        for (int i = 0; i < moves.size(); i++) {
            if (stopped) {
                break;
            }

            // Pick best remaining move and swap to position i
            moveOrdering.pickBestMove(board, moves, i, hashMove, ply);
            Move move = moves.get(i);

            board.doMove(move);

            int score;

            if (i == 0) {
                // First move: Full Window Search
                score = -alphaBeta(-beta, -alpha, depth - 1, ply + 1);
            } else {
                // Late moves: Null Window Search (PVS)
                // Search with (alpha, alpha + 1)

                // Interaction with LMR: apply primarily during the Null Window search step
                int searchDepth = depth - 1;

                // Check if LMR is applicable
                if (i >= 4 && depth >= 3 && !isCapture(move) && !isPromotion(move) && !inCheck) {
                    searchDepth = depth - 2;
                }

                // Search with Null Window (alpha, alpha+1)
                // Note: -alpha - 1 corresponds to -beta in recursive call where beta = alpha + 1
                score = -alphaBeta(-alpha - 1, -alpha, searchDepth, ply + 1);

                // Re-Search: If score > alpha (move was actually good) AND score < beta, 
                // search again with full window
                if (score > alpha && score < beta) {
                    score = -alphaBeta(-beta, -alpha, depth - 1, ply + 1);
                }

                // Also, if LMR was used and it failed high (score >= beta), 
                // or if it improved alpha but we only did re-search on (alpha < score < beta),
                // we might need to handle the case where LMR failed high but was unsafe?
                // The instructions say "Re-Search: If score > alpha ... AND score < beta".
                // This implies we trust LMR beta cutoffs.

                // However, if LMR returns score > alpha, and we didn't re-search (e.g. score >= beta),
                // we are accepting the LMR result.

                // Wait, if score > alpha (meaning score >= alpha+1 since integer), 
                // and if we used reduced depth, isn't it better to re-verify?
                // Standard PVS usually re-searches if (score > alpha).
                // The instruction says "AND score < beta".

                // What if score >= beta? We return beta (cutoff).
                // If we used LMR, this is a "soft" cutoff.
                // But the instructions don't ask to re-verify soft cutoffs.

                // One edge case: If LMR was used, and score > alpha.
                // If score < beta, we re-search with FULL depth (depth - 1). This is correct.
                // If score >= beta, we cutoff.
            }

            board.undoMove();

            if (stopped) {
                break;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, score);

            // Beta cutoff (pruning)
            if (alpha >= beta) {
                // Update killers and history for cutoff move
                if (!isCapture(move)) {
                    moveOrdering.updateKiller(move, ply);
                }
                moveOrdering.updateHistory(move, depth);

                // Store in TT as lower bound
                transpositionTable.store(zobristHash, move, beta, depth,
                        TranspositionTable.TTEntry.LOWER_BOUND);
                return beta;
            }
        }

        // Store result in transposition table
        if (!stopped && bestMove != null) {
            int flag;
            if (bestScore <= originalAlpha) {
                flag = TranspositionTable.TTEntry.UPPER_BOUND;
            } else {
                flag = TranspositionTable.TTEntry.EXACT;
            }
            transpositionTable.store(zobristHash, bestMove, bestScore, depth, flag);
        }

        return bestScore;
    }

    /**
     * Quiescence search: only search captures and checks until position is quiet.
     * Prevents horizon effect.
     */
    private int quiescence(int alpha, int beta) {
        // 1. Check time every 2048 nodes (bitwise AND is faster than modulo)
        if ((nodesSearched & 2047) == 0) {
            if (System.currentTimeMillis() >= stopTime) {
                stopped = true;
                return 0; // Return neutral score to exit quickly
            }
        }

        nodesSearched++;

        // Stand-pat: evaluate current position
        BoardStatus fastBoard = new BoardStatus(board);
        int standPat = Evaluator.evaluate(fastBoard);

        if (standPat >= beta) {
            return beta;
        }

        // DELTA PRUNING
        // huge margin (900 for Queen) + 200 safety for positional factors
        int BIG_DELTA = 900 + 200;
        if (standPat < alpha - BIG_DELTA) {
            // If we are so far behind that even a Queen capture won't help,
            // we can likely prune, BUT we must search promotions.
            // This is a "lazy" impl; sophisticated engines calc precise material gain.
            // For now, simply return alpha is risky without precise calculation,
            // so standard Delta Pruning is:
            // If (standPat + capturedPieceValue + 200 < alpha) continue;
        }

        if (alpha < standPat) {
            alpha = standPat;
        }

        // Generate and search only tactical moves (captures)
        List<Move> moves = board.legalMoves();

        for (Move move : moves) {
            // Only consider captures and promotions
            if (!isCapture(move) && !isPromotion(move)) {
                continue;
            }

            // Delta Pruning
            if (!isPromotion(move) && isCapture(move)) {
                Piece captured = board.getPiece(move.getTo());
                int capturedValue = getPieceValue(captured);
                if (standPat + capturedValue + 200 < alpha) {
                    continue;
                }
            }

            // SEE Pruning for bad captures
            if (isCapture(move) && !isPromotion(move)) {
                int seeScore = StaticExchangeEvaluator.see(board, move);
                if (seeScore < 0) {
                    continue;
                }
            }

            if (stopped) {
                break;
            }

            board.doMove(move);
            int score = -quiescence(-beta, -alpha);
            board.undoMove();

            if (stopped) {
                break;
            }

            if (score >= beta) {
                return beta;
            }

            if (score > alpha) {
                alpha = score;
            }
        }

        return alpha;
    }

    /**
     * Check if move is a capture.
     */
    private boolean isCapture(Move move) {
        return board.getPiece(move.getTo()) != com.github.bhlangonijr.chesslib.Piece.NONE;
    }

    /**
     * Check if move is a promotion.
     */
    private boolean isPromotion(Move move) {
        return move.getPromotion() != com.github.bhlangonijr.chesslib.Piece.NONE;
    }

    /**
     * Get number of nodes searched.
     */
    public int getNodesSearched() {
        return nodesSearched;
    }

    /**
     * Get depth reached during iterative deepening.
     */
    public int getDepthReached() {
        return depthReached;
    }

    private int getPieceValue(Piece piece) {
        if (piece == null) {
            return 0;
        }
        return switch (piece.getPieceType()) {
            case PAWN -> StaticExchangeEvaluator.PAWN_VALUE;
            case KNIGHT -> StaticExchangeEvaluator.KNIGHT_VALUE;
            case BISHOP -> StaticExchangeEvaluator.BISHOP_VALUE;
            case ROOK -> StaticExchangeEvaluator.ROOK_VALUE;
            case QUEEN -> StaticExchangeEvaluator.QUEEN_VALUE;
            case KING -> StaticExchangeEvaluator.KING_VALUE;
            default -> 0;
        };
    }
}
