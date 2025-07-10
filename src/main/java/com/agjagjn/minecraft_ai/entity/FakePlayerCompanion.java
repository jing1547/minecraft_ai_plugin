package com.agjagjn.minecraft_ai.entity;

import com.minecraft.ai.companion.data.CompanionData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class FakePlayerCompanion {
    private final Plugin plugin;
    private final FakePlayer fakePlayer;
    private final Player owner;
    private final CompanionData data;
    private BukkitTask behaviorTask;
    
    // Behavior settings
    private double followDistance = 5.0;
    private double teleportDistance = 30.0;
    
    public FakePlayerCompanion(Plugin plugin, Player owner, CompanionData data) {
        this.plugin = plugin;
        this.owner = owner;
        this.data = data;
        
        // Create the fake player at companion's location
        Location location = data.getLastLocation();
        if (location == null) {
            location = owner.getLocation();
        }
        
        this.fakePlayer = new FakePlayer(plugin, data.getCompanionId(), data.getName(), location);
        
        // Set default equipment
        fakePlayer.setEquipment(
            new ItemStack(Material.DIAMOND_SWORD),
            new ItemStack(Material.SHIELD)
        );
        
        // Spawn for the owner
        fakePlayer.spawn(owner);
        
        // Spawn for nearby players
        spawnForNearbyPlayers();
        
        // Start behavior update task
        startBehaviorTask();
    }
    
    private void spawnForNearbyPlayers() {
        Location loc = fakePlayer.getLocation();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.equals(owner)) continue;
            if (player.getLocation().distance(loc) <= 50) {
                fakePlayer.spawn(player);
            }
        }
    }
    
    private void startBehaviorTask() {
        behaviorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!owner.isOnline()) {
                destroy();
                return;
            }
            
            // Update behavior
            updateMovement();
            updateVisibility();
            
            // Play random animations
            if (Math.random() < 0.05) {
                for (Player observer : fakePlayer.getObservers()) {
                    fakePlayer.playAnimation(observer, FakePlayer.AnimationType.SWING_MAIN_ARM);
                }
            }
        }, 20L, 20L); // Run every second
    }
    
    private void updateMovement() {
        Location ownerLocation = owner.getLocation();
        Location companionLocation = fakePlayer.getLocation();
        
        // Different worlds - teleport
        if (!ownerLocation.getWorld().equals(companionLocation.getWorld())) {
            teleportToOwner();
            return;
        }
        
        double distance = ownerLocation.distance(companionLocation);
        
        // Too far - teleport
        if (distance > teleportDistance) {
            teleportToOwner();
            return;
        }
        
        // Follow the owner
        if (distance > followDistance) {
            // Look at owner
            fakePlayer.lookAt(ownerLocation.clone().add(0, 1.5, 0));
            
            // Calculate movement
            Location targetLocation = calculateTargetLocation(ownerLocation, companionLocation);
            fakePlayer.teleport(targetLocation);
            
            // Update data
            updateLocationData(targetLocation);
        }
    }
    
    private Location calculateTargetLocation(Location owner, Location companion) {
        // Get direction vector
        double dx = owner.getX() - companion.getX();
        double dz = owner.getZ() - companion.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        // Normalize and scale
        double moveDistance = Math.min(distance - 3, 1.5); // Move 1.5 blocks max per tick
        dx = (dx / distance) * moveDistance;
        dz = (dz / distance) * moveDistance;
        
        // Create new location
        Location newLoc = companion.clone();
        newLoc.add(dx, 0, dz);
        
        // Adjust Y position (simple ground following)
        newLoc.setY(owner.getY());
        
        return newLoc;
    }
    
    private void teleportToOwner() {
        Location safeLoc = owner.getLocation().clone();
        safeLoc.add(-2, 0, 0); // Spawn to the side
        
        fakePlayer.teleport(safeLoc);
        updateLocationData(safeLoc);
        
        // Play teleport effect
        owner.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL,
            safeLoc,
            50,
            0.5, 1, 0.5,
            0
        );
    }
    
    private void updateVisibility() {
        Location loc = fakePlayer.getLocation();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != loc.getWorld()) {
                if (fakePlayer.getObservers().contains(player)) {
                    fakePlayer.despawn(player);
                }
                continue;
            }
            
            double distance = player.getLocation().distance(loc);
            boolean isObserving = fakePlayer.getObservers().contains(player);
            
            if (distance <= 50 && !isObserving) {
                fakePlayer.spawn(player);
            } else if (distance > 50 && isObserving) {
                fakePlayer.despawn(player);
            }
        }
    }
    
    private void updateLocationData(Location location) {
        data.setLastLocation(location);
    }
    
    public void destroy() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }
        fakePlayer.destroy();
    }
    
    public void setSkin(String texture, String signature) {
        fakePlayer.setSkin(texture, signature);
    }
    
    // Getters
    public FakePlayer getFakePlayer() { return fakePlayer; }
    public Player getOwner() { return owner; }
    public Location getLocation() { return fakePlayer.getLocation(); }
    public CompanionData getData() { return data; }
    
    public void teleport(Location location) {
        fakePlayer.teleport(location);
        updateLocationData(location);
    }
} 