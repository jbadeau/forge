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
 * Maven build task - compile and package code (runs mvn package)
 */
@Task(
    name = MavenTaskNames.BUILD,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.BUILD
)
fun createMavenBuildTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.BUILD, TargetLifecycle.Build(BuildLifecyclePhase.BUILD)) {
        description("Compile and package code")
        command(MavenCommandBuilder.build().inProject(projectPath).withPhase("package").toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "src/main/**")
        outputs = listOf("target/classes/**", "target/*.jar", "target/*.war")
        options = mapOf(
            "phase" to "package"
        )
        cacheable(true) // Build is cacheable
    }
}