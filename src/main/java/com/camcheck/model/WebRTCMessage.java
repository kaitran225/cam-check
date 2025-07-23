package com.camcheck.model;

import java.util.Map;

/**
 * Model class for WebRTC signaling messages
 */
public class WebRTCMessage {
    
    private String connectionId;
    private String sender;
    private String type;
    private Map<String, Object> data;
    
    public WebRTCMessage() {
        // Default constructor
    }
    
    public WebRTCMessage(String connectionId, String sender, String type, Map<String, Object> data) {
        this.connectionId = connectionId;
        this.sender = sender;
        this.type = type;
        this.data = data;
    }
    
    /**
     * Get the connection ID
     * @return Connection ID
     */
    public String getConnectionId() {
        return connectionId;
    }
    
    /**
     * Set the connection ID
     * @param connectionId Connection ID
     */
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    /**
     * Get the sender ID
     * @return Sender ID
     */
    public String getSender() {
        return sender;
    }
    
    /**
     * Set the sender ID
     * @param sender Sender ID
     */
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    /**
     * Get the message type
     * @return Message type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Set the message type
     * @param type Message type
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Get the message data
     * @return Message data
     */
    public Map<String, Object> getData() {
        return data;
    }
    
    /**
     * Set the message data
     * @param data Message data
     */
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    @Override
    public String toString() {
        return "WebRTCMessage{" +
                "connectionId='" + connectionId + '\'' +
                ", sender='" + sender + '\'' +
                ", type='" + type + '\'' +
                ", data=" + data +
                '}';
    }
} 