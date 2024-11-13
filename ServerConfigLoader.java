import java.io.*;
import java.util.Properties;
import java.util.logging.*;

/**
 * Handles loading and managing server configuration settings from a properties
 * file
 * Provides fallback to default values if configuration file is missing or
 * invalid
 */
public class ServerConfigLoader {
    // Logger instance for this class
    private static final Logger LOGGER = Logger.getLogger(ServerConfigLoader.class.getName());

    // Configuration constants
    private static final String CONFIG_FILE = "server_info.dat"; // Path to config file
    private static final String DEFAULT_HOST = "localhost"; // Default server host
    private static final int DEFAULT_PORT = 1234; // Default server port

    // Server configuration properties
    private String host; // Server hostname
    private int port; // Server port number

    /**
     * Constructor initializes the configuration loader
     * Automatically loads configuration when instantiated
     */
    public ServerConfigLoader() {
        loadConfiguration();
    }

    /**
     * Loads server configuration from the properties file
     * If any errors occur, falls back to default configuration
     * 
     * Configuration file format:
     * host=hostname
     * port=portnumber
     */
    private void loadConfiguration() {
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            // Load properties from configuration file
            props.load(fis);

            // Get host value, use default if not specified
            host = props.getProperty("host", DEFAULT_HOST);

            // Parse port number, handle potential format errors
            try {
                port = Integer.parseInt(props.getProperty("port", String.valueOf(DEFAULT_PORT)));
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid port number in config file. Using default: " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }

            LOGGER.info("Loaded configuration from " + CONFIG_FILE);

        } catch (IOException e) {
            // Log warning and use default configuration if file cannot be read
            LOGGER.warning("Could not read " + CONFIG_FILE + ": " + e.getMessage());
            useDefaultConfiguration();
        }
    }

    /**
     * Sets configuration to default values when configuration file
     * cannot be read or contains invalid data
     */
    private void useDefaultConfiguration() {
        host = DEFAULT_HOST;
        port = DEFAULT_PORT;
        LOGGER.info("Using default configuration - Host: " + host + ", Port: " + port);
    }

    /**
     * Returns the configured server hostname
     * 
     * @return String containing the hostname (default: "localhost")
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the configured server port number
     * 
     * @return int containing the port number (default: 1234)
     */
    public int getPort() {
        return port;
    }

    public static void main(String[] args) {
        // Create an instance of ServerConfigLoader
        ServerConfigLoader config = new ServerConfigLoader();

        // Print the loaded configuration
        System.out.println("Server Configuration:");
        System.out.println("Host: " + config.getHost());
        System.out.println("Port: " + config.getPort());
    }
}