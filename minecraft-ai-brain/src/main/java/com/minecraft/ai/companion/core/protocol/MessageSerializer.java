package com.minecraft.ai.companion.core.protocol;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Message serializer for WebSocket communication
 * Handles JSON serialization/deserialization of protocol messages
 */
public class MessageSerializer {
    
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(BaseMessage.class, new BaseMessageDeserializer())
            .setPrettyPrinting()
            .create();
    
    /**
     * Serialize a message to JSON string
     */
    public static String serialize(BaseMessage message) {
        try {
            return gson.toJson(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserialize JSON string to BaseMessage
     */
    public static BaseMessage deserialize(String json) {
        try {
            return gson.fromJson(json, BaseMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deserialize JSON string to specific message type
     */
    @SuppressWarnings("unchecked")
    public static <T extends BaseMessage> T deserialize(String json, Class<T> messageType) {
        try {
            BaseMessage message = deserialize(json);
            if (messageType.isInstance(message)) {
                return (T) message;
            } else {
                throw new IllegalArgumentException("Message type mismatch: expected " + 
                        messageType.getSimpleName() + ", got " + message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message to " + 
                    messageType.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a JSON string represents a valid message
     */
    public static boolean isValidMessage(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject.has("type") && 
                   jsonObject.has("id") && 
                   jsonObject.has("timestamp") && 
                   jsonObject.has("version") && 
                   jsonObject.has("payload");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get message type from JSON without full deserialization
     */
    public static String getMessageType(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject.get("type").getAsString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract message type: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get message ID from JSON without full deserialization
     */
    public static String getMessageId(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject.get("id").getAsString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract message ID: " + e.getMessage(), e);
        }
    }
    
    /**
     * Custom deserializer for BaseMessage that handles different message types
     */
    private static class BaseMessageDeserializer implements JsonDeserializer<BaseMessage> {
        
        @Override
        public BaseMessage deserialize(JsonElement json, Type typeOfT, 
                JsonDeserializationContext context) throws JsonParseException {
            
            JsonObject jsonObject = json.getAsJsonObject();
            String messageType = jsonObject.get("type").getAsString();
            
            switch (messageType) {
                case "command":
                    return deserializeCommandMessage(jsonObject);
                case "response":
                    return deserializeResponseMessage(jsonObject);
                case "event":
                    return deserializeEventMessage(jsonObject);
                case "state":
                    return deserializeStateMessage(jsonObject);
                case "ping":
                    return deserializePingMessage(jsonObject);
                case "pong":
                    return deserializePongMessage(jsonObject);
                default:
                    throw new JsonParseException("Unknown message type: " + messageType);
            }
        }
        
        private CommandMessage deserializeCommandMessage(JsonObject jsonObject) {
            JsonObject payload = jsonObject.getAsJsonObject("payload");
            String action = payload.get("action").getAsString();
            int timeout = payload.has("timeout") ? payload.get("timeout").getAsInt() : 10000;
            
            // Parse parameters
            Map<String, Object> parameters = gson.fromJson(
                    payload.get("parameters"),
                    new TypeToken<Map<String, Object>>(){}.getType()
            );
            
            CommandMessage message = new CommandMessage(action, parameters, timeout);
            
            // Set optional fields
            if (jsonObject.has("correlationId")) {
                message.setCorrelationId(jsonObject.get("correlationId").getAsString());
            }
            if (jsonObject.has("priority")) {
                String priorityStr = jsonObject.get("priority").getAsString();
                message.setPriority(BaseMessage.Priority.valueOf(priorityStr.toUpperCase()));
            }
            
            return message;
        }
        
        private BaseMessage deserializeResponseMessage(JsonObject jsonObject) {
            // For now, create a simple response message
            // This would be expanded when ResponseMessage class is created
            return new BaseMessage(BaseMessage.MessageType.RESPONSE, 
                    jsonObject.get("payload")) {
                // Anonymous implementation for now
            };
        }
        
        private BaseMessage deserializeEventMessage(JsonObject jsonObject) {
            // For now, create a simple event message
            // This would be expanded when EventMessage class is created
            return new BaseMessage(BaseMessage.MessageType.EVENT, 
                    jsonObject.get("payload")) {
                // Anonymous implementation for now
            };
        }
        
        private BaseMessage deserializeStateMessage(JsonObject jsonObject) {
            // For now, create a simple state message
            // This would be expanded when StateMessage class is created
            return new BaseMessage(BaseMessage.MessageType.STATE, 
                    jsonObject.get("payload")) {
                // Anonymous implementation for now
            };
        }
        
        private BaseMessage deserializePingMessage(JsonObject jsonObject) {
            // For ping messages, we just need the basic structure
            return new BaseMessage(BaseMessage.MessageType.PING, null) {
                // Anonymous implementation for ping
            };
        }
        
        private BaseMessage deserializePongMessage(JsonObject jsonObject) {
            // For pong messages, we just need the basic structure  
            return new BaseMessage(BaseMessage.MessageType.PONG, 
                    jsonObject.has("payload") ? jsonObject.get("payload") : null) {
                // Anonymous implementation for pong
            };
        }
    }
    
    /**
     * Utility methods for common serialization patterns
     */
    public static class Utils {
        
        /**
         * Create a simple command message JSON
         */
        public static String createCommandJson(String action, Map<String, Object> parameters) {
            CommandMessage message = new CommandMessage(action, parameters);
            return serialize(message);
        }
        
        /**
         * Create a movement command JSON
         */
        public static String createMovementCommandJson(double x, double y, double z) {
            Map<String, Object> parameters = Map.of(
                    "x", x,
                    "y", y,
                    "z", z
            );
            CommandMessage message = new CommandMessage(CommandMessage.Action.MOVE_TO, parameters);
            return serialize(message);
        }
        
        /**
         * Create a follow command JSON
         */
        public static String createFollowCommandJson(String playerName) {
            Map<String, Object> parameters = Map.of("playerName", playerName);
            CommandMessage message = new CommandMessage(CommandMessage.Action.FOLLOW, parameters);
            return serialize(message);
        }
        
        /**
         * Create a stop command JSON
         */
        public static String createStopCommandJson() {
            Map<String, Object> parameters = Map.of();
            CommandMessage message = new CommandMessage(CommandMessage.Action.STOP, parameters);
            return serialize(message);
        }
    }
} 