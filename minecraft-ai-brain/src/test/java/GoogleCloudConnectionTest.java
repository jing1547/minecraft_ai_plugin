import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Google Cloud API 실제 연결 테스트
 */
public class GoogleCloudConnectionTest {
    
    public static void main(String[] args) {
        System.out.println("=== Google Cloud API 연결 테스트 시작 ===");
        
        try {
            // 1. 환경변수 확인
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            System.out.println("1. 환경변수 확인:");
            System.out.println("   GOOGLE_APPLICATION_CREDENTIALS = " + credentialsPath);
            
            if (credentialsPath == null || credentialsPath.isEmpty()) {
                System.err.println("❌ 환경변수 GOOGLE_APPLICATION_CREDENTIALS가 설정되지 않았습니다!");
                return;
            }
            
            // 2. 인증 파일 읽기 테스트
            System.out.println("\n2. 인증 파일 읽기 테스트:");
            try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream);
                System.out.println("   ✅ 인증 파일 읽기 성공");
                
                // 프로젝트 ID 확인 (가능한 경우)
                if (credentials instanceof com.google.auth.oauth2.ServiceAccountCredentials) {
                    com.google.auth.oauth2.ServiceAccountCredentials saCredentials = 
                        (com.google.auth.oauth2.ServiceAccountCredentials) credentials;
                    System.out.println("   📋 프로젝트 ID: " + saCredentials.getProjectId());
                    System.out.println("   📧 서비스 계정: " + saCredentials.getClientEmail());
                }
            } catch (Exception e) {
                System.err.println("   ❌ 인증 파일 읽기 실패: " + e.getMessage());
                return;
            }
            
            // 3. Speech-to-Text 클라이언트 생성 테스트
            System.out.println("\n3. STT 클라이언트 생성 테스트:");
            try {
                SpeechSettings speechSettings = SpeechSettings.newBuilder().build();
                SpeechClient speechClient = SpeechClient.create(speechSettings);
                System.out.println("   ✅ STT 클라이언트 생성 성공");
                speechClient.close();
            } catch (Exception e) {
                System.err.println("   ❌ STT 클라이언트 생성 실패: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 4. Text-to-Speech 클라이언트 생성 테스트
            System.out.println("\n4. TTS 클라이언트 생성 테스트:");
            try {
                TextToSpeechSettings ttsSettings = TextToSpeechSettings.newBuilder().build();
                TextToSpeechClient ttsClient = TextToSpeechClient.create(ttsSettings);
                System.out.println("   ✅ TTS 클라이언트 생성 성공");
                ttsClient.close();
            } catch (Exception e) {
                System.err.println("   ❌ TTS 클라이언트 생성 실패: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 5. 실제 API 호출 테스트 (간단한 버전)
            System.out.println("\n5. 실제 API 연결 테스트:");
            try {
                TextToSpeechClient ttsClient = TextToSpeechClient.create();
                
                // ListVoices API 호출 - 가장 간단한 테스트
                var listVoicesRequest = com.google.cloud.texttospeech.v1.ListVoicesRequest.newBuilder().build();
                var response = ttsClient.listVoices(listVoicesRequest);
                
                System.out.println("   ✅ Google Cloud API 실제 연결 성공!");
                System.out.println("   📢 사용 가능한 음성 개수: " + response.getVoicesCount());
                
                ttsClient.close();
                
            } catch (com.google.api.gax.rpc.ApiException e) {
                System.err.println("   ❌ Google Cloud API 오류: " + e.getStatusCode() + " - " + e.getMessage());
                if (e.getStatusCode().getCode().name().equals("PERMISSION_DENIED")) {
                    System.err.println("   💡 권한 오류입니다. Google Cloud 프로젝트에서 Speech API와 Text-to-Speech API가 활성화되어 있는지 확인하세요.");
                }
            } catch (Exception e) {
                System.err.println("   ❌ API 연결 실패: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("\n=== 테스트 완료 ===");
            
        } catch (Exception e) {
            System.err.println("❌ 전체 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 