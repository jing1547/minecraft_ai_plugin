package com.minecraft.ai.brain.service;

/**
 * Exception thrown when service operations fail
 */
public class ServiceException extends Exception {
    
    private final String serviceId;
    private final ErrorCode errorCode;
    
    /**
     * Error codes for service exceptions
     */
    public enum ErrorCode {
        INITIALIZATION_FAILED,
        STARTUP_FAILED,
        SHUTDOWN_FAILED,
        DEPENDENCY_NOT_FOUND,
        DEPENDENCY_FAILED,
        CIRCULAR_DEPENDENCY,
        INVALID_CONFIGURATION,
        SERVICE_NOT_FOUND,
        SERVICE_ALREADY_REGISTERED,
        HEALTH_CHECK_FAILED
    }
    
    public ServiceException(String serviceId, ErrorCode errorCode, String message) {
        super(message);
        this.serviceId = serviceId;
        this.errorCode = errorCode;
    }
    
    public ServiceException(String serviceId, ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.serviceId = serviceId;
        this.errorCode = errorCode;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String getMessage() {
        return String.format("[%s] %s: %s", serviceId, errorCode, super.getMessage());
    }
} 