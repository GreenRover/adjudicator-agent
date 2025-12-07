package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
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
    
    private Board board;
    private long stopTime;
    private volatile boolean stopped;
    private Move bestMoveFound;
    private int nodesSearched;
    private int depthReached;
    
    // Advanced move ordering
    private TranspositionTable transpositionTable;
    private MoveOrdering moveOrdering;
    
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
    
    public void stop() {
        this.stopped = true;
    }
    
    public void runHelper() {
        runIterativeDeepening(0); // 0 means relying on stopTime or external stop
    }

    private void runIterativeDeepening(long allocatedTimeMs) {
        // Iterative Deepening: search progressively deeper
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (stopped) {
                break;
            }
            
            int score = searchRoot(depth);
            
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
    private int searchRoot(int depth) {
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
        int alpha = -INFINITY;
        int beta = INFINITY;
        
        // Use pickBestMove for advanced move ordering
        for (int i = 0; i < moves.size(); i++) {
            if (stopped) {
                break;
            }
            
            // Pick best remaining move and swap to position i
            moveOrdering.pickBestMove(board, moves, i, hashMove, 0, zobristHash);
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
                alpha = score;
            }
        }
        
        // Store result in transposition table
        if (!stopped && localBestMove != null) {
            bestMoveFound = localBestMove;
            transpositionTable.store(zobristHash, localBestMove, bestScore, depth, 
                                    TranspositionTable.TTEntry.EXACT);
        }
        
        return bestScore;
    }
    
    /**
     * Alpha-Beta pruning search (Negamax framework).
     * 
     * @param alpha Lower bound
     * @param beta Upper bound
     * @param depth Remaining depth to search
     * @param ply Distance from root (for killer moves)
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
        
        // Use pickBestMove for advanced move ordering
        for (int i = 0; i < moves.size(); i++) {
            if (stopped) {
                break;
            }
            
            // Pick best remaining move and swap to position i
            moveOrdering.pickBestMove(board, moves, i, hashMove, ply, zobristHash);
            Move move = moves.get(i);
            
            board.doMove(move);
            int score = -alphaBeta(-beta, -alpha, depth - 1, ply + 1);
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
        nodesSearched++;
        
        // Stand-pat: evaluate current position
        BoardStatus fastBoard = new BoardStatus(board);
        int standPat = Evaluator.evaluate(fastBoard);
        
        if (standPat >= beta) {
            return beta;
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
        String moveStr = move.toString().toLowerCase();
        return moveStr.length() > 4; // Promotions have extra character
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
}
