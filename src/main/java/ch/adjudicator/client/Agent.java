package ch.adjudicator.client;

/**
 * Abstract interface for chess agents.
 * <p>
 * Agents must implement this interface to play games on the Adjudicator platform.
 */
public interface Agent {
    /**
     * Called when it's the agent's turn to move.
     *
     * @param request MoveRequest containing opponent's move and time information
     * @return Move in Long Algebraic Notation (e.g., "e2e4", "e7e8q" for promotion)
     * @throws Exception If an error occurs generating the move
     */
    String getMove(MoveRequest request) throws Exception;

    /**
     * Called when a game begins.
     *
     * @param info GameInfo containing game ID, color, and time control
     */
    void onGameStart(GameInfo info);

    /**
     * Called when the game ends.
     *
     * @param info GameOverInfo containing result, reason, and final PGN
     */
    void onGameOver(GameOverInfo info);

    /**
     * Called when a non-terminal error occurs.
     *
     * @param message Error message from the server
     */
    void onError(String message, Throwable cause);
}
