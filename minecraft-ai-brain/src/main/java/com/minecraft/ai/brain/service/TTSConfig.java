package com.minecraft.ai.brain.service;

import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configuration and client management for Google Cloud Text-to-Speech service.
 * Handles initialization, authentication, and lifecycle management of TTS client.
 */
public class TTSConfig {
    private static final String LOG_PREFIX = "[TTSConfig] ";
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    
    // Configuration cache
    private static boolean enabled = false;
    private static String credentialsPath;
    private static String defaultLanguageCode;
    private static String defaultVoiceName;
    private static String defaultGender;
    
    // Plugin and manager instances
    private static JavaPlugin pluginInstance;
    private static ConfigManager configManagerInstance;
    private static Logger loggerInstance;
    
    /**
     * Initialize TTS configuration from config.yml
     */
    public static void initialize(JavaPlugin plugin, ConfigManager configManager) {
        if (isInitialized.get()) {
            return;
        }
        
        try {
            // Store instances
            pluginInstance = plugin;
            configManagerInstance = configManager;
            loggerInstance = new Logger(plugin);
            
            loggerInstance.info(LOG_PREFIX + "Initializing TTS configuration...");
            loadConfiguration();
            
            if (enabled) {
                validateConfiguration();
                loggerInstance.info(LOG_PREFIX + "TTS configuration initialized successfully");
            } else {
                loggerInstance.info(LOG_PREFIX + "TTS is disabled in configuration");
            }
            
            isInitialized.set(true);
        } catch (Exception e) {
            if (loggerInstance != null) {
                loggerInstance.severe(LOG_PREFIX + "Failed to initialize TTS configuration: " + e.getMessage());
            } else {
                plugin.getLogger().severe(LOG_PREFIX + "Failed to initialize TTS configuration: " + e.getMessage());
            }
            throw new RuntimeException("TTS configuration initialization failed", e);
        }
    }
    
    /**
     * Check if TTS is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get default language code
     */
    public static String getDefaultLanguageCode() {
        return defaultLanguageCode;
    }
    
    /**
     * Get default voice name
     */
    public static String getDefaultVoiceName() {
        return defaultVoiceName;
    }
    
    /**
     * Get default voice gender
     */
    public static String getDefaultGender() {
        return defaultGender;
    }
    
    /**
     * Get credentials file path
     */
    public static String getCredentialsPath() {
        // If path is relative, make it relative to plugin data folder
        if (credentialsPath != null && !credentialsPath.trim().isEmpty() && !java.nio.file.Paths.get(credentialsPath).isAbsolute()) {
            if (pluginInstance != null) {
                return pluginInstance.getDataFolder().getAbsolutePath() + "/" + credentialsPath;
            }
        }
        return credentialsPath;
    }
    
    /**
     * Test if credentials file exists and is readable
     */
    public static boolean testCredentialsFile() {
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            if (loggerInstance != null) {
                loggerInstance.warning(LOG_PREFIX + "TTS credentials path is not configured");
            }
            return false;
        }
        
        // Get the actual path (with plugin folder conversion if needed)
        String actualPath = getCredentialsPath();
        File credFile = new File(actualPath);
        
        if (loggerInstance != null) {
            loggerInstance.info(LOG_PREFIX + "Testing credentials file at: " + actualPath);
        }
        
        if (!credFile.exists()) {
            if (loggerInstance != null) {
                loggerInstance.warning(LOG_PREFIX + "TTS credentials file not found: " + actualPath);
            }
            return false;
        }
        
        if (!credFile.canRead()) {
            if (loggerInstance != null) {
                loggerInstance.warning(LOG_PREFIX + "TTS credentials file is not readable: " + actualPath);
            }
            return false;
        }
        
        if (loggerInstance != null) {
            loggerInstance.info(LOG_PREFIX + "TTS credentials file validation successful: " + actualPath);
        }
        return true;
    }
    
    /**
     * Load configuration from config.yml
     */
    private static void loadConfiguration() {
        try {
            // Use ConfigManager getValue method to get configuration values
            enabled = (Boolean) configManagerInstance.getValue("tts.enabled", false);
            credentialsPath = (String) configManagerInstance.getValue("tts.credentials.path", "");
            defaultLanguageCode = (String) configManagerInstance.getValue("tts.voice.default.language-code", "ko-KR");
            defaultVoiceName = (String) configManagerInstance.getValue("tts.voice.default.name", "ko-KR-Neural2-C");
            defaultGender = (String) configManagerInstance.getValue("tts.voice.default.gender", "NEUTRAL");
            
            if (loggerInstance != null) {
                loggerInstance.debug(LOG_PREFIX + "Configuration loaded - Enabled: " + enabled + 
                            ", Language: " + defaultLanguageCode + 
                            ", Voice: " + defaultVoiceName);
            }
        } catch (Exception e) {
            if (loggerInstance != null) {
                loggerInstance.severe(LOG_PREFIX + "Failed to load configuration: " + e.getMessage());
            }
            throw new RuntimeException("Failed to load TTS configuration", e);
        }
    }
    
    /**
     * Validate the loaded configuration
     */
    private static void validateConfiguration() {
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            throw new IllegalStateException("TTS credentials path is not configured");
        }
        
        // Validate other required fields
        if (defaultLanguageCode == null || defaultLanguageCode.trim().isEmpty()) {
            throw new IllegalStateException("Default language code is not configured");
        }
        
        if (defaultVoiceName == null || defaultVoiceName.trim().isEmpty()) {
            throw new IllegalStateException("Default voice name is not configured");
        }
        
        if (loggerInstance != null) {
            loggerInstance.debug(LOG_PREFIX + "Configuration validation successful");
        }
    }
    
    /**
     * Get configuration summary for debugging
     */
    public static String getConfigurationSummary() {
        if (!isInitialized.get()) {
            return "TTS configuration not initialized";
        }
        
        return String.format(
            "TTS Configuration: Enabled=%s, Language=%s, Voice=%s, Gender=%s, Credentials=%s",
            enabled, defaultLanguageCode, defaultVoiceName, defaultGender, credentialsPath
        );
    }
    
    /**
     * Check if TTS configuration is initialized
     */
    public static boolean isInitialized() {
        return isInitialized.get();
    }
    
    /**
     * Reset configuration (for testing)
     */
    public static void reset() {
        isInitialized.set(false);
        enabled = false;
        credentialsPath = null;
        defaultLanguageCode = null;
        defaultVoiceName = null;
        defaultGender = null;
        pluginInstance = null;
        configManagerInstance = null;
        loggerInstance = null;
    }
} 