package ch.adjudicator.agent.engine;

import com.github.bhlangonijr.chesslib.CastleRight;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.List;

public class Bitboard implements BoardInterface {
    @Override
    public void loadFromFen(String fen) {

    }

    @Override
    public String getFen() {
        return "";
    }

    @Override
    public long getZobristKey() {
        return 0;
    }

    @Override
    public Side getSideToMove() {
        return null;
    }

    @Override
    public List<Move> legalMoves() {
        return List.of();
    }

    @Override
    public boolean doMove(Move move) {
        return false;
    }

    @Override
    public Move undoMove() {
        return null;
    }

    @Override
    public boolean doNullMove() {
        return false;
    }

    @Override
    public boolean isMated() {
        return false;
    }

    @Override
    public boolean isKingAttacked() {
        return false;
    }

    @Override
    public Piece getPiece(Square square) {
        return null;
    }

    @Override
    public Square getEnPassant() {
        return null;
    }

    @Override
    public CastleRight getCastleRight(Side side) {
        return null;
    }

    @Override
    public long getBitboard(Side side) {
        return 0;
    }

    @Override
    public long getBitboard(Piece piece) {
        return 0;
    }
}
