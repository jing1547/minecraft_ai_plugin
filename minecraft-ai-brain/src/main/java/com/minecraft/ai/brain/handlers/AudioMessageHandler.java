package com.minecraft.ai.brain.handlers;

import com.minecraft.ai.brain.service.AudioProcessor;
import com.minecraft.ai.brain.service.SpeechToTextConfig;
import com.minecraft.ai.brain.service.SpeechRecognitionService;
import com.minecraft.ai.brain.websocket.WebSocketServerManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handles audio data from WebSocket connections and processes it for speech-to-text conversion
 */
public class AudioMessageHandler {
    
    private static final Logger logger = Logger.getLogger(AudioMessageHandler.class.getName());
    
    // Audio session management
    private final Map<UUID, AudioSession> audioSessions = new ConcurrentHashMap<>();
    
    // Speech recognition service
    private SpeechRecognitionService speechRecognitionService;
    
    // WebSocket server manager for sending responses
    private WebSocketServerManager webSocketManager;
    
    /**
     * Audio session data for a player
     */
    private static class AudioSession {
        private final UUID playerId;
        private final String sessionId;
        private final long startTime;
        private final BlockingQueue<byte[]> audioBuffer;
        private volatile boolean isActive;
        private volatile long lastProcessTime;
        private volatile int totalAudioSize;
        
        public AudioSession(UUID playerId) {
            this.playerId = playerId;
            this.sessionId = "session_" + System.currentTimeMillis() + "_" + playerId.toString().substring(0, 8);
            this.startTime = System.currentTimeMillis();
            this.audioBuffer = new LinkedBlockingQueue<>();
            this.isActive = false;
            this.lastProcessTime = System.currentTimeMillis();
            this.totalAudioSize = 0;
        }
        
        public void startSession() {
            this.isActive = true;
            this.lastProcessTime = System.currentTimeMillis();
        }
        
        public void endSession() {
            this.isActive = false;
            this.audioBuffer.clear();
        }
        
        public void addAudioData(byte[] data) {
            if (isActive && audioBuffer.size() < 100) { // Prevent buffer overflow
                audioBuffer.offer(data);
                totalAudioSize += data.length;
                lastProcessTime = System.currentTimeMillis();
            }
        }
        
        public byte[] getAccumulatedAudio() {
            // Combine all audio data in buffer
            int totalSize = 0;
            for (byte[] chunk : audioBuffer) {
                totalSize += chunk.length;
            }
            
            byte[] combined = new byte[totalSize];
            int offset = 0;
            while (!audioBuffer.isEmpty()) {
                byte[] chunk = audioBuffer.poll();
                if (chunk != null) {
                    System.arraycopy(chunk, 0, combined, offset, chunk.length);
                    offset += chunk.length;
                }
            }
            return combined;
        }
        
        public void clearBuffer() {
            audioBuffer.clear();
            lastProcessTime = System.currentTimeMillis();
        }
        
        public boolean hasAudioData() {
            return !audioBuffer.isEmpty();
        }
        
        public double getAverageAudioLevel() {
            // Calculate average audio level (simplified)
            return 0.5; // Placeholder
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getSessionId() { return sessionId; }
        public long getStartTime() { return startTime; }
        public boolean isActive() { return isActive; }
        public long getLastProcessTime() { return lastProcessTime; }
        public int getBufferSize() { return audioBuffer.size(); }
        public long getDuration() { return System.currentTimeMillis() - startTime; }
        public int getTotalAudioSize() { return totalAudioSize; }
    }
    
    // Initialize Speech Recognition Service
    public AudioMessageHandler(WebSocketServerManager webSocketManager) {
        this.webSocketManager = webSocketManager;
        try {
            if (SpeechToTextConfig.isEnabled()) {
                this.speechRecognitionService = new SpeechRecognitionService();
                this.speechRecognitionService.startProcessingQueue();
                logger.info("SpeechRecognitionService initialized and started");
            } else {
                logger.info("Speech-to-Text is disabled in configuration");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize SpeechRecognitionService", e);
        }
    }
    
    /**
     * Handle incoming audio message from WebSocket
     * @param playerUUID UUID of the player sending audio
     * @param messageData Raw message data from WebSocket
     */
    public void handleAudioMessage(UUID playerUUID, String messageData) {
        try {
            // Parse the audio message
            JsonObject messageJson = JsonParser.parseString(messageData).getAsJsonObject();
            
            String audioAction = messageJson.get("action").getAsString();
            JsonObject audioData = messageJson.getAsJsonObject("data");
            
            switch (audioAction) {
                case "start_recording":
                    handleStartRecording(playerUUID, audioData);
                    break;
                case "audio_chunk":
                    handleAudioChunk(playerUUID, audioData);
                    break;
                case "stop_recording":
                    handleStopRecording(playerUUID, audioData);
                    break;
                case "get_audio_status":
                    handleGetAudioStatus(playerUUID);
                    break;
                default:
                    logger.warning("Unknown audio action: " + audioAction);
                    sendErrorResponse(playerUUID, "unknown_action", "Unknown audio action: " + audioAction);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling audio message from " + playerUUID, e);
            sendErrorResponse(playerUUID, "processing_error", "Failed to process audio message: " + e.getMessage());
        }
    }
    
    /**
     * Handle start recording command
     */
    private void handleStartRecording(UUID playerUUID, JsonObject data) {
        try {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) {
                sendErrorResponse(playerUUID, "player_not_found", "Player not found");
                return;
            }
            
            // Check STT service availability before starting
            if (speechRecognitionService == null) {
                logger.warning("SpeechRecognitionService is not available - STT functionality will not work");
                player.sendMessage("§c[Audio] STT 서비스가 사용할 수 없습니다. 설정을 확인해주세요.");
                return;
            }
            
            if (!SpeechToTextConfig.isEnabled()) {
                logger.warning("Speech-to-Text is disabled in configuration");
                player.sendMessage("§c[Audio] STT가 설정에서 비활성화되어 있습니다.");
                return;
            }
            
            try {
                SpeechToTextConfig.validateConfiguration();
                logger.info("STT configuration validated successfully");
            } catch (IllegalStateException e) {
                logger.severe("STT configuration validation failed: " + e.getMessage());
                player.sendMessage("§c[Audio] STT 설정 오류: " + e.getMessage());
                return;
            }
            
            // Create or update audio session
            AudioSession session = audioSessions.computeIfAbsent(playerUUID, k -> new AudioSession(playerUUID));
            session.startSession();
            
            logger.info("Started audio recording session for player: " + player.getName());
            logger.info("STT Service Status: Available and Ready");
            
            // Send success response through chat (WebSocket method not available)
            player.sendMessage("§a[Audio] 녹음이 시작되었습니다. STT 서비스가 준비되었습니다.");
            
            // Log success response
            logger.info("Recording started response sent to " + player.getName());
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error starting recording for " + playerUUID, e);
            sendErrorResponse(playerUUID, "start_recording_error", e.getMessage());
        }
    }
    
    /**
     * Handle incoming audio chunk
     */
    private void handleAudioChunk(UUID playerUUID, JsonObject data) {
        try {
            AudioSession session = audioSessions.get(playerUUID);
            if (session == null || !session.isActive()) {
                sendErrorResponse(playerUUID, "no_active_session", "No active audio session");
                return;
            }
            
            // Get audio data from the message
            String encodedAudio = data.get("audioData").getAsString();
            byte[] audioData = Base64.getDecoder().decode(encodedAudio);
            
            // Process audio data
            byte[] processedAudio = AudioProcessor.preprocessAudio(audioData);
            
            // Add to session buffer
            session.addAudioData(processedAudio);
            
            // Check if we should process for speech-to-text
            if (shouldProcessForSpeech(session)) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null) {
                    processAudioForSpeechToText(player, session.getAccumulatedAudio());
                    session.clearBuffer(); // Clear after processing
                }
            }
            
            logger.fine("Processed audio chunk for player: " + playerUUID + ", size: " + audioData.length);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing audio chunk from " + playerUUID, e);
            sendErrorResponse(playerUUID, "audio_processing_error", e.getMessage());
        }
    }
    
    /**
     * Handle stop recording command
     */
    private void handleStopRecording(UUID playerUUID, JsonObject data) {
        try {
            AudioSession session = audioSessions.get(playerUUID);
            if (session == null) {
                sendErrorResponse(playerUUID, "no_session", "No audio session found");
                return;
            }
            
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && session.hasAudioData()) {
                // Process any remaining audio for speech-to-text
                processAudioForSpeechToText(player, session.getAccumulatedAudio());
            }
            
            session.endSession();
            logger.info("Stopped audio recording session for player: " + playerUUID);
            
            // Send success response through chat
            if (player != null) {
                player.sendMessage("§a[Audio] Recording stopped. Duration: " + (session.getDuration() / 1000) + "s");
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error stopping recording for " + playerUUID, e);
            sendErrorResponse(playerUUID, "stop_recording_error", e.getMessage());
        }
    }
    
    /**
     * Handle get audio status command
     */
    private void handleGetAudioStatus(UUID playerUUID) {
        try {
            AudioSession session = audioSessions.get(playerUUID);
            Player player = Bukkit.getPlayer(playerUUID);
            
            if (player != null) {
                if (session != null && session.isActive()) {
                    player.sendMessage("§a[Audio] Status: Recording active, Duration: " + 
                                     (session.getDuration() / 1000) + "s, Buffer: " + session.getBufferSize());
                } else {
                    player.sendMessage("§a[Audio] Status: No active recording session");
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error getting audio status for " + playerUUID, e);
            sendErrorResponse(playerUUID, "status_error", e.getMessage());
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
                logger.warning("Speech-to-Text is disabled in configuration");
                player.sendMessage("§c[STT] Speech-to-Text가 비활성화되어 있습니다.");
                return;
            }
            
            if (speechRecognitionService == null) {
                logger.severe("SpeechRecognitionService is null - STT will not work");
                player.sendMessage("§c[STT] Speech Recognition 서비스를 사용할 수 없습니다.");
                return;
            }
            
            logger.info("Processing audio for STT for player: " + player.getName() + 
                       ", audio size: " + audioData.length + " bytes");
            
            // Validate audio data
            if (audioData == null || audioData.length == 0) {
                logger.warning("Empty or null audio data received from " + player.getName());
                player.sendMessage("§c[STT] 오디오 데이터가 비어있습니다.");
                return;
            }
            
            player.sendMessage("§e[STT] 음성 인식을 시작합니다... (" + audioData.length + " bytes)");
            
            // Perform speech recognition
            String recognizedText = speechRecognitionService.recognizeSpeech(audioData);
            
            if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                logger.info("Speech recognized from " + player.getName() + ": " + recognizedText);
                player.sendMessage("§a[STT] 인식된 텍스트: " + recognizedText);
                
                // Send recognition result to AI conversation system
                processRecognizedSpeech(player, recognizedText);
                
            } else {
                logger.info("No speech recognized from audio data for player: " + player.getName());
                player.sendMessage("§6[STT] 음성을 인식하지 못했습니다. 다시 시도해보세요.");
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing audio for STT from player: " + player.getName(), e);
            player.sendMessage("§c[STT] 오류 발생: " + e.getMessage());
            sendErrorResponse(player.getUniqueId(), "stt_error", "Speech recognition failed: " + e.getMessage());
        }
    }
    
    /**
     * Process recognized speech text through AI conversation system
     * @param player Player who spoke
     * @param recognizedText The recognized speech text
     */
    private void processRecognizedSpeech(Player player, String recognizedText) {
        try {
            logger.info("Processing recognized speech from " + player.getName() + ": " + recognizedText);
            
            // Check if this is a command (starts with !)
            if (recognizedText.startsWith("!")) {
                processVoiceCommand(player, recognizedText.substring(1).trim());
                return;
            }
            
            // Send to AI conversation system for natural language processing
            Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("MinecraftAIBrain"), 
                () -> {
                    try {
                        // Create AI conversation request
                        JsonObject aiRequest = new JsonObject();
                        aiRequest.addProperty("type", "conversation");
                        aiRequest.addProperty("playerName", player.getName());
                        aiRequest.addProperty("playerUUID", player.getUniqueId().toString());
                        aiRequest.addProperty("message", recognizedText);
                        aiRequest.addProperty("timestamp", System.currentTimeMillis());
                        aiRequest.addProperty("isVoiceInput", true);
                        
                        // Send to AI conversation service (this could be extended to call external AI API)
                        processAIConversation(player, aiRequest);
                        
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error in AI conversation processing", e);
                    }
                }
            );
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing recognized speech", e);
        }
    }
    
    /**
     * Process voice commands (starting with !)
     * @param player Player who issued the command
     * @param command The command text
     */
    private void processVoiceCommand(Player player, String command) {
        try {
            logger.info("Processing voice command from " + player.getName() + ": " + command);
            
            // Parse command and execute
            String[] parts = command.split(" ");
            String commandName = parts[0].toLowerCase();
            
            switch (commandName) {
                case "help":
                    player.sendMessage("§a[Voice AI] Available voice commands: !help, !status, !time, !weather");
                    break;
                case "status":
                    player.sendMessage("§a[Voice AI] Bot Status: Active, Health: " + player.getHealth() + "/20");
                    break;
                case "time":
                    long time = player.getWorld().getTime();
                    String timeOfDay = time < 6000 ? "Morning" : time < 12000 ? "Day" : time < 18000 ? "Evening" : "Night";
                    player.sendMessage("§a[Voice AI] Current time: " + timeOfDay + " (" + time + ")");
                    break;
                case "weather":
                    String weather = player.getWorld().hasStorm() ? "Stormy" : "Clear";
                    player.sendMessage("§a[Voice AI] Current weather: " + weather);
                    break;
                default:
                    player.sendMessage("§c[Voice AI] Unknown command: " + commandName + ". Say '!help' for available commands.");
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing voice command", e);
            player.sendMessage("§c[Voice AI] Error processing voice command: " + e.getMessage());
        }
    }
    
    /**
     * Process AI conversation request
     * @param player Player requesting conversation
     * @param request AI conversation request data
     */
    private void processAIConversation(Player player, JsonObject request) {
        try {
            // For now, provide a simple AI-like response
            // This could be extended to call OpenAI API, Claude, or other AI services
            
            String userMessage = request.get("message").getAsString().toLowerCase();
            String aiResponse;
            
            if (userMessage.contains("hello") || userMessage.contains("hi")) {
                aiResponse = "Hello " + player.getName() + "! How can I help you today?";
            } else if (userMessage.contains("weather")) {
                String weather = player.getWorld().hasStorm() ? "stormy" : "clear";
                aiResponse = "The weather is currently " + weather + " in your world.";
            } else if (userMessage.contains("time")) {
                long time = player.getWorld().getTime();
                String timeOfDay = time < 6000 ? "morning" : time < 12000 ? "day" : time < 18000 ? "evening" : "night";
                aiResponse = "It's currently " + timeOfDay + " in your world.";
            } else if (userMessage.contains("help")) {
                aiResponse = "I can help you with information about the game, weather, time, and answer questions. Just speak naturally!";
            } else {
                aiResponse = "I heard you say: \"" + request.get("message").getAsString() + "\". I'm a simple AI assistant. Try asking about weather, time, or say hello!";
            }
            
            // Send AI response back to player
            player.sendMessage("§b[AI Assistant] " + aiResponse);
            
            logger.info("AI response sent to " + player.getName() + ": " + aiResponse);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in AI conversation", e);
            player.sendMessage("§c[AI Assistant] Sorry, I encountered an error while processing your message.");
        }
    }
    
    /**
     * Check if audio session should be processed for speech recognition
     */
    private boolean shouldProcessForSpeech(AudioSession session) {
        // Process more aggressively for better responsiveness
        int bufferSize = session.getBufferSize();
        long timeSinceLastProcess = System.currentTimeMillis() - session.getLastProcessTime();
        
        // Process if we have any chunks and enough time has passed (reduced to 1 second)
        boolean timeThreshold = timeSinceLastProcess >= 1000; // 1 second instead of 3
        
        // Process if we have at least 2 chunks (reduced from 5)
        boolean bufferThreshold = bufferSize >= 2;
        
        // Log the decision for debugging
        if (timeThreshold || bufferThreshold) {
            logger.info("Processing audio: buffer=" + bufferSize + ", time=" + timeSinceLastProcess + "ms");
        }
        
        return timeThreshold || bufferThreshold;
    }
    
    /**
     * Send error response through chat message
     */
    private void sendErrorResponse(UUID playerUUID, String errorCode, String errorMessage) {
        try {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.sendMessage("§c[Audio Error] " + errorCode + ": " + errorMessage);
            }
            logger.warning("Audio error for " + playerUUID + " - " + errorCode + ": " + errorMessage);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending error response", e);
        }
    }
    
    /**
     * Get configured buffer threshold for processing
     */
    private int getConfiguredBufferThreshold() {
        // Default to 5 chunks, could be made configurable
        return 5;
    }
    
    /**
     * Get configured processing interval in milliseconds
     */
    private long getConfiguredProcessInterval() {
        // Default to 3 seconds, could be made configurable
        return 3000;
    }
    
    /**
     * Get audio statistics for monitoring
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
        
        // Add STT service statistics if available
        if (speechRecognitionService != null) {
            var sttStats = speechRecognitionService.getStatistics();
            stats.addProperty("stt_queue_size", sttStats.queueSize);
            stats.addProperty("stt_is_processing", sttStats.isProcessing);
        }
        
        return stats;
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        logger.info("Shutting down AudioMessageHandler");
        
        // Stop all active sessions
        audioSessions.values().forEach(AudioSession::endSession);
        audioSessions.clear();
        
        // Shutdown speech recognition service
        if (speechRecognitionService != null) {
            speechRecognitionService.shutdown();
        }
        
        logger.info("AudioMessageHandler shutdown complete");
    }
} 