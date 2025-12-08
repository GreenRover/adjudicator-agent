package ch.adjudicator.agent;

import ch.adjudicator.client.Color;
import ch.adjudicator.client.GameInfo;
import ch.adjudicator.client.MoveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class EasyAgentTest {

    private EasyAgent agent;

    @BeforeEach
    void setUp() {
        agent = new EasyAgent("TestBot");
        // Initialize the agent's internal board by calling onGameStart
        agent.onGameStart(new GameInfo("test-game", Color.WHITE, 300000, 0));
    }

    @Test
    void testFirstMove() throws Exception {
        MoveRequest request = new MoveRequest("", 300000, 300000);
        String move = agent.getMove(request);

        assertThat(move, notNullValue());
        assertThat(move.isEmpty(), is(false));
        // Initial move should be a white opening (e.g. "e2e4")
        assertThat(move, matchesPattern("[a-h][27][a-h][3456]"));
    }

    @Test
    void testResponseToOpponentMove() throws Exception {
        // Opponent plays e2e4 (assuming we are black now for this logic, 
        // but the agent instance doesn't strictly enforce turn order locally other than legal moves)
        // Actually, if we initialize as WHITE, and we receive a move, it implies we are BLACK or it's the next turn.
        // EasyAgent creates a new Board(), which starts at WHITE.
        // If we receive an opponent move "e2e4", the internal board (starting WHITE) will apply it as White's move,
        // then it will be our turn (Black).

        MoveRequest request = new MoveRequest("e2e4", 290000, 295000);
        String move = agent.getMove(request);

        assertThat(move, notNullValue());
        // Should be a valid response from black
        // e.g. e7e5, c7c5, etc.
        // Simple regex for a move
        assertThat(move.matches("[a-h][1-8][a-h][1-8][qrbn]?"), is(true));
    }

    @Test
    void testAgentName() {
        // Since name is private and no getter, we can't test it directly unless we add a getter
        // or verify logs. For now we trust the constructor.
    }
}
