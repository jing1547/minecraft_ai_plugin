package com.minecraft.ai.brain.commands;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.service.AudioCaptureService;
import com.minecraft.ai.brain.service.VoiceActivityDetector;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * STT 시스템 테스트 명령어
 * /stttest - STT 시스템 테스트 시작/중지
 */
public class STTTestCommand implements CommandExecutor {
    
    private final MinecraftAIBrainPlugin plugin;
    private final AudioCaptureService audioCaptureService;
    
    public STTTestCommand(MinecraftAIBrainPlugin plugin, AudioCaptureService audioCaptureService) {
        this.plugin = plugin;
        this.audioCaptureService = audioCaptureService;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 권한 확인
        if (!player.hasPermission("minecraftai.stttest")) {
            player.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        // 서브 명령어 처리
        if (args.length == 0) {
            // 기본: 토글
            toggleSTTTest(player);
        } else {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "start":
                    startSTTTest(player);
                    break;
                    
                case "stop":
                    stopSTTTest(player);
                    break;
                    
                case "status":
                    showSTTStatus(player);
                    break;
                    
                case "stats":
                    showSTTStatistics(player);
                    break;
                    
                case "vad":
                    showVADInfo(player);
                    break;
                    
                case "help":
                    showHelp(player);
                    break;
                    
                default:
                    player.sendMessage("§c알 수 없는 서브 명령어: " + subCommand);
                    showHelp(player);
                    break;
            }
        }
        
        return true;
    }
    
    /**
     * STT 테스트 토글
     */
    private void toggleSTTTest(Player player) {
        if (audioCaptureService.isPlayerBeingMonitored(player)) {
            stopSTTTest(player);
        } else {
            startSTTTest(player);
        }
    }
    
    /**
     * STT 테스트 시작
     */
    private void startSTTTest(Player player) {
        if (audioCaptureService.isPlayerBeingMonitored(player)) {
            player.sendMessage("§e[STT Test] 이미 테스트가 진행 중입니다.");
            return;
        }
        
        player.sendMessage("§b========== STT 시스템 테스트 시작 ==========");
        player.sendMessage("§a[STT Test] VAD 기반 음성 인식 테스트를 시작합니다.");
        player.sendMessage("§7");
        player.sendMessage("§e테스트 방법:");
        player.sendMessage("§f1. 마이크가 제대로 연결되어 있는지 확인하세요");
        player.sendMessage("§f2. 조용한 환경에서 테스트하세요");
        player.sendMessage("§f3. 말을 시작하면 자동으로 녹음이 시작됩니다");
        player.sendMessage("§f4. 말을 멈추면 자동으로 인식이 처리됩니다");
        player.sendMessage("§7");
        player.sendMessage("§6팁: 명확하고 자연스럽게 말씀해주세요!");
        player.sendMessage("§b==========================================");
        
        // 오디오 모니터링 시작
        audioCaptureService.startAudioMonitoring(player);
    }
    
    /**
     * STT 테스트 중지
     */
    private void stopSTTTest(Player player) {
        if (!audioCaptureService.isPlayerBeingMonitored(player)) {
            player.sendMessage("§e[STT Test] 진행 중인 테스트가 없습니다.");
            return;
        }
        
        // 오디오 모니터링 중지
        audioCaptureService.stopAudioMonitoring(player);
        
        player.sendMessage("§b========== STT 시스템 테스트 종료 ==========");
        player.sendMessage("§e[STT Test] 테스트가 종료되었습니다.");
        player.sendMessage("§b==========================================");
    }
    
    /**
     * STT 상태 표시
     */
    private void showSTTStatus(Player player) {
        player.sendMessage("§b========== STT 시스템 상태 ==========");
        
        // 모니터링 상태
        boolean isMonitoring = audioCaptureService.isPlayerBeingMonitored(player);
        player.sendMessage("§f모니터링 상태: " + (isMonitoring ? "§a활성" : "§c비활성"));
        
        // 오디오 캡처 서비스 상태
        Map<String, Object> audioStatus = audioCaptureService.getAudioMonitoringStatus();
        player.sendMessage("§f현재 볼륨 레벨: §e" + audioStatus.get("volume_level") + "%");
        player.sendMessage("§f음성 감지: " + 
            (Boolean.valueOf(audioStatus.get("voice_detected").toString()) ? "§a감지됨" : "§7감지 안됨"));
        player.sendMessage("§f음성 활동 비율: §e" + 
            String.format("%.1f%%", audioStatus.get("voice_activity_percentage")));
        
        // 마이크 상태
        Map<String, Object> micStatus = audioCaptureService.getMicrophoneStatus();
        player.sendMessage("§f마이크 상태: " + 
            (Boolean.valueOf(micStatus.get("microphone_active").toString()) ? "§a활성" : "§c비활성"));
        
        player.sendMessage("§b=====================================");
    }
    
    /**
     * STT 통계 표시
     */
    private void showSTTStatistics(Player player) {
        player.sendMessage("§b========== STT 시스템 통계 ==========");
        
        // STT 세션 통계
        Map<String, Object> sttStats = audioCaptureService.getSTTStatistics();
        if (!sttStats.isEmpty()) {
            player.sendMessage("§f활성 세션: §e" + sttStats.get("active_count"));
            player.sendMessage("§f일시 중지된 세션: §e" + sttStats.get("suspended_count"));
            player.sendMessage("§f총 처리된 프레임: §e" + sttStats.get("total_frames_processed"));
        }
        
        // 오디오 모니터링 통계
        Map<String, Object> audioStats = audioCaptureService.getAudioMonitoringStatus();
        player.sendMessage("§f총 오디오 프레임: §e" + audioStats.get("total_frames"));
        player.sendMessage("§f음성 활성 프레임: §e" + audioStats.get("voice_active_frames"));
        player.sendMessage("§f평균 볼륨: §e" + String.format("%.2f", audioStats.get("average_volume")));
        
        player.sendMessage("§b=====================================");
    }
    
    /**
     * VAD 정보 표시
     */
    private void showVADInfo(Player player) {
        player.sendMessage("§b========== VAD (Voice Activity Detection) 정보 ==========");
        player.sendMessage("§fVAD는 음성 활동을 감지하여 자동으로 녹음을 시작/종료합니다.");
        player.sendMessage("§7");
        player.sendMessage("§e주요 기능:");
        player.sendMessage("§f• 적응형 노이즈 필터링");
        player.sendMessage("§f• 에너지 기반 음성 감지");
        player.sendMessage("§f• 스펙트럴 분석");
        player.sendMessage("§f• 제로 크로싱 레이트 분석");
        player.sendMessage("§7");
        player.sendMessage("§e현재 설정:");
        player.sendMessage("§f• 최소 음성 길이: §a500ms");
        player.sendMessage("§f• 최대 음성 길이: §a15초");
        player.sendMessage("§f• 침묵 타임아웃: §a1.5초");
        player.sendMessage("§f• 프레임 크기: §a40ms");
        player.sendMessage("§b========================================================");
    }
    
    /**
     * 도움말 표시
     */
    private void showHelp(Player player) {
        player.sendMessage("§b========== STT 테스트 명령어 도움말 ==========");
        player.sendMessage("§f/stttest §7- STT 테스트 시작/중지 (토글)");
        player.sendMessage("§f/stttest start §7- STT 테스트 시작");
        player.sendMessage("§f/stttest stop §7- STT 테스트 중지");
        player.sendMessage("§f/stttest status §7- 현재 상태 확인");
        player.sendMessage("§f/stttest stats §7- 통계 정보 확인");
        player.sendMessage("§f/stttest vad §7- VAD 정보 확인");
        player.sendMessage("§f/stttest help §7- 이 도움말 표시");
        player.sendMessage("§b============================================");
    }
}