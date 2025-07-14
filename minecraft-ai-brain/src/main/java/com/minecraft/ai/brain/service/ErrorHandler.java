package com.minecraft.ai.brain.service;

import com.minecraft.ai.brain.utils.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Comprehensive error handling system with circuit breaker pattern,
 * exponential backoff, and graceful degradation for speech recognition service.
 */
public class ErrorHandler {
    private Logger logger;
    
    private final int maxConsecutiveErrors;
    private final long circuitBreakerTimeoutMs;
    private final long maxBackoffMs;
    private final ConcurrentHashMap<String, ServiceErrorInfo> serviceErrors;
    private final ReentrantLock lock;
    
    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        LOW,        // Temporary, recoverable errors
        MEDIUM,     // Service degradation
        HIGH,       // Service interruption
        CRITICAL    // System failure
    }
    
    /**
     * Error recovery strategies
     */
    public enum RecoveryStrategy {
        RETRY,              // Simple retry
        EXPONENTIAL_BACKOFF, // Retry with exponential backoff
        CIRCUIT_BREAKER,    // Use circuit breaker pattern
        GRACEFUL_DEGRADATION, // Fallback to alternative service
        FAIL_FAST          // Immediate failure
    }
    
    /**
     * Service-specific error information
     */
    private static class ServiceErrorInfo {
        final AtomicInteger consecutiveErrors = new AtomicInteger(0);
        final AtomicLong lastErrorTime = new AtomicLong(0);
        final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger totalErrors = new AtomicInteger(0);
        final AtomicInteger totalRequests = new AtomicInteger(0);
        volatile CircuitBreakerState circuitState = CircuitBreakerState.CLOSED;
        volatile long circuitOpenTime = 0;
        volatile String lastErrorMessage = "";
        volatile ErrorSeverity lastErrorSeverity = ErrorSeverity.LOW;
    }
    
    /**
     * Circuit breaker states
     */
    public enum CircuitBreakerState {
        CLOSED,     // Normal operation
        OPEN,       // Failing fast
        HALF_OPEN   // Testing recovery
    }
    
    /**
     * Error handling result
     */
    public static class ErrorResult {
        public final boolean shouldRetry;
        public final long retryDelayMs;
        public final RecoveryStrategy strategy;
        public final String message;
        public final boolean useAlternative;
        
        ErrorResult(boolean shouldRetry, long retryDelayMs, RecoveryStrategy strategy, 
                   String message, boolean useAlternative) {
            this.shouldRetry = shouldRetry;
            this.retryDelayMs = retryDelayMs;
            this.strategy = strategy;
            this.message = message;
            this.useAlternative = useAlternative;
        }
        
        public static ErrorResult retry(long delayMs, String message) {
            return new ErrorResult(true, delayMs, RecoveryStrategy.RETRY, message, false);
        }
        
        public static ErrorResult backoff(long delayMs, String message) {
            return new ErrorResult(true, delayMs, RecoveryStrategy.EXPONENTIAL_BACKOFF, message, false);
        }
        
        public static ErrorResult degraded(String message) {
            return new ErrorResult(false, 0, RecoveryStrategy.GRACEFUL_DEGRADATION, message, true);
        }
        
        public static ErrorResult failFast(String message) {
            return new ErrorResult(false, 0, RecoveryStrategy.FAIL_FAST, message, false);
        }
    }

    public ErrorHandler() {
        this(5, 30 * 1000, 60 * 1000); // 5 errors, 30s circuit timeout, 60s max backoff
    }

    public ErrorHandler(int maxConsecutiveErrors, long circuitBreakerTimeoutMs, long maxBackoffMs) {
        this.maxConsecutiveErrors = maxConsecutiveErrors;
        this.circuitBreakerTimeoutMs = circuitBreakerTimeoutMs;
        this.maxBackoffMs = maxBackoffMs;
        this.serviceErrors = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
    }

    /**
     * Set the logger instance for this error handler
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Handle a speech recognition error
     */
    public ErrorResult handleRecognitionError(String serviceId, Exception error) {
        return handleError(serviceId, error, ErrorSeverity.MEDIUM, "Speech recognition failed");
    }

    /**
     * Handle a general service error with automatic severity assessment
     */
    public ErrorResult handleError(String serviceId, Exception error) {
        ErrorSeverity severity = assessErrorSeverity(error);
        return handleError(serviceId, error, severity, error.getMessage());
    }

    /**
     * Handle an error with specified severity
     */
    public ErrorResult handleError(String serviceId, Exception error, ErrorSeverity severity, String context) {
        if (serviceId == null) serviceId = "default";
        
        ServiceErrorInfo errorInfo = serviceErrors.computeIfAbsent(serviceId, k -> new ServiceErrorInfo());
        long currentTime = System.currentTimeMillis();
        
        // Log the error
        logError(serviceId, error, severity, context);
        
        // Update error statistics
        updateErrorStats(errorInfo, currentTime, severity, error.getMessage());
        
        // Determine recovery strategy
        return determineRecoveryStrategy(serviceId, errorInfo, currentTime, severity);
    }

    /**
     * Record a successful operation
     */
    public void recordSuccess(String serviceId) {
        if (serviceId == null) serviceId = "default";
        
        ServiceErrorInfo errorInfo = serviceErrors.get(serviceId);
        if (errorInfo != null) {
            long currentTime = System.currentTimeMillis();
            
            // Reset consecutive errors on success
            if (errorInfo.consecutiveErrors.get() > 0 && logger != null) {
                logger.info("Service " + serviceId + " recovered after " + 
                          errorInfo.consecutiveErrors.get() + " consecutive errors");
            }
            
            errorInfo.consecutiveErrors.set(0);
            errorInfo.lastSuccessTime.set(currentTime);
            errorInfo.totalRequests.incrementAndGet();
            
            // Handle circuit breaker state transitions
            if (errorInfo.circuitState == CircuitBreakerState.HALF_OPEN) {
                errorInfo.circuitState = CircuitBreakerState.CLOSED;
                if (logger != null) {
                    logger.info("Circuit breaker for service " + serviceId + " closed after successful recovery");
                }
            }
        }
    }

    /**
     * Check if a service is available (circuit breaker check)
     */
    public boolean isServiceAvailable(String serviceId) {
        if (serviceId == null) serviceId = "default";
        
        ServiceErrorInfo errorInfo = serviceErrors.get(serviceId);
        if (errorInfo == null) {
            return true;
        }
        
        return checkCircuitBreaker(errorInfo, System.currentTimeMillis());
    }

    /**
     * Execute an operation with automatic error handling
     */
    public <T> T executeWithErrorHandling(String serviceId, Supplier<T> operation, Supplier<T> fallback) {
        if (!isServiceAvailable(serviceId)) {
            if (logger != null) {
                logger.warning("Service " + serviceId + " unavailable, using fallback");
            }
            return fallback != null ? fallback.get() : null;
        }
        
        try {
            T result = operation.get();
            recordSuccess(serviceId);
            return result;
        } catch (Exception e) {
            ErrorResult errorResult = handleError(serviceId, e);
            
            if (errorResult.useAlternative && fallback != null) {
                if (logger != null) {
                    logger.info("Using fallback for service " + serviceId + ": " + errorResult.message);
                }
                return fallback.get();
            }
            
            if (errorResult.shouldRetry && errorResult.retryDelayMs > 0) {
                try {
                    Thread.sleep(errorResult.retryDelayMs);
                    return operation.get(); // Single retry attempt
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation interrupted", ie);
                } catch (Exception retryError) {
                    // Retry failed, use fallback if available
                    if (fallback != null) {
                        return fallback.get();
                    }
                    throw new RuntimeException("Operation failed after retry", retryError);
                }
            }
            
            throw new RuntimeException("Operation failed: " + errorResult.message, e);
        }
    }

    /**
     * Assess error severity based on exception type and characteristics
     */
    private ErrorSeverity assessErrorSeverity(Exception error) {
        String errorMessage = error.getMessage();
        String errorClass = error.getClass().getSimpleName();
        
        // Network and timeout errors
        if (errorClass.contains("Timeout") || errorClass.contains("SocketTimeout")) {
            return ErrorSeverity.MEDIUM;
        }
        
        // Authentication and authorization errors
        if (errorClass.contains("Auth") || (errorMessage != null && 
            (errorMessage.contains("unauthorized") || errorMessage.contains("forbidden")))) {
            return ErrorSeverity.HIGH;
        }
        
        // Quota and rate limit errors
        if (errorMessage != null && (errorMessage.contains("quota") || errorMessage.contains("rate limit"))) {
            return ErrorSeverity.HIGH;
        }
        
        // Service unavailable
        if (errorClass.contains("ServiceUnavailable") || 
            (errorMessage != null && errorMessage.contains("unavailable"))) {
            return ErrorSeverity.CRITICAL;
        }
        
        // Default to medium severity
        return ErrorSeverity.MEDIUM;
    }

    /**
     * Update error statistics
     */
    private void updateErrorStats(ServiceErrorInfo errorInfo, long currentTime, 
                                ErrorSeverity severity, String errorMessage) {
        lock.lock();
        try {
            errorInfo.consecutiveErrors.incrementAndGet();
            errorInfo.totalErrors.incrementAndGet();
            errorInfo.totalRequests.incrementAndGet();
            errorInfo.lastErrorTime.set(currentTime);
            errorInfo.lastErrorSeverity = severity;
            errorInfo.lastErrorMessage = errorMessage != null ? errorMessage : "Unknown error";
        } finally {
            lock.unlock();
        }
    }

    /**
     * Determine appropriate recovery strategy
     */
    private ErrorResult determineRecoveryStrategy(String serviceId, ServiceErrorInfo errorInfo, 
                                               long currentTime, ErrorSeverity severity) {
        int consecutiveErrors = errorInfo.consecutiveErrors.get();
        
        // Check circuit breaker
        if (!checkCircuitBreaker(errorInfo, currentTime)) {
            return ErrorResult.degraded("Service " + serviceId + " circuit breaker is open");
        }
        
        // Critical errors trigger immediate circuit breaker
        if (severity == ErrorSeverity.CRITICAL) {
            errorInfo.circuitState = CircuitBreakerState.OPEN;
            errorInfo.circuitOpenTime = currentTime;
            return ErrorResult.degraded("Critical error detected, circuit breaker opened");
        }
        
        // High severity errors with multiple consecutive failures
        if (severity == ErrorSeverity.HIGH && consecutiveErrors >= maxConsecutiveErrors / 2) {
            errorInfo.circuitState = CircuitBreakerState.OPEN;
            errorInfo.circuitOpenTime = currentTime;
            return ErrorResult.degraded("Multiple high-severity errors, circuit breaker opened");
        }
        
        // Too many consecutive errors
        if (consecutiveErrors >= maxConsecutiveErrors) {
            errorInfo.circuitState = CircuitBreakerState.OPEN;
            errorInfo.circuitOpenTime = currentTime;
            return ErrorResult.degraded("Maximum consecutive errors reached, circuit breaker opened");
        }
        
        // Calculate exponential backoff
        long backoffTime = calculateBackoffTime(consecutiveErrors);
        
        return ErrorResult.backoff(backoffTime, 
            "Retrying with exponential backoff (" + backoffTime + "ms) after " + consecutiveErrors + " errors");
    }

    /**
     * Check circuit breaker state and manage transitions
     */
    private boolean checkCircuitBreaker(ServiceErrorInfo errorInfo, long currentTime) {
        switch (errorInfo.circuitState) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (currentTime - errorInfo.circuitOpenTime >= circuitBreakerTimeoutMs) {
                    errorInfo.circuitState = CircuitBreakerState.HALF_OPEN;
                    if (logger != null) {
                        logger.info("Circuit breaker transitioning to HALF_OPEN state");
                    }
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                return true;
                
            default:
                return true;
        }
    }

    /**
     * Calculate exponential backoff time
     */
    private long calculateBackoffTime(int consecutiveErrors) {
        long baseDelay = 1000; // 1 second base delay
        long backoff = (long) (baseDelay * Math.pow(2, Math.min(consecutiveErrors - 1, 6))); // Cap at 2^6
        return Math.min(backoff, maxBackoffMs);
    }

    /**
     * Log error with appropriate level based on severity
     */
    private void logError(String serviceId, Exception error, ErrorSeverity severity, String context) {
        if (logger == null) {
            return; // No logger available, skip logging
        }
        
        String message = String.format("Service %s error [%s]: %s - %s", 
                                     serviceId, severity, context, error.getMessage());
        
        switch (severity) {
            case LOW:
                logger.info(message);
                break;
            case MEDIUM:
                logger.warning(message);
                break;
            case HIGH:
            case CRITICAL:
                logger.severe(message);
                break;
        }
        
        // Log stack trace for high and critical errors
        if (severity == ErrorSeverity.HIGH || severity == ErrorSeverity.CRITICAL) {
            logger.severe("Stack trace: " + getStackTrace(error));
        }
    }

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception error) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get error statistics for a service
     */
    public ErrorStats getErrorStats(String serviceId) {
        if (serviceId == null) serviceId = "default";
        
        ServiceErrorInfo errorInfo = serviceErrors.get(serviceId);
        if (errorInfo == null) {
            return new ErrorStats(serviceId, 0, 0, 0, 0, CircuitBreakerState.CLOSED, "No errors recorded");
        }
        
        return new ErrorStats(
            serviceId,
            errorInfo.consecutiveErrors.get(),
            errorInfo.totalErrors.get(),
            errorInfo.totalRequests.get(),
            System.currentTimeMillis() - errorInfo.lastSuccessTime.get(),
            errorInfo.circuitState,
            errorInfo.lastErrorMessage
        );
    }

    /**
     * Reset error state for a service
     */
    public void resetErrorState(String serviceId) {
        if (serviceId == null) serviceId = "default";
        
        ServiceErrorInfo errorInfo = serviceErrors.get(serviceId);
        if (errorInfo != null) {
            lock.lock();
            try {
                errorInfo.consecutiveErrors.set(0);
                errorInfo.totalErrors.set(0);
                errorInfo.totalRequests.set(0);
                errorInfo.lastSuccessTime.set(System.currentTimeMillis());
                errorInfo.circuitState = CircuitBreakerState.CLOSED;
                errorInfo.circuitOpenTime = 0;
                errorInfo.lastErrorMessage = "";
                errorInfo.lastErrorSeverity = ErrorSeverity.LOW;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Error statistics container
     */
    public static class ErrorStats {
        public final String serviceId;
        public final int consecutiveErrors;
        public final int totalErrors;
        public final int totalRequests;
        public final long timeSinceLastSuccess;
        public final CircuitBreakerState circuitState;
        public final String lastErrorMessage;
        public final double errorRate;

        ErrorStats(String serviceId, int consecutiveErrors, int totalErrors, int totalRequests,
                  long timeSinceLastSuccess, CircuitBreakerState circuitState, String lastErrorMessage) {
            this.serviceId = serviceId;
            this.consecutiveErrors = consecutiveErrors;
            this.totalErrors = totalErrors;
            this.totalRequests = totalRequests;
            this.timeSinceLastSuccess = timeSinceLastSuccess;
            this.circuitState = circuitState;
            this.lastErrorMessage = lastErrorMessage;
            this.errorRate = totalRequests > 0 ? (double) totalErrors / totalRequests : 0.0;
        }

        @Override
        public String toString() {
            return String.format("ErrorStats{service=%s, consecutive=%d, total=%d/%d (%.2f%%), " +
                               "timeSinceSuccess=%dms, circuit=%s}", 
                               serviceId, consecutiveErrors, totalErrors, totalRequests, 
                               errorRate * 100, timeSinceLastSuccess, circuitState);
        }
    }
} 