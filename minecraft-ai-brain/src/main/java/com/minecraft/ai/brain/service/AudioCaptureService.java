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
    private final Map<UUID, ByteArrayOutputStream> playerAudioBuffers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSTTProcessTime = new ConcurrentHashMap<>();
    private static final long STT_PROCESS_INTERVAL = 2000; // Process STT every 2 seconds
    
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
                logger.info("SpeechRecognitionService initialized successfully");
            } catch (Exception e) {
                logger.severe("Failed to initialize SpeechRecognitionService: " + e.getMessage());
                speechRecognitionService = null;
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
     * Process audio data for Speech-to-Text
     * @param audioData Audio data to process
     */
    private void processAudioForSTT(AudioData audioData) {
        if (speechRecognitionService == null || !SpeechToTextConfig.isEnabled()) {
            return;
        }
        
        UUID playerId = audioData.getPlayerId();
        Player player = Bukkit.getPlayer(playerId);
        
        if (player == null) {
            return;
        }
        
        try {
            // Get or create audio buffer for this player
            ByteArrayOutputStream audioBuffer = playerAudioBuffers.computeIfAbsent(playerId, k -> new ByteArrayOutputStream());
            
            // Add new audio data to buffer
            audioBuffer.write(audioData.getData());
            
            // Check if enough time has passed since last STT processing
            long currentTime = System.currentTimeMillis();
            long lastProcessTime = lastSTTProcessTime.getOrDefault(playerId, 0L);
            
            if (currentTime - lastProcessTime >= STT_PROCESS_INTERVAL && audioBuffer.size() > 0) {
                // Process accumulated audio for STT
                byte[] accumulatedAudio = audioBuffer.toByteArray();
                
                logger.info("Processing " + accumulatedAudio.length + " bytes of audio for STT for player: " + player.getName());
                
                // Send to player immediately
                player.sendMessage("§e[STT] 음성 인식 처리 중... (" + accumulatedAudio.length + " bytes)");
                
                // Process STT asynchronously to avoid blocking audio queue
                Bukkit.getScheduler().runTaskAsynchronously(
                    Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                    () -> {
                        try {
                            String recognizedText = speechRecognitionService.recognizeSpeech(accumulatedAudio);
                            
                            // Send result back on main thread
                            Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                                () -> {
                                    if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                                        logger.info("STT Result for " + player.getName() + ": " + recognizedText);
                                        player.sendMessage("§a[STT] 인식된 텍스트: " + recognizedText);
                                        
                                        // Process recognized speech further if needed
                                        processRecognizedSpeech(player, recognizedText);
                                    } else {
                                        logger.info("No speech recognized for player: " + player.getName());
                                        player.sendMessage("§6[STT] 음성을 인식하지 못했습니다. 다시 시도해보세요.");
                                    }
                                }
                            );
                        } catch (Exception e) {
                            logger.severe("STT processing error for " + player.getName() + ": " + e.getMessage());
                            Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"),
                                () -> player.sendMessage("§c[STT] 음성 인식 오류: " + e.getMessage())
                            );
                        }
                    }
                );
                
                // Clear buffer and update timestamp
                audioBuffer.reset();
                lastSTTProcessTime.put(playerId, currentTime);
            }
            
        } catch (Exception e) {
            logger.severe("Error processing audio for STT: " + e.getMessage());
        }
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
        
        // Initialize STT buffers for this player
        playerAudioBuffers.put(playerId, new ByteArrayOutputStream());
        lastSTTProcessTime.put(playerId, System.currentTimeMillis());
        
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
        
        // Clean up STT buffers for this player
        playerAudioBuffers.remove(playerId);
        lastSTTProcessTime.remove(playerId);
        
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