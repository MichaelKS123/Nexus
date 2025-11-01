// Pulse - Real-Time Chat Application
// Created by Michael Semera
// ChatServer.java

package com.michaelsemera.pulse;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatServer {
    
    private static final int PORT = 5000;
    private static final int MAX_CLIENTS = 50;
    
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, ClientHandler> connectedClients;
    private Map<String, String> userDatabase;
    private List<ChatMessage> messageHistory;
    private boolean isRunning;
    
    public ChatServer() {
        connectedClients = new ConcurrentHashMap<>();
        userDatabase = new ConcurrentHashMap<>();
        messageHistory = new CopyOnWriteArrayList<>();
        threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
        isRunning = false;
        
        // Initialize with sample users
        initializeUsers();
    }
    
    private void initializeUsers() {
        // Sample users (in production, use encrypted passwords and database)
        userDatabase.put("admin", "admin123");
        userDatabase.put("alice", "password");
        userDatabase.put("bob", "password");
        userDatabase.put("charlie", "password");
        
        System.out.println("User database initialized with sample users");
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            isRunning = true;
            
            printBanner();
            System.out.println("🚀 Pulse Chat Server started on port " + PORT);
            System.out.println("⏰ " + getCurrentTimestamp());
            System.out.println("👥 Waiting for clients...\n");
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    threadPool.execute(clientHandler);
                    
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }
    
    private void printBanner() {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║         💬 PULSE CHAT SERVER        ║");
        System.out.println("║    Real-Time Messaging Platform     ║");
        System.out.println("║      Created by Michael Semera      ║");
        System.out.println("╚══════════════════════════════════════╝\n");
    }
    
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );
    }
    
    public void shutdown() {
        isRunning = false;
        
        try {
            System.out.println("\n🛑 Shutting down server...");
            
            // Close all client connections
            for (ClientHandler client : connectedClients.values()) {
                client.disconnect();
            }
            connectedClients.clear();
            
            // Shutdown thread pool
            threadPool.shutdown();
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            
            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            System.out.println("✅ Server shut down successfully");
            
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }
    
    private synchronized boolean authenticateUser(String username, String password) {
        return userDatabase.containsKey(username) && 
               userDatabase.get(username).equals(password);
    }
    
    private synchronized boolean registerUser(String username, String password) {
        if (userDatabase.containsKey(username)) {
            return false; // Username already exists
        }
        userDatabase.put(username, password);
        return true;
    }
    
    private synchronized boolean isUserOnline(String username) {
        return connectedClients.containsKey(username);
    }
    
    private synchronized void broadcastMessage(ChatMessage message, String excludeUser) {
        for (Map.Entry<String, ClientHandler> entry : connectedClients.entrySet()) {
            if (!entry.getKey().equals(excludeUser)) {
                entry.getValue().sendMessage(message);
            }
        }
    }
    
    private synchronized void sendUserList(ClientHandler client) {
        List<String> users = new ArrayList<>(connectedClients.keySet());
        ChatMessage userListMsg = new ChatMessage(
            MessageType.USER_LIST,
            "SERVER",
            String.join(",", users),
            getCurrentTimestamp()
        );
        client.sendMessage(userListMsg);
    }
    
    private synchronized void notifyUserJoined(String username) {
        ChatMessage joinMsg = new ChatMessage(
            MessageType.USER_JOINED,
            "SERVER",
            username + " has joined the chat",
            getCurrentTimestamp()
        );
        broadcastMessage(joinMsg, username);
        
        // Update user list for all clients
        for (ClientHandler client : connectedClients.values()) {
            sendUserList(client);
        }
    }
    
    private synchronized void notifyUserLeft(String username) {
        ChatMessage leaveMsg = new ChatMessage(
            MessageType.USER_LEFT,
            "SERVER",
            username + " has left the chat",
            getCurrentTimestamp()
        );
        broadcastMessage(leaveMsg, username);
        
        // Update user list for all clients
        for (ClientHandler client : connectedClients.values()) {
            sendUserList(client);
        }
    }
    
    private synchronized void addToHistory(ChatMessage message) {
        messageHistory.add(message);
        
        // Keep only last 100 messages
        if (messageHistory.size() > 100) {
            messageHistory.remove(0);
        }
    }
    
    private synchronized void sendMessageHistory(ClientHandler client) {
        for (ChatMessage msg : messageHistory) {
            if (msg.getType() == MessageType.CHAT) {
                client.sendMessage(msg);
            }
        }
    }
    
    // Inner class to handle individual client connections
    private class ClientHandler implements Runnable {
        
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;
        private boolean isAuthenticated;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.isAuthenticated = false;
        }
        
        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                
                System.out.println("📡 New client connected from: " + 
                    socket.getInetAddress().getHostAddress());
                
                // Authentication loop
                if (!handleAuthentication()) {
                    disconnect();
                    return;
                }
                
                // Add to connected clients
                connectedClients.put(username, this);
                notifyUserJoined(username);
                
                // Send message history
                sendMessageHistory(this);
                
                // Message handling loop
                while (isRunning && !socket.isClosed()) {
                    try {
                        ChatMessage message = (ChatMessage) in.readObject();
                        handleMessage(message);
                        
                    } catch (ClassNotFoundException e) {
                        System.err.println("Invalid message format from " + username);
                        break;
                    }
                }
                
            } catch (IOException e) {
                System.out.println("🔌 Client disconnected: " + 
                    (username != null ? username : "Unknown"));
                
            } finally {
                disconnect();
            }
        }
        
        private boolean handleAuthentication() throws IOException {
            try {
                // Send authentication request
                ChatMessage authRequest = new ChatMessage(
                    MessageType.AUTH_REQUEST,
                    "SERVER",
                    "Please authenticate",
                    getCurrentTimestamp()
                );
                sendMessage(authRequest);
                
                // Wait for authentication response (with timeout)
                socket.setSoTimeout(30000); // 30 seconds timeout
                
                ChatMessage response = (ChatMessage) in.readObject();
                
                if (response.getType() == MessageType.LOGIN) {
                    return handleLogin(response);
                    
                } else if (response.getType() == MessageType.REGISTER) {
                    return handleRegister(response);
                }
                
                return false;
                
            } catch (SocketTimeoutException e) {
                System.out.println("⏰ Authentication timeout");
                return false;
                
            } catch (ClassNotFoundException | IOException e) {
                System.err.println("Authentication error: " + e.getMessage());
                return false;
            }
        }
        
        private boolean handleLogin(ChatMessage message) {
            String[] credentials = message.getContent().split(":");
            
            if (credentials.length != 2) {
                sendAuthResult(false, "Invalid credentials format");
                return false;
            }
            
            String user = credentials[0];
            String pass = credentials[1];
            
            if (isUserOnline(user)) {
                sendAuthResult(false, "User already logged in");
                return false;
            }
            
            if (authenticateUser(user, pass)) {
                this.username = user;
                this.isAuthenticated = true;
                sendAuthResult(true, "Login successful");
                
                System.out.println("✅ User authenticated: " + username);
                return true;
            } else {
                sendAuthResult(false, "Invalid username or password");
                return false;
            }
        }
        
        private boolean handleRegister(ChatMessage message) {
            String[] credentials = message.getContent().split(":");
            
            if (credentials.length != 2) {
                sendAuthResult(false, "Invalid credentials format");
                return false;
            }
            
            String user = credentials[0];
            String pass = credentials[1];
            
            if (registerUser(user, pass)) {
                this.username = user;
                this.isAuthenticated = true;
                sendAuthResult(true, "Registration successful");
                
                System.out.println("📝 New user registered: " + username);
                return true;
            } else {
                sendAuthResult(false, "Username already exists");
                return false;
            }
        }
        
        private void sendAuthResult(boolean success, String message) {
            ChatMessage result = new ChatMessage(
                success ? MessageType.AUTH_SUCCESS : MessageType.AUTH_FAILURE,
                "SERVER",
                message,
                getCurrentTimestamp()
            );
            sendMessage(result);
        }
        
        private void handleMessage(ChatMessage message) {
            if (!isAuthenticated) {
                return;
            }
            
            switch (message.getType()) {
                case CHAT:
                    handleChatMessage(message);
                    break;
                    
                case PRIVATE_MESSAGE:
                    handlePrivateMessage(message);
                    break;
                    
                case TYPING:
                    broadcastTypingStatus(message);
                    break;
                    
                case DISCONNECT:
                    disconnect();
                    break;
                    
                default:
                    System.out.println("Unknown message type from " + username);
            }
        }
        
        private void handleChatMessage(ChatMessage message) {
            // Add sender information and timestamp
            message.setSender(username);
            message.setTimestamp(getCurrentTimestamp());
            
            System.out.println("💬 [" + username + "]: " + message.getContent());
            
            // Add to history
            addToHistory(message);
            
            // Broadcast to all clients
            broadcastMessage(message, null);
        }
        
        private void handlePrivateMessage(ChatMessage message) {
            String[] parts = message.getContent().split(":", 2);
            if (parts.length != 2) {
                return;
            }
            
            String recipient = parts[0];
            String content = parts[1];
            
            ClientHandler recipientHandler = connectedClients.get(recipient);
            if (recipientHandler != null) {
                ChatMessage privateMsg = new ChatMessage(
                    MessageType.PRIVATE_MESSAGE,
                    username,
                    content,
                    getCurrentTimestamp()
                );
                recipientHandler.sendMessage(privateMsg);
                
                // Send confirmation to sender
                ChatMessage confirmation = new ChatMessage(
                    MessageType.PRIVATE_MESSAGE,
                    "You",
                    "→ " + recipient + ": " + content,
                    getCurrentTimestamp()
                );
                sendMessage(confirmation);
                
                System.out.println("📨 Private message: " + username + " → " + recipient);
            } else {
                ChatMessage error = new ChatMessage(
                    MessageType.ERROR,
                    "SERVER",
                    "User " + recipient + " not found",
                    getCurrentTimestamp()
                );
                sendMessage(error);
            }
        }
        
        private void broadcastTypingStatus(ChatMessage message) {
            broadcastMessage(message, null);
        }
        
        public void sendMessage(ChatMessage message) {
            try {
                out.writeObject(message);
                out.flush();
                out.reset(); // Prevent memory leak
                
            } catch (IOException e) {
                System.err.println("Error sending message to " + username + ": " + e.getMessage());
                disconnect();
            }
        }
        
        public void disconnect() {
            try {
                if (username != null && connectedClients.containsKey(username)) {
                    connectedClients.remove(username);
                    notifyUserLeft(username);
                    System.out.println("👋 User disconnected: " + username);
                }
                
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                
            } catch (IOException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚠️  Shutdown signal received");
            server.shutdown();
        }));
        
        server.start();
    }
}