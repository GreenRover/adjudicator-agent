package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.Zobrist;
import ch.adjudicator.agent.engine.ZobristHasher;
import ch.adjudicator.agent.engine.Evaluator;
import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public class Bitboard {

    private static final Square[] SQUARES = Square.values();
    private static final Piece[] PIECES = Piece.values();
    private static final Side[] PIECE_SIDES = new Side[PIECES.length];
    // Map Piece enum to Zobrist piece index (0=Pawn..5=King)
    private static final int[] ZOBRIST_PIECE_INDICES = new int[PIECES.length];
    
    // Maps for Incremental Evaluation
    private static final int[] PIECE_VALUES = new int[PIECES.length];
    private static final int[][] MG_TABLES = new int[PIECES.length][];
    private static final int[][] EG_TABLES = new int[PIECES.length][];

    static {
        for (Piece p : PIECES) {
            if (p == Piece.NONE) {
                PIECE_SIDES[p.ordinal()] = null;
            } else {
                PIECE_SIDES[p.ordinal()] = p.name().startsWith("WHITE") ? Side.WHITE : Side.BLACK;

                int type = switch (p.getPieceType()) {
                    case PAWN -> Zobrist.PAWN;
                    case KNIGHT -> Zobrist.KNIGHT;
                    case BISHOP -> Zobrist.BISHOP;
                    case ROOK -> Zobrist.ROOK;
                    case QUEEN -> Zobrist.QUEEN;
                    case KING -> Zobrist.KING;
                    default -> -1;
                };
                ZOBRIST_PIECE_INDICES[p.ordinal()] = type;
                
                // Initialize Eval Tables
                switch (p.getPieceType()) {
                    case PAWN -> {
                        PIECE_VALUES[p.ordinal()] = Evaluator.PAWN_VALUE;
                        MG_TABLES[p.ordinal()] = Evaluator.MG_PAWN_TABLE;
                        EG_TABLES[p.ordinal()] = Evaluator.EG_PAWN_TABLE;
                    }
                    case KNIGHT -> {
                        PIECE_VALUES[p.ordinal()] = Evaluator.KNIGHT_VALUE;
                        MG_TABLES[p.ordinal()] = Evaluator.MG_KNIGHT_TABLE;
                        EG_TABLES[p.ordinal()] = Evaluator.EG_KNIGHT_TABLE;
                    }
                    case BISHOP -> {
                        PIECE_VALUES[p.ordinal()] = Evaluator.BISHOP_VALUE;
                        MG_TABLES[p.ordinal()] = Evaluator.MG_BISHOP_TABLE;
                        EG_TABLES[p.ordinal()] = Evaluator.EG_BISHOP_TABLE;
                    }
                    case ROOK -> {
                        PIECE_VALUES[p.ordinal()] = Evaluator.ROOK_VALUE;
                        MG_TABLES[p.ordinal()] = Evaluator.MG_ROOK_TABLE;
                        EG_TABLES[p.ordinal()] = Evaluator.EG_ROOK_TABLE;
                    }
                    case QUEEN -> {
                        PIECE_VALUES[p.ordinal()] = Evaluator.QUEEN_VALUE;
                        MG_TABLES[p.ordinal()] = Evaluator.MG_QUEEN_TABLE;
                        EG_TABLES[p.ordinal()] = Evaluator.EG_QUEEN_TABLE;
                    }
                    case KING -> {
                        PIECE_VALUES[p.ordinal()] = 0;
                        MG_TABLES[p.ordinal()] = Evaluator.MG_KING_TABLE;
                        EG_TABLES[p.ordinal()] = Evaluator.EG_KING_TABLE;
                    }
                    default -> {}
                }
            }
        }
    }

    private final Piece[] mailbox;

    public long whitePieces;
    public long blackPieces;
    public long occupiedSquares;

    public long whitePawns, whiteKnights, whiteBishops, whiteRooks, whiteQueens, whiteKing;
    public long blackPawns, blackKnights, blackBishops, blackRooks, blackQueens, blackKing;

    private int whiteKingSq = -1;
    private int blackKingSq = -1;

    private Side sideToMove;
    private int castlingRights;
    private Square enPassantSquare;
    private int halfMoveClock;
    private int fullMoveNumber;
    private long zobristHash;
    private int mgPestoScore;
    private int egPestoScore;

    private static final int MAX_GAME_MOVES = 2048;

    private static class StateHistory {
        public int move;
        public Piece capturedPiece;
        public int castlingRights;
        public Square enPassantSquare;
        public int halfMoveClock;
        public long zobristHash;
    }

    private final StateHistory[] history = new StateHistory[MAX_GAME_MOVES];
    private int historyPly = 0;

    // Castling constants
    private static final int CASTLE_WK = 1;
    private static final int CASTLE_WQ = 2;
    private static final int CASTLE_BK = 4;
    private static final int CASTLE_BQ = 8;

    public Bitboard() {
        for (int i = 0; i < MAX_GAME_MOVES; i++) {
            history[i] = new StateHistory();
        }
        mailbox = new Piece[64];
        // Initialize with standard start position
        loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public void clear() {
        whitePawns = 0L; whiteKnights = 0L; whiteBishops = 0L; whiteRooks = 0L; whiteQueens = 0L; whiteKing = 0L;
        blackPawns = 0L; blackKnights = 0L; blackBishops = 0L; blackRooks = 0L; blackQueens = 0L; blackKing = 0L;
        Arrays.fill(mailbox, Piece.NONE);
        whitePieces = 0L;
        blackPieces = 0L;
        occupiedSquares = 0L;
        sideToMove = Side.WHITE;
        castlingRights = 0;
        enPassantSquare = Square.NONE;
        halfMoveClock = 0;
        fullMoveNumber = 1;
        historyPly = 0;
        zobristHash = 0L;
        whiteKingSq = -1;
        blackKingSq = -1;
    }

    // --- Core Bitboard Operations ---

    public void putPiece(Piece piece, Square sq) {
        if (piece == Piece.NONE) return;
        int sqIdx = sq.ordinal();
        long bit = 1L << sqIdx;

        switch (piece) {
            case WHITE_PAWN -> whitePawns |= bit;
            case WHITE_KNIGHT -> whiteKnights |= bit;
            case WHITE_BISHOP -> whiteBishops |= bit;
            case WHITE_ROOK -> whiteRooks |= bit;
            case WHITE_QUEEN -> whiteQueens |= bit;
            case WHITE_KING -> whiteKing |= bit;
            case BLACK_PAWN -> blackPawns |= bit;
            case BLACK_KNIGHT -> blackKnights |= bit;
            case BLACK_BISHOP -> blackBishops |= bit;
            case BLACK_ROOK -> blackRooks |= bit;
            case BLACK_QUEEN -> blackQueens |= bit;
            case BLACK_KING -> blackKing |= bit;
        }

        if (PIECE_SIDES[piece.ordinal()] == Side.WHITE) {
            whitePieces |= bit;
            if (piece == Piece.WHITE_KING) whiteKingSq = sqIdx;
        } else {
            blackPieces |= bit;
            if (piece == Piece.BLACK_KING) blackKingSq = sqIdx;
        }
        occupiedSquares |= bit;
        mailbox[sqIdx] = piece;
    }

    public void removePiece(Square sq) {
        int sqIdx = sq.ordinal();
        Piece p = mailbox[sqIdx];
        if (p == Piece.NONE) return;

        long bit = 1L << sqIdx;
        long mask = ~bit;

        switch (p) {
            case WHITE_PAWN -> whitePawns &= mask;
            case WHITE_KNIGHT -> whiteKnights &= mask;
            case WHITE_BISHOP -> whiteBishops &= mask;
            case WHITE_ROOK -> whiteRooks &= mask;
            case WHITE_QUEEN -> whiteQueens &= mask;
            case WHITE_KING -> whiteKing &= mask;
            case BLACK_PAWN -> blackPawns &= mask;
            case BLACK_KNIGHT -> blackKnights &= mask;
            case BLACK_BISHOP -> blackBishops &= mask;
            case BLACK_ROOK -> blackRooks &= mask;
            case BLACK_QUEEN -> blackQueens &= mask;
            case BLACK_KING -> blackKing &= mask;
        }

        if (PIECE_SIDES[p.ordinal()] == Side.WHITE) {
            whitePieces &= mask;
            if (p == Piece.WHITE_KING) whiteKingSq = -1;
        } else {
            blackPieces &= mask;
            if (p == Piece.BLACK_KING) blackKingSq = -1;
        }
        occupiedSquares &= mask;
        mailbox[sqIdx] = Piece.NONE;
    }

    public Piece getPieceAt(Square sq) {
        return mailbox[sq.ordinal()];
    }

    public void loadFromFen(String fen) {
        clear();
        String[] parts = fen.split(" ");
        String placement = parts[0];
        String activeColor = parts[1];
        String castling = parts[2];
        String enPassant = parts[3];

        int rank = 7;
        int file = 0;
        for (char c : placement.toCharArray()) {
            if (c == '/') {
                rank--;
                file = 0;
            } else if (Character.isDigit(c)) {
                file += Character.getNumericValue(c);
            } else {
                if (file < 8) {
                    Square sq = SQUARES[rank * 8 + file];
                    Piece p = getPieceFromChar(c);
                    putPiece(p, sq);
                    file++;
                }
            }
        }

        sideToMove = activeColor.equals("w") ? Side.WHITE : Side.BLACK;

        if (!castling.equals("-")) {
            if (castling.contains("K")) castlingRights |= CASTLE_WK;
            if (castling.contains("Q")) castlingRights |= CASTLE_WQ;
            if (castling.contains("k")) castlingRights |= CASTLE_BK;
            if (castling.contains("q")) castlingRights |= CASTLE_BQ;
        }

        if (!enPassant.equals("-")) {
            try {
                enPassantSquare = Square.valueOf(enPassant.toUpperCase());
            } catch (IllegalArgumentException e) {
                enPassantSquare = Square.NONE;
            }
        } else {
            enPassantSquare = Square.NONE;
        }

        if (parts.length > 4) {
            try {
                halfMoveClock = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                halfMoveClock = 0;
            }
        }

        if (parts.length > 5) {
            try {
                fullMoveNumber = Integer.parseInt(parts[5]);
            } catch (NumberFormatException e) {
                fullMoveNumber = 1;
            }
        }

        // Full Zobrist calculation for initial position
        zobristHash = ZobristHasher.getZobristKey(this);
        initPestoScores();
    }

    private void initPestoScores() {
        mgPestoScore = 0;
        egPestoScore = 0;

        // White
        mgPestoScore += calculateScoreFor(whitePawns, Evaluator.PAWN_VALUE, true, Evaluator.MG_PAWN_TABLE);
        egPestoScore += calculateScoreFor(whitePawns, Evaluator.PAWN_VALUE, true, Evaluator.EG_PAWN_TABLE);
        mgPestoScore += calculateScoreFor(whiteKnights, Evaluator.KNIGHT_VALUE, true, Evaluator.MG_KNIGHT_TABLE);
        egPestoScore += calculateScoreFor(whiteKnights, Evaluator.KNIGHT_VALUE, true, Evaluator.EG_KNIGHT_TABLE);
        mgPestoScore += calculateScoreFor(whiteBishops, Evaluator.BISHOP_VALUE, true, Evaluator.MG_BISHOP_TABLE);
        egPestoScore += calculateScoreFor(whiteBishops, Evaluator.BISHOP_VALUE, true, Evaluator.EG_BISHOP_TABLE);
        mgPestoScore += calculateScoreFor(whiteRooks, Evaluator.ROOK_VALUE, true, Evaluator.MG_ROOK_TABLE);
        egPestoScore += calculateScoreFor(whiteRooks, Evaluator.ROOK_VALUE, true, Evaluator.EG_ROOK_TABLE);
        mgPestoScore += calculateScoreFor(whiteQueens, Evaluator.QUEEN_VALUE, true, Evaluator.MG_QUEEN_TABLE);
        egPestoScore += calculateScoreFor(whiteQueens, Evaluator.QUEEN_VALUE, true, Evaluator.EG_QUEEN_TABLE);
        mgPestoScore += calculateScoreFor(whiteKing, 0, true, Evaluator.MG_KING_TABLE);
        egPestoScore += calculateScoreFor(whiteKing, 0, true, Evaluator.EG_KING_TABLE);

        // Black
        mgPestoScore -= calculateScoreFor(blackPawns, Evaluator.PAWN_VALUE, false, Evaluator.MG_PAWN_TABLE);
        egPestoScore -= calculateScoreFor(blackPawns, Evaluator.PAWN_VALUE, false, Evaluator.EG_PAWN_TABLE);
        mgPestoScore -= calculateScoreFor(blackKnights, Evaluator.KNIGHT_VALUE, false, Evaluator.MG_KNIGHT_TABLE);
        egPestoScore -= calculateScoreFor(blackKnights, Evaluator.KNIGHT_VALUE, false, Evaluator.EG_KNIGHT_TABLE);
        mgPestoScore -= calculateScoreFor(blackBishops, Evaluator.BISHOP_VALUE, false, Evaluator.MG_BISHOP_TABLE);
        egPestoScore -= calculateScoreFor(blackBishops, Evaluator.BISHOP_VALUE, false, Evaluator.EG_BISHOP_TABLE);
        mgPestoScore -= calculateScoreFor(blackRooks, Evaluator.ROOK_VALUE, false, Evaluator.MG_ROOK_TABLE);
        egPestoScore -= calculateScoreFor(blackRooks, Evaluator.ROOK_VALUE, false, Evaluator.EG_ROOK_TABLE);
        mgPestoScore -= calculateScoreFor(blackQueens, Evaluator.QUEEN_VALUE, false, Evaluator.MG_QUEEN_TABLE);
        egPestoScore -= calculateScoreFor(blackQueens, Evaluator.QUEEN_VALUE, false, Evaluator.EG_QUEEN_TABLE);
        mgPestoScore -= calculateScoreFor(blackKing, 0, false, Evaluator.MG_KING_TABLE);
        egPestoScore -= calculateScoreFor(blackKing, 0, false, Evaluator.EG_KING_TABLE);
    }

    private int calculateScoreFor(long bitboard, int value, boolean white, int[] table) {
        int score = 0;
        while (bitboard != 0) {
            int square = Long.numberOfTrailingZeros(bitboard);
            int tableSquare = white ? (square ^ 56) : square;
            score += value + table[tableSquare];
            bitboard &= bitboard - 1;
        }
        return score;
    }

    private Piece getPieceFromChar(char c) {
        return switch (c) {
            case 'P' -> Piece.WHITE_PAWN;
            case 'N' -> Piece.WHITE_KNIGHT;
            case 'B' -> Piece.WHITE_BISHOP;
            case 'R' -> Piece.WHITE_ROOK;
            case 'Q' -> Piece.WHITE_QUEEN;
            case 'K' -> Piece.WHITE_KING;
            case 'p' -> Piece.BLACK_PAWN;
            case 'n' -> Piece.BLACK_KNIGHT;
            case 'b' -> Piece.BLACK_BISHOP;
            case 'r' -> Piece.BLACK_ROOK;
            case 'q' -> Piece.BLACK_QUEEN;
            case 'k' -> Piece.BLACK_KING;
            default -> Piece.NONE;
        };
    }

    public String getFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece p = mailbox[rank * 8 + file];
                if (p == Piece.NONE) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(getPieceChar(p));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        sb.append(' ');
        sb.append(sideToMove == Side.WHITE ? "w" : "b");
        sb.append(' ');
        boolean anyCastle = false;
        if ((castlingRights & CASTLE_WK) != 0) { sb.append('K'); anyCastle = true; }
        if ((castlingRights & CASTLE_WQ) != 0) { sb.append('Q'); anyCastle = true; }
        if ((castlingRights & CASTLE_BK) != 0) { sb.append('k'); anyCastle = true; }
        if ((castlingRights & CASTLE_BQ) != 0) { sb.append('q'); anyCastle = true; }
        if (!anyCastle) sb.append('-');
        sb.append(' ');
        if (enPassantSquare == Square.NONE) {
            sb.append('-');
        } else {
            sb.append(enPassantSquare.toString().toLowerCase());
        }
        sb.append(' ');
        sb.append(halfMoveClock);
        sb.append(' ');
        sb.append(fullMoveNumber);
        return sb.toString();
    }

    private char getPieceChar(Piece p) {
        return switch (p) {
            case WHITE_PAWN -> 'P';
            case WHITE_KNIGHT -> 'N';
            case WHITE_BISHOP -> 'B';
            case WHITE_ROOK -> 'R';
            case WHITE_QUEEN -> 'Q';
            case WHITE_KING -> 'K';
            case BLACK_PAWN -> 'p';
            case BLACK_KNIGHT -> 'n';
            case BLACK_BISHOP -> 'b';
            case BLACK_ROOK -> 'r';
            case BLACK_QUEEN -> 'q';
            case BLACK_KING -> 'k';
            default -> throw new IllegalArgumentException("Unknown piece: " + p);
        };
    }

    public long getZobristKey() {
        return zobristHash;
    }

    public int getMgPestoScore() {
        return mgPestoScore;
    }

    public int getEgPestoScore() {
        return egPestoScore;
    }

    public Side getSideToMove() {
        return sideToMove;
    }

    // --- Helpers for Zobrist Updates ---

    private void xorPiece(Piece piece, int sq) {
        int color = (PIECE_SIDES[piece.ordinal()] == Side.WHITE) ? Zobrist.WHITE : Zobrist.BLACK;
        int type = ZOBRIST_PIECE_INDICES[piece.ordinal()];
        zobristHash ^= Zobrist.pieceKey(type, color, sq);
    }

    private void xorCastling(int rights) {
        if ((rights & CASTLE_WK) != 0) zobristHash ^= Zobrist.castlingKey(0);
        if ((rights & CASTLE_WQ) != 0) zobristHash ^= Zobrist.castlingKey(1);
        if ((rights & CASTLE_BK) != 0) zobristHash ^= Zobrist.castlingKey(2);
        if ((rights & CASTLE_BQ) != 0) zobristHash ^= Zobrist.castlingKey(3);
    }

    private void xorEnPassant(Square epSq) {
        if (epSq != Square.NONE) {
            zobristHash ^= Zobrist.enPassantKey(epSq.getFile().ordinal());
        }
    }

    // --- Move Execution ---

    @SuppressWarnings("ExtractMethodRecommender")
    public void makeMove(int move) {
        StateHistory state = history[historyPly];
        state.move = move;
        state.castlingRights = castlingRights;
        state.enPassantSquare = enPassantSquare;
        state.halfMoveClock = halfMoveClock;
        state.zobristHash = zobristHash;

        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;
        int promo = (move >> 12) & 7;

        Piece movingPiece = mailbox[from];
        Piece capturedPiece = mailbox[to];

        boolean isEP = false;
        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) &&
                enPassantSquare != Square.NONE && to == enPassantSquare.ordinal()) {
            isEP = true;
            capturedPiece = (sideToMove == Side.WHITE) ? Piece.BLACK_PAWN : Piece.WHITE_PAWN;
        }

        state.capturedPiece = capturedPiece;
        historyPly++;

        // ZOBRIST: Remove moving piece from 'from'
        xorPiece(movingPiece, from);

        // 1. Remove moving piece from source
        removePieceInternal(movingPiece, from);

        // 2. Remove captured piece
        if (capturedPiece != Piece.NONE) {
            if (isEP) {
                int capSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
                removePieceInternal(capturedPiece, capSq);
                // ZOBRIST: Remove captured piece (EP location)
                xorPiece(capturedPiece, capSq);
            } else {
                removePieceInternal(capturedPiece, to);
                // ZOBRIST: Remove captured piece (Target location)
                xorPiece(capturedPiece, to);
            }
        }

        // 3. Place piece (handle promo)
        Piece pieceToPlace = movingPiece;
        if (promo != 0) {
            if (sideToMove == Side.WHITE) {
                switch (promo) {
                    case 1 -> pieceToPlace = Piece.WHITE_KNIGHT;
                    case 2 -> pieceToPlace = Piece.WHITE_BISHOP;
                    case 3 -> pieceToPlace = Piece.WHITE_ROOK;
                    case 4 -> pieceToPlace = Piece.WHITE_QUEEN;
                }
            } else {
                switch (promo) {
                    case 1 -> pieceToPlace = Piece.BLACK_KNIGHT;
                    case 2 -> pieceToPlace = Piece.BLACK_BISHOP;
                    case 3 -> pieceToPlace = Piece.BLACK_ROOK;
                    case 4 -> pieceToPlace = Piece.BLACK_QUEEN;
                }
            }
        }
        putPieceInternal(pieceToPlace, to);
        // ZOBRIST: Add placed piece at 'to'
        xorPiece(pieceToPlace, to);

        // 4. Handle Castling (Rook)
        if ((movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            int rFrom;
            int rTo;
            Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
            if (to > from) { // Kingside
                rFrom = from + 3;
                rTo = from + 1;
                // ZOBRIST: Update Rook
            } else { // Queenside
                rFrom = from - 4;
                rTo = from - 1;
                // ZOBRIST: Update Rook
            }
            removePieceInternal(rook, rFrom);
            putPieceInternal(rook, rTo);
            xorPiece(rook, rFrom);
            xorPiece(rook, rTo);
        }

        // ZOBRIST: Update Castling Rights (Remove old)
        xorCastling(castlingRights);

        // Update State
        if (movingPiece == Piece.WHITE_KING) castlingRights &= ~(CASTLE_WK | CASTLE_WQ);
        else if (movingPiece == Piece.BLACK_KING) castlingRights &= ~(CASTLE_BK | CASTLE_BQ);

        if (movingPiece == Piece.WHITE_ROOK) {
            if (from == 7) castlingRights &= ~CASTLE_WK;
            if (from == 0) castlingRights &= ~CASTLE_WQ;
        } else if (movingPiece == Piece.BLACK_ROOK) {
            if (from == 63) castlingRights &= ~CASTLE_BK;
            if (from == 56) castlingRights &= ~CASTLE_BQ;
        }

        if (capturedPiece == Piece.WHITE_ROOK) {
            if (to == 7) castlingRights &= ~CASTLE_WK;
            if (to == 0) castlingRights &= ~CASTLE_WQ;
        } else if (capturedPiece == Piece.BLACK_ROOK) {
            if (to == 63) castlingRights &= ~CASTLE_BK;
            if (to == 56) castlingRights &= ~CASTLE_BQ;
        }

        // ZOBRIST: Update Castling Rights (Add new)
        xorCastling(castlingRights);

        // ZOBRIST: Update EP (Remove old)
        xorEnPassant(enPassantSquare);

        enPassantSquare = Square.NONE;
        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) && Math.abs(to - from) == 16) {
            int epIndex = (from + to) / 2;
            enPassantSquare = SQUARES[epIndex];
        }

        // ZOBRIST: Update EP (Add new)
        xorEnPassant(enPassantSquare);

        if (capturedPiece != Piece.NONE || movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        if (sideToMove == Side.BLACK) fullMoveNumber++;
        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;

        // ZOBRIST: Flip side to move
        zobristHash ^= Zobrist.blackToMoveKey();
    }

    private void putPieceInternal(Piece piece, int sqIdx) {
        if (piece == Piece.NONE) return;

        long bit = 1L << sqIdx;

        switch (piece) {
            case WHITE_PAWN -> whitePawns |= bit;
            case WHITE_KNIGHT -> whiteKnights |= bit;
            case WHITE_BISHOP -> whiteBishops |= bit;
            case WHITE_ROOK -> whiteRooks |= bit;
            case WHITE_QUEEN -> whiteQueens |= bit;
            case WHITE_KING -> whiteKing |= bit;
            case BLACK_PAWN -> blackPawns |= bit;
            case BLACK_KNIGHT -> blackKnights |= bit;
            case BLACK_BISHOP -> blackBishops |= bit;
            case BLACK_ROOK -> blackRooks |= bit;
            case BLACK_QUEEN -> blackQueens |= bit;
            case BLACK_KING -> blackKing |= bit;
        }

        if (PIECE_SIDES[piece.ordinal()] == Side.WHITE) {
            whitePieces |= bit;
            if (piece == Piece.WHITE_KING) whiteKingSq = sqIdx;
        } else {
            blackPieces |= bit;
            if (piece == Piece.BLACK_KING) blackKingSq = sqIdx;
        }
        occupiedSquares |= bit;
        mailbox[sqIdx] = piece;

        // Incremental Score Update
        int pIdx = piece.ordinal();
        int value = PIECE_VALUES[pIdx];
        int[] mgTable = MG_TABLES[pIdx];
        int[] egTable = EG_TABLES[pIdx];

        boolean isWhite = PIECE_SIDES[pIdx] == Side.WHITE;
        int tableSquare = isWhite ? (sqIdx ^ 56) : sqIdx;

        int scoreMg = value + mgTable[tableSquare];
        int scoreEg = value + egTable[tableSquare];

        if (isWhite) {
            mgPestoScore += scoreMg;
            egPestoScore += scoreEg;
        } else {
            mgPestoScore -= scoreMg;
            egPestoScore -= scoreEg;
        }
    }

    private void removePieceInternal(Piece piece, int sqIdx) {
        if (piece == Piece.NONE) return;

        long bit = 1L << sqIdx;
        long mask = ~bit;

        switch (piece) {
            case WHITE_PAWN -> whitePawns &= mask;
            case WHITE_KNIGHT -> whiteKnights &= mask;
            case WHITE_BISHOP -> whiteBishops &= mask;
            case WHITE_ROOK -> whiteRooks &= mask;
            case WHITE_QUEEN -> whiteQueens &= mask;
            case WHITE_KING -> whiteKing &= mask;
            case BLACK_PAWN -> blackPawns &= mask;
            case BLACK_KNIGHT -> blackKnights &= mask;
            case BLACK_BISHOP -> blackBishops &= mask;
            case BLACK_ROOK -> blackRooks &= mask;
            case BLACK_QUEEN -> blackQueens &= mask;
            case BLACK_KING -> blackKing &= mask;
        }

        if (PIECE_SIDES[piece.ordinal()] == Side.WHITE) {
            whitePieces &= mask;
        } else {
            blackPieces &= mask;
        }
        occupiedSquares &= mask;
        mailbox[sqIdx] = Piece.NONE;

        // Incremental Score Update
        int pIdx = piece.ordinal();
        int value = PIECE_VALUES[pIdx];
        int[] mgTable = MG_TABLES[pIdx];
        int[] egTable = EG_TABLES[pIdx];

        boolean isWhite = PIECE_SIDES[pIdx] == Side.WHITE;
        int tableSquare = isWhite ? (sqIdx ^ 56) : sqIdx;

        int scoreMg = value + mgTable[tableSquare];
        int scoreEg = value + egTable[tableSquare];

        if (isWhite) {
            mgPestoScore -= scoreMg;
            egPestoScore -= scoreEg;
        } else {
            mgPestoScore += scoreMg;
            egPestoScore += scoreEg;
        }
    }

    public void doMove(Move move) {
        int from = move.getFrom().ordinal();
        int to = move.getTo().ordinal();
        int promo = 0;
        if (move.getPromotion() != Piece.NONE) {
            Piece p = move.getPromotion();
            if (p == Piece.WHITE_KNIGHT || p == Piece.BLACK_KNIGHT) promo = 1;
            else if (p == Piece.WHITE_BISHOP || p == Piece.BLACK_BISHOP) promo = 2;
            else if (p == Piece.WHITE_ROOK || p == Piece.BLACK_ROOK) promo = 3;
            else if (p == Piece.WHITE_QUEEN || p == Piece.BLACK_QUEEN) promo = 4;
        }
        int encoded = encodeMove(from, to, promo);
        makeMove(encoded);
    }

    public void undoMove() {
        if (historyPly > 0) {
            int move = history[historyPly - 1].move;
            unmakeMove(move);
        }
    }

    public void unmakeMove(int move) {
        historyPly--;
        StateHistory state = history[historyPly];

        castlingRights = state.castlingRights;
        enPassantSquare = state.enPassantSquare;
        halfMoveClock = state.halfMoveClock;
        zobristHash = state.zobristHash;
        Piece capturedPiece = state.capturedPiece;

        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;
        if (sideToMove == Side.BLACK) fullMoveNumber--;

        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;
        int promo = (move >> 12) & 7;

        Piece movedPiece = mailbox[to];
        if (promo != 0) {
            movedPiece = (sideToMove == Side.WHITE) ? Piece.WHITE_PAWN : Piece.BLACK_PAWN;
        }

        removePieceInternal(mailbox[to], to);
        putPieceInternal(movedPiece, from);

        if (capturedPiece != Piece.NONE) {
            boolean isEP = (movedPiece == Piece.WHITE_PAWN || movedPiece == Piece.BLACK_PAWN) &&
                    state.enPassantSquare != Square.NONE &&
                    to == state.enPassantSquare.ordinal();

            if (isEP) {
                int capSq = (sideToMove == Side.WHITE) ? to - 8 : to + 8;
                putPieceInternal(capturedPiece, capSq);
            } else {
                putPieceInternal(capturedPiece, to);
            }
        }

        if ((movedPiece == Piece.WHITE_KING || movedPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            int rFrom;
            int rTo;
            Piece rook = (sideToMove == Side.WHITE) ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
            if (to > from) {
                rFrom = from + 3;
                rTo = from + 1;
            } else {
                rFrom = from - 4;
                rTo = from - 1;
            }
            removePieceInternal(rook, rTo);
            putPieceInternal(rook, rFrom);
        }
    }

    public List<Move> legalMoves() {
        int[] moves = new int[256];
        int count = generateLegalMoves(moves);
        List<Move> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int m = moves[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;
            int promo = (m >> 12) & 7;
            Piece promoPiece = Piece.NONE;
            if (promo != 0) {
                if (sideToMove == Side.WHITE) {
                    switch (promo) {
                        case 1 -> promoPiece = Piece.WHITE_KNIGHT;
                        case 2 -> promoPiece = Piece.WHITE_BISHOP;
                        case 3 -> promoPiece = Piece.WHITE_ROOK;
                        case 4 -> promoPiece = Piece.WHITE_QUEEN;
                    }
                } else {
                    switch (promo) {
                        case 1 -> promoPiece = Piece.BLACK_KNIGHT;
                        case 2 -> promoPiece = Piece.BLACK_BISHOP;
                        case 3 -> promoPiece = Piece.BLACK_ROOK;
                        case 4 -> promoPiece = Piece.BLACK_QUEEN;
                    }
                }
            }
            list.add(new Move(SQUARES[from], SQUARES[to], promoPiece));
        }
        return list;
    }

    public int generateLegalMoves(int[] moves) {
        int[] pseudo = new int[256];
        int count = generatePseudoLegalMoves(pseudo);
        int legalCount = 0;

        int kingSq = (sideToMove == Side.WHITE) ? whiteKingSq : blackKingSq;
        Side us = sideToMove;
        Side enemy = (us == Side.WHITE) ? Side.BLACK : Side.WHITE;

        for (int i = 0; i < count; i++) {
            int m = pseudo[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;

            // Castling Path Check
            if (kingSq != -1 && from == kingSq && Math.abs(to - from) == 2) {
                if (isSquareAttacked(from, enemy)) continue;
                int mid = (from + to) / 2;
                if (isSquareAttacked(mid, enemy)) continue;
            }

            if (isLegalVirtual(m, kingSq)) {
                moves[legalCount++] = m;
            }
        }
        return legalCount;
    }

    private boolean isLegalVirtual(int move, int kingSq) {
        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;

        Piece movingPiece = mailbox[from];
        Side us = PIECE_SIDES[movingPiece.ordinal()];

        long fromBit = 1L << from;
        long toBit = 1L << to;
        long occupied = occupiedSquares;
        long ignoreMask = -1L;

        int currentKingSq = kingSq;
        if (movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) {
            currentKingSq = to;
        }

        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) &&
                enPassantSquare != Square.NONE && to == enPassantSquare.ordinal()) {

            int capSq = (us == Side.WHITE) ? to - 8 : to + 8;
            long capBit = 1L << capSq;
            occupied = (occupied & ~fromBit & ~capBit) | toBit;
            ignoreMask = ~capBit;

        } else {
            occupied = (occupied & ~fromBit) | toBit;
            if (mailbox[to] != Piece.NONE) {
                ignoreMask = ~toBit;
            }
        }

        if ((movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) && Math.abs(to - from) == 2) {
            int rFrom, rTo;
            if (to > from) { rFrom = from + 3; rTo = from + 1; }
            else { rFrom = from - 4; rTo = from - 1; }

            occupied &= ~(1L << rFrom);
            occupied |= (1L << rTo);
        }

        return !isSquareAttackedVirtual(currentKingSq, us == Side.WHITE ? Side.BLACK : Side.WHITE, occupied, ignoreMask);
    }

    private boolean isSquareAttackedVirtual(int sq, Side attackerSide, long occupied, long ignoreMask) {
        if (attackerSide == Side.WHITE) {
            if ((AttackLookups.PAWN_ATTACKS[Side.BLACK.ordinal()][sq] & whitePawns & ignoreMask) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & whiteKnights & ignoreMask) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & whiteKing & ignoreMask) != 0) return true;

            long bishopsQueens = (whiteBishops | whiteQueens) & ignoreMask;
            if (bishopsQueens != 0) {
                if ((AttackLookups.getBishopAttacks(sq, occupied) & bishopsQueens) != 0) return true;
            }

            long rooksQueens = (whiteRooks | whiteQueens) & ignoreMask;
            if (rooksQueens != 0) {
                return (AttackLookups.getRookAttacks(sq, occupied) & rooksQueens) != 0;
            }
        } else {
            if ((AttackLookups.PAWN_ATTACKS[Side.WHITE.ordinal()][sq] & blackPawns & ignoreMask) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & blackKnights & ignoreMask) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & blackKing & ignoreMask) != 0) return true;

            long bishopsQueens = (blackBishops | blackQueens) & ignoreMask;
            if (bishopsQueens != 0) {
                if ((AttackLookups.getBishopAttacks(sq, occupied) & bishopsQueens) != 0) return true;
            }

            long rooksQueens = (blackRooks | blackQueens) & ignoreMask;
            if (rooksQueens != 0) {
                return (AttackLookups.getRookAttacks(sq, occupied) & rooksQueens) != 0;
            }
        }
        return false;
    }

    public void doNullMove() {
        StateHistory state = history[historyPly];
        state.move = 0;
        state.capturedPiece = Piece.NONE;
        state.castlingRights = castlingRights;
        state.enPassantSquare = enPassantSquare;
        state.halfMoveClock = halfMoveClock;
        state.zobristHash = zobristHash;

        historyPly++;

        // ZOBRIST: Update EP (Remove old)
        xorEnPassant(enPassantSquare);
        if (enPassantSquare != Square.NONE) enPassantSquare = Square.NONE;

        // ZOBRIST: No new EP

        // Side change
        sideToMove = (sideToMove == Side.WHITE) ? Side.BLACK : Side.WHITE;
        if (sideToMove == Side.BLACK) fullMoveNumber++; // Increment if we just finished White's turn (now Black's)? No, standard is incr on Black move.
        // If White passes, side becomes Black. We treat it as White having moved.

        halfMoveClock++;

        // ZOBRIST: Flip side
        zobristHash ^= Zobrist.blackToMoveKey();
    }

    public boolean isMated() {
        if (!isKingAttacked()) return false;
        int[] moves = new int[256];
        int count = generateLegalMoves(moves);
        return count == 0;
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean isSquareAttacked(int sq, Side attackerSide) {
        long occ = occupiedSquares;
        if (attackerSide == Side.WHITE) {
            if ((AttackLookups.PAWN_ATTACKS[Side.BLACK.ordinal()][sq] & whitePawns) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & whiteKnights) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & whiteKing) != 0) return true;
            if ((AttackLookups.getBishopAttacks(sq, occ) & (whiteBishops | whiteQueens)) != 0) return true;
            if ((AttackLookups.getRookAttacks(sq, occ) & (whiteRooks | whiteQueens)) != 0) return true;
        } else {
            if ((AttackLookups.PAWN_ATTACKS[Side.WHITE.ordinal()][sq] & blackPawns) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & blackKnights) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & blackKing) != 0) return true;
            if ((AttackLookups.getBishopAttacks(sq, occ) & (blackBishops | blackQueens)) != 0) return true;
            if ((AttackLookups.getRookAttacks(sq, occ) & (blackRooks | blackQueens)) != 0) return true;
        }
        return false;
    }

    public boolean isKingAttacked() {
        int kingSq = (sideToMove == Side.WHITE) ? whiteKingSq : blackKingSq;
        if (kingSq == -1) return false;
        return isSquareAttacked(kingSq, sideToMove == Side.WHITE ? Side.BLACK : Side.WHITE);
    }

    public Piece getPiece(Square square) {
        return mailbox[square.ordinal()];
    }

    public Square getEnPassant() {
        return enPassantSquare;
    }

    public CastleRight getCastleRight(Side side) {
        boolean k;
        boolean q;
        if (side == Side.WHITE) {
            k = (castlingRights & CASTLE_WK) != 0;
            q = (castlingRights & CASTLE_WQ) != 0;
        } else {
            k = (castlingRights & CASTLE_BK) != 0;
            q = (castlingRights & CASTLE_BQ) != 0;
        }
        if (k && q) return CastleRight.KING_AND_QUEEN_SIDE;
        if (k) return CastleRight.KING_SIDE;
        if (q) return CastleRight.QUEEN_SIDE;
        return CastleRight.NONE;
    }

    public long getBitboard(Side side) {
        if (side == Side.WHITE) return whitePieces;
        if (side == Side.BLACK) return blackPieces;
        return 0L;
    }

    public long getBitboard(Piece piece) {
        return switch (piece) {
            case WHITE_PAWN -> whitePawns;
            case WHITE_KNIGHT -> whiteKnights;
            case WHITE_BISHOP -> whiteBishops;
            case WHITE_ROOK -> whiteRooks;
            case WHITE_QUEEN -> whiteQueens;
            case WHITE_KING -> whiteKing;
            case BLACK_PAWN -> blackPawns;
            case BLACK_KNIGHT -> blackKnights;
            case BLACK_BISHOP -> blackBishops;
            case BLACK_ROOK -> blackRooks;
            case BLACK_QUEEN -> blackQueens;
            case BLACK_KING -> blackKing;
            default -> 0L;
        };
    }

    public int generatePseudoLegalMoves(int[] moveList) {
        int index = 0;
        long friendly, enemy;
        long myPawns, myKnights, myBishops, myRooks, myQueens, myKing;
        
        int promoRank, startRank;
        boolean isWhite = (sideToMove == Side.WHITE);

        if (isWhite) {
            friendly = whitePieces;
            enemy = blackPieces & ~blackKing;
            myPawns = whitePawns;
            myKnights = whiteKnights;
            myBishops = whiteBishops;
            myRooks = whiteRooks;
            myQueens = whiteQueens;
            myKing = whiteKing;
            
            promoRank = 7;
            startRank = 1;
        } else {
            friendly = blackPieces;
            enemy = whitePieces & ~whiteKing;
            myPawns = blackPawns;
            myKnights = blackKnights;
            myBishops = blackBishops;
            myRooks = blackRooks;
            myQueens = blackQueens;
            myKing = blackKing;
            
            promoRank = 0;
            startRank = 6;
        }

        long occupied = occupiedSquares;

        // --- Pawns ---
        long p = myPawns;
        while (p != 0) {
            int sq = Long.numberOfTrailingZeros(p);
            p &= p - 1;

            int rank = sq / 8;
            int file = sq % 8;

            int nextRank = isWhite ? rank + 1 : rank - 1;
            int forwardSq = nextRank * 8 + file;
            if (((1L << forwardSq) & occupied) == 0) {
                if (nextRank == promoRank) {
                    addPromoMoves(moveList, index, sq, forwardSq);
                    index += 4;
                } else {
                    moveList[index++] = encodeMove(sq, forwardSq, 0);
                    if (rank == startRank) {
                        int doubleRank = isWhite ? rank + 2 : rank - 2;
                        int doubleSq = doubleRank * 8 + file;
                        if (((1L << doubleSq) & occupied) == 0) {
                            moveList[index++] = encodeMove(sq, doubleSq, 0);
                        }
                    }
                }
            }

            for (int dFile = -1; dFile <= 1; dFile += 2) {
                if (file + dFile >= 0 && file + dFile < 8) {
                    int captureSq = nextRank * 8 + (file + dFile);
                    long captureBit = 1L << captureSq;
                    if ((captureBit & enemy) != 0) {
                        if (nextRank == promoRank) {
                            addPromoMoves(moveList, index, sq, captureSq);
                            index += 4;
                        } else {
                            moveList[index++] = encodeMove(sq, captureSq, 0);
                        }
                    } else if (captureSq == enPassantSquare.ordinal()) {
                        moveList[index++] = encodeMove(sq, captureSq, 0);
                    }
                }
            }
        }

        // --- Knights ---
        long n = myKnights;
        while (n != 0) {
            int sq = Long.numberOfTrailingZeros(n);
            n &= n - 1;
            long attacks = AttackLookups.KNIGHT_ATTACKS[sq] & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Bishops ---
        long b = myBishops;
        while (b != 0) {
            int sq = Long.numberOfTrailingZeros(b);
            b &= b - 1;
            long attacks = AttackLookups.getBishopAttacks(sq, occupied) & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Rooks ---
        long r = myRooks;
        while (r != 0) {
            int sq = Long.numberOfTrailingZeros(r);
            r &= r - 1;
            long attacks = AttackLookups.getRookAttacks(sq, occupied) & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Queens ---
        long q = myQueens;
        while (q != 0) {
            int sq = Long.numberOfTrailingZeros(q);
            q &= q - 1;
            long attacks = AttackLookups.getQueenAttacks(sq, occupied) & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- King ---
        long k = myKing;
        while (k != 0) {
            int sq = Long.numberOfTrailingZeros(k);
            k &= k - 1;
            long attacks = AttackLookups.KING_ATTACKS[sq] & ~friendly;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                moveList[index++] = encodeMove(sq, to, 0);
            }
        }

        // --- Castling ---
        if (isWhite) {
            if ((castlingRights & CASTLE_WK) != 0) {
                if ((occupied & ((1L << 5) | (1L << 6))) == 0) {
                    if (!isSquareAttacked(4, Side.BLACK) && !isSquareAttacked(5, Side.BLACK) && !isSquareAttacked(6, Side.BLACK)) {
                        moveList[index++] = encodeMove(4, 6, 0);
                    }
                }
            }
            if ((castlingRights & CASTLE_WQ) != 0) {
                if ((occupied & ((1L << 1) | (1L << 2) | (1L << 3))) == 0) {
                    if (!isSquareAttacked(4, Side.BLACK) && !isSquareAttacked(3, Side.BLACK) && !isSquareAttacked(2, Side.BLACK)) {
                        moveList[index++] = encodeMove(4, 2, 0);
                    }
                }
            }
        } else {
            if ((castlingRights & CASTLE_BK) != 0) {
                if ((occupied & ((1L << 61) | (1L << 62))) == 0) {
                    if (!isSquareAttacked(60, Side.WHITE) && !isSquareAttacked(61, Side.WHITE) && !isSquareAttacked(62, Side.WHITE)) {
                        moveList[index++] = encodeMove(60, 62, 0);
                    }
                }
            }
            if ((castlingRights & CASTLE_BQ) != 0) {
                if ((occupied & ((1L << 57) | (1L << 58) | (1L << 59))) == 0) {
                    if (!isSquareAttacked(60, Side.WHITE) && !isSquareAttacked(59, Side.WHITE) && !isSquareAttacked(58, Side.WHITE)) {
                        moveList[index++] = encodeMove(60, 58, 0);
                    }
                }
            }
        }

        return index;
    }

    public int generateLoudMoves(int[] moveList) {
        int index = 0;
        long friendly, enemy;
        long myPawns, myKnights, myBishops, myRooks, myQueens, myKing;
        
        int promoRank, startRank;
        boolean isWhite = (sideToMove == Side.WHITE);
        int kingSq = isWhite ? whiteKingSq : blackKingSq;

        if (isWhite) {
            friendly = whitePieces;
            enemy = blackPieces & ~blackKing;
            myPawns = whitePawns;
            myKnights = whiteKnights;
            myBishops = whiteBishops;
            myRooks = whiteRooks;
            myQueens = whiteQueens;
            myKing = whiteKing;
            
            promoRank = 7;
            startRank = 1;
        } else {
            friendly = blackPieces;
            enemy = whitePieces & ~whiteKing;
            myPawns = blackPawns;
            myKnights = blackKnights;
            myBishops = blackBishops;
            myRooks = blackRooks;
            myQueens = blackQueens;
            myKing = blackKing;
            
            promoRank = 0;
            startRank = 6;
        }

        long occupied = occupiedSquares;

        // --- Pawns ---
        long p = myPawns;
        while (p != 0) {
            int sq = Long.numberOfTrailingZeros(p);
            p &= p - 1;

            int rank = sq / 8;
            int file = sq % 8;

            int nextRank = isWhite ? rank + 1 : rank - 1;
            int forwardSq = nextRank * 8 + file;
            
            // Check for Promotions (Capture and Non-Capture)
            if (nextRank == promoRank) {
                // Forward push promotion
                if (((1L << forwardSq) & occupied) == 0) {
                     // Add promo moves
                     addPromoMoves(moveList, index, sq, forwardSq);
                     index += 4;
                }
                // Capture promotion
                for (int dFile = -1; dFile <= 1; dFile += 2) {
                    if (file + dFile >= 0 && file + dFile < 8) {
                        int captureSq = nextRank * 8 + (file + dFile);
                        long captureBit = 1L << captureSq;
                        if ((captureBit & enemy) != 0) {
                             addPromoMoves(moveList, index, sq, captureSq);
                             index += 4;
                        }
                    }
                }
            } else {
                // Normal Captures (Non-Promo)
                for (int dFile = -1; dFile <= 1; dFile += 2) {
                    if (file + dFile >= 0 && file + dFile < 8) {
                        int captureSq = nextRank * 8 + (file + dFile);
                        long captureBit = 1L << captureSq;
                        if ((captureBit & enemy) != 0) {
                            int move = encodeMove(sq, captureSq, 0);
                            moveList[index++] = move;
                        } else if (captureSq == enPassantSquare.ordinal()) {
                            int move = encodeMove(sq, captureSq, 0);
                            moveList[index++] = move;
                        }
                    }
                }
            }
        }

        // --- Knights ---
        long n = myKnights;
        while (n != 0) {
            int sq = Long.numberOfTrailingZeros(n);
            n &= n - 1;
            long attacks = AttackLookups.KNIGHT_ATTACKS[sq] & enemy;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                int move = encodeMove(sq, to, 0);
                moveList[index++] = move;
            }
        }

        // --- Bishops ---
        long b = myBishops;
        while (b != 0) {
            int sq = Long.numberOfTrailingZeros(b);
            b &= b - 1;
            long attacks = AttackLookups.getBishopAttacks(sq, occupied) & enemy;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                int move = encodeMove(sq, to, 0);
                moveList[index++] = move;
            }
        }

        // --- Rooks ---
        long r = myRooks;
        while (r != 0) {
            int sq = Long.numberOfTrailingZeros(r);
            r &= r - 1;
            long attacks = AttackLookups.getRookAttacks(sq, occupied) & enemy;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                int move = encodeMove(sq, to, 0);
                moveList[index++] = move;
            }
        }

        // --- Queens ---
        long q = myQueens;
        while (q != 0) {
            int sq = Long.numberOfTrailingZeros(q);
            q &= q - 1;
            long attacks = AttackLookups.getQueenAttacks(sq, occupied) & enemy;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                int move = encodeMove(sq, to, 0);
                moveList[index++] = move;
            }
        }

        // --- King ---
        long k = myKing;
        while (k != 0) {
            int sq = Long.numberOfTrailingZeros(k);
            k &= k - 1;
            long attacks = AttackLookups.KING_ATTACKS[sq] & enemy;
            while (attacks != 0) {
                int to = Long.numberOfTrailingZeros(attacks);
                attacks &= attacks - 1;
                int move = encodeMove(sq, to, 0);
                moveList[index++] = move;
            }
        }

        return index;
    }

    private void addPromoMoves(int[] moveList, int index, int from, int to) {
        moveList[index] = encodeMove(from, to, 1);
        moveList[index+1] = encodeMove(from, to, 2);
        moveList[index+2] = encodeMove(from, to, 3);
        moveList[index+3] = encodeMove(from, to, 4);
    }

    public static int encodeMove(int from, int to, int promo) {
        return from | (to << 6) | (promo << 12);
    }

    public static int getFrom(int move) {
        return move & 0x3F;
    }

    public static int getTo(int move) {
        return (move >> 6) & 0x3F;
    }

    public static int getPromo(int move) {
        return (move >> 12) & 7;
    }
}