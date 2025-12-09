package ch.adjudicator.agent;

import ch.adjudicator.agent.engine.*;
import ch.adjudicator.client.*;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * High-performance native chess agent capable of defeating chess masters.
 * Uses bitboard engine, opening book, alpha-beta search, and time management.
 */
@SuppressWarnings("DuplicatedCode")
public class ProAgent implements Agent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProAgent.class);

    private final String name;
    private final TranspositionTable transpositionTable;
    private final CpuTemperatureMonitor temperatureMonitor;
    private BoardInterface board;
    private PolyglotBook bookPerfect;
    private PolyglotBook bookCerebellum;
    private Color myColor;
    private TimeManager timeManager;
    private int moveCount;
    @Getter
    private boolean lastMoveFromBook;
    private Search ponderSearch;
    private Thread ponderThread;

    public ProAgent(String name, boolean monitorCpuTemp) {
        this.name = name;
        this.board = new ChesslibBoard();
        this.moveCount = 0;
        this.transpositionTable = new TranspositionTable();
        this.temperatureMonitor = monitorCpuTemp ? new CpuTemperatureMonitor() : null;

        // Load opening books
        LOGGER.info("[{}] Loading opening books...", name);
        try {
            bookPerfect = new PolyglotBook("/polyglot/Perfect2023.bin");
            LOGGER.info("[{}] Loaded Perfect2023.bin: {} entries", name, bookPerfect.size());
        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to load Perfect2023.bin: {}", name, e.getMessage());
        }

        try {
            bookCerebellum = new PolyglotBook("/polyglot/Cerebellum3Merge.bin");
            LOGGER.info("[{}] Loaded Cerebellum3Merge.bin: {} entries", name, bookCerebellum.size());
        } catch (Exception e) {
            LOGGER.warn("[{}] Failed to load Cerebellum3Merge.bin: {}", name, e.getMessage());
        }
    }

    public static void main(String[] args) {
        AgentConfiguration config = new AgentConfiguration(args);

        try {
            config.validate();
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        // Parse game mode
        GameMode mode;
        try {
            mode = GameMode.valueOf(config.getMode());
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid game mode: " + config.getMode());
            System.err.println("Valid modes: TRAINING, OPEN, RANKED");
            System.exit(1);
            return;
        }

        LOGGER.info("Starting {} agent...", config.getAgentName());
        LOGGER.info("Server: {}", config.getServerAddress());
        LOGGER.info("Mode: {}", config.getMode());
        LOGGER.info("Time control: {}", config.getTimeControl());
        LOGGER.info("Protocol: gRPC");

        // Create agent
        ProAgent agent = new ProAgent(config.getAgentName(), config.isMonitorCpuTemp());

        // Create client and play game
        AdjudicatorClient client = new AdjudicatorClient(config.getServerAddress(), config.getApiKey(), true);

        // Loop to play multiple games
        while (true) {
            try {
                LOGGER.info("Starting new game...");
                client.playGame(agent, mode, config.getTimeControl());
                LOGGER.info("Game finished successfully");
            } catch (Exception e) {
                LOGGER.error("Game error", e);
                System.exit(1);
            }
        }
    }

    @Override
    public String getMove(MoveRequest request) throws Exception {
        // Stop pondering
        stopPondering();

        moveCount++;
        LOGGER.info("[{}] Move #{} - Time remaining: {}ms", name, moveCount, request.getYourTimeMs());


        // Update board with opponent's move
        if (!request.getOpponentMove().isEmpty()) {
            String opponentMove = request.getOpponentMove();
            LOGGER.info("[{}] Opponent played: {}", name, opponentMove);

            try {
                Move move = parseMove(opponentMove);
                board.doMove(move);
            } catch (Exception e) {
                LOGGER.error("[{}] Failed to parse opponent move: {}", name, opponentMove, e);
                throw new Exception("Failed to parse opponent move: " + opponentMove);
            }
        }

        // Check legal moves
        List<Move> legalMoves = board.legalMoves();
        if (legalMoves.isEmpty()) {
            LOGGER.error("[{}] No legal moves available!", name);
            throw new Exception("No legal moves available");
        }

        Move selectedMove = null;
        lastMoveFromBook = false;

        // 1. Try opening book first
        if (moveCount <= 15) {
            // Determine color if not set (fallback)
            if (myColor == null) {
                myColor = request.getOpponentMove().isEmpty() ? Color.WHITE : Color.BLACK;
            }

            try {
                PolyglotBook.BookEntry bookMove = null;

                if (bookCerebellum != null) {
                    bookMove = bookCerebellum.getBestMove(board);
                }

                if (bookMove != null) {
                    String bookMoveStr = PolyglotBook.moveToLAN(bookMove);

                    // Verify book move is legal
                    for (Move legal : legalMoves) {
                        if (moveToLAN(legal).equals(bookMoveStr)) {
                            selectedMove = legal;
                            lastMoveFromBook = true;
                            LOGGER.info("[{}] Using book move: {}", name, bookMoveStr);
                            break;
                        }
                    }
                } else {
                    LOGGER.info("[{}] Unable to find move in any book", name);
                }
            } catch (Exception e) {
                LOGGER.warn("[{}] Book lookup failed: {}", name, e.getMessage());
            }
        }

        // Optimize memory after move 15 by clearing opening books
        if (moveCount == 15) {
            LOGGER.info("[{}] Move 15 reached, clearing opening books to free memory", name);
            bookPerfect = null;
            bookCerebellum = null;
            System.gc();
            LOGGER.info("[{}] Opening books cleared and GC requested", name);
        }

        // 2. If not in book, use search
        if (selectedMove == null) {
            // Allocate time for this move
            long allocatedTime = timeManager.allocateTime(request.getYourTimeMs(), moveCount);
            LOGGER.info("[{}] Allocated time: {}ms", name, allocatedTime);

            // Search for best move
            long searchStart = System.currentTimeMillis();
            Search search = new Search(board, transpositionTable);
            selectedMove = search.findBestMove(allocatedTime);
            long searchTime = System.currentTimeMillis() - searchStart;

            LOGGER.info("[{}] Search complete: {}ms, {} nodes, move: {}, depth: {}",
                    name, searchTime, search.getNodesSearched(),
                    selectedMove != null ? moveToLAN(selectedMove) : "null", search.getDepthReached());

            if (selectedMove == null) {
                // Fallback: pick first legal move
                LOGGER.warn("[{}] Search returned null, using fallback", name);
                selectedMove = legalMoves.getFirst();
            }
        }

        // Apply move to board
        board.doMove(selectedMove);

        String moveStr = moveToLAN(selectedMove);
        LOGGER.info("[{}] Playing: {} (from {} legal moves)", name, moveStr, legalMoves.size());

        // Start pondering (only if CPU temperature is safe)
        if (temperatureMonitor == null || temperatureMonitor.isSafeForPondering()) {
            long zobristHash = board.getZobristKey();
            Move ponderMove = transpositionTable.getBestMove(zobristHash);

            if (ponderMove != null) {
                LOGGER.info("[{}] Pondering on {}", name, moveToLAN(ponderMove));
                BoardInterface ponderBoard = new ChesslibBoard();
                ponderBoard.loadFromFen(board.getFen());
                ponderBoard.doMove(ponderMove);

                ponderSearch = new Search(ponderBoard, transpositionTable);
                ponderThread = new Thread(() -> {
                    ponderSearch.findBestMove(36000000L); // 10 hours
                });
                ponderThread.start();
            }
        } else {
            LOGGER.info("[{}] Pondering disabled due to high CPU temperature", name);
        }

        return moveStr;
    }

    @Override
    public void onGameStart(GameInfo info) {
        stopPondering();

        if (temperatureMonitor != null) {
            temperatureMonitor.start();
        }
        LOGGER.info("[{}] *** Game Started ***", name);
        LOGGER.info("[{}] Game ID: {}", name, info.getGameId());
        LOGGER.info("[{}] Playing as: {}", name, info.getColor());
        this.myColor = info.getColor();
        LOGGER.info("[{}] Time control: {}ms + {}ms increment",
                name, info.getInitialTimeMs(), info.getIncrementMs());

        // Reset game state
        board = new ChesslibBoard();
        moveCount = 0;
        int incrementMs = info.getIncrementMs();
        timeManager = new TimeManager(incrementMs);

        // Clear transposition table for new game
        transpositionTable.clear();
    }

    @Override
    public void onGameOver(GameOverInfo info) {
        stopPondering();
        if (temperatureMonitor != null) {
            temperatureMonitor.stop();
        }
        LOGGER.info("[{}] *** Game Over ***", name);
        LOGGER.info("[{}] Result: {}", name, info.getResult());
        LOGGER.info("[{}] Reason: {}", name, info.getReason());
        if (!info.getFinalPgn().isEmpty()) {
            LOGGER.info("[{}] Final PGN:\n{}", name, info.getFinalPgn());
        }
    }

    @Override
    public void onError(String message, Throwable cause) {
        stopPondering();
        if (temperatureMonitor != null) {
            temperatureMonitor.stop();
        }
        LOGGER.error("[{}] ERROR: {}", name, message, cause);
    }

    /**
     * Parse a move in Long Algebraic Notation (LAN) format.
     */
    private Move parseMove(String lan) {
        String upperLan = lan.toUpperCase();

        // Find the move in legal moves that matches
        List<Move> legalMoves = board.legalMoves();
        for (Move move : legalMoves) {
            String moveLan = moveToLAN(move);
            if (moveLan.equalsIgnoreCase(lan)) {
                return move;
            }
        }

        // Fallback: construct move
        return new Move(upperLan, board.getSideToMove());
    }

    /**
     * Convert a Move to Long Algebraic Notation (LAN).
     */
    private String moveToLAN(Move move) {
        return move.toString().toLowerCase();
    }

    private void stopPondering() {
        if (ponderSearch != null) {
            ponderSearch.stop();
        }
        if (ponderThread != null) {
            try {
                ponderThread.join();
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while waiting for ponder thread", e);
            }
            ponderThread = null;
            ponderSearch = null;
        }
    }
}
