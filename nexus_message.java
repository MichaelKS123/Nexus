// Nexus   - ChatMessage.java and MessageType.java
// Created by Michael Semera

package com.michaelsemera.nexus  ;

import java.io.Serializable;

// ==================== MessageType Enum ====================
enum MessageType {
    // Authentication
    AUTH_REQUEST,       // Server requests authentication
    LOGIN,              // Client login attempt
    REGISTER,           // Client registration attempt
    AUTH_SUCCESS,       // Authentication successful
    AUTH_FAILURE,       // Authentication failed
    
    // Chat messages
    CHAT,               // Public chat message
    PRIVATE_MESSAGE,    // Private message to specific user
    
    // User management
    USER_JOINED,        // User joined notification
    USER_LEFT,          // User left notification
    USER_LIST,          // List of online users
    
    // Status
    TYPING,             // User typing indicator
    DISCONNECT,         // User disconnect notification
    
    // System
    ERROR,              // Error message
    SERVER_MESSAGE      // Server announcement
}

// ==================== ChatMessage Class ====================
class ChatMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private MessageType type;
    private String sender;
    private String content;
    private String timestamp;
    
    public ChatMessage(MessageType type, String sender, String content, String timestamp) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
    }
    
    // Getters
    public MessageType getType() {
        return type;
    }
    
    public String getSender() {
        return sender;
    }
    
    public String getContent() {
        return content;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    // Setters
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s (%s): %s", 
            timestamp, sender, type, content);
    }
}