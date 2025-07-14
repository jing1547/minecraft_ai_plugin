package com.minecraft.ai.brain.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.minecraft.ai.brain.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Configuration class for Google Cloud Speech-to-Text client
 * Manages authentication and client initialization
 */
public class SpeechToTextConfig {
    private static volatile SpeechClient speechClient;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final Object lock = new Object();
    private static final Logger logger = Logger.getLogger(SpeechToTextConfig.class.getName());
    private static ConfigManager configManager;
    
    // Configuration keys
    private static final String CONFIG_KEY_CREDENTIALS_PATH = "speech.credentials.path";
    private static final String CONFIG_KEY_ENABLED = "speech.enabled";
    private static final String CONFIG_KEY_LANGUAGE_CODE = "speech.language.code";
    private static final String CONFIG_KEY_SAMPLE_RATE = "speech.sample.rate";
    
    // Default values
    private static final String DEFAULT_LANGUAGE_CODE = "ko-KR";
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    
    /**
     * Initialize with ConfigManager instance
     * @param configManager the configuration manager
     */
    public static void setConfigManager(ConfigManager configManager) {
        SpeechToTextConfig.configManager = configManager;
    }
    
    /**
     * Get or create the Speech client instance
     * @return SpeechClient instance
     * @throws IOException if credentials cannot be loaded
     */
    public static SpeechClient getSpeechClient() throws IOException {
        if (speechClient == null) {
            synchronized (lock) {
                if (speechClient == null) {
                    initializeSpeechClient();
                }
            }
        }
        return speechClient;
    }
    
    /**
     * Initialize the Speech client with proper credentials
     * @throws IOException if credentials file cannot be read
     */
    private static void initializeSpeechClient() throws IOException {
        if (!isEnabled()) {
            throw new IllegalStateException("Speech-to-Text is disabled in configuration");
        }
        
        String credentialsPath = getCredentialsPath();
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            throw new IllegalStateException("Speech-to-Text credentials path is not configured");
        }
        
        try {
            logger.info("Initializing Google Cloud Speech-to-Text client...");
            
            // Load credentials from file
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(credentialsPath)
            );
            
            // Create Speech client with credentials
            speechClient = SpeechClient.create(
                SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build()
            );
            
            initialized.set(true);
            logger.info("Google Cloud Speech-to-Text client initialized successfully");
            
        } catch (IOException e) {
            logger.severe("Failed to initialize Speech-to-Text client: " + e.getMessage());
            throw new IOException("Failed to initialize Speech-to-Text client", e);
        }
    }
    
    /**
     * Check if Speech-to-Text is enabled in configuration
     * @return true if enabled, false otherwise
     */
    public static boolean isEnabled() {
        if (configManager != null) {
            return (Boolean) configManager.getValue(CONFIG_KEY_ENABLED, false);
        }
        // Fallback to direct config access if configManager is not set
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MinecraftAIBrain");
        if (plugin != null) {
            return plugin.getConfig().getBoolean(CONFIG_KEY_ENABLED, false);
        }
        return false;
    }
    
    /**
     * Get the credentials file path from configuration
     * @return credentials file path or null if not configured
     */
    public static String getCredentialsPath() {
        String path = null;
        
        // First try to get from configuration
        if (configManager != null) {
            path = (String) configManager.getValue(CONFIG_KEY_CREDENTIALS_PATH, "");
        } else {
            // Fallback to direct config access
            JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MinecraftAIBrain");
            if (plugin != null) {
                path = plugin.getConfig().getString(CONFIG_KEY_CREDENTIALS_PATH, "");
            }
        }
        
        // If config path is empty, try environment variable
        if (path == null || path.trim().isEmpty()) {
            path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        }
        
        // If path is relative, make it relative to plugin data folder
        if (path != null && !path.trim().isEmpty() && !java.nio.file.Paths.get(path).isAbsolute()) {
            JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MinecraftAIBrain");
            if (plugin != null) {
                path = plugin.getDataFolder().getAbsolutePath() + "/" + path;
            }
        }
        
        return path;
    }
    
    /**
     * Get the language code for speech recognition
     * @return language code (default: ko-KR)
     */
    public static String getLanguageCode() {
        if (configManager != null) {
            return (String) configManager.getValue(CONFIG_KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE_CODE);
        }
        // Fallback to direct config access
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MinecraftAIBrain");
        if (plugin != null) {
            return plugin.getConfig().getString(CONFIG_KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE_CODE);
        }
        return DEFAULT_LANGUAGE_CODE;
    }
    
    /**
     * Get the sample rate for audio processing
     * @return sample rate in Hz (default: 16000)
     */
    public static int getSampleRate() {
        if (configManager != null) {
            return (Integer) configManager.getValue(CONFIG_KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE);
        }
        // Fallback to direct config access
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MinecraftAIBrain");
        if (plugin != null) {
            return plugin.getConfig().getInt(CONFIG_KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE);
        }
        return DEFAULT_SAMPLE_RATE;
    }
    
    /**
     * Check if the client is initialized
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * Shutdown the Speech client and release resources
     */
    public static void shutdown() {
        synchronized (lock) {
            if (speechClient != null) {
                try {
                    logger.info("Shutting down Google Cloud Speech-to-Text client...");
                    speechClient.close();
                    speechClient = null;
                    initialized.set(false);
                    logger.info("Google Cloud Speech-to-Text client shut down successfully");
                } catch (Exception e) {
                    logger.severe("Error shutting down Speech-to-Text client: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Validate the configuration
     * @throws IllegalStateException if configuration is invalid
     */
    public static void validateConfiguration() throws IllegalStateException {
        if (!isEnabled()) {
            throw new IllegalStateException("Speech-to-Text is disabled in configuration");
        }
        
        String credentialsPath = getCredentialsPath();
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            throw new IllegalStateException("Speech-to-Text credentials path is not configured. " +
                "Please set '" + CONFIG_KEY_CREDENTIALS_PATH + "' in config.yml or " +
                "set GOOGLE_APPLICATION_CREDENTIALS environment variable.");
        }
        
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(credentialsPath);
            if (!java.nio.file.Files.exists(path)) {
                throw new IllegalStateException("Speech-to-Text credentials file not found: " + credentialsPath);
            }
            if (!java.nio.file.Files.isReadable(path)) {
                throw new IllegalStateException("Speech-to-Text credentials file is not readable: " + credentialsPath);
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new IllegalStateException("Invalid credentials path: " + credentialsPath);
        }
    }
    
    /**
     * Get configuration summary for logging/debugging
     * @return configuration summary string
     */
    public static String getConfigurationSummary() {
        return String.format(
            "Speech-to-Text Configuration:\n" +
            "  Enabled: %s\n" +
            "  Language Code: %s\n" +
            "  Sample Rate: %d Hz\n" +
            "  Credentials Path: %s\n" +
            "  Initialized: %s",
            isEnabled(),
            getLanguageCode(),
            getSampleRate(),
            getCredentialsPath() != null ? getCredentialsPath() : "Not configured",
            isInitialized()
        );
    }
} 