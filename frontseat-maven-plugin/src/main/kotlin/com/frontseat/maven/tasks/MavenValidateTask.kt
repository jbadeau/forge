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
 * Maven validate task - validates project structure and dependencies
 */
@Task(
    name = MavenTaskNames.VALIDATE,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.VALIDATE
)
fun createMavenValidateTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.VALIDATE, TargetLifecycle.Build(BuildLifecyclePhase.VALIDATE)) {
        description("Validate the project structure and dependencies")
        command(MavenCommandBuilder.build().inProject(projectPath).withPhase("validate").toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "src/**")
        outputs = listOf() // Validation doesn't produce outputs
        options = mapOf(
            "phase" to "validate"
        )
    }
}