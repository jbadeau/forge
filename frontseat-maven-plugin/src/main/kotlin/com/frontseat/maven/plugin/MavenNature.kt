package com.frontseat.maven.plugin

import com.frontseat.annotation.AutoRegister
import com.frontseat.annotation.Nature
import com.frontseat.maven.commons.MavenCommandBuilder
import com.frontseat.maven.commons.MavenUtils
import com.frontseat.maven.commons.MavenNatureIds
import com.frontseat.maven.commons.MavenTaskNames
import com.frontseat.nature.*
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
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
        val tasks = mutableMapOf<String, CommandTask>()
        
        // Build lifecycle tasks - using official lifecycle phase names
        tasks[MavenTaskNames.VALIDATE] = commandTask(MavenTaskNames.VALIDATE, TargetLifecycle.Build(BuildLifecyclePhase.VALIDATE)) {
            description("Validate the project structure and dependencies")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("validate").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks[MavenTaskNames.INITIALIZE] = commandTask(MavenTaskNames.INITIALIZE, TargetLifecycle.Build(BuildLifecyclePhase.INITIALIZE)) {
            description("Initialize the project (clean)")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("clean").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks[MavenTaskNames.COMPILE] = commandTask(MavenTaskNames.COMPILE, TargetLifecycle.Build(BuildLifecyclePhase.COMPILE)) {
            description("Compile source code")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("compile").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks[MavenTaskNames.TEST] = commandTask(MavenTaskNames.TEST, TargetLifecycle.Build(BuildLifecyclePhase.TEST)) {
            description("Run tests")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("test").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks[MavenTaskNames.PACKAGE] = commandTask(MavenTaskNames.PACKAGE, TargetLifecycle.Build(BuildLifecyclePhase.BUNDLE)) {
            description("Package compiled code")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("package").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks[MavenTaskNames.VERIFY] = commandTask(MavenTaskNames.VERIFY, TargetLifecycle.Build(BuildLifecyclePhase.VERIFY)) {
            description("Verify package integrity")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("verify").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks[MavenTaskNames.PUBLISH] = commandTask(MavenTaskNames.PUBLISH, TargetLifecycle.Release(ReleaseLifecyclePhase.PUBLISH)) {
            description("Deploy to repository")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("deploy").toCommandString())
            workingDirectory(projectPath)
            cacheable(false) // Publishing is not cacheable
        }
        
        return tasks
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        // Maven dependency analysis could be implemented here
        // For now, return empty list
        return emptyList()
    }
}