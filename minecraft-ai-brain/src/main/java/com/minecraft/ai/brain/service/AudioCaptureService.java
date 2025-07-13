package com.minecraft.ai.brain.service;

import org.bukkit.entity.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service for handling audio capture and processing from players
 * Manages audio streams and prepares them for Speech-to-Text processing
 */
public class AudioCaptureService implements Service {
    
    private static final String SERVICE_ID = "audio_capture";
    private static final int AUDIO_SAMPLE_RATE = 16000; // 16kHz
    private static final int AUDIO_BITS_PER_SAMPLE = 16;
    private static final int AUDIO_CHANNELS = 1; // Mono
    private static final int BUFFER_SIZE = 4096;
    
    private final Logger logger = Logger.getLogger(AudioCaptureService.class.getName());
    
    // Audio processing
    private final Map<UUID, AudioSession> activeSessions = new ConcurrentHashMap<>();
    private final BlockingQueue<AudioData> audioQueue = new LinkedBlockingQueue<>();
    private final ExecutorService audioProcessor = Executors.newFixedThreadPool(3);
    
    // Service state
    private boolean isRunning = false;
    private Future<?> queueProcessorTask;
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
        
        // Check if STT is enabled
        if (!SpeechToTextConfig.isEnabled()) {
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
        if (isRunning) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, "Audio Capture Service is already running");
        }
        
        logger.info("Starting Audio Capture Service...");
        
        isRunning = true;
        
        // Start audio queue processor
        queueProcessorTask = audioProcessor.submit(this::processAudioQueue);
        
        logger.info("Audio Capture Service started successfully");
    }
    
    @Override
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        logger.info("Stopping Audio Capture Service...");
        
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
                
                // Process audio data further if needed
                // This is where you would send to STT service
                logger.fine("Processing audio data for player: " + session.getPlayerName() + 
                           " (size: " + audioData.getData().length + " bytes)");
                
                // TODO: Integrate with SpeechToTextService when implemented
                
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
} 