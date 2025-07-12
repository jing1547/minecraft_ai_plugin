package com.minecraft.ai.brain.service;

import java.util.Map;
import java.util.List;

/**
 * Base interface for all services in the AI Brain plugin
 * Provides lifecycle management and dependency injection support
 */
public interface Service {
    
    /**
     * Lifecycle states for services
     */
    enum State {
        NOT_INITIALIZED,
        INITIALIZED,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        FAILED
    }
    
    /**
     * Priority levels for service initialization order
     */
    enum Priority {
        CRITICAL(0),    // Core services like ConfigManager
        HIGH(1),        // Important services like WebSocket
        NORMAL(2),      // Regular services like CommandHandler
        LOW(3);         // Optional services like Telemetry
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Get the unique identifier for this service
     * @return Service ID
     */
    String getServiceId();
    
    /**
     * Get the human-readable name for this service
     * @return Service name
     */
    String getServiceName();
    
    /**
     * Get the current state of this service
     * @return Current state
     */
    State getState();
    
    /**
     * Get the priority level for initialization order
     * @return Priority level
     */
    Priority getPriority();
    
    /**
     * Get the list of service IDs this service depends on
     * @return List of dependency service IDs
     */
    List<String> getDependencies();
    
    /**
     * Initialize the service with its dependencies
     * @param dependencies Map of dependency service ID to service instance
     * @throws ServiceException if initialization fails
     */
    void initialize(Map<String, Service> dependencies) throws ServiceException;
    
    /**
     * Start the service
     * @throws ServiceException if startup fails
     */
    void start() throws ServiceException;
    
    /**
     * Stop the service
     * @throws ServiceException if shutdown fails
     */
    void stop() throws ServiceException;
    
    /**
     * Check if the service is healthy
     * @return Health status
     */
    ServiceHealth getHealth();
    
    /**
     * Get service statistics/metrics
     * @return Map of metric names to values
     */
    Map<String, Object> getMetrics();
    
    /**
     * Get service configuration
     * @return Configuration map
     */
    Map<String, Object> getConfiguration();
    
    /**
     * Handle service configuration changes
     * @param newConfig New configuration
     */
    void onConfigurationChange(Map<String, Object> newConfig);
    
    /**
     * Check if this service is enabled
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
    
    /**
     * Get the time when this service was last started
     * @return Start time in milliseconds, or -1 if never started
     */
    long getStartTime();
    
    /**
     * Get the uptime of this service in milliseconds
     * @return Uptime in milliseconds, or 0 if not running
     */
    long getUptime();
} 