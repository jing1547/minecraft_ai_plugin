package com.minecraft.ai.companion.core.protocol;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.UUID;

/**
 * Base message class for all WebSocket communication between Brain and Body
 */
public abstract class BaseMessage {
    
    @SerializedName("type")
    private final String type;
    
    @SerializedName("id")
    private final String id;
    
    @SerializedName("timestamp")
    private final String timestamp;
    
    @SerializedName("version")
    private final String version;
    
    @SerializedName("correlationId")
    private String correlationId;
    
    @SerializedName("priority")
    private Priority priority;
    
    @SerializedName("payload")
    private Object payload;
    
    /**
     * Message priority levels
     */
    public enum Priority {
        @SerializedName("low") LOW,
        @SerializedName("normal") NORMAL,
        @SerializedName("high") HIGH,
        @SerializedName("urgent") URGENT
    }
    
    /**
     * Message types
     */
    public enum MessageType {
        @SerializedName("command") COMMAND,
        @SerializedName("response") RESPONSE,
        @SerializedName("event") EVENT,
        @SerializedName("state") STATE,
        @SerializedName("ping") PING,
        @SerializedName("pong") PONG
    }
    
    /**
     * Constructor for BaseMessage
     */
    protected BaseMessage(MessageType type, Object payload) {
        this.type = type.name().toLowerCase();
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toString();
        this.version = "1.0.0";
        this.priority = Priority.NORMAL;
        this.payload = payload;
    }
    
    /**
     * Constructor with correlation ID for responses
     */
    protected BaseMessage(MessageType type, Object payload, String correlationId) {
        this(type, payload);
        this.correlationId = correlationId;
    }
    
    /**
     * Constructor with priority
     */
    protected BaseMessage(MessageType type, Object payload, Priority priority) {
        this(type, payload);
        this.priority = priority;
    }
    
    // Getters
    public String getType() { return type; }
    public String getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getVersion() { return version; }
    public String getCorrelationId() { return correlationId; }
    public Priority getPriority() { return priority; }
    public Object getPayload() { return payload; }
    
    // Setters
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public void setPayload(Object payload) { this.payload = payload; }
    
    @Override
    public String toString() {
        return String.format("BaseMessage{type='%s', id='%s', timestamp='%s', priority=%s}",
                type, id, timestamp, priority);
    }
} 