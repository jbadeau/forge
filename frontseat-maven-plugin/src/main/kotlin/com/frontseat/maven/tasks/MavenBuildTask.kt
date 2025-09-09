package com.frontseat.maven.tasks

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
fun createMavenBuildTask(
    projectPath: Path,
    options: MavenBuildOptions
): CommandTask {
    return commandTask(MavenTaskNames.BUILD, TargetLifecycle.Build(BuildLifecyclePhase.BUILD)) {
        description("Compile, package, and install to local repository")
        
        // Build only this project (-pl .) and install to local repo
        command(MavenCommandBuilder.build()
            .inProject(projectPath.parent) // Run from parent to use -pl
            .withArg("-pl")
            .withArg(options.project)
            .withPhase(options.phase)
            .toCommandString())
        workingDirectory(projectPath.parent)
        
        // Nx-like task configuration  
        inputs = listOf("pom.xml", "src/main/**")
        outputs = listOf("target/classes/**", "target/*.jar", "target/*.war")
        cacheable(true) // Build is cacheable
    }
}