package com.minecraft.ai.companion.core.websocket;

import com.minecraft.ai.companion.core.protocol.BaseMessage;
import com.minecraft.ai.companion.core.protocol.CommandMessage;
import com.minecraft.ai.companion.core.protocol.MessageSerializer;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Dispatches incoming messages to appropriate handlers and manages responses
 */
public class MessageDispatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);
    
    private final Map<String, MessageHandler> commandHandlers = new ConcurrentHashMap<>();
    private final Map<String, MessageHandler> eventHandlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<BaseMessage>> pendingResponses = new ConcurrentHashMap<>();
    private final ExecutorService handlerExecutor = Executors.newCachedThreadPool();
    private final ConnectionManager connectionManager;
    
    /**
     * Message handler interface
     */
    @FunctionalInterface
    public interface MessageHandler {
        /**
         * Handle a message and optionally return a response
         * @param message The incoming message
         * @param connection The client connection
         * @return Response message, or null if no response needed
         */
        BaseMessage handle(BaseMessage message, ConnectionManager.ClientConnection connection) throws Exception;
    }
    
    /**
     * Constructor
     */
    public MessageDispatcher(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        registerDefaultHandlers();
        logger.info("MessageDispatcher initialized");
    }
    
    /**
     * Process an incoming message from a WebSocket connection
     */
    public void processMessage(WebSocket socket, String messageText) {
        ConnectionManager.ClientConnection connection = connectionManager.getConnection(socket);
        if (connection == null) {
            logger.warn("Received message from unknown connection: {}", socket.getRemoteSocketAddress());
            return;
        }
        
        handlerExecutor.submit(() -> {
            try {
                handleMessage(messageText, connection);
            } catch (Exception e) {
                logger.error("Error processing message from {}: {}", connection.getId(), e.getMessage(), e);
                sendErrorResponse(connection, null, "MESSAGE_PROCESSING_ERROR", e.getMessage());
            }
        });
    }
    
    /**
     * Handle an incoming message
     */
    private void handleMessage(String messageText, ConnectionManager.ClientConnection connection) {
        BaseMessage message;
        
        try {
            // Parse the message
            if (!MessageSerializer.isValidMessage(messageText)) {
                throw new IllegalArgumentException("Invalid message format");
            }
            
            message = MessageSerializer.deserialize(messageText);
            logger.debug("Received {} message from {}: {}", 
                    message.getType(), connection.getId(), message.getId());
            
        } catch (Exception e) {
            logger.warn("Failed to parse message from {}: {}", connection.getId(), e.getMessage());
            sendErrorResponse(connection, null, "INVALID_MESSAGE_FORMAT", e.getMessage());
            return;
        }
        
        // Update heartbeat for any message
        connectionManager.updateHeartbeat(connection.getSocket());
        
        try {
            BaseMessage response = null;
            
            switch (message.getType().toLowerCase()) {
                case "command":
                    response = handleCommand(message, connection);
                    break;
                case "response":
                    handleResponse(message, connection);
                    break;
                case "event":
                    response = handleEvent(message, connection);
                    break;
                case "ping":
                    response = handlePing(message, connection);
                    break;
                case "pong":
                    handlePong(message, connection);
                    break;
                default:
                    logger.warn("Unknown message type '{}' from {}", message.getType(), connection.getId());
                    sendErrorResponse(connection, message.getId(), "UNKNOWN_MESSAGE_TYPE", 
                            "Unsupported message type: " + message.getType());
                    return;
            }
            
            // Send response if one was generated
            if (response != null) {
                sendResponse(connection, response);
            }
            
        } catch (Exception e) {
            logger.error("Error handling message from {}: {}", connection.getId(), e.getMessage(), e);
            sendErrorResponse(connection, message.getId(), "HANDLER_ERROR", e.getMessage());
        }
    }
    
    /**
     * Handle command messages
     */
    private BaseMessage handleCommand(BaseMessage message, ConnectionManager.ClientConnection connection) {
        if (!(message instanceof CommandMessage)) {
            // Try to extract action from payload for generic messages
            Object payload = message.getPayload();
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = (Map<String, Object>) payload;
                String action = (String) payloadMap.get("action");
                if (action != null) {
                    MessageHandler handler = commandHandlers.get(action);
                    if (handler != null) {
                        try {
                            return handler.handle(message, connection);
                        } catch (Exception e) {
                            logger.error("Command handler '{}' failed: {}", action, e.getMessage(), e);
                            throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
                        }
                    } else {
                        throw new IllegalArgumentException("No handler for command: " + action);
                    }
                }
            }
            throw new IllegalArgumentException("Invalid command message format");
        }
        
        CommandMessage commandMessage = (CommandMessage) message;
        String action = commandMessage.getAction();
        
        MessageHandler handler = commandHandlers.get(action);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for command: " + action);
        }
        
        try {
            logger.info("Executing command '{}' from {}", action, connection.getId());
            return handler.handle(commandMessage, connection);
        } catch (Exception e) {
            logger.error("Command '{}' failed for {}: {}", action, connection.getId(), e.getMessage(), e);
            throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle response messages (for pending requests)
     */
    private void handleResponse(BaseMessage message, ConnectionManager.ClientConnection connection) {
        String correlationId = message.getCorrelationId();
        if (correlationId == null) {
            logger.warn("Received response without correlation ID from {}", connection.getId());
            return;
        }
        
        CompletableFuture<BaseMessage> future = pendingResponses.remove(correlationId);
        if (future != null) {
            future.complete(message);
            logger.debug("Completed pending response for {}", correlationId);
        } else {
            logger.warn("Received response for unknown request {} from {}", correlationId, connection.getId());
        }
    }
    
    /**
     * Handle event messages
     */
    private BaseMessage handleEvent(BaseMessage message, ConnectionManager.ClientConnection connection) {
        Object payload = message.getPayload();
        if (payload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = (Map<String, Object>) payload;
            String eventType = (String) payloadMap.get("eventType");
            
            if (eventType != null) {
                MessageHandler handler = eventHandlers.get(eventType);
                if (handler != null) {
                    try {
                        return handler.handle(message, connection);
                    } catch (Exception e) {
                        logger.error("Event handler '{}' failed: {}", eventType, e.getMessage(), e);
                    }
                } else {
                    logger.debug("No handler for event type: {}", eventType);
                }
            }
        }
        
        // Events typically don't require responses
        return null;
    }
    
    /**
     * Handle ping messages
     */
    private BaseMessage handlePing(BaseMessage message, ConnectionManager.ClientConnection connection) {
        logger.debug("Received ping from {}", connection.getId());
        
        // Create pong response
        String pongMessage = "{\"type\":\"pong\",\"timestamp\":\"" + 
                java.time.Instant.now().toString() + "\",\"correlationId\":\"" + 
                message.getId() + "\"}";
        
        try {
            connection.getSocket().send(pongMessage);
        } catch (Exception e) {
            logger.warn("Failed to send pong to {}: {}", connection.getId(), e.getMessage());
        }
        
        return null; // Already sent response directly
    }
    
    /**
     * Handle pong messages
     */
    private void handlePong(BaseMessage message, ConnectionManager.ClientConnection connection) {
        logger.debug("Received pong from {}", connection.getId());
        // Pong just confirms the connection is alive - heartbeat is already updated
    }
    
    /**
     * Register a command handler
     */
    public void registerCommandHandler(String action, MessageHandler handler) {
        commandHandlers.put(action, handler);
        logger.info("Registered command handler for: {}", action);
    }
    
    /**
     * Register an event handler
     */
    public void registerEventHandler(String eventType, MessageHandler handler) {
        eventHandlers.put(eventType, handler);
        logger.info("Registered event handler for: {}", eventType);
    }
    
    /**
     * Unregister a command handler
     */
    public void unregisterCommandHandler(String action) {
        commandHandlers.remove(action);
        logger.info("Unregistered command handler for: {}", action);
    }
    
    /**
     * Unregister an event handler
     */
    public void unregisterEventHandler(String eventType) {
        eventHandlers.remove(eventType);
        logger.info("Unregistered event handler for: {}", eventType);
    }
    
    /**
     * Send a response message
     */
    private void sendResponse(ConnectionManager.ClientConnection connection, BaseMessage response) {
        try {
            String responseJson = MessageSerializer.serialize(response);
            connection.getSocket().send(responseJson);
            logger.debug("Sent response to {}: {}", connection.getId(), response.getId());
        } catch (Exception e) {
            logger.error("Failed to send response to {}: {}", connection.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Send an error response
     */
    private void sendErrorResponse(ConnectionManager.ClientConnection connection, String correlationId, 
                                 String errorCode, String errorMessage) {
        try {
            String errorResponse = String.format(
                    "{\"type\":\"response\",\"id\":\"%s\",\"timestamp\":\"%s\",\"version\":\"1.0.0\"," +
                    "\"correlationId\":\"%s\",\"payload\":{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}}",
                    java.util.UUID.randomUUID().toString(),
                    java.time.Instant.now().toString(),
                    correlationId != null ? correlationId : "unknown",
                    errorCode,
                    errorMessage.replace("\"", "\\\"")
            );
            
            if (connection != null && connection.getSocket().isOpen()) {
                connection.getSocket().send(errorResponse);
                logger.debug("Sent error response to {}: {}", connection.getId(), errorCode);
            }
        } catch (Exception e) {
            logger.error("Failed to send error response: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Register default system handlers
     */
    private void registerDefaultHandlers() {
        // Status command handler
        registerCommandHandler("status", (message, connection) -> {
            ConnectionManager.ConnectionStats stats = connectionManager.getStats();
            String statusResponse = String.format(
                    "{\"type\":\"response\",\"id\":\"%s\",\"timestamp\":\"%s\",\"version\":\"1.0.0\"," +
                    "\"correlationId\":\"%s\",\"payload\":{\"success\":true,\"result\":{" +
                    "\"connectionId\":\"%s\",\"connected\":%d,\"stats\":%s}}}",
                    java.util.UUID.randomUUID().toString(),
                    java.time.Instant.now().toString(),
                    message.getId(),
                    connection.getId(),
                    connection.getConnectionDuration(),
                    stats.toString()
            );
            
            try {
                return MessageSerializer.deserialize(statusResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create status response", e);
            }
        });
        
        // Debug command handler
        registerCommandHandler("debug", (message, connection) -> {
            String debugInfo = String.format(
                    "Connection: %s, Handlers: %d commands, %d events, Pending: %d",
                    connection.toString(),
                    commandHandlers.size(),
                    eventHandlers.size(),
                    pendingResponses.size()
            );
            
            String debugResponse = String.format(
                    "{\"type\":\"response\",\"id\":\"%s\",\"timestamp\":\"%s\",\"version\":\"1.0.0\"," +
                    "\"correlationId\":\"%s\",\"payload\":{\"success\":true,\"result\":{\"debug\":\"%s\"}}}",
                    java.util.UUID.randomUUID().toString(),
                    java.time.Instant.now().toString(),
                    message.getId(),
                    debugInfo.replace("\"", "\\\"")
            );
            
            try {
                return MessageSerializer.deserialize(debugResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create debug response", e);
            }
        });
    }
    
    /**
     * Get dispatcher statistics
     */
    public DispatcherStats getStats() {
        return new DispatcherStats(
                commandHandlers.size(),
                eventHandlers.size(),
                pendingResponses.size()
        );
    }
    
    /**
     * Shutdown the message dispatcher
     */
    public void shutdown() {
        logger.info("Shutting down MessageDispatcher...");
        
        // Cancel all pending responses
        for (CompletableFuture<BaseMessage> future : pendingResponses.values()) {
            future.cancel(true);
        }
        pendingResponses.clear();
        
        handlerExecutor.shutdown();
        try {
            if (!handlerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                handlerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            handlerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("MessageDispatcher shutdown complete");
    }
    
    /**
     * Dispatcher statistics holder
     */
    public static class DispatcherStats {
        private final int commandHandlers;
        private final int eventHandlers;
        private final int pendingResponses;
        
        public DispatcherStats(int commandHandlers, int eventHandlers, int pendingResponses) {
            this.commandHandlers = commandHandlers;
            this.eventHandlers = eventHandlers;
            this.pendingResponses = pendingResponses;
        }
        
        public int getCommandHandlers() { return commandHandlers; }
        public int getEventHandlers() { return eventHandlers; }
        public int getPendingResponses() { return pendingResponses; }
        
        @Override
        public String toString() {
            return String.format("DispatcherStats{commands=%d, events=%d, pending=%d}", 
                    commandHandlers, eventHandlers, pendingResponses);
        }
    }
} 