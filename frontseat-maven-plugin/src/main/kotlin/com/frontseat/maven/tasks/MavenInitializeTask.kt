package com.frontseat.maven.tasks

import com.frontseat.annotation.Task
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import com.frontseat.maven.commons.MavenCommandBuilder
import com.frontseat.maven.commons.MavenTaskNames
import com.frontseat.nature.TargetLifecycle
import com.frontseat.nature.BuildLifecyclePhase
import java.nio.file.Path

/**
 * Maven initialize task - initialize the project (clean)
 */
@Task(
    name = MavenTaskNames.INITIALIZE,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.INITIALIZE
)
fun createMavenInitializeTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.INITIALIZE, TargetLifecycle.Build(BuildLifecyclePhase.INITIALIZE)) {
        description("Initialize the project (clean)")
        command(MavenCommandBuilder.build().inProject(projectPath).withPhase("clean").toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml")
        outputs = listOf("target/") // Clean affects target directory
        options = mapOf(
            "phase" to "clean"
        )
    }
}