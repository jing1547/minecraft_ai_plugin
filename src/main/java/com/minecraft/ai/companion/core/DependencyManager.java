package com.minecraft.ai.companion.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages external plugin dependencies and provides graceful fallback handling
 */
public class DependencyManager {
    private final JavaPlugin plugin;
    private boolean protocolLibAvailable = false;
    private String protocolLibVersion = "Unknown";
    
    public DependencyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        checkDependencies();
    }
    
    /**
     * Check all required and optional dependencies
     */
    private void checkDependencies() {
        checkProtocolLib();
    }
    
    /**
     * Check ProtocolLib availability and version
     */
    private void checkProtocolLib() {
        Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        
        if (protocolLib != null && protocolLib.isEnabled()) {
            protocolLibAvailable = true;
            protocolLibVersion = protocolLib.getDescription().getVersion();
            plugin.getLogger().info("§a✓ ProtocolLib 발견: " + protocolLibVersion);
            plugin.getLogger().info("§a  고급 AI 컴패니언 기능이 활성화됩니다.");
        } else {
            protocolLibAvailable = false;
            plugin.getLogger().warning("§c⚠ ProtocolLib이 설치되지 않았거나 비활성화되었습니다!");
            plugin.getLogger().warning("§c  기본 villager NPC 모드로 동작합니다.");
            plugin.getLogger().warning("§e  고급 기능을 사용하려면 ProtocolLib을 설치하세요:");
            plugin.getLogger().warning("§e  다운로드: https://www.spigotmc.org/resources/protocollib.1997/");
            plugin.getLogger().warning("§e  또는: https://github.com/dmulloy2/ProtocolLib/releases");
        }
    }
    
    /**
     * Check if ProtocolLib is available and loaded
     * @return true if ProtocolLib is available
     */
    public boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
    }
    
    /**
     * Get ProtocolLib version if available
     * @return ProtocolLib version string or "Not Available"
     */
    public String getProtocolLibVersion() {
        return protocolLibAvailable ? protocolLibVersion : "설치되지 않음";
    }
    
    /**
     * Get dependency status summary for commands
     * @return formatted dependency status string
     */
    public String getDependencyStatus() {
        StringBuilder status = new StringBuilder();
        status.append("§6=== 의존성 상태 ===\n");
        status.append("§7ProtocolLib: ");
        
        if (protocolLibAvailable) {
            status.append("§a✓ 사용 가능 (").append(protocolLibVersion).append(")\n");
            status.append("§a  → 고급 AI 컴패니언 기능 활성화");
        } else {
            status.append("§c✗ 사용 불가\n");
            status.append("§c  → 기본 villager NPC 모드로 제한\n");
            status.append("§e  → 설치 방법: /aic help dependencies");
        }
        
        return status.toString();
    }
    
    /**
     * Get installation instructions for missing dependencies
     * @return formatted installation instructions
     */
    public String getInstallationInstructions() {
        if (protocolLibAvailable) {
            return "§a모든 의존성이 올바르게 설치되었습니다!";
        }
        
        StringBuilder instructions = new StringBuilder();
        instructions.append("§6=== ProtocolLib 설치 방법 ===\n");
        instructions.append("§71. 다음 링크에서 ProtocolLib을 다운로드하세요:\n");
        instructions.append("§b   https://www.spigotmc.org/resources/protocollib.1997/\n");
        instructions.append("§b   또는: https://github.com/dmulloy2/ProtocolLib/releases\n");
        instructions.append("§72. 다운로드한 ProtocolLib.jar 파일을 서버의 plugins 폴더에 넣으세요\n");
        instructions.append("§73. 서버를 재시작하세요\n");
        instructions.append("§74. 설치 확인: /aic status\n");
        instructions.append("§e\n설치 후 AI 컴패니언이 실제 플레이어처럼 나타나고\n");
        instructions.append("§e탭 리스트에도 표시됩니다!");
        
        return instructions.toString();
    }
} 