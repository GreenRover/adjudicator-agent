package ch.adjudicator.agent;

import ch.adjudicator.client.*;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Easy Agent Example for Adjudicator Chess Contest Platform.
 * This agent uses the chesslib library to maintain proper board state
 * and generate legal moves. It selects randomly from all legal moves.
 */
public class EasyAgent implements Agent {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyAgent.class);

    private final String name;
    private final Random random;
    private Board board;

    public EasyAgent(String name) {
        this.name = name;
        this.random = new Random();
        this.board = new Board();
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
        EasyAgent agent = new EasyAgent(config.getAgentName());

        // Create client and play game
        AdjudicatorClient client = new AdjudicatorClient(config.getServerAddress(), config.getApiKey(), true);

        try {
            client.playGame(agent, mode, config.getTimeControl());
            LOGGER.info("Agent finished successfully");
        } catch (Exception e) {
            LOGGER.error("Game error", e);
            System.exit(1);
        }
    }

    @Override
    public String getMove(MoveRequest request) throws Exception {
        LOGGER.info("[{}] My turn! Time remaining: {}ms", name, request.getYourTimeMs());

        // Update board with opponent's move if present
        if (!request.getOpponentMove().isEmpty()) {
            String opponentMove = request.getOpponentMove();
            LOGGER.info("[{}] Opponent played: {}", name, opponentMove);

            try {
                Move move = parseMove(opponentMove);
                board.doMove(move);
            } catch (Exception e) {
                LOGGER.warn("[{}] Failed to parse opponent move: {}", name, opponentMove, e);
                // If we can't parse the move, reset the board and continue
                // This shouldn't happen in normal gameplay
            }
        }

        // Generate all legal moves
        List<Move> legalMoves = board.legalMoves();

        if (legalMoves.isEmpty()) {
            LOGGER.error("[{}] No legal moves available!", name);
            throw new Exception("No legal moves available");
        }

        // Select a random legal move
        Move selectedMove = legalMoves.get(random.nextInt(legalMoves.size()));

        // Apply the move to our board
        board.doMove(selectedMove);

        // Convert to Long Algebraic Notation (LAN)
        String moveStr = moveToLAN(selectedMove);
        LOGGER.info("[{}] Playing move: {} (from {} legal moves)", name, moveStr, legalMoves.size());
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        return moveStr;
    }

    @Override
    public void onGameStart(GameInfo info) {
        LOGGER.info("[{}] *** Game Started ***", name);
        LOGGER.info("[{}] Game ID: {}", name, info.getGameId());
        LOGGER.info("[{}] Playing as: {}", name, info.getColor());
        LOGGER.info("[{}] Time control: {}ms + {}ms increment",
                name, info.getInitialTimeMs(), info.getIncrementMs());

        // Reset the board for a new game
        board = new Board();
    }

    @Override
    public void onGameOver(GameOverInfo info) {
        LOGGER.info("[{}] *** Game Over ***", name);
        LOGGER.info("[{}] Result: {}", name, info.getResult());
        LOGGER.info("[{}] Reason: {}", name, info.getReason());
        if (!info.getFinalPgn().isEmpty()) {
            LOGGER.info("[{}] Final PGN:\n{}", name, info.getFinalPgn());
        }
    }

    @Override
    public void onError(String message, Throwable cause) {
        LOGGER.error("[{}] ERROR: {}", name, message);
    }

    /**
     * Parse a move in Long Algebraic Notation (LAN) format.
     * Examples: "e2e4", "e7e8q" (promotion)
     */
    private Move parseMove(String lan) {
        // chesslib expects moves in format like "E2E4"
        // LAN format: source square + destination square + optional promotion piece
        String upperLan = lan.toUpperCase();

        // Find the move in legal moves that matches
        List<Move> legalMoves = board.legalMoves();
        for (Move move : legalMoves) {
            String moveLan = moveToLAN(move);
            if (moveLan.equalsIgnoreCase(lan)) {
                return move;
            }
        }

        // If not found in legal moves, try to construct it
        // This is a fallback that shouldn't normally be needed
        return new Move(upperLan, board.getSideToMove());
    }

    /**
     * Convert a Move object to Long Algebraic Notation (LAN).
     * Examples: "e2e4", "e7e8q" (for promotion to queen)
     */
    private String moveToLAN(Move move) {
        return move.toString().toLowerCase();
    }
}
