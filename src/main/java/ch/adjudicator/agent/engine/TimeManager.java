package ch.adjudicator.agent.engine;

/**
 * Time management for chess games.
 * Allocates thinking time dynamically based on game situation.
 */
public class TimeManager {
    private static final long LAG_BUFFER_MS = 50;
    private static final long MIN_TIME_MS = 100;
    private static final long MAX_TIME_MS = 10000;
    private static final long PANIC_THRESHOLD_MS = 1000;

    private final int incrementMs;
    private int moveNumber;

    public TimeManager(int incrementMs) {
        this.incrementMs = incrementMs;
        this.moveNumber = 1;
    }

    /**
     * Allocate thinking time for the current move.
     *
     * @param timeRemainingMs Time remaining on the clock
     * @param moveNumber      Current move number (for time curve adjustment)
     * @return Allocated time in milliseconds
     */
    public long allocateTime(long timeRemainingMs, int moveNumber) {
        this.moveNumber = moveNumber;

        // Safety buffer: subtract lag
        long available = Math.max(0, timeRemainingMs - LAG_BUFFER_MS);

        // Panic mode: very low time remaining
        if (available < PANIC_THRESHOLD_MS) {
            return available / 2;
        }

        // Base calculation: divide by expected remaining moves
        // Use 20 as a reasonable estimate (games typically last 40 moves)
        long thinkingTime = available / 20;

        // Add increment consideration (we get time back after the move)
        thinkingTime += incrementMs / 2;

        // Curve adjustment: spend more time in middlegame
        double multiplier = getTimeMultiplier(moveNumber);
        thinkingTime = (long) (thinkingTime * multiplier);

        // Apply hard limits
        thinkingTime = Math.max(MIN_TIME_MS, thinkingTime);
        thinkingTime = Math.min(MAX_TIME_MS, thinkingTime);

        // Don't use more than 10% of remaining time in one move (unless forced)
        thinkingTime = Math.min(thinkingTime, available / 10);

        // Cap time at 500ms for first 3 moves
        if (moveNumber <= 3) {
            thinkingTime = Math.min(thinkingTime, 500);
        }

        return thinkingTime;
    }

    /**
     * Get time multiplier based on move number.
     * Opening (moves 1-10): play faster (0.3x)
     * Middlegame (moves 11-30): play normal/slower (1.2x)
     * Endgame (moves 31+): play stronger (1.3x)
     */
    private double getTimeMultiplier(int moveNumber) {
        if (moveNumber <= 10) {
            return 0.3; // Opening: play very fast if out of book
        } else if (moveNumber <= 30) {
            return 1.2; // Middlegame: take more time for critical positions
        } else {
            return 1.3; // Endgame: allocate extra time for precise endgame play
        }
    }

    /**
     * Check if we should stop searching due to time constraints.
     *
     * @param startTime     Search start time in milliseconds
     * @param allocatedTime Allocated time for this move
     * @return true if we should stop searching
     */
    public boolean shouldStop(long startTime, long allocatedTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed >= allocatedTime;
    }

    /**
     * Get a "soft" time limit (used for completing current depth).
     * Returns 85% of allocated time to allow finishing current iteration.
     */
    public long getSoftLimit(long allocatedTime) {
        return (long) (allocatedTime * 0.85);
    }

    /**
     * Get current move number.
     */
    public int getMoveNumber() {
        return moveNumber;
    }

    /**
     * Update move number.
     */
    public void setMoveNumber(int moveNumber) {
        this.moveNumber = moveNumber;
    }
}
