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
    userOptions: Map<String, Any> = emptyMap()
): CommandTask {
    // Default options
    val defaultOptions = mapOf(
        "imageName" to projectPath.fileName.toString(),
        "imageTag" to "latest",
        "builder" to "paketobuildpacks/builder:base",
        "publish" to false
    )
    
    // Merge user options with defaults
    val finalOptions = defaultOptions + userOptions
    
    // Build the Maven command for spring-boot:build-image
    val builder = MavenCommandBuilder.build()
        .inProject(projectPath)
        .withGoal("spring-boot:build-image")
    
    // Set image name
    val imageName = finalOptions["imageName"]?.toString()
    val imageTag = finalOptions["imageTag"]?.toString()
    if (!imageName.isNullOrEmpty()) {
        val fullImageName = if (!imageTag.isNullOrEmpty()) {
            "$imageName:$imageTag"
        } else {
            imageName
        }
        builder.withProperty("spring-boot.build-image.imageName", fullImageName)
    }
    
    // Set builder
    val builderImage = finalOptions["builder"]?.toString()
    if (!builderImage.isNullOrEmpty()) {
        builder.withProperty("spring-boot.build-image.builder", builderImage)
    }
    
    // Set run image if specified
    (finalOptions["runImage"] as? String)?.let { runImage ->
        builder.withProperty("spring-boot.build-image.runImage", runImage)
    }
    
    // Add environment variables
    (finalOptions["env"] as? Map<*, *>)?.forEach { (key, value) ->
        builder.withProperty("spring-boot.build-image.env.$key", value.toString())
    }
    
    // Handle publishing
    if (finalOptions["publish"] == true) {
        builder.withProperty("spring-boot.build-image.publish", "true")
        
        // Set registry if specified
        (finalOptions["registry"] as? String)?.let { registry ->
            builder.withProperty("docker.registry", registry)
        }
        
        // Set registry credentials (should use secrets in production!)
        (finalOptions["registryUsername"] as? String)?.let { username ->
            builder.withProperty("docker.registry.username", username)
        }
        (finalOptions["registryPassword"] as? String)?.let { password ->
            builder.withProperty("docker.registry.password", password)
        }
    }
    
    // Add any additional args
    (finalOptions["args"] as? List<*>)?.forEach { arg ->
        builder.withArg(arg.toString())
    }
    
    return commandTask(SpringBootTaskNames.PACKAGE, TargetLifecycle.Build(BuildLifecyclePhase.PACKAGE)) {
        description("Build container image using Cloud Native Buildpacks")
        command(builder.toCommandString())
        workingDirectory(projectPath)
        
        // Nx-like task configuration
        inputs = (userOptions["inputs"] as? List<String>) ?: listOf(
            "pom.xml",
            "target/*.jar",
            "src/main/resources/**"
        )
        outputs = (userOptions["outputs"] as? List<String>) ?: emptyList() // Image is stored in Docker
        options = finalOptions
        
        
        cacheable((userOptions["cache"] as? Boolean) ?: false) // Image building typically not cached
    }
}