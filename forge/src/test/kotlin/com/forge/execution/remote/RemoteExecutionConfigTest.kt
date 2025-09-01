package com.forge.execution.remote

import org.junit.jupiter.api.Test

class RemoteExecutionConfigTest {
    
    @Test
    fun `test RemoteExecutionServiceFactory channel creation`() {
        val config = RemoteExecutionConfig(
            endpoint = "127.0.0.1:8980",
            instanceName = "default", 
            useTls = false,
            maxConnections = 10,
            timeoutSeconds = 300,
            platform = emptyMap()
        )
        
        println("Testing RemoteExecutionServiceFactory with config: $config")
        
        try {
            val services = RemoteExecutionServiceFactory.create(config)
            println("Successfully created services")
            
            // Try to close services
            services.close()
            println("Successfully closed services")
            
        } catch (e: Exception) {
            println("Failed to create services: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
}