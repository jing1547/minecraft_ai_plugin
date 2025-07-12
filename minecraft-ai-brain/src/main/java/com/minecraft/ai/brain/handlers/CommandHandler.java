package com.minecraft.ai.brain.handlers;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.commands.CommandManager;
import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.ChatColor;

import java.util.*;
import java.util.stream.Collectors;

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
        help.append(ChatColor.YELLOW).append("/ai-voice start").append(ChatColor.WHITE).append(" - Start voice processing\n");
        help.append(ChatColor.YELLOW).append("/ai-voice stop").append(ChatColor.WHITE).append(" - Stop voice processing\n");
        help.append(ChatColor.YELLOW).append("/ai-voice test").append(ChatColor.WHITE).append(" - Test voice system\n");
        help.append(ChatColor.YELLOW).append("/ai-voice status").append(ChatColor.WHITE).append(" - Show voice system status");
        
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
        // TODO: Implement voice processing start
        return CommandManager.CommandResult.info("Voice processing is not yet implemented.");
    }
    
    /**
     * Stop voice processing
     */
    private CommandManager.CommandResult stopVoiceProcessing(CommandSender sender) {
        // TODO: Implement voice processing stop
        return CommandManager.CommandResult.info("Voice processing is not yet implemented.");
    }
    
    /**
     * Test voice processing
     */
    private CommandManager.CommandResult testVoiceProcessing(CommandSender sender) {
        // TODO: Implement voice processing test
        return CommandManager.CommandResult.info("Voice processing test is not yet implemented.");
    }
    
    /**
     * Get voice status
     */
    private CommandManager.CommandResult getVoiceStatus(CommandSender sender) {
        // TODO: Implement voice status
        return CommandManager.CommandResult.info("Voice processing is not yet implemented.");
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
            return Arrays.asList("start", "stop", "test", "status")
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