package ch.adjudicator.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Handles configuration loading from agent.env file and command line arguments.
 * Priority: CLI arguments > Environment variables > agent.env > Default values.
 */
public class AgentConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConfiguration.class);

    private static final String DEFAULT_SERVER = "grpc.adjudicator.ch";
    private static final String DEFAULT_KEY = "1234";
    private static final String DEFAULT_NAME = "EasyBot";
    private static final String DEFAULT_MODE = "TRAINING";
    private static final String DEFAULT_TIME = "300+0";
    private static final String ENV_FILE_NAME = "agent.env";

    private String serverAddress;
    private String apiKey;
    private String agentName;
    private String mode;
    private String timeControl;
    private boolean monitorCpuTemp = true;

    public AgentConfiguration(String[] args) {
        this(args, ENV_FILE_NAME);
    }

    public AgentConfiguration(String[] args, String envFileName) {
        // 1. Initialize with defaults
        this.serverAddress = DEFAULT_SERVER;
        this.apiKey = DEFAULT_KEY;
        this.agentName = DEFAULT_NAME;
        this.mode = DEFAULT_MODE;
        this.timeControl = DEFAULT_TIME;

        // 2. Load from agent.env (overrides defaults)
        loadFromEnvFile(envFileName);

        // 3. Load from CLI args (overrides env and defaults)
        parseArgs(args);
    }

    private void loadFromEnvFile(String fileName) {
        File envFile = new File(fileName);
        if (envFile.exists()) {
            try (FileInputStream fis = new FileInputStream(envFile)) {
                Properties props = new Properties();
                props.load(fis);

                if (hasValue(props.getProperty("SERVER"))) {
                    this.serverAddress = props.getProperty("SERVER");
                }
                if (hasValue(props.getProperty("API_KEY"))) {
                    this.apiKey = props.getProperty("API_KEY");
                }
                if (hasValue(props.getProperty("AGENT_NAME"))) {
                    this.agentName = props.getProperty("AGENT_NAME");
                }
                LOGGER.info("Loaded configuration from {}", fileName);
            } catch (IOException e) {
                LOGGER.warn("Failed to load {}: {}", fileName, e.getMessage());
            }
        }
    }

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--server":
                    this.serverAddress = args[i + 1];
                    break;
                case "--key":
                    this.apiKey = args[i + 1];
                    break;
                case "--name":
                    this.agentName = args[i + 1];
                    break;
                case "--mode":
                    this.mode = args[i + 1];
                    break;
                case "--time":
                    this.timeControl = args[i + 1];
                    break;
                case "--no-cpu-temp":
                    this.monitorCpuTemp = false;
                    break;
            }
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getMode() {
        return mode;
    }

    public String getTimeControl() {
        return timeControl;
    }

    public boolean isMonitorCpuTemp() {
        return monitorCpuTemp;
    }

    public void validate() {
        if (!hasValue(apiKey)) {
            throw new IllegalArgumentException("API key is required. Use --key <api-key> or set API_KEY in agent.env");
        }
    }
}
