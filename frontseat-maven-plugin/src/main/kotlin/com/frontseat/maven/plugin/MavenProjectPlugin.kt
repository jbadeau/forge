package com.frontseat.maven.plugin

import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.plugin.ProjectPlugin
import com.frontseat.project.Project
import com.frontseat.workspace.Workspace
import java.nio.file.Path

/**
 * Maven project plugin that provides both nature and project facilities
 */
class MavenProjectPlugin : FrontseatPlugin, ProjectPlugin {
    
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