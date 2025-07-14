package com.minecraft.ai.brain.service;

import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TextToSpeechService provides text-to-speech conversion with emotion mapping capabilities.
 * This is the core implementation that will use Google Cloud Text-to-Speech API.
 */
public class TextToSpeechService implements Service {
    private static final String LOG_PREFIX = "[TextToSpeechService] ";
    private static final String SERVICE_ID = "text-to-speech";
    private static final String SERVICE_NAME = "Text-to-Speech Service";
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile State currentState = State.NOT_INITIALIZED;
    private ServiceHealth currentHealth;
    private long startTime = -1;
    
    // Emotion mapping configuration
    private final Map<String, String> emotionVoiceMapping = new HashMap<>();
    private final Map<String, Double> emotionPitchMapping = new HashMap<>();
    private final Map<String, Double> emotionRateMapping = new HashMap<>();
    
    // Advanced cache and rate limiting
    private TTSCacheManager cacheManager;
    private APIRateLimiter rateLimiter;
    
    // Configuration cache
    private String defaultLanguageCode = "ko-KR";
    private String defaultVoiceName = "ko-KR-Neural2-C";
    private String audioEncoding = "MP3";
    private double sampleRateHertz = 22050.0;
    
    public TextToSpeechService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = new Logger(plugin);
        this.currentHealth = ServiceHealth.unknown("Service not initialized");
        initializeEmotionMappings();
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
    public ServiceHealth getHealth() {
        return currentHealth;
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Cache metrics
        if (cacheManager != null) {
            Map<String, Object> cacheStats = cacheManager.getCacheStatistics();
            metrics.putAll(cacheStats);
        } else {
            metrics.put("cache_size", 0);
            metrics.put("max_cache_size", 0);
        }
        
        // Rate limiter metrics
        if (rateLimiter != null) {
            Map<String, Object> rateLimiterStats = rateLimiter.getStatistics();
            rateLimiterStats.forEach((key, value) -> metrics.put("rate_limiter_" + key, value));
        }
        
        metrics.put("emotion_mappings_count", emotionVoiceMapping.size());
        metrics.put("state", currentState.name());
        metrics.put("uptime_ms", getUptime());
        metrics.put("enabled", isEnabled());
        return metrics;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("language_code", defaultLanguageCode);
        config.put("voice_name", defaultVoiceName);
        config.put("audio_encoding", audioEncoding);
        config.put("sample_rate", sampleRateHertz);
        config.put("enabled", isEnabled());
        config.put("available_emotions", getAvailableEmotions());
        return config;
    }
    
    @Override
    public void onConfigurationChange(Map<String, Object> newConfig) {
        logger.info(LOG_PREFIX + "Configuration change received");
        // Handle configuration changes if needed
        // TODO: Implement configuration update logic
    }
    
    @Override
    public boolean isEnabled() {
        return TTSConfig.isEnabled();
    }
    
    @Override
    public long getStartTime() {
        return startTime;
    }
    
    @Override
    public long getUptime() {
        return startTime > 0 ? System.currentTimeMillis() - startTime : 0;
    }
    
    @Override
    public void initialize(Map<String, Service> dependencies) throws ServiceException {
        if (currentState != State.NOT_INITIALIZED) {
            return;
        }
        
        try {
            currentState = State.INITIALIZED;
            logger.info(LOG_PREFIX + "Initializing TextToSpeech service...");
            
            // Check if TTS configuration is initialized
            if (!TTSConfig.isInitialized()) {
                throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                    "TTSConfig not initialized. Call TTSConfig.initialize() first.");
            }
            
            if (!TTSConfig.isEnabled()) {
                logger.info(LOG_PREFIX + "TTS is disabled in configuration");
                currentHealth = ServiceHealth.degraded("TTS disabled in configuration");
                return;
            }
            
            // Initialize Google Cloud TTS client (placeholder for now)
            // TODO: Implement actual TTS client initialization
            
            // Initialize advanced caching and rate limiting
            cacheManager = new TTSCacheManager(this, 200, 3600000L); // 200 items, 1 hour expiry
            rateLimiter = APIRateLimiter.forGoogleCloudTTS(); // 600 requests per minute
            
            currentHealth = ServiceHealth.healthy("TTS service initialized successfully");
            logger.info(LOG_PREFIX + "TextToSpeech service initialized successfully");
            
        } catch (Exception e) {
            currentState = State.FAILED;
            currentHealth = ServiceHealth.unhealthy("Initialization failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                "Failed to initialize TextToSpeech service", e);
        }
    }
    
    @Override
    public void start() throws ServiceException {
        if (currentState != State.INITIALIZED) {
            initialize(new HashMap<>());
        }
        
        if (currentState == State.RUNNING) {
            return;
        }
        
        try {
            currentState = State.STARTING;
            logger.info(LOG_PREFIX + "Starting TextToSpeech service...");
            
            // Test TTS connection (placeholder)
            // TODO: Implement actual TTS connection test
            
            currentState = State.RUNNING;
            startTime = System.currentTimeMillis();
            currentHealth = ServiceHealth.healthy("TTS service running");
            logger.info(LOG_PREFIX + "TextToSpeech service started successfully");
            
        } catch (Exception e) {
            currentState = State.FAILED;
            currentHealth = ServiceHealth.unhealthy("Start failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                "Failed to start TextToSpeech service", e);
        }
    }
    
    @Override
    public void stop() throws ServiceException {
        if (currentState != State.RUNNING) {
            return;
        }
        
        try {
            currentState = State.STOPPING;
            logger.info(LOG_PREFIX + "Stopping TextToSpeech service...");
            
            // Clear cache
            if (cacheManager != null) {
                cacheManager.clearCache();
            }
            
            // Close TTS client (placeholder)
            // TODO: Implement actual TTS client shutdown
            
            currentState = State.STOPPED;
            startTime = -1;
            currentHealth = ServiceHealth.unknown("Service stopped");
            logger.info(LOG_PREFIX + "TextToSpeech service stopped successfully");
            
        } catch (Exception e) {
            currentState = State.FAILED;
            currentHealth = ServiceHealth.unhealthy("Stop failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SHUTDOWN_FAILED, 
                "Failed to stop TextToSpeech service", e);
        }
    }
    
    /**
     * Initialize emotion mappings for voices, pitch, and speaking rate
     */
    private void initializeEmotionMappings() {
        // Voice mappings (Korean Neural voices)
        emotionVoiceMapping.put("happy", "ko-KR-Neural2-B");      // Bright female voice
        emotionVoiceMapping.put("sad", "ko-KR-Neural2-D");        // Deeper male voice
        emotionVoiceMapping.put("angry", "ko-KR-Neural2-A");      // Forceful male voice
        emotionVoiceMapping.put("fearful", "ko-KR-Neural2-C");    // Neutral voice
        emotionVoiceMapping.put("excited", "ko-KR-Neural2-B");    // Energetic female voice
        
        // Pitch adjustments (semitones)
        emotionPitchMapping.put("happy", 4.0);
        emotionPitchMapping.put("excited", 5.0);
        emotionPitchMapping.put("sad", -2.0);
        emotionPitchMapping.put("angry", 3.0);
        emotionPitchMapping.put("fearful", 1.5);
        
        // Speaking rate adjustments (multiplier)
        emotionRateMapping.put("happy", 1.2);
        emotionRateMapping.put("excited", 1.3);
        emotionRateMapping.put("sad", 0.8);
        emotionRateMapping.put("angry", 1.3);
        emotionRateMapping.put("fearful", 1.1);
        
        logger.info(LOG_PREFIX + "Initialized emotion mappings for " + emotionVoiceMapping.size() + " emotions");
    }
    
    /**
     * Synthesize speech from text with optional emotion (placeholder implementation)
     */
    public byte[] synthesizeSpeech(String text) throws ServiceException {
        return synthesizeSpeech(text, null);
    }
    
    /**
     * Synthesize speech from text with specified emotion (placeholder implementation)
     */
    public byte[] synthesizeSpeech(String text, String emotion) throws ServiceException {
        if (currentState != State.RUNNING) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "TextToSpeech service is not running");
        }
        
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        try {
            // Use advanced cache manager for retrieval and storage
            if (cacheManager != null) {
                return cacheManager.getAudio(text, emotion != null ? emotion : "neutral");
            } else {
                // Fallback to direct synthesis if cache manager is not available
                return synthesizeDirectly(text, emotion);
            }
            
        } catch (Exception e) {
            currentHealth = ServiceHealth.degraded("Synthesis failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "Failed to synthesize speech: " + e.getMessage(), e);
        }
    }
    
    /**
     * Directly synthesize speech without caching (used internally by cache manager)
     */
    public byte[] synthesizeDirectly(String text, String emotion) throws ServiceException {
        // Check rate limiting before making API call
        if (rateLimiter != null && !rateLimiter.allowRequest()) {
            long waitTime = rateLimiter.getTimeToNextAvailableSlot();
            logger.warning(LOG_PREFIX + "Rate limit exceeded. Next slot available in " + waitTime + "ms");
            
            // For now, throw exception - in production might want to queue or wait
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.HEALTH_CHECK_FAILED,
                "Rate limit exceeded. Try again in " + waitTime + "ms");
        }
        
        // TODO: Implement actual Google Cloud TTS synthesis
        // For now, return placeholder data
        logger.info(LOG_PREFIX + "Synthesizing speech directly with " + 
                   (emotion != null ? emotion : "default") + " emotion: " + 
                   text.substring(0, Math.min(50, text.length())) + 
                   (text.length() > 50 ? "..." : ""));
        
        // Simulate API call delay
        try {
            Thread.sleep(100); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Placeholder: return empty byte array
        logger.info(LOG_PREFIX + "Speech synthesis completed (placeholder), audio size: 0 bytes");
        return new byte[0];
    }
    
    /**
     * Synthesize speech using SSML markup for enhanced expression
     */
    public byte[] synthesizeSpeechWithSSML(String text, String emotion) throws ServiceException {
        if (currentState != State.RUNNING) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "TextToSpeech service is not running");
        }
        
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        try {
            // Generate SSML markup with emotion and Korean optimizations
            String ssmlText = addEmotionSSML(text, emotion);
            
            // Check cache first (using SSML as key)
            String cacheKey = "ssml|" + ssmlText + "|" + (emotion != null ? emotion : "default");
            if (audioCache.containsKey(cacheKey)) {
                logger.info(LOG_PREFIX + "Retrieved SSML audio from cache");
                return audioCache.get(cacheKey);
            }
            
            // TODO: Implement actual Google Cloud TTS synthesis with SSML
            // For now, return placeholder data
            logger.info(LOG_PREFIX + "Synthesizing SSML speech with " + 
                       (emotion != null ? emotion : "default") + " emotion: " + 
                       text.substring(0, Math.min(30, text.length())) + 
                       (text.length() > 30 ? "..." : ""));
            
            // Placeholder: return empty byte array
            byte[] audioData = new byte[0];
            
            // Cache the result (with size limit)
            if (audioCache.size() < maxCacheSize) {
                audioCache.put(cacheKey, audioData);
            }
            
            logger.info(LOG_PREFIX + "SSML speech synthesis completed (placeholder), audio size: " + 
                       audioData.length + " bytes");
            return audioData;
            
        } catch (Exception e) {
            currentHealth = ServiceHealth.degraded("SSML synthesis failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "Failed to synthesize SSML speech: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add emotion-specific SSML markup with Korean language optimizations
     */
    private String addEmotionSSML(String text, String emotion) {
        StringBuilder ssml = new StringBuilder();
        ssml.append("<speak>");
        
        // Apply emotion-specific prosody
        if (emotion != null && emotionPitchMapping.containsKey(emotion.toLowerCase())) {
            double pitch = getEmotionPitch(emotion);
            double rate = getEmotionRate(emotion);
            
            String pitchStr = pitch >= 0 ? "+" + (int)pitch + "st" : (int)pitch + "st";
            
            switch (emotion.toLowerCase()) {
                case "happy":
                case "excited":
                    ssml.append("<prosody rate=\"").append(rate).append("\" pitch=\"").append(pitchStr).append("\">");
                    ssml.append(addKoreanPauses(text));
                    ssml.append("</prosody>");
                    break;
                    
                case "sad":
                    ssml.append("<prosody rate=\"").append(rate).append("\" pitch=\"").append(pitchStr).append("\">");
                    ssml.append("<emphasis level=\"reduced\">");
                    ssml.append(addKoreanPauses(text));
                    ssml.append("</emphasis></prosody>");
                    break;
                    
                case "angry":
                    ssml.append("<prosody rate=\"").append(rate).append("\" pitch=\"").append(pitchStr).append("\">");
                    ssml.append("<emphasis level=\"strong\">");
                    ssml.append(addKoreanPauses(text));
                    ssml.append("</emphasis></prosody>");
                    break;
                    
                case "fearful":
                    ssml.append("<prosody rate=\"").append(rate).append("\" pitch=\"").append(pitchStr).append("\">");
                    ssml.append("<emphasis level=\"moderate\">");
                    ssml.append(addKoreanPauses(text));
                    ssml.append("</emphasis></prosody>");
                    break;
                    
                default:
                    ssml.append(addKoreanPauses(text));
            }
        } else {
            // Default: add Korean pauses without emotion
            ssml.append(addKoreanPauses(text));
        }
        
        ssml.append("</speak>");
        return ssml.toString();
    }
    
    /**
     * Add natural pauses for Korean language patterns
     */
    private String addKoreanPauses(String text) {
        // Korean-optimized text with natural pauses
        String processedText = text;
        
        // Add natural pauses after sentence endings in Korean
        processedText = processedText.replaceAll("([.!?])", "$1<break time=\"500ms\"/>");
        
        // Add shorter pauses after commas
        processedText = processedText.replaceAll("(,)", "$1<break time=\"300ms\"/>");
        
        // Add pauses after Korean conjunctive particles
        processedText = processedText.replaceAll("(그리고)", "$1<break time=\"200ms\"/>");
        processedText = processedText.replaceAll("(하지만)", "$1<break time=\"200ms\"/>");
        processedText = processedText.replaceAll("(그런데)", "$1<break time=\"200ms\"/>");
        processedText = processedText.replaceAll("(그래서)", "$1<break time=\"200ms\"/>");
        
        // Add pauses after Korean topic/subject particles when followed by long phrases
        processedText = processedText.replaceAll("([가-힣]+[는은이가를]\\s)", "$1<break time=\"150ms\"/>");
        
        // Apply Korean pronunciation dictionary
        processedText = applyKoreanPronunciationDictionary(processedText);
        
        return processedText;
    }
    
    /**
     * Apply Korean pronunciation dictionary for gaming terms and common expressions
     */
    private String applyKoreanPronunciationDictionary(String text) {
        // Korean pronunciation dictionary for gaming terms
        Map<String, String> pronunciationDict = new HashMap<>();
        
        // Gaming terms
        pronunciationDict.put("마인크래프트", "<phoneme alphabet=\"ipa\" ph=\"maɪnkɯɾæpɯtɯ\">마인크래프트</phoneme>");
        pronunciationDict.put("인벤토리", "<phoneme alphabet=\"ipa\" ph=\"ɪnbentori\">인벤토리</phoneme>");
        pronunciationDict.put("크리퍼", "<phoneme alphabet=\"ipa\" ph=\"kɯɾipʰʌ\">크리퍼</phoneme>");
        pronunciationDict.put("엔더맨", "<phoneme alphabet=\"ipa\" ph=\"endʌmæn\">엔더맨</phoneme>");
        pronunciationDict.put("레드스톤", "<phoneme alphabet=\"ipa\" ph=\"ɾeɾɯstʰon\">레드스톤</phoneme>");
        
        // Common expressions with natural pronunciation
        pronunciationDict.put("안녕하세요", "<phoneme alphabet=\"ipa\" ph=\"annjʌŋhasejo\">안녕하세요</phoneme>");
        pronunciationDict.put("감사합니다", "<phoneme alphabet=\"ipa\" ph=\"kamsahamnida\">감사합니다</phoneme>");
        pronunciationDict.put("죄송합니다", "<phoneme alphabet=\"ipa\" ph=\"tʃesɔŋhamnida\">죄송합니다</phoneme>");
        
        // Apply pronunciation corrections
        String processedText = text;
        for (Map.Entry<String, String> entry : pronunciationDict.entrySet()) {
            processedText = processedText.replace(entry.getKey(), entry.getValue());
        }
        
        return processedText;
    }
    
    /**
     * Detect and apply appropriate intonation patterns for Korean sentences
     */
    private String applyKoreanIntonationPatterns(String text) {
        String processedText = text;
        
        // Question patterns (의문문)
        if (processedText.matches(".*[가-힣]*[니까까나요인가]\\?.*")) {
            // Rising intonation for questions
            processedText = "<prosody contour=\"(0%,+0st)(50%,+2st)(100%,+5st)\">" + processedText + "</prosody>";
        }
        // Exclamation patterns (감탄문)
        else if (processedText.matches(".*[!].*")) {
            // Emphatic intonation for exclamations
            processedText = "<prosody contour=\"(0%,+2st)(30%,+4st)(70%,+3st)(100%,+0st)\">" + processedText + "</prosody>";
        }
        // Statement patterns with falling intonation (평서문)
        else if (processedText.matches(".*[다요니다습니다][\\.]*$")) {
            // Falling intonation for statements
            processedText = "<prosody contour=\"(0%,+0st)(80%,+1st)(100%,-2st)\">" + processedText + "</prosody>";
        }
        
        return processedText;
    }
    
    /**
     * Enhanced SSML synthesis with full Korean optimization
     */
    public byte[] synthesizeAdvancedKoreanSpeech(String text, String emotion) throws ServiceException {
        if (currentState != State.RUNNING) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "TextToSpeech service is not running");
        }
        
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        try {
            // Apply advanced Korean processing
            String processedText = text;
            
            // 1. Apply intonation patterns
            processedText = applyKoreanIntonationPatterns(processedText);
            
            // 2. Add emotion-specific SSML
            String ssmlText = addEmotionSSML(processedText, emotion);
            
            // 3. Check cache
            String cacheKey = "advanced|" + ssmlText + "|" + (emotion != null ? emotion : "default");
            if (audioCache.containsKey(cacheKey)) {
                logger.info(LOG_PREFIX + "Retrieved advanced Korean audio from cache");
                return audioCache.get(cacheKey);
            }
            
            // TODO: Implement actual Google Cloud TTS synthesis with advanced SSML
            logger.info(LOG_PREFIX + "Synthesizing advanced Korean speech with " + 
                       (emotion != null ? emotion : "default") + " emotion and intonation patterns");
            
            // Placeholder: return empty byte array
            byte[] audioData = new byte[0];
            
            // Cache the result
            if (audioCache.size() < maxCacheSize) {
                audioCache.put(cacheKey, audioData);
            }
            
            logger.info(LOG_PREFIX + "Advanced Korean speech synthesis completed (placeholder)");
            return audioData;
            
        } catch (Exception e) {
            currentHealth = ServiceHealth.degraded("Advanced Korean synthesis failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "Failed to synthesize advanced Korean speech: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get available emotion types
     */
    public String[] getAvailableEmotions() {
        return emotionVoiceMapping.keySet().toArray(new String[0]);
    }
    
    /**
     * Check if a specific emotion is supported
     */
    public boolean isEmotionSupported(String emotion) {
        return emotion != null && emotionVoiceMapping.containsKey(emotion.toLowerCase());
    }
    
    /**
     * Get emotion-specific voice name
     */
    public String getEmotionVoice(String emotion) {
        return emotionVoiceMapping.getOrDefault(emotion != null ? emotion.toLowerCase() : null, defaultVoiceName);
    }
    
    /**
     * Get emotion-specific pitch adjustment
     */
    public double getEmotionPitch(String emotion) {
        return emotionPitchMapping.getOrDefault(emotion != null ? emotion.toLowerCase() : null, 0.0);
    }
    
    /**
     * Get emotion-specific speaking rate adjustment
     */
    public double getEmotionRate(String emotion) {
        return emotionRateMapping.getOrDefault(emotion != null ? emotion.toLowerCase() : null, 1.0);
    }
    
    /**
     * Clear the audio cache
     */
    public void clearCache() {
        if (cacheManager != null) {
            cacheManager.clearCache();
        }
        logger.info(LOG_PREFIX + "Audio cache cleared");
    }
    
    /**
     * Get current cache size
     */
    public int getCacheSize() {
        return cacheManager != null ? cacheManager.getCacheSize() : 0;
    }
    
    /**
     * Get service statistics
     */
    public String getServiceStats() {
        return String.format("TTS Service Stats - Running: %s, Cache Size: %d/%d, Available Emotions: %d", 
                           (currentState == State.RUNNING), audioCache.size(), maxCacheSize, 
                           emotionVoiceMapping.size());
    }
} 