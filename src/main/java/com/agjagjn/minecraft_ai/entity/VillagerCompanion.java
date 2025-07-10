package com.agjagjn.minecraft_ai.entity;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Particle;

import com.minecraft.ai.companion.data.CompanionData;

import java.util.Random;
import java.util.UUID;

/**
 * 안정적인 Villager NPC 기반 AI 동료 클래스
 * 
 * FakePlayer 시스템의 호환성 문제를 해결하기 위해 Bukkit API의 
 * Villager 엔티티를 사용하여 안정적인 AI 동료 시스템을 구현합니다.
 */
public class VillagerCompanion implements AICompanionInterface {
    
    private final JavaPlugin plugin;
    private final Player owner;
    private final CompanionData companionData;
    private Villager villager;
    private BukkitTask behaviorTask;
    private final Random random = new Random();
    
    // 행동 설정
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double TELEPORT_DISTANCE = 15.0;
    private static final int BEHAVIOR_INTERVAL = 20; // 1초 (20 틱)
    
    public VillagerCompanion(JavaPlugin plugin, Player owner, CompanionData companionData) {
        this.plugin = plugin;
        this.owner = owner;
        this.companionData = companionData;
        
        spawnVillager();
        startBehaviorTask();
    }
    
    /**
     * Villager NPC 소환
     */
    private void spawnVillager() {
        Location spawnLocation = companionData.getLastLocation();
        
        // Villager 소환
        villager = (Villager) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
        
        // 기본 설정
        villager.setCustomName(ChatColor.GREEN + "🤖 " + companionData.getName());
        villager.setCustomNameVisible(true);
        villager.setAI(true);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        
        // 직업 설정 (외관상 구분을 위해)
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerLevel(5);
        
        // AI 제거 (우리가 직접 제어)
        villager.setAI(false);
        
        // 소환 효과
        spawnLocation.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, spawnLocation, 10, 0.5, 0.5, 0.5, 0);
        spawnLocation.getWorld().playSound(spawnLocation, Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.0f);
        
        plugin.getLogger().info("안정적인 Villager 동료 '" + companionData.getName() + "'가 소환되었습니다.");
    }
    
    /**
     * 행동 태스크 시작
     */
    private void startBehaviorTask() {
        behaviorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (villager == null || villager.isDead() || !owner.isOnline()) {
                    cancel();
                    return;
                }
                
                updateBehavior();
            }
        }.runTaskTimer(plugin, 0L, BEHAVIOR_INTERVAL);
    }
    
    /**
     * AI 행동 업데이트
     */
    private void updateBehavior() {
        if (villager == null || villager.isDead() || !owner.isOnline()) {
            return;
        }
        
        Location ownerLocation = owner.getLocation();
        Location villagerLocation = villager.getLocation();
        
        // 같은 월드에 있는지 확인
        if (!ownerLocation.getWorld().equals(villagerLocation.getWorld())) {
            // 다른 월드에 있으면 텔레포트
            teleportToOwner();
            return;
        }
        
        double distance = ownerLocation.distance(villagerLocation);
        
        // 너무 멀리 있으면 텔레포트
        if (distance > TELEPORT_DISTANCE) {
            teleportToOwner();
            return;
        }
        
        // 적절한 거리 유지
        if (distance > FOLLOW_DISTANCE) {
            followOwner();
        } else {
            // 가까이 있을 때 랜덤 행동
            performRandomBehavior();
        }
        
        // 주인을 향해 바라보기
        lookAtOwner();
    }
    
    /**
     * 주인에게 텔레포트
     */
    private void teleportToOwner() {
        Location ownerLocation = owner.getLocation();
        Location safeLocation = findSafeLocation(ownerLocation);
        
        if (safeLocation != null) {
            villager.teleport(safeLocation);
            
            // 텔레포트 효과
            safeLocation.getWorld().spawnParticle(Particle.PORTAL, safeLocation, 10, 0.5, 0.5, 0.5, 0);
            safeLocation.getWorld().playSound(safeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            
            plugin.getLogger().info("AI 동료 '" + companionData.getName() + "'가 주인에게 텔레포트했습니다.");
        }
    }
    
    /**
     * 주인을 따라가기
     */
    private void followOwner() {
        Location ownerLocation = owner.getLocation();
        Location villagerLocation = villager.getLocation();
        
        // 목표 지점 계산 (주인 뒤쪽)
        Location targetLocation = ownerLocation.clone();
        targetLocation.setX(targetLocation.getX() + (random.nextDouble() - 0.5) * 2);
        targetLocation.setZ(targetLocation.getZ() + (random.nextDouble() - 0.5) * 2);
        
        // 안전한 위치로 이동
        Location safeTarget = findSafeLocation(targetLocation);
        if (safeTarget != null) {
            // 부드러운 이동을 위해 속도 설정
            villager.setVelocity(safeTarget.toVector().subtract(villagerLocation.toVector()).normalize().multiply(0.3));
        }
    }
    
    /**
     * 주인을 바라보기
     */
    private void lookAtOwner() {
        Location ownerLocation = owner.getLocation();
        Location villagerLocation = villager.getLocation();
        
        // 시선 방향 계산
        double dx = ownerLocation.getX() - villagerLocation.getX();
        double dz = ownerLocation.getZ() - villagerLocation.getZ();
        
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        
        // 부드러운 회전
        Location newLocation = villagerLocation.clone();
        newLocation.setYaw(yaw);
        villager.teleport(newLocation);
    }
    
    /**
     * 랜덤 행동 수행
     */
    private void performRandomBehavior() {
        if (random.nextInt(100) < 5) { // 5% 확률
            switch (random.nextInt(3)) {
                case 0:
                    // 점프
                    villager.setVelocity(villager.getVelocity().add(new org.bukkit.util.Vector(0, 0.3, 0)));
                    break;
                case 1:
                    // 소리
                    villager.getWorld().playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, 1.0f);
                    break;
                case 2:
                    // 파티클
                    villager.getWorld().spawnParticle(Particle.HEART, villager.getLocation().add(0, 2, 0), 1);
                    break;
            }
        }
    }
    
    /**
     * 안전한 위치 찾기
     */
    private Location findSafeLocation(Location location) {
        Location safeLocation = location.clone();
        
        // 고체 블록 위에 있는지 확인
        for (int i = 0; i < 5; i++) {
            Location checkLocation = safeLocation.clone().add(0, -i, 0);
            if (checkLocation.getBlock().getType().isSolid()) {
                safeLocation = checkLocation.add(0, 1, 0);
                break;
            }
        }
        
        return safeLocation;
    }
    
    /**
     * 동료 제거
     */
    public void remove() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
            behaviorTask = null;
        }
        
        if (villager != null && !villager.isDead()) {
            // 제거 효과
            Location location = villager.getLocation();
            location.getWorld().spawnParticle(Particle.CLOUD, location, 10, 0.5, 0.5, 0.5, 0);
            location.getWorld().playSound(location, Sound.ENTITY_VILLAGER_DEATH, 0.5f, 1.0f);
            
            villager.remove();
            villager = null;
            
            plugin.getLogger().info("AI 동료 '" + companionData.getName() + "'가 제거되었습니다.");
        }
    }
    
    /**
     * 명령 처리
     */
    public void processCommand(String command) {
        if (villager == null || villager.isDead()) {
            return;
        }
        
        switch (command.toLowerCase()) {
            case "come":
            case "이리와":
                teleportToOwner();
                owner.sendMessage(ChatColor.GREEN + "🤖 " + companionData.getName() + ": 네, 주인님!");
                break;
            case "stay":
            case "기다려":
                // 행동 태스크 일시 중지
                if (behaviorTask != null) {
                    behaviorTask.cancel();
                }
                owner.sendMessage(ChatColor.YELLOW + "🤖 " + companionData.getName() + ": 여기서 기다리고 있을게요!");
                break;
            case "follow":
            case "따라와":
                // 행동 태스크 재시작
                startBehaviorTask();
                owner.sendMessage(ChatColor.GREEN + "🤖 " + companionData.getName() + ": 따라가겠습니다!");
                break;
            default:
                owner.sendMessage(ChatColor.GRAY + "🤖 " + companionData.getName() + ": 무슨 말씀인지 모르겠어요...");
                break;
        }
    }
    
    // Getters
    public Player getOwner() { return owner; }
    public CompanionData getCompanionData() { return companionData; }
    public Villager getVillager() { return villager; }
    public boolean isValid() { return villager != null && !villager.isDead(); }
    public Location getLocation() { return villager != null ? villager.getLocation() : null; }
    
    /**
     * 상태 정보 반환
     */
    public String getStatus() {
        if (villager == null || villager.isDead()) {
            return "❌ 비활성화";
        }
        
        if (behaviorTask == null) {
            return "⏸️ 대기 중";
        }
        
        return "✅ 활성화";
    }
    
    /**
     * 동료를 특정 위치로 텔레포트
     * 
     * @param location 목표 위치
     */
    @Override
    public void teleport(Location location) {
        if (villager != null && !villager.isDead()) {
            Location safeLocation = findSafeLocation(location);
            if (safeLocation != null) {
                villager.teleport(safeLocation);
                
                // 텔레포트 효과
                safeLocation.getWorld().spawnParticle(Particle.PORTAL, safeLocation, 10, 0.5, 0.5, 0.5, 0);
                safeLocation.getWorld().playSound(safeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            }
        }
    }
    
    /**
     * 동료 유형 반환
     * 
     * @return 동료 유형
     */
    @Override
    public String getType() {
        return "Villager";
    }
} 