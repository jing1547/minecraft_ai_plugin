package com.minecraft.ai.brain.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.minecraft.ai.brain.utils.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.Map;
import java.util.HashMap;

/**
 * 한국어 최적화를 포함한 음성 인식 서비스
 * Korean-optimized Speech Recognition Service with gaming terminology support
 */
public class SpeechRecognitionService implements Service {
    private static final String SERVICE_ID = "speech-recognition";
    private static final String SERVICE_NAME = "SpeechRecognitionService";
    
    private SpeechClient speechClient;
    private final BlockingQueue<AudioRequest> audioQueue;
    private final ExecutorService processingExecutor;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private Consumer<RecognitionResult> resultCallback;
    private Logger logger;
    private State currentState = State.NOT_INITIALIZED;
    private long startTime = -1;
    
    // 한국어 게임 관련 용어들 - Korean gaming terminology
    private static final List<String> KOREAN_GAMING_PHRASES = Arrays.asList(
        // 게임 기본 용어
        "게임", "마인크래프트", "명령어", "플레이어", "서버",
        // 이동 관련
        "앞으로", "뒤로", "왼쪽", "오른쪽", "점프", "웅크리기", "달리기",
        // 액션 관련
        "블록", "파기", "놓기", "공격", "상호작용", "사용",
        // 아이템 관련
        "인벤토리", "도구", "무기", "음식", "포션", "재료",
        // 건축 관련
        "짓기", "만들기", "건설", "설치", "제거", "배치",
        // 방향 관련
        "북쪽", "남쪽", "동쪽", "서쪽", "위", "아래",
        // 숫자
        "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열"
    );
    
    // 한국어 방언 설정 - Korean dialect settings
    private static final List<String> KOREAN_LANGUAGE_VARIANTS = Arrays.asList(
        "ko-KR",  // 표준 한국어
        "ko-KR-Seoul",  // 서울 방언 (표준어 기반)
        "ko-KR-Busan"   // 부산 방언
    );
    
    /**
     * 오디오 요청을 나타내는 내부 클래스
     */
    private static class AudioRequest {
        final byte[] audioData;
        final long timestamp;
        final CompletableFuture<String> future;
        
        AudioRequest(byte[] audioData) {
            this.audioData = audioData;
            this.timestamp = System.currentTimeMillis();
            this.future = new CompletableFuture<>();
        }
    }
    
    /**
     * 인식 결과를 나타내는 클래스
     */
    public static class RecognitionResult {
        private final String transcript;
        private final float confidence;
        private final long processingTime;
        private final boolean isFinal;
        
        public RecognitionResult(String transcript, float confidence, long processingTime, boolean isFinal) {
            this.transcript = transcript;
            this.confidence = confidence;
            this.processingTime = processingTime;
            this.isFinal = isFinal;
        }
        
        public String getTranscript() { return transcript; }
        public float getConfidence() { return confidence; }
        public long getProcessingTime() { return processingTime; }
        public boolean isFinal() { return isFinal; }
        
        @Override
        public String toString() {
            return String.format("RecognitionResult{transcript='%s', confidence=%.2f, processingTime=%dms, isFinal=%s}",
                    transcript, confidence, processingTime, isFinal);
        }
    }
    
    public SpeechRecognitionService() {
        this.audioQueue = new LinkedBlockingQueue<>();
        this.processingExecutor = Executors.newFixedThreadPool(2); // 병렬 처리를 위한 스레드 풀
    }
    
    // Service interface implementations
    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }
    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }
    
    @Override
    public State getState() {
        return currentState;
    }
    
    @Override
    public Priority getPriority() {
        return Priority.NORMAL;
    }
    
    @Override
    public List<String> getDependencies() {
        return new ArrayList<>(); // No dependencies for now
    }
    
    @Override
    public void initialize(Map<String, Service> dependencies) throws ServiceException {
        try {
            currentState = State.INITIALIZED;
            // Initialize logger - for now using a placeholder
            // TODO: Get logger from ServiceManager when available
        } catch (Exception e) {
            currentState = State.FAILED;
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                "Failed to initialize " + SERVICE_NAME, e);
        }
    }
    
    @Override
    public void start() throws ServiceException {
        try {
            currentState = State.STARTING;
            if (logger != null) {
                logger.info("Starting " + SERVICE_NAME + "...");
            }
            
            // Speech client 초기화
            this.speechClient = SpeechToTextConfig.getSpeechClient();
            
            if (speechClient == null) {
                throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED,
                    "Failed to initialize Speech client");
            }
            
            // 오디오 처리 큐 시작
            startProcessingQueue();
            
            currentState = State.RUNNING;
            startTime = System.currentTimeMillis();
            
            if (logger != null) {
                logger.info(SERVICE_NAME + " started successfully");
            }
            
        } catch (ServiceException e) {
            currentState = State.FAILED;
            throw e;
        } catch (Exception e) {
            currentState = State.FAILED;
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED,
                "Failed to start " + SERVICE_NAME, e);
        }
    }
    
    @Override
    public void stop() throws ServiceException {
        try {
            currentState = State.STOPPING;
            if (logger != null) {
                logger.info("Stopping " + SERVICE_NAME + "...");
            }
            
            isProcessing.set(false);
            
            // 큐에 남은 작업들 완료 대기
            processingExecutor.shutdown();
            if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
            
            // Speech client 정리
            if (speechClient != null) {
                speechClient.close();
                speechClient = null;
            }
            
            currentState = State.STOPPED;
            
            if (logger != null) {
                logger.info(SERVICE_NAME + " stopped successfully");
            }
            
        } catch (Exception e) {
            currentState = State.FAILED;
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SHUTDOWN_FAILED,
                "Failed to stop " + SERVICE_NAME, e);
        }
    }
    
    @Override
    public ServiceHealth getHealth() {
        try {
            if (currentState != State.RUNNING) {
                return ServiceHealth.unhealthy("Service is not running");
            }
            
            if (speechClient == null) {
                return ServiceHealth.unhealthy("Speech client is not initialized");
            }
            
            // 큐 상태 확인
            int queueSize = audioQueue.size();
            if (queueSize > 100) {
                return ServiceHealth.degraded("Audio queue is getting large: " + queueSize + " items");
            }
            
            return ServiceHealth.healthy("Service is operational, queue size: " + queueSize);
            
        } catch (Exception e) {
            return ServiceHealth.unhealthy("Health check failed: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("queue_size", audioQueue.size());
        metrics.put("is_processing", isProcessing.get());
        metrics.put("uptime_ms", getUptime());
        return metrics;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("sample_rate", AudioProcessor.SAMPLE_RATE);
        config.put("language_code", SpeechToTextConfig.getLanguageCode());
        config.put("korean_gaming_phrases", KOREAN_GAMING_PHRASES.size());
        return config;
    }
    
    @Override
    public void onConfigurationChange(Map<String, Object> newConfig) {
        // TODO: Handle configuration changes
        if (logger != null) {
            logger.info("Configuration change received for " + SERVICE_NAME);
        }
    }
    
    @Override
    public boolean isEnabled() {
        return SpeechToTextConfig.isEnabled();
    }
    
    @Override
    public long getStartTime() {
        return startTime;
    }
    
    @Override
    public long getUptime() {
        return (currentState == State.RUNNING && startTime > 0) ? 
            System.currentTimeMillis() - startTime : 0;
    }
    
    // Additional methods
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * 결과 콜백 설정
     */
    public void setResultCallback(Consumer<RecognitionResult> callback) {
        this.resultCallback = callback;
    }
    
    /**
     * 오디오 데이터를 큐에 추가하여 비동기 처리
     */
    public CompletableFuture<String> queueAudio(byte[] audioData) {
        if (currentState != State.RUNNING) {
            return CompletableFuture.failedFuture(new IllegalStateException("Service is not running"));
        }
        
        if (!AudioProcessor.isValidAudioData(audioData)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid audio data"));
        }
        
        AudioRequest request = new AudioRequest(audioData);
        if (audioQueue.offer(request)) {
            if (logger != null) {
                logger.debug("Audio queued for processing, queue size: " + audioQueue.size());
            }
            return request.future;
        } else {
            return CompletableFuture.failedFuture(new RuntimeException("Audio queue is full"));
        }
    }
    
    /**
     * 동기 음성 인식 처리
     */
    public RecognitionResult recognizeSpeech(byte[] audioData) throws ServiceException {
        if (currentState != State.RUNNING) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.INVALID_CONFIGURATION,
                "Service is not running");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 오디오 전처리
            byte[] processedAudio = AudioProcessor.preprocessAudio(audioData);
            
            if (!AudioProcessor.detectVoiceActivity(processedAudio)) {
                if (logger != null) {
                    logger.debug("No voice activity detected in audio");
                }
                return new RecognitionResult("", 0.0f, System.currentTimeMillis() - startTime, true);
            }
            
            // 한국어 최적화 설정으로 인식 구성
            RecognitionConfig config = buildKoreanOptimizedConfig();
            
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(processedAudio))
                    .build();
            
            if (logger != null) {
                logger.debug("Sending audio to Google Speech API...");
            }
            RecognizeResponse response = speechClient.recognize(config, audio);
            
            String transcript = extractBestTranscript(response);
            float confidence = extractBestConfidence(response);
            long processingTime = System.currentTimeMillis() - startTime;
            
            RecognitionResult result = new RecognitionResult(transcript, confidence, processingTime, true);
            
            if (logger != null) {
                logger.info("Speech recognition completed: " + result);
            }
            
            // 콜백 호출
            if (resultCallback != null) {
                resultCallback.accept(result);
            }
            
            return result;
            
        } catch (Exception e) {
            if (logger != null) {
                logger.severe("Speech recognition failed: " + e.getMessage());
            }
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED,
                "Speech recognition failed", e);
        }
    }
    
    /**
     * 한국어 최적화된 인식 설정 구성
     */
    private RecognitionConfig buildKoreanOptimizedConfig() {
        // 한국어 게임 용어를 위한 Speech Context 생성
        SpeechContext.Builder contextBuilder = SpeechContext.newBuilder();
        KOREAN_GAMING_PHRASES.forEach(contextBuilder::addPhrases);
        SpeechContext speechContext = contextBuilder.build();
        
        return RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(AudioProcessor.SAMPLE_RATE)
                .setAudioChannelCount(AudioProcessor.CHANNELS)
                .setLanguageCode(SpeechToTextConfig.getLanguageCode())
                .setModel("latest_long")  // 긴 발화에 최적화된 모델
                .setEnableAutomaticPunctuation(true)  // 자동 구두점
                .setUseEnhanced(true)  // 향상된 모델 사용
                .setEnableWordTimeOffsets(true)  // 단어별 타이밍 정보
                .setEnableWordConfidence(true)  // 단어별 신뢰도
                .setMaxAlternatives(3)  // 최대 3개의 대안 제공
                .addSpeechContexts(speechContext)  // 게임 용어 컨텍스트
                .setProfanityFilter(false)  // 욕설 필터 비활성화 (게임 용어 보호)
                .setMetadata(
                    RecognitionMetadata.newBuilder()
                        .setInteractionType(RecognitionMetadata.InteractionType.VOICE_COMMAND)  // 음성 명령 타입
                        .setMicrophoneDistance(RecognitionMetadata.MicrophoneDistance.NEARFIELD)  // 근거리 마이크
                        .setOriginalMediaType(RecognitionMetadata.OriginalMediaType.AUDIO)
                        .setRecordingDeviceType(RecognitionMetadata.RecordingDeviceType.PC)
                        .build()
                )
                .build();
    }
    
    /**
     * 응답에서 최상의 전사 텍스트 추출
     */
    private String extractBestTranscript(RecognizeResponse response) {
        if (response.getResultsCount() == 0) {
            return "";
        }
        
        SpeechRecognitionResult bestResult = response.getResults(0);
        if (bestResult.getAlternativesCount() == 0) {
            return "";
        }
        
        return bestResult.getAlternatives(0).getTranscript().trim();
    }
    
    /**
     * 응답에서 최상의 신뢰도 추출
     */
    private float extractBestConfidence(RecognizeResponse response) {
        if (response.getResultsCount() == 0) {
            return 0.0f;
        }
        
        SpeechRecognitionResult bestResult = response.getResults(0);
        if (bestResult.getAlternativesCount() == 0) {
            return 0.0f;
        }
        
        return bestResult.getAlternatives(0).getConfidence();
    }
    
    /**
     * 오디오 처리 큐 시작
     */
    private void startProcessingQueue() {
        isProcessing.set(true);
        
        processingExecutor.submit(() -> {
            if (logger != null) {
                logger.info("Audio processing queue started");
            }
            
            while (isProcessing.get()) {
                try {
                    AudioRequest request = audioQueue.poll(1, TimeUnit.SECONDS);
                    if (request != null) {
                        processAudioRequest(request);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (logger != null) {
                        logger.severe("Error in audio processing queue: " + e.getMessage());
                    }
                }
            }
            
            if (logger != null) {
                logger.info("Audio processing queue stopped");
            }
        });
    }
    
    /**
     * 개별 오디오 요청 처리
     */
    private void processAudioRequest(AudioRequest request) {
        try {
            RecognitionResult result = recognizeSpeech(request.audioData);
            request.future.complete(result.getTranscript());
        } catch (Exception e) {
            if (logger != null) {
                logger.severe("Failed to process audio request: " + e.getMessage());
            }
            request.future.completeExceptionally(e);
        }
    }
    
    /**
     * 큐 상태 정보 반환
     */
    public String getQueueStatus() {
        return String.format("Queue size: %d, Processing: %s", 
                audioQueue.size(), isProcessing.get());
    }
    
    /**
     * 지원되는 한국어 방언 목록 반환
     */
    public static List<String> getSupportedKoreanVariants() {
        return new ArrayList<>(KOREAN_LANGUAGE_VARIANTS);
    }
    
    /**
     * 게임 관련 한국어 용어 목록 반환
     */
    public static List<String> getKoreanGamingPhrases() {
        return new ArrayList<>(KOREAN_GAMING_PHRASES);
    }
} 