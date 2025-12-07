package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import lombok.Getter;

/**
 * High-performance bitboard representation of chess position.
 * Uses 64-bit integers for fast move generation and evaluation.
 */
public class BoardStatus {
    // Getters
    // Bitboards for each piece type and color
    @Getter
    private long whitePawns;
    @Getter
    private long whiteKnights;
    @Getter
    private long whiteBishops;
    @Getter
    private long whiteRooks;
    @Getter
    private long whiteQueens;
    @Getter
    private long whiteKing;
    @Getter
    private long blackPawns;
    @Getter
    private long blackKnights;
    @Getter
    private long blackBishops;
    @Getter
    private long blackRooks;
    @Getter
    private long blackQueens;
    @Getter
    private long blackKing;

    // Game state
    @Getter
    private boolean whiteToMove;

    /**
     * Create BitBoard from chesslib Board.
     */
    public BoardStatus(Board board) {
        initFromBoard(board);
    }
    
    /**
     * Default constructor for empty board.
     */
    public BoardStatus() {
        whiteToMove = true;
    }
    
    /**
     * Initialize from chesslib Board.
     */
    private void initFromBoard(Board board) {
        // Clear all bitboards
        whitePawns = whiteKnights = whiteBishops = whiteRooks = whiteQueens = whiteKing = 0L;
        blackPawns = blackKnights = blackBishops = blackRooks = blackQueens = blackKing = 0L;

        // Convert board to bitboards
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            
            Piece piece = board.getPiece(sq);
            if (piece == Piece.NONE) continue;
            
            int squareIndex = sq.ordinal();
            long bitboard = 1L << squareIndex;

            
            switch (piece) {
                case WHITE_PAWN:
                    whitePawns |= bitboard;
                    break;
                case WHITE_KNIGHT:
                    whiteKnights |= bitboard;
                    break;
                case WHITE_BISHOP:
                    whiteBishops |= bitboard;
                    break;
                case WHITE_ROOK:
                    whiteRooks |= bitboard;
                    break;
                case WHITE_QUEEN:
                    whiteQueens |= bitboard;
                    break;
                case WHITE_KING:
                    whiteKing |= bitboard;
                    break;
                case BLACK_PAWN:
                    blackPawns |= bitboard;
                    break;
                case BLACK_KNIGHT:
                    blackKnights |= bitboard;
                    break;
                case BLACK_BISHOP:
                    blackBishops |= bitboard;
                    break;
                case BLACK_ROOK:
                    blackRooks |= bitboard;
                    break;
                case BLACK_QUEEN:
                    blackQueens |= bitboard;
                    break;
                case BLACK_KING:
                    blackKing |= bitboard;
                    break;
            }
        }

        // Set game state
        whiteToMove = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE;
    }
}
