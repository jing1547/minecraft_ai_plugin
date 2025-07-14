package com.minecraft.ai.brain.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rate limiter for managing API requests to control costs and prevent quota exhaustion.
 * Uses sliding window algorithm with user-specific and global rate limiting.
 */
public class RateLimiter {
    private final int maxRequestsPerMinute;
    private final int maxRequestsPerHour;
    private final long windowSizeMs;
    private final ConcurrentLinkedQueue<Long> globalRequestTimestamps;
    private final ConcurrentHashMap<String, UserRateInfo> userRateInfo;
    private final ReentrantLock lock;
    private final AtomicInteger totalRequests;
    private final AtomicInteger rejectedRequests;
    
    /**
     * User-specific rate limiting information
     */
    private static class UserRateInfo {
        final ConcurrentLinkedQueue<Long> requestTimestamps;
        final AtomicInteger requestCount;
        volatile long lastRequestTime;
        volatile boolean rateLimited;
        
        UserRateInfo() {
            this.requestTimestamps = new ConcurrentLinkedQueue<>();
            this.requestCount = new AtomicInteger(0);
            this.lastRequestTime = 0;
            this.rateLimited = false;
        }
    }
    
    /**
     * Rate limit result information
     */
    public static class RateLimitResult {
        public final boolean allowed;
        public final long retryAfterMs;
        public final int remainingRequests;
        public final String reason;
        
        RateLimitResult(boolean allowed, long retryAfterMs, int remainingRequests, String reason) {
            this.allowed = allowed;
            this.retryAfterMs = retryAfterMs;
            this.remainingRequests = remainingRequests;
            this.reason = reason;
        }
        
        public static RateLimitResult allowed(int remainingRequests) {
            return new RateLimitResult(true, 0, remainingRequests, "Request allowed");
        }
        
        public static RateLimitResult denied(long retryAfterMs, String reason) {
            return new RateLimitResult(false, retryAfterMs, 0, reason);
        }
    }

    public RateLimiter() {
        this(60, 1000, 60 * 1000); // Default: 60 req/min, 1000 req/hour, 1-minute window
    }

    public RateLimiter(int maxRequestsPerMinute, int maxRequestsPerHour, long windowSizeMs) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxRequestsPerHour = maxRequestsPerHour;
        this.windowSizeMs = windowSizeMs;
        this.globalRequestTimestamps = new ConcurrentLinkedQueue<>();
        this.userRateInfo = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
        this.totalRequests = new AtomicInteger(0);
        this.rejectedRequests = new AtomicInteger(0);
    }

    /**
     * Check if a request is allowed for global rate limiting
     */
    public RateLimitResult allowRequest() {
        return allowRequest("global");
    }

    /**
     * Check if a request is allowed for a specific user
     */
    public RateLimitResult allowRequest(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            userId = "anonymous";
        }

        long currentTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        // Check global rate limits first
        RateLimitResult globalResult = checkGlobalRateLimit(currentTime);
        if (!globalResult.allowed) {
            rejectedRequests.incrementAndGet();
            return globalResult;
        }

        // Check user-specific rate limits
        RateLimitResult userResult = checkUserRateLimit(userId, currentTime);
        if (!userResult.allowed) {
            rejectedRequests.incrementAndGet();
            return userResult;
        }

        // Record the request
        recordRequest(userId, currentTime);
        return RateLimitResult.allowed(calculateRemainingRequests(userId, currentTime));
    }

    /**
     * Check global rate limiting
     */
    private RateLimitResult checkGlobalRateLimit(long currentTime) {
        lock.lock();
        try {
            // Clean old timestamps
            cleanOldTimestamps(globalRequestTimestamps, currentTime, windowSizeMs);
            
            int currentRequests = globalRequestTimestamps.size();
            
            // Check minute-based limit
            if (currentRequests >= maxRequestsPerMinute) {
                Long oldestTimestamp = globalRequestTimestamps.peek();
                long retryAfter = oldestTimestamp != null ? 
                    Math.max(0, windowSizeMs - (currentTime - oldestTimestamp)) : windowSizeMs;
                return RateLimitResult.denied(retryAfter, 
                    "Global rate limit exceeded: " + currentRequests + "/" + maxRequestsPerMinute + " requests per minute");
            }
            
            // Check hour-based limit
            long hourAgo = currentTime - (60 * 60 * 1000);
            long hourlyRequests = globalRequestTimestamps.stream()
                .mapToLong(Long::longValue)
                .filter(timestamp -> timestamp > hourAgo)
                .count();
                
            if (hourlyRequests >= maxRequestsPerHour) {
                return RateLimitResult.denied(60 * 60 * 1000, 
                    "Global hourly rate limit exceeded: " + hourlyRequests + "/" + maxRequestsPerHour + " requests per hour");
            }
            
            return RateLimitResult.allowed(maxRequestsPerMinute - currentRequests);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check user-specific rate limiting
     */
    private RateLimitResult checkUserRateLimit(String userId, long currentTime) {
        UserRateInfo userInfo = userRateInfo.computeIfAbsent(userId, k -> new UserRateInfo());
        
        synchronized (userInfo) {
            // Clean old timestamps
            cleanOldTimestamps(userInfo.requestTimestamps, currentTime, windowSizeMs);
            
            int userRequests = userInfo.requestTimestamps.size();
            int userMaxPerMinute = Math.max(1, maxRequestsPerMinute / 4); // 25% of global limit per user
            
            if (userRequests >= userMaxPerMinute) {
                Long oldestTimestamp = userInfo.requestTimestamps.peek();
                long retryAfter = oldestTimestamp != null ? 
                    Math.max(0, windowSizeMs - (currentTime - oldestTimestamp)) : windowSizeMs;
                userInfo.rateLimited = true;
                return RateLimitResult.denied(retryAfter, 
                    "User rate limit exceeded: " + userRequests + "/" + userMaxPerMinute + " requests per minute");
            }
            
            userInfo.rateLimited = false;
            return RateLimitResult.allowed(userMaxPerMinute - userRequests);
        }
    }

    /**
     * Record a successful request
     */
    private void recordRequest(String userId, long currentTime) {
        // Record global request
        globalRequestTimestamps.add(currentTime);
        
        // Record user request
        UserRateInfo userInfo = userRateInfo.get(userId);
        if (userInfo != null) {
            synchronized (userInfo) {
                userInfo.requestTimestamps.add(currentTime);
                userInfo.requestCount.incrementAndGet();
                userInfo.lastRequestTime = currentTime;
            }
        }
    }

    /**
     * Calculate remaining requests for a user
     */
    private int calculateRemainingRequests(String userId, long currentTime) {
        UserRateInfo userInfo = userRateInfo.get(userId);
        if (userInfo == null) {
            return maxRequestsPerMinute / 4;
        }
        
        synchronized (userInfo) {
            cleanOldTimestamps(userInfo.requestTimestamps, currentTime, windowSizeMs);
            int userMaxPerMinute = Math.max(1, maxRequestsPerMinute / 4);
            return Math.max(0, userMaxPerMinute - userInfo.requestTimestamps.size());
        }
    }

    /**
     * Clean timestamps older than the window
     */
    private void cleanOldTimestamps(ConcurrentLinkedQueue<Long> timestamps, long currentTime, long windowSize) {
        long cutoffTime = currentTime - windowSize;
        while (!timestamps.isEmpty()) {
            Long timestamp = timestamps.peek();
            if (timestamp != null && timestamp < cutoffTime) {
                timestamps.poll();
            } else {
                break;
            }
        }
    }

    /**
     * Get current rate limiter statistics
     */
    public RateLimiterStats getStats() {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            cleanOldTimestamps(globalRequestTimestamps, currentTime, windowSizeMs);
            
            int activeUsers = (int) userRateInfo.values().stream()
                .mapToLong(userInfo -> {
                    synchronized (userInfo) {
                        cleanOldTimestamps(userInfo.requestTimestamps, currentTime, windowSizeMs);
                        return userInfo.requestTimestamps.isEmpty() ? 0 : 1;
                    }
                })
                .sum();
                
            int rateLimitedUsers = (int) userRateInfo.values().stream()
                .mapToLong(userInfo -> userInfo.rateLimited ? 1 : 0)
                .sum();
            
            return new RateLimiterStats(
                globalRequestTimestamps.size(),
                maxRequestsPerMinute,
                activeUsers,
                rateLimitedUsers,
                totalRequests.get(),
                rejectedRequests.get()
            );
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reset rate limiter state
     */
    public void reset() {
        lock.lock();
        try {
            globalRequestTimestamps.clear();
            userRateInfo.clear();
            totalRequests.set(0);
            rejectedRequests.set(0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Rate limiter statistics
     */
    public static class RateLimiterStats {
        public final int currentRequests;
        public final int maxRequests;
        public final int activeUsers;
        public final int rateLimitedUsers;
        public final int totalRequests;
        public final int rejectedRequests;
        public final double rejectionRate;

        RateLimiterStats(int currentRequests, int maxRequests, int activeUsers, 
                        int rateLimitedUsers, int totalRequests, int rejectedRequests) {
            this.currentRequests = currentRequests;
            this.maxRequests = maxRequests;
            this.activeUsers = activeUsers;
            this.rateLimitedUsers = rateLimitedUsers;
            this.totalRequests = totalRequests;
            this.rejectedRequests = rejectedRequests;
            this.rejectionRate = totalRequests > 0 ? (double) rejectedRequests / totalRequests : 0.0;
        }

        @Override
        public String toString() {
            return String.format("RateLimiterStats{requests=%d/%d, users=%d (%d limited), total=%d, rejected=%d (%.2f%%)}", 
                currentRequests, maxRequests, activeUsers, rateLimitedUsers, 
                totalRequests, rejectedRequests, rejectionRate * 100);
        }
    }
} 