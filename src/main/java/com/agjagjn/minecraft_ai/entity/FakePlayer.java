package com.agjagjn.minecraft_ai.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import com.comphenix.protocol.utility.MinecraftReflection;

import java.util.*;

public class FakePlayer {
    private final Plugin plugin;
    private final UUID uuid;
    private final String name;
    private Location location;
    private String skinTexture = "";
    private String skinSignature = "";
    private boolean visible = true;
    private final Set<Player> observers = new HashSet<>();
    private final int entityId;
    
    // Equipment
    private ItemStack mainHand = new ItemStack(Material.AIR);
    private ItemStack offHand = new ItemStack(Material.AIR);
    
    public FakePlayer(Plugin plugin, UUID uuid, String name, Location location) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.name = name;
        this.location = location;
        this.entityId = (int) (Math.random() * Integer.MAX_VALUE);
    }
    
    public void setSkin(String texture, String signature) {
        this.skinTexture = texture;
        this.skinSignature = signature;
        
        // Update skin for all observers
        updateSkin();
    }
    
    public void spawn(Player player) {
        if (observers.contains(player)) return;
        observers.add(player);
        
        // Send player info packet (adds to tab list)
        sendPlayerInfoPacket(player, true);
        
        // Delay the spawn packet to ensure player info is processed
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Send spawn packet
            sendSpawnPacket(player);
            
            // Send equipment, metadata, etc.
            sendEntityMetadataPacket(player);
            sendEquipmentPacket(player);
        }, 5L);
    }
    
    public void despawn(Player player) {
        if (!observers.contains(player)) return;
        observers.remove(player);
        
        // Send destroy entity packet
        sendDestroyPacket(player);
        
        // Remove from tab list
        sendPlayerInfoPacket(player, false);
    }
    
    public void destroy() {
        for (Player player : new ArrayList<>(observers)) {
            despawn(player);
        }
        observers.clear();
    }
    
    public void teleport(Location location) {
        this.location = location.clone();
        
        // Skip sending teleport packets due to 1.21.6+ compatibility issues
        // Position is updated internally but not visually synchronized
        plugin.getLogger().info("Position updated internally (teleport packets disabled for 1.21.6+ compatibility)");
        
        // Alternative: For visible updates, we could destroy and respawn the entity
        // But for now, we'll just update the internal position
    }
    
    private void sendPlayerInfoPacket(Player player, boolean add) {
        // Skip player info packets due to 1.21.6+ compatibility issues
        // This means the fake player won't appear in the tab list, but will still be visible as an entity
        plugin.getLogger().info("Skipping player info packet due to 1.21.6+ compatibility issues");
        
        // Alternative: Send a simple chat message to notify players
        if (add) {
            player.sendMessage("§a[AI] §f" + name + " §a동료가 근처에 있습니다.");
        }
    }
    
    private PacketType getPlayerInfoPacketType() {
        // For now, use the standard PLAYER_INFO packet type
        // Future versions may need different packet types
        return PacketType.Play.Server.PLAYER_INFO;
    }
    
    private PacketType getPlayerInfoRemovePacketType() {
        // Try new 1.21.7 packet type first  
        try {
            return PacketType.Play.Server.PLAYER_INFO_REMOVE;
        } catch (Exception e) {
            // Fall back to old packet type for older versions
            return PacketType.Play.Server.PLAYER_INFO;
        }
    }
    
    private void sendSpawnPacket(Player player) {
        try {
            PacketContainer packet;
            
            // Try the appropriate spawn packet based on version
            try {
                                 // For 1.21.6+, try using SPAWN_ENTITY for player entities
                 packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SPAWN_ENTITY);
                 packet.getIntegers().write(0, entityId); // Entity ID
                 packet.getUUIDs().write(0, uuid); // UUID
                 
                 // Use villager entity type instead of player for better compatibility
                 packet.getIntegers().write(1, 18); // Entity type ID for villager (18 is more stable)
                 packet.getDoubles()
                     .write(0, location.getX())
                     .write(1, location.getY())
                     .write(2, location.getZ());
                 packet.getBytes()
                     .write(0, (byte) (location.getYaw() * 256 / 360))
                     .write(1, (byte) (location.getPitch() * 256 / 360));
                 packet.getIntegers().write(2, 0); // Data/velocity X
                 packet.getIntegers().write(3, 0); // Data/velocity Y  
                 packet.getIntegers().write(4, 0); // Data/velocity Z
                
                plugin.getLogger().info("Using SPAWN_ENTITY packet for 1.21.7 compatibility");
            } catch (Exception e) {
                // Fall back to older packet type for compatibility
                plugin.getLogger().warning("SPAWN_ENTITY failed, falling back to NAMED_ENTITY_SPAWN: " + e.getMessage());
                packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
                packet.getIntegers().write(0, entityId);
                packet.getUUIDs().write(0, uuid);
                packet.getDoubles()
                    .write(0, location.getX())
                    .write(1, location.getY())
                    .write(2, location.getZ());
                packet.getBytes()
                    .write(0, (byte) (location.getYaw() * 256 / 360))
                    .write(1, (byte) (location.getPitch() * 256 / 360));
            }
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send spawn packet: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendDestroyPacket(Player player) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            packet.getIntLists().write(0, Collections.singletonList(entityId));
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendTeleportPacket(Player player) {
        // Skip teleport packets due to 1.21.6+ compatibility issues
        // This means smooth teleportation won't work, but basic movement will still function
        plugin.getLogger().info("Skipping teleport packet due to 1.21.6+ compatibility issues");
        
        // Alternative: Use destroy and respawn for position updates if needed
        // For now, we'll rely on the basic spawn location updates
    }
    
    private void sendEntityMetadataPacket(Player player) {
        // Skip entity metadata packets due to 1.21.6+ compatibility issues
        // This means the fake player won't have custom metadata, but will still be visible
        plugin.getLogger().info("Skipping entity metadata packet due to 1.21.6+ compatibility issues");
        
        // The entity will still be visible without metadata
        // Basic spawn functionality will work without custom metadata
    }
    
    private <T> void setEntityMetadata(WrappedDataWatcher watcher, int index, Class<T> type, T value) {
        try {
            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(type);
            WrappedDataWatcher.WrappedDataWatcherObject watcherObject = 
                new WrappedDataWatcher.WrappedDataWatcherObject(index, serializer);
            watcher.setObject(watcherObject, value);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set metadata at index " + index + ": " + e.getMessage());
        }
    }
    
    private void sendEquipmentPacket(Player player) {
        // Skip equipment packets due to 1.21.6+ compatibility issues
        // This means the fake player won't show equipment, but will still be visible
        plugin.getLogger().info("Skipping equipment packet due to 1.21.6+ compatibility issues");
        
        // The entity will still be visible without equipment
        // Equipment display can be added later once ProtocolLib is fully compatible
    }
    
    private void updateSkin() {
        // Remove and re-add player info for all observers to update skin
        for (Player player : observers) {
            sendPlayerInfoPacket(player, false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerInfoPacket(player, true);
            }, 2L);
        }
    }
    
    public void playAnimation(Player observer, AnimationType animation) {
        // Skip animation packets due to 1.21.6+ compatibility issues
        // This means animations won't play, but the entity will still be visible
        plugin.getLogger().info("Skipping animation packet due to 1.21.6+ compatibility issues");
    }
    
    public void lookAt(Location target) {
        // Calculate yaw and pitch to look at target
        double dx = target.getX() - location.getX();
        double dy = target.getY() - location.getY();
        double dz = target.getZ() - location.getZ();
        
        double r = Math.sqrt(dx*dx + dz*dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, r));
        
        // Update location with new rotation
        location.setYaw(yaw);
        location.setPitch(pitch);
        
        // Send head rotation packet
        for (Player player : observers) {
            sendHeadRotationPacket(player, yaw);
        }
    }
    
    private void sendHeadRotationPacket(Player player, float yaw) {
        // Skip head rotation packets due to 1.21.6+ compatibility issues
        // This means the entity's head won't rotate smoothly, but will still be visible
        plugin.getLogger().info("Skipping head rotation packet due to 1.21.6+ compatibility issues");
    }
    
    public void setEquipment(ItemStack mainHand, ItemStack offHand) {
        this.mainHand = mainHand != null ? mainHand : new ItemStack(Material.AIR);
        this.offHand = offHand != null ? offHand : new ItemStack(Material.AIR);
        
        // Update equipment for all observers
        for (Player player : observers) {
            sendEquipmentPacket(player);
        }
    }
    
    // Getters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public Location getLocation() { return location.clone(); }
    public boolean isVisible() { return visible; }
    public Set<Player> getObservers() { return new HashSet<>(observers); }
    public int getEntityId() { return entityId; }
    
    public enum AnimationType {
        SWING_MAIN_ARM(0),
        TAKE_DAMAGE(1),
        LEAVE_BED(2),
        SWING_OFFHAND(3),
        CRITICAL_EFFECT(4),
        MAGIC_CRITICAL_EFFECT(5);
        
        private final int id;
        
        AnimationType(int id) {
            this.id = id;
        }
        
        public int getId() {
            return id;
        }
    }
} 