package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;

/**
 * High-performance bitboard representation of chess position.
 * Uses 64-bit integers for fast move generation and evaluation.
 */
public class BitBoard {
    // Bitboards for each piece type and color
    private long whitePawns, whiteKnights, whiteBishops, whiteRooks, whiteQueens, whiteKing;
    private long blackPawns, blackKnights, blackBishops, blackRooks, blackQueens, blackKing;
    
    // Occupancy bitboards
    private long whiteOccupancy;
    private long blackOccupancy;
    private long allOccupancy;
    
    // Game state
    private boolean whiteToMove;
    private int castlingRights; // 4 bits: WK, WQ, BK, BQ
    private int enPassantSquare; // -1 if none, otherwise 0-63
    private int halfMoveClock;
    private int fullMoveNumber;
    
    // Zobrist hash
    private long zobristHash;
    
    // Castling rights bit masks
    public static final int WHITE_KINGSIDE = 1;
    public static final int WHITE_QUEENSIDE = 2;
    public static final int BLACK_KINGSIDE = 4;
    public static final int BLACK_QUEENSIDE = 8;
    
    /**
     * Create BitBoard from chesslib Board.
     */
    public BitBoard(Board board) {
        initFromBoard(board);
    }
    
    /**
     * Default constructor for empty board.
     */
    public BitBoard() {
        whiteToMove = true;
        castlingRights = 0;
        enPassantSquare = -1;
        halfMoveClock = 0;
        fullMoveNumber = 1;
        zobristHash = 0L;
    }
    
    /**
     * Initialize from chesslib Board.
     */
    private void initFromBoard(Board board) {
        // Clear all bitboards
        whitePawns = whiteKnights = whiteBishops = whiteRooks = whiteQueens = whiteKing = 0L;
        blackPawns = blackKnights = blackBishops = blackRooks = blackQueens = blackKing = 0L;
        
        zobristHash = 0L;
        
        // Convert board to bitboards
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            
            Piece piece = board.getPiece(sq);
            if (piece == Piece.NONE) continue;
            
            int squareIndex = sq.ordinal();
            long bitboard = 1L << squareIndex;
            
            int pieceType = -1;
            int color = -1;
            
            switch (piece) {
                case WHITE_PAWN:
                    whitePawns |= bitboard;
                    pieceType = Zobrist.PAWN;
                    color = Zobrist.WHITE;
                    break;
                case WHITE_KNIGHT:
                    whiteKnights |= bitboard;
                    pieceType = Zobrist.KNIGHT;
                    color = Zobrist.WHITE;
                    break;
                case WHITE_BISHOP:
                    whiteBishops |= bitboard;
                    pieceType = Zobrist.BISHOP;
                    color = Zobrist.WHITE;
                    break;
                case WHITE_ROOK:
                    whiteRooks |= bitboard;
                    pieceType = Zobrist.ROOK;
                    color = Zobrist.WHITE;
                    break;
                case WHITE_QUEEN:
                    whiteQueens |= bitboard;
                    pieceType = Zobrist.QUEEN;
                    color = Zobrist.WHITE;
                    break;
                case WHITE_KING:
                    whiteKing |= bitboard;
                    pieceType = Zobrist.KING;
                    color = Zobrist.WHITE;
                    break;
                case BLACK_PAWN:
                    blackPawns |= bitboard;
                    pieceType = Zobrist.PAWN;
                    color = Zobrist.BLACK;
                    break;
                case BLACK_KNIGHT:
                    blackKnights |= bitboard;
                    pieceType = Zobrist.KNIGHT;
                    color = Zobrist.BLACK;
                    break;
                case BLACK_BISHOP:
                    blackBishops |= bitboard;
                    pieceType = Zobrist.BISHOP;
                    color = Zobrist.BLACK;
                    break;
                case BLACK_ROOK:
                    blackRooks |= bitboard;
                    pieceType = Zobrist.ROOK;
                    color = Zobrist.BLACK;
                    break;
                case BLACK_QUEEN:
                    blackQueens |= bitboard;
                    pieceType = Zobrist.QUEEN;
                    color = Zobrist.BLACK;
                    break;
                case BLACK_KING:
                    blackKing |= bitboard;
                    pieceType = Zobrist.KING;
                    color = Zobrist.BLACK;
                    break;
            }
            
            if (pieceType >= 0) {
                zobristHash ^= Zobrist.pieceKey(pieceType, color, squareIndex);
            }
        }
        
        // Update occupancy
        updateOccupancy();
        
        // Set game state
        whiteToMove = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE;
        
        // Parse castling rights from context (FEN)
        castlingRights = 0;
        String fen = board.getFen();
        String[] fenParts = fen.split(" ");
        if (fenParts.length > 2) {
            String castling = fenParts[2];
            if (castling.contains("K")) {
                castlingRights |= WHITE_KINGSIDE;
                zobristHash ^= Zobrist.castlingKey(0);
            }
            if (castling.contains("Q")) {
                castlingRights |= WHITE_QUEENSIDE;
                zobristHash ^= Zobrist.castlingKey(1);
            }
            if (castling.contains("k")) {
                castlingRights |= BLACK_KINGSIDE;
                zobristHash ^= Zobrist.castlingKey(2);
            }
            if (castling.contains("q")) {
                castlingRights |= BLACK_QUEENSIDE;
                zobristHash ^= Zobrist.castlingKey(3);
            }
        }
        
        // En passant
        Square epSquare = board.getEnPassant();
        if (epSquare != Square.NONE) {
            enPassantSquare = epSquare.ordinal();
            int file = enPassantSquare % 8;
            zobristHash ^= Zobrist.enPassantKey(file);
        } else {
            enPassantSquare = -1;
        }
        
        // Move counters
        halfMoveClock = board.getHalfMoveCounter();
        fullMoveNumber = board.getMoveCounter();
        
        // Side to move
        if (!whiteToMove) {
            zobristHash ^= Zobrist.blackToMoveKey();
        }
    }
    
    /**
     * Update occupancy bitboards.
     */
    private void updateOccupancy() {
        whiteOccupancy = whitePawns | whiteKnights | whiteBishops | whiteRooks | whiteQueens | whiteKing;
        blackOccupancy = blackPawns | blackKnights | blackBishops | blackRooks | blackQueens | blackKing;
        allOccupancy = whiteOccupancy | blackOccupancy;
    }
    
    // Getters
    public long getWhitePawns() { return whitePawns; }
    public long getWhiteKnights() { return whiteKnights; }
    public long getWhiteBishops() { return whiteBishops; }
    public long getWhiteRooks() { return whiteRooks; }
    public long getWhiteQueens() { return whiteQueens; }
    public long getWhiteKing() { return whiteKing; }
    
    public long getBlackPawns() { return blackPawns; }
    public long getBlackKnights() { return blackKnights; }
    public long getBlackBishops() { return blackBishops; }
    public long getBlackRooks() { return blackRooks; }
    public long getBlackQueens() { return blackQueens; }
    public long getBlackKing() { return blackKing; }
    
    public long getWhiteOccupancy() { return whiteOccupancy; }
    public long getBlackOccupancy() { return blackOccupancy; }
    public long getAllOccupancy() { return allOccupancy; }
    
    public boolean isWhiteToMove() { return whiteToMove; }
    public int getCastlingRights() { return castlingRights; }
    public int getEnPassantSquare() { return enPassantSquare; }
    public int getHalfMoveClock() { return halfMoveClock; }
    public int getFullMoveNumber() { return fullMoveNumber; }
    public long getZobristHash() { return zobristHash; }
    
    /**
     * Count total material (excluding kings).
     */
    public int getMaterialCount() {
        return Long.bitCount(whitePawns | blackPawns) +
               Long.bitCount(whiteKnights | blackKnights) * 3 +
               Long.bitCount(whiteBishops | blackBishops) * 3 +
               Long.bitCount(whiteRooks | blackRooks) * 5 +
               Long.bitCount(whiteQueens | blackQueens) * 9;
    }
    
    /**
     * Get piece at square (for evaluation).
     */
    public int getPieceAt(int square) {
        long mask = 1L << square;
        
        if ((whitePawns & mask) != 0) return Zobrist.PAWN | (Zobrist.WHITE << 3);
        if ((whiteKnights & mask) != 0) return Zobrist.KNIGHT | (Zobrist.WHITE << 3);
        if ((whiteBishops & mask) != 0) return Zobrist.BISHOP | (Zobrist.WHITE << 3);
        if ((whiteRooks & mask) != 0) return Zobrist.ROOK | (Zobrist.WHITE << 3);
        if ((whiteQueens & mask) != 0) return Zobrist.QUEEN | (Zobrist.WHITE << 3);
        if ((whiteKing & mask) != 0) return Zobrist.KING | (Zobrist.WHITE << 3);
        
        if ((blackPawns & mask) != 0) return Zobrist.PAWN | (Zobrist.BLACK << 3);
        if ((blackKnights & mask) != 0) return Zobrist.KNIGHT | (Zobrist.BLACK << 3);
        if ((blackBishops & mask) != 0) return Zobrist.BISHOP | (Zobrist.BLACK << 3);
        if ((blackRooks & mask) != 0) return Zobrist.ROOK | (Zobrist.BLACK << 3);
        if ((blackQueens & mask) != 0) return Zobrist.QUEEN | (Zobrist.BLACK << 3);
        if ((blackKing & mask) != 0) return Zobrist.KING | (Zobrist.BLACK << 3);
        
        return -1; // Empty square
    }
}
