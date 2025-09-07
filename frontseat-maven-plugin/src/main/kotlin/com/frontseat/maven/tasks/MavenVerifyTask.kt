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
 * Maven verify task - verify package integrity
 */
@Task(
    name = MavenTaskNames.VERIFY,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.VERIFY
)
fun createMavenVerifyTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.VERIFY, TargetLifecycle.Build(BuildLifecyclePhase.VERIFY)) {
        description("Verify package integrity")
        command(MavenCommandBuilder.build().inProject(projectPath).withPhase("verify").toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "target/*.jar", "target/*.war")
        outputs = listOf("target/failsafe-reports/**")
        options = mapOf(
            "phase" to "verify"
        )
        cacheable(true) // Verification is cacheable
    }
}