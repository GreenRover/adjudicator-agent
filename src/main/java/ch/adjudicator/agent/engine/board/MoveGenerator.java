package ch.adjudicator.agent.engine.board;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;

public class MoveGenerator {

    public static int generateLegalMoves(Bitboard board, int[] moves) {
        int[] pseudo = new int[256];
        int count = generatePseudoLegalMoves(board, pseudo);
        int legalCount = 0;

        int kingSq = (board.sideToMove == Side.WHITE) ? board.whiteKingSq : board.blackKingSq;
        Side us = board.sideToMove;
        Side enemy = (us == Side.WHITE) ? Side.BLACK : Side.WHITE;

        for (int i = 0; i < count; i++) {
            int m = pseudo[i];
            int from = m & 0x3F;
            int to = (m >> 6) & 0x3F;

            // Castling Path Check
            if (kingSq != -1 && from == kingSq && Math.abs(to - from) == 2) {
                if (board.isSquareAttacked(from, enemy)) continue;
                int mid = (from + to) / 2;
                if (board.isSquareAttacked(mid, enemy)) continue;
            }

            if (isLegalVirtual(board, m, kingSq)) {
                moves[legalCount++] = m;
            }
        }
        return legalCount;
    }

    static boolean isLegalVirtual(Bitboard board, int move, int kingSq) {
        int from = move & 0x3F;
        int to = (move >> 6) & 0x3F;

        Piece movingPiece = board.mailbox[from];
        Side us = Bitboard.PIECE_SIDES[movingPiece.ordinal()];

        long fromBit = 1L << from;
        long toBit = 1L << to;
        long occupied = board.occupiedSquares;
        long ignoreMask = -1L;

        int currentKingSq = kingSq;
        if (movingPiece == Piece.WHITE_KING || movingPiece == Piece.BLACK_KING) {
            currentKingSq = to;
        }

        if ((movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) &&
                board.enPassantSquare != Square.NONE && to == board.enPassantSquare.ordinal()) {

            int capSq = (us == Side.WHITE) ? to - 8 : to + 8;
            long capBit = 1L << capSq;
            occupied = (occupied & ~fromBit & ~capBit) | toBit;
            ignoreMask = ~capBit;

        } else {
            occupied = (occupied & ~fromBit) | toBit;
            if (board.mailbox[to] != Piece.NONE) {
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

        return !isSquareAttackedVirtual(board, currentKingSq, us == Side.WHITE ? Side.BLACK : Side.WHITE, occupied, ignoreMask);
    }

    static boolean isSquareAttackedVirtual(Bitboard board, int sq, Side attackerSide, long occupied, long ignoreMask) {
        if (attackerSide == Side.WHITE) {
            if ((AttackLookups.PAWN_ATTACKS[Side.BLACK.ordinal()][sq] & board.whitePawns & ignoreMask) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & board.whiteKnights & ignoreMask) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & board.whiteKing & ignoreMask) != 0) return true;

            long bishopsQueens = (board.whiteBishops | board.whiteQueens) & ignoreMask;
            if (bishopsQueens != 0) {
                if ((AttackLookups.getBishopAttacks(sq, occupied) & bishopsQueens) != 0) return true;
            }

            long rooksQueens = (board.whiteRooks | board.whiteQueens) & ignoreMask;
            if (rooksQueens != 0) {
                return (AttackLookups.getRookAttacks(sq, occupied) & rooksQueens) != 0;
            }
        } else {
            if ((AttackLookups.PAWN_ATTACKS[Side.WHITE.ordinal()][sq] & board.blackPawns & ignoreMask) != 0) return true;
            if ((AttackLookups.KNIGHT_ATTACKS[sq] & board.blackKnights & ignoreMask) != 0) return true;
            if ((AttackLookups.KING_ATTACKS[sq] & board.blackKing & ignoreMask) != 0) return true;

            long bishopsQueens = (board.blackBishops | board.blackQueens) & ignoreMask;
            if (bishopsQueens != 0) {
                if ((AttackLookups.getBishopAttacks(sq, occupied) & bishopsQueens) != 0) return true;
            }

            long rooksQueens = (board.blackRooks | board.blackQueens) & ignoreMask;
            if (rooksQueens != 0) {
                return (AttackLookups.getRookAttacks(sq, occupied) & rooksQueens) != 0;
            }
        }
        return false;
    }

    public static int generatePseudoLegalMoves(Bitboard board, int[] moveList) {
        int index = 0;
        long friendly, enemy;
        long myPawns, myKnights, myBishops, myRooks, myQueens, myKing;
        
        int promoRank, startRank;
        boolean isWhite = (board.sideToMove == Side.WHITE);

        if (isWhite) {
            friendly = board.whitePieces;
            enemy = board.blackPieces & ~board.blackKing;
            myPawns = board.whitePawns;
            myKnights = board.whiteKnights;
            myBishops = board.whiteBishops;
            myRooks = board.whiteRooks;
            myQueens = board.whiteQueens;
            myKing = board.whiteKing;
            
            promoRank = 7;
            startRank = 1;
        } else {
            friendly = board.blackPieces;
            enemy = board.whitePieces & ~board.whiteKing;
            myPawns = board.blackPawns;
            myKnights = board.blackKnights;
            myBishops = board.blackBishops;
            myRooks = board.blackRooks;
            myQueens = board.blackQueens;
            myKing = board.blackKing;
            
            promoRank = 0;
            startRank = 6;
        }

        long occupied = board.occupiedSquares;

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
                    moveList[index++] = Bitboard.encodeMove(sq, forwardSq, 0);
                    if (rank == startRank) {
                        int doubleRank = isWhite ? rank + 2 : rank - 2;
                        int doubleSq = doubleRank * 8 + file;
                        if (((1L << doubleSq) & occupied) == 0) {
                            moveList[index++] = Bitboard.encodeMove(sq, doubleSq, 0);
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
                            moveList[index++] = Bitboard.encodeMove(sq, captureSq, 0);
                        }
                    } else if (captureSq == board.enPassantSquare.ordinal()) {
                        moveList[index++] = Bitboard.encodeMove(sq, captureSq, 0);
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
                moveList[index++] = Bitboard.encodeMove(sq, to, 0);
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
                moveList[index++] = Bitboard.encodeMove(sq, to, 0);
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
                moveList[index++] = Bitboard.encodeMove(sq, to, 0);
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
                moveList[index++] = Bitboard.encodeMove(sq, to, 0);
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
                moveList[index++] = Bitboard.encodeMove(sq, to, 0);
            }
        }

        // --- Castling ---
        int castling = board.castlingRights;
        if (castling != 0) {
             if (isWhite) {
                 if ((castling & Bitboard.CASTLE_WK) != 0 && (occupied & 0x60L) == 0) {
                     if (!board.isSquareAttacked(4, Side.BLACK) && !board.isSquareAttacked(5, Side.BLACK) && !board.isSquareAttacked(6, Side.BLACK)) {
                         moveList[index++] = Bitboard.encodeMove(4, 6, 0);
                     }
                 }
                 if ((castling & Bitboard.CASTLE_WQ) != 0 && (occupied & 0xEL) == 0) {
                     if (!board.isSquareAttacked(4, Side.BLACK) && !board.isSquareAttacked(3, Side.BLACK) && !board.isSquareAttacked(2, Side.BLACK)) {
                         moveList[index++] = Bitboard.encodeMove(4, 2, 0);
                     }
                 }
             } else {
                 if ((castling & Bitboard.CASTLE_BK) != 0 && (occupied & 0x6000000000000000L) == 0) {
                     if (!board.isSquareAttacked(60, Side.WHITE) && !board.isSquareAttacked(61, Side.WHITE) && !board.isSquareAttacked(62, Side.WHITE)) {
                         moveList[index++] = Bitboard.encodeMove(60, 62, 0);
                     }
                 }
                 if ((castling & Bitboard.CASTLE_BQ) != 0 && (occupied & 0xE00000000000000L) == 0) {
                     if (!board.isSquareAttacked(60, Side.WHITE) && !board.isSquareAttacked(59, Side.WHITE) && !board.isSquareAttacked(58, Side.WHITE)) {
                         moveList[index++] = Bitboard.encodeMove(60, 58, 0);
                     }
                 }
             }
        }

        return index;
    }

    public static int generateLoudMoves(Bitboard board, int[] moveList) {
        int index = 0;
        long friendly, enemy;
        long myPawns, myKnights, myBishops, myRooks, myQueens, myKing;
        
        int promoRank;
        boolean isWhite = (board.sideToMove == Side.WHITE);

        if (isWhite) {
            friendly = board.whitePieces;
            enemy = board.blackPieces & ~board.blackKing;
            myPawns = board.whitePawns;
            myKnights = board.whiteKnights;
            myBishops = board.whiteBishops;
            myRooks = board.whiteRooks;
            myQueens = board.whiteQueens;
            myKing = board.whiteKing;
            
            promoRank = 7;
        } else {
            friendly = board.blackPieces;
            enemy = board.whitePieces & ~board.whiteKing;
            myPawns = board.blackPawns;
            myKnights = board.blackKnights;
            myBishops = board.blackBishops;
            myRooks = board.blackRooks;
            myQueens = board.blackQueens;
            myKing = board.blackKing;
            
            promoRank = 0;
        }

        long occupied = board.occupiedSquares;

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
                            int move = Bitboard.encodeMove(sq, captureSq, 0);
                            moveList[index++] = move;
                        } else if (captureSq == board.enPassantSquare.ordinal()) {
                            int move = Bitboard.encodeMove(sq, captureSq, 0);
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
                int move = Bitboard.encodeMove(sq, to, 0);
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
                int move = Bitboard.encodeMove(sq, to, 0);
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
                int move = Bitboard.encodeMove(sq, to, 0);
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
                int move = Bitboard.encodeMove(sq, to, 0);
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
                int move = Bitboard.encodeMove(sq, to, 0);
                moveList[index++] = move;
            }
        }
        
        return index;
    }

    private static void addPromoMoves(int[] moveList, int index, int from, int to) {
        moveList[index] = Bitboard.encodeMove(from, to, 1);
        moveList[index+1] = Bitboard.encodeMove(from, to, 2);
        moveList[index+2] = Bitboard.encodeMove(from, to, 3);
        moveList[index+3] = Bitboard.encodeMove(from, to, 4);
    }
}
