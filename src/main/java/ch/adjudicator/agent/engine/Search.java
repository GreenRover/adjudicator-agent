package ch.adjudicator.agent.engine;

import ch.adjudicator.agent.engine.board.Bitboard;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;

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
    private static final Square[] SQUARES = Square.values();

    private final Bitboard board;
    // Advanced move ordering
    private final TranspositionTable transpositionTable;
    private final MoveOrdering moveOrdering;
    private long stopTime;
    private volatile boolean stopped;
    private int bestMoveFound; // Encoded int move
    private int nodesSearched;
    private int depthReached;
    private boolean enableNmp = true;

    // Pre-allocated move buffers [depth][max_moves]
    private final int[][] moveBuffer = new int[MAX_DEPTH + 1][256];

    public Search(Bitboard board, TranspositionTable transpositionTable) {
        this.board = board;
        this.stopped = false;
        this.nodesSearched = 0;
        this.transpositionTable = transpositionTable;
        this.moveOrdering = new MoveOrdering(transpositionTable);
    }

    public Search(Bitboard board) {
        this(board, new TranspositionTable());
    }

    /**
     * Find the best move using Iterative Deepening.
     *
     * @param allocatedTimeMs Time allocated for this search
     * @return Best move found (encoded as int)
     */
    public int findBestMove(long allocatedTimeMs) {
        stopTime = System.currentTimeMillis() + allocatedTimeMs;
        stopped = false;
        bestMoveFound = 0;
        nodesSearched = 0;
        depthReached = 0;

        int numThreads = Runtime.getRuntime().availableProcessors();
        List<Search> helpers = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        if (numThreads > 1) {
            String fen = board.getFen();
            for (int i = 0; i < numThreads - 1; i++) {
                Bitboard helperBoard = new Bitboard();
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
            // Hard Limit check
            if (System.currentTimeMillis() > stopTime) {
                break;
            }

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
        // Use pre-allocated buffer for root (ply 0)
        int[] moves = moveBuffer[0];
        int count = board.generateLegalMoves(moves);

        if (count == 0) {
            return board.isMated() ? -MATE_SCORE : 0;
        }

        // Get Zobrist hash for current position
        long zobristHash = board.getZobristKey();

        // Get hash move from transposition table
        int hashMove = moveOrdering.getHashMove(zobristHash);

        int bestScore = -INFINITY;
        int localBestMove = 0;
        int originalAlpha = alpha;

        // Use pickBestMove for advanced move ordering
        for (int i = 0; i < count; i++) {
            if (stopped) {
                break;
            }

            // Pick best remaining move and swap to position i
            moveOrdering.pickBestMove(board, moves, count, i, hashMove, 0);
            int move = moves[i];

            board.makeMove(move);
            int score = -alphaBeta(-beta, -alpha, depth - 1, 1);
            board.unmakeMove(move);

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
        if (!stopped && localBestMove != 0) {
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
        if ((nodesSearched & 1023) == 0) { // Check every 1024 nodes
            if (System.currentTimeMillis() >= stopTime) {
                stopped = true;
                return 0;
            }
        }

        nodesSearched++;
        
        if (ply > MAX_DEPTH) {
            return Evaluator.evaluate(board);
        }

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

        boolean inCheck = board.isKingAttacked();

        // Null Move Pruning
        if (enableNmp && depth >= 3 && !inCheck) {
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
            return quiescence(alpha, beta, ply, 0);
        }

        int[] moves = moveBuffer[ply];
        int count = board.generateLegalMoves(moves);

        // Terminal node (checkmate or stalemate)
        if (count == 0) {
            if (inCheck) {
                return -MATE_SCORE + (MAX_DEPTH - depth); // Prefer faster mates
            }
            return 0; // Stalemate
        }

        // Get hash move from TT
        int hashMove = ttEntry != null ? ttEntry.bestMove : 0;

        int bestScore = -INFINITY;
        int bestMove = 0;
        int originalAlpha = alpha;

        // Use pickBestMove for advanced move ordering
        for (int i = 0; i < count; i++) {
            if (stopped) {
                break;
            }

            // Pick best remaining move and swap to position i
            moveOrdering.pickBestMove(board, moves, count, i, hashMove, ply);
            int move = moves[i];

            board.makeMove(move);

            int score;

            boolean givesCheck = board.isKingAttacked();
            int extension = 0;
            if ((inCheck || givesCheck) && ply < MAX_DEPTH * 2) {
                extension = 1;
            }
            int nextDepth = depth - 1 + extension;

            if (i == 0) {
                score = -alphaBeta(-beta, -alpha, nextDepth, ply + 1);
            } else {
                // Late moves: Null Window Search (PVS)
                
                int searchDepth = nextDepth;

                // Check if LMR is applicable
                if (i >= 4 && depth >= 3 && !isCapture(move) && !isPromotion(move) && !inCheck) {
                    searchDepth -= 1;
                }

                // Search with Null Window (alpha, alpha+1)
                score = -alphaBeta(-alpha - 1, -alpha, searchDepth, ply + 1);

                // Re-Search
                if (score > alpha && score < beta) {
                    score = -alphaBeta(-beta, -alpha, nextDepth, ply + 1);
                }
            }

            board.unmakeMove(move);

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
        if (!stopped && bestMove != 0) {
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
    private int quiescence(int alpha, int beta, int ply, int qsDepth) {
        // 1. Check time every 1024 nodes
        if ((nodesSearched & 1023) == 0) {
            if (System.currentTimeMillis() >= stopTime) {
                stopped = true;
                return 0;
            }
        }

        nodesSearched++;

        if (ply >= MAX_DEPTH) {
            return Evaluator.evaluate(board);
        }

        // Fix Tactical Blindness: Check for check
        boolean inCheck = board.isKingAttacked();
        int standPat = -INFINITY;

        if (inCheck) {
            // Force alpha to -INFINITY (must find evasion)
            alpha = -INFINITY;
        } else {
            // Stand-pat: evaluate current position
            standPat = Evaluator.evaluate(board);

            if (standPat >= beta) {
                return beta;
            }

            // DELTA PRUNING
            if (alpha < standPat) {
                alpha = standPat;
            }
        }

        // Generate moves
        // If in check: ALL legal moves (evasions)
        // If not in check: Only Loud moves (Captures/Promotions)
        int[] moves = moveBuffer[ply];
        int count;
        if (inCheck) {
            count = board.generateLegalMoves(moves);
        } else {
            count = board.generateLoudMoves(moves);
        }

        // Check for Checkmate/Stalemate (only if in check and no moves)
        if (inCheck && count == 0) {
            return -MATE_SCORE + (MAX_DEPTH - ply);
        }

        for (int i = 0; i < count; i++) {
            int move = moves[i];
            
            boolean isCap = isCapture(move);
            boolean isProm = isPromotion(move);
            
            // Delta Pruning (Only if not in check)
            if (!inCheck && !isProm && isCap) {
                Piece captured = board.getPieceAt(SQUARES[Bitboard.getTo(move)]);
                int capturedValue = getPieceValue(captured);
                if (standPat + capturedValue + 200 < alpha) {
                    continue;
                }
            }
            
            // SEE Pruning for bad captures
            if (!inCheck && isCap && !isProm) {
                int seeScore = StaticExchangeEvaluator.see(board, move);
                if (seeScore < 0) {
                    continue;
                }
            }
            
            board.makeMove(move);
            int score = -quiescence(-beta, -alpha, ply + 1, qsDepth + 1);
            board.unmakeMove(move);
            
            if (stopped) break;
            
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private boolean isCapture(int move) {
        int to = Bitboard.getTo(move);
        return board.getPieceAt(SQUARES[to]) != Piece.NONE;
    }

    private boolean isPromotion(int move) {
        return Bitboard.getPromo(move) != 0;
    }

    public int getNodesSearched() {
        return nodesSearched;
    }

    public int getDepthReached() {
        return depthReached;
    }

    private int getPieceValue(Piece piece) {
        if (piece == null || piece == Piece.NONE) {
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
