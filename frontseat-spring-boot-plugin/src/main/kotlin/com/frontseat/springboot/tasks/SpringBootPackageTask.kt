package com.frontseat.springboot.tasks

import com.frontseat.annotation.Task
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import com.frontseat.maven.commons.MavenCommandBuilder
import com.frontseat.springboot.commons.SpringBootTaskNames
import com.frontseat.nature.TargetLifecycle
import com.frontseat.nature.BuildLifecyclePhase
import java.nio.file.Path

/**
 * Spring Boot build-image task - build a container image using Cloud Native Buildpacks
 * 
 * Options that can be overridden in project.json:
 * - imageName: Name of the image to build (default: project name)
 * - imageTag: Tag for the image (default: "latest")
 * - builder: Builder image to use (default: "paketobuildpacks/builder:base")
 * - runImage: Run image to use (optional)
 * - env: Environment variables for the build
 * - publish: Whether to publish the image (default: false)
 * - registry: Registry to publish to (if publish is true)
 * - registryUsername: Registry username
 * - registryPassword: Registry password (should use secrets!)
 */
@Task(
    name = SpringBootTaskNames.PACKAGE,
    lifecycle = TargetLifecycle.Build::class,
    phase = BuildLifecyclePhase.PACKAGE
)
fun createSpringBootPackageTask(
    projectPath: Path,
    options: SpringBootPackageOptions
): CommandTask {
    // Build the Maven command for spring-boot:build-image
    val builder = MavenCommandBuilder.build()
        .inProject(projectPath)
        .withGoal("spring-boot:build-image")
    
    // Set image name
    if (options.imageName.isNotEmpty()) {
        val fullImageName = "${options.imageName}:${options.imageTag}"
        builder.withProperty("spring-boot.build-image.imageName", fullImageName)
    }
    
    // Set builder
    builder.withProperty("spring-boot.build-image.builder", options.builder)
    
    // Set run image if specified
    options.runImage?.let { runImage ->
        builder.withProperty("spring-boot.build-image.runImage", runImage)
    }
    
    // Add environment variables
    options.env.forEach { (key, value) ->
        builder.withProperty("spring-boot.build-image.env.$key", value)
    }
    
    // Handle publishing
    if (options.publish) {
        builder.withProperty("spring-boot.build-image.publish", "true")
        
        // Set registry if specified
        options.registry?.let { registry ->
            builder.withProperty("docker.registry", registry)
        }
        
        // Set registry credentials (should use secrets in production!)
        options.registryUsername?.let { username ->
            builder.withProperty("docker.registry.username", username)
        }
        options.registryPassword?.let { password ->
            builder.withProperty("docker.registry.password", password)
        }
    }
    
    // Add any additional args
    options.args.forEach { arg ->
        builder.withArg(arg)
    }
    
    return commandTask(SpringBootTaskNames.PACKAGE, TargetLifecycle.Build(BuildLifecyclePhase.PACKAGE)) {
        description("Build container image using Cloud Native Buildpacks")
        command(builder.toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = listOf("pom.xml", "target/*.jar", "src/main/resources/**")
        outputs = emptyList() // Image is stored in Docker
        cacheable(false) // Image building typically not cached
    }
}