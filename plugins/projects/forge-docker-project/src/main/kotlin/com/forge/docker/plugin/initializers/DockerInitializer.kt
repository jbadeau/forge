package com.forge.docker.plugin.initializers

import com.forge.plugin.api.*
import org.slf4j.LoggerFactory

/**
 * Docker plugin initializer that runs once when "forge install forge-docker-project" is executed.
 * Adds the plugin to forge.json/nx.json plugins section.
 */
class DockerInitializer : Initializer {
    
    private val logger = LoggerFactory.getLogger(DockerInitializer::class.java)
    
    override val metadata = InitializerMetadata(
        id = "com.forge.docker.initializer",
        name = "Docker Plugin Initializer",
        version = "1.0.0",
        description = "Initialize Docker plugin support in the workspace"
    )
    
    override suspend fun initialize(
        workspace: WorkspaceContext,
        options: Map<String, Any>
    ): InitializerResult {
        
        logger.warn("Docker support is experimental. Breaking changes may occur.")
        
        val skipFormat = options["skipFormat"] as? Boolean ?: false
        
        // Add plugin to workspace configuration using workspace API
        val pluginAdded = workspace.addPlugin(
            pluginName = "@forge/docker-project",
            options = mapOf(
                "buildTarget" to listOf("docker:build", "docker-build", "build-docker"),
                "runTarget" to listOf("docker:run", "docker-run", "run-docker"),
                "pushTarget" to listOf("docker:push", "docker-push", "push-docker")
            )
        )
        
        // Format files if not skipping
        if (!skipFormat) {
            workspace.formatFiles()
        }
        
        val messages = if (pluginAdded) {
            listOf("✅ Docker plugin installed successfully")
        } else {
            listOf("ℹ️ Docker plugin already installed")
        }
        
        return InitializerResult(
            success = true,
            messages = messages,
            pluginInstalled = pluginAdded
        )
    }
}