package com.frontseat.springboot.plugin

import com.frontseat.annotation.AutoRegister
import com.frontseat.annotation.Nature
import com.frontseat.maven.commons.MavenNatureIds
import com.frontseat.springboot.commons.SpringBootUtils
import com.frontseat.springboot.commons.SpringBootNatureIds
import com.frontseat.springboot.commons.SpringBootTaskNames
import com.frontseat.springboot.tasks.*
import com.frontseat.nature.*
import com.frontseat.command.CommandTask
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Spring Boot project nature that provides Spring Boot specific capabilities
 */
@Nature(id = SpringBootNatureIds.SPRING_BOOT, layer = NatureLayers.FRAMEWORKS)
@AutoRegister
class SpringBootNature : ProjectNature {
    private val logger = LoggerFactory.getLogger(SpringBootNature::class.java)
    
    // No need to override id or layer - they come from @Nature annotation!
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        return SpringBootUtils.isSpringBootProject(projectPath)
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        val tasks = mutableMapOf<String, CommandTask>()
        
        // Check if Maven nature is available for Spring Boot Maven plugin tasks
        if (context.hasNature(MavenNatureIds.MAVEN)) {
            // Spring Boot development start task (uses spring-boot:run)
            tasks[SpringBootTaskNames.START] = createSpringBootStartTask(projectPath)
            
            // Build container image using Cloud Native Buildpacks
            tasks[SpringBootTaskNames.CONTAINERIZE] = createSpringBootContainerizeTask(projectPath)
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