package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.List;

public interface BoardInterface {
    void loadFromFen(String fen);
    String getFen();
    long getZobristKey();
    Side getSideToMove();
    List<Move> legalMoves();
    boolean doMove(Move move);
    Move undoMove();
    boolean doNullMove();
    boolean isMated();
    boolean isKingAttacked();
    Piece getPiece(Square square);
    Square getEnPassant();
    CastleRight getCastleRight(Side side);
    long getBitboard(Side side);
    long getBitboard(Piece piece);
}
