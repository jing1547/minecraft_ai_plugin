package com.minecraft.ai.companion.core.websocket;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration management for WebSocket server
 */
public class WebSocketConfig {
    
    // Default values
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 60000; // 60 seconds
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30000; // 30 seconds
    public static final boolean DEFAULT_AUTO_START = true;
    public static final int DEFAULT_MAX_CONNECTIONS = 100;
    public static final boolean DEFAULT_LOGGING_ENABLED = true;
    
    // Configuration keys
    private static final String WEBSOCKET_SECTION = "websocket";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String CONNECTION_TIMEOUT_KEY = "connection-timeout-ms";
    private static final String HEARTBEAT_INTERVAL_KEY = "heartbeat-interval-ms";
    private static final String AUTO_START_KEY = "auto-start";
    private static final String MAX_CONNECTIONS_KEY = "max-connections";
    private static final String LOGGING_ENABLED_KEY = "logging-enabled";
    
    private final String host;
    private final int port;
    private final int connectionTimeout;
    private final int heartbeatInterval;
    private final boolean autoStart;
    private final int maxConnections;
    private final boolean loggingEnabled;
    
    /**
     * Constructor with default values
     */
    public WebSocketConfig() {
        this.host = DEFAULT_HOST;
        this.port = DEFAULT_PORT;
        this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        this.heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        this.autoStart = DEFAULT_AUTO_START;
        this.maxConnections = DEFAULT_MAX_CONNECTIONS;
        this.loggingEnabled = DEFAULT_LOGGING_ENABLED;
    }
    
    /**
     * Constructor with custom values
     */
    public WebSocketConfig(String host, int port, int connectionTimeout, int heartbeatInterval,
                          boolean autoStart, int maxConnections, boolean loggingEnabled) {
        this.host = host != null ? host : DEFAULT_HOST;
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.connectionTimeout = connectionTimeout > 0 ? connectionTimeout : DEFAULT_CONNECTION_TIMEOUT;
        this.heartbeatInterval = heartbeatInterval > 0 ? heartbeatInterval : DEFAULT_HEARTBEAT_INTERVAL;
        this.autoStart = autoStart;
        this.maxConnections = maxConnections > 0 ? maxConnections : DEFAULT_MAX_CONNECTIONS;
        this.loggingEnabled = loggingEnabled;
    }
    
    /**
     * Load configuration from Bukkit FileConfiguration
     */
    public static WebSocketConfig fromFileConfiguration(FileConfiguration config) {
        if (config == null) {
            return new WebSocketConfig();
        }
        
        ConfigurationSection wsSection = config.getConfigurationSection(WEBSOCKET_SECTION);
        if (wsSection == null) {
            return new WebSocketConfig();
        }
        
        String host = wsSection.getString(HOST_KEY, DEFAULT_HOST);
        int port = wsSection.getInt(PORT_KEY, DEFAULT_PORT);
        int connectionTimeout = wsSection.getInt(CONNECTION_TIMEOUT_KEY, DEFAULT_CONNECTION_TIMEOUT);
        int heartbeatInterval = wsSection.getInt(HEARTBEAT_INTERVAL_KEY, DEFAULT_HEARTBEAT_INTERVAL);
        boolean autoStart = wsSection.getBoolean(AUTO_START_KEY, DEFAULT_AUTO_START);
        int maxConnections = wsSection.getInt(MAX_CONNECTIONS_KEY, DEFAULT_MAX_CONNECTIONS);
        boolean loggingEnabled = wsSection.getBoolean(LOGGING_ENABLED_KEY, DEFAULT_LOGGING_ENABLED);
        
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    /**
     * Save configuration to Bukkit FileConfiguration
     */
    public void saveToFileConfiguration(FileConfiguration config) {
        if (config == null) {
            return;
        }
        
        config.set(WEBSOCKET_SECTION + "." + HOST_KEY, host);
        config.set(WEBSOCKET_SECTION + "." + PORT_KEY, port);
        config.set(WEBSOCKET_SECTION + "." + CONNECTION_TIMEOUT_KEY, connectionTimeout);
        config.set(WEBSOCKET_SECTION + "." + HEARTBEAT_INTERVAL_KEY, heartbeatInterval);
        config.set(WEBSOCKET_SECTION + "." + AUTO_START_KEY, autoStart);
        config.set(WEBSOCKET_SECTION + "." + MAX_CONNECTIONS_KEY, maxConnections);
        config.set(WEBSOCKET_SECTION + "." + LOGGING_ENABLED_KEY, loggingEnabled);
    }
    
    /**
     * Validate configuration values
     */
    public boolean isValid() {
        return host != null && !host.trim().isEmpty() &&
               port > 0 && port <= 65535 &&
               connectionTimeout > 0 &&
               heartbeatInterval > 0 &&
               maxConnections > 0;
    }
    
    /**
     * Get validation errors
     */
    public String getValidationErrors() {
        StringBuilder errors = new StringBuilder();
        
        if (host == null || host.trim().isEmpty()) {
            errors.append("Host cannot be null or empty. ");
        }
        
        if (port <= 0 || port > 65535) {
            errors.append("Port must be between 1 and 65535. ");
        }
        
        if (connectionTimeout <= 0) {
            errors.append("Connection timeout must be positive. ");
        }
        
        if (heartbeatInterval <= 0) {
            errors.append("Heartbeat interval must be positive. ");
        }
        
        if (maxConnections <= 0) {
            errors.append("Max connections must be positive. ");
        }
        
        return errors.toString().trim();
    }
    
    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public int getHeartbeatInterval() { return heartbeatInterval; }
    public boolean isAutoStart() { return autoStart; }
    public int getMaxConnections() { return maxConnections; }
    public boolean isLoggingEnabled() { return loggingEnabled; }
    
    @Override
    public String toString() {
        return String.format("WebSocketConfig{host='%s', port=%d, timeout=%dms, heartbeat=%dms, " +
                "autoStart=%s, maxConnections=%d, logging=%s}",
                host, port, connectionTimeout, heartbeatInterval, autoStart, maxConnections, loggingEnabled);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        WebSocketConfig that = (WebSocketConfig) obj;
        return port == that.port &&
               connectionTimeout == that.connectionTimeout &&
               heartbeatInterval == that.heartbeatInterval &&
               autoStart == that.autoStart &&
               maxConnections == that.maxConnections &&
               loggingEnabled == that.loggingEnabled &&
               host.equals(that.host);
    }
    
    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        result = 31 * result + connectionTimeout;
        result = 31 * result + heartbeatInterval;
        result = 31 * result + (autoStart ? 1 : 0);
        result = 31 * result + maxConnections;
        result = 31 * result + (loggingEnabled ? 1 : 0);
        return result;
    }
    
    /**
     * Create a copy of this configuration with modified values
     */
    public WebSocketConfig withHost(String host) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    public WebSocketConfig withPort(int port) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    public WebSocketConfig withConnectionTimeout(int connectionTimeout) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    public WebSocketConfig withHeartbeatInterval(int heartbeatInterval) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    public WebSocketConfig withAutoStart(boolean autoStart) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    public WebSocketConfig withMaxConnections(int maxConnections) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    public WebSocketConfig withLoggingEnabled(boolean loggingEnabled) {
        return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                autoStart, maxConnections, loggingEnabled);
    }
    
    /**
     * Create a builder for this configuration
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder pattern for WebSocketConfig
     */
    public static class Builder {
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private int heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        private boolean autoStart = DEFAULT_AUTO_START;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private boolean loggingEnabled = DEFAULT_LOGGING_ENABLED;
        
        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder connectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; return this; }
        public Builder heartbeatInterval(int heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; return this; }
        public Builder autoStart(boolean autoStart) { this.autoStart = autoStart; return this; }
        public Builder maxConnections(int maxConnections) { this.maxConnections = maxConnections; return this; }
        public Builder loggingEnabled(boolean loggingEnabled) { this.loggingEnabled = loggingEnabled; return this; }
        
        public WebSocketConfig build() {
            return new WebSocketConfig(host, port, connectionTimeout, heartbeatInterval,
                    autoStart, maxConnections, loggingEnabled);
        }
    }
} 