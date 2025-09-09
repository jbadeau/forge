package com.frontseat.maven.tasks

import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import com.frontseat.maven.commons.MavenCommandBuilder
import com.frontseat.maven.commons.MavenTaskNames
import com.frontseat.nature.TargetLifecycle
import com.frontseat.nature.ReleaseLifecyclePhase
import java.nio.file.Path

/**
 * Maven publish task - deploy to repository
 */
fun createMavenPublishTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.PUBLISH, TargetLifecycle.Release(ReleaseLifecyclePhase.PUBLISH)) {
        description("Deploy to repository")
        
        // Build only this project (-pl .) for parallelization
        val projectName = projectPath.fileName.toString()
        command(MavenCommandBuilder.build()
            .inProject(projectPath.parent) // Run from parent to use -pl
            .withArg("-pl")
            .withArg(projectName)
            .withPhase("deploy")
            .toCommandString())
        workingDirectory(projectPath.parent)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "target/*.jar", "target/*.war")
        outputs = listOf() // Publishing doesn't produce local outputs
        options = mapOf(
            "phase" to "deploy",
            "project" to projectName
        )
        cacheable(false) // Publishing is not cacheable
    }
}