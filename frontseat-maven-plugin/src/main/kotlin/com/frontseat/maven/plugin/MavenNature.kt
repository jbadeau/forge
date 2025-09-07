package com.frontseat.maven.plugin

import com.frontseat.annotation.AutoRegister
import com.frontseat.annotation.Nature
import com.frontseat.maven.commons.MavenUtils
import com.frontseat.maven.commons.MavenNatureIds
import com.frontseat.maven.commons.MavenTaskNames
import com.frontseat.maven.tasks.*
import com.frontseat.nature.*
import com.frontseat.command.CommandTask
import java.nio.file.Path

/**
 * Maven project nature that provides Maven build system capabilities
 */
@Nature(id = MavenNatureIds.MAVEN, layer = NatureLayers.BUILD_SYSTEMS)
@AutoRegister
class MavenNature : ProjectNature {
    // No need to override id or layer - they come from @Nature annotation!
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        return MavenUtils.isMavenProject(projectPath)
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        return mapOf(
            MavenTaskNames.VALIDATE to createMavenValidateTask(projectPath),
            MavenTaskNames.INITIALIZE to createMavenInitializeTask(projectPath),
            MavenTaskNames.COMPILE to createMavenCompileTask(projectPath),
            MavenTaskNames.TEST to createMavenTestTask(projectPath),
            MavenTaskNames.PACKAGE to createMavenPackageTask(projectPath),
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