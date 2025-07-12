package com.minecraft.ai.brain.commands;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized command manager for AI Brain plugin
 * Handles command registration, permission checking, and tab completion
 */
public class CommandManager {
    
    private final MinecraftAIBrainPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    
    // Command registry
    private final Map<String, AICommand> commands = new HashMap<>();
    private final Map<String, List<String>> commandAliases = new HashMap<>();
    
    /**
     * Interface for AI command implementations
     */
    public interface AICommand {
        /**
         * Execute the command
         */
        CommandResult execute(CommandSender sender, String[] args);
        
        /**
         * Get command description
         */
        String getDescription();
        
        /**
         * Get command usage
         */
        String getUsage();
        
        /**
         * Get required permission
         */
        String getPermission();
        
        /**
         * Get tab completion suggestions
         */
        List<String> getTabCompletions(CommandSender sender, String[] args);
        
        /**
         * Check if command requires player
         */
        boolean requiresPlayer();
    }
    
    /**
     * Command execution result
     */
    public static class CommandResult {
        private final boolean success;
        private final String message;
        private final ChatColor color;
        
        private CommandResult(boolean success, String message, ChatColor color) {
            this.success = success;
            this.message = message;
            this.color = color;
        }
        
        public static CommandResult success(String message) {
            return new CommandResult(true, message, ChatColor.GREEN);
        }
        
        public static CommandResult error(String message) {
            return new CommandResult(false, message, ChatColor.RED);
        }
        
        public static CommandResult warning(String message) {
            return new CommandResult(false, message, ChatColor.YELLOW);
        }
        
        public static CommandResult info(String message) {
            return new CommandResult(true, message, ChatColor.AQUA);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ChatColor getColor() { return color; }
    }
    
    /**
     * Constructor
     */
    public CommandManager(MinecraftAIBrainPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getPluginLogger();
        
        registerDefaultCommands();
        loadCommandAliases();
    }
    
    /**
     * Register a new command
     */
    public void registerCommand(String name, AICommand command) {
        commands.put(name.toLowerCase(), command);
        logger.info("Registered command: " + name);
    }
    
    /**
     * Unregister a command
     */
    public void unregisterCommand(String name) {
        commands.remove(name.toLowerCase());
        logger.info("Unregistered command: " + name);
    }
    
    /**
     * Execute a command
     */
    public CommandResult executeCommand(CommandSender sender, String commandName, String[] args) {
        String normalizedName = commandName.toLowerCase();
        
        // Check for command alias
        String actualCommand = resolveCommandAlias(normalizedName);
        if (actualCommand != null) {
            normalizedName = actualCommand;
        }
        
        // Get command implementation
        AICommand command = commands.get(normalizedName);
        if (command == null) {
            return CommandResult.error("Unknown command: " + commandName);
        }
        
        // Check permission
        if (!hasPermission(sender, command.getPermission())) {
            return CommandResult.error("You don't have permission to use this command.");
        }
        
        // Check if player is required
        if (command.requiresPlayer() && !(sender instanceof Player)) {
            return CommandResult.error("This command can only be used by players.");
        }
        
        try {
            // Execute command
            return command.execute(sender, args);
        } catch (Exception e) {
            logger.severe("Error executing command '" + commandName + "': " + e.getMessage());
            if (configManager.isDebugMode()) {
                e.printStackTrace();
            }
            return CommandResult.error("An error occurred while executing the command.");
        }
    }
    
    /**
     * Get tab completions for a command
     */
    public List<String> getTabCompletions(CommandSender sender, String commandName, String[] args) {
        String normalizedName = commandName.toLowerCase();
        
        // Check for command alias
        String actualCommand = resolveCommandAlias(normalizedName);
        if (actualCommand != null) {
            normalizedName = actualCommand;
        }
        
        // Get command implementation
        AICommand command = commands.get(normalizedName);
        if (command == null) {
            return new ArrayList<>();
        }
        
        // Check permission
        if (!hasPermission(sender, command.getPermission())) {
            return new ArrayList<>();
        }
        
        try {
            return command.getTabCompletions(sender, args);
        } catch (Exception e) {
            logger.warning("Error getting tab completions for '" + commandName + "': " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if sender has permission
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        
        // Allow console to use all commands
        if (!(sender instanceof Player)) {
            return true;
        }
        
        return sender.hasPermission(permission);
    }
    
    /**
     * Resolve command alias to actual command name
     */
    private String resolveCommandAlias(String alias) {
        for (Map.Entry<String, List<String>> entry : commandAliases.entrySet()) {
            if (entry.getValue().contains(alias)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Load command aliases from configuration
     */
    private void loadCommandAliases() {
        try {
            // Load from config if available
            if (configManager.getConfig().contains("commands.custom-aliases")) {
                var aliasSection = configManager.getConfig().getConfigurationSection("commands.custom-aliases");
                if (aliasSection != null) {
                    for (String command : aliasSection.getKeys(false)) {
                        List<String> aliases = aliasSection.getStringList(command);
                        if (!aliases.isEmpty()) {
                            commandAliases.put(command, aliases);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load command aliases: " + e.getMessage());
        }
    }
    
    /**
     * Register default commands
     */
    private void registerDefaultCommands() {
        // Status command
        registerCommand("status", new AICommand() {
            @Override
            public CommandResult execute(CommandSender sender, String[] args) {
                StringBuilder status = new StringBuilder();
                status.append(ChatColor.AQUA).append("=== AI Brain Status ===\n");
                status.append(ChatColor.WHITE).append("Plugin Version: ").append(plugin.getDescription().getVersion()).append("\n");
                status.append(ChatColor.WHITE).append("Configuration Loaded: ").append(configManager.isLoaded() ? "Yes" : "No").append("\n");
                status.append(ChatColor.WHITE).append("WebSocket Host: ").append(configManager.getWebSocketHost()).append("\n");
                status.append(ChatColor.WHITE).append("WebSocket Port: ").append(configManager.getWebSocketPort()).append("\n");
                status.append(ChatColor.WHITE).append("AI Enabled: ").append(configManager.isAIEnabled() ? "Yes" : "No").append("\n");
                status.append(ChatColor.WHITE).append("Debug Mode: ").append(configManager.isDebugMode() ? "Yes" : "No");
                
                return CommandResult.info(status.toString());
            }
            
            @Override
            public String getDescription() {
                return "Show AI Brain plugin status";
            }
            
            @Override
            public String getUsage() {
                return "/ai status";
            }
            
            @Override
            public String getPermission() {
                return "minecraft.ai.use";
            }
            
            @Override
            public List<String> getTabCompletions(CommandSender sender, String[] args) {
                return new ArrayList<>();
            }
            
            @Override
            public boolean requiresPlayer() {
                return false;
            }
        });
        
        // Help command
        registerCommand("help", new AICommand() {
            @Override
            public CommandResult execute(CommandSender sender, String[] args) {
                StringBuilder help = new StringBuilder();
                help.append(ChatColor.AQUA).append("=== AI Brain Commands ===\n");
                
                for (Map.Entry<String, AICommand> entry : commands.entrySet()) {
                    String commandName = entry.getKey();
                    AICommand command = entry.getValue();
                    
                    if (hasPermission(sender, command.getPermission())) {
                        help.append(ChatColor.YELLOW).append(command.getUsage()).append("\n");
                        help.append(ChatColor.WHITE).append("  ").append(command.getDescription()).append("\n");
                    }
                }
                
                return CommandResult.info(help.toString());
            }
            
            @Override
            public String getDescription() {
                return "Show available commands";
            }
            
            @Override
            public String getUsage() {
                return "/ai help";
            }
            
            @Override
            public String getPermission() {
                return "minecraft.ai.use";
            }
            
            @Override
            public List<String> getTabCompletions(CommandSender sender, String[] args) {
                return new ArrayList<>();
            }
            
            @Override
            public boolean requiresPlayer() {
                return false;
            }
        });
        
        // Reload command
        registerCommand("reload", new AICommand() {
            @Override
            public CommandResult execute(CommandSender sender, String[] args) {
                try {
                    configManager.reloadConfig();
                    loadCommandAliases();
                    return CommandResult.success("Configuration reloaded successfully!");
                } catch (Exception e) {
                    return CommandResult.error("Failed to reload configuration: " + e.getMessage());
                }
            }
            
            @Override
            public String getDescription() {
                return "Reload plugin configuration";
            }
            
            @Override
            public String getUsage() {
                return "/ai reload";
            }
            
            @Override
            public String getPermission() {
                return "minecraft.ai.admin";
            }
            
            @Override
            public List<String> getTabCompletions(CommandSender sender, String[] args) {
                return new ArrayList<>();
            }
            
            @Override
            public boolean requiresPlayer() {
                return false;
            }
        });
        
        // Debug command
        registerCommand("debug", new AICommand() {
            @Override
            public CommandResult execute(CommandSender sender, String[] args) {
                if (args.length == 0) {
                    return CommandResult.warning("Usage: /ai debug <toggle|info|logs>");
                }
                
                switch (args[0].toLowerCase()) {
                    case "toggle":
                        boolean currentDebug = configManager.isDebugMode();
                        configManager.setValue("plugin.debug-mode", !currentDebug);
                        return CommandResult.success("Debug mode " + (currentDebug ? "disabled" : "enabled"));
                    
                    case "info":
                        StringBuilder info = new StringBuilder();
                        info.append(ChatColor.AQUA).append("=== Debug Information ===\n");
                        info.append(ChatColor.WHITE).append("Registered Commands: ").append(commands.size()).append("\n");
                        info.append(ChatColor.WHITE).append("Command Aliases: ").append(commandAliases.size()).append("\n");
                        info.append(ChatColor.WHITE).append("Configuration Errors: ").append(configManager.getValidationErrors().size()).append("\n");
                        info.append(ChatColor.WHITE).append("Java Version: ").append(System.getProperty("java.version")).append("\n");
                        info.append(ChatColor.WHITE).append("Memory Usage: ").append(getMemoryUsage());
                        
                        return CommandResult.info(info.toString());
                    
                    case "logs":
                        return CommandResult.info("Debug logs are written to: " + configManager.getLogFilePath());
                    
                    default:
                        return CommandResult.warning("Unknown debug option: " + args[0]);
                }
            }
            
            @Override
            public String getDescription() {
                return "Debug commands and information";
            }
            
            @Override
            public String getUsage() {
                return "/ai debug <toggle|info|logs>";
            }
            
            @Override
            public String getPermission() {
                return "minecraft.ai.debug";
            }
            
            @Override
            public List<String> getTabCompletions(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return Arrays.asList("toggle", "info", "logs")
                            .stream()
                            .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return new ArrayList<>();
            }
            
            @Override
            public boolean requiresPlayer() {
                return false;
            }
        });
    }
    
    /**
     * Get memory usage information
     */
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return String.format("%.1f MB / %.1f MB", 
                usedMemory / 1024.0 / 1024.0, 
                totalMemory / 1024.0 / 1024.0);
    }
    
    /**
     * Get all registered commands
     */
    public Set<String> getRegisteredCommands() {
        return commands.keySet();
    }
    
    /**
     * Get command by name
     */
    public AICommand getCommand(String name) {
        return commands.get(name.toLowerCase());
    }
    
    /**
     * Get all commands the sender has permission to use
     */
    public List<String> getAvailableCommands(CommandSender sender) {
        return commands.entrySet().stream()
                .filter(entry -> hasPermission(sender, entry.getValue().getPermission()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * Shutdown the command manager
     */
    public void shutdown() {
        commands.clear();
        commandAliases.clear();
        logger.info("Command manager shutdown complete");
    }
} 