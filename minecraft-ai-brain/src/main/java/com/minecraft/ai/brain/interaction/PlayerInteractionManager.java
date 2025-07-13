package com.minecraft.ai.brain.interaction;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.utils.ConfigManager;
import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Manager for tracking player interactions, movements, and proximity detection
 * Provides real-time monitoring of player activities for AI interaction purposes
 */
public class PlayerInteractionManager {
    
    private final MinecraftAIBrainPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    
    // Player tracking data
    private final Map<UUID, PlayerActivity> playerActivities = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastKnownLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractionTimes = new ConcurrentHashMap<>();
    
    // Proximity tracking
    private final Map<UUID, Set<UUID>> nearbyPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Entity>> nearbyEntities = new ConcurrentHashMap<>();
    
    // Interaction history
    private final List<PlayerInteraction> interactionHistory = new CopyOnWriteArrayList<>();
    private final int maxHistorySize = 1000;
    
    // Background tasks
    private BukkitTask proximityCheckTask;
    private BukkitTask cleanupTask;
    
    /**
     * Player activity data holder
     */
    public static class PlayerActivity {
        private final UUID playerId;
        private final String playerName;
        private Location currentLocation;
        private Location previousLocation;
        private long lastMoveTime;
        private long lastChatTime;
        private long lastInteractionTime;
        private int movementCount;
        private double totalDistanceMoved;
        private boolean isActive;
        
        public PlayerActivity(UUID playerId, String playerName, Location initialLocation) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.currentLocation = initialLocation.clone();
            this.previousLocation = initialLocation.clone();
            this.lastMoveTime = System.currentTimeMillis();
            this.isActive = true;
        }
        
        public void updateLocation(Location newLocation) {
            if (currentLocation != null) {
                this.previousLocation = currentLocation.clone();
                this.totalDistanceMoved += currentLocation.distance(newLocation);
            }
            this.currentLocation = newLocation.clone();
            this.lastMoveTime = System.currentTimeMillis();
            this.movementCount++;
            this.isActive = true;
        }
        
        public void updateChatTime() {
            this.lastChatTime = System.currentTimeMillis();
            this.isActive = true;
        }
        
        public void updateInteractionTime() {
            this.lastInteractionTime = System.currentTimeMillis();
            this.isActive = true;
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public Location getCurrentLocation() { return currentLocation != null ? currentLocation.clone() : null; }
        public Location getPreviousLocation() { return previousLocation != null ? previousLocation.clone() : null; }
        public long getLastMoveTime() { return lastMoveTime; }
        public long getLastChatTime() { return lastChatTime; }
        public long getLastInteractionTime() { return lastInteractionTime; }
        public int getMovementCount() { return movementCount; }
        public double getTotalDistanceMoved() { return totalDistanceMoved; }
        public boolean isActive() { return isActive; }
        
        public void setActive(boolean active) { this.isActive = active; }
        
        /**
         * Check if player has been inactive for specified time
         */
        public boolean isInactive(long inactivityThresholdMs) {
            long lastActivity = Math.max(Math.max(lastMoveTime, lastChatTime), lastInteractionTime);
            return System.currentTimeMillis() - lastActivity > inactivityThresholdMs;
        }
        
        /**
         * Get movement speed in blocks per second
         */
        public double getMovementSpeed() {
            if (movementCount < 2 || totalDistanceMoved == 0) {
                return 0.0;
            }
            long timeDiff = System.currentTimeMillis() - (lastMoveTime - (movementCount * 50)); // Approximate
            return timeDiff > 0 ? (totalDistanceMoved / (timeDiff / 1000.0)) : 0.0;
        }
    }
    
    /**
     * Player interaction record
     */
    public static class PlayerInteraction {
        private final UUID playerId;
        private final String playerName;
        private final InteractionType type;
        private final Location location;
        private final long timestamp;
        private final Map<String, Object> data;
        
        public enum InteractionType {
            MOVE, CHAT, BLOCK_BREAK, BLOCK_PLACE, ITEM_USE, ENTITY_INTERACT, 
            COMMAND, LOGIN, LOGOUT, WORLD_CHANGE, RESPAWN
        }
        
        public PlayerInteraction(UUID playerId, String playerName, InteractionType type, Location location) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.type = type;
            this.location = location != null ? location.clone() : null;
            this.timestamp = System.currentTimeMillis();
            this.data = new HashMap<>();
        }
        
        public PlayerInteraction(UUID playerId, String playerName, InteractionType type, Location location, Map<String, Object> data) {
            this(playerId, playerName, type, location);
            if (data != null) {
                this.data.putAll(data);
            }
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public InteractionType getType() { return type; }
        public Location getLocation() { return location != null ? location.clone() : null; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getData() { return new HashMap<>(data); }
        
        @Override
        public String toString() {
            return String.format("PlayerInteraction{player=%s, type=%s, location=%s, time=%d}", 
                    playerName, type, location, timestamp);
        }
    }
    
    /**
     * Constructor
     */
    public PlayerInteractionManager(MinecraftAIBrainPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getPluginLogger();
        
        startBackgroundTasks();
        logger.info("PlayerInteractionManager initialized");
    }
    
    /**
     * Start background monitoring tasks
     */
    private void startBackgroundTasks() {
        // Proximity check task (every 2 seconds) - MUST run on main thread due to getNearbyEntities
        proximityCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, 
            this::performProximityCheck, 40L, 40L);
        
        // Cleanup task (every 5 minutes) - can run async since it doesn't use Bukkit API
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, 
            this::performCleanup, 6000L, 6000L);
        
        logger.info("Background monitoring tasks started");
    }
    
    /**
     * Track player join
     */
    public void onPlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Location location = player.getLocation();
        
        PlayerActivity activity = new PlayerActivity(playerId, playerName, location);
        playerActivities.put(playerId, activity);
        lastKnownLocations.put(playerId, location.clone());
        
        recordInteraction(playerId, playerName, PlayerInteraction.InteractionType.LOGIN, location);
        logger.debug("Player joined: " + playerName + " at " + formatLocation(location));
    }
    
    /**
     * Track player quit
     */
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Location location = player.getLocation();
        
        recordInteraction(playerId, playerName, PlayerInteraction.InteractionType.LOGOUT, location);
        
        // Don't remove immediately - keep for a while for analysis
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity != null) {
            activity.setActive(false);
        }
        
        logger.debug("Player quit: " + playerName);
    }
    
    /**
     * Track player movement
     */
    public void onPlayerMove(Player player, Location from, Location to) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        
        // Only track significant movement (more than 0.1 blocks)
        if (from.distance(to) < 0.1) {
            return;
        }
        
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity == null) {
            activity = new PlayerActivity(playerId, playerName, from);
            playerActivities.put(playerId, activity);
        }
        
        activity.updateLocation(to);
        lastKnownLocations.put(playerId, to.clone());
        
        // Record significant movements (more than 5 blocks from last recorded position)
        Location lastRecorded = getLastRecordedLocation(playerId);
        if (lastRecorded == null || lastRecorded.distance(to) > 5.0) {
            recordInteraction(playerId, playerName, PlayerInteraction.InteractionType.MOVE, to);
        }
        
        updateLastInteractionTime(playerId);
    }
    
    /**
     * Track player chat
     */
    public void onPlayerChat(Player player, String message) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Location location = player.getLocation();
        
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity != null) {
            activity.updateChatTime();
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        data.put("messageLength", message.length());
        
        recordInteraction(playerId, playerName, PlayerInteraction.InteractionType.CHAT, location, data);
        updateLastInteractionTime(playerId);
        
        logger.debug("Player chat: " + playerName + " said: " + message);
    }
    
    /**
     * Track player block interaction
     */
    public void onPlayerBlockInteraction(Player player, PlayerInteraction.InteractionType type, Location blockLocation, String blockType) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity != null) {
            activity.updateInteractionTime();
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("blockType", blockType);
        data.put("blockLocation", formatLocation(blockLocation));
        
        recordInteraction(playerId, playerName, type, blockLocation, data);
        updateLastInteractionTime(playerId);
    }
    
    /**
     * Track general player interaction
     */
    public void onPlayerInteraction(Player player, PlayerInteraction.InteractionType type, Location location, Map<String, Object> additionalData) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity != null) {
            activity.updateInteractionTime();
        }
        
        recordInteraction(playerId, playerName, type, location, additionalData);
        updateLastInteractionTime(playerId);
    }
    
    /**
     * Get player activity data
     */
    public PlayerActivity getPlayerActivity(UUID playerId) {
        return playerActivities.get(playerId);
    }
    
    /**
     * Get player activity by name
     */
    public PlayerActivity getPlayerActivity(String playerName) {
        return playerActivities.values().stream()
                .filter(activity -> activity.getPlayerName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get all active players
     */
    public List<PlayerActivity> getActivePlayers() {
        return playerActivities.values().stream()
                .filter(PlayerActivity::isActive)
                .collect(Collectors.toList());
    }
    
    /**
     * Get players within radius of location
     */
    public List<PlayerActivity> getPlayersInRadius(Location center, double radius) {
        return playerActivities.values().stream()
                .filter(PlayerActivity::isActive)
                .filter(activity -> {
                    Location playerLoc = activity.getCurrentLocation();
                    return playerLoc != null && 
                           playerLoc.getWorld().equals(center.getWorld()) &&
                           playerLoc.distance(center) <= radius;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get players near a specific player
     */
    public List<PlayerActivity> getPlayersNear(UUID playerId, double radius) {
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity == null || activity.getCurrentLocation() == null) {
            return new ArrayList<>();
        }
        
        return getPlayersInRadius(activity.getCurrentLocation(), radius);
    }
    
    /**
     * Get interaction history for a player
     */
    public List<PlayerInteraction> getPlayerInteractionHistory(UUID playerId, int limit) {
        return interactionHistory.stream()
                .filter(interaction -> interaction.getPlayerId().equals(playerId))
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get recent interactions of specific type
     */
    public List<PlayerInteraction> getRecentInteractions(PlayerInteraction.InteractionType type, long sinceMs) {
        long cutoff = System.currentTimeMillis() - sinceMs;
        return interactionHistory.stream()
                .filter(interaction -> interaction.getType() == type && interaction.getTimestamp() > cutoff)
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if player is near location
     */
    public boolean isPlayerNearLocation(UUID playerId, Location location, double radius) {
        PlayerActivity activity = playerActivities.get(playerId);
        if (activity == null || activity.getCurrentLocation() == null) {
            return false;
        }
        
        Location playerLoc = activity.getCurrentLocation();
        return playerLoc.getWorld().equals(location.getWorld()) && 
               playerLoc.distance(location) <= radius;
    }
    
    /**
     * Perform proximity check for all players
     */
    private void performProximityCheck() {
        double interactionRadius = configManager.getPlayerInteractionRadius();
        double notificationRadius = configManager.getPlayerNotificationRadius();
        
        try {
            for (PlayerActivity activity : getActivePlayers()) {
                UUID playerId = activity.getPlayerId();
                Location playerLoc = activity.getCurrentLocation();
                
                if (playerLoc == null) continue;
                
                // Verify player is still online and world is valid
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || playerLoc.getWorld() == null) {
                    continue;
                }
                
                // Find nearby players
                Set<UUID> nearbyPlayerIds = getPlayersInRadius(playerLoc, interactionRadius)
                        .stream()
                        .map(PlayerActivity::getPlayerId)
                        .filter(id -> !id.equals(playerId))
                        .collect(Collectors.toSet());
                
                nearbyPlayers.put(playerId, nearbyPlayerIds);
                
                // Find nearby entities (if enabled)
                if (configManager.getConfig().getBoolean("player-interaction.track-entities", true)) {
                    try {
                        Set<Entity> entities = playerLoc.getWorld()
                                .getNearbyEntities(playerLoc, interactionRadius, interactionRadius, interactionRadius)
                                .stream()
                                .filter(entity -> !(entity instanceof Player))
                                .collect(Collectors.toSet());
                        
                        nearbyEntities.put(playerId, entities);
                    } catch (Exception entityException) {
                        logger.warning("Error getting nearby entities for player " + activity.getPlayerName() + ": " + entityException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error during proximity check: " + e.getMessage());
        }
    }
    
    /**
     * Perform cleanup of old data
     */
    private void performCleanup() {
        try {
            long inactivityThreshold = 30 * 60 * 1000; // 30 minutes
            long historyRetention = 24 * 60 * 60 * 1000; // 24 hours
            long now = System.currentTimeMillis();
            
            // Remove inactive player activities
            playerActivities.entrySet().removeIf(entry -> {
                PlayerActivity activity = entry.getValue();
                return !activity.isActive() && activity.isInactive(inactivityThreshold);
            });
            
            // Clean up old interaction history
            interactionHistory.removeIf(interaction -> 
                now - interaction.getTimestamp() > historyRetention);
            
            // Trim history if too large
            while (interactionHistory.size() > maxHistorySize) {
                interactionHistory.remove(0);
            }
            
            // Clean up location tracking
            lastKnownLocations.entrySet().removeIf(entry -> 
                !playerActivities.containsKey(entry.getKey()));
            
            logger.debug("Cleanup completed - Active players: " + playerActivities.size() + 
                        ", History entries: " + interactionHistory.size());
                        
        } catch (Exception e) {
            logger.warning("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Record a player interaction
     */
    private void recordInteraction(UUID playerId, String playerName, PlayerInteraction.InteractionType type, Location location) {
        recordInteraction(playerId, playerName, type, location, null);
    }
    
    private void recordInteraction(UUID playerId, String playerName, PlayerInteraction.InteractionType type, Location location, Map<String, Object> data) {
        PlayerInteraction interaction = new PlayerInteraction(playerId, playerName, type, location, data);
        interactionHistory.add(interaction);
        
        // Trim if necessary
        if (interactionHistory.size() > maxHistorySize) {
            interactionHistory.remove(0);
        }
    }
    
    /**
     * Update last interaction time
     */
    private void updateLastInteractionTime(UUID playerId) {
        lastInteractionTimes.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * Get last recorded location for interaction
     */
    private Location getLastRecordedLocation(UUID playerId) {
        return interactionHistory.stream()
                .filter(i -> i.getPlayerId().equals(playerId) && i.getType() == PlayerInteraction.InteractionType.MOVE)
                .max(Comparator.comparing(PlayerInteraction::getTimestamp))
                .map(PlayerInteraction::getLocation)
                .orElse(null);
    }
    
    /**
     * Format location for display
     */
    private String formatLocation(Location location) {
        if (location == null) return "null";
        return String.format("%.1f,%.1f,%.1f in %s", 
                location.getX(), location.getY(), location.getZ(), 
                location.getWorld().getName());
    }
    
    /**
     * Get statistics about tracked players
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalPlayers", playerActivities.size());
        stats.put("activePlayers", getActivePlayers().size());
        stats.put("interactionHistorySize", interactionHistory.size());
        stats.put("totalInteractionsRecorded", interactionHistory.size());
        
        // Interaction type breakdown
        Map<PlayerInteraction.InteractionType, Long> typeCounts = interactionHistory.stream()
                .collect(Collectors.groupingBy(PlayerInteraction::getType, Collectors.counting()));
        stats.put("interactionsByType", typeCounts);
        
        return stats;
    }
    
    /**
     * Shutdown the interaction manager
     */
    public void shutdown() {
        if (proximityCheckTask != null) {
            proximityCheckTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        playerActivities.clear();
        lastKnownLocations.clear();
        lastInteractionTimes.clear();
        nearbyPlayers.clear();
        nearbyEntities.clear();
        interactionHistory.clear();
        
        logger.info("PlayerInteractionManager shutdown completed");
    }
} 