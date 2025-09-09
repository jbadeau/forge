package com.frontseat.maven.plugin

import com.frontseat.project.nature.NatureInfo
import com.frontseat.maven.commons.MavenUtils
import com.frontseat.maven.commons.MavenNatureIds
import com.frontseat.maven.commons.MavenTaskNames
import com.frontseat.maven.tasks.*
import com.frontseat.project.nature.Nature
import com.frontseat.project.nature.NatureContext
import com.frontseat.project.nature.NatureLayers
import com.frontseat.task.CommandTask
import java.nio.file.Path

/**
 * Maven project nature that provides Maven build system capabilities
 */
@NatureInfo(id = MavenNatureIds.MAVEN, layer = NatureLayers.BUILD_SYSTEMS)
class MavenNature : Nature {
    // No need to override id or layer - they come from @NatureInfo annotation!
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        return MavenUtils.isMavenProject(projectPath)
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        val buildOptions = MavenBuildOptions.defaults(projectPath.fileName.toString())
        
        return mapOf(
            MavenTaskNames.BUILD to createMavenBuildTask(projectPath, buildOptions),
            MavenTaskNames.TEST to createMavenTestTask(projectPath),
            MavenTaskNames.VERIFY to createMavenVerifyTask(projectPath),
            MavenTaskNames.PUBLISH to createMavenPublishTask(projectPath)
        )
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        // Maven dependency analysis could be implemented here
        // For now, return empty list
        return emptyList()
    }
}