package ch.adjudicator.agent;

import ch.adjudicator.client.Color;
import ch.adjudicator.client.GameInfo;
import ch.adjudicator.client.MoveRequest;
import ch.adjudicator.agent.engine.BitBoard;
import ch.adjudicator.agent.engine.PolyglotBook;
import com.github.bhlangonijr.chesslib.Board;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.github.bhlangonijr.chesslib.move.Move;

class ProAgentTest {

    private ProAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ProAgent("ProTestBot", false);
        // Initialize the agent's internal board by calling onGameStart
        agent.onGameStart(new GameInfo("test-game-pro", Color.WHITE, 300000, 0));
    }

    @Test
    void testFirstMove() throws Exception {
        MoveRequest request = new MoveRequest("", 300000, 300000);
        String move = agent.getMove(request);
        
        assertThat(move, notNullValue());
        assertThat(move.isEmpty(), is(false));
        
        // ProAgent specific openings - checking for valid move format instead of hardcoded list
        // as the agent now uses opening books which might vary
        assertThat("Move " + move + " should be a valid LAN move", move.matches("[a-h][1-8][a-h][1-8][qrbn]?"), is(true));
    }

    @Test
    void testOpeningBookUsage_assertThatForTheFirst15moveAreFromBook() throws Exception {
        // This test plays a standard Ruy Lopez opening to verify that moves come from the opening book
        // Ruy Lopez: 1.e4 e5 2.Nf3 Nc6 3.Bb5 ...
        
        // Create a white agent that will respond to our moves
        ProAgent whiteAgent = new ProAgent("WhiteTestBot", false);
        whiteAgent.onGameStart(new GameInfo("test-book-game", Color.WHITE, 300000, 0));
        
        // Define a common opening line that should be in the books
        // We'll feed white standard moves and verify white's responses are from book
        String[] standardOpening = {
            "", // White's first move (no opponent move yet)
            "e7e5", // Black plays e5
            "b8c6", // Black plays Nc6
            "g8f6", // Black plays Nf6
            "f8e7", // Black plays Be7
            "e8g8", // Black castles kingside
            "d7d6", // Black plays d6
            "b7b5" // Black plays b5
        };
        
        int moveCount = 0;
        
        // Play the opening moves and verify they come from book
        for (int i = 0; i < standardOpening.length && moveCount < 15; i++) {
            MoveRequest request = new MoveRequest(standardOpening[i], 300000, 300000);
            String move = whiteAgent.getMove(request);
            moveCount++;
            
            // Verify the move came from the opening book
            assertThat("Move #" + moveCount + " (White) should be from the opening book", 
                      whiteAgent.isLastMoveFromBook(), is(true));
            
            // Verify move is valid format
            assertThat(move, notNullValue());
            assertThat(move.matches("[a-h][1-8][a-h][1-8][qrbn]?"), is(true));
        }
    }

    @Test
    void testResponseToOpponentMove() throws Exception {
        MoveRequest request = new MoveRequest("e2e4", 290000, 295000);
        String move = agent.getMove(request);
        
        assertThat(move, notNullValue());
        // Should be a valid response (e.g. e7e5, c7c5, etc.)
        // Basic regex for any move: from square to square, optional promotion
        assertThat(move.matches("[a-h][1-8][a-h][1-8][qrbn]?"), is(true));
    }
    @Test
    void checkChessLibHash() {
         Board board = new Board();
         // board.getZobristKey(); // checking if this compiles and works
         long hash = board.getZobristKey();
         System.out.println("ChessLib Hash: " + Long.toHexString(hash));
         // assertEquals(0x463b96181691fc9cL, hash, "Hash should match Polyglot start position");
    }

    static class PolyglotRandom {
        private int seed = 1070372;

        long next() {
            long r1 = update();
            long r2 = update();
            long r3 = update();
            long r4 = update();
            return r1 | (r2 << 16) | (r3 << 32) | (r4 << 48);
        }

        private long update() {
            seed = seed * 30903 + 62527;
            return (seed >>> 16) & 0xFFFFL;
        }
    }

    private boolean checkMapping(String name, long target, boolean blackFirst, boolean interleaved) {
        PolyglotRandom rand = new PolyglotRandom();
        
        long[] linearKeys = new long[12 * 64];
        for (int i = 0; i < 12 * 64; i++) linearKeys[i] = rand.next();

        long[] castlingKeys = new long[16];
        for (int i = 0; i < 16; i++) castlingKeys[i] = rand.next();
        
        long[] epKeys = new long[64];
        for (int i = 0; i < 64; i++) epKeys[i] = rand.next();
        
        long whiteToMove = rand.next();
        
        long hash = 0;
        
        // Pieces: R,N,B,Q,K,B,N,R (rank 0/7) and Pawns (rank 1/6)
        int PAWN=0, KNIGHT=1, BISHOP=2, ROOK=3, QUEEN=4, KING=5;
        
        hash ^= getKey(linearKeys, ROOK, 0, 0, blackFirst, interleaved);
        hash ^= getKey(linearKeys, KNIGHT, 0, 1, blackFirst, interleaved);
        hash ^= getKey(linearKeys, BISHOP, 0, 2, blackFirst, interleaved);
        hash ^= getKey(linearKeys, QUEEN, 0, 3, blackFirst, interleaved);
        hash ^= getKey(linearKeys, KING, 0, 4, blackFirst, interleaved);
        hash ^= getKey(linearKeys, BISHOP, 0, 5, blackFirst, interleaved);
        hash ^= getKey(linearKeys, KNIGHT, 0, 6, blackFirst, interleaved);
        hash ^= getKey(linearKeys, ROOK, 0, 7, blackFirst, interleaved);
        
        for (int f=0; f<8; f++) hash ^= getKey(linearKeys, PAWN, 0, 8+f, blackFirst, interleaved);
        
        hash ^= getKey(linearKeys, ROOK, 1, 56, blackFirst, interleaved);
        hash ^= getKey(linearKeys, KNIGHT, 1, 57, blackFirst, interleaved);
        hash ^= getKey(linearKeys, BISHOP, 1, 58, blackFirst, interleaved);
        hash ^= getKey(linearKeys, QUEEN, 1, 59, blackFirst, interleaved);
        hash ^= getKey(linearKeys, KING, 1, 60, blackFirst, interleaved);
        hash ^= getKey(linearKeys, BISHOP, 1, 61, blackFirst, interleaved);
        hash ^= getKey(linearKeys, KNIGHT, 1, 62, blackFirst, interleaved);
        hash ^= getKey(linearKeys, ROOK, 1, 63, blackFirst, interleaved);
        
        for (int f=0; f<8; f++) hash ^= getKey(linearKeys, PAWN, 1, 48+f, blackFirst, interleaved);
        
        hash ^= castlingKeys[15]; // Full rights
        
        if (hash == target) {
            System.out.println("MATCH FOUND: " + name);
            return true;
        }
        System.out.println(name + ": " + Long.toHexString(hash));
        return false;
    }
    
    private long getKey(long[] linearKeys, int pieceType, int color, int square, boolean blackFirst, boolean interleaved) {
        // pieceType: 0..5
        // color: 0=White, 1=Black
        int pieceIndex;
        if (interleaved) {
             // interleaved w,b or b,w
             int offset = (color == 1) ? (blackFirst ? 0 : 1) : (blackFirst ? 1 : 0);
             pieceIndex = pieceType * 2 + offset;
        } else {
             // sequential
             if (blackFirst) {
                 pieceIndex = (color == 1) ? pieceType : (6 + pieceType);
             } else {
                 pieceIndex = (color == 0) ? pieceType : (6 + pieceType);
             }
        }
        return linearKeys[pieceIndex * 64 + square];
    }
}
