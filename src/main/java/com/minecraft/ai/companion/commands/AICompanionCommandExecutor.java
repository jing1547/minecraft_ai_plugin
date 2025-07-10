package com.minecraft.ai.companion.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import com.minecraft.ai.companion.MinecraftAICompanionPlugin;
import com.minecraft.ai.companion.entity.AICompanionEntity;
import com.agjagjn.minecraft_ai.entity.FakePlayerCompanion;
import com.minecraft.ai.companion.data.CompanionData;

import java.util.List;

/**
 * AI 동료 플러그인의 명령어 처리 클래스
 */
public class AICompanionCommandExecutor implements CommandExecutor {
    
    private final MinecraftAICompanionPlugin plugin;
    
    public AICompanionCommandExecutor(MinecraftAICompanionPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 플레이어만 사용 가능
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 이 명령어는 플레이어만 사용할 수 있습니다!");
            return true;
        }
        
        Player player = (Player) sender;
        String commandName = command.getName().toLowerCase();
        
        switch (commandName) {
            case "ai-spawn":
                return handleSpawnCommand(player, args);
                
            case "ai-remove":
                return handleRemoveCommand(player, args);
                
            case "ai-settings":
                return handleSettingsCommand(player, args);
                
            case "aicompanion":
            case "aic":
            case "companion":
                return handleMainCommand(player, args);
                
            default:
                return false;
        }
    }
    
    /**
     * AI 동료 소환 명령어 처리
     */
    private boolean handleSpawnCommand(Player player, String[] args) {
        // 권한 확인
        if (!player.hasPermission("aicompanion.spawn")) {
            player.sendMessage(ChatColor.RED + "❌ 이 명령어를 사용할 권한이 없습니다!");
            return true;
        }
        
        // 이미 동료가 있는지 확인
        if (plugin.getCompanionManager().hasActiveCompanion(player)) {
            player.sendMessage(ChatColor.RED + "❌ 이미 활성화된 AI 동료가 있습니다!");
            player.sendMessage(ChatColor.YELLOW + "💡 먼저 /ai-remove 명령어로 기존 동료를 제거해주세요.");
            return true;
        }
        
        // 동료 이름 설정
        String companionName = null;
        if (args.length > 0) {
            companionName = String.join(" ", args);
            
            // 이름 길이 제한
            if (companionName.length() > 16) {
                player.sendMessage(ChatColor.RED + "❌ 동료 이름은 16글자를 초과할 수 없습니다!");
                return true;
            }
        }
        
        // 플레이어 위치에서 소환
        Location spawnLocation = player.getLocation().add(2, 0, 0); // 플레이어 옆에 소환
        
        // AI 동료 소환
        boolean success = plugin.getCompanionManager().spawnCompanion(player, spawnLocation, companionName);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "✅ AI 동료가 성공적으로 소환되었습니다!");
            player.sendMessage(ChatColor.AQUA + "💬 동료와 대화하려면 채팅에 메시지를 입력하세요!");
        } else {
            player.sendMessage(ChatColor.RED + "❌ AI 동료 소환에 실패했습니다. 관리자에게 문의하세요.");
        }
        
        return true;
    }
    
    /**
     * AI 동료 제거 명령어 처리
     */
    private boolean handleRemoveCommand(Player player, String[] args) {
        // 권한 확인
        if (!player.hasPermission("aicompanion.remove")) {
            player.sendMessage(ChatColor.RED + "❌ 이 명령어를 사용할 권한이 없습니다!");
            return true;
        }
        
        // 동료가 있는지 확인
        if (!plugin.getCompanionManager().hasActiveCompanion(player)) {
            player.sendMessage(ChatColor.RED + "❌ 활성화된 AI 동료가 없습니다!");
            return true;
        }
        
        // AI 동료 제거
        boolean success = plugin.getCompanionManager().despawnCompanion(player);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "✅ AI 동료가 성공적으로 제거되었습니다!");
        } else {
            player.sendMessage(ChatColor.RED + "❌ AI 동료 제거에 실패했습니다.");
        }
        
        return true;
    }
    
    /**
     * AI 동료 설정 명령어 처리
     */
    private boolean handleSettingsCommand(Player player, String[] args) {
        // 권한 확인
        if (!player.hasPermission("aicompanion.settings")) {
            player.sendMessage(ChatColor.RED + "❌ 이 명령어를 사용할 권한이 없습니다!");
            return true;
        }
        
        if (args.length == 0) {
            // 설정 목록 표시
            showSettingsHelp(player);
            return true;
        }
        
        String setting = args[0].toLowerCase();
        
        switch (setting) {
            case "voice":
                return handleVoiceSettings(player, args);
                
            case "behavior":
                return handleBehaviorSettings(player, args);
                
            case "info":
                return showCompanionInfo(player);
                
            default:
                player.sendMessage(ChatColor.RED + "❌ 알 수 없는 설정: " + setting);
                showSettingsHelp(player);
                return true;
        }
    }
    
    /**
     * 메인 명령어 처리
     */
    private boolean handleMainCommand(Player player, String[] args) {
        if (args.length == 0) {
            showMainHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        
        switch (subCommand) {
            case "spawn":
                return handleSpawnCommand(player, subArgs);
                
            case "remove":
            case "despawn":
                return handleRemoveCommand(player, subArgs);
                
            case "settings":
            case "config":
                return handleSettingsCommand(player, subArgs);
                
            case "help":
                showMainHelp(player);
                return true;
                
            case "info":
                return showCompanionInfo(player);
                
            case "teleport":
            case "tp":
                return handleTeleportCommand(player);
                
            case "list":
                return showCompanionList(player);
                
            default:
                player.sendMessage(ChatColor.RED + "❌ 알 수 없는 하위 명령어: " + subCommand);
                showMainHelp(player);
                return true;
        }
    }
    
    /**
     * 음성 설정 처리
     */
    private boolean handleVoiceSettings(Player player, String[] args) {
        player.sendMessage(ChatColor.YELLOW + "🔧 음성 설정 기능은 개발 중입니다!");
        // TODO: 음성 설정 기능 구현
        return true;
    }
    
    /**
     * 행동 설정 처리
     */
    private boolean handleBehaviorSettings(Player player, String[] args) {
        player.sendMessage(ChatColor.YELLOW + "🔧 행동 설정 기능은 개발 중입니다!");
        // TODO: 행동 설정 기능 구현
        return true;
    }
    
    /**
     * 동료 정보 표시
     */
    private boolean showCompanionInfo(Player player) {
        FakePlayerCompanion companion = plugin.getCompanionManager().getCompanionByOwner(player);
        
        if (companion == null) {
            player.sendMessage(ChatColor.RED + "❌ 활성화된 AI 동료가 없습니다!");
            return true;
        }
        
        CompanionData data = companion.getData();
        
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "🤖 AI 동료 정보");
        player.sendMessage(ChatColor.YELLOW + "이름: " + ChatColor.WHITE + data.getName());
        player.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + data.getCompanionId());
        player.sendMessage(ChatColor.YELLOW + "생성일: " + ChatColor.WHITE + new java.util.Date(data.getCreatedTime()));
        player.sendMessage(ChatColor.YELLOW + "상태: " + ChatColor.GREEN + "활성화");
        
        // FakePlayer는 항상 체력이 20
        player.sendMessage(ChatColor.YELLOW + "체력: " + ChatColor.WHITE + "20.0/20.0");
        
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        
        return true;
    }
    
    /**
     * 텔레포트 명령어 처리
     */
    private boolean handleTeleportCommand(Player player) {
        FakePlayerCompanion companion = plugin.getCompanionManager().getCompanionByOwner(player);
        
        if (companion == null) {
            player.sendMessage(ChatColor.RED + "❌ 활성화된 AI 동료가 없습니다!");
            return true;
        }
        
        companion.teleport(player.getLocation().add(2, 0, 0));
        player.sendMessage(ChatColor.GREEN + "✅ AI 동료가 당신에게 텔레포트했습니다!");
        
        return true;
    }
    
    /**
     * 동료 목록 표시
     */
    private boolean showCompanionList(Player player) {
        // 관리자 권한 확인
        if (!player.hasPermission("aicompanion.admin")) {
            player.sendMessage(ChatColor.RED + "❌ 이 명령어를 사용할 권한이 없습니다!");
            return true;
        }
        
        List<FakePlayerCompanion> companions = plugin.getCompanionManager().getAllActiveCompanions();
        
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "📋 활성화된 AI 동료 목록");
        
        if (companions.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "활성화된 AI 동료가 없습니다.");
        } else {
            for (FakePlayerCompanion companion : companions) {
                CompanionData data = companion.getData();
                player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + data.getName() + 
                                 ChatColor.GRAY + " (소유자: " + companion.getOwner().getName() + ")");
            }
        }
        
        player.sendMessage(ChatColor.GOLD + "총 " + companions.size() + "개의 동료가 활성화되어 있습니다.");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        
        return true;
    }
    
    /**
     * 메인 도움말 표시
     */
    private void showMainHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "🤖 AI 동료 플러그인 도움말");
        player.sendMessage(ChatColor.YELLOW + "/ai-spawn [이름]" + ChatColor.WHITE + " - AI 동료 소환");
        player.sendMessage(ChatColor.YELLOW + "/ai-remove" + ChatColor.WHITE + " - AI 동료 제거");
        player.sendMessage(ChatColor.YELLOW + "/aic info" + ChatColor.WHITE + " - 동료 정보 확인");
        player.sendMessage(ChatColor.YELLOW + "/aic tp" + ChatColor.WHITE + " - 동료를 내 위치로 텔레포트");
        player.sendMessage(ChatColor.YELLOW + "/aic settings" + ChatColor.WHITE + " - 동료 설정 관리");
        
        if (player.hasPermission("aicompanion.admin")) {
            player.sendMessage(ChatColor.RED + "/aic list" + ChatColor.WHITE + " - 모든 동료 목록 (관리자)");
        }
        
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }
    
    /**
     * 설정 도움말 표시
     */
    private void showSettingsHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "⚙️ AI 동료 설정");
        player.sendMessage(ChatColor.YELLOW + "/ai-settings voice" + ChatColor.WHITE + " - 음성 설정");
        player.sendMessage(ChatColor.YELLOW + "/ai-settings behavior" + ChatColor.WHITE + " - 행동 설정");
        player.sendMessage(ChatColor.YELLOW + "/ai-settings info" + ChatColor.WHITE + " - 동료 정보");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }
} 