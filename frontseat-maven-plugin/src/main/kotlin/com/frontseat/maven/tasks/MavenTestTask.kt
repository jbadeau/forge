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
 * Maven test task - run tests
 */
@Task(
    name = MavenTaskNames.TEST,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.TEST
)
fun createMavenTestTask(projectPath: Path): CommandTask {
    return commandTask(MavenTaskNames.TEST, TargetLifecycle.Build(BuildLifecyclePhase.TEST)) {
        description("Run tests")
        
        // Build only this project (-pl .) for parallelization
        val projectName = projectPath.fileName.toString()
        command(MavenCommandBuilder.build()
            .inProject(projectPath.parent) // Run from parent to use -pl
            .withArg("-pl")
            .withArg(projectName)
            .withPhase("test")
            .toCommandString())
        workingDirectory(projectPath.parent)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "src/main/**", "src/test/**", "target/classes/**")
        outputs = listOf("target/test-classes/**", "target/surefire-reports/**")
        options = mapOf(
            "phase" to "test",
            "project" to projectName
        )
        cacheable(true) // Test results are cacheable
    }
}