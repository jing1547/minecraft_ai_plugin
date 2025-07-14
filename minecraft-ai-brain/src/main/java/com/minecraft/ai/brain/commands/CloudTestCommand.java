package com.minecraft.ai.brain.commands;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.minecraft.ai.brain.service.SpeechToTextConfig;
import com.minecraft.ai.brain.service.TTSConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Google Cloud API 연결 테스트 명령어
 */
public class CloudTestCommand implements CommandExecutor {
    
    private static final Logger logger = Logger.getLogger(CloudTestCommand.class.getName());
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "auth":
                testAuthentication(player);
                break;
            case "stt":
                testSTTConnection(player);
                break;
            case "tts":
                testTTSConnection(player);
                break;
            case "config":
                showConfiguration(player);
                break;
            case "all":
                testAllComponents(player);
                break;
            default:
                showHelp(player);
                break;
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§6=== Google Cloud 테스트 명령어 ===");
        player.sendMessage("§e/cloudtest auth §7- 인증 파일 확인");
        player.sendMessage("§e/cloudtest stt §7- STT 연결 테스트");
        player.sendMessage("§e/cloudtest tts §7- TTS 연결 테스트");
        player.sendMessage("§e/cloudtest config §7- 설정 정보 확인");
        player.sendMessage("§e/cloudtest all §7- 전체 테스트 실행");
    }
    
    private void testAuthentication(Player player) {
        player.sendMessage("§6[인증 테스트] 시작...");
        
        try {
            // 1. 인증 파일 경로 확인
            String credentialsPath = SpeechToTextConfig.getCredentialsPath();
            player.sendMessage("§e인증 파일 경로: " + credentialsPath);
            
            if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
                player.sendMessage("§c✗ 인증 파일 경로가 설정되지 않았습니다!");
                return;
            }
            
            // 2. 파일 존재 확인
            if (!Files.exists(Paths.get(credentialsPath))) {
                player.sendMessage("§c✗ 인증 파일이 존재하지 않습니다: " + credentialsPath);
                return;
            }
            player.sendMessage("§a✓ 인증 파일 존재 확인");
            
            // 3. 파일 읽기 가능 확인
            if (!Files.isReadable(Paths.get(credentialsPath))) {
                player.sendMessage("§c✗ 인증 파일을 읽을 수 없습니다 (권한 문제)");
                return;
            }
            player.sendMessage("§a✓ 인증 파일 읽기 권한 확인");
            
            // 4. 인증 파일 내용 검증
            try (FileInputStream fis = new FileInputStream(credentialsPath)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(fis);
                player.sendMessage("§a✓ 인증 파일 파싱 성공");
                
                // 프로젝트 ID 확인
                if (credentials instanceof com.google.auth.oauth2.ServiceAccountCredentials) {
                    com.google.auth.oauth2.ServiceAccountCredentials saCreds = 
                        (com.google.auth.oauth2.ServiceAccountCredentials) credentials;
                    player.sendMessage("§e프로젝트 ID: " + saCreds.getProjectId());
                    player.sendMessage("§e클라이언트 이메일: " + saCreds.getClientEmail());
                }
                
            } catch (Exception e) {
                player.sendMessage("§c✗ 인증 파일 파싱 실패: " + e.getMessage());
                logger.severe("Authentication parsing failed: " + e.getMessage());
                return;
            }
            
            player.sendMessage("§a✓ 인증 테스트 완료!");
            
        } catch (Exception e) {
            player.sendMessage("§c✗ 인증 테스트 실패: " + e.getMessage());
            logger.severe("Authentication test failed: " + e.getMessage());
        }
    }
    
    private void testSTTConnection(Player player) {
        player.sendMessage("§6[STT 연결 테스트] 시작...");
        
        try {
            // 1. STT 활성화 확인
            if (!SpeechToTextConfig.isEnabled()) {
                player.sendMessage("§c✗ STT가 설정에서 비활성화되어 있습니다");
                return;
            }
            player.sendMessage("§a✓ STT 활성화 확인");
            
            // 2. 설정 검증
            try {
                SpeechToTextConfig.validateConfiguration();
                player.sendMessage("§a✓ STT 설정 검증 완료");
            } catch (Exception e) {
                player.sendMessage("§c✗ STT 설정 검증 실패: " + e.getMessage());
                return;
            }
            
            // 3. 실제 STT 클라이언트 생성 테스트
            try {
                String credentialsPath = SpeechToTextConfig.getCredentialsPath();
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream(credentialsPath));
                
                SpeechClient speechClient = SpeechClient.create(
                    SpeechSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build()
                );
                
                player.sendMessage("§a✓ STT 클라이언트 생성 성공");
                
                // 클라이언트 닫기
                speechClient.close();
                player.sendMessage("§a✓ STT 연결 테스트 완료!");
                
            } catch (Exception e) {
                player.sendMessage("§c✗ STT 클라이언트 생성 실패: " + e.getMessage());
                logger.severe("STT client creation failed: " + e.getMessage());
                e.printStackTrace();
            }
            
        } catch (Exception e) {
            player.sendMessage("§c✗ STT 연결 테스트 실패: " + e.getMessage());
            logger.severe("STT connection test failed: " + e.getMessage());
        }
    }
    
    private void testTTSConnection(Player player) {
        player.sendMessage("§6[TTS 연결 테스트] 시작...");
        
        try {
            // 1. TTS 활성화 확인
            if (!TTSConfig.isEnabled()) {
                player.sendMessage("§c✗ TTS가 설정에서 비활성화되어 있습니다");
                return;
            }
            player.sendMessage("§a✓ TTS 활성화 확인");
            
            // 2. 실제 TTS 클라이언트 생성 테스트
            try {
                String credentialsPath = SpeechToTextConfig.getCredentialsPath(); // 같은 인증 파일 사용
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream(credentialsPath));
                
                TextToSpeechClient ttsClient = TextToSpeechClient.create(
                    TextToSpeechSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build()
                );
                
                player.sendMessage("§a✓ TTS 클라이언트 생성 성공");
                
                // 클라이언트 닫기
                ttsClient.close();
                player.sendMessage("§a✓ TTS 연결 테스트 완료!");
                
            } catch (Exception e) {
                player.sendMessage("§c✗ TTS 클라이언트 생성 실패: " + e.getMessage());
                logger.severe("TTS client creation failed: " + e.getMessage());
                e.printStackTrace();
            }
            
        } catch (Exception e) {
            player.sendMessage("§c✗ TTS 연결 테스트 실패: " + e.getMessage());
            logger.severe("TTS connection test failed: " + e.getMessage());
        }
    }
    
    private void showConfiguration(Player player) {
        player.sendMessage("§6=== Google Cloud 설정 정보 ===");
        
        // STT 설정
        player.sendMessage("§e[STT 설정]");
        player.sendMessage("§7활성화: " + (SpeechToTextConfig.isEnabled() ? "§a예" : "§c아니오"));
        player.sendMessage("§7언어 코드: §f" + SpeechToTextConfig.getLanguageCode());
        player.sendMessage("§7샘플 레이트: §f" + SpeechToTextConfig.getSampleRate() + "Hz");
        player.sendMessage("§7인증 파일: §f" + SpeechToTextConfig.getCredentialsPath());
        
        // TTS 설정
        player.sendMessage("§e[TTS 설정]");
        player.sendMessage("§7활성화: " + (TTSConfig.isEnabled() ? "§a예" : "§c아니오"));
        
        // 환경 변수 확인
        player.sendMessage("§e[환경 변수]");
        String googleCreds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        player.sendMessage("§7GOOGLE_APPLICATION_CREDENTIALS: " + 
            (googleCreds != null ? "§a설정됨" : "§c설정되지 않음"));
    }
    
    private void testAllComponents(Player player) {
        player.sendMessage("§6=== 전체 테스트 시작 ===");
        
        testAuthentication(player);
        player.sendMessage("");
        testSTTConnection(player);
        player.sendMessage("");
        testTTSConnection(player);
        player.sendMessage("");
        showConfiguration(player);
        
        player.sendMessage("§6=== 전체 테스트 완료 ===");
    }
} 