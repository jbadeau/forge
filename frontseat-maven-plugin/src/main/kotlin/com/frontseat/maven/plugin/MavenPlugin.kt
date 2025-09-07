package com.frontseat.maven.plugin

import com.frontseat.nature.NatureRegistry
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.workspace.Workspace

/**
 * Maven plugin that contributes the Maven nature
 */
class MavenPlugin : FrontseatPlugin {
    
    override val id = "maven"
    override val name = "Maven Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Maven nature with the nature registry
        val natureRegistry = workspace.getNatureRegistry()
        natureRegistry.register(MavenNature())
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}