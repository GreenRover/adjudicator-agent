package ch.adjudicator.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Monitors CPU temperature to prevent overheating.
 * Used to disable pondering when CPU temperature exceeds threshold.
 * Gathers temperature in a separate thread every 10 seconds.
 */
public class CpuTemperatureMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CpuTemperatureMonitor.class);

    private static final double DEFAULT_TEMPERATURE_THRESHOLD = 80.0; // Celsius
    private static final long POLLING_INTERVAL_MS = 10000; // 10 seconds

    private final double temperatureThreshold;
    private volatile double lastTemperature = -1.0;
    private volatile boolean running = false;
    private Thread monitorThread;

    public CpuTemperatureMonitor() {
        this(DEFAULT_TEMPERATURE_THRESHOLD);
    }

    public CpuTemperatureMonitor(double temperatureThreshold) {
        this.temperatureThreshold = temperatureThreshold;
        LOGGER.info("CPU Temperature Monitor initialized with threshold: {}°C", temperatureThreshold);


        start();
    }

    /**
     * Starts the background temperature monitoring thread.
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        monitorThread = new Thread(this::monitorLoop, "CPU-Temperature-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        LOGGER.info("CPU temperature monitoring started (polling every {}s)", POLLING_INTERVAL_MS / 1000);
    }

    /**
     * Stops the background temperature monitoring thread.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
            try {
                monitorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("CPU temperature monitoring stopped");
    }

    /**
     * Background monitoring loop that polls temperature every 10 seconds.
     */
    private void monitorLoop() {
        while (running) {
            try {
                double temp = readTemperature();
                lastTemperature = temp;

                if (temp >= 0) {
                    LOGGER.debug("CPU temperature: {}°C", temp);
                }

                Thread.sleep(POLLING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.debug("Error in temperature monitoring loop: {}", e.getMessage());
            }
        }
    }

    /**
     * Checks if CPU temperature is safe for pondering.
     * Uses the last cached temperature reading from the background thread.
     * Returns false if temperature exceeds threshold.
     */
    public boolean isSafeForPondering() {
        double temperature = lastTemperature;

        if (temperature < 0) {
            // Temperature reading failed, allow pondering by default
            return true;
        }

        boolean safe = temperature < temperatureThreshold;
        if (!safe) {
            LOGGER.warn("CPU temperature too high for pondering: {}°C (threshold: {}°C)",
                    temperature, temperatureThreshold);
        }
        return safe;
    }

    /**
     * Gets the last cached CPU temperature in Celsius.
     * Returns -1 if temperature cannot be read.
     */
    public double getLastTemperature() {
        return lastTemperature;
    }

    /**
     * Reads current CPU temperature in Celsius.
     * Returns -1 if temperature cannot be read.
     */
    private double readTemperature() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return getWindowsTemperature();
        } else if (os.contains("linux")) {
            return getLinuxTemperature();
        } else if (os.contains("mac")) {
            return getMacTemperature();
        }

        return -1;
    }

    /**
     * Reads CPU temperature on Windows using WMI.
     */
    private double getWindowsTemperature() {
        try {
            // Try using WMI to get temperature from MSAcpi_ThermalZoneTemperature
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "powershell.exe",
                    "-Command",
                    "Get-WmiObject -Namespace root/wmi -Class MSAcpi_ThermalZoneTemperature | Select-Object -ExpandProperty CurrentTemperature"
            );

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line = reader.readLine();
            process.waitFor();

            if (line != null && !line.trim().isEmpty()) {
                try {
                    // WMI returns temperature in tenths of Kelvin
                    double kelvin = Double.parseDouble(line.trim()) / 10.0;
                    double celsius = kelvin - 273.15;
                    LOGGER.debug("CPU temperature: {}°C", celsius);
                    return celsius;
                } catch (NumberFormatException e) {
                    LOGGER.debug("Failed to parse temperature: {}", line);
                }
            }

            // Fallback: try OpenHardwareMonitor or similar tools if available
            // For now, return -1 to indicate unavailable
            LOGGER.debug("WMI temperature reading not available");
            return -1;

        } catch (Exception e) {
            LOGGER.debug("Error reading Windows temperature: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Reads CPU temperature on Linux from /sys/class/thermal.
     */
    private double getLinuxTemperature() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "bash", "-c",
                    "cat /sys/class/thermal/thermal_zone0/temp"
            );

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line = reader.readLine();
            process.waitFor();

            if (line != null && !line.trim().isEmpty()) {
                try {
                    // Linux returns temperature in millidegrees Celsius
                    double celsius = Double.parseDouble(line.trim()) / 1000.0;
                    LOGGER.debug("CPU temperature: {}°C", celsius);
                    return celsius;
                } catch (NumberFormatException e) {
                    LOGGER.debug("Failed to parse temperature: {}", line);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("Error reading Linux temperature: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Reads CPU temperature on macOS using powermetrics or other tools.
     */
    private double getMacTemperature() {
        // macOS temperature reading is more complex and often requires root access
        // For now, return -1 to indicate unavailable
        LOGGER.debug("macOS temperature reading not implemented");
        return -1;
    }

    public double getTemperatureThreshold() {
        return temperatureThreshold;
    }
}
