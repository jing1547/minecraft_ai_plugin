package com.minecraft.ai.companion.core.websocket;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages WebSocket client connections, heartbeats, and connection lifecycle
 */
public class ConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    
    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> socketToId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2);
    private final AtomicLong connectionCounter = new AtomicLong(0);
    
    // Configuration
    private final long heartbeatInterval;
    private final long connectionTimeout;
    
    /**
     * Connection information holder
     */
    public static class ClientConnection {
        private final String id;
        private final WebSocket socket;
        private final Instant connectedAt;
        private volatile Instant lastHeartbeat;
        private volatile boolean isAlive;
        private final String clientInfo;
        
        public ClientConnection(String id, WebSocket socket, String clientInfo) {
            this.id = id;
            this.socket = socket;
            this.clientInfo = clientInfo;
            this.connectedAt = Instant.now();
            this.lastHeartbeat = Instant.now();
            this.isAlive = true;
        }
        
        // Getters
        public String getId() { return id; }
        public WebSocket getSocket() { return socket; }
        public Instant getConnectedAt() { return connectedAt; }
        public Instant getLastHeartbeat() { return lastHeartbeat; }
        public boolean isAlive() { return isAlive; }
        public String getClientInfo() { return clientInfo; }
        
        // Update methods
        public void updateHeartbeat() {
            this.lastHeartbeat = Instant.now();
            this.isAlive = true;
        }
        
        public void markDead() {
            this.isAlive = false;
        }
        
        public long getConnectionDuration() {
            return connectedAt.until(Instant.now(), java.time.temporal.ChronoUnit.SECONDS);
        }
        
        public long getTimeSinceLastHeartbeat() {
            return lastHeartbeat.until(Instant.now(), java.time.temporal.ChronoUnit.SECONDS);
        }
        
        @Override
        public String toString() {
            return String.format("ClientConnection{id='%s', alive=%s, duration=%ds, lastHeartbeat=%ds ago}",
                    id, isAlive, getConnectionDuration(), getTimeSinceLastHeartbeat());
        }
    }
    
    /**
     * Constructor with default settings
     */
    public ConnectionManager() {
        this(30000, 90000); // 30s heartbeat, 90s timeout
    }
    
    /**
     * Constructor with custom settings
     */
    public ConnectionManager(long heartbeatIntervalMs, long connectionTimeoutMs) {
        this.heartbeatInterval = heartbeatIntervalMs;
        this.connectionTimeout = connectionTimeoutMs;
        
        startHeartbeatTask();
        startCleanupTask();
        
        logger.info("ConnectionManager initialized - Heartbeat: {}ms, Timeout: {}ms", 
                heartbeatInterval, connectionTimeout);
    }
    
    /**
     * Register a new client connection
     */
    public String addConnection(WebSocket socket, String clientInfo) {
        String connectionId = generateConnectionId();
        ClientConnection connection = new ClientConnection(connectionId, socket, clientInfo);
        
        connections.put(connectionId, connection);
        socketToId.put(socket, connectionId);
        
        logger.info("New client connected: {} from {} (Total: {})", 
                connectionId, socket.getRemoteSocketAddress(), connections.size());
        
        return connectionId;
    }
    
    /**
     * Remove a client connection
     */
    public ClientConnection removeConnection(WebSocket socket) {
        String connectionId = socketToId.remove(socket);
        if (connectionId != null) {
            ClientConnection connection = connections.remove(connectionId);
            if (connection != null) {
                logger.info("Client disconnected: {} (Duration: {}s, Total: {})", 
                        connectionId, connection.getConnectionDuration(), connections.size());
                return connection;
            }
        }
        return null;
    }
    
    /**
     * Update heartbeat for a connection
     */
    public boolean updateHeartbeat(WebSocket socket) {
        String connectionId = socketToId.get(socket);
        if (connectionId != null) {
            ClientConnection connection = connections.get(connectionId);
            if (connection != null) {
                connection.updateHeartbeat();
                logger.debug("Heartbeat updated for client: {}", connectionId);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get connection by socket
     */
    public ClientConnection getConnection(WebSocket socket) {
        String connectionId = socketToId.get(socket);
        return connectionId != null ? connections.get(connectionId) : null;
    }
    
    /**
     * Get connection by ID
     */
    public ClientConnection getConnection(String connectionId) {
        return connections.get(connectionId);
    }
    
    /**
     * Get all active connections
     */
    public Collection<ClientConnection> getAllConnections() {
        return connections.values();
    }
    
    /**
     * Get all alive connections
     */
    public List<ClientConnection> getAliveConnections() {
        return connections.values().stream()
                .filter(ClientConnection::isAlive)
                .collect(Collectors.toList());
    }
    
    /**
     * Broadcast message to all alive connections
     */
    public int broadcastMessage(String message) {
        List<ClientConnection> aliveConnections = getAliveConnections();
        int sent = 0;
        
        for (ClientConnection connection : aliveConnections) {
            try {
                if (connection.getSocket().isOpen()) {
                    connection.getSocket().send(message);
                    sent++;
                }
            } catch (Exception e) {
                logger.warn("Failed to send message to client {}: {}", 
                        connection.getId(), e.getMessage());
                connection.markDead();
            }
        }
        
        logger.debug("Broadcast message sent to {}/{} connections", sent, aliveConnections.size());
        return sent;
    }
    
    /**
     * Send message to specific connection
     */
    public boolean sendMessage(String connectionId, String message) {
        ClientConnection connection = connections.get(connectionId);
        if (connection != null && connection.isAlive() && connection.getSocket().isOpen()) {
            try {
                connection.getSocket().send(message);
                return true;
            } catch (Exception e) {
                logger.warn("Failed to send message to client {}: {}", connectionId, e.getMessage());
                connection.markDead();
            }
        }
        return false;
    }
    
    /**
     * Get connection statistics
     */
    public ConnectionStats getStats() {
        long totalConnections = connectionCounter.get();
        int activeConnections = connections.size();
        int aliveConnections = getAliveConnections().size();
        
        return new ConnectionStats(totalConnections, activeConnections, aliveConnections);
    }
    
    /**
     * Generate unique connection ID
     */
    private String generateConnectionId() {
        long count = connectionCounter.incrementAndGet();
        return String.format("client-%d-%s", count, UUID.randomUUID().toString().substring(0, 8));
    }
    
    /**
     * Start heartbeat monitoring task
     */
    private void startHeartbeatTask() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeats();
            } catch (Exception e) {
                logger.error("Error in heartbeat task", e);
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Start cleanup task for dead connections
     */
    private void startCleanupTask() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupDeadConnections();
            } catch (Exception e) {
                logger.error("Error in cleanup task", e);
            }
        }, connectionTimeout, connectionTimeout / 2, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Send heartbeat ping to all connections
     */
    private void sendHeartbeats() {
        String pingMessage = "{\"type\":\"ping\",\"timestamp\":\"" + Instant.now().toString() + "\"}";
        List<ClientConnection> aliveConnections = getAliveConnections();
        
        for (ClientConnection connection : aliveConnections) {
            try {
                if (connection.getSocket().isOpen()) {
                    connection.getSocket().send(pingMessage);
                    logger.debug("Sent heartbeat to client: {}", connection.getId());
                } else {
                    connection.markDead();
                }
            } catch (Exception e) {
                logger.warn("Failed to send heartbeat to client {}: {}", 
                        connection.getId(), e.getMessage());
                connection.markDead();
            }
        }
        
        if (!aliveConnections.isEmpty()) {
            logger.debug("Sent heartbeats to {} connections", aliveConnections.size());
        }
    }
    
    /**
     * Remove connections that have timed out
     */
    private void cleanupDeadConnections() {
        Instant cutoff = Instant.now().minusMillis(connectionTimeout);
        List<ClientConnection> toRemove = connections.values().stream()
                .filter(conn -> !conn.isAlive() || conn.getLastHeartbeat().isBefore(cutoff))
                .collect(Collectors.toList());
        
        for (ClientConnection connection : toRemove) {
            try {
                if (connection.getSocket().isOpen()) {
                    connection.getSocket().close();
                }
            } catch (Exception e) {
                logger.debug("Error closing dead connection: {}", e.getMessage());
            }
            
            connections.remove(connection.getId());
            socketToId.remove(connection.getSocket());
            
            logger.info("Cleaned up dead connection: {} (inactive for {}s)", 
                    connection.getId(), connection.getTimeSinceLastHeartbeat());
        }
        
        if (!toRemove.isEmpty()) {
            logger.info("Cleaned up {} dead connections", toRemove.size());
        }
    }
    
    /**
     * Shutdown the connection manager
     */
    public void shutdown() {
        logger.info("Shutting down ConnectionManager...");
        
        // Close all connections
        for (ClientConnection connection : connections.values()) {
            try {
                if (connection.getSocket().isOpen()) {
                    connection.getSocket().close();
                }
            } catch (Exception e) {
                logger.debug("Error closing connection during shutdown: {}", e.getMessage());
            }
        }
        
        connections.clear();
        socketToId.clear();
        heartbeatExecutor.shutdown();
        
        try {
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ConnectionManager shutdown complete");
    }
    
    /**
     * Connection statistics holder
     */
    public static class ConnectionStats {
        private final long totalConnections;
        private final int activeConnections;
        private final int aliveConnections;
        
        public ConnectionStats(long totalConnections, int activeConnections, int aliveConnections) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.aliveConnections = aliveConnections;
        }
        
        public long getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getAliveConnections() { return aliveConnections; }
        
        @Override
        public String toString() {
            return String.format("ConnectionStats{total=%d, active=%d, alive=%d}", 
                    totalConnections, activeConnections, aliveConnections);
        }
    }
} 