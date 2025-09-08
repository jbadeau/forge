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
 * Maven build task - compile, package, and install to local repository (runs mvn install)
 */
@Task(
    name = MavenTaskNames.BUILD,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.BUILD
)
fun createMavenBuildTask(
    projectPath: Path,
    userOptions: Map<String, Any> = emptyMap()
): CommandTask {
    return commandTask(MavenTaskNames.BUILD, TargetLifecycle.Build(BuildLifecyclePhase.BUILD)) {
        description("Compile, package, and install to local repository")
        
        // Build only this project (-pl .) and install to local repo
        val projectName = projectPath.fileName.toString()
        command(MavenCommandBuilder.build()
            .inProject(projectPath.parent) // Run from parent to use -pl
            .withArg("-pl")
            .withArg(projectName)
            .withPhase("install")
            .toCommandString())
        workingDirectory(projectPath.parent)
        
        // Nx-like task configuration  
        inputs = (userOptions["inputs"] as? List<String>) ?: listOf("pom.xml", "src/main/**")
        outputs = (userOptions["outputs"] as? List<String>) ?: listOf("target/classes/**", "target/*.jar", "target/*.war")
        options = mapOf(
            "phase" to "install",
            "project" to projectName
        ) + userOptions
        
        cacheable((userOptions["cache"] as? Boolean) ?: true)
    }
}