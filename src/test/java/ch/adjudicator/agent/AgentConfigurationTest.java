package ch.adjudicator.agent;

import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class AgentConfigurationTest {

    @Test
    void testDefaults() {
        // Use a non-existent file to ensure defaults are loaded
        AgentConfiguration config = new AgentConfiguration(new String[]{}, "non-existent.env");
        assertThat(config.getServerAddress(), is("grpc.adjudicator.ch"));
        assertThat(config.getApiKey(), is("1234"));
        assertThat(config.getAgentName(), is("EasyBot"));
        assertThat(config.getMode(), is("TRAINING"));
        assertThat(config.getTimeControl(), is("300+0"));
    }

    @Test
    void testCliOverrides() {
        String[] args = {
            "--server", "localhost:9090",
            "--key", "secret",
            "--name", "TestBot",
            "--mode", "RANKED",
            "--time", "600+5"
        };
        AgentConfiguration config = new AgentConfiguration(args);
        
        assertThat(config.getServerAddress(), is("localhost:9090"));
        assertThat(config.getApiKey(), is("secret"));
        assertThat(config.getAgentName(), is("TestBot"));
        assertThat(config.getMode(), is("RANKED"));
        assertThat(config.getTimeControl(), is("600+5"));
    }

    @Test
    void testValidationSuccess() {
        AgentConfiguration config = new AgentConfiguration(new String[]{"--key", "123"});
        try {
            config.validate();
            // If we reach here, validation succeeded (no exception thrown)
            assertThat(true, is(true));
        } catch (Exception e) {
            assertThat("Validation should not throw exception: " + e.getMessage(), false, is(true));
        }
    }
}
