package ch.adjudicator.agent;

import ch.adjudicator.agent.engine.*;
import ch.adjudicator.agent.engine.board.Bitboard;
import ch.adjudicator.client.*;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * High-performance native chess agent capable of defeating chess masters.
 * Uses bitboard engine, opening book, alpha-beta search, and time management.
 */
@SuppressWarnings("DuplicatedCode")
public class ProAgent implements Agent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProAgent.class);
    private final boolean ponderingEnabled;

    private final String name;
    private final TranspositionTable transpositionTable;
    private final MoveOrdering moveOrdering;
    private final CpuTemperatureMonitor temperatureMonitor;
    private Bitboard board;
    private PolyglotBook bookPerfect;
    private PolyglotBook bookCerebellum;
    private Color myColor;
    private TimeManager timeManager;
    private int moveCount;
    private boolean disableBookLookup;
    @Getter
    private boolean lastMoveFromBook;
    private Search ponderSearch;
    private Thread ponderThread;

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PonderWatchdog");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> watchdogTask;
    private long ponderTimeoutMs = 5 * 60 * 1000; // 5 minutes

    public ProAgent(String name, boolean monitorCpuTemp) {
        this(name, monitorCpuTemp, true);
    }

    public ProAgent(String name, boolean monitorCpuTemp, boolean ponderingEnabled) {
        this.name = name;
        this.board = new Bitboard();
        this.moveCount = 0;
        this.disableBookLookup = false;
        this.transpositionTable = new TranspositionTable();
        this.moveOrdering = new MoveOrdering(transpositionTable);
        this.temperatureMonitor = monitorCpuTemp ? new CpuTemperatureMonitor() : null;
        this.ponderingEnabled = ponderingEnabled;

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
        ProAgent agent = new ProAgent(config.getAgentName(), config.isMonitorCpuTemp(), config.isPonderingEnabled());

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

        // Decay history scores
        moveOrdering.decayHistory();

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

        int selectedMove = 0;
        lastMoveFromBook = false;

        // 1. Try opening book first
        if (moveCount <= 12 && !disableBookLookup) {
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
                            selectedMove = encodeMove(legal);
                            lastMoveFromBook = true;
                            LOGGER.info("[{}] Using book move: {}", name, bookMoveStr);
                            break;
                        }
                    }
                } else {
                    LOGGER.info("[{}] Unable to find move in any book", name);
                    if (moveCount > 5) {
                        disableBookLookup = true;
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[{}] Book lookup failed: {}", name, e.getMessage());
            }
        }

        // Optimize memory after move 12 by clearing opening books
        if (moveCount == 12) {
            LOGGER.info("[{}] Move 12 reached, clearing opening books to free memory", name);
            bookPerfect = null;
            bookCerebellum = null;
            System.gc();
            LOGGER.info("[{}] Opening books cleared and GC requested", name);
        }

        // 2. If not in book, use search
        if (selectedMove == 0) {
            // Allocate time for this move
            long allocatedTime = timeManager.allocateTime(request.getYourTimeMs(), moveCount);
            LOGGER.info("[{}] Allocated time: {}ms", name, allocatedTime);

            // Search for best move
            long searchStart = System.currentTimeMillis();
            Search search = new Search(board, transpositionTable, moveOrdering);
            search.setTimeManager(timeManager);
            selectedMove = search.findBestMove(allocatedTime);
            long searchTime = System.currentTimeMillis() - searchStart;

            LOGGER.info("[{}] Search complete: {}ms, {} nodes, move: {}, depth: {}",
                    name, searchTime, search.getNodesSearched(),
                    selectedMove != 0 ? moveToLAN(selectedMove) : "null", search.getDepthReached());

            if (selectedMove == 0) {
                // Fallback: pick first legal move
                LOGGER.warn("[{}] Search returned null, using fallback", name);
                selectedMove = encodeMove(legalMoves.getFirst());
            }
        }

        // Apply move to board
        board.makeMove(selectedMove);

        String moveStr = moveToLAN(selectedMove);
        LOGGER.info("[{}] Playing: {} (from {} legal moves)", name, moveStr, legalMoves.size());

        // Start pondering (only if CPU temperature is safe)
        if (ponderingEnabled && (temperatureMonitor == null || temperatureMonitor.isSafeForPondering())) {
            long zobristHash = board.getZobristKey();
            int ponderMove = transpositionTable.getBestMove(zobristHash);

            if (ponderMove != 0) {
                LOGGER.info("[{}] Pondering on {}", name, moveToLAN(ponderMove));
                Bitboard ponderBoard = new Bitboard();
                ponderBoard.loadFromFen(board.getFen());
                ponderBoard.makeMove(ponderMove);

                ponderSearch = new Search(ponderBoard, transpositionTable, moveOrdering);
                ponderThread = new Thread(() -> {
                    ponderSearch.findBestMove(36000000L); // 10 hours
                });
                ponderThread.start();

                // Schedule watchdog
                watchdogTask = watchdog.schedule(this::triggerEndGameDueToTimeout, ponderTimeoutMs, TimeUnit.MILLISECONDS);
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
        board = new Bitboard();
        moveCount = 0;
        int incrementMs = info.getIncrementMs();
        timeManager = new TimeManager(incrementMs);

        // Clear transposition table for new game
        transpositionTable.clear();
        moveOrdering.reset();
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

    private String moveToLAN(int move) {
        if (move == 0) return "0000";
        Square from = Square.values()[Bitboard.getFrom(move)];
        Square to = Square.values()[Bitboard.getTo(move)];
        int promo = Bitboard.getPromo(move);

        StringBuilder sb = new StringBuilder();
        sb.append(from.toString().toLowerCase());
        sb.append(to.toString().toLowerCase());

        if (promo != 0) {
            switch (promo) {
                case 1 -> sb.append("n");
                case 2 -> sb.append("b");
                case 3 -> sb.append("r");
                case 4 -> sb.append("q");
            }
        }
        return sb.toString();
    }

    private int encodeMove(Move move) {
        int promo = 0;
        if (move.getPromotion() != com.github.bhlangonijr.chesslib.Piece.NONE) {
            // Map piece to 1-4
            String p = move.getPromotion().getPieceType().toString();
            if (p.equals("KNIGHT")) promo = 1;
            else if (p.equals("BISHOP")) promo = 2;
            else if (p.equals("ROOK")) promo = 3;
            else if (p.equals("QUEEN")) promo = 4;
        }
        return Bitboard.encodeMove(move.getFrom().ordinal(), move.getTo().ordinal(), promo);
    }

    /**
     * Set ponder timeout for testing.
     */
    protected void setPonderTimeoutMs(long ms) {
        this.ponderTimeoutMs = ms;
    }

    protected void disableBookForTesting() {
        this.disableBookLookup = true;
    }

    /**
     * Trigger end game due to pondering timeout.
     * Can be overridden for testing.
     */
    protected void triggerEndGameDueToTimeout() {
        LOGGER.error("[{}] Pondering timeout ({} ms). Ending game.", name, ponderTimeoutMs);
        System.exit(1);
    }

    private void stopPondering() {
        if (watchdogTask != null) {
            watchdogTask.cancel(false);
            watchdogTask = null;
        }

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
