package com.talexck.gameVoting.api.cloudnet;

import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.provider.CloudServiceFactory;
import eu.cloudnetservice.driver.provider.ServiceTaskProvider;
import eu.cloudnetservice.driver.provider.GroupConfigurationProvider;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.driver.service.ServiceTask;
import eu.cloudnetservice.driver.service.GroupConfiguration;
import eu.cloudnetservice.driver.service.ServiceCreateResult;
import eu.cloudnetservice.driver.service.ServiceConfiguration;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * CloudNet v4 API Wrapper using Driver API
 * This replaces the old REST API approach with native CloudNet v4 Driver API
 */
public class CloudNetAPI {
    private static CloudNetAPI instance;
    
    private final CloudServiceProvider serviceProvider;
    private final CloudServiceFactory serviceFactory;
    private final ServiceTaskProvider taskProvider;
    private final GroupConfigurationProvider groupProvider;

    private CloudNetAPI() {
        // Get providers from CloudNet's dependency injection layer
        this.serviceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
        this.serviceFactory = InjectionLayer.ext().instance(CloudServiceFactory.class);
        this.taskProvider = InjectionLayer.ext().instance(ServiceTaskProvider.class);
        this.groupProvider = InjectionLayer.ext().instance(GroupConfigurationProvider.class);
    }

    public static void initialize() {
        if (instance == null) {
            instance = new CloudNetAPI();
        }
    }

    public static CloudNetAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CloudNetAPI not initialized!");
        }
        return instance;
    }

    // === Service Management ===

    /**
     * Get all running services
     */
    public Collection<ServiceInfoSnapshot> getServices() {
        return serviceProvider.services();
    }

    /**
     * Get services filtered by task
     */
    public Collection<ServiceInfoSnapshot> getServicesByTask(String taskName) {
        return serviceProvider.servicesByTask(taskName);
    }

    /**
     * Get services filtered by group
     */
    public Collection<ServiceInfoSnapshot> getServicesByGroup(String groupName) {
        return serviceProvider.servicesByGroup(groupName);
    }

    /**
     * Get a specific service by unique id
     */
    public Optional<ServiceInfoSnapshot> getService(UUID uniqueId) {
        return Optional.ofNullable(serviceProvider.service(uniqueId));
    }

    /**
     * Get a specific service by name
     */
    public Optional<ServiceInfoSnapshot> getServiceByName(String name) {
        return serviceProvider.services().stream()
            .filter(service -> service.name().equalsIgnoreCase(name))
            .findFirst();
    }

    /**
     * Create and start a single service from a task
     * Equivalent to CloudNet command: create by <task_name> 1
     * 
     * @param taskName The name of the task
     * @return The created service result
     */
    public ServiceCreateResult createService(String taskName) {
        ServiceTask task = taskProvider.serviceTask(taskName);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskName);
        }
        
        // Build service configuration based on the task
        // This properly inherits all task settings including environment
        ServiceConfiguration.Builder builder = ServiceConfiguration.builder(task);
        
        // Ensure auto-delete is enabled
        builder.autoDeleteOnStop(true);
        
        ServiceConfiguration config = builder.build();
        
        // Create the service
        return serviceFactory.createCloudService(config);
    }

    /**
     * Start a service
     */
    public void startService(UUID uniqueId) {
        serviceProvider.serviceProvider(uniqueId).start();
    }

    /**
     * Stop a service
     */
    public void stopService(UUID uniqueId) {
        serviceProvider.serviceProvider(uniqueId).stop();
    }

    /**
     * Delete a service
     */
    public void deleteService(UUID uniqueId) {
        serviceProvider.serviceProvider(uniqueId).delete();
    }

    // === Task Management ===

    /**
     * Get all service tasks
     */
    public Collection<ServiceTask> getTasks() {
        return taskProvider.serviceTasks();
    }

    /**
     * Get a specific task by name
     */
    public Optional<ServiceTask> getTask(String name) {
        return Optional.ofNullable(taskProvider.serviceTask(name));
    }

    /**
     * Create or update a task
     */
    public void addTask(ServiceTask task) {
        taskProvider.addServiceTask(task);
    }

    /**
     * Delete a task
     */
    public void removeTask(String name) {
        taskProvider.removeServiceTaskByName(name);
    }

    // === Group Management ===

    /**
     * Get all group configurations
     */
    public Collection<GroupConfiguration> getGroups() {
        return groupProvider.groupConfigurations();
    }

    /**
     * Get a specific group by name
     */
    public Optional<GroupConfiguration> getGroup(String name) {
        return Optional.ofNullable(groupProvider.groupConfiguration(name));
    }

    /**
     * Create or update a group
     */
    public void addGroup(GroupConfiguration group) {
        groupProvider.addGroupConfiguration(group);
    }

    /**
     * Delete a group
     */
    public void removeGroup(String name) {
        groupProvider.removeGroupConfigurationByName(name);
    }

    /**
     * Get the count of services in a specific state for a task
     */
    public int getServiceCount(String taskName) {
        return serviceProvider.servicesByTask(taskName).size();
    }

    /**
     * Check if a service exists
     */
    public boolean serviceExists(UUID uniqueId) {
        return serviceProvider.service(uniqueId) != null;
    }
    
    /**
     * Execute a command on a specific service.
     * 
     * @param serviceName The name of the service
     * @param command The command to execute
     */
    public void executeServiceCommand(String serviceName, String command) {
        Optional<ServiceInfoSnapshot> serviceOpt = getServiceByName(serviceName);
        if (serviceOpt.isEmpty()) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }
        
        UUID serviceId = serviceOpt.get().serviceId().uniqueId();
        serviceProvider.serviceProvider(serviceId).runCommand(command);
    }
}
