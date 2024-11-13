import java.io.*;
import java.net.*;
import java.util.logging.*;
import java.util.Date;
import java.util.Scanner; // Scanner Ãß°¡

/**
 * A client application for connecting to and interacting with a quiz server
 * Handles network communication, user input/output, and logging
 */
public class QuizClient {
    // Logger instance for this class to handle logging operations
    private static final Logger LOGGER = Logger.getLogger(QuizClient.class.getName());
    // Configuration file path for server connection details
    private static final String CONFIG_FILE = "server_info.dat";
    // Default server connection parameters
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1234;

    // Network and I/O components
    private Socket socket; // Socket for server connection
    private BufferedReader in; // For reading server responses
    private PrintWriter out; // For sending messages to server

    public QuizClient() {
        setupLogger();
    }

    /**
     * Configures the logging system with custom formatting
     * Sets up console handler and formats log messages with timestamp
     */
    private void setupLogger() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%1$tF %1$tT] %2$s%n",
                        new Date(record.getMillis()),
                        record.getMessage());
            }
        });
        rootLogger.addHandler(handler);
    }

    /**
     * Creates and returns a new server configuration loader
     * 
     * @return ServerConfigLoader instance for reading server settings
     */
    private ServerConfigLoader loadServerConfig() {
        return new ServerConfigLoader();
    }

    /**
     * Main method to start the quiz session
     * Establishes connection with server and handles the quiz interaction
     */
    public void startQuiz() {
        ServerConfigLoader config = loadServerConfig();

        try {
            // Attempt to connect to the quiz server
            LOGGER.info("Connecting to server at " + config.getHost() + ":" + config.getPort());
            socket = new Socket(config.getHost(), config.getPort());
            LOGGER.info("Connected to quiz server");

            // Initialize input/output streams with UTF-8 encoding
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Initialize Scanner for user input
            Scanner scanner = new Scanner(System.in);

            String serverMessage;
            // Main communication loop
            while ((serverMessage = in.readLine()) != null) {
                // Remove server prefix if present
                if (serverMessage.startsWith("Server> ")) {
                    serverMessage = serverMessage.substring(8);
                }

                // Display server message to user
                System.out.println(serverMessage);

                // Check for session end conditions
                if (serverMessage.startsWith("Final Score:") ||
                        serverMessage.contains("Goodbye!")) {
                    break;
                }

                // Handle user input for questions or commands
                if (serverMessage.contains("(a/b/c/d)") ||
                        serverMessage.contains("Commands:") ||
                        serverMessage.contains("Type 'quiz'")) {
                    String userResponse = scanner.nextLine();
                    out.println(userResponse);
                }
            }

        } catch (UnknownHostException e) {
            LOGGER.severe("Server not found: " + config.getHost());
        } catch (IOException e) {
            LOGGER.severe("Error connecting to server: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Performs cleanup by closing all open resources
     * Called when the quiz session ends or encounters an error
     */
    private void cleanup() {
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null)
                socket.close();
            LOGGER.info("Disconnected from quiz server");
        } catch (IOException e) {
            LOGGER.warning("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Entry point of the application
     * Creates a new QuizClient instance and starts the quiz
     */
    public static void main(String[] args) {
        QuizClient client = new QuizClient();
        client.startQuiz();
    }
}