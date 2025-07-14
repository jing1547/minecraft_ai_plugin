package com.minecraft.ai.brain.service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * APIRateLimiter implements rate limiting for API requests to stay within quotas.
 * Uses sliding window approach with configurable time windows and request limits.
 */
public class APIRateLimiter {
    
    private final int maxRequestsPerWindow;
    private final long windowSizeMs;
    private final Queue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    
    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    
    // Configuration
    private final String limitType;
    private volatile boolean enabled = true;
    
    /**
     * Constructor for APIRateLimiter
     * @param maxRequestsPerWindow Maximum number of requests allowed per time window
     * @param windowSizeMs Size of the time window in milliseconds
     * @param limitType Description of what this limiter is for (e.g., "TTS_API", "OpenAI_API")
     */
    public APIRateLimiter(int maxRequestsPerWindow, long windowSizeMs, String limitType) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSizeMs = windowSizeMs;
        this.limitType = limitType;
        
        System.out.println("[APIRateLimiter] Created " + limitType + " rate limiter: " + 
                         maxRequestsPerWindow + " requests per " + (windowSizeMs / 1000) + " seconds");
    }
    
    /**
     * Constructor with default 1-minute window
     * @param maxRequestsPerMinute Maximum requests per minute
     * @param limitType Description of the limiter
     */
    public APIRateLimiter(int maxRequestsPerMinute, String limitType) {
        this(maxRequestsPerMinute, 60000L, limitType); // 1 minute window
    }
    
    /**
     * Check if a request is allowed under the current rate limit
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest() {
        if (!enabled) {
            return true;
        }
        
        totalRequests.incrementAndGet();
        
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - windowSizeMs;
            
            // Remove timestamps outside the current window
            while (!requestTimestamps.isEmpty() && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll();
            }
            
            // Check if we're under the limit
            if (requestTimestamps.size() < maxRequestsPerWindow) {
                requestTimestamps.add(currentTime);
                allowedRequests.incrementAndGet();
                System.out.println("[APIRateLimiter] " + limitType + " - Request ALLOWED (" + 
                                 requestTimestamps.size() + "/" + maxRequestsPerWindow + ")");
                return true;
            } else {
                rejectedRequests.incrementAndGet();
                long waitTime = getTimeToNextAvailableSlot();
                totalWaitTime.addAndGet(waitTime);
                System.out.println("[APIRateLimiter] " + limitType + " - Request REJECTED. Wait " + 
                                 waitTime + "ms (" + requestTimestamps.size() + "/" + maxRequestsPerWindow + ")");
                return false;
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Block until a request is allowed under the rate limit
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void waitForPermission() throws InterruptedException {
        while (!allowRequest()) {
            long waitTime = getTimeToNextAvailableSlot();
            if (waitTime > 0) {
                System.out.println("[APIRateLimiter] " + limitType + " - Waiting " + waitTime + "ms for rate limit slot");
                Thread.sleep(waitTime);
            }
        }
    }
    
    /**
     * Try to acquire permission with a timeout
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if permission was acquired, false if timeout
     */
    public boolean tryAcquirePermission(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (allowRequest()) {
                return true;
            }
            
            long waitTime = Math.min(getTimeToNextAvailableSlot(), 
                                   timeoutMs - (System.currentTimeMillis() - startTime));
            
            if (waitTime <= 0) {
                break;
            }
            
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Get time to wait until next request slot is available
     * @return milliseconds to wait, or 0 if slot is immediately available
     */
    public long getTimeToNextAvailableSlot() {
        if (!enabled) {
            return 0;
        }
        
        lock.lock();
        try {
            if (requestTimestamps.size() < maxRequestsPerWindow) {
                return 0;
            }
            
            // Calculate when the oldest request will expire
            long oldestRequest = requestTimestamps.peek();
            long timeUntilExpiry = windowSizeMs - (System.currentTimeMillis() - oldestRequest);
            
            return Math.max(0, timeUntilExpiry);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Get current number of requests in the time window
     */
    public int getCurrentRequestCount() {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - windowSizeMs;
            
            // Remove expired timestamps
            while (!requestTimestamps.isEmpty() && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll();
            }
            
            return requestTimestamps.size();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Check if rate limiter is currently at capacity
     */
    public boolean isAtCapacity() {
        return getCurrentRequestCount() >= maxRequestsPerWindow;
    }
    
    /**
     * Get remaining requests in current window
     */
    public int getRemainingRequests() {
        return Math.max(0, maxRequestsPerWindow - getCurrentRequestCount());
    }
    
    /**
     * Reset the rate limiter by clearing all timestamps
     */
    public void reset() {
        lock.lock();
        try {
            requestTimestamps.clear();
            System.out.println("[APIRateLimiter] " + limitType + " - Rate limiter reset");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Enable or disable the rate limiter
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        System.out.println("[APIRateLimiter] " + limitType + " - Rate limiter " + 
                         (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if rate limiter is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    // =========================
    // Statistics and Monitoring
    // =========================
    
    /**
     * Get rate limiter statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("limit_type", limitType);
        stats.put("enabled", enabled);
        stats.put("max_requests_per_window", maxRequestsPerWindow);
        stats.put("window_size_ms", windowSizeMs);
        stats.put("window_size_seconds", windowSizeMs / 1000);
        stats.put("current_request_count", getCurrentRequestCount());
        stats.put("remaining_requests", getRemainingRequests());
        stats.put("is_at_capacity", isAtCapacity());
        stats.put("time_to_next_slot_ms", getTimeToNextAvailableSlot());
        stats.put("total_requests", totalRequests.get());
        stats.put("allowed_requests", allowedRequests.get());
        stats.put("rejected_requests", rejectedRequests.get());
        stats.put("rejection_rate_percent", getRejectionRate());
        stats.put("average_wait_time_ms", getAverageWaitTime());
        return stats;
    }
    
    /**
     * Get rejection rate as percentage
     */
    public double getRejectionRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) rejectedRequests.get() / total * 100.0;
    }
    
    /**
     * Get average wait time for rejected requests
     */
    public double getAverageWaitTime() {
        long rejected = rejectedRequests.get();
        if (rejected == 0) {
            return 0.0;
        }
        return (double) totalWaitTime.get() / rejected;
    }
    
    /**
     * Reset statistics counters
     */
    public void resetStatistics() {
        totalRequests.set(0);
        allowedRequests.set(0);
        rejectedRequests.set(0);
        totalWaitTime.set(0);
        System.out.println("[APIRateLimiter] " + limitType + " - Statistics reset");
    }
    
    /**
     * Get current utilization as percentage
     */
    public double getCurrentUtilization() {
        return (double) getCurrentRequestCount() / maxRequestsPerWindow * 100.0;
    }
    
    /**
     * Check if rate limiter is healthy (not constantly at capacity)
     */
    public boolean isHealthy() {
        return getCurrentUtilization() < 90.0; // Consider healthy if under 90% utilization
    }
    
    /**
     * Get detailed status information
     */
    public String getStatusInfo() {
        return String.format("[%s] %d/%d requests (%.1f%%), next slot in %dms, %s", 
                           limitType,
                           getCurrentRequestCount(),
                           maxRequestsPerWindow,
                           getCurrentUtilization(),
                           getTimeToNextAvailableSlot(),
                           isHealthy() ? "HEALTHY" : "DEGRADED");
    }
    
    // =========================
    // Factory Methods for Common Configurations
    // =========================
    
    /**
     * Create rate limiter for Google Cloud TTS API (600 requests per minute default)
     */
    public static APIRateLimiter forGoogleCloudTTS() {
        return new APIRateLimiter(600, 60000L, "Google_Cloud_TTS");
    }
    
    /**
     * Create rate limiter for OpenAI API (3000 requests per minute for GPT-4)
     */
    public static APIRateLimiter forOpenAI() {
        return new APIRateLimiter(3000, 60000L, "OpenAI_API");
    }
    
    /**
     * Create rate limiter for general API usage
     */
    public static APIRateLimiter forGeneralAPI(int requestsPerMinute, String apiName) {
        return new APIRateLimiter(requestsPerMinute, 60000L, apiName);
    }
    
    /**
     * Create conservative rate limiter (lower limits for safety)
     */
    public static APIRateLimiter createConservative(int requestsPerMinute, String apiName) {
        // Use 80% of the specified limit for safety margin
        int conservativeLimit = (int) (requestsPerMinute * 0.8);
        return new APIRateLimiter(conservativeLimit, 60000L, apiName + "_Conservative");
    }
} 