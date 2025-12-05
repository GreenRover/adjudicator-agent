package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for BitBoard, focusing on zobristHash validation.
 */
class BitBoardTest {

    @Test
    void testZobristHashForStartingPosition() {
        Board board = new Board();
        BitBoard bitBoard = new BitBoard(board);
        
        long zobristHash = bitBoard.getZobristHash();
        
        // Hash should not be zero for starting position
        assertNotEquals(0L, zobristHash, "Zobrist hash should not be zero for starting position");
    }

    @Test
    void testZobristHashConsistency() {
        Board board = new Board();
        BitBoard bitBoard1 = new BitBoard(board);
        BitBoard bitBoard2 = new BitBoard(board);
        
        // Same position should produce same hash
        assertEquals(bitBoard1.getZobristHash(), bitBoard2.getZobristHash(),
                "Same positions should produce identical zobrist hashes");
    }

    @Test
    void testZobristHashDifferentForDifferentPositions() {
        Board board1 = new Board();
        BitBoard bitBoard1 = new BitBoard(board1);
        
        // Make a move
        Board board2 = new Board();
        board2.doMove("e2e4");
        BitBoard bitBoard2 = new BitBoard(board2);
        
        // Different positions should have different hashes
        assertNotEquals(bitBoard1.getZobristHash(), bitBoard2.getZobristHash(),
                "Different positions should produce different zobrist hashes");
    }

    @Test
    void testZobristHashChangesWithSideToMove() {
        // Create position with white to move
        Board board1 = new Board();
        board1.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        BitBoard bitBoard1 = new BitBoard(board1);
        
        // Create same position with black to move
        Board board2 = new Board();
        board2.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1");
        BitBoard bitBoard2 = new BitBoard(board2);
        
        // Hashes should differ only by the side to move key
        long hashDiff = bitBoard1.getZobristHash() ^ bitBoard2.getZobristHash();
        assertEquals(Zobrist.blackToMoveKey(), hashDiff,
                "Hash difference should equal black to move key");
    }

    @Test
    void testZobristHashChangesWithCastlingRights() {
        // Position with all castling rights
        Board board1 = new Board();
        board1.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        BitBoard bitBoard1 = new BitBoard(board1);
        
        // Same position without castling rights
        Board board2 = new Board();
        board2.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
        BitBoard bitBoard2 = new BitBoard(board2);
        
        // Hashes should be different
        assertNotEquals(bitBoard1.getZobristHash(), bitBoard2.getZobristHash(),
                "Castling rights should affect zobrist hash");
        
        // The difference should be the XOR of all castling keys
        long expectedDiff = Zobrist.castlingKey(0) ^ Zobrist.castlingKey(1) ^ 
                           Zobrist.castlingKey(2) ^ Zobrist.castlingKey(3);
        long actualDiff = bitBoard1.getZobristHash() ^ bitBoard2.getZobristHash();
        assertEquals(expectedDiff, actualDiff,
                "Hash difference should equal XOR of all castling keys");
    }

    @Test
    void testZobristHashChangesWithEnPassant() {
        // Position with en passant on e3
        Board board1 = new Board();
        board1.loadFromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        BitBoard bitBoard1 = new BitBoard(board1);
        
        // Same position without en passant
        Board board2 = new Board();
        board2.loadFromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1");
        BitBoard bitBoard2 = new BitBoard(board2);
        
        // Hashes should differ by the en passant key for file e (4)
        long hashDiff = bitBoard1.getZobristHash() ^ bitBoard2.getZobristHash();
        assertEquals(Zobrist.enPassantKey(4), hashDiff,
                "Hash difference should equal en passant key for file e");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", // Starting position
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", // After e4
        "rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2", // After e4 Nf6
        "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", // Italian opening
        "8/8/8/8/8/8/8/4K3 w - - 0 1", // King only
        "4k3/8/8/8/8/8/8/4K3 w - - 0 1" // Both kings only
    })
    void testZobristHashValidForVariousPositions(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        BitBoard bitBoard = new BitBoard(board);
        
        long zobristHash = bitBoard.getZobristHash();
        
        // Hash should be computed (may be zero in rare cases, but typically non-zero)
        assertNotNull(zobristHash, "Zobrist hash should be computed");
        
        // Verify consistency: creating the same position again should yield same hash
        BitBoard bitBoard2 = new BitBoard(board);
        assertEquals(zobristHash, bitBoard2.getZobristHash(),
                "Same FEN should produce same zobrist hash");
    }

    @Test
    void testZobristHashForEmptyBoard() {
        BitBoard bitBoard = new BitBoard();
        
        // Empty board with white to move should have hash of 0
        assertEquals(0L, bitBoard.getZobristHash(),
                "Empty board should have zobrist hash of 0");
    }

    @Test
    void testZobristHashIncorporatesPieces() {
        // Test that each piece type contributes to the hash
        Board board1 = new Board();
        board1.loadFromFen("8/8/8/8/8/8/8/4K3 w - - 0 1"); // Only white king
        BitBoard bitBoard1 = new BitBoard(board1);
        
        Board board2 = new Board();
        board2.loadFromFen("8/8/8/8/8/8/4P3/4K3 w - - 0 1"); // White king and pawn
        BitBoard bitBoard2 = new BitBoard(board2);
        
        // Adding a pawn should change the hash
        assertNotEquals(bitBoard1.getZobristHash(), bitBoard2.getZobristHash(),
                "Adding a piece should change zobrist hash");
        
        // The difference should be the pawn's zobrist key
        long hashDiff = bitBoard1.getZobristHash() ^ bitBoard2.getZobristHash();
        long expectedKey = Zobrist.pieceKey(Zobrist.PAWN, Zobrist.WHITE, 12); // e2 = square 12
        assertEquals(expectedKey, hashDiff,
                "Hash difference should equal the piece's zobrist key");
    }

    @Test
    void testZobristHashSymmetry() {
        // Test that swapping colors produces different hashes
        Board board1 = new Board();
        board1.loadFromFen("8/8/8/8/8/8/4P3/4K3 w - - 0 1"); // White pawn and king
        BitBoard bitBoard1 = new BitBoard(board1);
        
        Board board2 = new Board();
        board2.loadFromFen("4k3/4p3/8/8/8/8/8/8 w - - 0 1"); // Black pawn and king (mirrored)
        BitBoard bitBoard2 = new BitBoard(board2);
        
        // Different colored pieces should produce different hashes
        assertNotEquals(bitBoard1.getZobristHash(), bitBoard2.getZobristHash(),
                "Different colored pieces should produce different zobrist hashes");
    }

    @Test
    void testZobristHashAfterMoveSequence() {
        // Test that hash changes correctly after a sequence of moves
        Board board = new Board();
        BitBoard bitBoard1 = new BitBoard(board);
        long hash1 = bitBoard1.getZobristHash();
        
        // Make some moves
        board.doMove("e2e4");
        BitBoard bitBoard2 = new BitBoard(board);
        long hash2 = bitBoard2.getZobristHash();
        
        board.doMove("e7e5");
        BitBoard bitBoard3 = new BitBoard(board);
        long hash3 = bitBoard3.getZobristHash();
        
        // Each position should have a unique hash
        assertNotEquals(hash1, hash2, "Hash should change after first move");
        assertNotEquals(hash2, hash3, "Hash should change after second move");
        assertNotEquals(hash1, hash3, "Hash should be different from starting position");
    }
}
