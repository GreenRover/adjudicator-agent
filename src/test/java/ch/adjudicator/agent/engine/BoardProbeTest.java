
package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;

import org.junit.jupiter.api.Test;

public class BoardProbeTest {
    @Test
    public void probe() {
        // Bitboard b = new Bitboard(); // Check if exists
        try {
            Class.forName("com.github.bhlangonijr.chesslib.Bitboard");
            System.out.println("Bitboard class found");
        } catch (ClassNotFoundException e) {
            System.out.println("Bitboard class NOT found");
        }
        
        Board board = new Board();
        // Check for bitboard access
        // System.out.println(board.getBitboard(Piece.WHITE_PAWN)); 
        // If the above line compiles, we are good.
        // We can inspect methods via reflection
        for (java.lang.reflect.Method m : Board.class.getMethods()) {
            if (m.getName().contains("Bitboard") || m.getName().contains("attack")) {
                System.out.println(m.getName());
            }
        }
    }
}
