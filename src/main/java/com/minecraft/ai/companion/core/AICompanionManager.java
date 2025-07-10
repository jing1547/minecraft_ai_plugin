package com.minecraft.ai.companion.core;

import com.minecraft.ai.companion.MinecraftAICompanionPlugin;
import com.minecraft.ai.companion.data.CompanionData;
import com.minecraft.ai.companion.entity.AICompanionEntity;
import com.agjagjn.minecraft_ai.entity.FakePlayerCompanion;

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
    private final DependencyManager dependencyManager;
    
    // 활성화된 AI 동료들 (CompanionID -> FakePlayerCompanion)
    private final Map<UUID, FakePlayerCompanion> activeCompanions;
    
    // 플레이어별 동료 데이터 목록 (PlayerID -> List<CompanionData>)
    private final Map<UUID, List<CompanionData>> companionDataMap;
    
    // 데이터 파일
    private File dataFile;
    private FileConfiguration dataConfig;
    
    // 플레이어당 최대 AI 동료 수
    private final int maxCompanionsPerPlayer;
    
    public AICompanionManager(MinecraftAICompanionPlugin plugin, DependencyManager dependencyManager) {
        this.plugin = plugin;
        this.dependencyManager = dependencyManager;
        this.activeCompanions = new HashMap<>();
        this.companionDataMap = new HashMap<>();
        
        // 설정에서 최대 동료 수 읽기
        this.maxCompanionsPerPlayer = plugin.getConfig().getInt("ai.companion.max-per-player", 5);
        
        // 데이터 파일 초기화
        initializeDataFile();
        
        // 저장된 데이터 로드
        loadCompanionData();
        
        plugin.getLogger().info("✅ AI 동료 관리자가 초기화되었습니다. (플레이어당 최대 " + maxCompanionsPerPlayer + "명)");
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
                
                // 플레이어별 목록에 추가
                UUID ownerId = data.getOwnerId();
                companionDataMap.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(data);
                
                plugin.getLogger().info("동료 데이터 로드: " + data.getName() + " (소유자: " + ownerId + ")");
                
            } catch (Exception e) {
                plugin.getLogger().warning("동료 데이터 로드 실패 (키: " + key + "): " + e.getMessage());
            }
        }
        
        int totalCompanions = companionDataMap.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("총 " + totalCompanions + "개의 동료 데이터를 로드했습니다.");
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
        for (List<CompanionData> companions : companionDataMap.values()) {
            for (CompanionData data : companions) {
                dataConfig.set(data.getCompanionId().toString(), data.serialize());
            }
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("동료 데이터 저장 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 서버 시작 시 저장된 동료들을 재생성합니다.
     */
    public void respawnSavedCompanions() {
        plugin.getLogger().info("저장된 AI 동료들을 재생성하는 중...");
        
        for (Map.Entry<UUID, List<CompanionData>> entry : companionDataMap.entrySet()) {
            Player owner = plugin.getServer().getPlayer(entry.getKey());
            
            if (owner != null && owner.isOnline()) {
                for (CompanionData data : entry.getValue()) {
                    if (data.isActive()) {
                        respawnCompanion(owner, data);
                    }
                }
            }
        }
    }
    
    /**
     * 저장된 데이터로부터 동료를 재생성합니다.
     */
    private void respawnCompanion(Player owner, CompanionData data) {
        try {
            FakePlayerCompanion companion = new FakePlayerCompanion(plugin, owner, data);
            activeCompanions.put(data.getCompanionId(), companion);
            
            plugin.getLogger().info("AI 동료 '" + data.getName() + "'이(가) 재생성되었습니다.");
        } catch (Exception e) {
            plugin.getLogger().severe("AI 동료 재생성 실패: " + e.getMessage());
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
        
        // 현재 플레이어의 동료 수 확인
        List<CompanionData> playerCompanions = companionDataMap.getOrDefault(owner.getUniqueId(), new ArrayList<>());
        long activeCount = playerCompanions.stream().filter(CompanionData::isActive).count();
        
        if (activeCount >= maxCompanionsPerPlayer) {
            owner.sendMessage(ChatColor.RED + "❌ 최대 " + maxCompanionsPerPlayer + "명의 AI 동료만 소환할 수 있습니다!");
            return false;
        }
        
        // 기본 이름 설정
        if (name == null || name.trim().isEmpty()) {
            name = plugin.getConfig().getString("ai.companion.default-name", "AI동료") + "_" + (activeCount + 1);
        }
        
        try {
            // 동료 데이터 생성
            CompanionData companionData = new CompanionData(owner, name, location);
            
            // ProtocolLib 사용 가능 여부에 따라 동료 생성
            FakePlayerCompanion companion = null;
            
            if (dependencyManager.isProtocolLibAvailable()) {
                try {
                    // ProtocolLib 기반 고급 동료 생성
                    companion = new FakePlayerCompanion(plugin, owner, companionData);
                    owner.sendMessage(ChatColor.GREEN + "🚀 고급 AI 동료 '" + name + "'이(가) 소환되었습니다! (실제 플레이어처럼 표시됨)");
                } catch (NoClassDefFoundError | Exception e) {
                    plugin.getLogger().warning("ProtocolLib 기반 동료 생성 실패, 기본 모드로 전환: " + e.getMessage());
                    // fallback을 위해 companion은 null로 유지
                }
            }
            
            if (companion == null) {
                // ProtocolLib이 없거나 생성 실패 시 안내 메시지
                owner.sendMessage(ChatColor.YELLOW + "⚠️ ProtocolLib이 설치되지 않아 기본 모드로 동작합니다.");
                owner.sendMessage(ChatColor.YELLOW + "💡 더 나은 AI 동료 경험을 위해 ProtocolLib 설치를 권장합니다:");
                owner.sendMessage(ChatColor.AQUA + "   https://www.spigotmc.org/resources/protocollib.1997/");
                owner.sendMessage(ChatColor.RED + "❌ 현재 AI 동료 기능을 사용할 수 없습니다.");
                return false;
            }
            
            // 활성화된 동료 목록에 추가
            activeCompanions.put(companionData.getCompanionId(), companion);
            companionDataMap.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(companionData);
            
            // 데이터 저장
            saveCompanionData();
            
            plugin.getLogger().info("AI 동료 '" + name + "'이(가) " + owner.getName() + "에 의해 소환되었습니다.");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("AI 동료 소환 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            owner.sendMessage(ChatColor.RED + "❌ AI 동료 소환 중 오류가 발생했습니다.");
            owner.sendMessage(ChatColor.YELLOW + "💡 ProtocolLib이 설치되어 있는지 확인해보세요.");
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
        
        List<CompanionData> playerCompanions = companionDataMap.get(owner.getUniqueId());
        if (playerCompanions == null || playerCompanions.isEmpty()) {
            owner.sendMessage(ChatColor.RED + "❌ 소환된 AI 동료가 없습니다!");
            return false;
        }
        
        // 활성화된 동료 중 하나를 제거
        CompanionData dataToDespawn = null;
        for (CompanionData data : playerCompanions) {
            if (data.isActive()) {
                dataToDespawn = data;
                break;
            }
        }

        if (dataToDespawn == null) {
            owner.sendMessage(ChatColor.RED + "❌ 활성화된 AI 동료가 없습니다!");
            return false;
        }

        return despawnCompanion(dataToDespawn.getCompanionId());
    }
    
    /**
     * 특정 ID의 AI 동료를 제거합니다.
     * 
     * @param companionId 동료 ID
     * @return 제거 성공 여부
     */
    public boolean despawnCompanion(UUID companionId) {
        FakePlayerCompanion companion = activeCompanions.get(companionId);
        if (companion == null) {
            return false;
        }
        
        try {
            // 엔티티 제거
            companion.destroy();
            
            // 목록에서 제거
            activeCompanions.remove(companionId);
            
            // 플레이어별 목록에서 제거
            companionDataMap.values().forEach(companions -> companions.removeIf(data -> data.getCompanionId().equals(companionId)));
            
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
        FakePlayerCompanion companion = activeCompanions.get(companionId);
        if (companion == null) return false;
        
        // FakePlayerCompanion은 자동으로 플레이어를 따라가므로 강제 텔레포트
        Location ownerLoc = companion.getOwner().getLocation();
        companion.teleport(ownerLoc.clone().add(2, 0, 0));
        return true;
    }
    
    /**
     * 플레이어의 활성화된 동료를 반환합니다.
     * 
     * @param owner 소유자 플레이어
     * @return AI 동료 엔티티 (없으면 null)
     */
    public FakePlayerCompanion getCompanionByOwner(Player owner) {
        if (owner == null) return null;
        
        List<CompanionData> playerCompanions = companionDataMap.get(owner.getUniqueId());
        if (playerCompanions == null || playerCompanions.isEmpty()) return null;

        // 활성화된 동료 중 하나를 반환
        return activeCompanions.values().stream()
                .filter(companion -> playerCompanions.stream().anyMatch(data -> data.getCompanionId().equals(companion.getData().getCompanionId())))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 특정 ID의 동료를 반환합니다.
     * 
     * @param companionId 동료 ID
     * @return AI 동료 엔티티 (없으면 null)
     */
    public FakePlayerCompanion getCompanion(UUID companionId) {
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
    public List<FakePlayerCompanion> getAllActiveCompanions() {
        return new ArrayList<>(activeCompanions.values());
    }
    
    /**
     * 특정 플레이어의 동료 데이터를 반환합니다.
     * 
     * @param owner 소유자 플레이어
     * @return 동료 데이터 (없으면 null)
     */
    public List<CompanionData> getCompanionData(Player owner) {
        if (owner == null) return null;
        return companionDataMap.get(owner.getUniqueId());
    }
} 