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

public class FakePlayerCompanion implements AICompanionInterface {
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
    
    // AICompanionInterface 구현 메서드들
    
    /**
     * 동료 제거 (destroy와 동일)
     */
    @Override
    public void remove() {
        destroy();
    }
    
    /**
     * 동료 명령 처리
     */
    @Override
    public void processCommand(String command) {
        // FakePlayer는 시각적으로만 존재하므로 기본 명령만 처리
        switch (command.toLowerCase()) {
            case "come":
            case "이리와":
                teleportToOwner();
                owner.sendMessage("§a🤖 " + data.getName() + ": 네, 주인님!");
                break;
            case "stay":
            case "기다려":
                // 행동 일시정지 (followDistance를 0으로 설정)
                followDistance = 0.0;
                owner.sendMessage("§e🤖 " + data.getName() + ": 여기서 기다리겠습니다!");
                break;
            case "follow":
            case "따라와":
                // 행동 재개
                followDistance = 5.0;
                owner.sendMessage("§a🤖 " + data.getName() + ": 따라가겠습니다!");
                break;
            default:
                owner.sendMessage("§7🤖 " + data.getName() + ": 무슨 말씀인지 모르겠어요...");
                break;
        }
    }
    
    /**
     * 동료 데이터 반환
     */
    @Override
    public CompanionData getCompanionData() {
        return data;
    }
    
    /**
     * 동료 유효성 확인
     */
    @Override
    public boolean isValid() {
        return fakePlayer != null && owner != null && owner.isOnline();
    }
    
    /**
     * 동료 상태 반환
     */
    @Override
    public String getStatus() {
        if (!isValid()) {
            return "❌ 비활성화";
        }
        
        if (followDistance == 0.0) {
            return "⏸️ 대기 중";
        }
        
        return "✅ 활성화";
    }
    
    /**
     * 동료 유형 반환
     */
    @Override
    public String getType() {
        return "FakePlayer";
    }
} 