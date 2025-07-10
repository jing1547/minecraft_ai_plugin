package com.minecraft.ai.companion;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import com.minecraft.ai.companion.core.AICompanionManager;
import com.minecraft.ai.companion.core.ConfigManager;
import com.minecraft.ai.companion.core.DependencyManager;
import com.minecraft.ai.companion.commands.AICompanionCommandExecutor;
import com.minecraft.ai.companion.listeners.PlayerEventListener;

import java.util.logging.Logger;

/**
 * 마인크래프트 AI 동료 플러그인의 메인 클래스
 * 
 * 이 플러그인은 다음 기능을 제공합니다:
 * - AI 동료 엔티티 생성 및 관리 (ProtocolLib 사용 시 고급 기능)
 * - 한국어 음성 인식 및 출력
 * - 플레이어와의 실시간 상호작용
 * - Yes Steve Model 통합
 */
public final class MinecraftAICompanionPlugin extends JavaPlugin {
    
    private static MinecraftAICompanionPlugin instance;
    private Logger logger;
    
    // 핵심 매니저 클래스들
    private AICompanionManager companionManager;
    private ConfigManager configManager;
    private DependencyManager dependencyManager;
    
    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        
        // 플러그인 시작 메시지
        logger.info("=".repeat(50));
        logger.info("마인크래프트 AI 동료 플러그인이 시작됩니다...");
        logger.info("버전: " + getDescription().getVersion());
        logger.info("=".repeat(50));
        
        try {
            // 의존성 확인 (ProtocolLib이 없어도 계속 진행)
            dependencyManager = new DependencyManager(this);
            
            // 초기화 단계들
            initializeConfig();
            initializeManagers();
            registerCommands();
            registerListeners();
            
            // 서버 재시작 시 AI 동료 재생성
            if (getConfig().getBoolean("ai.companion.respawn-on-restart", true)) {
                // 서버가 완전히 로드된 후 AI 재생성
                getServer().getScheduler().runTaskLater(this, () -> {
                    logger.info("🔄 저장된 AI 동료들을 재생성합니다...");
                    companionManager.respawnSavedCompanions();
                }, 100L); // 5초 후 실행
            }
            
            // 플러그인 성공적으로 로드됨
            logger.info("✅ 마인크래프트 AI 동료 플러그인이 성공적으로 활성화되었습니다!");
            
            // 모드 상태 표시
            if (dependencyManager.isProtocolLibAvailable()) {
                logger.info("🚀 고급 모드: ProtocolLib 기반 실제 플레이어 AI 컴패니언");
            } else {
                logger.info("🔧 기본 모드: Villager NPC 기반 AI 컴패니언");
                logger.info("💡 더 나은 경험을 위해 ProtocolLib 설치를 권장합니다.");
            }
            
            // 온라인 플레이어들에게 환영 메시지 전송
            sendWelcomeMessage();
            
        } catch (Exception e) {
            logger.severe("❌ 플러그인 초기화 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        logger.info("마인크래프트 AI 동료 플러그인이 비활성화됩니다...");
        
        try {
            // 모든 AI 동료들을 안전하게 제거
            if (companionManager != null) {
                companionManager.removeAllCompanions();
            }
            
            // 리소스 정리
            cleanup();
            
            logger.info("✅ 마인크래프트 AI 동료 플러그인이 성공적으로 비활성화되었습니다!");
            
        } catch (Exception e) {
            logger.severe("❌ 플러그인 비활성화 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 설정 파일들을 초기화합니다.
     */
    private void initializeConfig() {
        logger.info("📁 설정 파일을 초기화하는 중...");
        
        // config.yml 파일 생성 (존재하지 않는 경우)
        saveDefaultConfig();
        
        // 설정 매니저 초기화
        configManager = new ConfigManager(this);
        
        logger.info("✅ 설정 파일 초기화 완료");
    }
    
    /**
     * 핵심 매니저 클래스들을 초기화합니다.
     */
    private void initializeManagers() {
        logger.info("🤖 AI 동료 매니저를 초기화하는 중...");
        
        companionManager = new AICompanionManager(this, dependencyManager);
        
        logger.info("✅ 매니저 클래스들 초기화 완료");
    }
    
    /**
     * 명령어들을 등록합니다.
     */
    private void registerCommands() {
        logger.info("⌨️ 명령어를 등록하는 중...");
        
        AICompanionCommandExecutor commandExecutor = new AICompanionCommandExecutor(this, dependencyManager);
        
        // 모든 명령어에 같은 실행자 할당
        getCommand("aicompanion").setExecutor(commandExecutor);
        getCommand("ai-spawn").setExecutor(commandExecutor);
        getCommand("ai-remove").setExecutor(commandExecutor);
        getCommand("ai-settings").setExecutor(commandExecutor);
        getCommand("ai-voice").setExecutor(commandExecutor);
        getCommand("aic").setExecutor(commandExecutor);
        
        logger.info("✅ 명령어 등록 완료");
    }
    
    /**
     * 이벤트 리스너들을 등록합니다.
     */
    private void registerListeners() {
        logger.info("🔧 이벤트 리스너를 등록하는 중...");
        
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        
        logger.info("✅ 이벤트 리스너 등록 완료");
    }
    
    /**
     * 온라인 플레이어들에게 환영 메시지를 전송합니다.
     */
    private void sendWelcomeMessage() {
        String welcomeMessage = ChatColor.GREEN + "🤖 " + ChatColor.BOLD + "AI 동료 플러그인" + ChatColor.RESET + ChatColor.GREEN + "이 활성화되었습니다!\n" +
                               ChatColor.YELLOW + "📢 " + ChatColor.WHITE + "/ai-spawn 명령어로 AI 동료를 소환하세요!\n";
        
        if (dependencyManager.isProtocolLibAvailable()) {
            welcomeMessage += ChatColor.GREEN + "🚀 " + ChatColor.WHITE + "고급 모드: 실제 플레이어처럼 나타나는 AI!\n";
        } else {
            welcomeMessage += ChatColor.YELLOW + "🔧 " + ChatColor.WHITE + "기본 모드: Villager NPC AI\n";
            welcomeMessage += ChatColor.GRAY + "💡 ProtocolLib 설치로 더 나은 경험을 즐기세요!\n";
        }
        
        welcomeMessage += ChatColor.YELLOW + "📋 " + ChatColor.WHITE + "/aic status 명령어로 상태를 확인하세요!";
        
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission("aicompanion.admin")) {
                player.sendMessage(welcomeMessage);
            }
        }
    }
    
    /**
     * 플러그인 리소스들을 정리합니다.
     */
    private void cleanup() {
        // 향후 추가될 정리 작업들
        companionManager = null;
        configManager = null;
        dependencyManager = null;
    }
    
    // Getter 메서드들
    public static MinecraftAICompanionPlugin getInstance() {
        return instance;
    }
    
    public AICompanionManager getCompanionManager() {
        return companionManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }
    
    /**
     * 플러그인 정보를 반환합니다.
     */
    public void showPluginInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=".repeat(50));
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "🤖 마인크래프트 AI 동료 플러그인");
        player.sendMessage(ChatColor.YELLOW + "버전: " + ChatColor.WHITE + getDescription().getVersion());
        player.sendMessage(ChatColor.YELLOW + "제작자: " + ChatColor.WHITE + getDescription().getAuthors());
        player.sendMessage(ChatColor.YELLOW + "설명: " + ChatColor.WHITE + getDescription().getDescription());
        player.sendMessage("");
        player.sendMessage(dependencyManager.getDependencyStatus());
        player.sendMessage(ChatColor.GOLD + "=".repeat(50));
    }
} 