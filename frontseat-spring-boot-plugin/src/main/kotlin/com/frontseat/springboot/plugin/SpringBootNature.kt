package com.frontseat.springboot.plugin

import com.frontseat.annotation.AutoRegister
import com.frontseat.annotation.Nature
import com.frontseat.maven.plugin.MavenCommandBuilder
import com.frontseat.nature.*
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Spring Boot project nature that provides Spring Boot specific capabilities
 */
@Nature(id = "spring-boot", layer = NatureLayers.FRAMEWORKS)
@AutoRegister
class SpringBootNature : ProjectNature {
    private val logger = LoggerFactory.getLogger(SpringBootNature::class.java)
    
    // No need to override id or layer - they come from @Nature annotation!
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        return SpringBootUtils.isSpringBootProject(projectPath)
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        val tasks = mutableMapOf<String, CommandTask>()
        
        // Check if Maven nature is available and create serve task
        if (context.hasNature("maven")) {
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
        } else {
            logger.debug("SpringBoot nature found but no supported build system (maven) available for project: $projectPath")
        }
        
        return tasks
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        // Spring Boot nature doesn't create additional dependencies beyond what build system natures find
        // Could potentially analyze application.yml/properties for service discovery, etc.
        return emptyList()
    }
}