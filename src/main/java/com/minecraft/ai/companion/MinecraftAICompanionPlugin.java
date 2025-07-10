package com.minecraft.ai.companion;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import com.minecraft.ai.companion.core.AICompanionManager;
import com.minecraft.ai.companion.core.ConfigManager;
import com.minecraft.ai.companion.commands.AICompanionCommandExecutor;
import com.minecraft.ai.companion.listeners.PlayerEventListener;

import java.util.logging.Logger;

/**
 * 마인크래프트 AI 동료 플러그인의 메인 클래스
 * 
 * 이 플러그인은 다음 기능을 제공합니다:
 * - AI 동료 엔티티 생성 및 관리
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
            // 초기화 단계들
            initializeConfig();
            initializeManagers();
            registerCommands();
            registerListeners();
            
            // 플러그인 성공적으로 로드됨
            logger.info("✅ 마인크래프트 AI 동료 플러그인이 성공적으로 활성화되었습니다!");
            
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
        
        companionManager = new AICompanionManager(this);
        
        logger.info("✅ 매니저 클래스들 초기화 완료");
    }
    
    /**
     * 명령어들을 등록합니다.
     */
    private void registerCommands() {
        logger.info("⌨️ 명령어를 등록하는 중...");
        
        AICompanionCommandExecutor commandExecutor = new AICompanionCommandExecutor(this);
        
        // 모든 명령어에 같은 실행자 할당
        getCommand("aicompanion").setExecutor(commandExecutor);
        getCommand("ai-spawn").setExecutor(commandExecutor);
        getCommand("ai-remove").setExecutor(commandExecutor);
        getCommand("ai-settings").setExecutor(commandExecutor);
        getCommand("ai-voice").setExecutor(commandExecutor);
        
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
                               ChatColor.YELLOW + "📢 " + ChatColor.WHITE + "/ai-spawn 명령어로 AI 동료를 소환하세요!\n" +
                               ChatColor.YELLOW + "🎤 " + ChatColor.WHITE + "/ai-voice 명령어로 음성 모드를 활성화하세요!";
        
        for (Player player : getServer().getOnlinePlayers()) {
            player.sendMessage(welcomeMessage);
        }
    }
    
    /**
     * 플러그인 리소스들을 정리합니다.
     */
    private void cleanup() {
        // 향후 추가될 정리 작업들
        companionManager = null;
        configManager = null;
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
    
    /**
     * 플러그인 정보를 반환합니다.
     */
    public void showPluginInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=".repeat(50));
        player.sendMessage(ChatColor.GREEN + ChatColor.BOLD + "🤖 마인크래프트 AI 동료 플러그인");
        player.sendMessage(ChatColor.YELLOW + "버전: " + ChatColor.WHITE + getDescription().getVersion());
        player.sendMessage(ChatColor.YELLOW + "제작자: " + ChatColor.WHITE + getDescription().getAuthors());
        player.sendMessage(ChatColor.YELLOW + "설명: " + ChatColor.WHITE + getDescription().getDescription());
        player.sendMessage(ChatColor.GOLD + "=".repeat(50));
    }
} 