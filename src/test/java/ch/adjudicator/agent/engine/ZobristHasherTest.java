package ch.adjudicator.agent.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZobristHasherTest {

    @Test
    public void testStartPositionKey() {
        String startPos = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String expectedHex = "463b96181691fc9c"; // created with https://shinkarom.github.io/zobrist/
        long key = ZobristHasher.getZobristKey(startPos);
        String actualHex = ZobristHasher.toHex(key);

        assertEquals(expectedHex, actualHex, "Zobrist key for start position should match Polyglot standard");
    }
}
