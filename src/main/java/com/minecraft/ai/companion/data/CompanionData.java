package com.minecraft.ai.companion.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 동료의 데이터를 저장하고 관리하는 클래스
 * 서버 재시작 시 데이터 지속성을 보장합니다.
 */
public class CompanionData implements ConfigurationSerializable {
    
    private UUID companionId;
    private UUID ownerId;
    private String name;
    private Location lastLocation;
    private boolean isActive;
    private long createdTime;
    private Map<String, Object> customData;
    
    // 기본 생성자 (역직렬화용)
    public CompanionData() {
        this.customData = new HashMap<>();
    }
    
    /**
     * AI 동료 데이터를 생성합니다.
     * 
     * @param owner 소유자 플레이어
     * @param name 동료 이름
     * @param location 생성 위치
     */
    public CompanionData(Player owner, String name, Location location) {
        this.companionId = UUID.randomUUID();
        this.ownerId = owner.getUniqueId();
        this.name = name;
        this.lastLocation = location.clone();
        this.isActive = true;
        this.createdTime = System.currentTimeMillis();
        this.customData = new HashMap<>();
    }
    
    /**
     * Map에서 CompanionData 객체를 생성합니다. (역직렬화)
     */
    public static CompanionData deserialize(Map<String, Object> map) {
        CompanionData data = new CompanionData();
        
        data.companionId = UUID.fromString((String) map.get("companionId"));
        data.ownerId = UUID.fromString((String) map.get("ownerId"));
        data.name = (String) map.get("name");
        data.isActive = (Boolean) map.getOrDefault("isActive", true);
        data.createdTime = ((Number) map.getOrDefault("createdTime", System.currentTimeMillis())).longValue();
        
        // Location 역직렬화
        @SuppressWarnings("unchecked")
        Map<String, Object> locMap = (Map<String, Object>) map.get("lastLocation");
        if (locMap != null) {
            data.lastLocation = Location.deserialize(locMap);
        }
        
        // 커스텀 데이터 역직렬화
        @SuppressWarnings("unchecked")
        Map<String, Object> customMap = (Map<String, Object>) map.get("customData");
        if (customMap != null) {
            data.customData = new HashMap<>(customMap);
        } else {
            data.customData = new HashMap<>();
        }
        
        return data;
    }
    
    /**
     * CompanionData 객체를 Map으로 직렬화합니다.
     */
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        
        map.put("companionId", companionId.toString());
        map.put("ownerId", ownerId.toString());
        map.put("name", name);
        map.put("isActive", isActive);
        map.put("createdTime", createdTime);
        
        if (lastLocation != null) {
            map.put("lastLocation", lastLocation.serialize());
        }
        
        if (customData != null && !customData.isEmpty()) {
            map.put("customData", customData);
        }
        
        return map;
    }
    
    // Getter 및 Setter 메서드들
    public UUID getCompanionId() {
        return companionId;
    }
    
    public void setCompanionId(UUID companionId) {
        this.companionId = companionId;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Location getLastLocation() {
        return lastLocation != null ? lastLocation.clone() : null;
    }
    
    public void setLastLocation(Location location) {
        this.lastLocation = location != null ? location.clone() : null;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }
    
    public Map<String, Object> getCustomData() {
        return new HashMap<>(customData);
    }
    
    public void setCustomData(String key, Object value) {
        this.customData.put(key, value);
    }
    
    public Object getCustomData(String key) {
        return this.customData.get(key);
    }
    
    public void removeCustomData(String key) {
        this.customData.remove(key);
    }
    
    @Override
    public String toString() {
        return String.format("CompanionData{id=%s, owner=%s, name='%s', active=%s}", 
                           companionId, ownerId, name, isActive);
    }
} 