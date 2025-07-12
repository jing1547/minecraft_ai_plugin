package com.minecraft.ai.brain.service;

import com.minecraft.ai.brain.MinecraftAIBrainPlugin;
import com.minecraft.ai.brain.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Central service manager for dependency injection and lifecycle management
 * Handles service registration, dependency resolution, and health monitoring
 */
public class ServiceManager {
    
    private final MinecraftAIBrainPlugin plugin;
    private final Logger logger;
    
    // Service registry
    private final Map<String, Service> services = new ConcurrentHashMap<>();
    private final Map<String, ServiceRegistration> registrations = new ConcurrentHashMap<>();
    private final List<ServiceLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    
    // Health monitoring
    private BukkitTask healthCheckTask;
    private final Map<String, ServiceHealth> healthCache = new ConcurrentHashMap<>();
    
    // Lifecycle management
    private volatile boolean isInitialized = false;
    private volatile boolean isStarted = false;
    private volatile boolean isShuttingDown = false;
    
    /**
     * Service registration info
     */
    public static class ServiceRegistration {
        private final String serviceId;
        private final Class<? extends Service> serviceClass;
        private final Service serviceInstance;
        private final long registrationTime;
        
        public ServiceRegistration(String serviceId, Class<? extends Service> serviceClass, Service serviceInstance) {
            this.serviceId = serviceId;
            this.serviceClass = serviceClass;
            this.serviceInstance = serviceInstance;
            this.registrationTime = System.currentTimeMillis();
        }
        
        public String getServiceId() { return serviceId; }
        public Class<? extends Service> getServiceClass() { return serviceClass; }
        public Service getServiceInstance() { return serviceInstance; }
        public long getRegistrationTime() { return registrationTime; }
    }
    
    /**
     * Service lifecycle listener
     */
    public interface ServiceLifecycleListener {
        void onServiceRegistered(String serviceId, Service service);
        void onServiceInitialized(String serviceId, Service service);
        void onServiceStarted(String serviceId, Service service);
        void onServiceStopped(String serviceId, Service service);
        void onServiceFailed(String serviceId, Service service, Exception error);
    }
    
    /**
     * Constructor
     */
    public ServiceManager(MinecraftAIBrainPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        
        logger.info("ServiceManager initialized");
    }
    
    /**
     * Register a service instance
     */
    public void registerService(Service service) throws ServiceException {
        if (service == null) {
            throw new ServiceException("unknown", ServiceException.ErrorCode.INVALID_CONFIGURATION, "Service cannot be null");
        }
        
        String serviceId = service.getServiceId();
        if (serviceId == null || serviceId.trim().isEmpty()) {
            throw new ServiceException("unknown", ServiceException.ErrorCode.INVALID_CONFIGURATION, "Service ID cannot be null or empty");
        }
        
        if (services.containsKey(serviceId)) {
            throw new ServiceException(serviceId, ServiceException.ErrorCode.SERVICE_ALREADY_REGISTERED, 
                    "Service with ID '" + serviceId + "' is already registered");
        }
        
        // Validate dependencies
        List<String> dependencies = service.getDependencies();
        if (dependencies != null) {
            for (String depId : dependencies) {
                if (depId.equals(serviceId)) {
                    throw new ServiceException(serviceId, ServiceException.ErrorCode.CIRCULAR_DEPENDENCY, 
                            "Service cannot depend on itself");
                }
            }
        }
        
        services.put(serviceId, service);
        registrations.put(serviceId, new ServiceRegistration(serviceId, service.getClass(), service));
        
        logger.info("Service registered: " + serviceId + " (" + service.getServiceName() + ")");
        
        // Notify listeners
        for (ServiceLifecycleListener listener : listeners) {
            try {
                listener.onServiceRegistered(serviceId, service);
            } catch (Exception e) {
                logger.warning("Error notifying listener of service registration: " + e.getMessage());
            }
        }
    }
    
    /**
     * Register a service class (will be instantiated)
     */
    public void registerService(String serviceId, Class<? extends Service> serviceClass) throws ServiceException {
        try {
            Service service = serviceClass.getDeclaredConstructor().newInstance();
            registerService(service);
        } catch (Exception e) {
            throw new ServiceException(serviceId, ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                    "Failed to instantiate service class: " + serviceClass.getSimpleName(), e);
        }
    }
    
    /**
     * Get a service by ID
     */
    public <T extends Service> T getService(String serviceId, Class<T> serviceClass) {
        Service service = services.get(serviceId);
        if (service == null) {
            return null;
        }
        
        if (serviceClass.isAssignableFrom(service.getClass())) {
            return serviceClass.cast(service);
        }
        
        return null;
    }
    
    /**
     * Get a service by ID
     */
    public Service getService(String serviceId) {
        return services.get(serviceId);
    }
    
    /**
     * Check if a service is registered
     */
    public boolean isServiceRegistered(String serviceId) {
        return services.containsKey(serviceId);
    }
    
    /**
     * Get all registered service IDs
     */
    public Set<String> getRegisteredServiceIds() {
        return new HashSet<>(services.keySet());
    }
    
    /**
     * Get all services of a specific type
     */
    public <T extends Service> List<T> getServicesOfType(Class<T> serviceClass) {
        return services.values().stream()
                .filter(service -> serviceClass.isAssignableFrom(service.getClass()))
                .map(serviceClass::cast)
                .collect(Collectors.toList());
    }
    
    /**
     * Initialize all services
     */
    public void initializeServices() throws ServiceException {
        if (isInitialized) {
            logger.warning("Services already initialized");
            return;
        }
        
        logger.info("Initializing services...");
        
        // Get initialization order
        List<String> initOrder = calculateInitializationOrder();
        
        // Initialize services in dependency order
        for (String serviceId : initOrder) {
            Service service = services.get(serviceId);
            if (service == null) continue;
            
            try {
                logger.info("Initializing service: " + serviceId);
                
                // Get dependencies
                Map<String, Service> dependencies = new HashMap<>();
                List<String> deps = service.getDependencies();
                if (deps != null) {
                    for (String depId : deps) {
                        Service depService = services.get(depId);
                        if (depService == null) {
                            throw new ServiceException(serviceId, ServiceException.ErrorCode.DEPENDENCY_NOT_FOUND, 
                                    "Dependency service not found: " + depId);
                        }
                        dependencies.put(depId, depService);
                    }
                }
                
                // Initialize service
                service.initialize(dependencies);
                
                logger.info("Service initialized: " + serviceId);
                
                // Notify listeners
                for (ServiceLifecycleListener listener : listeners) {
                    try {
                        listener.onServiceInitialized(serviceId, service);
                    } catch (Exception e) {
                        logger.warning("Error notifying listener of service initialization: " + e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                logger.severe("Failed to initialize service: " + serviceId + " - " + e.getMessage());
                
                // Notify listeners
                for (ServiceLifecycleListener listener : listeners) {
                    try {
                        listener.onServiceFailed(serviceId, service, e);
                    } catch (Exception ex) {
                        logger.warning("Error notifying listener of service failure: " + ex.getMessage());
                    }
                }
                
                throw new ServiceException(serviceId, ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                        "Service initialization failed", e);
            }
        }
        
        isInitialized = true;
        logger.info("All services initialized successfully");
    }
    
    /**
     * Start all services
     */
    public void startServices() throws ServiceException {
        if (!isInitialized) {
            throw new ServiceException("ServiceManager", ServiceException.ErrorCode.INITIALIZATION_FAILED, 
                    "Services must be initialized before starting");
        }
        
        if (isStarted) {
            logger.warning("Services already started");
            return;
        }
        
        logger.info("Starting services...");
        
        // Get startup order (same as initialization order)
        List<String> startOrder = calculateInitializationOrder();
        
        // Start services in dependency order
        for (String serviceId : startOrder) {
            Service service = services.get(serviceId);
            if (service == null || !service.isEnabled()) continue;
            
            try {
                logger.info("Starting service: " + serviceId);
                
                service.start();
                
                logger.info("Service started: " + serviceId);
                
                // Notify listeners
                for (ServiceLifecycleListener listener : listeners) {
                    try {
                        listener.onServiceStarted(serviceId, service);
                    } catch (Exception e) {
                        logger.warning("Error notifying listener of service start: " + e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                logger.severe("Failed to start service: " + serviceId + " - " + e.getMessage());
                
                // Notify listeners
                for (ServiceLifecycleListener listener : listeners) {
                    try {
                        listener.onServiceFailed(serviceId, service, e);
                    } catch (Exception ex) {
                        logger.warning("Error notifying listener of service failure: " + ex.getMessage());
                    }
                }
                
                throw new ServiceException(serviceId, ServiceException.ErrorCode.STARTUP_FAILED, 
                        "Service startup failed", e);
            }
        }
        
        isStarted = true;
        
        // Start health monitoring
        startHealthMonitoring();
        
        logger.info("All services started successfully");
    }
    
    /**
     * Stop all services
     */
    public void stopServices() {
        if (!isStarted) {
            logger.warning("Services not started");
            return;
        }
        
        isShuttingDown = true;
        
        logger.info("Stopping services...");
        
        // Stop health monitoring
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }
        
        // Get shutdown order (reverse of initialization order)
        try {
            List<String> shutdownOrder = calculateInitializationOrder();
            Collections.reverse(shutdownOrder);
            
            // Stop services in reverse dependency order
            for (String serviceId : shutdownOrder) {
                Service service = services.get(serviceId);
                if (service == null) continue;
                
                try {
                    logger.info("Stopping service: " + serviceId);
                    
                    service.stop();
                    
                    logger.info("Service stopped: " + serviceId);
                    
                    // Notify listeners
                    for (ServiceLifecycleListener listener : listeners) {
                        try {
                            listener.onServiceStopped(serviceId, service);
                        } catch (Exception e) {
                            logger.warning("Error notifying listener of service stop: " + e.getMessage());
                        }
                    }
                    
                } catch (Exception e) {
                    logger.warning("Error stopping service: " + serviceId + " - " + e.getMessage());
                }
            }
        } catch (ServiceException e) {
            logger.warning("Error calculating shutdown order: " + e.getMessage());
        }
        
        isStarted = false;
        isShuttingDown = false;
        
        logger.info("All services stopped");
    }
    
    /**
     * Calculate service initialization order based on dependencies
     */
    private List<String> calculateInitializationOrder() throws ServiceException {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        // Perform topological sort using DFS
        for (String serviceId : services.keySet()) {
            if (!visited.contains(serviceId)) {
                calculateInitializationOrderRecursive(serviceId, visited, visiting, order);
            }
        }
        
        // Sort by priority within dependency constraints
        return order.stream()
                .sorted((a, b) -> {
                    Service serviceA = services.get(a);
                    Service serviceB = services.get(b);
                    
                    if (serviceA == null || serviceB == null) return 0;
                    
                    return Integer.compare(serviceA.getPriority().getLevel(), serviceB.getPriority().getLevel());
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Recursive helper for topological sort
     */
    private void calculateInitializationOrderRecursive(String serviceId, Set<String> visited, Set<String> visiting, List<String> order) throws ServiceException {
        if (visiting.contains(serviceId)) {
            throw new ServiceException(serviceId, ServiceException.ErrorCode.CIRCULAR_DEPENDENCY, 
                    "Circular dependency detected involving service: " + serviceId);
        }
        
        if (visited.contains(serviceId)) {
            return;
        }
        
        visiting.add(serviceId);
        
        Service service = services.get(serviceId);
        if (service != null && service.getDependencies() != null) {
            for (String depId : service.getDependencies()) {
                if (!services.containsKey(depId)) {
                    throw new ServiceException(serviceId, ServiceException.ErrorCode.DEPENDENCY_NOT_FOUND, 
                            "Dependency service not found: " + depId);
                }
                calculateInitializationOrderRecursive(depId, visited, visiting, order);
            }
        }
        
        visiting.remove(serviceId);
        visited.add(serviceId);
        order.add(serviceId);
    }
    
    /**
     * Start health monitoring task
     */
    private void startHealthMonitoring() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
        }
        
        healthCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                logger.warning("Error during health check: " + e.getMessage());
            }
        }, 200L, 1200L); // 10 seconds initial delay, 60 seconds interval
        
        logger.info("Health monitoring started");
    }
    
    /**
     * Perform health check on all services
     */
    private void performHealthCheck() {
        for (Map.Entry<String, Service> entry : services.entrySet()) {
            String serviceId = entry.getKey();
            Service service = entry.getValue();
            
            try {
                ServiceHealth health = service.getHealth();
                healthCache.put(serviceId, health);
                
                if (!health.isHealthy()) {
                    logger.warning("Service health issue: " + serviceId + " - " + health.getMessage());
                }
            } catch (Exception e) {
                ServiceHealth unhealthy = ServiceHealth.unhealthy("Health check failed: " + e.getMessage());
                healthCache.put(serviceId, unhealthy);
                logger.warning("Health check failed for service: " + serviceId + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * Get health status for a service
     */
    public ServiceHealth getServiceHealth(String serviceId) {
        return healthCache.get(serviceId);
    }
    
    /**
     * Get health status for all services
     */
    public Map<String, ServiceHealth> getAllServiceHealth() {
        return new HashMap<>(healthCache);
    }
    
    /**
     * Add a lifecycle listener
     */
    public void addLifecycleListener(ServiceLifecycleListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a lifecycle listener
     */
    public void removeLifecycleListener(ServiceLifecycleListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Get service statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalServices", services.size());
        stats.put("initializedServices", isInitialized ? services.size() : 0);
        stats.put("startedServices", isStarted ? services.size() : 0);
        
        // Count by priority
        Map<Service.Priority, Long> priorityCounts = services.values().stream()
                .collect(Collectors.groupingBy(Service::getPriority, Collectors.counting()));
        stats.put("servicesByPriority", priorityCounts);
        
        // Count by state
        Map<Service.State, Long> stateCounts = services.values().stream()
                .collect(Collectors.groupingBy(Service::getState, Collectors.counting()));
        stats.put("servicesByState", stateCounts);
        
        // Health summary
        Map<ServiceHealth.Status, Long> healthCounts = healthCache.values().stream()
                .collect(Collectors.groupingBy(ServiceHealth::getStatus, Collectors.counting()));
        stats.put("servicesByHealth", healthCounts);
        
        return stats;
    }
    
    /**
     * Get service registrations
     */
    public Map<String, ServiceRegistration> getServiceRegistrations() {
        return new HashMap<>(registrations);
    }
    
    /**
     * Check if services are initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Check if services are started
     */
    public boolean isStarted() {
        return isStarted;
    }
    
    /**
     * Check if services are shutting down
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
    
    /**
     * Shutdown the service manager
     */
    public void shutdown() {
        stopServices();
        services.clear();
        registrations.clear();
        healthCache.clear();
        listeners.clear();
        
        isInitialized = false;
        logger.info("ServiceManager shutdown completed");
    }
} 