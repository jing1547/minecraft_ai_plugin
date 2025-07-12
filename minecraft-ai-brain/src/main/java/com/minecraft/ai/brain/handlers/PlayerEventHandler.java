package com.minecraft.ai.brain.handlers;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.interaction.PlayerInteractionManager;
import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive event handler for player interactions and server events
 * Integrates with PlayerInteractionManager to track all player activities
 */
public class PlayerEventHandler implements Listener {
    
    private final MinecraftAIBrainPlugin plugin;
    private final PlayerInteractionManager interactionManager;
    private final ConfigManager configManager;
    private final Logger logger;
    
    // Rate limiting for events
    private final Map<UUID, Long> lastMoveEventTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChatEventTimes = new ConcurrentHashMap<>();
    private final long moveEventThreshold = 100; // 100ms
    private final long chatEventThreshold = 1000; // 1 second
    
    // Statistics tracking
    private long totalEventsProcessed = 0;
    private final Map<String, Long> eventTypeCounts = new ConcurrentHashMap<>();
    
    public PlayerEventHandler(MinecraftAIBrainPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getPluginLogger();
        this.interactionManager = new PlayerInteractionManager(plugin);
        
        logger.info("PlayerEventHandler initialized with PlayerInteractionManager");
    }
    
    // ===== Player Connection Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        incrementEventCount("PlayerJoin");
        
        Player player = event.getPlayer();
        logger.info("Player joined: " + player.getName() + " (" + player.getUniqueId() + ")");
        
        interactionManager.onPlayerJoin(player);
        
        // Send welcome message if enabled
        if (configManager.getConfig().getBoolean("player-interaction.send-welcome-message", true)) {
            player.sendMessage("§aWelcome! AI Brain plugin is monitoring for better gameplay experience.");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        incrementEventCount("PlayerQuit");
        
        Player player = event.getPlayer();
        logger.info("Player quit: " + player.getName());
        
        interactionManager.onPlayerQuit(player);
        
        // Cleanup rate limiting data
        UUID playerId = player.getUniqueId();
        lastMoveEventTimes.remove(playerId);
        lastChatEventTimes.remove(playerId);
    }
    
    // ===== Player Movement Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!configManager.getConfig().getBoolean("player-interaction.track-movements", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Rate limiting
        Long lastMoveTime = lastMoveEventTimes.get(playerId);
        long currentTime = System.currentTimeMillis();
        if (lastMoveTime != null && currentTime - lastMoveTime < moveEventThreshold) {
            return;
        }
        lastMoveEventTimes.put(playerId, currentTime);
        
        incrementEventCount("PlayerMove");
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to != null && from.distance(to) > 0.1) {
            interactionManager.onPlayerMove(player, from, to);
            
            if (configManager.isDebugMode()) {
                logger.debug("Player " + player.getName() + " moved " + 
                           String.format("%.2f", from.distance(to)) + " blocks");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        incrementEventCount("PlayerTeleport");
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("teleportCause", event.getCause().toString());
            data.put("distance", from.distance(to));
            
            interactionManager.onPlayerInteraction(player, 
                PlayerInteractionManager.PlayerInteraction.InteractionType.MOVE, 
                to, data);
            
            logger.debug("Player " + player.getName() + " teleported via " + event.getCause());
        }
    }
    
    // ===== Player Communication Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!configManager.getConfig().getBoolean("player-interaction.track-chat", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Rate limiting for chat
        Long lastChatTime = lastChatEventTimes.get(playerId);
        long currentTime = System.currentTimeMillis();
        if (lastChatTime != null && currentTime - lastChatTime < chatEventThreshold) {
            return;
        }
        lastChatEventTimes.put(playerId, currentTime);
        
        incrementEventCount("PlayerChat");
        
        String message = event.getMessage();
        interactionManager.onPlayerChat(player, message);
        
        // Check for AI interaction triggers
        if (shouldTriggerAIResponse(message)) {
            handleAITrigger(player, message);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!configManager.getConfig().getBoolean("player-interaction.track-commands", false)) {
            return;
        }
        
        incrementEventCount("PlayerCommand");
        
        Player player = event.getPlayer();
        String command = event.getMessage();
        
        Map<String, Object> data = new HashMap<>();
        data.put("command", command);
        data.put("fullCommand", command);
        
        interactionManager.onPlayerInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.COMMAND, 
            player.getLocation(), data);
        
        logger.debug("Player " + player.getName() + " executed command: " + command);
    }
    
    // ===== Player Block Interaction Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        incrementEventCount("BlockBreak");
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        interactionManager.onPlayerBlockInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.BLOCK_BREAK, 
            block.getLocation(), 
            block.getType().toString());
        
        if (configManager.isDebugMode()) {
            logger.debug("Player " + player.getName() + " broke " + block.getType() + 
                        " at " + formatLocation(block.getLocation()));
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        incrementEventCount("BlockPlace");
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        interactionManager.onPlayerBlockInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.BLOCK_PLACE, 
            block.getLocation(), 
            block.getType().toString());
        
        if (configManager.isDebugMode()) {
            logger.debug("Player " + player.getName() + " placed " + block.getType() + 
                        " at " + formatLocation(block.getLocation()));
        }
    }
    
    // ===== Player Item/Entity Interaction Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        incrementEventCount("PlayerInteract");
        
        Player player = event.getPlayer();
        
        Map<String, Object> data = new HashMap<>();
        data.put("action", event.getAction().toString());
        
        if (event.getClickedBlock() != null) {
            data.put("clickedBlock", event.getClickedBlock().getType().toString());
            data.put("blockLocation", formatLocation(event.getClickedBlock().getLocation()));
        }
        
        if (event.getItem() != null) {
            data.put("item", event.getItem().getType().toString());
            data.put("itemAmount", event.getItem().getAmount());
        }
        
        interactionManager.onPlayerInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.ITEM_USE, 
            player.getLocation(), data);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        incrementEventCount("PlayerItemHeld");
        
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        
        Map<String, Object> data = new HashMap<>();
        data.put("newSlot", event.getNewSlot());
        data.put("previousSlot", event.getPreviousSlot());
        
        if (newItem != null && newItem.getType() != Material.AIR) {
            data.put("newItem", newItem.getType().toString());
            data.put("itemAmount", newItem.getAmount());
        }
        
        interactionManager.onPlayerInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.ITEM_USE, 
            player.getLocation(), data);
    }
    
    // ===== Player Status Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        incrementEventCount("PlayerDeath");
        
        Player player = event.getEntity();
        
        Map<String, Object> data = new HashMap<>();
        data.put("deathMessage", event.getDeathMessage());
        
        if (player.getLastDamageCause() != null) {
            data.put("damageCause", player.getLastDamageCause().getCause().toString());
        }
        
        data.put("droppedExp", event.getDroppedExp());
        data.put("keepInventory", event.getKeepInventory());
        data.put("keepLevel", event.getKeepLevel());
        
        interactionManager.onPlayerInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.RESPAWN, 
            player.getLocation(), data);
        
        logger.info("Player " + player.getName() + " died: " + event.getDeathMessage());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        incrementEventCount("PlayerRespawn");
        
        Player player = event.getPlayer();
        Location respawnLocation = event.getRespawnLocation();
        
        Map<String, Object> data = new HashMap<>();
        data.put("respawnLocation", formatLocation(respawnLocation));
        data.put("isBedSpawn", event.isBedSpawn());
        data.put("isAnchorSpawn", event.isAnchorSpawn());
        
        interactionManager.onPlayerInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.RESPAWN, 
            respawnLocation, data);
        
        logger.info("Player " + player.getName() + " respawned at " + formatLocation(respawnLocation));
    }
    
    // ===== World Events =====
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        incrementEventCount("WorldChange");
        
        Player player = event.getPlayer();
        
        Map<String, Object> data = new HashMap<>();
        data.put("previousWorld", event.getFrom().getName());
        data.put("newWorld", player.getWorld().getName());
        
        interactionManager.onPlayerInteraction(player, 
            PlayerInteractionManager.PlayerInteraction.InteractionType.WORLD_CHANGE, 
            player.getLocation(), data);
        
        logger.info("Player " + player.getName() + " changed world: " + 
                   event.getFrom().getName() + " -> " + player.getWorld().getName());
    }
    
    // ===== Utility Methods =====
    
    /**
     * Check if message should trigger AI response
     */
    private boolean shouldTriggerAIResponse(String message) {
        if (!configManager.shouldAutoRespond()) {
            return false;
        }
        
        // Check for AI keywords
        String lowerMessage = message.toLowerCase();
        String[] aiTriggers = {"ai", "bot", "assistant", "help", "how", "what", "why", "when", "where"};
        
        for (String trigger : aiTriggers) {
            if (lowerMessage.contains(trigger)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handle AI interaction trigger
     */
    private void handleAITrigger(Player player, String message) {
        // Rate limiting for AI responses
        if (!canSendAIResponse(player)) {
            return;
        }
        
        // Schedule AI response (placeholder for now)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage("§b[AI] I heard you mention something that might need assistance. " +
                                 "AI functionality is being developed!");
            }
        }, configManager.getResponseDelay() / 50L); // Convert ms to ticks
        
        logger.debug("AI trigger detected for player " + player.getName() + ": " + message);
    }
    
    /**
     * Check if can send AI response (rate limiting)
     */
    private boolean canSendAIResponse(Player player) {
        // TODO: Implement proper rate limiting based on config
        return true;
    }
    
    /**
     * Format location for logging
     */
    private String formatLocation(Location location) {
        return String.format("%.1f,%.1f,%.1f in %s", 
                location.getX(), location.getY(), location.getZ(), 
                location.getWorld().getName());
    }
    
    /**
     * Increment event counter
     */
    private void incrementEventCount(String eventType) {
        totalEventsProcessed++;
        eventTypeCounts.merge(eventType, 1L, Long::sum);
    }
    
    /**
     * Get event statistics
     */
    public Map<String, Object> getEventStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEventsProcessed", totalEventsProcessed);
        stats.put("eventTypeCounts", new HashMap<>(eventTypeCounts));
        
        // Add interaction manager stats
        Map<String, Object> interactionStats = interactionManager.getStatistics();
        stats.put("interactionManager", interactionStats);
        
        return stats;
    }
    
    /**
     * Get the interaction manager
     */
    public PlayerInteractionManager getInteractionManager() {
        return interactionManager;
    }
    
    /**
     * Reset statistics
     */
    public void resetStatistics() {
        totalEventsProcessed = 0;
        eventTypeCounts.clear();
        logger.info("Event statistics reset");
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        // Cleanup rate limiting data
        lastMoveEventTimes.clear();
        lastChatEventTimes.clear();
        
        // Shutdown interaction manager
        if (interactionManager != null) {
            interactionManager.shutdown();
        }
        
        // Clear statistics
        eventTypeCounts.clear();
        
        logger.info("PlayerEventHandler cleanup completed - " + totalEventsProcessed + " events processed");
    }
} 