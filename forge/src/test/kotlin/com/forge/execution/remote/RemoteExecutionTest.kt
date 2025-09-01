package com.forge.execution.remote

import com.forge.core.ProjectConfiguration
import com.forge.core.RemoteExecutionTargetConfig
import com.forge.core.TargetConfiguration
import com.forge.core.WorkspaceConfiguration
import com.forge.graph.Task
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemoteExecutionTest {
    
    @Test
    fun `test remote execution configuration creation`() {
        // Create workspace config with remote execution enabled
        val workspaceConfig = WorkspaceConfiguration(
            remoteExecution = com.forge.core.RemoteExecutionWorkspaceConfig(
                enabled = true,
                defaultEndpoint = "build-cluster.example.com:443",
                defaultInstanceName = "default",
                useTls = true,
                maxConnections = 50,
                defaultTimeoutSeconds = 600,
                defaultPlatform = mapOf("OS" to "linux", "cpu" to "x86_64")
            )
        )
        
        // Create target config with remote execution override
        val targetConfig = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf("commands" to listOf("npm run build")),
            cache = true,
            remoteExecution = RemoteExecutionTargetConfig(
                endpoint = "localhost:8080",
                timeoutSeconds = 300,
                platform = mapOf("nodeVersion" to "18")
            )
        )
        
        // Get merged remote execution config
        val remoteConfig = workspaceConfig.getRemoteExecutionConfig(targetConfig)
        
        assertNotNull(remoteConfig)
        assertEquals("localhost:8080", remoteConfig.endpoint) // Target override
        assertEquals("default", remoteConfig.instanceName) // From workspace
        assertEquals(300, remoteConfig.timeoutSeconds) // Target override
        assertEquals(true, remoteConfig.useTls) // From workspace
        assertEquals(50, remoteConfig.maxConnections) // From workspace
        
        // Platform should merge workspace and target
        val expectedPlatform = mapOf(
            "OS" to "linux", 
            "cpu" to "x86_64", 
            "nodeVersion" to "18"
        )
        assertEquals(expectedPlatform, remoteConfig.platform)
    }
    
    @Test
    fun `test remote execution disabled by target`() {
        val workspaceConfig = WorkspaceConfiguration(
            remoteExecution = com.forge.core.RemoteExecutionWorkspaceConfig(
                enabled = true,
                defaultEndpoint = "build-cluster.example.com:443"
            )
        )
        
        val targetConfig = TargetConfiguration(
            remoteExecution = RemoteExecutionTargetConfig(enabled = false)
        )
        
        val remoteConfig = workspaceConfig.getRemoteExecutionConfig(targetConfig)
        
        assertNull(remoteConfig, "Remote execution should be disabled when target explicitly disables it")
    }
    
    @Test
    fun `test remote execution builder creates action`() {
        val workspaceRoot = Path.of("/tmp/test-workspace")
        val builder = RemoteExecutionBuilder(workspaceRoot, "test-instance")
        
        val task = Task(
            id = "test-project:build",
            projectName = "test-project",
            targetName = "build",
            target = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf("npm run build"),
                    "cwd" to "apps/web-app"
                ),
                inputs = listOf("src/**/*", "package.json"),
                outputs = listOf("dist/"),
                cache = true
            )
        )
        
        // Build the action
        val action = builder.buildAction(task, "apps/web-app")
        
        assertNotNull(action)
        assertNotNull(action.commandDigest)
        assertNotNull(action.inputRootDigest)
        assertEquals(false, action.doNotCache) // Should be false since cache=true
        assertEquals(300, action.timeout.seconds) // Default timeout
    }
    
    @Test
    fun `test remote execution builder creates command`() {
        val workspaceRoot = Path.of("/tmp/test-workspace")
        val builder = RemoteExecutionBuilder(workspaceRoot, "test-instance")
        
        val task = Task(
            id = "test-project:test",
            projectName = "test-project", 
            targetName = "test",
            target = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf("mvn test", "mvn verify"),
                    "env" to mapOf("NODE_ENV" to "test", "CI" to "true")
                ),
                outputs = listOf("target/test-reports/")
            )
        )
        
        // Build the command
        val command = builder.buildCommand(task, "services/auth-service")
        
        assertNotNull(command)
        assertEquals(3, command.argumentsList.size) // sh, -c, "mvn test && mvn verify"
        assertEquals("sh", command.argumentsList[0])
        assertEquals("-c", command.argumentsList[1])
        assertEquals("mvn test && mvn verify", command.argumentsList[2])
        
        // Check environment variables
        val envVars = command.environmentVariablesList
        assertEquals(2, envVars.size)
        assertEquals("NODE_ENV", envVars[0].name)
        assertEquals("test", envVars[0].value)
        assertEquals("CI", envVars[1].name)
        assertEquals("true", envVars[1].value)
        
        // Check output paths
        assertEquals(1, command.outputPathsList.size)
        assertEquals("target/test-reports/", command.outputPathsList[0])
    }
    
    @Test
    fun `test remote execution service factory creates services`() {
        val config = RemoteExecutionConfig(
            endpoint = "localhost:8080",
            instanceName = "test",
            useTls = false,
            maxConnections = 10,
            timeoutSeconds = 120
        )
        
        val services = RemoteExecutionServiceFactory.create(config)
        
        assertNotNull(services)
        assertNotNull(services.execution)
        assertNotNull(services.cas)
        assertNotNull(services.actionCache)
        
        // Clean up
        services.close()
    }
}