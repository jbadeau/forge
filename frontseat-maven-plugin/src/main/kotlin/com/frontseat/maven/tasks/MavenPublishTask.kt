package com.frontseat.maven.tasks

import com.frontseat.annotation.Task
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
@Task(
    name = MavenTaskNames.PUBLISH,
    lifecycle = TargetLifecycle.Release::class,
    phase = ReleaseLifecyclePhase.PUBLISH
)
fun createMavenPublishTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.PUBLISH, TargetLifecycle.Release(ReleaseLifecyclePhase.PUBLISH)) {
        description("Deploy to repository")
        command(MavenCommandBuilder.build().inProject(projectPath).withPhase("deploy").toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "target/*.jar", "target/*.war")
        outputs = listOf() // Publishing doesn't produce local outputs
        options = mapOf(
            "phase" to "deploy"
        )
        cacheable(false) // Publishing is not cacheable
    }
}