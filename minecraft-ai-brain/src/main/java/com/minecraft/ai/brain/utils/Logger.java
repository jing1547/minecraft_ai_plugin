package com.minecraft.ai.brain.utils;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Custom logger wrapper for the AI Brain plugin
 * TODO: Implement full logging functionality in future subtasks
 */
public class Logger {
    
    private final JavaPlugin plugin;
    private final java.util.logging.Logger bukkitLogger;
    
    public Logger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.bukkitLogger = plugin.getLogger();
    }
    
    public void info(String message) {
        bukkitLogger.info(message);
    }
    
    public void warning(String message) {
        bukkitLogger.warning(message);
    }
    
    public void severe(String message) {
        bukkitLogger.severe(message);
    }
    
    public void debug(String message) {
        // TODO: Implement debug level checking
        bukkitLogger.info("[DEBUG] " + message);
    }
} 