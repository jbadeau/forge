package com.frontseat.springboot.plugin

import com.frontseat.maven.plugin.MavenTasks
import com.frontseat.maven.plugin.MavenUtils
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for defining Spring Boot project tasks
 */
object SpringBootTasks {
    
    /**
     * Generate standard Spring Boot tasks for a project
     */
    fun generateTasks(projectPath: Path): Map<String, Map<String, Any>> {
        val category = SpringBootInference.inferSpringBootProjectType(projectPath)
        return generateTasksForCategory(projectPath, category)
    }
    
    /**
     * Generate tasks based on Spring Boot project category
     */
    fun generateTasksForCategory(projectPath: Path, category: SpringBootProjectCategory): Map<String, Map<String, Any>> {
        val tasks = mutableMapOf<String, Map<String, Any>>()
        
        // Use Maven plugin to generate base Maven tasks if pom.xml exists
        if (MavenUtils.isMavenProject(projectPath)) {
            // Get standard Maven tasks from the Maven plugin
            val mavenTasks = MavenTasks.generateMavenTasks(projectPath)
            tasks.putAll(mavenTasks)
        } else {
            // Fallback for non-Maven Spring Boot projects (Gradle, etc.)
            tasks["build"] = mapOf(
                "executor" to "generic",
                "options" to mapOf(
                    "command" to "./gradlew build"
                )
            )
            tasks["test"] = mapOf(
                "executor" to "generic",
                "options" to mapOf(
                    "command" to "./gradlew test"
                )
            )
        }
        
        // Add Spring Boot specific tasks
        addSpringBootSpecificTasks(tasks, projectPath, category)
        
        
        // Add Docker tasks if applicable
        addDockerTasks(tasks, projectPath)
        
        return tasks
    }
    
    /**
     * Add Spring Boot specific tasks to the task map
     */
    private fun addSpringBootSpecificTasks(
        tasks: MutableMap<String, Map<String, Any>>,
        projectPath: Path,
        category: SpringBootProjectCategory
    ) {
        // Serve task (only for applications)
        if (category == SpringBootProjectCategory.APPLICATION) {
            if (MavenUtils.isMavenProject(projectPath)) {
                tasks["serve"] = MavenTasks.createMavenTask("spring-boot:run", projectPath)
            } else {
                // Gradle fallback
                tasks["serve"] = mapOf(
                    "executor" to "generic",
                    "options" to mapOf(
                        "command" to "./gradlew bootRun"
                    )
                )
            }
        }
        
        // Additional tasks based on project type
        val projectType = SpringBootUtils.inferProjectType(projectPath)
        
        when (projectType) {
            SpringBootProjectType.WEB, SpringBootProjectType.REACTIVE_WEB -> {
                // Integration test task for web applications (if not already added by Maven plugin)
                if (!tasks.containsKey("integration-test")) {
                    if (MavenUtils.isMavenProject(projectPath)) {
                        tasks["integration-test"] = MavenTasks.createMavenTask("verify", projectPath)
                    }
                }
            }
            SpringBootProjectType.DATA -> {
                // Database migration task
                if (MavenUtils.isMavenProject(projectPath)) {
                    // Only add if Flyway plugin is present
                    if (MavenTasks.hasPlugin(projectPath, "flyway-maven-plugin")) {
                        tasks["migrate"] = MavenTasks.createMavenTask("flyway:migrate", projectPath)
                        tasks["migrate-info"] = MavenTasks.createMavenTask("flyway:info", projectPath)
                    }
                }
            }
            else -> {
                // No additional tasks for basic projects
            }
        }
    }
    
    /**
     * Add Docker tasks if Dockerfile exists
     */
    private fun addDockerTasks(
        tasks: MutableMap<String, Map<String, Any>>,
        projectPath: Path
    ) {
        if ((projectPath / "Dockerfile").exists()) {
            val projectName = projectPath.fileName.toString()
            
            tasks["docker-build"] = mapOf(
                "executor" to "docker",
                "options" to mapOf(
                    "command" to "build",
                    "tag" to projectName,
                    "context" to ".",
                    "file" to "Dockerfile"
                )
            )
            
            tasks["docker-run"] = mapOf(
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
     * Get dependencies for Spring Boot tasks
     */
    fun getTaskDependencies(taskName: String): List<String> {
        // First check Maven plugin dependencies
        val mavenDependencies = MavenTasks.getMavenTaskDependencies(taskName)
        if (mavenDependencies.isNotEmpty()) {
            return mavenDependencies
        }
        
        // Spring Boot specific dependencies
        return when (taskName) {
            "serve" -> listOf("build") // Applications need to be compiled first
            "docker-build" -> listOf("package")
            "docker-run" -> listOf("docker-build")
            "migrate" -> listOf("build") // Database migrations need compiled classes
            "migrate-info" -> emptyList() // Info doesn't need compilation
            else -> emptyList()
        }
    }
    
    /**
     * Get task metadata for Spring Boot projects
     */
    fun getTaskMetadata(taskName: String, projectPath: Path): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        
        when (taskName) {
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