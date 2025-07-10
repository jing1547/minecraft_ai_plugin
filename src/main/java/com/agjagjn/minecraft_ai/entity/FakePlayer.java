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
        this.location = location;
        
        // Send teleport packet to all observers
        for (Player player : observers) {
            sendTeleportPacket(player);
        }
    }
    
    private void sendPlayerInfoPacket(Player player, boolean add) {
        try {
            PacketContainer packet;
            
            if (add) {
                packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
                packet.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
                
                List<PlayerInfoData> dataList = new ArrayList<>();
                WrappedGameProfile profile = new WrappedGameProfile(uuid, name);
                
                // Add skin properties if available
                if (!skinTexture.isEmpty() && !skinSignature.isEmpty()) {
                    profile.getProperties().put("textures", new WrappedSignedProperty("textures", skinTexture, skinSignature));
                }
                
                dataList.add(new PlayerInfoData(
                    profile,
                    20, // Ping
                    EnumWrappers.NativeGameMode.SURVIVAL,
                    WrappedChatComponent.fromText(name)
                ));
                
                packet.getPlayerInfoDataLists().write(0, dataList);
            } else {
                packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                packet.getUUIDLists().write(0, Collections.singletonList(uuid));
            }
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendSpawnPacket(Player player) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
            packet.getIntegers().write(0, entityId);
            packet.getUUIDs().write(0, uuid);
            packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
            packet.getBytes()
                .write(0, (byte) (location.getYaw() * 256 / 360))
                .write(1, (byte) (location.getPitch() * 256 / 360));
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
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
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            packet.getIntegers().write(0, entityId);
            packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
            packet.getBytes()
                .write(0, (byte) (location.getYaw() * 256 / 360))
                .write(1, (byte) (location.getPitch() * 256 / 360));
            packet.getBooleans().write(0, true); // On Ground
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendEntityMetadataPacket(Player player) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entityId);
            
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            
            // Set entity flags
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class)), (byte) 0);
            
            // Set custom name visibility
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, WrappedDataWatcher.Registry.get(Boolean.class)), true);
            
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void sendEquipmentPacket(Player player) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, entityId);
            
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, mainHand));
            equipment.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, offHand));
            
            packet.getSlotStackPairLists().write(0, equipment);
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ANIMATION);
            packet.getIntegers()
                .write(0, entityId)
                .write(1, animation.getId());
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            packet.getIntegers().write(0, entityId);
            packet.getBytes().write(0, (byte) (yaw * 256 / 360));
            
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
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