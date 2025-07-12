package com.minecraft.ai.companion.core.websocket;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket server for AI companion communication
 * Handles connections, message routing, and client management
 */
public class AIWebSocketServer extends WebSocketServer {
    
    private static final Logger logger = LoggerFactory.getLogger(AIWebSocketServer.class);
    
    private final ConnectionManager connectionManager;
    private final MessageDispatcher messageDispatcher;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final CountDownLatch startupLatch = new CountDownLatch(1);
    
    // Configuration
    private final int port;
    private final String host;
    private final int connectionLostTimeout;
    
    /**
     * Constructor with default configuration
     */
    public AIWebSocketServer(int port) throws UnknownHostException {
        this("localhost", port, 60000); // 60 second connection timeout
    }
    
    /**
     * Constructor with custom configuration
     */
    public AIWebSocketServer(String host, int port, int connectionLostTimeoutMs) throws UnknownHostException {
        super(new InetSocketAddress(host, port));
        
        this.host = host;
        this.port = port;
        this.connectionLostTimeout = connectionLostTimeoutMs;
        
        // Set connection lost timeout
        this.setConnectionLostTimeout(connectionLostTimeoutMs / 1000);
        
        // Initialize managers
        this.connectionManager = new ConnectionManager();
        this.messageDispatcher = new MessageDispatcher(connectionManager);
        
        logger.info("AIWebSocketServer initialized on {}:{} (timeout: {}ms)", 
                host, port, connectionLostTimeoutMs);
    }
    
    /**
     * Start the WebSocket server
     */
    public boolean startServer() {
        return startServer(10); // 10 second startup timeout
    }
    
    /**
     * Start the WebSocket server with timeout
     */
    public boolean startServer(int timeoutSeconds) {
        if (isRunning.get()) {
            logger.warn("Server is already running");
            return true;
        }
        
        try {
            logger.info("Starting WebSocket server on {}:{}...", host, port);
            
            // Start the server in a separate thread
            new Thread(() -> {
                try {
                    this.start();
                } catch (Exception e) {
                    logger.error("Failed to start WebSocket server", e);
                    startupLatch.countDown();
                }
            }, "WebSocket-Server-Thread").start();
            
            // Wait for startup confirmation
            boolean started = startupLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (started && isRunning.get()) {
                logger.info("WebSocket server started successfully on {}:{}", host, port);
                return true;
            } else {
                logger.error("WebSocket server failed to start within {} seconds", timeoutSeconds);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error starting WebSocket server", e);
            return false;
        }
    }
    
    /**
     * Stop the WebSocket server
     */
    public void stopServer() {
        stopServer(10); // 10 second shutdown timeout
    }
    
    /**
     * Stop the WebSocket server with timeout
     */
    public void stopServer(int timeoutSeconds) {
        if (!isRunning.get()) {
            logger.info("Server is not running");
            return;
        }
        
        logger.info("Stopping WebSocket server...");
        isRunning.set(false);
        
        try {
            // Shutdown components
            messageDispatcher.shutdown();
            connectionManager.shutdown();
            
            // Stop the server
            this.stop(timeoutSeconds * 1000);
            
            logger.info("WebSocket server stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error stopping WebSocket server", e);
        }
    }
    
    /**
     * Called when server starts successfully
     */
    @Override
    public void onStart() {
        isRunning.set(true);
        startupLatch.countDown();
        logger.info("WebSocket server is now accepting connections on {}:{}", host, port);
    }
    
    /**
     * Called when a new client connects
     */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        try {
            String clientInfo = String.format("%s (%s)", 
                    conn.getRemoteSocketAddress().toString(),
                    handshake.getResourceDescriptor());
            
            String connectionId = connectionManager.addConnection(conn, clientInfo);
            
            logger.info("New client connected: {} from {}", connectionId, clientInfo);
            
            // Send welcome message
            sendWelcomeMessage(conn, connectionId);
            
        } catch (Exception e) {
            logger.error("Error handling new connection", e);
            try {
                conn.close();
            } catch (Exception closeEx) {
                logger.debug("Error closing connection after onOpen error", closeEx);
            }
        }
    }
    
    /**
     * Called when a client disconnects
     */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        try {
            ConnectionManager.ClientConnection connection = connectionManager.removeConnection(conn);
            
            if (connection != null) {
                logger.info("Client disconnected: {} (Code: {}, Reason: '{}', Remote: {}, Duration: {}s)", 
                        connection.getId(), code, reason, remote, connection.getConnectionDuration());
            } else {
                logger.info("Unknown client disconnected from {} (Code: {}, Reason: '{}')", 
                        conn.getRemoteSocketAddress(), code, reason);
            }
            
        } catch (Exception e) {
            logger.error("Error handling client disconnect", e);
        }
    }
    
    /**
     * Called when a message is received from a client
     */
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            logger.debug("Received message from {}: {} chars", 
                    conn.getRemoteSocketAddress(), message.length());
            
            // Delegate to message dispatcher
            messageDispatcher.processMessage(conn, message);
            
        } catch (Exception e) {
            logger.error("Error processing message from {}: {}", 
                    conn.getRemoteSocketAddress(), e.getMessage(), e);
        }
    }
    
    /**
     * Called when an error occurs
     */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            ConnectionManager.ClientConnection connection = connectionManager.getConnection(conn);
            String connectionId = connection != null ? connection.getId() : "unknown";
            
            logger.error("WebSocket error for connection {}: {}", connectionId, ex.getMessage(), ex);
            
            // Try to close the connection gracefully
            try {
                conn.close();
            } catch (Exception closeEx) {
                logger.debug("Error closing connection after error", closeEx);
            }
            
        } else {
            logger.error("WebSocket server error: {}", ex.getMessage(), ex);
        }
    }
    
    /**
     * Send welcome message to new connection
     */
    private void sendWelcomeMessage(WebSocket conn, String connectionId) {
        try {
            String welcomeMessage = String.format(
                    "{\"type\":\"event\",\"id\":\"%s\",\"timestamp\":\"%s\",\"version\":\"1.0.0\"," +
                    "\"payload\":{\"eventType\":\"welcome\",\"data\":{\"connectionId\":\"%s\"," +
                    "\"serverVersion\":\"1.0.0\",\"message\":\"Welcome to Minecraft AI Companion\"}}}",
                    java.util.UUID.randomUUID().toString(),
                    java.time.Instant.now().toString(),
                    connectionId
            );
            
            conn.send(welcomeMessage);
            logger.debug("Sent welcome message to {}", connectionId);
            
        } catch (Exception e) {
            logger.warn("Failed to send welcome message to {}: {}", connectionId, e.getMessage());
        }
    }
    
    /**
     * Broadcast a message to all connected clients
     */
    public int broadcastMessage(String message) {
        return connectionManager.broadcastMessage(message);
    }
    
    /**
     * Send a message to a specific client
     */
    public boolean sendToClient(String connectionId, String message) {
        return connectionManager.sendMessage(connectionId, message);
    }
    
    /**
     * Get connection manager for direct access
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    /**
     * Get message dispatcher for handler registration
     */
    public MessageDispatcher getMessageDispatcher() {
        return messageDispatcher;
    }
    
    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Get server configuration info
     */
    public ServerInfo getServerInfo() {
        return new ServerInfo(host, port, connectionLostTimeout, isRunning.get());
    }
    
    /**
     * Get comprehensive server statistics
     */
    public ServerStats getServerStats() {
        ConnectionManager.ConnectionStats connStats = connectionManager.getStats();
        MessageDispatcher.DispatcherStats dispStats = messageDispatcher.getStats();
        
        return new ServerStats(
                connStats.getTotalConnections(),
                connStats.getActiveConnections(),
                connStats.getAliveConnections(),
                dispStats.getCommandHandlers(),
                dispStats.getEventHandlers(),
                dispStats.getPendingResponses()
        );
    }
    
    /**
     * Register a command handler
     */
    public void registerCommandHandler(String action, MessageDispatcher.MessageHandler handler) {
        messageDispatcher.registerCommandHandler(action, handler);
    }
    
    /**
     * Register an event handler
     */
    public void registerEventHandler(String eventType, MessageDispatcher.MessageHandler handler) {
        messageDispatcher.registerEventHandler(eventType, handler);
    }
    
    /**
     * Server configuration information
     */
    public static class ServerInfo {
        private final String host;
        private final int port;
        private final int connectionTimeout;
        private final boolean running;
        
        public ServerInfo(String host, int port, int connectionTimeout, boolean running) {
            this.host = host;
            this.port = port;
            this.connectionTimeout = connectionTimeout;
            this.running = running;
        }
        
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getConnectionTimeout() { return connectionTimeout; }
        public boolean isRunning() { return running; }
        
        @Override
        public String toString() {
            return String.format("ServerInfo{host='%s', port=%d, timeout=%dms, running=%s}", 
                    host, port, connectionTimeout, running);
        }
    }
    
    /**
     * Server statistics information
     */
    public static class ServerStats {
        private final long totalConnections;
        private final int activeConnections;
        private final int aliveConnections;
        private final int commandHandlers;
        private final int eventHandlers;
        private final int pendingResponses;
        
        public ServerStats(long totalConnections, int activeConnections, int aliveConnections,
                          int commandHandlers, int eventHandlers, int pendingResponses) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.aliveConnections = aliveConnections;
            this.commandHandlers = commandHandlers;
            this.eventHandlers = eventHandlers;
            this.pendingResponses = pendingResponses;
        }
        
        public long getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getAliveConnections() { return aliveConnections; }
        public int getCommandHandlers() { return commandHandlers; }
        public int getEventHandlers() { return eventHandlers; }
        public int getPendingResponses() { return pendingResponses; }
        
        @Override
        public String toString() {
            return String.format("ServerStats{total=%d, active=%d, alive=%d, commands=%d, events=%d, pending=%d}", 
                    totalConnections, activeConnections, aliveConnections, 
                    commandHandlers, eventHandlers, pendingResponses);
        }
    }
} 