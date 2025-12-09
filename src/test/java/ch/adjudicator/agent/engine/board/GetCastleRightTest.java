package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Side;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GetCastleRightTest {

    @ParameterizedTest
    @MethodSource("provideFens")
    void testGetCastleRightBitboard(String fen, CastleRight expectedWhite, CastleRight expectedBlack) {
        Bitboard board = new Bitboard();
        board.loadFromFen(fen);

        assertEquals(expectedWhite, board.getCastleRight(Side.WHITE), "Failed White rights for FEN: " + fen);
        assertEquals(expectedBlack, board.getCastleRight(Side.BLACK), "Failed Black rights for FEN: " + fen);
    }

    private static Stream<Object[]> provideFens() {
        List<Object[]> cases = new ArrayList<>();
        
        // Remove the empty board if it causes issues, stick to 6 valid setups.
        // Setup 1: Start
        // Setup 2: e4
        // Setup 3: Kings only
        // Setup 4: Rooks/Kings
        // Setup 5: Center Kings
        // Setup 6: Corner Kings
        
        String[] setups = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR",
            "4k3/8/8/8/8/8/8/4K3",
            "r3k2r/8/8/8/8/8/8/R3K2R",
            "8/8/3k4/8/8/3K4/8/8",
            "k7/8/8/8/8/8/8/K7"
        };

        // Permutations of rights
        // White rights: KQ, K, Q, -
        // Black rights: kq, k, q, -
        
        String[] whiteOpts = {"KQ", "K", "Q", "-"};
        String[] blackOpts = {"kq", "k", "q", "-"};
        
        for (String w : whiteOpts) {
            for (String b : blackOpts) {
                // Construct castling string
                String castling = "";
                if (!w.equals("-")) castling += w;
                if (!b.equals("-")) castling += b;
                if (castling.isEmpty()) castling = "-";
                
                // Determine expected objects
                CastleRight wRight = getRight(w, true);
                CastleRight bRight = getRight(b, false);
                
                // Add test cases for each board setup
                for (String setup : setups) {
                    String fen = setup + " w " + castling + " - 0 1";
                    cases.add(new Object[]{fen, wRight, bRight});
                }
            }
        }
        
        return cases.stream();
    }
    
    private static CastleRight getRight(String s, boolean white) {
        if (s.equals("-")) return CastleRight.NONE;
        if (white) {
            if (s.equals("KQ")) return CastleRight.KING_AND_QUEEN_SIDE;
            if (s.equals("K")) return CastleRight.KING_SIDE;
            if (s.equals("Q")) return CastleRight.QUEEN_SIDE;
        } else {
            if (s.equals("kq")) return CastleRight.KING_AND_QUEEN_SIDE;
            if (s.equals("k")) return CastleRight.KING_SIDE;
            if (s.equals("q")) return CastleRight.QUEEN_SIDE;
        }
        return CastleRight.NONE;
    }
}
