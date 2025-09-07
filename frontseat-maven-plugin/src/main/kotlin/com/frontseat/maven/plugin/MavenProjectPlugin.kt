package com.frontseat.maven.plugin

import com.frontseat.nature.NatureRegistry
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.plugin.ProjectPlugin
import com.frontseat.project.Project
import com.frontseat.workspace.Workspace
import java.nio.file.Path

/**
 * Maven project plugin that contributes the Maven nature
 */
class MavenProjectPlugin : FrontseatPlugin, ProjectPlugin {
    
    override val id = "maven"
    override val name = "Maven Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Maven nature with the nature registry
        val natureRegistry = NatureRegistry.instance
        natureRegistry.register(MavenNature())
    }
    
    override fun discover(workspace: Workspace, projectPath: Path): Project? {
        // Project discovery is handled by the nature-based ProjectInferenceEngine
        // This plugin just contributes the Maven nature capability
        return null
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}