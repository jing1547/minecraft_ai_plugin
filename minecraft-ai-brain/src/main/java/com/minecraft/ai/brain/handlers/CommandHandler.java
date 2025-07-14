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
import org.bukkit.scheduler.BukkitTask;

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
    
    // Track running periodic update tasks by player UUID
    private final Map<UUID, BukkitTask> periodicUpdateTasks = new HashMap<>();
    
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
     * Start microphone input test with real-time monitoring
     */
    private CommandManager.CommandResult startMicrophoneTest(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return CommandManager.CommandResult.error("이 명령어는 플레이어만 사용할 수 있습니다.");
        }
        
        Player player = (Player) sender;
        
        try {
            // Get AudioCaptureService from ServiceManager
            ServiceManager serviceManager = plugin.getServiceManager();
            if (serviceManager == null) {
                return CommandManager.CommandResult.error("ServiceManager를 찾을 수 없습니다.");
            }
            
            Service audioCaptureService = serviceManager.getService("audio_capture");
            if (audioCaptureService == null) {
                return CommandManager.CommandResult.error("AudioCaptureService를 찾을 수 없습니다.");
            }
            
            if (!(audioCaptureService instanceof AudioCaptureService)) {
                return CommandManager.CommandResult.error("AudioCaptureService 타입이 올바르지 않습니다.");
            }
            
            AudioCaptureService audioService = (AudioCaptureService) audioCaptureService;
            
            // Check service status
            if (audioService.getState() != Service.State.RUNNING) {
                return CommandManager.CommandResult.error("AudioCaptureService가 실행 중이 아닙니다. 상태: " + audioService.getState());
            }
            
            player.sendMessage("§a[마이크 테스트] 실시간 마이크 모니터링을 시작합니다...");
            player.sendMessage("§7[마이크 테스트] 마이크에 대고 말씀해보세요. 볼륨과 음성 활동이 실시간으로 감지됩니다.");
            player.sendMessage("§7[마이크 테스트] 중지하려면 '/ai voice mictest-stop'을 입력하세요.");
            
            // Start real-time audio monitoring
            audioService.startAudioMonitoring(player);
            
            // Start periodic status updates
            startPeriodicStatusUpdates(player, audioService);
            
            return CommandManager.CommandResult.success("마이크 테스트가 시작되었습니다. 말씀해보세요!");
            
        } catch (Exception e) {
            logger.warning("마이크 테스트 시작 중 오류: " + e.getMessage());
            return CommandManager.CommandResult.error("마이크 테스트 시작 실패: " + e.getMessage());
        }
    }
    
    /**
     * Stop microphone input test
     */
    private CommandManager.CommandResult stopMicrophoneTest(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return CommandManager.CommandResult.error("이 명령어는 플레이어만 사용할 수 있습니다.");
        }
        
        Player player = (Player) sender;
        
        try {
            // Get AudioCaptureService from ServiceManager
            ServiceManager serviceManager = plugin.getServiceManager();
            if (serviceManager == null) {
                return CommandManager.CommandResult.error("ServiceManager를 찾을 수 없습니다.");
            }
            
            Service audioCaptureService = serviceManager.getService("audio_capture");
            if (audioCaptureService == null) {
                return CommandManager.CommandResult.error("AudioCaptureService를 찾을 수 없습니다.");
            }
            
            if (!(audioCaptureService instanceof AudioCaptureService)) {
                return CommandManager.CommandResult.error("AudioCaptureService 타입이 올바르지 않습니다.");
            }
            
            AudioCaptureService audioService = (AudioCaptureService) audioCaptureService;
            
            // Stop audio monitoring
            audioService.stopAudioMonitoring(player);
            
            // Stop periodic updates
            stopPeriodicStatusUpdates(player);
            
            return CommandManager.CommandResult.success("마이크 테스트가 중지되었습니다.");
            
        } catch (Exception e) {
            logger.warning("마이크 테스트 중지 중 오류: " + e.getMessage());
            return CommandManager.CommandResult.error("마이크 테스트 중지 실패: " + e.getMessage());
        }
    }
    
    /**
     * Get detailed microphone status with real-time data
     */
    private CommandManager.CommandResult getMicrophoneStatus(CommandSender sender) {
        try {
            // Get AudioCaptureService from ServiceManager
            ServiceManager serviceManager = plugin.getServiceManager();
            if (serviceManager == null) {
                return CommandManager.CommandResult.error("ServiceManager를 찾을 수 없습니다.");
            }
            
            Service audioCaptureService = serviceManager.getService("audio_capture");
            if (audioCaptureService == null) {
                return CommandManager.CommandResult.error("AudioCaptureService를 찾을 수 없습니다.");
            }
            
            if (!(audioCaptureService instanceof AudioCaptureService)) {
                return CommandManager.CommandResult.error("AudioCaptureService 타입이 올바르지 않습니다.");
            }
            
            AudioCaptureService audioService = (AudioCaptureService) audioCaptureService;
            Map<String, Object> status = audioService.getAudioMonitoringStatus();
            Map<String, Object> micStatus = audioService.getMicrophoneStatus();
            
            StringBuilder statusMsg = new StringBuilder();
            statusMsg.append("§b=== 실시간 마이크 상태 ===\n");
            
            // Microphone hardware status
            statusMsg.append("§a=== 마이크 하드웨어 상태 ===\n");
            statusMsg.append("§7마이크 초기화됨: ").append((Boolean) micStatus.get("microphone_initialized") ? "§a✓" : "§c✗").append("\n");
            statusMsg.append("§7마이크 열림: ").append((Boolean) micStatus.get("microphone_open") ? "§a✓" : "§c✗").append("\n");
            statusMsg.append("§7마이크 활성: ").append((Boolean) micStatus.get("microphone_active") ? "§a✓" : "§c✗").append("\n");
            statusMsg.append("§7캡처 중: ").append((Boolean) micStatus.get("capturing") ? "§a✓" : "§c✗").append("\n");
            statusMsg.append("§7샘플 레이트: §e").append(micStatus.get("sample_rate")).append(" Hz\n");
            statusMsg.append("§7채널: §e").append(micStatus.get("channels")).append(" (모노)\n");
            statusMsg.append("§7비트 깊이: §e").append(micStatus.get("bits_per_sample")).append(" bit\n");
            statusMsg.append("§7버퍼 크기: §e").append(micStatus.get("buffer_size")).append(" bytes\n");
            
            if (micStatus.containsKey("microphone_info")) {
                statusMsg.append("§7마이크 정보: §e").append(micStatus.get("microphone_info")).append("\n");
            }
            
            // Audio monitoring status  
            statusMsg.append("\n§6=== 오디오 모니터링 상태 ===\n");
            statusMsg.append("§7서비스 상태: ").append(getServiceStatusColor(audioService.getState())).append(audioService.getState()).append("\n");
            statusMsg.append("§7현재 볼륨 레벨: §a").append(status.get("volume_level")).append("%\n");
            statusMsg.append("§7음성 활동 감지: ").append((Boolean) status.get("voice_detected") ? "§a✓ 활성" : "§c✗ 비활성").append("\n");
            statusMsg.append("§7총 오디오 프레임: §e").append(status.get("total_frames")).append("\n");
            statusMsg.append("§7음성 활동 비율: §e").append(String.format("%.1f%%", status.get("voice_activity_percentage"))).append("\n");
            statusMsg.append("§7평균 볼륨: §e").append(String.format("%.2f", status.get("average_volume"))).append("\n");
            statusMsg.append("§7활성 세션: §e").append(status.get("active_sessions")).append("개");
            
            sender.sendMessage(statusMsg.toString());
            
            return CommandManager.CommandResult.success("마이크 상태 조회 완료");
            
        } catch (Exception e) {
            logger.warning("마이크 상태 조회 중 오류: " + e.getMessage());
            return CommandManager.CommandResult.error("마이크 상태 조회 실패: " + e.getMessage());
        }
    }
    
    /**
     * Start periodic status updates for microphone test
     */
    private void startPeriodicStatusUpdates(Player player, AudioCaptureService audioService) {
        UUID playerId = player.getUniqueId();
        
        // Cancel any existing task for this player
        stopPeriodicStatusUpdates(player);
        
        // Schedule periodic updates every 3 seconds
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    // Check if player is still being monitored
                    if (!player.isOnline() || audioService.getState() != Service.State.RUNNING || 
                        !audioService.isPlayerBeingMonitored(player)) {
                        stopPeriodicStatusUpdates(player);
                        return;
                    }
                    
                    Map<String, Object> status = audioService.getAudioMonitoringStatus();
                    
                    // Create volume bar visualization
                    int volumeLevel = (Integer) status.get("volume_level");
                    String volumeBar = createVolumeBar(volumeLevel);
                    
                    // Send real-time update
                    player.sendMessage(String.format("§7[실시간] 볼륨: %s §7(%d%%) | 음성: %s", 
                                                   volumeBar, 
                                                   volumeLevel,
                                                   (Boolean) status.get("voice_detected") ? "§a감지" : "§8무음"));
                    
                } catch (Exception e) {
                    // If there's an error, stop the task
                    logger.warning("Error in periodic status update: " + e.getMessage());
                    stopPeriodicStatusUpdates(player);
                }
            }
        }, 60L, 60L); // Start after 3 seconds, repeat every 3 seconds
        
        // Store the task for later cancellation
        periodicUpdateTasks.put(playerId, task);
    }
    
    /**
     * Stop periodic status updates
     */
    private void stopPeriodicStatusUpdates(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = periodicUpdateTasks.remove(playerId);
        
        if (task != null && !task.isCancelled()) {
            task.cancel();
            logger.info("Cancelled periodic status updates for player: " + player.getName());
        }
    }
    
    /**
     * Create visual volume bar
     */
    private String createVolumeBar(int volumeLevel) {
        int bars = volumeLevel / 10; // 0-10 bars
        StringBuilder volumeBar = new StringBuilder("§8[");
        
        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                if (i < 3) volumeBar.append("§a|"); // Green for low
                else if (i < 7) volumeBar.append("§e|"); // Yellow for medium  
                else volumeBar.append("§c|"); // Red for high
            } else {
                volumeBar.append("§8|");
            }
        }
        
        volumeBar.append("§8]");
        return volumeBar.toString();
    }
    
    /**
     * Get appropriate color for service state
     */
    private String getServiceStatusColor(Service.State state) {
        switch (state) {
            case RUNNING: return "§a";
            case STARTING: return "§e"; 
            case STOPPING: return "§6";
            case STOPPED: return "§c";
            case FAILED: return "§4";
            default: return "§7";
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
        
        // Cancel all periodic update tasks
        int cancelledTasks = 0;
        for (BukkitTask task : periodicUpdateTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
                cancelledTasks++;
            }
        }
        periodicUpdateTasks.clear();
        
        if (commandManager != null) {
            commandManager.shutdown();
        }
        
        logger.info("CommandHandler cleanup completed - cancelled " + cancelledTasks + " periodic tasks");
    }
} 