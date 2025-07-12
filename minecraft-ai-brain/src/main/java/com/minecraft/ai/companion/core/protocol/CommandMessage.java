package com.minecraft.ai.companion.core.protocol;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Command message sent from Brain to Body
 */
public class CommandMessage extends BaseMessage {
    
    /**
     * Command payload structure
     */
    public static class CommandPayload {
        @SerializedName("action")
        private String action;
        
        @SerializedName("parameters")
        private Map<String, Object> parameters;
        
        @SerializedName("timeout")
        private int timeout = 10000; // Default 10 seconds
        
        public CommandPayload(String action, Map<String, Object> parameters) {
            this.action = action;
            this.parameters = parameters;
        }
        
        public CommandPayload(String action, Map<String, Object> parameters, int timeout) {
            this.action = action;
            this.parameters = parameters;
            this.timeout = timeout;
        }
        
        // Getters
        public String getAction() { return action; }
        public Map<String, Object> getParameters() { return parameters; }
        public int getTimeout() { return timeout; }
        
        // Setters
        public void setAction(String action) { this.action = action; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        
        @Override
        public String toString() {
            return String.format("CommandPayload{action='%s', timeout=%d, parameters=%s}",
                    action, timeout, parameters);
        }
    }
    
    /**
     * Supported command actions
     */
    public enum Action {
        MOVE_TO("moveTo"),
        FOLLOW("follow"),
        STOP("stop"),
        ATTACK("attack"),
        USE("use"),
        BREAK("break"),
        PLACE("place"),
        EQUIP_ITEM("equipItem"),
        CRAFT_ITEM("craftItem"),
        DROP_ITEM("dropItem"),
        CHAT("chat"),
        WHISPER("whisper"),
        DISCONNECT("disconnect"),
        STATUS("status"),
        DEBUG("debug");
        
        private final String value;
        
        Action(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return value;
        }
    }
    
    /**
     * Constructor for CommandMessage
     */
    public CommandMessage(String action, Map<String, Object> parameters) {
        super(MessageType.COMMAND, new CommandPayload(action, parameters));
    }
    
    /**
     * Constructor with timeout
     */
    public CommandMessage(String action, Map<String, Object> parameters, int timeout) {
        super(MessageType.COMMAND, new CommandPayload(action, parameters, timeout));
    }
    
    /**
     * Constructor with Action enum
     */
    public CommandMessage(Action action, Map<String, Object> parameters) {
        super(MessageType.COMMAND, new CommandPayload(action.getValue(), parameters));
    }
    
    /**
     * Constructor with Action enum and timeout
     */
    public CommandMessage(Action action, Map<String, Object> parameters, int timeout) {
        super(MessageType.COMMAND, new CommandPayload(action.getValue(), parameters, timeout));
    }
    
    /**
     * Constructor with priority
     */
    public CommandMessage(Action action, Map<String, Object> parameters, Priority priority) {
        super(MessageType.COMMAND, new CommandPayload(action.getValue(), parameters), priority);
    }
    
    /**
     * Get the command payload (typed)
     */
    public CommandPayload getCommandPayload() {
        return (CommandPayload) getPayload();
    }
    
    /**
     * Get the action from the payload
     */
    public String getAction() {
        return getCommandPayload().getAction();
    }
    
    /**
     * Get the parameters from the payload
     */
    public Map<String, Object> getParameters() {
        return getCommandPayload().getParameters();
    }
    
    /**
     * Get the timeout from the payload
     */
    public int getTimeout() {
        return getCommandPayload().getTimeout();
    }
    
    @Override
    public String toString() {
        return String.format("CommandMessage{id='%s', action='%s', timeout=%d}",
                getId(), getAction(), getTimeout());
    }
} 