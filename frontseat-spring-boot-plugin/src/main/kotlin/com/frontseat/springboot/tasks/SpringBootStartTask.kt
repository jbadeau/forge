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
    userOptions: Map<String, Any> = emptyMap()
): CommandTask {
    // Default options
    val defaultOptions = mapOf(
        "profiles" to "dev",
        "port" to 8080,
        "debug" to false,
        "debugPort" to 5005
    )
    
    // Merge user options with defaults
    val finalOptions = defaultOptions + userOptions
    
    // Build the Maven command for spring-boot:run
    val builder = MavenCommandBuilder.build()
        .inProject(projectPath)
        .withGoal("spring-boot:run")
    
    // Set active profiles
    val profiles = finalOptions["profiles"]?.toString()
    if (!profiles.isNullOrEmpty()) {
        builder.withProperty("spring-boot.run.profiles", profiles)
    }
    
    // Set server port
    val port = finalOptions["port"]
    builder.withProperty("spring-boot.run.arguments", "--server.port=$port")
    
    // Enable debug if requested
    if (finalOptions["debug"] == true) {
        val debugPort = finalOptions["debugPort"]
        builder.withProperty(
            "spring-boot.run.jvmArguments",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debugPort"
        )
    }
    
    // Add custom JVM args
    (finalOptions["jvmArgs"] as? List<*>)?.forEach { arg ->
        builder.withProperty("spring-boot.run.jvmArguments", arg.toString())
    }
    
    // Add custom main class if specified
    (finalOptions["mainClass"] as? String)?.let { mainClass ->
        builder.withProperty("spring-boot.run.mainClass", mainClass)
    }
    
    // Add any additional args
    (finalOptions["args"] as? List<*>)?.forEach { arg ->
        builder.withArg(arg.toString())
    }
    
    return commandTask(SpringBootTaskNames.START, TargetLifecycle.Development(DevelopmentLifecyclePhase.START)) {
        description("Start Spring Boot application in development mode")
        command(builder.toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = (userOptions["inputs"] as? List<String>) ?: listOf(
            "pom.xml",
            "src/main/**",
            "src/main/resources/**"
        )
        outputs = (userOptions["outputs"] as? List<String>) ?: emptyList()
        options = finalOptions
        
        cacheable(false) // Starting is never cacheable
        readyWhen((userOptions["readyWhen"] as? String) ?: "Started") // Wait for Spring Boot startup
    }
}