package com.minecraft.ai.brain.service;

import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;
import java.io.FileInputStream;

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
    private TTSErrorHandler errorHandler;
    private TTSQueueManager queueManager;
    
    // Audio player dependency
    private AudioPlayerService audioPlayerService;
    
    // Google Cloud TTS client
    private TextToSpeechClient ttsClient;
    
    // Configuration cache
    private String defaultLanguageCode = "ko-KR";
    private String defaultVoiceName = "ko-KR-Neural2-C";
    private AudioEncoding audioEncoding = AudioEncoding.MP3;
    private int sampleRateHertz = 24000; // 24kHz for better quality
    
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
        return Arrays.asList("audio_player");
    }
    
    @Override
    public ServiceHealth getHealth() {
        return currentHealth;
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // TTS-specific metrics
        metrics.put("state", currentState.name());
        metrics.put("uptime", getUptime());
        metrics.put("defaultLanguage", defaultLanguageCode);
        metrics.put("defaultVoice", defaultVoiceName);
        
        // Cache metrics
        if (cacheManager != null) {
            Map<String, Object> cacheStats = cacheManager.getCacheStatistics();
            metrics.put("cacheHitRate", cacheStats.get("hit_rate_percent"));
            metrics.put("cacheSize", cacheStats.get("cache_size"));
            metrics.put("totalCacheRequests", cacheStats.get("total_requests"));
        }
        
        // Rate limiter metrics
        if (rateLimiter != null) {
            metrics.put("rateLimitAllowed", rateLimiter.allowRequest());
            metrics.put("rateLimitWaitTime", rateLimiter.getTimeToNextAvailableSlot());
        }
        
        // Error handler metrics
        if (errorHandler != null) {
            metrics.put("errorStats", errorHandler.getErrorStatistics());
            metrics.put("serviceHealthy", errorHandler.isServiceHealthy());
        }
        
        // Queue manager metrics
        if (queueManager != null) {
            metrics.put("queueStats", queueManager.getStatistics());
        }
        
        return metrics;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("languageCode", defaultLanguageCode);
        config.put("voiceName", defaultVoiceName);
        config.put("audioEncoding", audioEncoding);
        config.put("sampleRateHertz", sampleRateHertz);
        return config;
    }
    
    @Override
    public void onConfigurationChange(Map<String, Object> newConfig) {
        // Handle configuration changes
    }
    
    @Override
    public boolean isEnabled() {
        // Check if TTS is enabled in configuration, not the current running state
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
            
            // Get AudioPlayerService dependency
            Service audioService = dependencies.get("audio_player");
            if (audioService instanceof AudioPlayerService) {
                this.audioPlayerService = (AudioPlayerService) audioService;
                logger.info(LOG_PREFIX + "AudioPlayerService dependency resolved");
            } else {
                throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.DEPENDENCY_NOT_FOUND, 
                    "AudioPlayerService dependency not found or invalid type");
            }
            
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
            
            // Initialize Google Cloud TTS client
            try {
                initializeTTSClient();
                
                // Initialize advanced caching and rate limiting
                cacheManager = new TTSCacheManager(this, 200, 3600000L); // 200 items, 1 hour expiry
                rateLimiter = APIRateLimiter.forGoogleCloudTTS(); // 600 requests per minute
                errorHandler = new TTSErrorHandler();
                queueManager = new TTSQueueManager(this, audioPlayerService);
                
                currentHealth = ServiceHealth.healthy("TTS service initialized successfully");
                logger.info(LOG_PREFIX + "TextToSpeech service initialized successfully");
            } catch (IOException e) {
                // Handle credential errors gracefully
                logger.severe(LOG_PREFIX + "Failed to initialize Google Cloud TTS client: " + e.getMessage());
                currentHealth = ServiceHealth.unhealthy("TTS client initialization failed: " + e.getMessage());
                // Don't throw exception here to allow service to be in INITIALIZED state
                // The error will be caught in start() method
            }
            
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
        
        // Check if TTS is enabled
        if (!TTSConfig.isEnabled()) {
            logger.warning(LOG_PREFIX + "TTS is disabled in configuration, not starting service");
            currentState = State.STOPPED;
            currentHealth = ServiceHealth.degraded("TTS disabled in configuration");
            return;
        }
        
        // Check if TTS client was initialized
        if (ttsClient == null) {
            currentState = State.FAILED;
            currentHealth = ServiceHealth.unhealthy("TTS client not initialized - check credentials");
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                "TTS client not initialized. Please check Google Cloud credentials file.");
        }
        
        try {
            currentState = State.STARTING;
            logger.info(LOG_PREFIX + "Starting TextToSpeech service...");
            
            // Test TTS connection
            testTTSConnection();
            
            // Start queue manager
            if (queueManager != null) {
                queueManager.start();
            }
            
            currentState = State.RUNNING;
            startTime = System.currentTimeMillis();
            currentHealth = ServiceHealth.healthy("TTS service running");
            logger.info(LOG_PREFIX + "TextToSpeech service started successfully");
            
        } catch (Exception e) {
            currentState = State.FAILED;
            currentHealth = ServiceHealth.unhealthy("Start failed: " + e.getMessage());
            logger.severe(LOG_PREFIX + "Failed to start TTS service: " + e.getMessage());
            e.printStackTrace();
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                "Failed to start TextToSpeech service: " + e.getMessage(), e);
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
            
            // Stop queue manager
            if (queueManager != null) {
                queueManager.stop();
            }
            
            // Cleanup resources
            if (cacheManager != null) {
                cacheManager.clearCache();
            }
            
            // Close TTS client
            if (ttsClient != null) {
                ttsClient.close();
                ttsClient = null;
            }
            
            currentState = State.STOPPED;
            currentHealth = ServiceHealth.healthy("TTS service stopped");
            logger.info(LOG_PREFIX + "TextToSpeech service stopped successfully");
            
        } catch (Exception e) {
            currentState = State.FAILED;
            currentHealth = ServiceHealth.unhealthy("Stop failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.STARTUP_FAILED, 
                "Failed to stop TextToSpeech service", e);
        }
    }
    
    /**
     * Initialize Google Cloud TTS client
     */
    private void initializeTTSClient() throws IOException {
        try {
            // Set credentials from config
            String credentialsPath = TTSConfig.getCredentialsPath();
            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                // TTSConfig.getCredentialsPath() now returns absolute path for relative paths
                java.io.File credFile = new java.io.File(credentialsPath);
                
                if (!credFile.exists()) {
                    logger.severe(LOG_PREFIX + "Credentials file not found: " + credentialsPath);
                    throw new IOException("Google Cloud credentials file not found: " + credentialsPath);
                }
                if (!credFile.canRead()) {
                    logger.severe(LOG_PREFIX + "Cannot read credentials file: " + credentialsPath);
                    throw new IOException("Cannot read Google Cloud credentials file: " + credentialsPath);
                }
                
                logger.info(LOG_PREFIX + "Loading credentials from: " + credentialsPath);
                
                // Use GoogleCredentials like STT does
                com.google.auth.oauth2.GoogleCredentials credentials = 
                    com.google.auth.oauth2.GoogleCredentials.fromStream(
                        new java.io.FileInputStream(credentialsPath)
                    );
                
                // Create TTS client with credentials
                TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                    .setCredentialsProvider(com.google.api.gax.core.FixedCredentialsProvider.create(credentials))
                    .build();
                    
                ttsClient = TextToSpeechClient.create(settings);
                logger.info(LOG_PREFIX + "Google Cloud TTS client initialized successfully with credentials");
            } else {
                logger.warning(LOG_PREFIX + "No credentials path configured, trying default authentication");
                // Create TTS client with default credentials
                ttsClient = TextToSpeechClient.create();
            }
            
        } catch (IOException e) {
            logger.severe(LOG_PREFIX + "Failed to initialize TTS client: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Test TTS connection by listing available voices
     */
    private void testTTSConnection() throws ServiceException {
        try {
            // List available voices for the default language
            ListVoicesRequest request = ListVoicesRequest.newBuilder()
                .setLanguageCode(defaultLanguageCode)
                .build();
                
            ListVoicesResponse response = ttsClient.listVoices(request);
            
            logger.info(LOG_PREFIX + "TTS connection test successful. Available voices for " + defaultLanguageCode + ":");
            for (Voice voice : response.getVoicesList()) {
                logger.info(LOG_PREFIX + "  - " + voice.getName() + " (" + voice.getSsmlGender() + ")");
            }
            
        } catch (Exception e) {
            logger.severe(LOG_PREFIX + "TTS connection test failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.HEALTH_CHECK_FAILED,
                "Failed to connect to Google Cloud TTS", e);
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
            
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.HEALTH_CHECK_FAILED,
                "Rate limit exceeded. Try again in " + waitTime + "ms");
        }
        
        if (ttsClient == null) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "TTS client not initialized");
        }
        
        // 에러 핸들러를 통한 재시도 로직
        if (errorHandler != null) {
            byte[] result = errorHandler.executeWithRetry(() -> {
                return performSynthesis(text, emotion);
            }, text);
            
            if (result != null) {
                return result;
            } else {
                // 모든 재시도 실패 - 폴백 처리
                TTSErrorHandler.ErrorType lastError = errorHandler.classifyError(
                    new Exception("All retry attempts failed"));
                String fallbackMsg = errorHandler.getFallbackMessage(text, lastError);
                throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                    fallbackMsg);
            }
        } else {
            // 에러 핸들러가 없으면 직접 실행
            try {
                return performSynthesis(text, emotion);
            } catch (Exception e) {
                throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                    "Failed to synthesize speech: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * 실제 음성 합성 수행
     */
    private byte[] performSynthesis(String text, String emotion) throws Exception {
        logger.info(LOG_PREFIX + "Synthesizing speech with " + 
                   (emotion != null ? emotion : "default") + " emotion: " + 
                   text.substring(0, Math.min(50, text.length())) + 
                   (text.length() > 50 ? "..." : ""));
        
        // Build the voice request
        String voiceName = getEmotionVoice(emotion);
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
            .setLanguageCode(defaultLanguageCode)
            .setName(voiceName)
            .setSsmlGender(getVoiceGender(voiceName))
            .build();
        
        // Select the type of audio file
        AudioConfig audioConfig = AudioConfig.newBuilder()
            .setAudioEncoding(audioEncoding)
            .setSampleRateHertz(sampleRateHertz)
            .setPitch(getEmotionPitch(emotion))
            .setSpeakingRate(getEmotionRate(emotion))
            .build();
        
        // Build the synthesis input
        SynthesisInput input = SynthesisInput.newBuilder()
            .setText(text)
            .build();
        
        // Perform the text-to-speech request
        SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);
        
        // Get the audio contents from the response
        ByteString audioContents = response.getAudioContent();
        byte[] audioData = audioContents.toByteArray();
        
        logger.info(LOG_PREFIX + "Speech synthesis completed, audio size: " + audioData.length + " bytes");
        return audioData;
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
            
            // Use advanced cache manager with SSML text as key
            if (cacheManager != null) {
                return cacheManager.getSSMLAudio(ssmlText, emotion != null ? emotion : "neutral");
            } else {
                // Fallback to direct synthesis if cache manager is not available
                return synthesizeSSMLDirectly(ssmlText, emotion);
            }
            
        } catch (Exception e) {
            currentHealth = ServiceHealth.degraded("SSML synthesis failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "Failed to synthesize SSML speech: " + e.getMessage(), e);
        }
    }
    
    /**
     * Directly synthesize SSML speech without caching (used internally by cache manager)
     */
    public byte[] synthesizeSSMLDirectly(String ssmlText, String emotion) throws ServiceException {
        // Check rate limiting before making API call
        if (rateLimiter != null && !rateLimiter.allowRequest()) {
            long waitTime = rateLimiter.getTimeToNextAvailableSlot();
            logger.warning(LOG_PREFIX + "Rate limit exceeded for SSML. Next slot available in " + waitTime + "ms");
            
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.HEALTH_CHECK_FAILED,
                "Rate limit exceeded for SSML. Try again in " + waitTime + "ms");
        }
        
        if (ttsClient == null) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "TTS client not initialized");
        }
        
        try {
            logger.info(LOG_PREFIX + "Synthesizing SSML speech with " + 
                       (emotion != null ? emotion : "default") + " emotion");
            
            // Build the voice request
            String voiceName = getEmotionVoice(emotion);
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(defaultLanguageCode)
                .setName(voiceName)
                .setSsmlGender(getVoiceGender(voiceName))
                .build();
            
            // Select the type of audio file
            AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(audioEncoding)
                .setSampleRateHertz(sampleRateHertz)
                .build();
            
            // Build the synthesis input with SSML
            SynthesisInput input = SynthesisInput.newBuilder()
                .setSsml(ssmlText)
                .build();
            
            // Perform the text-to-speech request
            SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);
            
            // Get the audio contents from the response
            ByteString audioContents = response.getAudioContent();
            byte[] audioData = audioContents.toByteArray();
            
            logger.info(LOG_PREFIX + "SSML speech synthesis completed, audio size: " + audioData.length + " bytes");
            return audioData;
            
        } catch (Exception e) {
            logger.severe(LOG_PREFIX + "SSML speech synthesis failed: " + e.getMessage());
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
            
            // 3. Use advanced cache manager with processed SSML text
            if (cacheManager != null) {
                return cacheManager.getSSMLAudio(ssmlText, emotion != null ? emotion : "neutral");
            } else {
                // Fallback to direct synthesis if cache manager is not available
                return synthesizeAdvancedKoreanDirectly(ssmlText, emotion);
            }
            
        } catch (Exception e) {
            currentHealth = ServiceHealth.degraded("Advanced Korean synthesis failed: " + e.getMessage());
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "Failed to synthesize advanced Korean speech: " + e.getMessage(), e);
        }
    }
    
    /**
     * Directly synthesize advanced Korean speech without caching (used internally by cache manager)
     */
    public byte[] synthesizeAdvancedKoreanDirectly(String ssmlText, String emotion) throws ServiceException {
        // Check rate limiting before making API call
        if (rateLimiter != null && !rateLimiter.allowRequest()) {
            long waitTime = rateLimiter.getTimeToNextAvailableSlot();
            logger.warning(LOG_PREFIX + "Rate limit exceeded for advanced Korean. Next slot available in " + waitTime + "ms");
            
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.HEALTH_CHECK_FAILED,
                "Rate limit exceeded for advanced Korean. Try again in " + waitTime + "ms");
        }
        
        // Use SSML synthesis method for advanced Korean
        return synthesizeSSMLDirectly(ssmlText, emotion);
    }
    
    /**
     * Check if the service is currently running
     */
    public boolean isRunning() {
        return currentState == State.RUNNING;
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
     * Get voice gender based on voice name
     * Korean Neural2 voices: A, B, C are female; D is male
     */
    private SsmlVoiceGender getVoiceGender(String voiceName) {
        if (voiceName == null || voiceName.isEmpty()) {
            return SsmlVoiceGender.FEMALE; // Default to female
        }
        
        // Check the last character of the voice name
        char lastChar = voiceName.charAt(voiceName.length() - 1);
        
        // Korean Neural2 voice gender mapping
        if (voiceName.contains("ko-KR-Neural2-")) {
            switch (lastChar) {
                case 'D':
                    return SsmlVoiceGender.MALE;
                case 'A':
                case 'B':
                case 'C':
                default:
                    return SsmlVoiceGender.FEMALE;
            }
        }
        
        // Default to female for other voices
        return SsmlVoiceGender.FEMALE;
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
        int cacheSize = cacheManager != null ? cacheManager.getCacheSize() : 0;
        int maxCache = cacheManager != null ? cacheManager.getMaxCacheSize() : 0;
        double hitRate = cacheManager != null ? cacheManager.getCacheHitRate() : 0.0;
        
        return String.format("TTS Service Stats - Running: %s, Cache: %d/%d (%.1f%% hit rate), Emotions: %d, Rate Limiter: %s", 
                           (currentState == State.RUNNING), 
                           cacheSize, maxCache, hitRate,
                           emotionVoiceMapping.size(),
                           rateLimiter != null ? rateLimiter.getStatusInfo() : "N/A");
    }

    /**
     * Synthesize speech and play it to a player at a specific location
     */
    public void synthesizeAndPlay(String text, Player player, AudioPlayerService.Position position) throws ServiceException {
        synthesizeAndPlay(text, "neutral", player, position);
    }
    
    /**
     * Synthesize speech with emotion and play it to a player at a specific location
     */
    public void synthesizeAndPlay(String text, String emotion, Player player, AudioPlayerService.Position position) throws ServiceException {
        synthesizeAndPlay(text, emotion, player, position, 5); // 기본 우선순위 5
    }
    
    /**
     * Synthesize speech with emotion and priority, and play it to a player at a specific location
     */
    public void synthesizeAndPlay(String text, String emotion, Player player, AudioPlayerService.Position position, int priority) throws ServiceException {
        if (currentState != State.RUNNING) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                "TextToSpeech service is not running");
        }
        
        if (audioPlayerService == null) {
            throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.DEPENDENCY_NOT_FOUND,
                "AudioPlayerService not available");
        }
        
        if (queueManager != null) {
            // 큐 매니저를 통해 처리
            queueManager.queueRequest(player.getUniqueId(), text, emotion, position, priority)
                .thenAccept(success -> {
                    if (success) {
                        logger.debug(LOG_PREFIX + "TTS request queued successfully: " + 
                                   text.substring(0, Math.min(50, text.length())));
                    } else {
                        logger.warning(LOG_PREFIX + "TTS request failed: " + 
                                     text.substring(0, Math.min(50, text.length())));
                    }
                });
        } else {
            // 큐 매니저가 없으면 직접 처리 (이전 방식)
            try {
                byte[] audioData = synthesizeSpeech(text, emotion);
                audioPlayerService.queueAudio(audioData, position, player.getUniqueId());
                logger.debug(LOG_PREFIX + "Queued audio for playback: " + 
                           text.substring(0, Math.min(50, text.length())));
            } catch (Exception e) {
                currentHealth = ServiceHealth.degraded("Synthesis and play failed: " + e.getMessage());
                throw new ServiceException(SERVICE_ID, ServiceException.ErrorCode.SERVICE_NOT_FOUND,
                    "Failed to synthesize and play speech: " + e.getMessage(), e);
            }
        }
    }
} 