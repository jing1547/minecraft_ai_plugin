package com.minecraft.ai.companion.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;

import com.minecraft.ai.companion.MinecraftAICompanionPlugin;
import com.minecraft.ai.companion.data.CompanionData;

import java.util.UUID;

/**
 * AI 동료 엔티티를 나타내는 클래스
 * Villager 엔티티를 기반으로 하여 AI 기능을 추가합니다.
 */
public class AICompanionEntity {
    
    private final MinecraftAICompanionPlugin plugin;
    private final CompanionData companionData;
    private Villager villagerEntity;
    private Player owner;
    private BukkitTask followTask;
    private BukkitTask healthCheckTask;
    
    // 행동 설정
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double MAX_DISTANCE = 20.0;
    private static final double TELEPORT_DISTANCE = 50.0;
    
    /**
     * AI 동료 엔티티를 생성합니다.
     * 
     * @param plugin 플러그인 인스턴스
     * @param data 동료 데이터
     * @param owner 소유자 플레이어
     */
    public AICompanionEntity(MinecraftAICompanionPlugin plugin, CompanionData data, Player owner) {
        this.plugin = plugin;
        this.companionData = data;
        this.owner = owner;
    }
    
    /**
     * 지정된 위치에 AI 동료를 소환합니다.
     * 
     * @param location 소환 위치
     * @return 소환 성공 여부
     */
    public boolean spawn(Location location) {
        try {
            // Villager 엔티티 생성
            villagerEntity = location.getWorld().spawn(location, Villager.class);
            
            // 엔티티 설정
            setupEntity();
            
            // 행동 시스템 시작
            startBehaviorTasks();
            
            // 소환 메시지 전송
            owner.sendMessage(ChatColor.GREEN + "🤖 AI 동료 '" + companionData.getName() + "'이(가) 소환되었습니다!");
            
            plugin.getLogger().info("AI 동료 '" + companionData.getName() + "'이(가) " + owner.getName() + "에 의해 소환되었습니다.");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("AI 동료 소환 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 엔티티의 기본 설정을 수행합니다.
     */
    private void setupEntity() {
        if (villagerEntity == null) return;
        
        // 이름 설정
        villagerEntity.setCustomName(ChatColor.AQUA + "🤖 " + ChatColor.WHITE + companionData.getName());
        villagerEntity.setCustomNameVisible(true);
        
        // 엔티티 속성 설정
        villagerEntity.setPersistent(true);  // 청크가 언로드되어도 엔티티 유지
        villagerEntity.setRemoveWhenFarAway(false);  // 거리가 멀어져도 제거되지 않음
        villagerEntity.setAI(true);  // AI 활성화
        villagerEntity.setSilent(false);  // 소리 활성화
        
        // 건강 설정
        if (villagerEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            villagerEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0);  // 최대 체력 40
            villagerEntity.setHealth(40.0);
        }
        
        // 이동 속도 설정
        if (villagerEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            villagerEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25);  // 약간 빠르게
        }
        
        // 직업 설정 (없음)
        villagerEntity.setProfession(Villager.Profession.NONE);
        villagerEntity.setVillagerType(Villager.Type.PLAINS);
        
        // 메타데이터에 AI 동료임을 표시
        villagerEntity.setMetadata("AICompanion", new org.bukkit.metadata.FixedMetadataValue(plugin, companionData.getCompanionId().toString()));
        villagerEntity.setMetadata("Owner", new org.bukkit.metadata.FixedMetadataValue(plugin, owner.getUniqueId().toString()));
    }
    
    /**
     * AI 동료의 행동 태스크들을 시작합니다.
     */
    private void startBehaviorTasks() {
        // 플레이어 따라가기 태스크
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) {
                    this.cancel();
                    return;
                }
                
                followOwner();
            }
        }.runTaskTimer(plugin, 20L, 20L);  // 1초마다 실행
        
        // 건강 상태 체크 태스크
        healthCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) {
                    this.cancel();
                    return;
                }
                
                checkHealth();
            }
        }.runTaskTimer(plugin, 100L, 100L);  // 5초마다 실행
    }
    
    /**
     * 플레이어 따라가기 로직
     */
    private void followOwner() {
        if (!isValid() || !owner.isOnline()) return;
        
        Location companionLoc = villagerEntity.getLocation();
        Location ownerLoc = owner.getLocation();
        
        // 같은 월드가 아니면 텔레포트
        if (!companionLoc.getWorld().equals(ownerLoc.getWorld())) {
            teleportToOwner();
            return;
        }
        
        double distance = companionLoc.distance(ownerLoc);
        
        // 너무 멀면 텔레포트
        if (distance > TELEPORT_DISTANCE) {
            teleportToOwner();
            return;
        }
        
        // 적당한 거리면 따라가기
        if (distance > FOLLOW_DISTANCE && distance <= MAX_DISTANCE) {
            // 간단한 벡터 이동 방식 사용
            org.bukkit.util.Vector direction = ownerLoc.toVector().subtract(companionLoc.toVector()).normalize();
            direction.multiply(0.5); // 이동 속도 조절
            direction.setY(0); // Y축 이동 제한
            
            villagerEntity.setVelocity(direction);
        }
    }
    
    /**
     * 소유자에게 텔레포트합니다.
     */
    public void teleportToOwner() {
        if (!isValid() || !owner.isOnline()) return;
        
        Location teleportLoc = owner.getLocation().clone();
        // 플레이어 주변 안전한 위치 찾기
        teleportLoc = findSafeLocation(teleportLoc);
        
        villagerEntity.teleport(teleportLoc);
        
        // 위치 업데이트
        companionData.setLastLocation(teleportLoc);
        
        plugin.getLogger().info("AI 동료 '" + companionData.getName() + "'이(가) 소유자에게 텔레포트했습니다.");
    }
    
    /**
     * 안전한 텔레포트 위치를 찾습니다.
     */
    private Location findSafeLocation(Location center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location testLoc = center.clone().add(x, 0, z);
                
                // 땅 위의 공기 블록 2개가 있는 위치 찾기
                if (testLoc.getBlock().getType().isAir() && 
                    testLoc.clone().add(0, 1, 0).getBlock().getType().isAir() &&
                    !testLoc.clone().add(0, -1, 0).getBlock().getType().isAir()) {
                    return testLoc;
                }
            }
        }
        return center;  // 안전한 위치를 찾지 못한 경우 원래 위치 반환
    }
    
    /**
     * 건강 상태를 체크합니다.
     */
    private void checkHealth() {
        if (!isValid()) return;
        
        // 체력이 낮으면 알림
        double health = villagerEntity.getHealth();
        double maxHealth = villagerEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        
        if (health < maxHealth * 0.3) {  // 체력이 30% 미만
            owner.sendMessage(ChatColor.RED + "⚠️ AI 동료 '" + companionData.getName() + "'의 체력이 부족합니다! (" + 
                            String.format("%.1f", health) + "/" + String.format("%.1f", maxHealth) + ")");
        }
    }
    
    /**
     * AI 동료를 제거합니다.
     */
    public void despawn() {
        // 태스크 정리
        if (followTask != null && !followTask.isCancelled()) {
            followTask.cancel();
        }
        if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
            healthCheckTask.cancel();
        }
        
        // 엔티티 제거
        if (villagerEntity != null && !villagerEntity.isDead()) {
            // 마지막 위치 저장
            companionData.setLastLocation(villagerEntity.getLocation());
            companionData.setActive(false);
            
            villagerEntity.remove();
        }
        
        // 제거 메시지
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.YELLOW + "👋 AI 동료 '" + companionData.getName() + "'이(가) 제거되었습니다.");
        }
        
        plugin.getLogger().info("AI 동료 '" + companionData.getName() + "'이(가) 제거되었습니다.");
    }
    
    /**
     * 엔티티가 유효한지 확인합니다.
     */
    public boolean isValid() {
        return villagerEntity != null && !villagerEntity.isDead() && villagerEntity.isValid();
    }
    
    /**
     * AI 동료에게 메시지를 전송합니다. (추후 AI 응답 시스템과 연동)
     */
    public void sendMessage(String message) {
        if (!isValid()) return;
        
        // 일단 간단한 응답
        owner.sendMessage(ChatColor.AQUA + "[" + companionData.getName() + "] " + ChatColor.WHITE + "네, 알겠습니다!");
        
        // TODO: 실제 AI 응답 시스템과 연동
    }
    
    // Getter 메서드들
    public CompanionData getCompanionData() {
        return companionData;
    }
    
    public Villager getVillagerEntity() {
        return villagerEntity;
    }
    
    public Player getOwner() {
        return owner;
    }
    
    public UUID getCompanionId() {
        return companionData.getCompanionId();
    }
    
    public String getName() {
        return companionData.getName();
    }
} 