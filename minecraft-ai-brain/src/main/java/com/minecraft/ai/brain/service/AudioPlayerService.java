package com.minecraft.ai.brain.service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;

/**
 * AudioPlayerService handles audio playback with spatial positioning in the game environment.
 * This service manages an audio queue and provides spatial audio capabilities for TTS output.
 */
public class AudioPlayerService implements Service {
    
    private static final String SERVICE_ID = "audio_player";
    private static final String SERVICE_NAME = "Audio Player Service";
    
    private State state = State.NOT_INITIALIZED;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicLong startTime = new AtomicLong(-1);
    private final Queue<AudioEntry> audioQueue = new ConcurrentLinkedQueue<>();
    
    // Configuration
    private boolean enabled = true;
    private double maxAudioDistance = 32.0; // Maximum distance for audio to be heard
    private double volumeFalloffRate = 1.0; // How quickly volume decreases with distance
    private int maxQueueSize = 50; // Maximum number of queued audio entries
    
    // Metrics
    private final AtomicLong totalAudioPlayed = new AtomicLong(0);
    private final AtomicLong totalQueuedAudio = new AtomicLong(0);
    private final AtomicLong audioDropped = new AtomicLong(0);
    
    // Dependencies
    private ServiceManager serviceManager;
    
    /**
     * Position class for 3D coordinates in the game world
     */
    public static class Position {
        private final double x, y, z;
        
        public Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        
        public double distanceTo(Position other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        
        @Override
        public String toString() {
            return String.format("Position(%.2f, %.2f, %.2f)", x, y, z);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Position)) return false;
            Position other = (Position) obj;
            return Double.compare(x, other.x) == 0 &&
                   Double.compare(y, other.y) == 0 &&
                   Double.compare(z, other.z) == 0;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
    
    /**
     * AudioEntry represents a queued audio item with spatial information
     */
    public static class AudioEntry {
        private final byte[] audioData;
        private final Position position;
        private final UUID entityId;
        private final String emotion;
        private final long timestamp;
        private final double priority;
        
        public AudioEntry(byte[] audioData, Position position, UUID entityId) {
            this(audioData, position, entityId, "neutral", 1.0);
        }
        
        public AudioEntry(byte[] audioData, Position position, UUID entityId, String emotion, double priority) {
            this.audioData = audioData.clone();
            this.position = position;
            this.entityId = entityId;
            this.emotion = emotion;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }
        
        public byte[] getAudioData() { return audioData.clone(); }
        public Position getPosition() { return position; }
        public UUID getEntityId() { return entityId; }
        public String getEmotion() { return emotion; }
        public double getPriority() { return priority; }
        public long getTimestamp() { return timestamp; }
        
        public long getAge() {
            return System.currentTimeMillis() - timestamp;
        }
    }
    
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
        return state;
    }
    
    @Override
    public Priority getPriority() {
        return Priority.NORMAL;
    }
    
    @Override
    public List<String> getDependencies() {
        return Arrays.asList("service_manager");
    }
    
    @Override
    public void initialize(Map<String, Service> dependencies) throws ServiceException {
        if (state != State.NOT_INITIALIZED) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                                     "AudioPlayerService already initialized");
        }
        
        state = State.INITIALIZED;
        
        // Get dependencies
        Service serviceManagerService = dependencies.get("service_manager");
        if (serviceManagerService instanceof ServiceManager) {
            this.serviceManager = (ServiceManager) serviceManagerService;
        }
        
        System.out.println("[AudioPlayerService] Initialized with queue size limit: " + maxQueueSize);
    }
    
    @Override
    public void start() throws ServiceException {
        if (state != State.INITIALIZED) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                                     "AudioPlayerService not properly initialized");
        }
        
        state = State.STARTING;
        startTime.set(System.currentTimeMillis());
        
        try {
            // Initialize audio system components
            initializeAudioSystem();
            
            state = State.RUNNING;
            System.out.println("[AudioPlayerService] Started successfully");
            
        } catch (Exception e) {
            state = State.FAILED;
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                                     "Failed to start AudioPlayerService", e);
        }
    }
    
    @Override
    public void stop() throws ServiceException {
        if (state != State.RUNNING) {
            return;
        }
        
        state = State.STOPPING;
        
        try {
            // Stop current playback
            isPlaying.set(false);
            
            // Clear audio queue
            audioQueue.clear();
            
            state = State.STOPPED;
            System.out.println("[AudioPlayerService] Stopped successfully");
            
        } catch (Exception e) {
            state = State.FAILED;
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SHUTDOWN_FAILED, 
                                     "Failed to stop AudioPlayerService", e);
        }
    }
    
    @Override
    public ServiceHealth getHealth() {
        switch (state) {
            case RUNNING:
                int queueSize = audioQueue.size();
                if (queueSize > maxQueueSize * 0.9) {
                    return ServiceHealth.degraded("Audio queue nearly full: " + queueSize + "/" + maxQueueSize);
                }
                return ServiceHealth.healthy();
            case FAILED:
                return ServiceHealth.unhealthy("Service failed");
            default:
                return ServiceHealth.degraded("Service not running: " + state);
        }
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("state", state.toString());
        metrics.put("is_playing", isPlaying.get());
        metrics.put("queue_size", audioQueue.size());
        metrics.put("max_queue_size", maxQueueSize);
        metrics.put("total_audio_played", totalAudioPlayed.get());
        metrics.put("total_queued_audio", totalQueuedAudio.get());
        metrics.put("audio_dropped", audioDropped.get());
        metrics.put("max_audio_distance", maxAudioDistance);
        metrics.put("volume_falloff_rate", volumeFalloffRate);
        metrics.put("uptime_ms", getUptime());
        return metrics;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", enabled);
        config.put("max_audio_distance", maxAudioDistance);
        config.put("volume_falloff_rate", volumeFalloffRate);
        config.put("max_queue_size", maxQueueSize);
        config.put("service_id", SERVICE_ID);
        config.put("service_name", SERVICE_NAME);
        config.put("priority", getPriority().toString());
        return config;
    }
    
    @Override
    public void onConfigurationChange(Map<String, Object> newConfig) {
        if (newConfig.containsKey("enabled")) {
            this.enabled = (Boolean) newConfig.get("enabled");
        }
        if (newConfig.containsKey("max_audio_distance")) {
            this.maxAudioDistance = ((Number) newConfig.get("max_audio_distance")).doubleValue();
        }
        if (newConfig.containsKey("volume_falloff_rate")) {
            this.volumeFalloffRate = ((Number) newConfig.get("volume_falloff_rate")).doubleValue();
        }
        if (newConfig.containsKey("max_queue_size")) {
            this.maxQueueSize = ((Number) newConfig.get("max_queue_size")).intValue();
        }
        
        System.out.println("[AudioPlayerService] Configuration updated");
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public long getStartTime() {
        return startTime.get();
    }
    
    @Override
    public long getUptime() {
        long start = startTime.get();
        if (start == -1 || state != State.RUNNING) {
            return 0;
        }
        return System.currentTimeMillis() - start;
    }
    
    // =========================
    // Audio Player Specific Methods
    // =========================
    
    /**
     * Queue audio for playback with spatial positioning
     */
    public boolean queueAudio(byte[] audioData, Position position, UUID entityId) {
        return queueAudio(audioData, position, entityId, "neutral", 1.0);
    }
    
    /**
     * Queue audio for playback with spatial positioning and emotion
     */
    public boolean queueAudio(byte[] audioData, Position position, UUID entityId, String emotion, double priority) {
        if (!enabled || state != State.RUNNING) {
            System.out.println("[AudioPlayerService] Cannot queue audio - service not running");
            return false;
        }
        
        if (audioData == null || audioData.length == 0) {
            System.out.println("[AudioPlayerService] Cannot queue empty audio data");
            return false;
        }
        
        // Check queue size limit
        if (audioQueue.size() >= maxQueueSize) {
            audioDropped.incrementAndGet();
            System.out.println("[AudioPlayerService] Audio queue full, dropping audio entry");
            return false;
        }
        
        AudioEntry entry = new AudioEntry(audioData, position, entityId, emotion, priority);
        audioQueue.add(entry);
        totalQueuedAudio.incrementAndGet();
        
        // Start playing if not already playing
        if (!isPlaying.get()) {
            CompletableFuture.runAsync(this::playNextInQueue);
        }
        
        return true;
    }
    
    /**
     * Play the next audio in the queue
     */
    private void playNextInQueue() {
        if (!isPlaying.compareAndSet(false, true)) {
            return; // Already playing
        }
        
        try {
            while (!audioQueue.isEmpty() && enabled && state == State.RUNNING) {
                AudioEntry entry = audioQueue.poll();
                if (entry != null) {
                    playAudioEntry(entry);
                    totalAudioPlayed.incrementAndGet();
                }
            }
        } finally {
            isPlaying.set(false);
        }
    }
    
    /**
     * Play a single audio entry with spatial positioning
     */
    private void playAudioEntry(AudioEntry entry) {
        try {
            System.out.println("[AudioPlayerService] Playing audio at position: " + entry.getPosition() + 
                             " for entity: " + entry.getEntityId() + 
                             " with emotion: " + entry.getEmotion());
            
            // Convert audio data to sound event
            SoundEventWrapper sound = convertToSoundEvent(entry.getAudioData(), entry.getEmotion());
            
            // Calculate spatial audio parameters
            SpatialAudioParams spatialParams = calculateSpatialAudio(entry.getPosition(), entry.getEntityId());
            
            // Play the audio with spatial positioning
            playSpatialAudio(sound, spatialParams, entry);
            
            // Simulate audio playback duration (this would be real audio duration in actual implementation)
            Thread.sleep(Math.min(entry.getAudioData().length / 100, 5000)); // Max 5 seconds
            
        } catch (Exception e) {
            System.err.println("[AudioPlayerService] Error playing audio entry: " + e.getMessage());
        }
    }
    
    /**
     * Convert audio data to a sound event wrapper
     */
    private SoundEventWrapper convertToSoundEvent(byte[] audioData, String emotion) {
        // In a real implementation, this would:
        // 1. Save audio data to a temporary file
        // 2. Register it as a dynamic sound resource in Minecraft
        // 3. Return a proper SoundEvent
        
        System.out.println("[AudioPlayerService] Converting " + audioData.length + 
                         " bytes of audio data to sound event (emotion: " + emotion + ")");
        
        // For now, return a wrapper with metadata
        return new SoundEventWrapper(audioData, emotion);
    }
    
    /**
     * Calculate spatial audio parameters based on position and entity
     */
    private SpatialAudioParams calculateSpatialAudio(Position position, UUID entityId) {
        // In a real implementation, this would:
        // 1. Get player positions from Minecraft API
        // 2. Calculate distance and direction
        // 3. Apply volume and stereo positioning
        
        double volume = 1.0; // Default volume
        double pitch = 1.0;  // Default pitch
        double pan = 0.0;    // Center stereo position
        
        // Placeholder calculations
        System.out.println("[AudioPlayerService] Calculating spatial audio for position: " + position);
        
        return new SpatialAudioParams(volume, pitch, pan, position);
    }
    
    /**
     * Play audio with spatial positioning
     */
    private void playSpatialAudio(SoundEventWrapper sound, SpatialAudioParams spatialParams, AudioEntry entry) {
        // In a real implementation, this would use Minecraft's sound system:
        // world.playSound(null, position.x, position.y, position.z, soundEvent, SoundCategory, volume, pitch);
        
        System.out.println("[AudioPlayerService] Playing spatial audio - " +
                         "Volume: " + spatialParams.getVolume() + 
                         ", Pitch: " + spatialParams.getPitch() + 
                         ", Pan: " + spatialParams.getPan() +
                         ", Position: " + spatialParams.getPosition());
    }
    
    /**
     * Initialize the audio system
     */
    private void initializeAudioSystem() {
        System.out.println("[AudioPlayerService] Initializing audio system...");
        // In a real implementation, this would:
        // 1. Initialize Minecraft audio components
        // 2. Register dynamic sound events
        // 3. Set up audio resources
        System.out.println("[AudioPlayerService] Audio system initialized");
    }
    
    /**
     * Get current queue size
     */
    public int getQueueSize() {
        return audioQueue.size();
    }
    
    /**
     * Clear the audio queue
     */
    public void clearQueue() {
        audioQueue.clear();
        System.out.println("[AudioPlayerService] Audio queue cleared");
    }
    
    /**
     * Check if audio is currently playing
     */
    public boolean isCurrentlyPlaying() {
        return isPlaying.get();
    }
    
    // =========================
    // Helper Classes
    // =========================
    
    /**
     * Wrapper for sound events with metadata
     */
    private static class SoundEventWrapper {
        private final byte[] audioData;
        private final String emotion;
        private final long createdAt;
        
        public SoundEventWrapper(byte[] audioData, String emotion) {
            this.audioData = audioData;
            this.emotion = emotion;
            this.createdAt = System.currentTimeMillis();
        }
        
        public byte[] getAudioData() { return audioData; }
        public String getEmotion() { return emotion; }
        public long getCreatedAt() { return createdAt; }
    }
    
    /**
     * Spatial audio parameters for 3D positioning
     */
    private static class SpatialAudioParams {
        private final double volume;
        private final double pitch;
        private final double pan;
        private final Position position;
        
        public SpatialAudioParams(double volume, double pitch, double pan, Position position) {
            this.volume = volume;
            this.pitch = pitch;
            this.pan = pan;
            this.position = position;
        }
        
        public double getVolume() { return volume; }
        public double getPitch() { return pitch; }
        public double getPan() { return pan; }
        public Position getPosition() { return position; }
    }
} 