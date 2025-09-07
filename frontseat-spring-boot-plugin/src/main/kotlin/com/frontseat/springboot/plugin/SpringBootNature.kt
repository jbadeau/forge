package com.frontseat.springboot.plugin

import com.frontseat.maven.plugin.MavenCommandBuilder
import com.frontseat.nature.*
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Spring Boot project nature that provides Spring Boot specific capabilities
 */
class SpringBootNature : ProjectNature {
    
    override val id = "spring-boot"
    override val name = "Spring Boot"
    override val version = "1.0.0"
    override val description = "Spring Boot application framework support"
    override val layer = NatureLayers.FRAMEWORKS
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        // Spring Boot only requires Spring Boot markers - build system is checked at task creation
        return SpringBootInference.isSpringBootProject(projectPath.toString())
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        val tasks = mutableMapOf<String, CommandTask>()
        
        // Only add serve task for Spring Boot applications (not libraries)
        val projectInfo = SpringBootInference.inferProject(projectPath.toString())
        if (projectInfo?.category == SpringBootProjectCategory.APPLICATION) {
            
            // Check what build system natures are available and create appropriate tasks
            when {
                context.hasNature("maven") -> {
                    // Create serve task using Maven's spring-boot:run goal
                    val command = MavenCommandBuilder.build()
                        .inProject(projectPath)
                        .withGoal("spring-boot:run")
                        .toCommandString()
                    
                    tasks["serve"] = commandTask("serve", TargetLifecycle.Development(DevelopmentLifecyclePhase.SERVE)) {
                        description("Start Spring Boot application for development")
                        command(command)
                        workingDirectory(projectPath)
                        cacheable(false) // Serving is not cacheable
                        readyWhen("Started") // Wait for Spring Boot "Started" message
                    }
                }
                
                // Future: Add support for other build systems
                // context.hasNature("gradle") -> { ... }
                // context.hasNature("sbt") -> { ... }
                
                else -> {
                    // No supported build system available - don't create any tasks
                    logger.debug("SpringBoot nature found but no supported build system (maven) available for project: $projectPath")
                }
            }
        }
        
        return tasks
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        // Spring Boot nature doesn't create additional dependencies beyond what build system natures find
        // Could potentially analyze application.yml/properties for service discovery, etc.
        return emptyList()
    }
}