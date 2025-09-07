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
 * Maven package task - package compiled code
 */
@Task(
    name = MavenTaskNames.PACKAGE,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.PACKAGE
)
fun createMavenPackageTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.PACKAGE, TargetLifecycle.Build(BuildLifecyclePhase.PACKAGE)) {
        description("Package compiled code")
        command(MavenCommandBuilder.build().inProject(projectPath).withPhase("package").toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "target/classes/**")
        outputs = listOf("target/*.jar", "target/*.war")
        options = mapOf(
            "phase" to "package"
        )
        cacheable(true) // Packaging is cacheable
    }
}