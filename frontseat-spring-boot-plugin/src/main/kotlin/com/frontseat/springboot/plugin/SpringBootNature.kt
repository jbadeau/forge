package com.frontseat.springboot.plugin

import com.frontseat.nature.*
import com.frontseat.plugin.api.TargetConfiguration
import java.nio.file.Path

/**
 * Spring Boot project nature that provides Spring Boot specific capabilities
 */
class SpringBootNature : ProjectNature {
    
    override val id = "spring-boot"
    override val name = "Spring Boot"
    override val version = "1.0.0"
    override val description = "Spring Boot application framework support"
    override val dependencies = emptyList<String>() // Can work with either maven or gradle
    override val conflicts = emptyList<String>()
    
    override fun isApplicable(projectPath: Path): Boolean {
        return SpringBootInference.isSpringBootProject(projectPath.toString())
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, NatureTargetDefinition> {
        val tasks = mutableMapOf<String, NatureTargetDefinition>()
        
        // Only add serve task for Spring Boot applications (not libraries)
        val projectInfo = SpringBootInference.inferProject(projectPath.toString())
        if (projectInfo?.category == SpringBootProjectCategory.APPLICATION) {
            
            // Create serve task based on available build system
            val serveTask = when {
                context.hasNature("maven") -> {
                    TargetConfiguration(
                        executor = "maven",
                        options = mapOf(
                            "command" to "mvn spring-boot:run",
                            "workingDirectory" to projectPath.toString()
                        )
                    )
                }
                context.hasNature("gradle") -> {
                    TargetConfiguration(
                        executor = "gradle", 
                        options = mapOf(
                            "command" to "./gradlew bootRun",
                            "workingDirectory" to projectPath.toString()
                        )
                    )
                }
                else -> {
                    // Fallback - shouldn't happen if dependencies are correct
                    TargetConfiguration(
                        executor = "shell",
                        options = mapOf(
                            "command" to "echo 'No build system found for Spring Boot serve'",
                            "workingDirectory" to projectPath.toString()
                        )
                    )
                }
            }
            
            tasks["serve"] = NatureTargetDefinition(
                configuration = serveTask,
                lifecycle = TargetLifecycle.Development(DevelopmentLifecyclePhase.SERVE),
                cacheable = false // Serving is not cacheable
            )
        }
        
        return tasks
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        // Spring Boot nature doesn't create additional dependencies beyond what build system natures find
        // Could potentially analyze application.yml/properties for service discovery, etc.
        return emptyList()
    }
}