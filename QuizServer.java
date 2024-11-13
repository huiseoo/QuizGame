import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

/**
 * Represents a single quiz question with its metadata
 */
class QuizItem {
    private final int number; // Question number/ID
    private final String question; // Question text
    private final String answer; // Correct answer
    private final int score; // Points awarded for correct answer

    // Constructor initializes all quiz item properties
    public QuizItem(int number, String question, String answer, int score) {
        this.number = number;
        this.question = question;
        this.answer = answer;
        this.score = score;
    }

    // Getter methods for accessing quiz item properties
    public int getNumber() {
        return number;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public int getScore() {
        return score;
    }

    @Override
    public String toString() {
        return String.format("%s", question);
    }
}

/**
 * Manages a collection of quiz items and tracks which questions have been used
 * Supports random question selection and quiz state management
 */
class Quiz implements Cloneable {
    private final List<QuizItem> quizItems; // Stores all quiz questions
    private final Set<Integer> usedQuestions; // Tracks used question numbers
    private final Random random; // For random question selection

    public Quiz() {
        this.quizItems = new ArrayList<>();
        this.usedQuestions = new HashSet<>();
        this.random = new Random();
    }

    /**
     * Adds a new quiz item to the question pool
     */
    public void addQuizItem(QuizItem item) {
        quizItems.add(item);
    }

    /**
     * Selects and returns a random unused quiz question
     * 
     * @return QuizItem or null if all questions have been used
     */
    public QuizItem getRandomUnusedQuiz() {
        // 사용 가능한 문제가 없으면 null 반환
        List<QuizItem> availableQuizzes = quizItems.stream()
                .filter(item -> !usedQuestions.contains(item.getNumber()))
                .collect(Collectors.toList());

        if (availableQuizzes.isEmpty()) {
            return null;
        }

        // 사용 가능한 문제 중 랜덤으로 선택
        QuizItem selectedQuiz = availableQuizzes.get(random.nextInt(availableQuizzes.size()));
        usedQuestions.add(selectedQuiz.getNumber());
        return selectedQuiz;
    }

    /**
     * Clears the set of used questions, allowing them to be used again
     */
    public void resetUsedQuestions() {
        usedQuestions.clear();
    }

    /**
     * Creates a deep copy of the quiz for thread safety
     */
    @Override
    public Quiz clone() {
        Quiz clonedQuiz = new Quiz();
        for (QuizItem item : quizItems) {
            clonedQuiz.addQuizItem(item);
        }
        return clonedQuiz;
    }
}

/**
 * Holds server configuration settings (host and port)
 */
class ServerConfig {
    private String host;
    private int port;

    // Getter and setter methods
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

/**
 * Handles individual client connections and quiz sessions
 * Runs in its own thread to support multiple concurrent clients
 */
class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private static final int MAX_QUIZ_COUNT = 5; // Maximum questions per session

    private final Socket clientSocket;
    private final Quiz quiz;
    private QuizItem currentQuiz; // Current active question
    private int totalScore = 0; // Client's running score
    private int quizCount = 0; // Number of questions answered
    private final BufferedReader in; // Input stream from client
    private final BufferedWriter out; // Output stream to client

    /**
     * Initializes the client handler with socket and quiz instance
     */
    public ClientHandler(Socket socket, Quiz quiz) throws IOException {
        this.clientSocket = socket;
        this.quiz = quiz;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    }

    @Override
    public void run() {
        try {
            LOGGER.info("New client connected from: " + clientSocket.getInetAddress());
            sendWelcomeMessage();
            handleClientCommunication();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error handling client connection", e);
        } finally {
            closeConnection();
        }
    }

    /**
     * Main communication loop - reads and processes client messages
     */
    private void handleClientCommunication() throws IOException {
        String inputMessage;
        while ((inputMessage = in.readLine()) != null) {
            System.out.println("Received from client: " + inputMessage);
            if (!processClientMessage(inputMessage.trim())) {
                break;
            }
        }
    }

    /**
     * Processes client messages and returns false if session should end
     */
    private boolean processClientMessage(String message) throws IOException {
        switch (message.toLowerCase().trim()) {
            case "finish":
                sendMessage(String.format("Final Score: %d%nGoodbye!%n", totalScore));
                return false;
            case "quiz":
                return handleQuizRequest();
            case "a":
            case "b":
            case "c":
            case "d":
                processAnswer(message.toLowerCase());
                return true;
            default:
                sendMessage("Invalid input. Please type 'quiz' for a new question or 'finish' to exit");
                return true;
        }
    }

    /**
     * Handles a quiz request from the client
     * Returns false if session should end (max questions reached)
     */

    private int maxPossibleScore = 0;

    private boolean handleQuizRequest() throws IOException {
        if (quizCount >= MAX_QUIZ_COUNT) {
            sendMessage(String.format("You've completed all %d quizzes!", MAX_QUIZ_COUNT));
            sendMessage(String.format("Final Score: %d out of %d possible points",
                    totalScore, maxPossibleScore));
            sendMessage("Thank you for playing! Type 'finish' to exit.");
            return false;
        }

        currentQuiz = quiz.getRandomUnusedQuiz();
        if (currentQuiz != null) {
            quizCount++;
            maxPossibleScore += currentQuiz.getScore();
            sendMessage(String.format("Quiz %d/%d", quizCount, MAX_QUIZ_COUNT));
            sendMessage(currentQuiz.toString());
            sendMessage("Please select your answer (a/b/c/d):");
        } else {
            sendMessage("No more questions available.");
            sendMessage(String.format("Final Score: %d out of %d possible points",
                    totalScore, maxPossibleScore));
            sendMessage("Thank you for playing! Type 'finish' to exit.");
            return false;
        }
        return true;
    }

    /**
     * Processes an answer from the client and updates the score
     */
    private void processAnswer(String answer) throws IOException {
        if (currentQuiz == null) {
            sendMessage("Please request a quiz first by typing 'quiz'");
            return;
        }

        boolean isCorrect = answer.equalsIgnoreCase(currentQuiz.getAnswer());
        if (isCorrect) {
            totalScore += currentQuiz.getScore();
            sendMessage(String.format("Correct! You get %d points!", currentQuiz.getScore()));
            sendMessage(String.format("Total score: %d", totalScore));
        } else {
            sendMessage(String.format("Incorrect. The correct answer is %s", currentQuiz.getAnswer()));
            sendMessage(String.format("Total score: %d", totalScore));
        }

        if (quizCount == MAX_QUIZ_COUNT) {
            sendMessage(String.format("\nQuiz completed!\nFinal Score: %d out of %d possible points",
                    totalScore, maxPossibleScore));
            sendMessage("Thank you for playing! Type 'finish' to exit.");
        } else {
            sendMessage("Type 'quiz' for the next question!");
        }

        currentQuiz = null;
    }

    // Utility methods for communication and connection management
    private void sendMessage(String message) throws IOException {
        out.write("Server> " + message + "\n");
        out.flush();
    }

    private void sendWelcomeMessage() throws IOException {
        sendMessage("Welcome to the Network Quiz Server!");
        sendMessage("Commands: 'quiz' for a new question, 'finish' to exit");
    }

    private void closeConnection() {
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (clientSocket != null)
                clientSocket.close();
            LOGGER.info("Client disconnected from: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing client connection", e);
        }
    }
}

/**
 * Main server class that manages client connections and quiz system
 * Supports multiple concurrent clients using a thread pool
 */
public class QuizServer {
    private static final Logger LOGGER = Logger.getLogger(QuizServer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;
    private static final String CONFIG_FILE = "server_info.dat";

    private final ServerConfigLoader config;
    private final Quiz quiz;
    private final ExecutorService executorService;
    private volatile boolean isRunning = true;

    public QuizServer() {
        this.config = new ServerConfigLoader();
        this.quiz = initializeQuiz();
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        setupLogger();
    }

    /**
     * Configures server logging with custom format
     */
    private static void setupLogger() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%1$tF %1$tT] [%2$s] %3$s%n",
                        new Date(record.getMillis()),
                        record.getLevel(),
                        record.getMessage());
            }
        });
        rootLogger.addHandler(handler);
    }

    /**
     * Creates default server configuration file if none exists
     */
    public static void createDefaultConfigFile() {
        Properties props = new Properties();
        props.setProperty("host", "localhost");
        props.setProperty("port", "1234");

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Quiz Server Configuration");
            LOGGER.info("Created default configuration file: " + CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.severe("Failed to create configuration file: " + e.getMessage());
        }
    }

    /**
     * Initializes quiz with predefined questions about computer networking
     * Questions include both basic (10 points) and advanced (20 points) topics
     */
    private static Quiz initializeQuiz() {
        Quiz quiz = new Quiz();

        quiz.addQuizItem(new QuizItem(1,
                "What is the formula for transmission delay in Packet Switching? (L=bits, R=bits/sec)\n" +
                        "a) L*R\n" +
                        "b) L/R\n" +
                        "c) R/L\n" +
                        "d) L+R",
                "b",
                10));

        quiz.addQuizItem(new QuizItem(2,
                "Which transport layer protocol does HTTP use?\n" +
                        "a) UDP\n" +
                        "b) IP\n" +
                        "c) TCP\n" +
                        "d) DNS",
                "c",
                10));

        quiz.addQuizItem(new QuizItem(3,
                "What port number is used for SMTP (email transmission)?\n" +
                        "a) 20\n" +
                        "b) 21\n" +
                        "c) 23\n" +
                        "d) 25",
                "d",
                10));

        quiz.addQuizItem(new QuizItem(4,
                "What is the main function of DNS?\n" +
                        "a) Converting IP addresses to email addresses\n" +
                        "b) Translating hostnames to IP addresses\n" +
                        "c) Packet routing\n" +
                        "d) Data encryption",
                "b",
                10));

        quiz.addQuizItem(new QuizItem(5,
                "How many bits compose an IPv4 address?\n" +
                        "a) 16 bits\n" +
                        "b) 32 bits\n" +
                        "c) 64 bits\n" +
                        "d) 128 bits",
                "b",
                10));

        quiz.addQuizItem(new QuizItem(6,
                "What message does a host broadcast in the first step of the DHCP protocol?\n" +
                        "a) DHCP offer\n" +
                        "b) DHCP request\n" +
                        "c) DHCP discover\n" +
                        "d) DHCP ACK",
                "c",
                10));

        quiz.addQuizItem(new QuizItem(7,
                "Which of the following is NOT a characteristic of TCP?\n" +
                        "a) Reliable transmission\n" +
                        "b) Connection-oriented\n" +
                        "c) Congestion control\n" +
                        "d) Connectionless communication",
                "d",
                10));

        quiz.addQuizItem(new QuizItem(8,
                "What is the main function of a router's data plane?\n" +
                        "a) Executing routing algorithms\n" +
                        "b) Computing forwarding tables\n" +
                        "c) Packet forwarding\n" +
                        "d) Determining network policies",
                "c",
                10));

        quiz.addQuizItem(new QuizItem(9,
                "What does TTL represent in an IP datagram?\n" +
                        "a) Packet lifetime\n" +
                        "b) Maximum remaining hops\n" +
                        "c) Transmission delay time\n" +
                        "d) Packet size",
                "b",
                10));

        quiz.addQuizItem(new QuizItem(10,
                "What HTTP/1.1 feature allows multiple objects to be sent over a single TCP connection?\n" +
                        "a) Stateless HTTP\n" +
                        "b) Non-persistent HTTP\n" +
                        "c) Persistent HTTP\n" +
                        "d) Proxy HTTP",
                "c",
                10));

        // Additional difficult questions (20 points each)
        quiz.addQuizItem(new QuizItem(11,
                "In a network with a transmission rate of 2 Mbps and a propagation speed of 2 * 10^8 m/s, " +
                        "if a packet is 1500 bytes and the distance between source and destination is 8000 km, " +
                        "calculate the total delay (transmission + propagation). Choose the closest answer:\n" +
                        "a) 46 ms\n" +
                        "b) 52 ms\n" +
                        "c) 64 ms\n" +
                        "d) 78 ms",
                "a",
                20));

        quiz.addQuizItem(new QuizItem(12,
                "Given a subnet mask of 255.255.254.0 and an IP address of 192.168.5.130, " +
                        "which of the following statements is correct?\n" +
                        "a) The network can host 254 devices, and 192.168.5.131 is in the same subnet\n" +
                        "b) The network can host 510 devices, and 192.168.4.130 is in a different subnet\n" +
                        "c) The network can host 510 devices, and 192.168.5.131 is in the same subnet\n" +
                        "d) The network can host 254 devices, and 192.168.6.130 is in the same subnet",
                "c",
                20));

        quiz.addQuizItem(new QuizItem(13,
                "What is the main function of a router's data plane?\n" +
                        "a) Executing routing algorithms\n" +
                        "b) Computing forwarding tables\n" +
                        "c) Packet forwarding\n" +
                        "d) Determining network policies",
                "c",
                20));
        return quiz;
    }

    /**
     * Starts the server and begins accepting client connections
     * Each client is handled in a separate thread from the thread pool
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
            LOGGER.info("Server started on port " + config.getPort());
            LOGGER.info("Waiting for clients...");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.execute(new ClientHandler(clientSocket, quiz.clone()));
                    LOGGER.info("New client connected: " + clientSocket.getInetAddress());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error accepting client connection", e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Server failed to start", e);
        } finally {
            shutdown();
        }
    }

    /**
     * Gracefully shuts down the server and its thread pool
     */
    public void shutdown() {
        isRunning = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        LOGGER.info("Server shutdown completed");
    }

    /**
     * Entry point of the server application
     * Creates default configuration if needed and starts the server
     */
    public static void main(String[] args) {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            createDefaultConfigFile();
        }

        QuizServer server = new QuizServer();
        server.start();
    }
}