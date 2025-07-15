package com.minecraft.ai.brain.commands;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.service.TextToSpeechService;
import com.minecraft.ai.brain.service.AudioPlayerService;
import com.minecraft.ai.brain.service.ServiceException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.Map;

/**
 * TTS 시스템 테스트 명령어
 * /ttstest - TTS 시스템 테스트
 */
public class TTSTestCommand implements CommandExecutor {
    
    private final MinecraftAIBrainPlugin plugin;
    private final TextToSpeechService ttsService;
    private final AudioPlayerService audioPlayerService;
    
    // 테스트 문구들
    private static final String[] TEST_PHRASES = {
        "안녕하세요! TTS 시스템 테스트입니다.",
        "마인크래프트에서 음성 합성이 작동합니다!",
        "한국어 음성 합성 테스트 중입니다.",
        "감정을 담아서 말할 수도 있어요!",
        "이 문장은 조금 더 긴 문장으로, 자연스러운 음성 합성을 테스트합니다."
    };
    
    // 감정 테스트
    private static final String[] EMOTIONS = {"happy", "sad", "angry", "excited", "fearful"};
    private static final String[] EMOTION_PHRASES = {
        "정말 기뻐요! 오늘은 정말 좋은 날이에요!",
        "조금 슬프네요... 비가 오는 날이군요.",
        "화가 났어요! 이건 정말 불공평해요!",
        "너무 신나요! 새로운 모험이 시작됐어요!",
        "무서워요... 어두운 동굴은 위험해요."
    };
    
    public TTSTestCommand(MinecraftAIBrainPlugin plugin, TextToSpeechService ttsService, AudioPlayerService audioPlayerService) {
        this.plugin = plugin;
        this.ttsService = ttsService;
        this.audioPlayerService = audioPlayerService;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 권한 확인
        if (!player.hasPermission("minecraftai.ttstest")) {
            player.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        
        // 서브 명령어 처리
        if (args.length == 0) {
            showHelp(player);
        } else {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "simple":
                    testSimpleTTS(player, args);
                    break;
                    
                case "emotion":
                    testEmotionTTS(player, args);
                    break;
                    
                case "ssml":
                    testSSMLTTS(player, args);
                    break;
                    
                case "korean":
                    testKoreanTTS(player, args);
                    break;
                    
                case "custom":
                    testCustomTTS(player, args);
                    break;
                    
                case "status":
                    showTTSStatus(player);
                    break;
                    
                case "cache":
                    manageTTSCache(player, args);
                    break;
                    
                case "help":
                    showHelp(player);
                    break;
                    
                default:
                    player.sendMessage(ChatColor.RED + "알 수 없는 서브 명령어: " + subCommand);
                    showHelp(player);
                    break;
            }
        }
        
        return true;
    }
    
    /**
     * 간단한 TTS 테스트
     */
    private void testSimpleTTS(Player player, String[] args) {
        player.sendMessage(ChatColor.AQUA + "========== 간단한 TTS 테스트 ==========");
        
        String text;
        if (args.length > 1) {
            // 사용자 지정 텍스트
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            text = sb.toString().trim();
        } else {
            // 기본 테스트 문구
            text = TEST_PHRASES[(int) (Math.random() * TEST_PHRASES.length)];
        }
        
        player.sendMessage(ChatColor.YELLOW + "텍스트: " + ChatColor.WHITE + text);
        
        try {
            // 플레이어 위치에서 재생
            AudioPlayerService.Position position = new AudioPlayerService.Position(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            
            ttsService.synthesizeAndPlay(text, player, position);
            player.sendMessage(ChatColor.GREEN + "✓ TTS 재생 요청이 전송되었습니다.");
            
        } catch (ServiceException e) {
            player.sendMessage(ChatColor.RED + "✗ TTS 오류: " + e.getMessage());
        }
    }
    
    /**
     * 감정 TTS 테스트
     */
    private void testEmotionTTS(Player player, String[] args) {
        player.sendMessage(ChatColor.AQUA + "========== 감정 TTS 테스트 ==========");
        
        String emotion;
        String text;
        
        if (args.length > 1 && ttsService.isEmotionSupported(args[1])) {
            emotion = args[1].toLowerCase();
            int emotionIndex = -1;
            for (int i = 0; i < EMOTIONS.length; i++) {
                if (EMOTIONS[i].equals(emotion)) {
                    emotionIndex = i;
                    break;
                }
            }
            text = emotionIndex >= 0 ? EMOTION_PHRASES[emotionIndex] : EMOTION_PHRASES[0];
        } else {
            // 랜덤 감정 선택
            int index = (int) (Math.random() * EMOTIONS.length);
            emotion = EMOTIONS[index];
            text = EMOTION_PHRASES[index];
        }
        
        player.sendMessage(ChatColor.YELLOW + "감정: " + ChatColor.GOLD + emotion);
        player.sendMessage(ChatColor.YELLOW + "텍스트: " + ChatColor.WHITE + text);
        
        try {
            AudioPlayerService.Position position = new AudioPlayerService.Position(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            
            ttsService.synthesizeAndPlay(text, emotion, player, position);
            player.sendMessage(ChatColor.GREEN + "✓ 감정 TTS 재생 요청이 전송되었습니다.");
            
        } catch (ServiceException e) {
            player.sendMessage(ChatColor.RED + "✗ 감정 TTS 오류: " + e.getMessage());
        }
        
        // 사용 가능한 감정 표시
        player.sendMessage(ChatColor.GRAY + "사용 가능한 감정: " + String.join(", ", ttsService.getAvailableEmotions()));
    }
    
    /**
     * SSML TTS 테스트
     */
    private void testSSMLTTS(Player player, String[] args) {
        player.sendMessage(ChatColor.AQUA + "========== SSML TTS 테스트 ==========");
        
        String text = "안녕하세요! <break time=\"500ms\"/> " +
                     "이것은 <emphasis level=\"strong\">SSML</emphasis> 테스트입니다. " +
                     "<prosody rate=\"slow\">천천히 말하고</prosody>, " +
                     "<prosody rate=\"fast\">빠르게 말할 수도 있어요!</prosody>";
        
        player.sendMessage(ChatColor.YELLOW + "SSML 텍스트 테스트 중...");
        
        try {
            AudioPlayerService.Position position = new AudioPlayerService.Position(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            
            byte[] audioData = ttsService.synthesizeSpeechWithSSML(text, null);
            audioPlayerService.queueAudio(audioData, position, player.getUniqueId());
            
            player.sendMessage(ChatColor.GREEN + "✓ SSML TTS 재생 요청이 전송되었습니다.");
            
        } catch (ServiceException e) {
            player.sendMessage(ChatColor.RED + "✗ SSML TTS 오류: " + e.getMessage());
        }
    }
    
    /**
     * 고급 한국어 TTS 테스트
     */
    private void testKoreanTTS(Player player, String[] args) {
        player.sendMessage(ChatColor.AQUA + "========== 고급 한국어 TTS 테스트 ==========");
        
        String[] testSentences = {
            "마인크래프트에서 크리퍼를 조심하세요!",
            "엔더맨이 나타났습니다. 눈을 마주치지 마세요.",
            "레드스톤 회로를 만들어 볼까요?",
            "오늘은 어떤 건축을 하실 건가요?",
            "새로운 아이템을 발견했어요! 인벤토리를 확인해보세요."
        };
        
        String text = testSentences[(int) (Math.random() * testSentences.length)];
        String emotion = args.length > 1 ? args[1] : "neutral";
        
        player.sendMessage(ChatColor.YELLOW + "텍스트: " + ChatColor.WHITE + text);
        player.sendMessage(ChatColor.YELLOW + "한국어 최적화 적용 중...");
        
        try {
            AudioPlayerService.Position position = new AudioPlayerService.Position(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            
            byte[] audioData = ttsService.synthesizeAdvancedKoreanSpeech(text, emotion);
            audioPlayerService.queueAudio(audioData, position, player.getUniqueId());
            
            player.sendMessage(ChatColor.GREEN + "✓ 고급 한국어 TTS 재생 요청이 전송되었습니다.");
            
        } catch (ServiceException e) {
            player.sendMessage(ChatColor.RED + "✗ 고급 한국어 TTS 오류: " + e.getMessage());
        }
    }
    
    /**
     * 사용자 지정 TTS 테스트
     */
    private void testCustomTTS(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "사용법: /ttstest custom <텍스트>");
            return;
        }
        
        player.sendMessage(ChatColor.AQUA + "========== 사용자 지정 TTS 테스트 ==========");
        
        // 모든 인자를 하나의 텍스트로 결합
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String text = sb.toString().trim();
        
        player.sendMessage(ChatColor.YELLOW + "텍스트: " + ChatColor.WHITE + text);
        
        try {
            AudioPlayerService.Position position = new AudioPlayerService.Position(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            
            ttsService.synthesizeAndPlay(text, player, position);
            player.sendMessage(ChatColor.GREEN + "✓ 사용자 지정 TTS 재생 요청이 전송되었습니다.");
            
        } catch (ServiceException e) {
            player.sendMessage(ChatColor.RED + "✗ TTS 오류: " + e.getMessage());
        }
    }
    
    /**
     * TTS 상태 표시
     */
    private void showTTSStatus(Player player) {
        player.sendMessage(ChatColor.AQUA + "========== TTS 시스템 상태 ==========");
        
        // 서비스 상태
        player.sendMessage(ChatColor.YELLOW + "서비스 상태: " + 
            (ttsService.isEnabled() ? ChatColor.GREEN + "활성" : ChatColor.RED + "비활성"));
        
        // 서비스 통계
        String stats = ttsService.getServiceStats();
        player.sendMessage(ChatColor.YELLOW + "통계: " + ChatColor.WHITE + stats);
        
        // 메트릭스
        Map<String, Object> metrics = ttsService.getMetrics();
        player.sendMessage(ChatColor.YELLOW + "업타임: " + ChatColor.WHITE + 
            formatUptime((Long) metrics.get("uptime")));
        player.sendMessage(ChatColor.YELLOW + "캐시 크기: " + ChatColor.WHITE + 
            metrics.get("cacheSize") + " 항목");
        player.sendMessage(ChatColor.YELLOW + "캐시 적중률: " + ChatColor.WHITE + 
            metrics.get("cacheHitRate") + "%");
        
        // 에러 상태
        if (metrics.containsKey("serviceHealthy")) {
            boolean healthy = (Boolean) metrics.get("serviceHealthy");
            player.sendMessage(ChatColor.YELLOW + "서비스 상태: " + 
                (healthy ? ChatColor.GREEN + "정상" : ChatColor.RED + "오류"));
        }
        
        player.sendMessage(ChatColor.AQUA + "=====================================");
    }
    
    /**
     * TTS 캐시 관리
     */
    private void manageTTSCache(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "캐시 크기: " + ttsService.getCacheSize() + " 항목");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "clear":
                ttsService.clearCache();
                player.sendMessage(ChatColor.GREEN + "✓ TTS 캐시가 삭제되었습니다.");
                break;
                
            case "info":
                player.sendMessage(ChatColor.AQUA + "========== TTS 캐시 정보 ==========");
                player.sendMessage(ChatColor.YELLOW + "현재 크기: " + ChatColor.WHITE + 
                    ttsService.getCacheSize() + " 항목");
                player.sendMessage(ChatColor.YELLOW + "통계: " + ChatColor.WHITE + 
                    ttsService.getServiceStats());
                player.sendMessage(ChatColor.AQUA + "==================================");
                break;
                
            default:
                player.sendMessage(ChatColor.RED + "알 수 없는 캐시 명령: " + action);
                player.sendMessage(ChatColor.GRAY + "사용 가능: clear, info");
                break;
        }
    }
    
    /**
     * 도움말 표시
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.AQUA + "========== TTS 테스트 명령어 도움말 ==========");
        player.sendMessage(ChatColor.WHITE + "/ttstest simple [텍스트]" + ChatColor.GRAY + " - 간단한 TTS 테스트");
        player.sendMessage(ChatColor.WHITE + "/ttstest emotion [감정]" + ChatColor.GRAY + " - 감정 TTS 테스트");
        player.sendMessage(ChatColor.WHITE + "/ttstest ssml" + ChatColor.GRAY + " - SSML 마크업 테스트");
        player.sendMessage(ChatColor.WHITE + "/ttstest korean [감정]" + ChatColor.GRAY + " - 고급 한국어 TTS 테스트");
        player.sendMessage(ChatColor.WHITE + "/ttstest custom <텍스트>" + ChatColor.GRAY + " - 사용자 지정 텍스트");
        player.sendMessage(ChatColor.WHITE + "/ttstest status" + ChatColor.GRAY + " - TTS 시스템 상태 확인");
        player.sendMessage(ChatColor.WHITE + "/ttstest cache [clear|info]" + ChatColor.GRAY + " - 캐시 관리");
        player.sendMessage(ChatColor.WHITE + "/ttstest help" + ChatColor.GRAY + " - 이 도움말 표시");
        player.sendMessage(ChatColor.AQUA + "============================================");
        player.sendMessage(ChatColor.GRAY + "사용 가능한 감정: " + 
            String.join(", ", ttsService.getAvailableEmotions()));
    }
    
    /**
     * 업타임 포맷
     */
    private String formatUptime(long uptimeMs) {
        if (uptimeMs <= 0) return "N/A";
        
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%d일 %d시간", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%d시간 %d분", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds % 60);
        } else {
            return String.format("%d초", seconds);
        }
    }
}