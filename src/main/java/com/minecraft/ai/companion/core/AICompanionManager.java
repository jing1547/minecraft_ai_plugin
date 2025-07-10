package com.minecraft.ai.companion.core;

import com.minecraft.ai.companion.MinecraftAICompanionPlugin;
import com.minecraft.ai.companion.data.CompanionData;
import com.minecraft.ai.companion.entity.AICompanionEntity;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 동료 시스템의 핵심 관리 클래스
 * 엔티티 생성, 제거, 지속성 관리를 담당합니다.
 */
public class AICompanionManager {
    
    private final MinecraftAICompanionPlugin plugin;
    
    // 활성화된 AI 동료들 (CompanionID -> AICompanionEntity)
    private final Map<UUID, AICompanionEntity> activeCompanions;
    
    // 플레이어별 동료 데이터 (PlayerID -> CompanionData)
    private final Map<UUID, CompanionData> companionDataMap;
    
    // 데이터 파일
    private File dataFile;
    private FileConfiguration dataConfig;
    
    public AICompanionManager(MinecraftAICompanionPlugin plugin) {
        this.plugin = plugin;
        this.activeCompanions = new HashMap<>();
        this.companionDataMap = new HashMap<>();
        
        // 데이터 파일 초기화
        initializeDataFile();
        
        // 저장된 데이터 로드
        loadCompanionData();
        
        plugin.getLogger().info("✅ AI 동료 관리자가 초기화되었습니다.");
    }
    
    /**
     * 데이터 파일을 초기화합니다.
     */
    private void initializeDataFile() {
        dataFile = new File(plugin.getDataFolder(), "companions.yml");
        
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                plugin.getLogger().info("새로운 동료 데이터 파일을 생성했습니다.");
            } catch (IOException e) {
                plugin.getLogger().severe("동료 데이터 파일 생성 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    /**
     * 저장된 동료 데이터를 로드합니다.
     */
    private void loadCompanionData() {
        if (dataConfig == null) return;
        
        for (String key : dataConfig.getKeys(false)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) dataConfig.get(key);
                CompanionData data = CompanionData.deserialize(dataMap);
                
                companionDataMap.put(data.getOwnerId(), data);
                
                plugin.getLogger().info("동료 데이터 로드: " + data.getName() + " (소유자: " + data.getOwnerId() + ")");
                
            } catch (Exception e) {
                plugin.getLogger().warning("동료 데이터 로드 실패 (키: " + key + "): " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("총 " + companionDataMap.size() + "개의 동료 데이터를 로드했습니다.");
    }
    
    /**
     * 동료 데이터를 저장합니다.
     */
    private void saveCompanionData() {
        if (dataConfig == null) return;
        
        // 기존 데이터 지우기
        for (String key : dataConfig.getKeys(false)) {
            dataConfig.set(key, null);
        }
        
        // 새 데이터 저장
        for (CompanionData data : companionDataMap.values()) {
            dataConfig.set(data.getCompanionId().toString(), data.serialize());
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("동료 데이터 저장 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 플레이어를 위한 AI 동료를 소환합니다.
     * 
     * @param owner 소유자 플레이어
     * @param location 소환 위치
     * @param name 동료 이름 (null이면 기본 이름 사용)
     * @return 소환 성공 여부
     */
    public boolean spawnCompanion(Player owner, Location location, String name) {
        if (owner == null || location == null) {
            return false;
        }
        
        // 이미 동료가 있는지 확인
        if (hasActiveCompanion(owner)) {
            owner.sendMessage(ChatColor.RED + "❌ 이미 활성화된 AI 동료가 있습니다!");
            return false;
        }
        
        // 기본 이름 설정
        if (name == null || name.trim().isEmpty()) {
            name = plugin.getConfig().getString("ai.companion.default-name", "AI동료");
        }
        
        try {
            // 동료 데이터 생성
            CompanionData companionData = new CompanionData(owner, name, location);
            
            // AI 동료 엔티티 생성
            AICompanionEntity companion = new AICompanionEntity(plugin, companionData, owner);
            
            // 엔티티 소환
            if (companion.spawn(location)) {
                // 활성화된 동료 목록에 추가
                activeCompanions.put(companionData.getCompanionId(), companion);
                companionDataMap.put(owner.getUniqueId(), companionData);
                
                // 데이터 저장
                saveCompanionData();
                
                plugin.getLogger().info("AI 동료 '" + name + "'이(가) " + owner.getName() + "에 의해 소환되었습니다.");
                return true;
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("AI 동료 소환 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            owner.sendMessage(ChatColor.RED + "❌ AI 동료 소환 중 오류가 발생했습니다.");
        }
        
        return false;
    }
    
    /**
     * 플레이어의 AI 동료를 제거합니다.
     * 
     * @param owner 소유자 플레이어
     * @return 제거 성공 여부
     */
    public boolean despawnCompanion(Player owner) {
        if (owner == null) return false;
        
        CompanionData data = companionDataMap.get(owner.getUniqueId());
        if (data == null) {
            owner.sendMessage(ChatColor.RED + "❌ 소환된 AI 동료가 없습니다!");
            return false;
        }
        
        return despawnCompanion(data.getCompanionId());
    }
    
    /**
     * 특정 ID의 AI 동료를 제거합니다.
     * 
     * @param companionId 동료 ID
     * @return 제거 성공 여부
     */
    public boolean despawnCompanion(UUID companionId) {
        AICompanionEntity companion = activeCompanions.get(companionId);
        if (companion == null) {
            return false;
        }
        
        try {
            // 엔티티 제거
            companion.despawn();
            
            // 목록에서 제거
            activeCompanions.remove(companionId);
            companionDataMap.remove(companion.getOwner().getUniqueId());
            
            // 데이터 저장
            saveCompanionData();
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("AI 동료 제거 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 모든 AI 동료를 제거합니다.
     */
    public void removeAllCompanions() {
        plugin.getLogger().info("모든 AI 동료를 제거하는 중...");
        
        List<UUID> companionIds = new ArrayList<>(activeCompanions.keySet());
        
        for (UUID companionId : companionIds) {
            despawnCompanion(companionId);
        }
        
        activeCompanions.clear();
        companionDataMap.clear();
        
        plugin.getLogger().info("✅ 모든 AI 동료가 제거되었습니다.");
    }
    
    /**
     * 플레이어에게 텔레포트 명령을 실행합니다.
     * 
     * @param companionId 동료 ID
     * @return 성공 여부
     */
    public boolean teleportToOwner(UUID companionId) {
        AICompanionEntity companion = activeCompanions.get(companionId);
        if (companion == null) return false;
        
        companion.teleportToOwner();
        return true;
    }
    
    /**
     * 플레이어의 활성화된 동료를 반환합니다.
     * 
     * @param owner 소유자 플레이어
     * @return AI 동료 엔티티 (없으면 null)
     */
    public AICompanionEntity getCompanionByOwner(Player owner) {
        if (owner == null) return null;
        
        CompanionData data = companionDataMap.get(owner.getUniqueId());
        if (data == null) return null;
        
        return activeCompanions.get(data.getCompanionId());
    }
    
    /**
     * 특정 ID의 동료를 반환합니다.
     * 
     * @param companionId 동료 ID
     * @return AI 동료 엔티티 (없으면 null)
     */
    public AICompanionEntity getCompanion(UUID companionId) {
        return activeCompanions.get(companionId);
    }
    
    /**
     * 플레이어가 활성화된 동료를 가지고 있는지 확인합니다.
     * 
     * @param owner 소유자 플레이어
     * @return 동료 존재 여부
     */
    public boolean hasActiveCompanion(Player owner) {
        return getCompanionByOwner(owner) != null;
    }
    
    /**
     * 현재 활성화된 모든 동료의 수를 반환합니다.
     * 
     * @return 활성화된 동료 수
     */
    public int getActiveCompanionCount() {
        return activeCompanions.size();
    }
    
    /**
     * 활성화된 모든 동료의 목록을 반환합니다.
     * 
     * @return 동료 엔티티 목록
     */
    public List<AICompanionEntity> getAllActiveCompanions() {
        return new ArrayList<>(activeCompanions.values());
    }
    
    /**
     * 특정 플레이어의 동료 데이터를 반환합니다.
     * 
     * @param owner 소유자 플레이어
     * @return 동료 데이터 (없으면 null)
     */
    public CompanionData getCompanionData(Player owner) {
        if (owner == null) return null;
        return companionDataMap.get(owner.getUniqueId());
    }
} 