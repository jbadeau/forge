package com.forge.gradle.plugin

import com.forge.plugin.ForgePlugin
import com.forge.plugin.ProjectPlugin
import com.forge.project.Project
import com.forge.project.ProjectConfig
import com.forge.workspace.Workspace
import java.nio.file.Path

/**
 * Gradle project plugin that provides both nature and project facilities
 */
class GradleProjectPlugin : ForgePlugin, ProjectPlugin {
    
    override val id = "gradle"
    override val name = "Gradle Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Gradle nature
        // Nature registration will be handled by service loader
    }
    
    override fun discover(workspace: Workspace, projectPath: Path): Project? {
        if (!GradleUtils.isGradleProject(projectPath)) {
            return null
        }
        
        // Create a simple Gradle project configuration
        val projectName = projectPath.fileName.toString()
        val config = ProjectConfig(
            name = projectName,
            root = projectPath.toString(),
            projectType = "gradle",
            tags = setOf("gradle", "jvm")
        )
        
        return Project(workspace, config)
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}