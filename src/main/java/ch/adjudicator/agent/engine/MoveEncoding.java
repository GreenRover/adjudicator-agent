package ch.adjudicator.agent.engine;

/**
 * Utility class to pack a move (from, to, promotion) into a single int.
 * <p>
 * Encoding:
 * 0-5: From square (0-63)
 * 6-11: To square (0-63)
 * 12-14: Promotion piece type (0: None, 1: Queen, 2: Rook, 3: Bishop, 4: Knight)
 */
public class MoveEncoding {

    public static final int PROMOTION_NONE = 0;
    public static final int PROMOTION_QUEEN = 1;
    public static final int PROMOTION_ROOK = 2;
    public static final int PROMOTION_BISHOP = 3;
    public static final int PROMOTION_KNIGHT = 4;

    private static final int FROM_MASK = 0x3F;
    private static final int TO_MASK = 0x3F;
    private static final int PROMOTION_MASK = 0x7;

    private static final int TO_SHIFT = 6;
    private static final int PROMOTION_SHIFT = 12;

    private MoveEncoding() {
        // Utility class
    }

    public static int pack(int from, int to, int promotion) {
        return (from & FROM_MASK) |
                ((to & TO_MASK) << TO_SHIFT) |
                ((promotion & PROMOTION_MASK) << PROMOTION_SHIFT);
    }
    
    public static int pack(int from, int to) {
        return pack(from, to, PROMOTION_NONE);
    }

    public static int getFrom(int move) {
        return move & FROM_MASK;
    }

    public static int getTo(int move) {
        return (move >>> TO_SHIFT) & TO_MASK;
    }

    public static int getPromotion(int move) {
        return (move >>> PROMOTION_SHIFT) & PROMOTION_MASK;
    }
}
