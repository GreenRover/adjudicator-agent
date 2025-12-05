package ch.adjudicator.agent.engine;

import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TimeManagerTest {

    @Test
    void testPanicMode() {
        // Increment 0
        TimeManager tm = new TimeManager(0);
        // Time remaining 500ms (below panic threshold 1000ms)
        // LAG_BUFFER is 50ms. Available = 450ms.
        // Panic returns available / 2 = 225ms.
        long allocated = tm.allocateTime(500, 10);
        assertThat("Should return half of available time in panic mode", allocated, is(225L));
    }

    @Test
    void testOpeningPhase() {
        // Increment 0
        TimeManager tm = new TimeManager(0);
        // Move 5 (Opening <= 10), Multiplier 0.7
        // Time 60000ms. Available 59950.
        // Base = 59950 / 20 = 2997.
        // Multiplied = 2997 * 0.7 = 2097.
        // Limits: Min 100, Max 10000. 2097 is within limits.
        // 10% cap: 59950 / 10 = 5995. 2097 is within cap.
        long allocated = tm.allocateTime(60000, 5);
        
        // Calculation check
        long available = 60000 - 50;
        long expectedBase = available / 20;
        long expected = (long) (expectedBase * 0.7);
        
        assertThat("Opening moves should use 0.7 multiplier", allocated, is(expected));
    }

    @Test
    void testMiddlegamePhase() {
        // Increment 0
        TimeManager tm = new TimeManager(0);
        // Move 20 (Middlegame 11-30), Multiplier 1.2
        // Time 60000ms. Available 59950.
        // Base = 59950 / 20 = 2997.
        // Multiplied = 2997 * 1.2 = 3596.
        long allocated = tm.allocateTime(60000, 20);
        
        long available = 60000 - 50;
        long expectedBase = available / 20;
        long expected = (long) (expectedBase * 1.2);
        
        assertThat("Middlegame moves should use 1.2 multiplier", allocated, is(expected));
    }

    @Test
    void testEndgamePhase() {
        TimeManager tm = new TimeManager(0);
        // Move 40 (Endgame > 30), Multiplier 0.8
        long allocated = tm.allocateTime(60000, 40);
        
        long available = 60000 - 50;
        long expectedBase = available / 20;
        long expected = (long) (expectedBase * 0.8);
        
        assertThat("Endgame moves should use 0.8 multiplier", allocated, is(expected));
    }

    @Test
    void testWithIncrement() {
        // Increment 1000ms
        TimeManager tm = new TimeManager(1000);
        // Move 20 (Middlegame, 1.2x)
        // Time 60000ms. Available 59950.
        // Base = 59950 / 20 = 2997.
        // Plus increment/2 = 2997 + 500 = 3497.
        // Multiplied = 3497 * 1.2 = 4196.
        long allocated = tm.allocateTime(60000, 20);
        
        long available = 60000 - 50;
        long base = available / 20;
        base += 1000 / 2;
        long expected = (long) (base * 1.2);
        
        assertThat("Should include increment in calculation", allocated, is(expected));
    }

    @Test
    void testMinTimeLimit() {
        TimeManager tm = new TimeManager(0);
        // Very low time but above panic? 
        // Panic is < 1000. Let's try 1100ms.
        // Available 1050. 
        // Base = 1050 / 20 = 52.
        // Multiplier 0.7 (move 1) -> 36.
        // Min limit is 100.
        // 10% cap is 105. 
        // Result should be 100.
        long allocated = tm.allocateTime(1100, 1);
        assertThat("Should respect minimum time limit", allocated, is(100L));
    }

    @Test
    void testMaxTimeLimit() {
        TimeManager tm = new TimeManager(0);
        // Huge time. 1,000,000 ms.
        // Base = 50000. Multiplier 1.2 -> 60000.
        // Max limit is 10000.
        long allocated = tm.allocateTime(1000000, 20);
        assertThat("Should respect maximum time limit", allocated, is(10000L));
    }

    @Test
    void testTenPercentCap() {
        TimeManager tm = new TimeManager(0);
        // Time 2000ms. Available 1950.
        // Base 1950 / 20 = 97.
        // Multiplier 1.2 (move 20) -> 116.
        // Min limit 100. Max 10000.
        // 10% cap = 195. 
        // 116 < 195, so it returns 116. This doesn't trigger the cap.
        
        // Let's try to trigger the cap. We need thinkingTime > available / 10.
        // ThinkingTime is approx available / 20 * multiplier.
        // So multiplier / 20 > 1 / 10 => multiplier > 2.
        // But max multiplier is 1.2. So with 0 increment, we likely won't hit the 10% cap easily unless increment pushes it up.
        
        // Let's use increment.
        // Time 2000ms. Available 1950. 10% = 195.
        // Increment 2000ms. 
        // Base = 97 + 1000 = 1097.
        // Multiplier 1.2 -> 1316.
        // Cap is 195.
        // Result should be 195.
        
        tm = new TimeManager(2000);
        long allocated = tm.allocateTime(2000, 20);
        assertThat("Should be capped at 10% of available time", allocated, is(195L));
    }

    @Test
    void testShouldStop() {
        TimeManager tm = new TimeManager(0);
        long start = System.currentTimeMillis();
        // Allocated 100ms.
        // If we check immediately, should be false.
        assertThat(tm.shouldStop(start, 1000), is(false));
        
        // Simulate elapsed time (we can't easily sleep in unit tests without slowing down, 
        // but we can pass a past timestamp).
        long pastStart = start - 2000;
        assertThat("Should stop if time exceeded", tm.shouldStop(pastStart, 1000), is(true));
    }

    @Test
    void testSoftLimit() {
        TimeManager tm = new TimeManager(0);
        assertThat(tm.getSoftLimit(1000), is(850L));
    }
    
    @Test
    void testMoveNumberUpdate() {
        TimeManager tm = new TimeManager(0);
        assertThat(tm.getMoveNumber(), is(1));
        
        tm.allocateTime(10000, 5);
        assertThat(tm.getMoveNumber(), is(5));
        
        tm.setMoveNumber(10);
        assertThat(tm.getMoveNumber(), is(10));
    }
}
