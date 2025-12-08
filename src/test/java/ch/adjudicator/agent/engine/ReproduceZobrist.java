package ch.adjudicator.agent.engine;

import org.junit.jupiter.api.Test;

public class ReproduceZobrist {

    private long currentHash;

    @Test
    public void crackZobrist() {
        long target = 0x463b96181691fc9cL;

        // Generate Keys
        PolyglotRandom rand = new PolyglotRandom();
        long[] pieceKeys = new long[768]; // 12 * 64
        for (int i = 0; i < 768; i++) pieceKeys[i] = rand.next();

        long[] castlingKeys = new long[16];
        for (int i = 0; i < 16; i++) castlingKeys[i] = rand.next();

        long[] epKeys = new long[64];
        for (int i = 0; i < 64; i++) epKeys[i] = rand.next();

        long sideKey = rand.next();

        // Variations
        boolean[] booleanOpts = {true, false};

        for (boolean pieceMajor : booleanOpts) {
            for (boolean blackFirst : booleanOpts) {
                for (boolean squareMajor : booleanOpts) { // keys layout: [piece][sq] vs [sq][piece]
                    for (boolean mapRank0ToA1 : booleanOpts) { // 0=a1 vs 0=a8

                        long hash = 0;

                        // Calculate hash for Start Position

                        // Pieces
                        // White (Color 0)
                        // R: a1, h1
                        // N: b1, g1
                        // B: c1, f1
                        // Q: d1
                        // K: e1
                        // P: a2..h2

                        // Black (Color 1)
                        // R: a8, h8
                        // ...

                        // We need square indices based on mapping

                        addPiece(pieceKeys, hash, 3, 0, 0, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WR a1
                        hash = currentHash;
                        addPiece(pieceKeys, hash, 3, 0, 7, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WR h1
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 1, 0, 1, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WN b1
                        hash = currentHash;
                        addPiece(pieceKeys, hash, 1, 0, 6, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WN g1
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 2, 0, 2, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WB c1
                        hash = currentHash;
                        addPiece(pieceKeys, hash, 2, 0, 5, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WB f1
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 4, 0, 3, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WQ d1
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 5, 0, 4, 0, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // WK e1
                        hash = currentHash;

                        // WP a2-h2 (File 0-7, Rank 1)
                        for (int f = 0; f < 8; f++) {
                            addPiece(pieceKeys, hash, 0, 0, f, 1, pieceMajor, blackFirst, squareMajor, mapRank0ToA1);
                            hash = currentHash;
                        }

                        // Black pieces (Rank 7 and 8 -> indices 6 and 7)
                        // Black R: a8, h8 (Rank 7)
                        addPiece(pieceKeys, hash, 3, 1, 0, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BR a8
                        hash = currentHash;
                        addPiece(pieceKeys, hash, 3, 1, 7, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BR h8
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 1, 1, 1, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BN b8
                        hash = currentHash;
                        addPiece(pieceKeys, hash, 1, 1, 6, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BN g8
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 2, 1, 2, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BB c8
                        hash = currentHash;
                        addPiece(pieceKeys, hash, 2, 1, 5, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BB f8
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 4, 1, 3, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BQ d8
                        hash = currentHash;

                        addPiece(pieceKeys, hash, 5, 1, 4, 7, pieceMajor, blackFirst, squareMajor, mapRank0ToA1); // BK e8
                        hash = currentHash;

                        // BP a7-h7 (File 0-7, Rank 6)
                        for (int f = 0; f < 8; f++) {
                            addPiece(pieceKeys, hash, 0, 1, f, 6, pieceMajor, blackFirst, squareMajor, mapRank0ToA1);
                            hash = currentHash;
                        }

                        // Castling
                        long castlingHashMask = castlingKeys[15];
                        long castlingHashXor4 = castlingKeys[0] ^ castlingKeys[1] ^ castlingKeys[2] ^ castlingKeys[3];
                        long castlingHashXorBits = castlingKeys[1] ^ castlingKeys[2] ^ castlingKeys[4] ^ castlingKeys[8];

                        long[] castlingOptions = {castlingHashMask, castlingHashXor4, castlingHashXorBits};
                        String[] castlingNames = {"Mask[15]", "Xor[0..3]", "Xor[1,2,4,8]"};

                        for (int cIdx = 0; cIdx < castlingOptions.length; cIdx++) {
                            long cHash = castlingOptions[cIdx];
                            long finalHash = currentHash ^ cHash;

                            // Side
                            if (finalHash == target) {
                                throwFound("No Side Key, Castling=" + castlingNames[cIdx], pieceMajor, blackFirst, squareMajor, mapRank0ToA1);
                            }
                            if ((finalHash ^ sideKey) == target) {
                                throwFound("With Side Key, Castling=" + castlingNames[cIdx], pieceMajor, blackFirst, squareMajor, mapRank0ToA1);
                            }
                        }

                    }
                }
            }
        }
    }

    private void addPiece(long[] keys, long hash, int pieceType, int color, int file, int rank,
                          boolean pieceMajor, boolean blackFirst, boolean squareMajor, boolean mapRank0ToA1) {

        int squareIndex;
        if (mapRank0ToA1) {
            // 0=a1 (file 0, rank 0), 63=h8
            squareIndex = rank * 8 + file;
        } else {
            // 0=a8 (file 0, rank 7), 63=h1
            // rank 7 -> 0..7
            // rank 0 -> 56..63
            squareIndex = (7 - rank) * 8 + file;
        }

        // pieceType: 0..5
        // color: 0=White, 1=Black

        int typeIndex;
        if (pieceMajor) {
            // PieceType -> (Color1, Color2)
            int firstColor = blackFirst ? 1 : 0;
            int offset = (color == firstColor) ? 0 : 1;
            typeIndex = pieceType * 2 + offset;
        } else {
            // Color1 -> Pieces, Color2 -> Pieces
            int firstColor = blackFirst ? 1 : 0;
            if (color == firstColor) {
                typeIndex = pieceType;
            } else {
                typeIndex = 6 + pieceType;
            }
        }

        long key;
        if (squareMajor) {
            // [Square][Piece]
            key = keys[squareIndex * 12 + typeIndex];
        } else {
            // [Piece][Square]
            key = keys[typeIndex * 64 + squareIndex];
        }

        currentHash = hash ^ key;
    }

    private void throwFound(String sideInfo, boolean pieceMajor, boolean blackFirst, boolean squareMajor, boolean mapRank0ToA1) {
        String msg = String.format("FOUND! Side:%s, PieceMajor:%b, BlackFirst:%b, SquareMajor:%b, Map0=A1:%b",
                sideInfo, pieceMajor, blackFirst, squareMajor, mapRank0ToA1);
        System.out.println(msg);
        throw new RuntimeException(msg);
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
}
