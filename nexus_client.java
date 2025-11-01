// Nexus  - ChatClient.java
// Created by Michael Semera
// JavaFX GUI Client for real-time chat

package com.michaelsemera.nexus ;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.Optional;

public class ChatClient extends Application {
    
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private boolean isConnected;
    
    private TextArea chatArea;
    private TextField messageField;
    private ListView<String> userListView;
    private Label statusLabel;
    private Button sendButton;
    
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Nexus  - Real-Time Chat");
        
        // Show login dialog
        if (!showLoginDialog()) {
            Platform.exit();
            return;
        }
        
        // Create main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));
        
        // Top: Header
        VBox header = createHeader();
        mainLayout.setTop(header);
        
        // Center: Chat area
        VBox centerPanel = createChatPanel();
        mainLayout.setCenter(centerPanel);
        
        // Right: User list
        VBox rightPanel = createUserListPanel();
        mainLayout.setRight(rightPanel);
        
        // Bottom: Input area
        HBox inputArea = createInputArea();
        mainLayout.setBottom(inputArea);
        
        Scene scene = new Scene(mainLayout, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/chat-styles.css").toExternalForm());
        
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> disconnect());
        primaryStage.show();
        
        // Start message receiver thread
        new Thread(new MessageReceiver()).start();
    }
    
    private VBox createHeader() {
        VBox header = new VBox(5);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 5;");
        
        Label titleLabel = new Label("💬 nexus  Chat");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);
        
        statusLabel = new Label("Connected as: " + username);
        statusLabel.setTextFill(Color.web("#1abc9c"));
        statusLabel.setFont(Font.font("Arial", 14));
        
        Label authorLabel = new Label("Created by Michael Semera");
        authorLabel.setTextFill(Color.web("#95a5a6"));
        authorLabel.setFont(Font.font("Arial", 12));
        
        header.getChildren().addAll(titleLabel, statusLabel, authorLabel);
        
        return header;
    }
    
    private VBox createChatPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        
        Label chatLabel = new Label("Chat Messages");
        chatLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-control-inner-background: #ecf0f1; " +
                         "-fx-font-family: 'Courier New'; " +
                         "-fx-font-size: 13px;");
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        
        panel.getChildren().addAll(chatLabel, chatArea);
        
        return panel;
    }
    
    private VBox createUserListPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(200);
        
        Label usersLabel = new Label("👥 Online Users");
        usersLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        
        userListView = new ListView<>();
        userListView.setStyle("-fx-background-color: #ecf0f1;");
        VBox.setVgrow(userListView, Priority.ALWAYS);
        
        // Double-click to send private message
        userListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedUser = userListView.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.equals(username)) {
                    sendPrivateMessage(selectedUser);
                }
            }
        });
        
        panel.getChildren().addAll(usersLabel, userListView);
        
        return panel;
    }
    
    private HBox createInputArea() {
        HBox inputArea = new HBox(10);
        inputArea.setPadding(new Insets(10));
        inputArea.setAlignment(Pos.CENTER);
        
        messageField = new TextField();
        messageField.setPromptText("Type your message here...");
        messageField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        messageField.setOnAction(e -> sendMessage());
        
        // Typing indicator
        messageField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty() && oldText.isEmpty()) {
                sendTypingStatus(true);
            } else if (newText.isEmpty() && !oldText.isEmpty()) {
                sendTypingStatus(false);
            }
        });
        
        sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                           "-fx-font-size: 14px; -fx-font-weight: bold; " +
                           "-fx-padding: 10 20; -fx-cursor: hand;");
        sendButton.setOnAction(e -> sendMessage());
        
        Button emojiButton = new Button("😊");
        emojiButton.setStyle("-fx-font-size: 16px; -fx-padding: 10; -fx-cursor: hand;");
        emojiButton.setOnAction(e -> showEmojiPicker());
        
        inputArea.getChildren().addAll(messageField, emojiButton, sendButton);
        
        return inputArea;
    }
    
    private boolean showLoginDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("nexus  - Login");
        dialog.setHeaderText("Welcome to Nexus  Chat\nCreated by Michael Semera");
        
        ButtonType loginButton = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        ButtonType registerButton = new ButtonType("Register", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(loginButton, registerButton, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        
        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        Platform.runLater(() -> usernameField.requestFocus());
        
        Optional<ButtonType> result = dialog.showAndWait();
        
        if (result.isPresent() && result.get() != ButtonType.CANCEL) {
            String user = usernameField.getText().trim();
            String pass = passwordField.getText();
            
            if (user.isEmpty() || pass.isEmpty()) {
                showAlert("Error", "Please enter both username and password");
                return showLoginDialog();
            }
            
            boolean isLogin = result.get() == loginButton;
            return connectToServer(user, pass, isLogin);
        }
        
        return false;
    }
    
    private boolean connectToServer(String user, String pass, boolean isLogin) {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            
            // Wait for authentication request
            ChatMessage authRequest = (ChatMessage) in.readObject();
            
            if (authRequest.getType() == MessageType.AUTH_REQUEST) {
                // Send credentials
                ChatMessage credentials = new ChatMessage(
                    isLogin ? MessageType.LOGIN : MessageType.REGISTER,
                    user,
                    user + ":" + pass,
                    ""
                );
                out.writeObject(credentials);
                out.flush();
                
                // Wait for authentication result
                ChatMessage result = (ChatMessage) in.readObject();
                
                if (result.getType() == MessageType.AUTH_SUCCESS) {
                    this.username = user;
                    this.isConnected = true;
                    showAlert("Success", result.getContent());
                    return true;
                } else {
                    showAlert("Authentication Failed", result.getContent());
                    socket.close();
                    return showLoginDialog();
                }
            }
            
        } catch (IOException | ClassNotFoundException e) {
            showAlert("Connection Error", "Could not connect to server: " + e.getMessage());
            return false;
        }
        
        return false;
    }
    
    private void sendMessage() {
        String message = messageField.getText().trim();
        
        if (message.isEmpty()) {
            return;
        }
        
        try {
            ChatMessage chatMsg = new ChatMessage(
                MessageType.CHAT,
                username,
                message,
                ""
            );
            
            out.writeObject(chatMsg);
            out.flush();
            
            messageField.clear();
            
        } catch (IOException e) {
            showAlert("Error", "Failed to send message: " + e.getMessage());
        }
    }
    
    private void sendPrivateMessage(String recipient) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Private Message");
        dialog.setHeaderText("Send private message to " + recipient);
        dialog.setContentText("Message:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(message -> {
            try {
                ChatMessage privateMsg = new ChatMessage(
                    MessageType.PRIVATE_MESSAGE,
                    username,
                    recipient + ":" + message,
                    ""
                );
                
                out.writeObject(privateMsg);
                out.flush();
                
            } catch (IOException e) {
                showAlert("Error", "Failed to send private message");
            }
        });
    }
    
    private void sendTypingStatus(boolean isTyping) {
        try {
            ChatMessage typingMsg = new ChatMessage(
                MessageType.TYPING,
                username,
                isTyping ? "typing" : "stopped",
                ""
            );
            
            out.writeObject(typingMsg);
            out.flush();
            
        } catch (IOException e) {
            // Ignore typing status errors
        }
    }
    
    private void showEmojiPicker() {
        String[] emojis = {"😊", "😂", "❤️", "👍", "🎉", "😎", "🔥", "✨", "💯", "🚀"};
        
        ChoiceDialog<String> dialog = new ChoiceDialog<>(emojis[0], emojis);
        dialog.setTitle("Emoji Picker");
        dialog.setHeaderText("Select an emoji");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(emoji -> {
            messageField.setText(messageField.getText() + emoji);
            messageField.requestFocus();
            messageField.positionCaret(messageField.getText().length());
        });
    }
    
    private void displayMessage(ChatMessage message) {
        Platform.runLater(() -> {
            String displayText = "";
            
            switch (message.getType()) {
                case CHAT:
                    displayText = String.format("[%s] %s: %s\n", 
                        message.getTimestamp(), message.getSender(), message.getContent());
                    break;
                    
                case PRIVATE_MESSAGE:
                    displayText = String.format("[%s] 📨 %s: %s\n", 
                        message.getTimestamp(), message.getSender(), message.getContent());
                    break;
                    
                case USER_JOINED:
                    displayText = String.format("✅ %s\n", message.getContent());
                    break;
                    
                case USER_LEFT:
                    displayText = String.format("❌ %s\n", message.getContent());
                    break;
                    
                case TYPING:
                    if (message.getContent().equals("typing")) {
                        statusLabel.setText(message.getSender() + " is typing...");
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                                Platform.runLater(() -> 
                                    statusLabel.setText("Connected as: " + username));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    }
                    return;
                    
                case USER_LIST:
                    updateUserList(message.getContent());
                    return;
                    
                case ERROR:
                    displayText = String.format("⚠️ ERROR: %s\n", message.getContent());
                    break;
            }
            
            chatArea.appendText(displayText);
        });
    }
    
    private void updateUserList(String userListStr) {
        Platform.runLater(() -> {
            userListView.getItems().clear();
            String[] users = userListStr.split(",");
            for (String user : users) {
                if (!user.isEmpty()) {
                    userListView.getItems().add(user);
                }
            }
        });
    }
    
    private void disconnect() {
        try {
            if (isConnected && out != null) {
                ChatMessage disconnectMsg = new ChatMessage(
                    MessageType.DISCONNECT,
                    username,
                    "disconnect",
                    ""
                );
                out.writeObject(disconnectMsg);
                out.flush();
            }
            
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
            
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
    
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private class MessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                while (isConnected && !socket.isClosed()) {
                    ChatMessage message = (ChatMessage) in.readObject();
                    displayMessage(message);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (isConnected) {
                    Platform.runLater(() -> {
                        showAlert("Connection Lost", "Disconnected from server");
                        chatArea.appendText("\n⚠️ Connection lost. Please restart the application.\n");
                    });
                }
            }
        }
    }
    
    @Override
    public void stop() {
        disconnect();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}