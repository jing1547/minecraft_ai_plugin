package com.minecraft.ai.brain.handlers;

import com.minecraft.ai.brain.service.AudioProcessor;
import com.minecraft.ai.brain.service.SpeechToTextConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles audio-related WebSocket messages from clients
 * Processes audio data for speech-to-text conversion
 */
public class AudioMessageHandler {
    
    private static final Logger logger = Logger.getLogger(AudioMessageHandler.class.getName());
    
    // Audio session management
    private final Map<UUID, AudioSession> audioSessions = new ConcurrentHashMap<>();
    
    /**
     * Audio session data for a player
     */
    private static class AudioSession {
        private final UUID playerId;
        private final long startTime;
        private volatile boolean isActive;
        private volatile boolean isPushToTalkActive;
        private int audioPacketCount;
        private double totalAudioLevel;
        
        public AudioSession(UUID playerId) {
            this.playerId = playerId;
            this.startTime = System.currentTimeMillis();
            this.isActive = true;
            this.isPushToTalkActive = false;
            this.audioPacketCount = 0;
            this.totalAudioLevel = 0.0;
        }
        
        public void updateAudioLevel(double level) {
            audioPacketCount++;
            totalAudioLevel += level;
        }
        
        public double getAverageAudioLevel() {
            return audioPacketCount > 0 ? totalAudioLevel / audioPacketCount : 0.0;
        }
        
        // Getters and setters
        public UUID getPlayerId() { return playerId; }
        public long getStartTime() { return startTime; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { this.isActive = active; }
        public boolean isPushToTalkActive() { return isPushToTalkActive; }
        public void setPushToTalkActive(boolean active) { this.isPushToTalkActive = active; }
        public int getAudioPacketCount() { return audioPacketCount; }
    }
    
    /**
     * Handle audio start message from client
     * @param playerId Player UUID
     * @param message Audio start message
     */
    public void handleAudioStart(UUID playerId, String message) {
        try {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                logger.warning("Received audio start for unknown player: " + playerId);
                return;
            }
            
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            
            // Create audio session
            AudioSession session = new AudioSession(playerId);
            audioSessions.put(playerId, session);
            
            logger.info("Started audio session for player: " + player.getName());
            
            // Send acknowledgment back to client
            JsonObject response = new JsonObject();
            response.addProperty("type", "audio_start_ack");
            response.addProperty("status", "success");
            response.addProperty("audio_format", AudioProcessor.getAudioFormatInfo());
            
            // TODO: Send response through WebSocket
            
        } catch (Exception e) {
            logger.severe("Error handling audio start: " + e.getMessage());
        }
    }
    
    /**
     * Handle audio data message from client
     * @param playerId Player UUID
     * @param message Audio data message
     */
    public void handleAudioData(UUID playerId, String message) {
        try {
            AudioSession session = audioSessions.get(playerId);
            if (session == null || !session.isActive()) {
                logger.warning("Received audio data for inactive session: " + playerId);
                return;
            }
            
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                logger.warning("Received audio data for unknown player: " + playerId);
                return;
            }
            
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            
            // Check if push-to-talk is active (if enabled)
            if (isPushToTalkEnabled() && !session.isPushToTalkActive()) {
                return; // Ignore audio data if push-to-talk is not active
            }
            
            // Extract audio data
            String audioDataBase64 = json.get("data").getAsString();
            byte[] audioData = Base64.getDecoder().decode(audioDataBase64);
            
            // Validate audio data
            if (!AudioProcessor.isValidAudioData(audioData)) {
                logger.warning("Invalid audio data received from player: " + player.getName());
                return;
            }
            
            // Calculate audio level for monitoring
            double audioLevel = AudioProcessor.calculateAudioLevel(audioData);
            session.updateAudioLevel(audioLevel);
            
            // Check for voice activity
            if (AudioProcessor.detectVoiceActivity(audioData)) {
                // Process audio data
                byte[] processedAudio = AudioProcessor.preprocessAudio(audioData);
                
                // TODO: Send to Speech-to-Text service
                processAudioForSpeechToText(player, processedAudio);
                
                logger.info("Processed audio data for player: " + player.getName() + 
                           " (level: " + String.format("%.2f", audioLevel) + ")");
            }
            
        } catch (Exception e) {
            logger.severe("Error handling audio data: " + e.getMessage());
        }
    }
    
    /**
     * Handle audio stop message from client
     * @param playerId Player UUID
     * @param message Audio stop message
     */
    public void handleAudioStop(UUID playerId, String message) {
        try {
            AudioSession session = audioSessions.remove(playerId);
            if (session == null) {
                logger.warning("Received audio stop for unknown session: " + playerId);
                return;
            }
            
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                logger.info("Stopped audio session for player: " + player.getName() + 
                           " (packets: " + session.getAudioPacketCount() + 
                           ", avg level: " + String.format("%.2f", session.getAverageAudioLevel()) + ")");
            }
            
            session.setActive(false);
            
            // Send acknowledgment back to client
            JsonObject response = new JsonObject();
            response.addProperty("type", "audio_stop_ack");
            response.addProperty("status", "success");
            response.addProperty("session_duration", System.currentTimeMillis() - session.getStartTime());
            
            // TODO: Send response through WebSocket
            
        } catch (Exception e) {
            logger.severe("Error handling audio stop: " + e.getMessage());
        }
    }
    
    /**
     * Handle push-to-talk state change
     * @param playerId Player UUID
     * @param message Push-to-talk message
     */
    public void handlePushToTalk(UUID playerId, String message) {
        try {
            AudioSession session = audioSessions.get(playerId);
            if (session == null) {
                logger.warning("Received push-to-talk for unknown session: " + playerId);
                return;
            }
            
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            boolean active = json.get("active").getAsBoolean();
            
            session.setPushToTalkActive(active);
            
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                logger.info("Push-to-talk " + (active ? "activated" : "deactivated") + 
                           " for player: " + player.getName());
            }
            
        } catch (Exception e) {
            logger.severe("Error handling push-to-talk: " + e.getMessage());
        }
    }
    
    /**
     * Process audio data for speech-to-text conversion
     * @param player Player who sent the audio
     * @param audioData Processed audio data
     */
    private void processAudioForSpeechToText(Player player, byte[] audioData) {
        try {
            // Check if Speech-to-Text is enabled
            if (!SpeechToTextConfig.isEnabled()) {
                return;
            }
            
            // TODO: Implement actual Speech-to-Text processing
            // This would involve:
            // 1. Accumulating audio data until speech is complete
            // 2. Sending to Google Cloud Speech-to-Text API
            // 3. Processing the response
            // 4. Triggering appropriate actions based on the recognized text
            
            logger.info("Processing audio for STT for player: " + player.getName() + 
                       " (data size: " + audioData.length + " bytes)");
            
        } catch (Exception e) {
            logger.severe("Error processing audio for STT: " + e.getMessage());
        }
    }
    
    /**
     * Get active audio sessions
     * @return Map of active sessions
     */
    public Map<UUID, String> getActiveSessions() {
        Map<UUID, String> sessions = new ConcurrentHashMap<>();
        audioSessions.forEach((playerId, session) -> {
            if (session.isActive()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    sessions.put(playerId, player.getName());
                }
            }
        });
        return sessions;
    }
    
    /**
     * Stop all audio sessions
     */
    public void stopAllSessions() {
        audioSessions.values().forEach(session -> session.setActive(false));
        audioSessions.clear();
        logger.info("Stopped all audio sessions");
    }
    
    /**
     * Check if push-to-talk is enabled
     * @return true if enabled
     */
    private boolean isPushToTalkEnabled() {
        // TODO: Read from configuration
        return false; // Default to false for now
    }
    
    /**
     * Get audio session statistics
     * @return Statistics as JSON object
     */
    public JsonObject getAudioStatistics() {
        JsonObject stats = new JsonObject();
        stats.addProperty("active_sessions", audioSessions.size());
        stats.addProperty("total_sessions", audioSessions.size());
        
        // Calculate average audio level across all sessions
        double totalLevel = 0.0;
        int sessionCount = 0;
        for (AudioSession session : audioSessions.values()) {
            if (session.isActive()) {
                totalLevel += session.getAverageAudioLevel();
                sessionCount++;
            }
        }
        
        stats.addProperty("average_audio_level", sessionCount > 0 ? totalLevel / sessionCount : 0.0);
        
        return stats;
    }
} 