package com.minecraft.ai.brain.service;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * AudioCaptureService captures and processes audio input from players
 * Provides real-time audio monitoring and voice activity detection
 */
public class AudioCaptureService implements Service {
    
    private static final String SERVICE_ID = "audio_capture";
    private static final int AUDIO_SAMPLE_RATE = 16000; // 16kHz
    private static final int AUDIO_BITS_PER_SAMPLE = 16;
    private static final int AUDIO_CHANNELS = 1; // Mono
    private static final int BUFFER_SIZE = 4096;
    private static final double SILENCE_THRESHOLD = 0.01; // Threshold for detecting silence
    private static final int VOICE_ACTIVITY_WINDOW = 10; // Number of frames to analyze for voice activity
    
    private final Logger logger = Logger.getLogger(AudioCaptureService.class.getName());
    
    // Core service components
    private final Map<UUID, AudioSession> activeSessions = new ConcurrentHashMap<>();
    private final BlockingQueue<AudioData> audioQueue = new LinkedBlockingQueue<>();
    private final ExecutorService audioProcessor = Executors.newFixedThreadPool(3);
    
    // STT service integration
    private SpeechRecognitionService speechRecognitionService;
    private STTSessionManager sttSessionManager;
    
    // ✅ VAD (Voice Activity Detection) 관련
    private final Map<UUID, VoiceSession> playerVoiceSessions = new ConcurrentHashMap<>();
    private final Map<UUID, VoiceActivityDetector> playerVADs = new ConcurrentHashMap<>();
    private static final long MIN_VOICE_DURATION = 500;       // 최소 음성 길이 (ms)
    private static final long MAX_VOICE_DURATION = 15000;     // 최대 음성 길이 (ms)
    private static final long SILENCE_TIMEOUT = 1500;         // 침묵 타임아웃 (ms)
    private static final int VAD_FRAME_SIZE = 640;            // VAD 프레임 크기 (40ms at 16kHz)
    
    /**
     * 플레이어별 음성 세션 관리
     */
    private static class VoiceSession {
        private ByteArrayOutputStream audioBuffer;
        private ByteArrayOutputStream vadBuffer; // VAD 분석용 버퍼
        private long recordingStartTime;
        private long lastVoiceActivityTime;
        private boolean isRecording;
        private boolean isVoiceDetected;
        private int silenceFrameCount;
        private int totalFrameCount;
        
        public VoiceSession() {
            this.audioBuffer = new ByteArrayOutputStream();
            this.vadBuffer = new ByteArrayOutputStream();
            this.recordingStartTime = 0;
            this.lastVoiceActivityTime = 0;
            this.isRecording = false;
            this.isVoiceDetected = false;
            this.silenceFrameCount = 0;
            this.totalFrameCount = 0;
        }
        
        public void startRecording() {
            audioBuffer.reset();
            vadBuffer.reset();
            recordingStartTime = System.currentTimeMillis();
            lastVoiceActivityTime = recordingStartTime;
            isRecording = true;
            isVoiceDetected = true;
            silenceFrameCount = 0;
            totalFrameCount = 0;
        }
        
        public void stopRecording() {
            isRecording = false;
        }
        
        public void addAudioData(byte[] data) {
            if (isRecording) {
                try {
                    audioBuffer.write(data);
                } catch (java.io.IOException e) {
                    // Handle silently
                }
            }
        }
        
        public void addVADData(byte[] data) {
            try {
                vadBuffer.write(data);
            } catch (java.io.IOException e) {
                // Handle silently
            }
        }
        
        public byte[] getVADData() {
            byte[] data = vadBuffer.toByteArray();
            vadBuffer.reset();
            return data;
        }
        
        public boolean hasVADData() {
            return vadBuffer.size() >= VAD_FRAME_SIZE;
        }
        
        public byte[] getRecordedAudio() {
            return audioBuffer.toByteArray();
        }
        
        public long getRecordingDuration() {
            return isRecording ? System.currentTimeMillis() - recordingStartTime : 0;
        }
        
        public boolean shouldEndRecording(long currentTime) {
            // 침묵이 일정 시간 지속되거나, 최대 길이 초과시 종료
            return (currentTime - lastVoiceActivityTime > SILENCE_TIMEOUT) || 
                   (getRecordingDuration() > MAX_VOICE_DURATION);
        }
        
        public boolean isMinimumDurationMet() {
            return getRecordingDuration() >= MIN_VOICE_DURATION;
        }
        
        public void updateVoiceActivity(boolean voiceDetected, long currentTime) {
            totalFrameCount++;
            
            if (voiceDetected) {
                this.lastVoiceActivityTime = currentTime;
                this.isVoiceDetected = true;
                this.silenceFrameCount = 0;
            } else {
                this.silenceFrameCount++;
            }
        }
        
        // Getters
        public boolean isRecording() { return isRecording; }
        public boolean isVoiceDetected() { return isVoiceDetected; }
        public long getLastVoiceActivityTime() { return lastVoiceActivityTime; }
        public long getRecordingStartTime() { return recordingStartTime; }
        public int getSilenceFrameCount() { return silenceFrameCount; }
        public int getTotalFrameCount() { return totalFrameCount; }
    }
    
    // Audio monitoring
    private final AtomicInteger currentVolumeLevel = new AtomicInteger(0); // 0-100
    private final AtomicLong totalAudioFrames = new AtomicLong(0);
    private final AtomicLong voiceActiveFrames = new AtomicLong(0);
    private final AtomicBoolean voiceActivityDetected = new AtomicBoolean(false);
    private final Queue<Double> recentVolumeHistory = new ConcurrentLinkedQueue<>();
    
    // Player-specific monitoring control
    private final Set<UUID> monitoringPlayers = ConcurrentHashMap.newKeySet();
    
    // Real microphone capture components
    private TargetDataLine microphone;
    private AudioFormat audioFormat;
    private Future<?> microphoneCaptureTask;
    private volatile boolean isMicrophoneCapturing = false;
    
    // Service state
    private boolean isRunning = false;
    private Future<?> queueProcessorTask;
    private Future<?> volumeMonitorTask;
    private State currentState = State.NOT_INITIALIZED;
    private long startTime = -1;
    private boolean enabled = true;
    
    /**
     * Audio session for a player
     */
    private static class AudioSession {
        private final UUID playerId;
        private final String playerName;
        private final long startTime;
        private final BlockingQueue<byte[]> audioBuffer;
        private volatile boolean isActive;
        private volatile boolean isPushToTalkActive;
        
        public AudioSession(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.startTime = System.currentTimeMillis();
            this.audioBuffer = new LinkedBlockingQueue<>();
            this.isActive = true;
            this.isPushToTalkActive = false;
        }
        
        public void addAudioData(byte[] data) {
            if (isActive && audioBuffer.size() < 100) { // Prevent buffer overflow
                audioBuffer.offer(data);
            }
        }
        
        public byte[] getAudioData() {
            return audioBuffer.poll();
        }
        
        public boolean hasAudioData() {
            return !audioBuffer.isEmpty();
        }
        
        public void stop() {
            isActive = false;
            audioBuffer.clear();
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getStartTime() { return startTime; }
        public boolean isActive() { return isActive; }
        public boolean isPushToTalkActive() { return isPushToTalkActive; }
        public void setPushToTalkActive(boolean active) { this.isPushToTalkActive = active; }
    }
    
    /**
     * Audio data container
     */
    private static class AudioData {
        private final UUID playerId;
        private final byte[] data;
        private final long timestamp;
        
        public AudioData(UUID playerId, byte[] data) {
            this.playerId = playerId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public UUID getPlayerId() { return playerId; }
        public byte[] getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
    
    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }
    
    @Override
    public String getServiceName() {
        return "Audio Capture Service";
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
        return Arrays.asList(); // No dependencies for now
    }
    
    @Override
    public void initialize(Map<String, Service> dependencies) throws ServiceException {
        if (currentState != State.NOT_INITIALIZED) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.INITIALIZATION_FAILED, "Service already initialized");
        }
        
        currentState = State.INITIALIZED;
        
        logger.info("Initializing Audio Capture Service...");
        
        // Initialize STT service if enabled
        if (SpeechToTextConfig.isEnabled()) {
            try {
                logger.info("Initializing SpeechRecognitionService...");
                speechRecognitionService = new SpeechRecognitionService();
                sttSessionManager = new STTSessionManager();
                logger.info("SpeechRecognitionService initialized successfully");
            } catch (Exception e) {
                logger.severe("Failed to initialize SpeechRecognitionService: " + e.getMessage());
                speechRecognitionService = null;
                sttSessionManager = null;
                // Continue without STT
            }
        } else {
            logger.warning("Speech-to-Text is disabled - Audio Capture Service will have limited functionality");
        }
        
        logger.info("Audio Capture Service initialized successfully");
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("active_sessions", activeSessions.size());
        metrics.put("audio_queue_size", audioQueue.size());
        metrics.put("is_running", isRunning);
        metrics.put("start_time", startTime);
        metrics.put("uptime", getUptime());
        return metrics;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("sample_rate", AUDIO_SAMPLE_RATE);
        config.put("bits_per_sample", AUDIO_BITS_PER_SAMPLE);
        config.put("channels", AUDIO_CHANNELS);
        config.put("buffer_size", BUFFER_SIZE);
        config.put("enabled", enabled);
        return config;
    }
    
    @Override
    public void onConfigurationChange(Map<String, Object> newConfig) {
        // Handle configuration changes
        if (newConfig.containsKey("enabled")) {
            enabled = (Boolean) newConfig.get("enabled");
        }
        logger.info("Configuration updated for Audio Capture Service");
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public long getStartTime() {
        return startTime;
    }
    
    @Override
    public long getUptime() {
        if (startTime == -1 || !isRunning) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    @Override
    public void start() throws ServiceException {
        if (currentState != State.INITIALIZED) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                                     "AudioCaptureService must be initialized before starting");
        }
        
        if (isRunning) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                                     "Audio Capture Service is already running");
        }
        
        logger.info("Starting Audio Capture Service...");
        
        currentState = State.STARTING;
        
        isRunning = true;
        startTime = System.currentTimeMillis();
        
        // Start audio queue processor
        queueProcessorTask = audioProcessor.submit(this::processAudioQueue);
        
        currentState = State.RUNNING;
        
        logger.info("Audio Capture Service started successfully");
    }
    
    @Override
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        logger.info("Stopping Audio Capture Service...");
        
        currentState = State.STOPPING;
        
        isRunning = false;
        
        // Stop all active sessions
        activeSessions.values().forEach(AudioSession::stop);
        activeSessions.clear();
        
        // Stop queue processor
        if (queueProcessorTask != null) {
            queueProcessorTask.cancel(true);
        }
        
        // Clear audio queue
        audioQueue.clear();
        
        currentState = State.STOPPED;
        
        logger.info("Audio Capture Service stopped successfully");
    }
    
    public void shutdown() {
        stop();
        
        // Shutdown STT session manager
        if (sttSessionManager != null) {
            sttSessionManager.shutdown();
        }
        
        // Shutdown executor
        audioProcessor.shutdown();
        try {
            if (!audioProcessor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                audioProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            audioProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Audio Capture Service shut down successfully");
    }
    
    @Override
    public ServiceHealth getHealth() {
        if (isRunning) {
            return ServiceHealth.healthy("Audio Capture Service is running")
                .withDetail("active_sessions", activeSessions.size())
                .withDetail("audio_queue_size", audioQueue.size());
        } else {
            return ServiceHealth.unhealthy("Audio Capture Service is not running");
        }
    }
    
    /**
     * Start audio capture session for a player
     * @param player The player to start capture for
     * @return true if session started successfully
     */
    public boolean startCaptureSession(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        
        if (activeSessions.containsKey(playerId)) {
            logger.warning("Audio capture session already exists for player: " + playerName);
            return false;
        }
        
        AudioSession session = new AudioSession(playerId, playerName);
        activeSessions.put(playerId, session);
        
        logger.info("Started audio capture session for player: " + playerName);
        return true;
    }
    
    /**
     * Stop audio capture session for a player
     * @param player The player to stop capture for
     * @return true if session stopped successfully
     */
    public boolean stopCaptureSession(Player player) {
        UUID playerId = player.getUniqueId();
        AudioSession session = activeSessions.remove(playerId);
        
        if (session == null) {
            logger.warning("No active audio capture session for player: " + player.getName());
            return false;
        }
        
        session.stop();
        logger.info("Stopped audio capture session for player: " + player.getName());
        return true;
    }
    
    /**
     * Process incoming audio data from a player
     * @param player The player sending audio
     * @param audioData The raw audio data
     */
    public void processAudioData(Player player, byte[] audioData) {
        UUID playerId = player.getUniqueId();
        AudioSession session = activeSessions.get(playerId);
        
        if (session == null) {
            logger.warning("Received audio data for inactive session: " + player.getName());
            return;
        }
        
        if (!session.isActive()) {
            logger.warning("Received audio data for stopped session: " + player.getName());
            return;
        }
        
        // Preprocess audio data
        byte[] processedData = preprocessAudio(audioData);
        
        // Add to session buffer
        session.addAudioData(processedData);
        
        // Queue for further processing
        audioQueue.offer(new AudioData(playerId, processedData));
    }
    
    /**
     * Set push-to-talk state for a player
     * @param player The player
     * @param active Whether push-to-talk is active
     */
    public void setPushToTalkState(Player player, boolean active) {
        AudioSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.setPushToTalkActive(active);
            logger.fine("Push-to-talk " + (active ? "activated" : "deactivated") + " for player: " + player.getName());
        }
    }
    
    /**
     * Get active audio sessions
     * @return Map of active sessions
     */
    public Map<UUID, String> getActiveSessions() {
        Map<UUID, String> sessions = new ConcurrentHashMap<>();
        activeSessions.forEach((id, session) -> {
            if (session.isActive()) {
                sessions.put(id, session.getPlayerName());
            }
        });
        return sessions;
    }
    
    /**
     * Get STT session statistics
     * @return Statistics map
     */
    public Map<String, Object> getSTTStatistics() {
        if (sttSessionManager != null) {
            return sttSessionManager.getStatistics();
        }
        return new HashMap<>();
    }
    
    /**
     * Preprocess audio data (noise reduction, normalization)
     * @param audioData Raw audio data
     * @return Processed audio data
     */
    private byte[] preprocessAudio(byte[] audioData) {
        // Basic audio preprocessing
        // In a real implementation, this would include:
        // - Noise reduction
        // - Volume normalization
        // - Format conversion
        
        // For now, apply simple volume normalization
        return normalizeVolume(audioData);
    }
    
    /**
     * Normalize audio volume
     * @param audioData Raw audio data
     * @return Normalized audio data
     */
    private byte[] normalizeVolume(byte[] audioData) {
        // Simple volume normalization
        // Convert bytes to samples, normalize, convert back
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        // Simple amplitude normalization
        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert bytes to 16-bit sample
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            
            // Apply simple normalization (this is very basic)
            // In production, use proper audio processing libraries
            sample = (short) Math.max(-32768, Math.min(32767, sample));
            
            // Convert back to bytes
            output.write(sample & 0xFF);
            output.write((sample >> 8) & 0xFF);
        }
        
        return output.toByteArray();
    }
    
    /**
     * Process audio queue continuously
     */
    private void processAudioQueue() {
        logger.info("Audio queue processor started");
        
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                AudioData audioData = audioQueue.take();
                
                // Get session
                AudioSession session = activeSessions.get(audioData.getPlayerId());
                if (session == null || !session.isActive()) {
                    continue;
                }
                
                // Check if push-to-talk is required and active
                if (isPushToTalkRequired() && !session.isPushToTalkActive()) {
                    continue;
                }
                
                // Process audio data for STT
                processAudioForSTT(audioData);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing audio queue", e);
            }
        }
        
        logger.info("Audio queue processor stopped");
    }
    
    /**
     * ✅ 새로운 VAD 기반 오디오 처리
     * Process audio data using Voice Activity Detection
     * @param audioData Audio data to process
     */
    private void processAudioForSTT(AudioData audioData) {
        if (speechRecognitionService == null || !SpeechToTextConfig.isEnabled()) {
            return;
        }
        
        UUID playerId = audioData.getPlayerId();
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        
        if (player == null || !player.isOnline()) {
            return;
        }

        // STT 세션 확인
        if (sttSessionManager != null) {
            var sttSession = sttSessionManager.getSession(playerId);
            if (sttSession == null) {
                // 세션이 없거나 일시 중지된 경우
                sttSession = sttSessionManager.createSession(playerId);
                if (sttSession == null) {
                    logger.warning("Cannot create STT session for player " + player.getName() + " (suspended)");
                    return;
                }
            }
            sttSessionManager.updateSessionActivity(playerId);
        }
        
        try {
            byte[] audioBytes = audioData.getData();
            long currentTime = System.currentTimeMillis();
            
            // 플레이어별 VAD와 세션 가져오기
            VoiceActivityDetector vad = playerVADs.computeIfAbsent(playerId, k -> new VoiceActivityDetector());
            VoiceSession session = playerVoiceSessions.computeIfAbsent(playerId, k -> new VoiceSession());
            
            // VAD 버퍼에 데이터 추가
            session.addVADData(audioBytes);
            
            // VAD 프레임 크기만큼 데이터가 모이면 분석
            while (session.hasVADData()) {
                byte[] vadFrame = session.getVADData();
                
                // VAD로 음성 활동 감지
                boolean voiceDetected = vad.detectSpeech(vadFrame);
                session.updateVoiceActivity(voiceDetected, currentTime);
                
                // 상태별 처리
                if (!session.isRecording()) {
                    // 🎤 녹음 중이 아님 - 음성 시작 감지
                    if (voiceDetected && vad.isInSpeechSegment()) {
                        // 음성 시작 감지!
                        session.startRecording();
                        session.addAudioData(vadFrame);
                        
                        logger.info("Voice recording STARTED for player: " + player.getName() + 
                                   " (SNR: " + String.format("%.2f", vad.getSignalToNoiseRatio()) + 
                                   ", Noise: " + String.format("%.4f", vad.getBackgroundNoiseLevel()) + ")");
                        
                        // 플레이어에게 녹음 시작 알림
                        org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                            () -> player.sendMessage("§a[음성] 🎤 말씀해주세요...")
                        );
                    }
                    
                } else {
                    // 📼 녹음 중 - 오디오 데이터 누적
                    session.addAudioData(vadFrame);
                    
                    // 디버그용 주기적 상태 표시
                    if (session.getTotalFrameCount() % 250 == 0) { // 약 5초마다
                        logger.info("Recording in progress for " + player.getName() + 
                                   " - Duration: " + session.getRecordingDuration() + "ms, " +
                                   "VAD Status: " + vad.getVADStats());
                    }
                    
                    // 녹음 종료 조건 확인
                    if (!voiceDetected && !vad.isInSpeechSegment() && session.shouldEndRecording(currentTime)) {
                        // 음성 종료 감지!
                        byte[] recordedAudio = session.getRecordedAudio();
                        long duration = session.getRecordingDuration();
                        
                        session.stopRecording();
                        vad.reset(); // VAD 상태 리셋
                        
                        logger.info("Voice recording ENDED for player: " + player.getName() + 
                                   " - Duration: " + duration + "ms, Size: " + recordedAudio.length + " bytes");
                        
                        // 최소 길이 체크
                        if (session.isMinimumDurationMet() && recordedAudio.length > 0) {
                            // 🎯 STT 처리
                            processRecordedVoice(player, recordedAudio, duration);
                        } else {
                            // 너무 짧은 녹음
                            logger.info("Recording too short, ignored: " + duration + "ms");
                            org.bukkit.Bukkit.getScheduler().runTask(
                                org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                                () -> player.sendMessage("§6[음성] 너무 짧게 말씀하셨습니다. 다시 시도해주세요.")
                            );
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.severe("Error in VAD processing for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            
            // 오류 기록
            if (sttSessionManager != null) {
                sttSessionManager.recordError(playerId, "VAD processing error: " + e.getMessage());
            }
        }
    }
    
    /**
     * ✅ 녹음된 음성을 STT로 처리
     * Process recorded voice with Speech-to-Text
     * @param player The player who spoke
     * @param audioData The recorded audio data
     * @param duration Recording duration in milliseconds
     */
    private void processRecordedVoice(Player player, byte[] audioData, long duration) {
        try {
            // 오디오 품질 검증
            AudioQualityResult qualityResult = validateAudioQuality(audioData);
            
            if (!qualityResult.isValid()) {
                // 품질 문제 피드백
                org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                    () -> {
                        switch (qualityResult.reason) {
                            case TOO_QUIET:
                                player.sendMessage("§6[STT] 음성이 너무 작습니다. 더 크게 말씀해주세요.");
                                break;
                            case NO_VOICE_ACTIVITY:
                                player.sendMessage("§6[STT] 명확한 음성이 감지되지 않았습니다.");
                                break;
                            case MOSTLY_NOISE:
                                player.sendMessage("§6[STT] 주변 소음이 많습니다. 조용한 곳에서 다시 시도해주세요.");
                                break;
                            default:
                                player.sendMessage("§6[STT] 음성 품질이 좋지 않습니다. 다시 시도해주세요.");
                                break;
                        }
                    }
                );
                return;
            }
            
            // STT 처리 시작 알림
            org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                () -> player.sendMessage("§e[STT] 🔄 음성 인식 중... (" + 
                    String.format("%.1f", duration/1000.0) + "초, " + audioData.length + " bytes, 품질: " + qualityResult.qualityScore + "%)")
            );
            
            logger.info("Starting STT processing for " + player.getName() + 
                       " - Duration: " + duration + "ms, Quality: " + qualityResult.qualityScore + "%");
            
            // 비동기 STT 처리
            audioProcessor.submit(() -> {
                try {
                    logger.info("Starting Google Cloud Speech API call for player: " + player.getName());
                    long startTime = System.currentTimeMillis();
                    
                    String recognizedText = speechRecognitionService.recognizeSpeech(audioData);
                    
                    long endTime = System.currentTimeMillis();
                    logger.info("Google Cloud Speech API call completed for player: " + player.getName() + 
                               " (took " + (endTime - startTime) + "ms)");
                    
                    if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                        logger.info("STT Recognition Success for " + player.getName() + ": '" + recognizedText + "'");
                        
                        // 성공 메시지
                        org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                            () -> {
                                player.sendMessage("§a[STT] ✅ 인식 완료: " + recognizedText);
                                processRecognizedSpeech(player, recognizedText.trim());
                            }
                        );
                    } else {
                        logger.warning("STT Recognition returned empty/null result for player: " + player.getName());
                        
                        // 인식 실패 메시지
                        org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                            () -> player.sendMessage("§6[STT] ❌ 음성을 명확하게 인식하지 못했습니다. 다시 시도해주세요.")
                        );
                    }
                } catch (Exception e) {
                    logger.severe("STT processing error for player " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    
                    // 오류 기록
                    if (sttSessionManager != null) {
                        sttSessionManager.recordError(player.getUniqueId(), "STT processing error: " + e.getMessage());
                    }
                    
                    // 에러 메시지
                    org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                        () -> player.sendMessage("§c[STT] ⚠️ 음성 인식 오류: " + e.getMessage())
                    );
                }
            });
            
        } catch (Exception e) {
            logger.severe("Error processing recorded voice: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 오디오 품질 검증 결과
     */
    private static class AudioQualityResult {
        final boolean valid;
        final QualityIssue reason;
        final int qualityScore; // 0-100
        final boolean hasVoiceActivity;
        
        AudioQualityResult(boolean valid, QualityIssue reason, int qualityScore, boolean hasVoiceActivity) {
            this.valid = valid;
            this.reason = reason;
            this.qualityScore = qualityScore;
            this.hasVoiceActivity = hasVoiceActivity;
        }
        
        boolean isValid() { return valid; }
    }
    
    /**
     * 오디오 품질 문제 유형
     */
    private enum QualityIssue {
        TOO_SHORT, TOO_QUIET, NO_VOICE_ACTIVITY, MOSTLY_NOISE, NONE
    }
    
    /**
     * ✅ 새로운 기능: 오디오 품질 검증
     * @param audioBytes 검증할 오디오 데이터
     * @return 품질 검증 결과
     */
    private AudioQualityResult validateAudioQuality(byte[] audioBytes) {
        // 최소 길이 체크 (최소 0.5초)
        int minBytes = (AUDIO_SAMPLE_RATE * AUDIO_BITS_PER_SAMPLE / 8) / 2;
        if (audioBytes.length < minBytes) {
            return new AudioQualityResult(false, QualityIssue.TOO_SHORT, 0, false);
        }
        
        // RMS 볼륨 계산
        double rms = calculateRMS(audioBytes);
        double volume = Math.min(100.0, rms * 1000); // 0-100 스케일
        
        // 너무 조용한지 체크
        if (volume < 5.0) {
            return new AudioQualityResult(false, QualityIssue.TOO_QUIET, (int)volume, false);
        }
        
        // 음성 활동 감지
        boolean hasVoiceActivity = detectVoiceActivity(audioBytes, rms);
        
        if (!hasVoiceActivity) {
            return new AudioQualityResult(false, QualityIssue.NO_VOICE_ACTIVITY, (int)volume, false);
        }
        
        // 노이즈 대비 신호 비율 추정
        double signalToNoiseRatio = estimateSignalToNoise(audioBytes);
        
        if (signalToNoiseRatio < 0.3) { // 30% 미만이면 노이즈가 너무 많음
            return new AudioQualityResult(false, QualityIssue.MOSTLY_NOISE, (int)volume, hasVoiceActivity);
        }
        
        // 품질 점수 계산 (볼륨, 음성 활동, 신호대잡음비 종합)
        int qualityScore = (int)Math.min(100, (volume * 0.3 + signalToNoiseRatio * 70));
        
        return new AudioQualityResult(true, QualityIssue.NONE, qualityScore, hasVoiceActivity);
    }
    
    /**
     * ✅ 새로운 기능: 음성 활동 감지
     * @param audioData 오디오 데이터
     * @param avgRms 평균 RMS 값
     * @return 음성 활동이 감지되었는지 여부
     */
    private boolean detectVoiceActivity(byte[] audioData, double avgRms) {
        int frameSize = 1024; // 약 64ms at 16kHz
        int voiceFrames = 0;
        int totalFrames = 0;
        
        for (int i = 0; i < audioData.length - frameSize; i += frameSize) {
            byte[] frame = java.util.Arrays.copyOfRange(audioData, i, i + frameSize);
            double frameRms = calculateRMS(frame);
            
            // 프레임이 충분히 크고, 변화가 있으면 음성으로 간주
            if (frameRms > avgRms * 0.5 && frameRms > 0.01) {
                voiceFrames++;
            }
            totalFrames++;
        }
        
        // 전체 프레임의 20% 이상에서 음성 활동이 있으면 유효한 음성으로 판단
        return totalFrames > 0 && (double)voiceFrames / totalFrames >= 0.2;
    }
    
    /**
     * ✅ 새로운 기능: 신호 대 잡음 비율 추정
     * @param audioData 오디오 데이터
     * @return 신호 대 잡음 비율 (0.0 ~ 1.0)
     */
    private double estimateSignalToNoise(byte[] audioData) {
        int frameSize = 512;
        double maxFrameRms = 0;
        double minFrameRms = Double.MAX_VALUE;
        
        for (int i = 0; i < audioData.length - frameSize; i += frameSize) {
            byte[] frame = java.util.Arrays.copyOfRange(audioData, i, i + frameSize);
            double frameRms = calculateRMS(frame);
            
            maxFrameRms = Math.max(maxFrameRms, frameRms);
            minFrameRms = Math.min(minFrameRms, frameRms);
        }
        
        // 최대값과 최소값의 비율로 신호 품질 추정
        if (minFrameRms == 0 || maxFrameRms == 0) {
            return 0.0;
        }
        
        return Math.min(1.0, (maxFrameRms - minFrameRms) / maxFrameRms);
    }
    
    /**
     * Process recognized speech text
     * @param player Player who spoke
     * @param recognizedText Recognized speech text
     */
    private void processRecognizedSpeech(Player player, String recognizedText) {
        try {
            logger.info("Processing recognized speech from " + player.getName() + ": " + recognizedText);
            
            // 음성 명령어 트리거 키워드들 (대소문자 구분 없음)
            String lowerText = recognizedText.toLowerCase().trim();
            
            // 다양한 음성 명령어 시작 키워드들
            String[] commandTriggers = {
                "명령어", "커맨드", "command", "실행", "마인크래프트"
            };
            
            boolean isCommand = false;
            String commandText = recognizedText;
            
            for (String trigger : commandTriggers) {
                if (lowerText.startsWith(trigger.toLowerCase())) {
                    isCommand = true;
                    // 트리거 키워드 제거하고 실제 명령어 부분만 추출
                    commandText = recognizedText.substring(trigger.length()).trim();
                    break;
                }
            }
            
            // 기존 "!" 시작도 여전히 지원 (텍스트 명령어용)
            if (recognizedText.startsWith("!")) {
                isCommand = true;
                commandText = recognizedText;
            }
            
            if (isCommand) {
                player.sendMessage("§b[Voice Command] " + commandText);
                logger.info("Voice command detected: " + commandText);
                // TODO: Process voice commands
                return;
            }
            
            // Send to AI conversation system
            player.sendMessage("§b[AI Conversation] " + recognizedText);
            logger.info("Sending to AI conversation: " + recognizedText);
            // TODO: Send to AI conversation system
            
        } catch (Exception e) {
            logger.severe("Error processing recognized speech: " + e.getMessage());
        }
    }
    
    /**
     * Check if push-to-talk is required
     * @return true if push-to-talk is required
     */
    private boolean isPushToTalkRequired() {
        // TODO: Read from configuration
        return false; // Default to false for now
    }
    
    /**
     * Get audio format specifications
     * @return Audio format info as string
     */
    public String getAudioFormatInfo() {
        return String.format(
            "Audio Format: %d Hz, %d-bit, %d channel(s)",
            AUDIO_SAMPLE_RATE,
            AUDIO_BITS_PER_SAMPLE,
            AUDIO_CHANNELS
        );
    }
    
    /**
     * Start real-time audio monitoring for microphone testing
     */
    public void startAudioMonitoring(Player player) {
        if (!isRunning) {
            logger.warning("Cannot start audio monitoring - service not running");
            return;
        }
        
        logger.info("Starting audio monitoring for player: " + player.getName());
        
        // Add player to monitoring set
        UUID playerId = player.getUniqueId();
        monitoringPlayers.add(playerId);
        
        // Initialize VAD and session for this player
        playerVoiceSessions.put(playerId, new VoiceSession());
        playerVADs.put(playerId, new VoiceActivityDetector());
        
        // Reset statistics for new monitoring session
        totalAudioFrames.set(0);
        voiceActiveFrames.set(0);
        currentVolumeLevel.set(0);
        voiceActivityDetected.set(false);
        recentVolumeHistory.clear();
        
        // Initialize and start real microphone capture
        try {
            if (initializeMicrophone()) {
                startMicrophoneCapture(player);
                player.sendMessage("§a[Audio Test] 마이크가 성공적으로 연결되었습니다!");
            } else {
                player.sendMessage("§c[Audio Test] 마이크 연결에 실패했습니다. 마이크를 확인해주세요.");
                monitoringPlayers.remove(playerId);
                return;
            }
        } catch (Exception e) {
            logger.warning("Failed to initialize microphone: " + e.getMessage());
            player.sendMessage("§c[Audio Test] 마이크 초기화 실패: " + e.getMessage());
            monitoringPlayers.remove(playerId);
            return;
        }
        
        // Start capture session for this player
        startCaptureSession(player);
        
        // Start volume monitoring task if not already running
        if (volumeMonitorTask == null || volumeMonitorTask.isDone()) {
            volumeMonitorTask = audioProcessor.submit(this::monitorAudioLevels);
        }
        
        player.sendMessage("§a[Audio Test] 마이크 모니터링이 시작되었습니다. 말씀해보세요!");
        player.sendMessage("§7[Audio Test] 실시간 볼륨 레벨과 음성 활동이 감지됩니다.");
    }
    
    /**
     * Stop audio monitoring for microphone testing
     */
    public void stopAudioMonitoring(Player player) {
        logger.info("Stopping audio monitoring for player: " + player.getName());
        
        // Remove player from monitoring set
        UUID playerId = player.getUniqueId();
        monitoringPlayers.remove(playerId);
        
        // Clean up VAD and session for this player
        playerVoiceSessions.remove(playerId);
        playerVADs.remove(playerId);
        
        // Stop real microphone capture
        stopMicrophoneCapture();
        
        stopCaptureSession(player);
        
        // If no players are being monitored, stop the volume monitor task
        if (monitoringPlayers.isEmpty() && volumeMonitorTask != null) {
            volumeMonitorTask.cancel(true);
        }
        
        player.sendMessage("§e[Audio Test] 마이크 모니터링이 중지되었습니다.");
        player.sendMessage("§7[Audio Test] 마이크 연결이 해제되었습니다.");
        
        // Show final statistics
        showAudioStatistics(player);
    }
    
    /**
     * Check if a specific player is being monitored
     */
    public boolean isPlayerBeingMonitored(Player player) {
        return monitoringPlayers.contains(player.getUniqueId());
    }
    
    /**
     * Get current audio monitoring status
     */
    public Map<String, Object> getAudioMonitoringStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("volume_level", currentVolumeLevel.get());
        status.put("voice_detected", voiceActivityDetected.get());
        status.put("total_frames", totalAudioFrames.get());
        status.put("voice_active_frames", voiceActiveFrames.get());
        status.put("voice_activity_percentage", getVoiceActivityPercentage());
        status.put("average_volume", getAverageVolume());
        status.put("active_sessions", activeSessions.size());
        return status;
    }
    
    /**
     * Calculate voice activity percentage
     */
    private double getVoiceActivityPercentage() {
        long total = totalAudioFrames.get();
        if (total == 0) return 0.0;
        return (double) voiceActiveFrames.get() / total * 100.0;
    }
    
    /**
     * Calculate average volume from recent history
     */
    private double getAverageVolume() {
        if (recentVolumeHistory.isEmpty()) return 0.0;
        
        double sum = 0.0;
        int count = 0;
        for (Double volume : recentVolumeHistory) {
            sum += volume;
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }
    
    /**
     * Monitor audio levels in real-time
     */
    private void monitorAudioLevels() {
        logger.info("Starting real-time audio level monitoring");
        
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                // Check if any players are currently being monitored
                if (monitoringPlayers.isEmpty()) {
                    Thread.sleep(500); // Wait before checking again
                    continue;
                }
                
                // Poll audio data from queue
                AudioData audioData = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                if (audioData != null) {
                    // Only analyze if the player is being monitored
                    if (monitoringPlayers.contains(audioData.getPlayerId())) {
                        analyzeAudioFrame(audioData.getData());
                    }
                }
                
                // Clean up old volume history (keep last 100 entries)
                while (recentVolumeHistory.size() > 100) {
                    recentVolumeHistory.poll();
                }
                
            } catch (InterruptedException e) {
                logger.info("Audio monitoring interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warning("Error in audio monitoring: " + e.getMessage());
            }
        }
        
        logger.info("Audio level monitoring stopped");
    }
    
    /**
     * Analyze individual audio frame for volume and voice activity
     */
    private void analyzeAudioFrame(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;
        
        // Calculate RMS (Root Mean Square) for volume level
        double rms = calculateRMS(audioData);
        
        // Convert to volume percentage (0-100)
        int volumePercent = Math.min(100, (int) (rms * 100 * 10)); // Scale factor for sensitivity
        currentVolumeLevel.set(volumePercent);
        
        // Add to history
        recentVolumeHistory.offer(rms);
        
        // Voice activity detection
        boolean isVoiceActive = rms > SILENCE_THRESHOLD;
        voiceActivityDetected.set(isVoiceActive);
        
        // Update counters
        totalAudioFrames.incrementAndGet();
        if (isVoiceActive) {
            voiceActiveFrames.incrementAndGet();
        }
        
        // Log periodic updates (every 50 frames) only when actively monitoring
        if (totalAudioFrames.get() % 50 == 0 && !monitoringPlayers.isEmpty()) {
            logger.info(String.format("Audio Stats - Volume: %d%%, Voice Active: %s, Total Frames: %d", 
                                    volumePercent, isVoiceActive ? "YES" : "NO", totalAudioFrames.get()));
        }
    }
    
    /**
     * Calculate RMS (Root Mean Square) for audio volume
     */
    private double calculateRMS(byte[] audioData) {
        long sum = 0;
        int sampleCount = audioData.length / 2; // 16-bit samples
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert bytes to 16-bit sample
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }
        
        if (sampleCount == 0) return 0.0;
        return Math.sqrt((double) sum / sampleCount) / 32768.0; // Normalize to 0-1
    }
    
    /**
     * Show audio statistics to player
     */
    private void showAudioStatistics(Player player) {
        Map<String, Object> stats = getAudioMonitoringStatus();
        
        player.sendMessage("§b=== 마이크 테스트 결과 ===");
        player.sendMessage("§7최종 볼륨 레벨: §a" + stats.get("volume_level") + "%");
        player.sendMessage("§7음성 활동 감지: " + (voiceActivityDetected.get() ? "§a✓ 감지됨" : "§c✗ 감지 안됨"));
        player.sendMessage("§7총 오디오 프레임: §e" + stats.get("total_frames"));
        player.sendMessage("§7음성 활동 비율: §e" + String.format("%.1f%%", stats.get("voice_activity_percentage")));
        player.sendMessage("§7평균 볼륨: §e" + String.format("%.2f", stats.get("average_volume")));
        
        if (totalAudioFrames.get() > 0) {
            player.sendMessage("§a✓ 마이크 입력이 정상적으로 감지되었습니다!");
        } else {
            player.sendMessage("§c✗ 마이크 입력이 감지되지 않았습니다. 마이크 설정을 확인해주세요.");
        }
    }
    
    /**
     * Initialize microphone for audio capture
     */
    private boolean initializeMicrophone() {
        try {
            // Configure audio format (16kHz, 16-bit, mono)
            audioFormat = new AudioFormat(
                AUDIO_SAMPLE_RATE,    // Sample rate
                AUDIO_BITS_PER_SAMPLE, // Sample size in bits
                AUDIO_CHANNELS,       // Channels (1 = mono)
                true,                 // Signed
                false                 // Big endian
            );
            
            // Get microphone line
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                logger.warning("Audio line not supported: " + audioFormat);
                return false;
            }
            
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat, BUFFER_SIZE);
            
            logger.info("Microphone initialized successfully");
            logger.info("Audio format: " + audioFormat);
            logger.info("Buffer size: " + BUFFER_SIZE + " bytes");
            
            return true;
            
        } catch (LineUnavailableException e) {
            logger.warning("Microphone unavailable: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warning("Failed to initialize microphone: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Start capturing audio from microphone
     */
    private void startMicrophoneCapture(Player player) {
        if (microphone == null) {
            logger.warning("Cannot start microphone capture - microphone not initialized");
            return;
        }
        
        try {
            microphone.start();
            isMicrophoneCapturing = true;
            
            // Start capture task
            microphoneCaptureTask = audioProcessor.submit(() -> captureMicrophoneData(player));
            
            logger.info("Microphone capture started for player: " + player.getName());
            
        } catch (Exception e) {
            logger.warning("Failed to start microphone capture: " + e.getMessage());
            isMicrophoneCapturing = false;
        }
    }
    
    /**
     * Stop microphone capture
     */
    private void stopMicrophoneCapture() {
        isMicrophoneCapturing = false;
        
        if (microphoneCaptureTask != null) {
            microphoneCaptureTask.cancel(true);
        }
        
        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }
        
        logger.info("Microphone capture stopped");
    }
    
    /**
     * Continuously capture audio data from microphone
     */
    private void captureMicrophoneData(Player player) {
        byte[] buffer = new byte[BUFFER_SIZE];
        UUID playerId = player.getUniqueId();
        
        logger.info("Starting real-time microphone data capture");
        
        while (isMicrophoneCapturing && microphone != null && microphone.isOpen()) {
            try {
                // Read audio data from microphone
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    // Create a copy of the actual data read
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    
                    // Process the audio data
                    processAudioData(player, audioData);
                    
                    // Add to audio queue for analysis
                    audioQueue.offer(new AudioData(playerId, audioData));
                }
                
                // Small delay to prevent excessive CPU usage
                Thread.sleep(10);
                
            } catch (InterruptedException e) {
                logger.info("Microphone capture interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warning("Error capturing microphone data: " + e.getMessage());
                break;
            }
        }
        
        logger.info("Microphone data capture ended");
    }
    
    /**
     * Get microphone status information
     */
    public Map<String, Object> getMicrophoneStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("microphone_initialized", microphone != null);
        status.put("microphone_open", microphone != null && microphone.isOpen());
        status.put("microphone_active", microphone != null && microphone.isActive());
        status.put("capturing", isMicrophoneCapturing);
        status.put("audio_format", audioFormat != null ? audioFormat.toString() : "Not set");
        status.put("buffer_size", BUFFER_SIZE);
        status.put("sample_rate", AUDIO_SAMPLE_RATE);
        status.put("channels", AUDIO_CHANNELS);
        status.put("bits_per_sample", AUDIO_BITS_PER_SAMPLE);
        
        if (microphone != null) {
            status.put("microphone_info", microphone.getLineInfo().toString());
        }
        
        return status;
    }
} 