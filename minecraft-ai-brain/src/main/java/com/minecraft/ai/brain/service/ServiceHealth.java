package com.minecraft.ai.brain.service;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents the health status of a service
 */
public class ServiceHealth {
    
    /**
     * Health status levels
     */
    public enum Status {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
    
    private final Status status;
    private final String message;
    private final Map<String, Object> details;
    private final List<String> issues;
    private final long timestamp;
    
    /**
     * Create a healthy service health status
     */
    public static ServiceHealth healthy() {
        return new ServiceHealth(Status.HEALTHY, "Service is healthy", null, null);
    }
    
    /**
     * Create a healthy service health status with message
     */
    public static ServiceHealth healthy(String message) {
        return new ServiceHealth(Status.HEALTHY, message, null, null);
    }
    
    /**
     * Create a degraded service health status
     */
    public static ServiceHealth degraded(String message) {
        return new ServiceHealth(Status.DEGRADED, message, null, null);
    }
    
    /**
     * Create a degraded service health status with issues
     */
    public static ServiceHealth degraded(String message, List<String> issues) {
        return new ServiceHealth(Status.DEGRADED, message, null, issues);
    }
    
    /**
     * Create an unhealthy service health status
     */
    public static ServiceHealth unhealthy(String message) {
        return new ServiceHealth(Status.UNHEALTHY, message, null, null);
    }
    
    /**
     * Create an unhealthy service health status with issues
     */
    public static ServiceHealth unhealthy(String message, List<String> issues) {
        return new ServiceHealth(Status.UNHEALTHY, message, null, issues);
    }
    
    /**
     * Create an unknown service health status
     */
    public static ServiceHealth unknown(String message) {
        return new ServiceHealth(Status.UNKNOWN, message, null, null);
    }
    
    /**
     * Private constructor
     */
    private ServiceHealth(Status status, String message, Map<String, Object> details, List<String> issues) {
        this.status = status;
        this.message = message;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get the health status
     */
    public Status getStatus() {
        return status;
    }
    
    /**
     * Get the health message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Get additional health details
     */
    public Map<String, Object> getDetails() {
        return new HashMap<>(details);
    }
    
    /**
     * Get list of issues
     */
    public List<String> getIssues() {
        return new ArrayList<>(issues);
    }
    
    /**
     * Get the timestamp when this health status was created
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Check if the service is healthy
     */
    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }
    
    /**
     * Check if the service is degraded
     */
    public boolean isDegraded() {
        return status == Status.DEGRADED;
    }
    
    /**
     * Check if the service is unhealthy
     */
    public boolean isUnhealthy() {
        return status == Status.UNHEALTHY;
    }
    
    /**
     * Check if the health status is unknown
     */
    public boolean isUnknown() {
        return status == Status.UNKNOWN;
    }
    
    /**
     * Add a detail to the health status
     */
    public ServiceHealth withDetail(String key, Object value) {
        Map<String, Object> newDetails = new HashMap<>(this.details);
        newDetails.put(key, value);
        return new ServiceHealth(status, message, newDetails, issues);
    }
    
    /**
     * Add an issue to the health status
     */
    public ServiceHealth withIssue(String issue) {
        List<String> newIssues = new ArrayList<>(this.issues);
        newIssues.add(issue);
        return new ServiceHealth(status, message, details, newIssues);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ServiceHealth{");
        sb.append("status=").append(status);
        sb.append(", message='").append(message).append('\'');
        if (!details.isEmpty()) {
            sb.append(", details=").append(details);
        }
        if (!issues.isEmpty()) {
            sb.append(", issues=").append(issues);
        }
        sb.append(", timestamp=").append(timestamp);
        sb.append('}');
        return sb.toString();
    }
} 