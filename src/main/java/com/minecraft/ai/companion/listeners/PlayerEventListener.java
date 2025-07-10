package com.minecraft.ai.companion.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import com.minecraft.ai.companion.MinecraftAICompanionPlugin;

public class PlayerEventListener implements Listener {
    private final MinecraftAICompanionPlugin plugin;
    
    public PlayerEventListener(MinecraftAICompanionPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // TODO: Implement player join handling
    }
} 