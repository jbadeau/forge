package com.frontseat.springboot.plugin

import com.frontseat.maven.plugin.MavenTargets
import com.frontseat.maven.plugin.MavenUtils
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for defining Spring Boot project targets
 */
object SpringBootTargets {
    
    /**
     * Generate standard Spring Boot targets for a project
     */
    fun generateTargets(projectPath: Path): Map<String, Map<String, Any>> {
        val category = SpringBootInference.inferSpringBootProjectType(projectPath)
        return generateTargetsForCategory(projectPath, category)
    }
    
    /**
     * Generate targets based on Spring Boot project category
     */
    fun generateTargetsForCategory(projectPath: Path, category: SpringBootProjectCategory): Map<String, Map<String, Any>> {
        val targets = mutableMapOf<String, Map<String, Any>>()
        
        // Use Maven plugin to generate base Maven targets if pom.xml exists
        if (MavenUtils.isMavenProject(projectPath)) {
            // Get standard Maven targets from the Maven plugin
            val mavenTargets = MavenTargets.generateMavenTargets(projectPath)
            targets.putAll(mavenTargets)
        } else {
            // Fallback for non-Maven Spring Boot projects (Gradle, etc.)
            targets["build"] = mapOf(
                "executor" to "generic",
                "options" to mapOf(
                    "command" to "./gradlew build"
                )
            )
            targets["test"] = mapOf(
                "executor" to "generic",
                "options" to mapOf(
                    "command" to "./gradlew test"
                )
            )
        }
        
        // Add Spring Boot specific targets
        addSpringBootSpecificTargets(targets, projectPath, category)
        
        
        // Add Docker targets if applicable
        addDockerTargets(targets, projectPath)
        
        return targets
    }
    
    /**
     * Add Spring Boot specific targets to the target map
     */
    private fun addSpringBootSpecificTargets(
        targets: MutableMap<String, Map<String, Any>>,
        projectPath: Path,
        category: SpringBootProjectCategory
    ) {
        // Serve target (only for applications)
        if (category == SpringBootProjectCategory.APPLICATION) {
            if (MavenUtils.isMavenProject(projectPath)) {
                targets["serve"] = MavenTargets.createMavenTarget("spring-boot:run", projectPath)
            } else {
                // Gradle fallback
                targets["serve"] = mapOf(
                    "executor" to "generic",
                    "options" to mapOf(
                        "command" to "./gradlew bootRun"
                    )
                )
            }
        }
        
        // Additional targets based on project type
        val projectType = SpringBootUtils.inferProjectType(projectPath)
        
        when (projectType) {
            SpringBootProjectType.WEB, SpringBootProjectType.REACTIVE_WEB -> {
                // Integration test target for web applications (if not already added by Maven plugin)
                if (!targets.containsKey("integration-test")) {
                    if (MavenUtils.isMavenProject(projectPath)) {
                        targets["integration-test"] = MavenTargets.createMavenTarget("verify", projectPath)
                    }
                }
            }
            SpringBootProjectType.DATA -> {
                // Database migration target
                if (MavenUtils.isMavenProject(projectPath)) {
                    // Only add if Flyway plugin is present
                    if (MavenTargets.hasPlugin(projectPath, "flyway-maven-plugin")) {
                        targets["migrate"] = MavenTargets.createMavenTarget("flyway:migrate", projectPath)
                        targets["migrate-info"] = MavenTargets.createMavenTarget("flyway:info", projectPath)
                    }
                }
            }
            else -> {
                // No additional targets for basic projects
            }
        }
    }
    
    /**
     * Add Docker targets if Dockerfile exists
     */
    private fun addDockerTargets(
        targets: MutableMap<String, Map<String, Any>>,
        projectPath: Path
    ) {
        if ((projectPath / "Dockerfile").exists()) {
            val projectName = projectPath.fileName.toString()
            
            targets["docker-build"] = mapOf(
                "executor" to "docker",
                "options" to mapOf(
                    "command" to "build",
                    "tag" to projectName,
                    "context" to ".",
                    "file" to "Dockerfile"
                )
            )
            
            targets["docker-run"] = mapOf(
                "executor" to "docker",
                "options" to mapOf(
                    "command" to "run", 
                    "image" to projectName,
                    "ports" to listOf("8080:8080"),
                    "detach" to true
                )
            )
        }
    }
    
    /**
     * Get dependencies for Spring Boot targets
     */
    fun getTargetDependencies(targetName: String): List<String> {
        // First check Maven plugin dependencies
        val mavenDependencies = MavenTargets.getMavenTargetDependencies(targetName)
        if (mavenDependencies.isNotEmpty()) {
            return mavenDependencies
        }
        
        // Spring Boot specific dependencies
        return when (targetName) {
            "serve" -> listOf("build") // Applications need to be compiled first
            "docker-build" -> listOf("package")
            "docker-run" -> listOf("docker-build")
            "migrate" -> listOf("build") // Database migrations need compiled classes
            "migrate-info" -> emptyList() // Info doesn't need compilation
            else -> emptyList()
        }
    }
    
    /**
     * Get target metadata for Spring Boot projects
     */
    fun getTargetMetadata(targetName: String, projectPath: Path): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        
        when (targetName) {
            "serve" -> {
                metadata["port"] = getServerPort(projectPath)
                metadata["url"] = "http://localhost:${getServerPort(projectPath)}"
            }
            "package" -> {
                metadata["outputPath"] = "target/"
                metadata["artifactType"] = "jar"
            }
            "docker-build" -> {
                metadata["dockerfile"] = "Dockerfile"
                metadata["context"] = "."
            }
        }
        
        return metadata
    }
    
    private fun getServerPort(projectPath: Path): Int {
        val configPath = SpringBootUtils.getApplicationConfigPath(projectPath)
        if (configPath != null) {
            val content = configPath.readText()
            
            // Look for server.port property
            val portRegex = if (configPath.fileName.toString().endsWith(".properties")) {
                Regex("server\\.port\\s*=\\s*(\\d+)")
            } else {
                Regex("port:\\s*(\\d+)")
            }
            
            portRegex.find(content)?.let { match ->
                return match.groupValues[1].toIntOrNull() ?: 8080
            }
        }
        
        return 8080 // Default Spring Boot port
    }
}