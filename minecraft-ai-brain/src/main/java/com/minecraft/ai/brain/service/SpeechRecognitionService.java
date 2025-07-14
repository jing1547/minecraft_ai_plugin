package com.minecraft.ai.brain.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

/**
 * Speech Recognition Service using Google Cloud Speech-to-Text API
 * Optimized for Korean language and gaming terminology
 */
public class SpeechRecognitionService {
    
    private static final Logger logger = Logger.getLogger(SpeechRecognitionService.class.getName());
    
    private final SpeechClient speechClient;
    private final BlockingQueue<byte[]> audioQueue;
    private final RecognitionCache cache;
    private final RateLimiter rateLimiter;
    private final ErrorHandler errorHandler;
    private volatile boolean isProcessing = false;
    private Thread processingThread;
    
    // Korean gaming terminology for speech context
    private static final String[] KOREAN_GAMING_PHRASES = {
        "게임", "마인크래프트", "명령어", "블록", "아이템", "인벤토리",
        "채팅", "플레이어", "서버", "월드", "건축", "채굴", "제작",
        "공격", "방어", "이동", "점프", "달리기", "크리에이티브", "서바이벌"
    };
    
    public SpeechRecognitionService() throws IOException {
        this.speechClient = SpeechToTextConfig.getSpeechClient();
        this.audioQueue = new LinkedBlockingQueue<>(100); // Max 100 audio chunks in queue
        this.cache = new RecognitionCache();
        this.rateLimiter = new RateLimiter();
        this.errorHandler = new ErrorHandler();
        
        logger.info("SpeechRecognitionService initialized with Korean optimization");
    }
    
    /**
     * Queue audio data for processing
     * @param audioData Raw audio data (16kHz, 16-bit, mono)
     * @return true if successfully queued, false if queue is full
     */
    public boolean queueAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            logger.warning("Attempted to queue empty audio data");
            return false;
        }
        
        boolean queued = audioQueue.offer(audioData);
        if (!queued) {
            logger.warning("Audio queue is full, dropping audio data");
        }
        
        return queued;
    }
    
    /**
     * Recognize speech from audio data synchronously
     * @param audioData Raw audio data
     * @return Recognized text or empty string if recognition failed
     */
    public String recognizeSpeech(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            logger.warning("Received null or empty audio data for speech recognition");
            return "";
        }
        
        logger.info("Starting speech recognition for audio data: " + audioData.length + " bytes");
        
        // Check cache first
        try {
            var cachedResult = cache.getCachedResult(audioData);
            if (cachedResult.isPresent()) {
                logger.fine("Cache hit for audio recognition");
                return cachedResult.get();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking cache", e);
        }
        
        // Check rate limiting
        var rateLimitResult = rateLimiter.allowRequest();
        if (!rateLimitResult.allowed) {
            logger.warning("Rate limit exceeded, dropping recognition request: " + rateLimitResult.reason);
            return "";
        }
        
        try {
            // Configure recognition for Korean language with gaming context
            RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(SpeechToTextConfig.getSampleRate())
                .setLanguageCode(SpeechToTextConfig.getLanguageCode())
                .setModel("latest_long")
                .setEnableAutomaticPunctuation(true)
                .setUseEnhanced(true)
                .setMaxAlternatives(1);
            
            // Add Korean gaming terminology for better recognition
            SpeechContext.Builder contextBuilder = SpeechContext.newBuilder();
            for (String phrase : KOREAN_GAMING_PHRASES) {
                contextBuilder.addPhrases(phrase);
            }
            configBuilder.addSpeechContexts(contextBuilder.build());
            
            RecognitionConfig config = configBuilder.build();
            
            // Create recognition audio
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioData))
                .build();
            
            // Perform recognition
            RecognizeResponse response = speechClient.recognize(config, audio);
            
            // Process results
            StringBuilder transcript = new StringBuilder();
            for (SpeechRecognitionResult result : response.getResultsList()) {
                if (!result.getAlternativesList().isEmpty()) {
                    SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                    String text = alternative.getTranscript().trim();
                    float confidence = alternative.getConfidence();
                    
                    logger.info(String.format("Recognition result: '%s' (confidence: %.2f)", text, confidence));
                    
                    // Only include results with reasonable confidence
                    if (confidence >= 0.5f || result.getAlternativesList().size() == 1) {
                        transcript.append(text);
                        if (!text.isEmpty() && !text.endsWith(" ")) {
                            transcript.append(" ");
                        }
                    }
                }
            }
            
            String finalText = transcript.toString().trim();
            
            // Cache the result if not empty
            if (!finalText.isEmpty()) {
                cache.cacheResult(audioData, finalText);
            }
            
            // Record success in error handler
            errorHandler.recordSuccess("speech-recognition");
            
            return finalText;
            
        } catch (com.google.api.gax.rpc.ApiException e) {
            logger.log(Level.SEVERE, "Google Cloud API error during speech recognition - Code: " + 
                      e.getStatusCode() + ", Message: " + e.getMessage(), e);
            errorHandler.handleError("speech-recognition", e);
            return "";
        } catch (java.lang.IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Invalid audio data format: " + e.getMessage(), e);
            errorHandler.handleError("speech-recognition", e);
            return "";
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during speech recognition: " + e.getClass().getSimpleName() + 
                      " - " + e.getMessage(), e);
            errorHandler.handleError("speech-recognition", e);
            return "";
        }
    }
    
    /**
     * Start processing audio queue in background thread
     */
    public void startProcessingQueue() {
        if (isProcessing) {
            logger.warning("Audio processing already started");
            return;
        }
        
        isProcessing = true;
        processingThread = new Thread(this::processAudioQueue, "STT-AudioProcessor");
        processingThread.setDaemon(true);
        processingThread.start();
        
        logger.info("Started audio queue processing");
    }
    
    /**
     * Stop processing audio queue
     */
    public void stopProcessingQueue() {
        if (!isProcessing) {
            return;
        }
        
        isProcessing = false;
        if (processingThread != null) {
            processingThread.interrupt();
            try {
                processingThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Stopped audio queue processing");
    }
    
    /**
     * Process audio queue continuously
     */
    private void processAudioQueue() {
        logger.info("Audio queue processing started");
        
        while (isProcessing && !Thread.currentThread().isInterrupted()) {
            try {
                // Wait for audio data with timeout
                byte[] audioData = audioQueue.poll(1, TimeUnit.SECONDS);
                
                if (audioData != null) {
                    String recognizedText = recognizeSpeech(audioData);
                    
                    if (!recognizedText.isEmpty()) {
                        logger.info("Recognized text from queue: " + recognizedText);
                        // TODO: Send recognized text to appropriate handler
                        // This could trigger AI conversation, command processing, etc.
                        handleRecognizedText(recognizedText);
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing audio queue", e);
                // Continue processing despite errors
            }
        }
        
        logger.info("Audio queue processing stopped");
    }
    
    /**
     * Handle recognized text (placeholder for integration with other systems)
     * @param text Recognized text
     */
    private void handleRecognizedText(String text) {
        // TODO: Integrate with AI conversation system
        // TODO: Integrate with command processing system
        // TODO: Send response back through WebSocket
        
        logger.info("Processing recognized text: " + text);
        
        // For now, just log the recognized text
        // In a complete implementation, this would:
        // 1. Parse the text for commands or conversation
        // 2. Send to AI conversation service if it's natural language
        // 3. Execute commands if it matches command patterns
        // 4. Send responses back to the client
    }
    
    /**
     * Get current queue size
     * @return Number of audio chunks waiting to be processed
     */
    public int getQueueSize() {
        return audioQueue.size();
    }
    
    /**
     * Check if the service is actively processing
     * @return true if processing, false otherwise
     */
    public boolean isProcessing() {
        return isProcessing;
    }
    
    /**
     * Get recognition statistics
     * @return Statistics object with cache and rate limiting info
     */
    public RecognitionStatistics getStatistics() {
        return new RecognitionStatistics(
            cache.getStats(),
            rateLimiter.getStats(),
            audioQueue.size(),
            isProcessing
        );
    }
    
    /**
     * Shutdown the service and clean up resources
     */
    public void shutdown() {
        logger.info("Shutting down SpeechRecognitionService");
        
        stopProcessingQueue();
        
        if (speechClient != null) {
            try {
                speechClient.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing speech client", e);
            }
        }
        
        // Clear queue
        audioQueue.clear();
        
        logger.info("SpeechRecognitionService shutdown complete");
    }
    
    /**
     * Statistics container for recognition service
     */
    public static class RecognitionStatistics {
        public final Object cacheStats;
        public final Object rateLimitStats;
        public final int queueSize;
        public final boolean isProcessing;
        
        public RecognitionStatistics(Object cacheStats, Object rateLimitStats, int queueSize, boolean isProcessing) {
            this.cacheStats = cacheStats;
            this.rateLimitStats = rateLimitStats;
            this.queueSize = queueSize;
            this.isProcessing = isProcessing;
        }
    }
} 