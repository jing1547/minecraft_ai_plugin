package com.minecraft.ai.brain.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Robust configuration manager for the AI Brain plugin
 * Handles loading, saving, validation, and migration of plugin settings
 */
public class ConfigManager {
    
    private static final String CONFIG_VERSION = "1.0.0";
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    private boolean isLoaded = false;
    
    // Configuration validation errors
    private final List<String> validationErrors = new ArrayList<>();
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }
    
    /**
     * Load configuration from file with validation and migration
     */
    public void loadConfig() {
        try {
            // Create data folder if it doesn't exist
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // Save default config if file doesn't exist
            if (!configFile.exists()) {
                plugin.saveDefaultConfig();
                plugin.getLogger().info("Created default configuration file");
            }
            
            // Load configuration
            config = YamlConfiguration.loadConfiguration(configFile);
            
            // Validate configuration
            validateConfiguration();
            
            // Handle configuration migration if needed
            handleMigration();
            
            // Mark as loaded
            isLoaded = true;
            
            plugin.getLogger().info("Configuration loaded successfully");
            if (!validationErrors.isEmpty()) {
                plugin.getLogger().warning("Configuration validation found " + validationErrors.size() + " issues:");
                for (String error : validationErrors) {
                    plugin.getLogger().warning("  - " + error);
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            
            // Fall back to default configuration
            config = new YamlConfiguration();
            setDefaultValues();
            isLoaded = true;
        }
    }
    
    /**
     * Save configuration to file
     */
    public void saveConfig() {
        if (config == null) {
            plugin.getLogger().warning("Cannot save config - configuration not loaded");
            return;
        }
        
        try {
            config.save(configFile);
            plugin.getLogger().info("Configuration saved successfully");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reload configuration from file
     */
    public void reloadConfig() {
        validationErrors.clear();
        isLoaded = false;
        loadConfig();
    }
    
    /**
     * Validate the configuration and populate validation errors
     */
    private void validateConfiguration() {
        validationErrors.clear();
        
        // Validate required sections
        validateRequiredSections();
        
        // Validate WebSocket configuration
        validateWebSocketConfig();
        
        // Validate AI configuration
        validateAIConfig();
        
        // Validate player interaction settings
        validatePlayerInteractionConfig();
        
        // Validate performance settings
        validatePerformanceConfig();
        
        // Validate logging configuration
        validateLoggingConfig();
    }
    
    /**
     * Validate that required configuration sections exist
     */
    private void validateRequiredSections() {
        String[] requiredSections = {"plugin", "websocket", "ai", "player-interaction", 
                                   "commands", "storage", "performance", "logging"};
        
        for (String section : requiredSections) {
            if (!config.contains(section)) {
                validationErrors.add("Missing required section: " + section);
            }
        }
    }
    
    /**
     * Validate WebSocket configuration
     */
    private void validateWebSocketConfig() {
        ConfigurationSection ws = config.getConfigurationSection("websocket");
        if (ws == null) return;
        
        // Validate port
        int port = ws.getInt("port", 8080);
        if (port < 1 || port > 65535) {
            validationErrors.add("WebSocket port must be between 1 and 65535 (current: " + port + ")");
        }
        
        // Validate host
        String host = ws.getString("host", "localhost");
        if (host == null || host.trim().isEmpty()) {
            validationErrors.add("WebSocket host cannot be empty");
        }
        
        // Validate timeouts
        int connectionTimeout = ws.getInt("connection-timeout-ms", 60000);
        if (connectionTimeout < 1000) {
            validationErrors.add("WebSocket connection timeout too low (minimum 1000ms)");
        }
        
        int heartbeatInterval = ws.getInt("heartbeat-interval-ms", 30000);
        if (heartbeatInterval < 5000) {
            validationErrors.add("WebSocket heartbeat interval too low (minimum 5000ms)");
        }
        
        // Validate max connections
        int maxConnections = ws.getInt("max-connections", 100);
        if (maxConnections < 1) {
            validationErrors.add("WebSocket max connections must be positive");
        }
    }
    
    /**
     * Validate AI configuration
     */
    private void validateAIConfig() {
        ConfigurationSection ai = config.getConfigurationSection("ai");
        if (ai == null) return;
        
        // Validate response timeout
        int responseTimeout = ai.getInt("response-timeout-ms", 30000);
        if (responseTimeout < 1000) {
            validationErrors.add("AI response timeout too low (minimum 1000ms)");
        }
        
        // Validate conversation history
        int maxHistory = ai.getInt("max-conversation-history", 50);
        if (maxHistory < 1) {
            validationErrors.add("AI max conversation history must be positive");
        }
        
        // Validate voice settings if enabled
        ConfigurationSection voice = ai.getConfigurationSection("voice");
        if (voice != null && voice.getBoolean("enabled", false)) {
            double threshold = voice.getDouble("voice-activation-threshold", 0.7);
            if (threshold < 0.0 || threshold > 1.0) {
                validationErrors.add("Voice activation threshold must be between 0.0 and 1.0");
            }
        }
    }
    
    /**
     * Validate player interaction configuration
     */
    private void validatePlayerInteractionConfig() {
        ConfigurationSection pi = config.getConfigurationSection("player-interaction");
        if (pi == null) return;
        
        // Validate radii
        double interactionRadius = pi.getDouble("interaction-radius", 10.0);
        if (interactionRadius < 0.0) {
            validationErrors.add("Player interaction radius cannot be negative");
        }
        
        double notificationRadius = pi.getDouble("notification-radius", 5.0);
        if (notificationRadius < 0.0) {
            validationErrors.add("Player notification radius cannot be negative");
        }
        
        // Validate response limits
        int maxResponses = pi.getInt("max-responses-per-minute", 10);
        if (maxResponses < 1) {
            validationErrors.add("Max responses per minute must be positive");
        }
    }
    
    /**
     * Validate performance configuration
     */
    private void validatePerformanceConfig() {
        ConfigurationSection perf = config.getConfigurationSection("performance");
        if (perf == null) return;
        
        // Validate thread count
        int maxThreads = perf.getInt("max-worker-threads", 4);
        if (maxThreads < 1) {
            validationErrors.add("Max worker threads must be positive");
        }
        
        // Validate memory settings
        int maxMemory = perf.getInt("max-memory-mb", 256);
        if (maxMemory < 64) {
            validationErrors.add("Max memory too low (minimum 64MB)");
        }
        
        int gcThreshold = perf.getInt("gc-threshold-mb", 200);
        if (gcThreshold >= maxMemory) {
            validationErrors.add("GC threshold must be less than max memory");
        }
    }
    
    /**
     * Validate logging configuration
     */
    private void validateLoggingConfig() {
        ConfigurationSection logging = config.getConfigurationSection("logging");
        if (logging == null) return;
        
        // Validate log level
        String level = logging.getString("level", "INFO");
        if (!Arrays.asList("DEBUG", "INFO", "WARN", "ERROR").contains(level.toUpperCase())) {
            validationErrors.add("Invalid log level: " + level + " (must be DEBUG, INFO, WARN, or ERROR)");
        }
        
        // Validate file size limits
        int maxFileSize = logging.getInt("max-file-size-mb", 10);
        if (maxFileSize < 1) {
            validationErrors.add("Max log file size must be positive");
        }
        
        int maxFiles = logging.getInt("max-files", 5);
        if (maxFiles < 1) {
            validationErrors.add("Max log files must be positive");
        }
    }
    
    /**
     * Handle configuration migration from older versions
     */
    private void handleMigration() {
        String currentVersion = config.getString("plugin.version", "0.0.0");
        
        if (!CONFIG_VERSION.equals(currentVersion)) {
            plugin.getLogger().info("Migrating configuration from version " + currentVersion + " to " + CONFIG_VERSION);
            
            // Perform migration logic here
            // For now, just update the version
            config.set("plugin.version", CONFIG_VERSION);
            
            // Save migrated configuration
            saveConfig();
            
            plugin.getLogger().info("Configuration migration completed");
        }
    }
    
    /**
     * Set default values for critical configuration options
     */
    private void setDefaultValues() {
        plugin.getLogger().info("Setting default configuration values");
        
        // Plugin defaults
        config.set("plugin.version", CONFIG_VERSION);
        config.set("plugin.debug-mode", false);
        
        // WebSocket defaults
        config.set("websocket.host", "localhost");
        config.set("websocket.port", 8080);
        config.set("websocket.max-connections", 100);
        config.set("websocket.connection-timeout-ms", 60000);
        config.set("websocket.heartbeat-interval-ms", 30000);
        config.set("websocket.auto-start", true);
        
        // AI defaults
        config.set("ai.enabled", true);
        config.set("ai.response-timeout-ms", 30000);
        config.set("ai.max-conversation-history", 50);
        
        // Logging defaults
        config.set("logging.level", "INFO");
        config.set("logging.console", true);
        config.set("logging.file", true);
    }
    
    // ===== Configuration Getters with Type Safety =====
    
    /**
     * Get WebSocket server host
     */
    public String getWebSocketHost() {
        return config.getString("websocket.host", "localhost");
    }
    
    /**
     * Get WebSocket server port
     */
    public int getWebSocketPort() {
        return config.getInt("websocket.port", 8080);
    }
    
    /**
     * Get WebSocket max connections
     */
    public int getWebSocketMaxConnections() {
        return config.getInt("websocket.max-connections", 100);
    }
    
    /**
     * Get WebSocket connection timeout in milliseconds
     */
    public int getWebSocketConnectionTimeout() {
        return config.getInt("websocket.connection-timeout-ms", 60000);
    }
    
    /**
     * Get WebSocket heartbeat interval in milliseconds
     */
    public int getWebSocketHeartbeatInterval() {
        return config.getInt("websocket.heartbeat-interval-ms", 30000);
    }
    
    /**
     * Check if WebSocket server should auto-start
     */
    public boolean shouldAutoStartWebSocket() {
        return config.getBoolean("websocket.auto-start", true);
    }
    
    /**
     * Check if WebSocket connections should be logged
     */
    public boolean shouldLogWebSocketConnections() {
        return config.getBoolean("websocket.log-connections", true);
    }
    
    /**
     * Check if WebSocket messages should be logged
     */
    public boolean shouldLogWebSocketMessages() {
        return config.getBoolean("websocket.log-messages", false);
    }
    
    /**
     * Check if AI is enabled
     */
    public boolean isAIEnabled() {
        return config.getBoolean("ai.enabled", true);
    }
    
    /**
     * Get AI response timeout in milliseconds
     */
    public int getAIResponseTimeout() {
        return config.getInt("ai.response-timeout-ms", 30000);
    }
    
    /**
     * Get maximum conversation history size
     */
    public int getMaxConversationHistory() {
        return config.getInt("ai.max-conversation-history", 50);
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugMode() {
        return config.getBoolean("plugin.debug-mode", false);
    }
    
    /**
     * Get player interaction radius
     */
    public double getPlayerInteractionRadius() {
        return config.getDouble("player-interaction.interaction-radius", 10.0);
    }
    
    /**
     * Get player notification radius
     */
    public double getPlayerNotificationRadius() {
        return config.getDouble("player-interaction.notification-radius", 5.0);
    }
    
    /**
     * Check if auto-respond is enabled
     */
    public boolean shouldAutoRespond() {
        return config.getBoolean("player-interaction.auto-respond", true);
    }
    
    /**
     * Get response delay in milliseconds
     */
    public int getResponseDelay() {
        return config.getInt("player-interaction.response-delay-ms", 1000);
    }
    
    /**
     * Get maximum responses per minute
     */
    public int getMaxResponsesPerMinute() {
        return config.getInt("player-interaction.max-responses-per-minute", 10);
    }
    
    /**
     * Get logging level
     */
    public String getLoggingLevel() {
        return config.getString("logging.level", "INFO");
    }
    
    /**
     * Check if console logging is enabled
     */
    public boolean isConsoleLoggingEnabled() {
        return config.getBoolean("logging.console", true);
    }
    
    /**
     * Check if file logging is enabled
     */
    public boolean isFileLoggingEnabled() {
        return config.getBoolean("logging.file", true);
    }
    
    /**
     * Get log file path
     */
    public String getLogFilePath() {
        return config.getString("logging.file-path", "logs/minecraft-ai-brain.log");
    }
    
    /**
     * Get performance: max worker threads
     */
    public int getMaxWorkerThreads() {
        return config.getInt("performance.max-worker-threads", 4);
    }
    
    /**
     * Get performance: max memory in MB
     */
    public int getMaxMemoryMB() {
        return config.getInt("performance.max-memory-mb", 256);
    }
    
    /**
     * Check if configuration is loaded and valid
     */
    public boolean isLoaded() {
        return isLoaded;
    }
    
    /**
     * Get validation errors
     */
    public List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }
    
    /**
     * Check if configuration has validation errors
     */
    public boolean hasValidationErrors() {
        return !validationErrors.isEmpty();
    }
    
    /**
     * Get the underlying Bukkit configuration
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Update a configuration value and save
     */
    public void setValue(String path, Object value) {
        if (config != null) {
            config.set(path, value);
            saveConfig();
        }
    }
    
    /**
     * Get configuration value with default
     */
    public Object getValue(String path, Object defaultValue) {
        if (config != null) {
            return config.get(path, defaultValue);
        }
        return defaultValue;
    }
} 