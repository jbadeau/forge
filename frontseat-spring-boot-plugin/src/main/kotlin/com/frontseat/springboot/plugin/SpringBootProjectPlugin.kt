package com.frontseat.springboot.plugin

import com.frontseat.nature.NatureRegistry
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.workspace.Workspace

/**
 * Spring Boot plugin that contributes the Spring Boot nature
 */
class SpringBootProjectPlugin : FrontseatPlugin {
    
    override val id = "spring-boot"
    override val name = "Spring Boot Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Spring Boot nature with the nature registry
        val natureRegistry = NatureRegistry.instance
        natureRegistry.register(SpringBootNature())
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}