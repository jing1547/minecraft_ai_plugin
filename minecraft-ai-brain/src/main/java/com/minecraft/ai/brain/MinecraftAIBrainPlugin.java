package com.minecraft.ai.brain;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;

import com.minecraft.ai.brain.websocket.WebSocketServerManager;
import com.minecraft.ai.brain.handlers.PlayerEventHandler;
import com.minecraft.ai.brain.handlers.CommandHandler;
import com.minecraft.ai.brain.commands.CloudTestCommand;
import com.minecraft.ai.brain.commands.STTTestCommand;
import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import com.minecraft.ai.brain.service.ServiceManager;
import com.minecraft.ai.brain.service.ServiceException;
import com.minecraft.ai.brain.service.TextToSpeechService;
import com.minecraft.ai.brain.service.AudioPlayerService;
import com.minecraft.ai.brain.service.AudioCaptureService;
import com.minecraft.ai.brain.service.TTSConfig;
import com.minecraft.ai.brain.service.SpeechToTextConfig;

import java.util.logging.Level;

/**
 * Main plugin class for Minecraft AI Brain component.
 * This is the Java side of the hybrid architecture that handles:
 * - AI conversation processing
 * - Voice input/output (STT/TTS)
 * - WebSocket communication with Node.js Mineflayer bot
 * - Player interaction management
 * - Configuration and state management
 */
public class MinecraftAIBrainPlugin extends JavaPlugin {

    // Core managers
    private static MinecraftAIBrainPlugin instance;
    private WebSocketServerManager webSocketManager;
    private ConfigManager configManager;
    private Logger pluginLogger;
    private ServiceManager serviceManager;
    
    // Event handlers
    private PlayerEventHandler playerEventHandler;
    private CommandHandler commandHandler;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize logging
        this.pluginLogger = new Logger(this);
        pluginLogger.info("=== Minecraft AI Brain Plugin Starting ===");
        
        try {
            // Load configuration
            saveDefaultConfig();
            this.configManager = new ConfigManager(this);
            configManager.loadConfig();
            
            pluginLogger.info("Configuration loaded successfully");
            
            // Initialize service manager and register all services
            initializeServices();
            
            // Initialize WebSocket server for bot communication
            initializeWebSocketServer();
            
            // Register event handlers
            registerEventHandlers();
            
            // Register commands
            registerCommands();
            
            // Schedule tasks
            scheduleTasks();
            
            pluginLogger.info("=== Minecraft AI Brain Plugin Started Successfully ===");
            pluginLogger.info("Ready to communicate with Node.js Mineflayer bot");
            
        } catch (Exception e) {
            pluginLogger.severe("Failed to initialize plugin: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        pluginLogger.info("=== Minecraft AI Brain Plugin Shutting Down ===");
        
        try {
            // Shutdown services first
            if (serviceManager != null) {
                serviceManager.shutdown();
                pluginLogger.info("Services stopped");
            }
            
            // Shutdown WebSocket server
            if (webSocketManager != null) {
                webSocketManager.shutdown();
                pluginLogger.info("WebSocket server stopped");
            }
            
            // Cleanup handlers
            if (playerEventHandler != null) {
                playerEventHandler.cleanup();
            }
            
            // Save configuration
            if (configManager != null) {
                configManager.saveConfig();
                pluginLogger.info("Configuration saved");
            }
            
        } catch (Exception e) {
            pluginLogger.severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        }
        
        pluginLogger.info("=== Minecraft AI Brain Plugin Shutdown Complete ===");
        instance = null;
    }

    /**
     * Initialize service manager and register all services
     */
    private void initializeServices() throws ServiceException {
        this.serviceManager = new ServiceManager(this);
        
        // Initialize TTS and STT configurations before service registration
        TTSConfig.initialize(this, configManager);
        SpeechToTextConfig.setConfigManager(configManager);
        
        // Register all services
        serviceManager.registerService(new TextToSpeechService(this));
        serviceManager.registerService(new AudioPlayerService());
        serviceManager.registerService(new AudioCaptureService());
        
        pluginLogger.info("Services registered");
        
        // Initialize and start all services
        serviceManager.initializeServices();
        serviceManager.startServices();
        
        pluginLogger.info("All services initialized and started successfully");
    }

    /**
     * Initialize WebSocket server for communication with Node.js bot
     */
    private void initializeWebSocketServer() {
        try {
            int port = configManager.getWebSocketPort();
            String host = configManager.getWebSocketHost();
            
            this.webSocketManager = new WebSocketServerManager(this, host, port);
            webSocketManager.start();
            
            pluginLogger.info("WebSocket server started on " + host + ":" + port);
            
        } catch (Exception e) {
            pluginLogger.severe("Failed to start WebSocket server: " + e.getMessage());
            throw new RuntimeException("WebSocket server initialization failed", e);
        }
    }

    /**
     * Register event handlers for player interactions and server events
     */
    private void registerEventHandlers() {
        this.playerEventHandler = new PlayerEventHandler(this);
        Bukkit.getPluginManager().registerEvents(playerEventHandler, this);
        
        pluginLogger.info("Event handlers registered");
    }

    /**
     * Register plugin commands
     */
    private void registerCommands() {
        this.commandHandler = new CommandHandler(this);
        
        // Register commands defined in plugin.yml
        this.getCommand("ai").setExecutor(commandHandler);
        this.getCommand("ai-config").setExecutor(commandHandler);
        this.getCommand("ai-voice").setExecutor(commandHandler);
        this.getCommand("ai-debug").setExecutor(commandHandler);
        this.getCommand("cloudtest").setExecutor(new CloudTestCommand());
        
        // Register STT test command
        AudioCaptureService audioCaptureService = serviceManager.getService("audio_capture", AudioCaptureService.class);
        if (audioCaptureService != null) {
            this.getCommand("stttest").setExecutor(new STTTestCommand(this, audioCaptureService));
        }
        
        // Register tab completers
        this.getCommand("ai").setTabCompleter(commandHandler);
        this.getCommand("ai-config").setTabCompleter(commandHandler);
        this.getCommand("ai-voice").setTabCompleter(commandHandler);
        this.getCommand("ai-debug").setTabCompleter(commandHandler);
        
        pluginLogger.info("Commands and tab completers registered");
    }

    /**
     * Schedule periodic tasks
     */
    private void scheduleTasks() {
        // Schedule periodic status checks (every 30 seconds)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (webSocketManager != null) {
                webSocketManager.checkConnectionStatus();
            }
            
            if (serviceManager != null) {
                // Log service health status
                serviceManager.getAllServiceHealth().forEach((serviceId, health) -> {
                    if (!health.isHealthy()) {
                        pluginLogger.warning("Service health issue: " + serviceId + " - " + health.getMessage());
                    }
                });
            }
        }, 20L * 30L, 20L * 30L); // 30 seconds in ticks
        
        pluginLogger.debug("Scheduled tasks initialized");
    }

    // ===== Getter methods for other classes =====
    
    /**
     * Get the plugin instance
     */
    public static MinecraftAIBrainPlugin getInstance() {
        return instance;
    }

    /**
     * Get the service manager
     */
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    /**
     * Get the WebSocket server manager
     */
    public WebSocketServerManager getWebSocketManager() {
        return webSocketManager;
    }

    /**
     * Get the configuration manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Get the plugin logger
     */
    public Logger getPluginLogger() {
        return pluginLogger;
    }

    /**
     * Get the player event handler
     */
    public PlayerEventHandler getPlayerEventHandler() {
        return playerEventHandler;
    }

    /**
     * Get the command handler
     */
    public CommandHandler getCommandHandler() {
        return commandHandler;
    }
} 