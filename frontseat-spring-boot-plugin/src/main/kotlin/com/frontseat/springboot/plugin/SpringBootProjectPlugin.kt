package com.frontseat.springboot.plugin

import com.frontseat.nature.NatureRegistry
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.plugin.ProjectPlugin
import com.frontseat.project.Project
import com.frontseat.workspace.Workspace
import java.nio.file.Path

/**
 * Spring Boot project plugin that contributes the Spring Boot nature
 */
class SpringBootProjectPlugin : FrontseatPlugin, ProjectPlugin {
    
    override val id = "spring-boot"
    override val name = "Spring Boot Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Spring Boot nature with the nature registry
        val natureRegistry = NatureRegistry.instance
        natureRegistry.register(SpringBootNature())
    }
    
    override fun discover(workspace: Workspace, projectPath: Path): Project? {
        // Project discovery is handled by the nature-based ProjectInferenceEngine
        // This plugin just contributes the Spring Boot nature capability
        return null
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}