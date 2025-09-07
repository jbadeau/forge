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
 * Maven compile task - compile source code
 * 
 * Options that can be overridden in project.json:
 * - phase: Maven phase to run (default: "compile")
 * - skipTests: Skip test compilation (default: false)
 * - args: Additional Maven arguments
 */
@Task(
    name = MavenTaskNames.COMPILE,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.COMPILE
)
fun createMavenCompileTask(
    projectPath: Path,
    userOptions: Map<String, Any> = emptyMap()
): CommandTask {
    // Default options
    val defaultOptions = mapOf(
        "phase" to "compile",
        "skipTests" to false
    )
    
    // Merge user options with defaults
    val finalOptions = defaultOptions + userOptions
    
    // Build the Maven command based on options
    val builder = MavenCommandBuilder.build()
        .inProject(projectPath)
        .withPhase(finalOptions["phase"] as String)
    
    // Add skip tests flag if requested
    if (finalOptions["skipTests"] == true) {
        builder.withArg("-DskipTests")
    }
    
    // Add any additional args from user
    (finalOptions["args"] as? List<*>)?.forEach { arg ->
        builder.withArg(arg.toString())
    }
    
    return commandTask(MavenTaskNames.COMPILE, TargetLifecycle.Build(BuildLifecyclePhase.COMPILE)) {
        description("Compile source code")
        command(builder.toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = (userOptions["inputs"] as? List<String>) ?: listOf("pom.xml", "src/main/**")
        outputs = (userOptions["outputs"] as? List<String>) ?: listOf("target/classes/**")
        options = finalOptions
        cacheable((userOptions["cache"] as? Boolean) ?: true)
    }
}