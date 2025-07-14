package com.minecraft.ai.brain.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * TTSCacheManager manages audio caching for Text-to-Speech operations.
 * Implements LRU (Least Recently Used) cache eviction policy for efficient memory usage.
 */
public class TTSCacheManager {
    
    private static final Logger logger = Logger.getLogger(TTSCacheManager.class.getName());
    
    private final Map<String, CacheEntry> audioCache = new ConcurrentHashMap<>();
    private final Map<String, Long> accessTimes = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final int maxCacheSize;
    private final long maxCacheAgeMs;
    private final TextToSpeechService ttsService;
    
    // Metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    /**
     * Cache entry containing audio data and metadata
     */
    private static class CacheEntry {
        private final byte[] audioData;
        private final String emotion;
        private final long createdAt;
        private final long lastAccessed;
        private final String textHash;
        
        public CacheEntry(byte[] audioData, String emotion, String textHash) {
            this.audioData = audioData.clone();
            this.emotion = emotion;
            this.textHash = textHash;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessed = System.currentTimeMillis();
        }
        
        public byte[] getAudioData() { 
            return audioData.clone(); 
        }
        
        public String getEmotion() { 
            return emotion; 
        }
        
        public long getCreatedAt() { 
            return createdAt; 
        }
        
        public long getLastAccessed() { 
            return lastAccessed; 
        }
        
        public String getTextHash() { 
            return textHash; 
        }
        
        public long getAge() {
            return System.currentTimeMillis() - createdAt;
        }
        
        public boolean isExpired(long maxAgeMs) {
            return getAge() > maxAgeMs;
        }
    }
    
    /**
     * Constructor for TTSCacheManager
     * @param ttsService The TextToSpeechService to use for generating audio
     * @param maxCacheSize Maximum number of entries in the cache
     * @param maxCacheAgeMs Maximum age of cache entries in milliseconds (default: 1 hour)
     */
    public TTSCacheManager(TextToSpeechService ttsService, int maxCacheSize, long maxCacheAgeMs) {
        this.ttsService = ttsService;
        this.maxCacheSize = maxCacheSize;
        this.maxCacheAgeMs = maxCacheAgeMs;
        
        // Start cleanup thread
        startCleanupThread();
    }
    
    /**
     * Constructor with default cache age (1 hour)
     */
    public TTSCacheManager(TextToSpeechService ttsService, int maxCacheSize) {
        this(ttsService, maxCacheSize, 3600000L); // 1 hour default
    }
    
    /**
     * Get audio data for the given text and emotion, using cache if available
     */
    public byte[] getAudio(String text, String emotion) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        totalRequests.incrementAndGet();
        String cacheKey = generateCacheKey(text, emotion);
        
        lock.readLock().lock();
        try {
            // Check if in cache and not expired
            CacheEntry entry = audioCache.get(cacheKey);
            if (entry != null && !entry.isExpired(maxCacheAgeMs)) {
                cacheHits.incrementAndGet();
                updateAccessTime(cacheKey);
                logger.info("[TTSCacheManager] Cache HIT for key: " + cacheKey.substring(0, Math.min(50, cacheKey.length())) + "...");
                return entry.getAudioData();
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Cache miss - generate new audio
        cacheMisses.incrementAndGet();
        logger.warning("[TTSCacheManager] Cache MISS for key: " + cacheKey.substring(0, Math.min(50, cacheKey.length())) + "...");
        
        byte[] audioData;
        try {
            audioData = ttsService.synthesizeSpeech(text, emotion != null ? emotion : "neutral");
        } catch (Exception e) {
            logger.severe("[TTSCacheManager] Failed to generate audio: " + e.getMessage());
            throw new RuntimeException("Failed to generate audio for text: " + text, e);
        }
        
        // Add to cache
        addToCache(cacheKey, audioData, emotion != null ? emotion : "neutral", text);
        
        return audioData;
    }
    
    /**
     * Get audio data with default neutral emotion
     */
    public byte[] getAudio(String text) {
        return getAudio(text, "neutral");
    }
    
    /**
     * Add audio data to cache
     */
    private void addToCache(String cacheKey, byte[] audioData, String emotion, String text) {
        lock.writeLock().lock();
        try {
            // Check if cache is full
            if (audioCache.size() >= maxCacheSize) {
                evictLeastRecentlyUsed();
            }
            
            String textHash = generateTextHash(text);
            CacheEntry entry = new CacheEntry(audioData, emotion, textHash);
            audioCache.put(cacheKey, entry);
            updateAccessTime(cacheKey);
            
            logger.info("[TTSCacheManager] Added to cache: " + cacheKey.substring(0, Math.min(50, cacheKey.length())) + 
                             "... (Size: " + audioCache.size() + "/" + maxCacheSize + ")");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Generate cache key from text and emotion
     */
    private String generateCacheKey(String text, String emotion) {
        String normalizedText = text.trim().toLowerCase();
        String normalizedEmotion = emotion != null ? emotion.toLowerCase() : "neutral";
        return normalizedText + "|" + normalizedEmotion;
    }
    
    /**
     * Generate hash for text content
     */
    private String generateTextHash(String text) {
        return String.valueOf(text.hashCode());
    }
    
    /**
     * Update access time for LRU tracking
     */
    private void updateAccessTime(String cacheKey) {
        accessTimes.put(cacheKey, System.currentTimeMillis());
    }
    
    /**
     * Evict least recently used entry
     */
    private void evictLeastRecentlyUsed() {
        if (audioCache.isEmpty()) {
            return;
        }
        
        String lruKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, Long> entry : accessTimes.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                lruKey = entry.getKey();
            }
        }
        
        if (lruKey != null) {
            audioCache.remove(lruKey);
            accessTimes.remove(lruKey);
            evictions.incrementAndGet();
            logger.info("[TTSCacheManager] Evicted LRU entry: " + lruKey.substring(0, Math.min(50, lruKey.length())) + "...");
        }
    }
    
    /**
     * Clear all cache entries
     */
    public void clearCache() {
        lock.writeLock().lock();
        try {
            audioCache.clear();
            accessTimes.clear();
            logger.info("[TTSCacheManager] Cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove expired entries from cache
     */
    public void cleanupExpiredEntries() {
        lock.writeLock().lock();
        try {
            List<String> expiredKeys = new ArrayList<>();
            
            for (Map.Entry<String, CacheEntry> entry : audioCache.entrySet()) {
                if (entry.getValue().isExpired(maxCacheAgeMs)) {
                    expiredKeys.add(entry.getKey());
                }
            }
            
            for (String key : expiredKeys) {
                audioCache.remove(key);
                accessTimes.remove(key);
                evictions.incrementAndGet();
            }
            
            if (!expiredKeys.isEmpty()) {
                logger.info("[TTSCacheManager] Cleaned up " + expiredKeys.size() + " expired entries");
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Start background thread for periodic cleanup
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300000); // 5 minutes
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.severe("[TTSCacheManager] Cleanup thread error: " + e.getMessage());
                }
            }
        }, "TTS-Cache-Cleanup");
        
        cleanupThread.setDaemon(true);
        cleanupThread.start();
        logger.info("[TTSCacheManager] Started cache cleanup thread");
    }
    
    // =========================
    // Cache Statistics and Management
    // =========================
    
    /**
     * Get current cache size
     */
    public int getCacheSize() {
        return audioCache.size();
    }
    
    /**
     * Get maximum cache size
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }
    
    /**
     * Get cache hit rate as percentage
     */
    public double getCacheHitRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHits.get() / total * 100.0;
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_size", getCacheSize());
        stats.put("max_cache_size", getMaxCacheSize());
        stats.put("cache_hits", cacheHits.get());
        stats.put("cache_misses", cacheMisses.get());
        stats.put("evictions", evictions.get());
        stats.put("total_requests", totalRequests.get());
        stats.put("hit_rate_percent", getCacheHitRate());
        stats.put("max_cache_age_ms", maxCacheAgeMs);
        return stats;
    }
    
    /**
     * Get detailed cache entries information
     */
    public List<Map<String, Object>> getCacheEntries() {
        lock.readLock().lock();
        try {
            List<Map<String, Object>> entries = new ArrayList<>();
            
            for (Map.Entry<String, CacheEntry> entry : audioCache.entrySet()) {
                Map<String, Object> entryInfo = new HashMap<>();
                entryInfo.put("key", entry.getKey());
                entryInfo.put("emotion", entry.getValue().getEmotion());
                entryInfo.put("created_at", entry.getValue().getCreatedAt());
                entryInfo.put("last_accessed", entry.getValue().getLastAccessed());
                entryInfo.put("age_ms", entry.getValue().getAge());
                entryInfo.put("audio_size_bytes", entry.getValue().getAudioData().length);
                entryInfo.put("text_hash", entry.getValue().getTextHash());
                entries.add(entryInfo);
            }
            
            return entries;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if a specific text and emotion combination is cached
     */
    public boolean isCached(String text, String emotion) {
        String cacheKey = generateCacheKey(text, emotion);
        lock.readLock().lock();
        try {
            CacheEntry entry = audioCache.get(cacheKey);
            return entry != null && !entry.isExpired(maxCacheAgeMs);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Preload common phrases into cache
     */
    public void preloadCommonPhrases(List<String> phrases, String emotion) {
        logger.info("[TTSCacheManager] Preloading " + phrases.size() + " common phrases with emotion: " + emotion);
        
        for (String phrase : phrases) {
            try {
                getAudio(phrase, emotion);
            } catch (Exception e) {
                logger.severe("[TTSCacheManager] Failed to preload phrase: " + phrase + " - " + e.getMessage());
            }
        }
        
        logger.info("[TTSCacheManager] Preloading completed. Cache size: " + getCacheSize());
    }
    
    /**
     * Reset cache statistics
     */
    public void resetStatistics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        evictions.set(0);
        totalRequests.set(0);
        logger.info("[TTSCacheManager] Cache statistics reset");
    }
} 