package com.frontseat.springboot.plugin

import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.plugin.ProjectPlugin
import com.frontseat.project.Project
import com.frontseat.workspace.Workspace
import java.nio.file.Path

/**
 * Spring Boot project plugin that provides both nature and project facilities
 */
class SpringBootProjectPlugin : FrontseatPlugin, ProjectPlugin {
    
    override val id = "spring-boot"
    override val name = "Spring Boot Plugin"
    override val version = "1.0.0"
    
    override fun initialize(workspace: Workspace) {
        // Register Spring Boot nature
        // Nature registration will be handled by service loader
    }
    
    override fun discover(workspace: Workspace, projectPath: Path): Project? {
        // Spring Boot is not a primary project type - it's an additional nature
        // that enhances Maven or Gradle projects. So we don't discover projects here.
        // The Spring Boot nature will be applied on top of Maven/Gradle projects.
        return null
    }
    
    override fun shutdown() {
        // Cleanup if needed
    }
}