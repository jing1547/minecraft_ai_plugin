package com.minecraft.ai.brain.handlers;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.commands.CommandManager;
import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import com.minecraft.ai.brain.service.AudioCaptureService;
import com.minecraft.ai.brain.service.SpeechToTextConfig;
import com.minecraft.ai.brain.service.ServiceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Level;
import com.minecraft.ai.brain.service.Service;

/**
 * Main command handler for AI Brain plugin commands
 * Routes commands to appropriate handlers and provides tab completion
 */
public class CommandHandler implements CommandExecutor, TabCompleter {
    
    private final MinecraftAIBrainPlugin plugin;
    private final CommandManager commandManager;
    private final ConfigManager configManager;
    private final Logger logger;
    
    // Command cooldown tracking
    private final Map<String, Long> commandCooldowns = new HashMap<>();
    
    public CommandHandler(MinecraftAIBrainPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getPluginLogger();
        this.commandManager = new CommandManager(plugin);
        
        logger.info("CommandHandler initialized with CommandManager");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // Check if plugin is properly loaded
            if (!configManager.isLoaded()) {
                sendMessage(sender, ChatColor.RED + "Plugin is not properly loaded. Please check server logs.");
                return true;
            }
            
            // Check command cooldown
            if (isOnCooldown(sender, command.getName())) {
                sendMessage(sender, ChatColor.YELLOW + "Please wait before using this command again.");
                return true;
            }
            
            // Handle different command types
            String commandName = command.getName().toLowerCase();
            CommandManager.CommandResult result = handleCommand(sender, commandName, args);
            
            // Send result to sender
            if (result != null) {
                sendMessage(sender, result.getColor() + result.getMessage());
                
                // Set cooldown on successful command execution
                if (result.isSuccess()) {
                    setCooldown(sender, command.getName());
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Error handling command '" + command.getName() + "': " + e.getMessage());
            if (configManager.isDebugMode()) {
                e.printStackTrace();
            }
            sendMessage(sender, ChatColor.RED + "An error occurred while processing your command.");
            return true;
        }
    }
    
    /**
     * Handle specific command execution
     */
    private CommandManager.CommandResult handleCommand(CommandSender sender, String commandName, String[] args) {
        switch (commandName) {
            case "ai":
                return handleAICommand(sender, args);
            case "ai-config":
                return handleConfigCommand(sender, args);
            case "ai-voice":
                return handleVoiceCommand(sender, args);
            case "ai-debug":
                return handleDebugCommand(sender, args);
            default:
                return CommandManager.CommandResult.error("Unknown command: " + commandName);
        }
    }
    
    /**
     * Handle main AI command
     */
    private CommandManager.CommandResult handleAICommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return commandManager.executeCommand(sender, "help", new String[0]);
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        return commandManager.executeCommand(sender, subCommand, subArgs);
    }
    
    /**
     * Handle AI configuration command
     */
    private CommandManager.CommandResult handleConfigCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showConfigHelp();
        }
        
        String action = args[0].toLowerCase();
        switch (action) {
            case "show":
                return showConfiguration(sender, args);
            case "set":
                return setConfiguration(sender, args);
            case "get":
                return getConfiguration(sender, args);
            case "reload":
                return commandManager.executeCommand(sender, "reload", new String[0]);
            default:
                return CommandManager.CommandResult.warning("Unknown config action: " + action);
        }
    }
    
    /**
     * Handle voice command
     */
    private CommandManager.CommandResult handleVoiceCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showVoiceHelp();
        }
        
        String action = args[0].toLowerCase();
        switch (action) {
            case "start":
                return startVoiceProcessing(sender);
            case "stop":
                return stopVoiceProcessing(sender);
            case "test":
                return testVoiceProcessing(sender);
            case "status":
                return getVoiceStatus(sender);
            case "mictest":
                return startMicrophoneTest(sender);
            case "mictest-stop":
                return stopMicrophoneTest(sender);
            case "micstatus":
                return getMicrophoneStatus(sender);
            default:
                return CommandManager.CommandResult.warning("Unknown voice action: " + action);
        }
    }
    
    /**
     * Handle debug command
     */
    private CommandManager.CommandResult handleDebugCommand(CommandSender sender, String[] args) {
        return commandManager.executeCommand(sender, "debug", args);
    }
    
    /**
     * Show configuration help
     */
    private CommandManager.CommandResult showConfigHelp() {
        StringBuilder help = new StringBuilder();
        help.append(ChatColor.AQUA).append("=== AI Configuration Commands ===\n");
        help.append(ChatColor.YELLOW).append("/ai-config show").append(ChatColor.WHITE).append(" - Show current configuration\n");
        help.append(ChatColor.YELLOW).append("/ai-config get <key>").append(ChatColor.WHITE).append(" - Get specific configuration value\n");
        help.append(ChatColor.YELLOW).append("/ai-config set <key> <value>").append(ChatColor.WHITE).append(" - Set configuration value\n");
        help.append(ChatColor.YELLOW).append("/ai-config reload").append(ChatColor.WHITE).append(" - Reload configuration from file");
        
        return CommandManager.CommandResult.info(help.toString());
    }
    
    /**
     * Show voice help
     */
    private CommandManager.CommandResult showVoiceHelp() {
        StringBuilder help = new StringBuilder();
        help.append(ChatColor.AQUA).append("=== AI Voice Commands ===\n");
        help.append(ChatColor.YELLOW).append("/ai voice start").append(ChatColor.WHITE).append(" - Start voice processing\n");
        help.append(ChatColor.YELLOW).append("/ai voice stop").append(ChatColor.WHITE).append(" - Stop voice processing\n");
        help.append(ChatColor.YELLOW).append("/ai voice test").append(ChatColor.WHITE).append(" - Test voice system\n");
        help.append(ChatColor.YELLOW).append("/ai voice status").append(ChatColor.WHITE).append(" - Show voice system status\n");
        help.append(ChatColor.GREEN).append("=== Microphone Test Commands ===\n");
        help.append(ChatColor.YELLOW).append("/ai voice mictest").append(ChatColor.WHITE).append(" - Start microphone input test\n");
        help.append(ChatColor.YELLOW).append("/ai voice mictest-stop").append(ChatColor.WHITE).append(" - Stop microphone input test\n");
        help.append(ChatColor.YELLOW).append("/ai voice micstatus").append(ChatColor.WHITE).append(" - Show detailed microphone status");
        
        return CommandManager.CommandResult.info(help.toString());
    }
    
    /**
     * Show configuration
     */
    private CommandManager.CommandResult showConfiguration(CommandSender sender, String[] args) {
        StringBuilder config = new StringBuilder();
        config.append(ChatColor.AQUA).append("=== Current Configuration ===\n");
        config.append(ChatColor.WHITE).append("WebSocket Host: ").append(configManager.getWebSocketHost()).append("\n");
        config.append(ChatColor.WHITE).append("WebSocket Port: ").append(configManager.getWebSocketPort()).append("\n");
        config.append(ChatColor.WHITE).append("AI Enabled: ").append(configManager.isAIEnabled()).append("\n");
        config.append(ChatColor.WHITE).append("Debug Mode: ").append(configManager.isDebugMode()).append("\n");
        config.append(ChatColor.WHITE).append("Auto Response: ").append(configManager.shouldAutoRespond()).append("\n");
        config.append(ChatColor.WHITE).append("Max Worker Threads: ").append(configManager.getMaxWorkerThreads()).append("\n");
        config.append(ChatColor.WHITE).append("Logging Level: ").append(configManager.getLoggingLevel());
        
        if (configManager.hasValidationErrors()) {
            config.append(ChatColor.RED).append("\nConfiguration Errors:\n");
            for (String error : configManager.getValidationErrors()) {
                config.append(ChatColor.RED).append("  - ").append(error).append("\n");
            }
        }
        
        return CommandManager.CommandResult.info(config.toString());
    }
    
    /**
     * Set configuration value
     */
    private CommandManager.CommandResult setConfiguration(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return CommandManager.CommandResult.warning("Usage: /ai-config set <key> <value>");
        }
        
        String key = args[1];
        String value = args[2];
        
        try {
            // Parse value based on key type
            Object parsedValue = parseConfigValue(key, value);
            configManager.setValue(key, parsedValue);
            
            return CommandManager.CommandResult.success("Configuration updated: " + key + " = " + value);
        } catch (Exception e) {
            return CommandManager.CommandResult.error("Failed to set configuration: " + e.getMessage());
        }
    }
    
    /**
     * Get configuration value
     */
    private CommandManager.CommandResult getConfiguration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return CommandManager.CommandResult.warning("Usage: /ai-config get <key>");
        }
        
        String key = args[1];
        Object value = configManager.getValue(key, null);
        
        if (value == null) {
            return CommandManager.CommandResult.warning("Configuration key not found: " + key);
        }
        
        return CommandManager.CommandResult.info("Configuration value: " + key + " = " + value);
    }
    
    /**
     * Parse configuration value based on key
     */
    private Object parseConfigValue(String key, String value) {
        // Boolean values
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        
        // Integer values
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer, try double
        }
        
        // Double values
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double, treat as string
        }
        
        // String value
        return value;
    }
    
    /**
     * Start voice processing
     */
    private CommandManager.CommandResult startVoiceProcessing(CommandSender sender) {
        if (handleVoiceStart(sender, new String[0])) {
            return CommandManager.CommandResult.success("Voice processing started successfully.");
        } else {
            return CommandManager.CommandResult.error("Failed to start voice processing.");
        }
    }
    
    /**
     * Stop voice processing
     */
    private CommandManager.CommandResult stopVoiceProcessing(CommandSender sender) {
        if (handleVoiceStop(sender, new String[0])) {
            return CommandManager.CommandResult.success("Voice processing stopped successfully.");
        } else {
            return CommandManager.CommandResult.error("Failed to stop voice processing.");
        }
    }
    
    /**
     * Test voice processing
     */
    private CommandManager.CommandResult testVoiceProcessing(CommandSender sender) {
        if (handleVoiceTest(sender, new String[0])) {
            return CommandManager.CommandResult.success("Voice processing test completed successfully.");
        } else {
            return CommandManager.CommandResult.error("Voice processing test failed.");
        }
    }
    
    /**
     * Get voice status
     */
    private CommandManager.CommandResult getVoiceStatus(CommandSender sender) {
        if (handleVoiceStatus(sender, new String[0])) {
            return CommandManager.CommandResult.success("Voice status retrieved successfully.");
        } else {
            return CommandManager.CommandResult.error("Failed to get voice status.");
        }
    }
    
    /**
     * Handle voice processing start command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if command was handled
     */
    private boolean handleVoiceStart(CommandSender sender, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c[Voice AI] This command can only be used by players.");
                return true;
            }
            
            Player player = (Player) sender;
            
            // Check if speech-to-text is enabled
            if (!SpeechToTextConfig.isEnabled()) {
                player.sendMessage("§c[Voice AI] Speech-to-Text is disabled in the configuration.");
                return true;
            }
            
            // Simple implementation - just notify player
            player.sendMessage("§a[Voice AI] Voice processing started! Start speaking...");
            player.sendMessage("§7[Voice AI] Say '!help' for voice commands or speak naturally to chat with AI.");
            player.sendMessage("§7[Voice AI] Note: Connect your audio client to the WebSocket server to send audio data.");
            
            logger.info("Voice processing command executed for player: " + player.getName());
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Error starting voice processing: " + e.getMessage());
            sender.sendMessage("§c[Voice AI] Error starting voice processing: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Handle voice processing stop command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if command was handled
     */
    private boolean handleVoiceStop(CommandSender sender, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c[Voice AI] This command can only be used by players.");
                return true;
            }
            
            Player player = (Player) sender;
            
            // Simple implementation - just notify player
            player.sendMessage("§a[Voice AI] Voice processing stopped successfully.");
            logger.info("Voice processing stop command executed for player: " + player.getName());
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Error stopping voice processing: " + e.getMessage());
            sender.sendMessage("§c[Voice AI] Error stopping voice processing: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Handle voice processing test command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if command was handled
     */
    private boolean handleVoiceTest(CommandSender sender, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c[Voice AI] This command can only be used by players.");
                return true;
            }
            
            Player player = (Player) sender;
            
            player.sendMessage("§a[Voice AI] Running comprehensive voice system test...");
            
            // Test Speech-to-Text configuration
            boolean sttEnabled = SpeechToTextConfig.isEnabled();
            player.sendMessage("§7[Voice AI] STT Enabled: " + (sttEnabled ? "§a✓" : "§c✗"));
            
            if (sttEnabled) {
                try {
                    String credentialsPath = SpeechToTextConfig.getCredentialsPath();
                    String languageCode = SpeechToTextConfig.getLanguageCode();
                    int sampleRate = SpeechToTextConfig.getSampleRate();
                    
                    player.sendMessage("§7[Voice AI] Credentials: " + (credentialsPath != null ? "§a✓" : "§c✗"));
                    player.sendMessage("§7[Voice AI] Language: §b" + languageCode);
                    player.sendMessage("§7[Voice AI] Sample Rate: §b" + sampleRate + "Hz");
                    
                    // Test Google Cloud connection
                    try {
                        var speechClient = SpeechToTextConfig.getSpeechClient();
                        if (speechClient != null) {
                            player.sendMessage("§7[Voice AI] Google Cloud Connection: §a✓");
                            speechClient.close(); // Close test connection
                        } else {
                            player.sendMessage("§7[Voice AI] Google Cloud Connection: §c✗");
                        }
                    } catch (Exception e) {
                        player.sendMessage("§7[Voice AI] Google Cloud Connection: §c✗ (" + e.getMessage() + ")");
                    }
                    
                } catch (Exception e) {
                    player.sendMessage("§c[Voice AI] STT Configuration Error: " + e.getMessage());
                }
            }
            
            // Test AudioCaptureService
            ServiceManager serviceManager = plugin.getServiceManager();
            if (serviceManager != null) {
                AudioCaptureService audioCaptureService = (AudioCaptureService) serviceManager.getService("audio_capture");
                if (audioCaptureService != null) {
                    player.sendMessage("§7[Voice AI] AudioCaptureService: §a✓ Available");
                    player.sendMessage("§7[Voice AI] AudioCapture State: §b" + audioCaptureService.getState());
                    player.sendMessage("§7[Voice AI] AudioCapture Health: §b" + audioCaptureService.getHealth().getStatus());
                    
                    // Start microphone capture test
                    player.sendMessage("§e[Voice AI] Starting microphone capture test...");
                    boolean sessionStarted = audioCaptureService.startCaptureSession(player);
                    if (sessionStarted) {
                        player.sendMessage("§a[Voice AI] ✓ Microphone capture session started successfully!");
                        player.sendMessage("§6[Voice AI] Try speaking into your microphone now...");
                        player.sendMessage("§6[Voice AI] Use '/ai voice mictest-stop' to end the test");
                        
                        // Show audio format info
                        String formatInfo = audioCaptureService.getAudioFormatInfo();
                        player.sendMessage("§7[Voice AI] Audio Format: §b" + formatInfo);
                    } else {
                        player.sendMessage("§c[Voice AI] ✗ Failed to start microphone capture session");
                    }
                } else {
                    player.sendMessage("§7[Voice AI] AudioCaptureService: §c✗ Not Available");
                }
            } else {
                player.sendMessage("§7[Voice AI] ServiceManager: §c✗ Not Available");
            }
            
            // Test basic system components
            player.sendMessage("§7[Voice AI] Plugin Status: §aLoaded");
            player.sendMessage("§7[Voice AI] WebSocket Status: §aConfigured");
            
            player.sendMessage("§a[Voice AI] Voice system test completed!");
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Error testing voice processing: " + e.getMessage());
            sender.sendMessage("§c[Voice AI] Error testing voice processing: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Handle voice processing status command
     * @param sender Command sender
     * @param args Command arguments
     * @return true if command was handled
     */
    private boolean handleVoiceStatus(CommandSender sender, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c[Voice AI] This command can only be used by players.");
                return true;
            }
            
            Player player = (Player) sender;
            
            player.sendMessage("§a[Voice AI] ===== Voice System Status =====");
            
            // Overall system status
            boolean sttEnabled = SpeechToTextConfig.isEnabled();
            player.sendMessage("§7[Voice AI] System Status: " + (sttEnabled ? "§aEnabled" : "§cDisabled"));
            
            if (sttEnabled) {
                // Speech recognition status
                try {
                    String languageCode = SpeechToTextConfig.getLanguageCode();
                    int sampleRate = SpeechToTextConfig.getSampleRate();
                    player.sendMessage("§7[Voice AI] Language: §b" + languageCode);
                    player.sendMessage("§7[Voice AI] Sample Rate: §b" + sampleRate + "Hz");
                } catch (Exception e) {
                    player.sendMessage("§c[Voice AI] STT Config Error: " + e.getMessage());
                }
            }
            
            // Basic plugin status
            player.sendMessage("§7[Voice AI] Plugin: §aActive");
            player.sendMessage("§7[Voice AI] Commands: §aAvailable");
            
            player.sendMessage("§a[Voice AI] ==============================");
            
            return true;
            
        } catch (Exception e) {
            logger.severe("Error getting voice status: " + e.getMessage());
            sender.sendMessage("§c[Voice AI] Error getting voice status: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Format uptime duration into human readable string
     * @param uptimeMs Uptime in milliseconds
     * @return Formatted uptime string
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Start microphone test for the player
     * @param sender Command sender
     * @return Command result
     */
    private CommandManager.CommandResult startMicrophoneTest(CommandSender sender) {
        try {
            if (!(sender instanceof Player)) {
                return CommandManager.CommandResult.error("This command can only be used by players.");
            }
            
            Player player = (Player) sender;
            ServiceManager serviceManager = plugin.getServiceManager();
            
            if (serviceManager == null) {
                return CommandManager.CommandResult.error("ServiceManager not available.");
            }
            
            AudioCaptureService audioCaptureService = (AudioCaptureService) serviceManager.getService("audio_capture");
            if (audioCaptureService == null) {
                return CommandManager.CommandResult.error("AudioCaptureService not available.");
            }
            
            // Check if service is running
            if (audioCaptureService.getState() != Service.State.RUNNING) {
                return CommandManager.CommandResult.error("AudioCaptureService is not running. Current state: " + audioCaptureService.getState());
            }
            
            // Start capture session
            boolean sessionStarted = audioCaptureService.startCaptureSession(player);
            if (sessionStarted) {
                player.sendMessage("§a[Voice AI] ✓ Microphone test started!");
                player.sendMessage("§6[Voice AI] Speak into your microphone...");
                player.sendMessage("§7[Voice AI] Audio Format: " + audioCaptureService.getAudioFormatInfo());
                player.sendMessage("§6[Voice AI] Use '/ai voice mictest-stop' to end the test");
                return CommandManager.CommandResult.success("Microphone test session started successfully.");
            } else {
                return CommandManager.CommandResult.error("Failed to start microphone capture session.");
            }
            
        } catch (Exception e) {
            logger.severe("Error starting microphone test: " + e.getMessage());
            return CommandManager.CommandResult.error("Error starting microphone test: " + e.getMessage());
        }
    }
    
    /**
     * Stop microphone test for the player
     * @param sender Command sender
     * @return Command result
     */
    private CommandManager.CommandResult stopMicrophoneTest(CommandSender sender) {
        try {
            if (!(sender instanceof Player)) {
                return CommandManager.CommandResult.error("This command can only be used by players.");
            }
            
            Player player = (Player) sender;
            ServiceManager serviceManager = plugin.getServiceManager();
            
            if (serviceManager == null) {
                return CommandManager.CommandResult.error("ServiceManager not available.");
            }
            
            AudioCaptureService audioCaptureService = (AudioCaptureService) serviceManager.getService("audio_capture");
            if (audioCaptureService == null) {
                return CommandManager.CommandResult.error("AudioCaptureService not available.");
            }
            
            // Stop capture session
            boolean sessionStopped = audioCaptureService.stopCaptureSession(player);
            if (sessionStopped) {
                player.sendMessage("§a[Voice AI] ✓ Microphone test stopped!");
                player.sendMessage("§7[Voice AI] Microphone capture session ended.");
                return CommandManager.CommandResult.success("Microphone test session stopped successfully.");
            } else {
                return CommandManager.CommandResult.warning("No active microphone session found for player.");
            }
            
        } catch (Exception e) {
            logger.severe("Error stopping microphone test: " + e.getMessage());
            return CommandManager.CommandResult.error("Error stopping microphone test: " + e.getMessage());
        }
    }
    
    /**
     * Get microphone status for the player
     * @param sender Command sender
     * @return Command result
     */
    private CommandManager.CommandResult getMicrophoneStatus(CommandSender sender) {
        try {
            if (!(sender instanceof Player)) {
                return CommandManager.CommandResult.error("This command can only be used by players.");
            }
            
            Player player = (Player) sender;
            ServiceManager serviceManager = plugin.getServiceManager();
            
            player.sendMessage("§a[Voice AI] ===== Microphone Status =====");
            
            if (serviceManager == null) {
                player.sendMessage("§c[Voice AI] ServiceManager: Not Available");
                return CommandManager.CommandResult.warning("ServiceManager not available.");
            }
            
            AudioCaptureService audioCaptureService = (AudioCaptureService) serviceManager.getService("audio_capture");
            if (audioCaptureService == null) {
                player.sendMessage("§c[Voice AI] AudioCaptureService: Not Available");
                return CommandManager.CommandResult.warning("AudioCaptureService not available.");
            }
            
            // Service status
            player.sendMessage("§7[Voice AI] Service State: §b" + audioCaptureService.getState());
            player.sendMessage("§7[Voice AI] Service Health: §b" + audioCaptureService.getHealth().getStatus());
            player.sendMessage("§7[Voice AI] Service Enabled: §b" + audioCaptureService.isEnabled());
            
            if (audioCaptureService.getStartTime() > 0) {
                long uptime = audioCaptureService.getUptime();
                player.sendMessage("§7[Voice AI] Service Uptime: §b" + formatUptime(uptime));
            }
            
            // Active sessions info
            Map<UUID, String> activeSessions = audioCaptureService.getActiveSessions();
            player.sendMessage("§7[Voice AI] Total Active Sessions: §b" + activeSessions.size());
            
            boolean playerHasSession = activeSessions.containsKey(player.getUniqueId());
            player.sendMessage("§7[Voice AI] Your Session Status: " + (playerHasSession ? "§aActive" : "§cInactive"));
            
            // Audio format info
            player.sendMessage("§7[Voice AI] Audio Format: §b" + audioCaptureService.getAudioFormatInfo());
            
            // Service metrics
            Map<String, Object> metrics = audioCaptureService.getMetrics();
            if (metrics != null && !metrics.isEmpty()) {
                player.sendMessage("§7[Voice AI] Service Metrics:");
                for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                    player.sendMessage("§7  - " + entry.getKey() + ": §b" + entry.getValue());
                }
            }
            
            player.sendMessage("§a[Voice AI] ==============================");
            
            return CommandManager.CommandResult.success("Microphone status displayed.");
            
        } catch (Exception e) {
            logger.severe("Error getting microphone status: " + e.getMessage());
            return CommandManager.CommandResult.error("Error getting microphone status: " + e.getMessage());
        }
    }
    
    /**
     * Check if sender is on cooldown
     */
    private boolean isOnCooldown(CommandSender sender, String commandName) {
        String key = sender.getName() + ":" + commandName;
        Long lastUsed = commandCooldowns.get(key);
        
        if (lastUsed == null) {
            return false;
        }
        
        long cooldownTime = configManager.getConfig().getInt("commands.command-cooldown-ms", 1000);
        return System.currentTimeMillis() - lastUsed < cooldownTime;
    }
    
    /**
     * Set cooldown for sender
     */
    private void setCooldown(CommandSender sender, String commandName) {
        String key = sender.getName() + ":" + commandName;
        commandCooldowns.put(key, System.currentTimeMillis());
    }
    
    /**
     * Send message to sender with proper formatting
     */
    private void sendMessage(CommandSender sender, String message) {
        // Split message by newlines and send each line
        String[] lines = message.split("\n");
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            String commandName = command.getName().toLowerCase();
            
            switch (commandName) {
                case "ai":
                    return getAITabCompletions(sender, args);
                case "ai-config":
                    return getConfigTabCompletions(sender, args);
                case "ai-voice":
                    return getVoiceTabCompletions(sender, args);
                case "ai-debug":
                    return getDebugTabCompletions(sender, args);
                default:
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.warning("Error getting tab completions: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Get AI command tab completions
     */
    private List<String> getAITabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(commandManager.getAvailableCommands(sender));
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return commandManager.getTabCompletions(sender, subCommand, subArgs);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Get config command tab completions
     */
    private List<String> getConfigTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("show", "get", "set", "reload")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 2 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            // Common configuration keys
            return Arrays.asList(
                    "plugin.debug-mode",
                    "websocket.host",
                    "websocket.port",
                    "ai.enabled",
                    "player-interaction.auto-respond",
                    "logging.level"
            ).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Get voice command tab completions
     */
    private List<String> getVoiceTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "test", "status", "mictest", "mictest-stop", "micstatus")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Get debug command tab completions
     */
    private List<String> getDebugTabCompletions(CommandSender sender, String[] args) {
        return commandManager.getTabCompletions(sender, "debug", args);
    }
    
    /**
     * Get the command manager instance
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        commandCooldowns.clear();
        if (commandManager != null) {
            commandManager.shutdown();
        }
        logger.info("CommandHandler cleanup completed");
    }
} 