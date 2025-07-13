package com.minecraft.ai.brain.websocket;

import com.minecraft.ai.companion.core.websocket.AIWebSocketServer;
import com.minecraft.ai.companion.core.protocol.BaseMessage;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WebSocket server manager for communication with Node.js Mineflayer bot
 * Uses AIWebSocketServer for actual WebSocket functionality
 */
public class WebSocketServerManager {
    
    /**
     * Simple response message implementation
     */
    private static class ResponseMessage extends BaseMessage {
        public ResponseMessage(Object payload, String correlationId) {
            super(MessageType.RESPONSE, payload, correlationId);
        }
    }
    
    private final JavaPlugin plugin;
    private final String host;
    private final int port;
    private AIWebSocketServer webSocketServer;
    private boolean isRunning = false;
    
    public WebSocketServerManager(JavaPlugin plugin, String host, int port) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
    }
    
    public void start() {
        if (isRunning) {
            plugin.getLogger().warning("WebSocket server is already running");
            return;
        }
        
        try {
            plugin.getLogger().info("Starting WebSocket server on " + host + ":" + port + "...");
            
            // Create and start the WebSocket server
            webSocketServer = new AIWebSocketServer(host, port, 60000); // 60 second timeout
            
            // Start server with 15 second timeout
            boolean started = webSocketServer.startServer(15);
            
            if (started) {
                isRunning = true;
                plugin.getLogger().info("WebSocket server started successfully on " + host + ":" + port);
                
                // Register default command handlers for Minecraft integration
                registerMinecraftHandlers();
            } else {
                plugin.getLogger().severe("Failed to start WebSocket server on " + host + ":" + port);
                webSocketServer = null;
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error starting WebSocket server: " + e.getMessage());
            e.printStackTrace();
            webSocketServer = null;
            isRunning = false;
        }
    }
    
    public void shutdown() {
        if (!isRunning || webSocketServer == null) {
            plugin.getLogger().info("WebSocket server is not running");
            return;
        }
        
        try {
            plugin.getLogger().info("Stopping WebSocket server...");
            webSocketServer.stopServer(10); // 10 second timeout
            webSocketServer = null;
            isRunning = false;
            plugin.getLogger().info("WebSocket server stopped successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("Error stopping WebSocket server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void checkConnectionStatus() {
        if (webSocketServer == null) {
            plugin.getLogger().info("WebSocket server status: Not running");
            return;
        }
        
        AIWebSocketServer.ServerStats stats = webSocketServer.getServerStats();
        plugin.getLogger().info("WebSocket server status: " + 
            (isRunning ? "Running" : "Stopped") + 
            " | Active connections: " + stats.getActiveConnections() +
            " | Total connections: " + stats.getTotalConnections());
    }
    
    public boolean isRunning() {
        return isRunning && webSocketServer != null && webSocketServer.isRunning();
    }
    
    public AIWebSocketServer getWebSocketServer() {
        return webSocketServer;
    }
    
    /**
     * Register Minecraft-specific command handlers
     */
    private void registerMinecraftHandlers() {
        if (webSocketServer == null) return;
        
        // Register basic status command
        webSocketServer.registerCommandHandler("status", (message, connection) -> {
            plugin.getLogger().info("Status command received from " + connection.getId());
            
            // Create status response
            java.util.Map<String, Object> statusData = new java.util.HashMap<>();
            statusData.put("status", "online");
            statusData.put("server", "minecraft");
            statusData.put("plugin_version", plugin.getDescription().getVersion());
            statusData.put("timestamp", java.time.Instant.now().toString());
            
            // Return response message
            return new ResponseMessage(statusData, message.getId());
        });
        
        // Register ping command
        webSocketServer.registerCommandHandler("ping", (message, connection) -> {
            plugin.getLogger().info("Ping received from " + connection.getId());
            
            java.util.Map<String, Object> pongData = new java.util.HashMap<>();
            pongData.put("message", "pong");
            pongData.put("timestamp", java.time.Instant.now().toString());
            
            return new ResponseMessage(pongData, message.getId());
        });
        
        plugin.getLogger().info("Minecraft command handlers registered");
    }
} 