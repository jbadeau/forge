package com.frontseat.springboot.tasks

import com.frontseat.annotation.Task
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import com.frontseat.maven.commons.MavenCommandBuilder
import com.frontseat.springboot.commons.SpringBootTaskNames
import com.frontseat.nature.TargetLifecycle
import com.frontseat.nature.DevelopmentLifecyclePhase
import java.nio.file.Path

/**
 * Spring Boot start task - start the application in development mode using Maven
 * 
 * Options that can be overridden in project.json:
 * - profiles: Active Spring profiles (default: "dev")
 * - port: Server port (default: 8080)
 * - debug: Enable debug mode (default: false)
 * - debugPort: Debug port (default: 5005)
 * - jvmArgs: Additional JVM arguments
 * - args: Additional application arguments
 * - mainClass: Main class to run (auto-detected by default)
 */
@Task(
    name = SpringBootTaskNames.START,
    lifecycle = TargetLifecycle.Development::class,
    phase = DevelopmentLifecyclePhase.START
)
fun createSpringBootStartTask(
    projectPath: Path,
    options: SpringBootStartOptions
): CommandTask {
    // Build the Maven command for spring-boot:run
    val builder = MavenCommandBuilder.build()
        .inProject(projectPath)
        .withGoal("spring-boot:run")
    
    // Set active profiles
    if (options.profiles.isNotEmpty()) {
        builder.withProperty("spring-boot.run.profiles", options.profiles)
    }
    
    // Set server port
    builder.withProperty("spring-boot.run.arguments", "--server.port=${options.port}")
    
    // Enable debug if requested
    if (options.debug) {
        builder.withProperty(
            "spring-boot.run.jvmArguments",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${options.debugPort}"
        )
    }
    
    // Add custom JVM args
    options.jvmArgs.forEach { arg ->
        builder.withProperty("spring-boot.run.jvmArguments", arg)
    }
    
    // Add any additional args
    options.args.forEach { arg ->
        builder.withArg(arg)
    }
    
    return commandTask(SpringBootTaskNames.START, TargetLifecycle.Development(DevelopmentLifecyclePhase.START)) {
        description("Start Spring Boot application in development mode")
        command(builder.toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "src/main/**")
        outputs = emptyList() // Dev server doesn't produce outputs
        cacheable(false) // Dev server is not cacheable
    }
}