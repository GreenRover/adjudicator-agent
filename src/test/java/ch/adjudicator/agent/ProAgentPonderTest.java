package ch.adjudicator.agent;

import ch.adjudicator.client.Color;
import ch.adjudicator.client.GameInfo;
import ch.adjudicator.client.MoveRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ProAgentPonderTest {

    static class TestProAgent extends ProAgent {
        boolean timeoutTriggered = false;
        CountDownLatch latch = new CountDownLatch(1);

        public TestProAgent() {
            super("TestBot", false); // false = no CPU monitoring
        }

        @Override
        protected void triggerEndGameDueToTimeout() {
            timeoutTriggered = true;
            latch.countDown();
        }
    }

    @Test
    void testPonderTimeoutTriggersEndGame() throws Exception {
        TestProAgent agent = new TestProAgent();
        agent.onGameStart(new GameInfo("test-ponder", Color.WHITE, 60000, 0));
        
        // Set a short timeout for the test
        agent.setPonderTimeoutMs(200);
        agent.disableBookForTesting();

        // We need the agent to search and populate TT so that pondering starts.
        // We give it some time to think.
        MoveRequest request = new MoveRequest("", 60000, 60000);
        
        // This move will take some time (allocated time ~1-3s)
        // It should populate TT.
        // Then it will start pondering.
        String move = agent.getMove(request);
        
        System.out.println("Agent played: " + move);
        
        // Wait for the timeout to trigger
        boolean triggered = agent.latch.await(2000, TimeUnit.MILLISECONDS);
        
        // If pondering didn't start, this will fail.
        // Pondering starts if TT has a move.
        // If it fails, we might need to ensure search goes deep enough or force pondering.
        
        // However, we can't easily force pondering if TT is empty. 
        // But let's see if default search behavior is enough.
        
        if (!triggered && !agent.timeoutTriggered) {
             System.out.println("Timeout not triggered. Maybe pondering didn't start?");
        }
        
        assertThat("Timeout should have triggered", agent.timeoutTriggered, is(true));
    }
}
