package ch.adjudicator.agent.engine.board;

import ch.adjudicator.agent.engine.Zobrist;
import ch.adjudicator.agent.engine.ZobristHasher;
import ch.adjudicator.agent.engine.eval.EvaluationConstants;
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

    static final Square[] SQUARES = Square.values();
    static final Piece[] PIECES = Piece.values();
    static final Side[] PIECE_SIDES = new Side[PIECES.length];
    // Map Piece enum to Zobrist piece index (0=Pawn..5=King)
    static final int[] ZOBRIST_PIECE_INDICES = new int[PIECES.length];
    
    // Maps for Incremental Evaluation
    static final int[] PIECE_VALUES = new int[PIECES.length];
    static final int[][] MG_TABLES = new int[PIECES.length][];
    static final int[][] EG_TABLES = new int[PIECES.length][];

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
                        PIECE_VALUES[p.ordinal()] = EvaluationConstants.PAWN_VALUE;
                        MG_TABLES[p.ordinal()] = EvaluationConstants.MG_PAWN_TABLE;
                        EG_TABLES[p.ordinal()] = EvaluationConstants.EG_PAWN_TABLE;
                    }
                    case KNIGHT -> {
                        PIECE_VALUES[p.ordinal()] = EvaluationConstants.KNIGHT_VALUE;
                        MG_TABLES[p.ordinal()] = EvaluationConstants.MG_KNIGHT_TABLE;
                        EG_TABLES[p.ordinal()] = EvaluationConstants.EG_KNIGHT_TABLE;
                    }
                    case BISHOP -> {
                        PIECE_VALUES[p.ordinal()] = EvaluationConstants.BISHOP_VALUE;
                        MG_TABLES[p.ordinal()] = EvaluationConstants.MG_BISHOP_TABLE;
                        EG_TABLES[p.ordinal()] = EvaluationConstants.EG_BISHOP_TABLE;
                    }
                    case ROOK -> {
                        PIECE_VALUES[p.ordinal()] = EvaluationConstants.ROOK_VALUE;
                        MG_TABLES[p.ordinal()] = EvaluationConstants.MG_ROOK_TABLE;
                        EG_TABLES[p.ordinal()] = EvaluationConstants.EG_ROOK_TABLE;
                    }
                    case QUEEN -> {
                        PIECE_VALUES[p.ordinal()] = EvaluationConstants.QUEEN_VALUE;
                        MG_TABLES[p.ordinal()] = EvaluationConstants.MG_QUEEN_TABLE;
                        EG_TABLES[p.ordinal()] = EvaluationConstants.EG_QUEEN_TABLE;
                    }
                    case KING -> {
                        PIECE_VALUES[p.ordinal()] = 0;
                        MG_TABLES[p.ordinal()] = EvaluationConstants.MG_KING_TABLE;
                        EG_TABLES[p.ordinal()] = EvaluationConstants.EG_KING_TABLE;
                    }
                    default -> {}
                }
            }
        }
    }

    final Piece[] mailbox;

    long whitePieces;
    long blackPieces;
    long occupiedSquares;

    long whitePawns, whiteKnights, whiteBishops, whiteRooks, whiteQueens, whiteKing;
    long blackPawns, blackKnights, blackBishops, blackRooks, blackQueens, blackKing;

    int whiteKingSq = -1;
    int blackKingSq = -1;

    Side sideToMove;
    int castlingRights;
    Square enPassantSquare;
    int halfMoveClock;
    int fullMoveNumber;
    long zobristHash;
    int mgPestoScore;
    int egPestoScore;

    static final int MAX_GAME_MOVES = 2048;

    static class StateHistory {
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
    static final int CASTLE_WK = 1;
    static final int CASTLE_WQ = 2;
    static final int CASTLE_BK = 4;
    static final int CASTLE_BQ = 8;

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
        initPestoScores();
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
        FenParser.load(this, fen);
    }

    void initPestoScores() {
        mgPestoScore = 0;
        egPestoScore = 0;

        // White
        mgPestoScore += calculateScoreFor(whitePawns, EvaluationConstants.PAWN_VALUE, true, EvaluationConstants.MG_PAWN_TABLE);
        egPestoScore += calculateScoreFor(whitePawns, EvaluationConstants.PAWN_VALUE, true, EvaluationConstants.EG_PAWN_TABLE);
        mgPestoScore += calculateScoreFor(whiteKnights, EvaluationConstants.KNIGHT_VALUE, true, EvaluationConstants.MG_KNIGHT_TABLE);
        egPestoScore += calculateScoreFor(whiteKnights, EvaluationConstants.KNIGHT_VALUE, true, EvaluationConstants.EG_KNIGHT_TABLE);
        mgPestoScore += calculateScoreFor(whiteBishops, EvaluationConstants.BISHOP_VALUE, true, EvaluationConstants.MG_BISHOP_TABLE);
        egPestoScore += calculateScoreFor(whiteBishops, EvaluationConstants.BISHOP_VALUE, true, EvaluationConstants.EG_BISHOP_TABLE);
        mgPestoScore += calculateScoreFor(whiteRooks, EvaluationConstants.ROOK_VALUE, true, EvaluationConstants.MG_ROOK_TABLE);
        egPestoScore += calculateScoreFor(whiteRooks, EvaluationConstants.ROOK_VALUE, true, EvaluationConstants.EG_ROOK_TABLE);
        mgPestoScore += calculateScoreFor(whiteQueens, EvaluationConstants.QUEEN_VALUE, true, EvaluationConstants.MG_QUEEN_TABLE);
        egPestoScore += calculateScoreFor(whiteQueens, EvaluationConstants.QUEEN_VALUE, true, EvaluationConstants.EG_QUEEN_TABLE);
        mgPestoScore += calculateScoreFor(whiteKing, 0, true, EvaluationConstants.MG_KING_TABLE);
        egPestoScore += calculateScoreFor(whiteKing, 0, true, EvaluationConstants.EG_KING_TABLE);

        // Black
        mgPestoScore -= calculateScoreFor(blackPawns, EvaluationConstants.PAWN_VALUE, false, EvaluationConstants.MG_PAWN_TABLE);
        egPestoScore -= calculateScoreFor(blackPawns, EvaluationConstants.PAWN_VALUE, false, EvaluationConstants.EG_PAWN_TABLE);
        mgPestoScore -= calculateScoreFor(blackKnights, EvaluationConstants.KNIGHT_VALUE, false, EvaluationConstants.MG_KNIGHT_TABLE);
        egPestoScore -= calculateScoreFor(blackKnights, EvaluationConstants.KNIGHT_VALUE, false, EvaluationConstants.EG_KNIGHT_TABLE);
        mgPestoScore -= calculateScoreFor(blackBishops, EvaluationConstants.BISHOP_VALUE, false, EvaluationConstants.MG_BISHOP_TABLE);
        egPestoScore -= calculateScoreFor(blackBishops, EvaluationConstants.BISHOP_VALUE, false, EvaluationConstants.EG_BISHOP_TABLE);
        mgPestoScore -= calculateScoreFor(blackRooks, EvaluationConstants.ROOK_VALUE, false, EvaluationConstants.MG_ROOK_TABLE);
        egPestoScore -= calculateScoreFor(blackRooks, EvaluationConstants.ROOK_VALUE, false, EvaluationConstants.EG_ROOK_TABLE);
        mgPestoScore -= calculateScoreFor(blackQueens, EvaluationConstants.QUEEN_VALUE, false, EvaluationConstants.MG_QUEEN_TABLE);
        egPestoScore -= calculateScoreFor(blackQueens, EvaluationConstants.QUEEN_VALUE, false, EvaluationConstants.EG_QUEEN_TABLE);
        mgPestoScore -= calculateScoreFor(blackKing, 0, false, EvaluationConstants.MG_KING_TABLE);
        egPestoScore -= calculateScoreFor(blackKing, 0, false, EvaluationConstants.EG_KING_TABLE);
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

    private void removePieceInternal(Piece piece, int sqIdx) {
        if (piece == Piece.NONE) return;

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
        return MoveGenerator.generateLegalMoves(this, moves);
    }

    private boolean isLegalVirtual(int move, int kingSq) {
        return MoveGenerator.isLegalVirtual(this, move, kingSq);
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
        return isSquareAttacked(sq, attackerSide, occupiedSquares, -1L);
    }

    public boolean isSquareAttacked(int sq, Side attackerSide, long occupied, long attackersMask) {
        long pawns, knights, king, bishopsQueens, rooksQueens;
        int pawnColorIndex;

        if (attackerSide == Side.WHITE) {
            pawns = whitePawns;
            knights = whiteKnights;
            king = whiteKing;
            bishopsQueens = whiteBishops | whiteQueens;
            rooksQueens = whiteRooks | whiteQueens;
            pawnColorIndex = Side.BLACK.ordinal();
        } else {
            pawns = blackPawns;
            knights = blackKnights;
            king = blackKing;
            bishopsQueens = blackBishops | blackQueens;
            rooksQueens = blackRooks | blackQueens;
            pawnColorIndex = Side.WHITE.ordinal();
        }

        pawns &= attackersMask;
        knights &= attackersMask;
        king &= attackersMask;
        bishopsQueens &= attackersMask;
        rooksQueens &= attackersMask;

        if ((AttackLookups.PAWN_ATTACKS[pawnColorIndex][sq] & pawns) != 0) return true;
        if ((AttackLookups.KNIGHT_ATTACKS[sq] & knights) != 0) return true;
        if ((AttackLookups.KING_ATTACKS[sq] & king) != 0) return true;
        if (bishopsQueens != 0 && (AttackLookups.getBishopAttacks(sq, occupied) & bishopsQueens) != 0) return true;
        if (rooksQueens != 0 && (AttackLookups.getRookAttacks(sq, occupied) & rooksQueens) != 0) return true;
        
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

    public long getOccupiedSquares() {
        return occupiedSquares;
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
        return MoveGenerator.generatePseudoLegalMoves(this, moveList);
    }

    public int generateLoudMoves(int[] moveList) {
        return MoveGenerator.generateLoudMoves(this, moveList);
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