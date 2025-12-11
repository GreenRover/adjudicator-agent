Here is a comprehensive plan to transition your engine to Incremental Evaluation. This involves updating the board score (Piece-Square Tables) incrementally every time a move is made (makeMove), rather than calculating it from scratch at every leaf node. This changes your complexity from O(N) (where N is piece count) to O(1) per node for the base evaluation.

1. The Strategy: Incremental PeSTO + Optimized Safety
   We will move the Piece-Square Table (PeSTO) scores into the Bitboard class as state variables (mgScore, egScore).

Current: Evaluator.evaluate() iterates over 64 squares and 12 piece types at every node.

New: Bitboard.makeMove() updates mgScore and egScore by subtracting the captured piece and adding the moved piece's new position value. Evaluator.evaluate() simply reads these values.

2. Task List for Gemini AI in IDE
   Copy and paste this task list into your IDE's chat or context window to track progress.

[ ] Step 1: Baseline Verification

[ ] Create IncrementalEvalTest.java to verify current evaluation values match expected outputs for specific FENs.

[ ] Document the test cases clearly explaining the expected scores.

[ ] Step 2: Expose Evaluation Data

[ ] Refactor Evaluator.java: Make MG_PAWN_TABLE, EG_PAWN_TABLE, etc., public static so Bitboard can access them for incremental updates.

[ ] Step 3: Implement Incremental State

[ ] Modify Bitboard.java: Add fields int mgPestoScore and int egPestoScore.

[ ] Implement initPestoScores() method in Bitboard to calculate initial scores (called in loadFromFen).

[ ] Step 4: Implement Incremental Updates

[ ] Modify Bitboard.putPieceInternal() to add values to mgPestoScore/egPestoScore.

[ ] Modify Bitboard.removePieceInternal() to subtract values from mgPestoScore/egPestoScore.

[ ] Step 5: Switch Evaluator

[ ] Modify Evaluator.evaluate() to use board.getMgPestoScore() instead of calling evaluatePieces().

[ ] Disable/Simplify the expensive evaluateMobility and evaluateKingSafety (iterative loops) to ensure they don't negate the speed gains.

[ ] Step 6: Regression Testing

[ ] Run IncrementalEvalTest to ensure the new incremental logic yields the same scores as a full recalculation.

3. Instructions to "Force" the IDE
   Use the following prompts with your IDE's AI Assistant (Gemini) to execute each step safely.

Prompt 1: Create Baseline Tests (Safety Net)
"Create a new test class src/test/java/ch/adjudicator/agent/engine/IncrementalEvalTest.java. I need to ensure that as we refactor for incremental evaluation, we don't break the scoring logic.

Add a test method testConsistency() that loads the Start Position and 3 complex middlegame FENs.

For each FEN, calculate Evaluator.evaluate(board) and assert it equals the known current value (you can run it once to get the value, then hardcode it as the expected value).

Add Javadoc to the test explaining that this test serves as a regression baseline for the move to incremental updates."

Prompt 2: Expose Tables & Add State
"Perform the following refactoring to prepare for incremental evaluation:

In src/main/java/ch/adjudicator/agent/engine/Evaluator.java, change the visibility of all MG_*_TABLE and EG_*_TABLE arrays from private to public.

In src/main/java/ch/adjudicator/agent/engine/board/Bitboard.java:

Add two private integer fields: mgPestoScore and egPestoScore.

Add getters for these fields.

Create a private method initPestoScores() that iterates over the board (similar to Evaluator.evaluatePieces) and calculates the initial values for these two fields based on the current pieces.

Call initPestoScores() at the end of loadFromFen()."

Prompt 3: Implement Incremental Logic (The Core Logic)
"This is the critical step. We need to update scores incrementally when pieces move.

In src/main/java/ch/adjudicator/agent/engine/board/Bitboard.java:

Update putPieceInternal(Piece piece, int sqIdx):

Look up the Evaluator.MG_*_TABLE and EG_*_TABLE values for the piece at sqIdx.

Add these values to mgPestoScore and egPestoScore.

Handle score mirroring for White (index ^ 56) as done in Evaluator.

If the piece is Black, subtract the value (since evaluation is White relative); if White, add it.

Update removePieceInternal(Piece piece, int sqIdx):

Look up the table values.

Subtract them (reverse the operation: subtract for White, add for Black) to remove the piece's contribution.

Verify makeMove calls these methods, so mgPestoScore should automatically track the board state."

Prompt 4: Switch Evaluator & Verify
"Now we switch to using the cached scores to get the performance boost.

In src/main/java/ch/adjudicator/agent/engine/Evaluator.java:

Modify evaluate(Bitboard board) to initialize whiteScore and blackScore (or the combined score) directly from board.getMgPestoScore() and board.getEgPestoScore().

Remove or comment out the call to evaluatePieces(), as this is now covered by the incremental score.

Performance Fix: Comment out evaluateMobility() completely (it is too slow for now).

Performance Fix: Inside evaluateKingSafety(), comment out the while() loops that count attackers. Keep only the Pawn Shield logic.

Run the IncrementalEvalTest. If values differ slightly (due to removing mobility), update the test expectations to the new 'faster' values, but ensure the logic holds (e.g., hanging a queen still drops the score)."