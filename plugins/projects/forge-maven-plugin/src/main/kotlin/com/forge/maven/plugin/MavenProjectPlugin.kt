package com.forge.maven.plugin

import com.forge.plugin.ForgePlugin
import com.forge.plugin.ProjectPlugin
import com.forge.project.Project
import com.forge.workspace.Workspace
import java.nio.file.Path

/**
 * Maven project plugin that provides both nature and project facilities
 */
class MavenProjectPlugin : ForgePlugin, ProjectPlugin {
    
    override val id = "maven"
    override val name = "Maven Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Maven nature
        // Nature registration will be handled by service loader
    }
    
    override fun discover(workspace: Workspace, projectPath: Path): Project? {
        if (!MavenUtils.isMavenProject(projectPath)) {
            return null
        }
        
        val inference = MavenProjectInference()
        return inference.inferProject(workspace, projectPath)
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}