package com.forge.execution

import org.slf4j.LoggerFactory

/**
 * Factory for creating task executor plugins based on configuration
 */
class TaskExecutorPluginFactory {
    
    companion object {
        private val logger = LoggerFactory.getLogger(TaskExecutorPluginFactory::class.java)
        
        // Registry of available task executor plugins
        private val executors = mutableMapOf<String, () -> TaskExecutorPlugin>()
        
        init {
            // Register built-in executor plugins
            try {
                // Local executor - always available
                val localExecutorClass = Class.forName("com.forge.execution.local.LocalTaskExecutorPlugin")
                registerExecutor("com.forge.executor.LocalTaskExecutor") { 
                    localExecutorClass.getDeclaredConstructor().newInstance() as TaskExecutorPlugin
                }
            } catch (e: ClassNotFoundException) {
                logger.warn("Local executor plugin not available")
            }
            
            try {
                // Remote executor - only available if forge-remote-executor module is present
                val remoteExecutorClass = Class.forName("com.forge.execution.remote.RemoteTaskExecutorPlugin")
                registerExecutor("com.forge.executor.RemoteTaskExecutor") {
                    remoteExecutorClass.getDeclaredConstructor().newInstance() as TaskExecutorPlugin
                }
            } catch (e: ClassNotFoundException) {
                logger.info("Remote executor plugin not available - forge-remote-executor module not found")
            }
        }
        
        /**
         * Register a task executor plugin class
         */
        fun registerExecutor(executorId: String, factory: () -> TaskExecutorPlugin) {
            executors[executorId] = factory
            logger.debug("Registered task executor plugin: $executorId")
        }
        
        /**
         * Create a task executor plugin from configuration
         */
        fun createTaskExecutor(config: TaskExecutorConfig): TaskExecutorPlugin {
            val factory = executors[config.executor]
            if (factory == null) {
                logger.error("Unknown task executor plugin: ${config.executor}")
                throw IllegalArgumentException("Unknown task executor plugin: ${config.executor}")
            }
            
            val executor = factory()
            
            // Validate options before initialization
            if (!executor.validateOptions(config.options)) {
                throw IllegalArgumentException("Invalid options for task executor plugin: ${config.executor}")
            }
            
            // Initialize with options
            executor.initialize(config.options)
            
            logger.info("Created task executor plugin: ${config.executor}")
            return executor
        }
        
        /**
         * Create a task executor plugin with just the executor ID and default options
         */
        fun createTaskExecutor(executorId: String, options: Map<String, Any> = emptyMap()): TaskExecutorPlugin {
            return createTaskExecutor(TaskExecutorConfig(executorId, options))
        }
        
        /**
         * Get list of available executor plugins
         */
        fun getAvailableExecutors(): Set<String> = executors.keys.toSet()
        
        /**
         * Check if an executor plugin is available
         */
        fun isExecutorAvailable(executorId: String): Boolean = executors.containsKey(executorId)
    }
}