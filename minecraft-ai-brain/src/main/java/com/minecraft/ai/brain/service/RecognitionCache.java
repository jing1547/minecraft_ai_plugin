package com.minecraft.ai.brain.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Caching system for speech recognition results to reduce API calls and costs.
 * Uses audio content hashing and LRU eviction policy.
 */
public class RecognitionCache {
    private final Map<String, CacheEntry> audioHashToText;
    private final int maxCacheSize;
    private final long cacheEntryTtlMs;
    private final ReentrantReadWriteLock lock;

    /**
     * Cache entry with timestamp for TTL management
     */
    private static class CacheEntry {
        final String recognizedText;
        final long timestamp;
        final int confidence;

        CacheEntry(String recognizedText, int confidence) {
            this.recognizedText = recognizedText;
            this.confidence = confidence;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    /**
     * Thread-safe LRU cache implementation
     */
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public LRUCache(int maxSize) {
            super(maxSize + 1, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    public RecognitionCache() {
        this(100, 5 * 60 * 1000); // Default: 100 entries, 5 minutes TTL
    }

    public RecognitionCache(int maxCacheSize, long cacheEntryTtlMs) {
        this.maxCacheSize = maxCacheSize;
        this.cacheEntryTtlMs = cacheEntryTtlMs;
        this.audioHashToText = new ConcurrentHashMap<>(new LRUCache<>(maxCacheSize));
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Cache a recognition result with confidence score
     */
    public void cacheResult(byte[] audioData, String recognizedText, int confidence) {
        if (audioData == null || recognizedText == null || recognizedText.trim().isEmpty()) {
            return;
        }

        String audioHash = calculateAudioHash(audioData);
        CacheEntry entry = new CacheEntry(recognizedText.trim(), confidence);

        lock.writeLock().lock();
        try {
            audioHashToText.put(audioHash, entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Cache a recognition result with default confidence
     */
    public void cacheResult(byte[] audioData, String recognizedText) {
        cacheResult(audioData, recognizedText, 95); // Default high confidence
    }

    /**
     * Retrieve cached recognition result
     */
    public Optional<String> getCachedResult(byte[] audioData) {
        if (audioData == null) {
            return Optional.empty();
        }

        String audioHash = calculateAudioHash(audioData);
        
        lock.readLock().lock();
        try {
            CacheEntry entry = audioHashToText.get(audioHash);
            if (entry != null && !entry.isExpired(cacheEntryTtlMs)) {
                return Optional.of(entry.recognizedText);
            } else if (entry != null && entry.isExpired(cacheEntryTtlMs)) {
                // Remove expired entry - need to upgrade to write lock
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // Double-check pattern - entry might have been removed by another thread
                    CacheEntry recheck = audioHashToText.get(audioHash);
                    if (recheck != null && recheck.isExpired(cacheEntryTtlMs)) {
                        audioHashToText.remove(audioHash);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
                return Optional.empty();
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Calculate perceptual hash for audio data
     * Uses SHA-256 for content-based hashing
     */
    private String calculateAudioHash(byte[] audioData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Add basic audio properties to hash to differentiate similar content
            digest.update(audioData);
            digest.update(("" + audioData.length).getBytes());
            
            byte[] hash = digest.digest();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to UUID if SHA-256 is not available
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Clean expired entries from cache
     */
    public void cleanExpiredEntries() {
        lock.writeLock().lock();
        try {
            audioHashToText.entrySet().removeIf(entry -> 
                entry.getValue().isExpired(cacheEntryTtlMs));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            int totalEntries = audioHashToText.size();
            long expiredEntries = audioHashToText.values().stream()
                .mapToLong(entry -> entry.isExpired(cacheEntryTtlMs) ? 1 : 0)
                .sum();
            
            return new CacheStats(totalEntries, (int)expiredEntries, maxCacheSize);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all cached entries
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            audioHashToText.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Cache statistics container
     */
    public static class CacheStats {
        public final int totalEntries;
        public final int expiredEntries;
        public final int maxSize;
        public final int activeEntries;
        public final double hitRatio;

        CacheStats(int totalEntries, int expiredEntries, int maxSize) {
            this.totalEntries = totalEntries;
            this.expiredEntries = expiredEntries;
            this.maxSize = maxSize;
            this.activeEntries = totalEntries - expiredEntries;
            this.hitRatio = totalEntries > 0 ? (double)activeEntries / totalEntries : 0.0;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{active=%d, expired=%d, total=%d/%d, hitRatio=%.2f}", 
                activeEntries, expiredEntries, totalEntries, maxSize, hitRatio);
        }
    }
} 