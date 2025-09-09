package com.frontseat.springboot.plugin

import com.frontseat.project.nature.NatureInfo
import com.frontseat.maven.commons.MavenNatureIds
import com.frontseat.springboot.commons.SpringBootUtils
import com.frontseat.springboot.commons.SpringBootNatureIds
import com.frontseat.springboot.commons.SpringBootTaskNames
import com.frontseat.springboot.tasks.*
import com.frontseat.project.nature.Nature
import com.frontseat.project.nature.NatureContext
import com.frontseat.project.nature.NatureLayers
import com.frontseat.project.nature.ProjectDependency
import com.frontseat.task.CommandTask
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Spring Boot project nature that provides Spring Boot specific capabilities
 */
@NatureInfo(id = SpringBootNatureIds.SPRING_BOOT, layer = NatureLayers.FRAMEWORKS)
class SpringBootNature : Nature {
    private val logger = LoggerFactory.getLogger(SpringBootNature::class.java)
    
    // No need to override id or layer - they come from @NatureInfo annotation!
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        return SpringBootUtils.isSpringBootProject(projectPath)
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        val tasks = mutableMapOf<String, CommandTask>()
        
        // Check if Maven nature is available for Spring Boot Maven plugin tasks
        if (context.hasNature(MavenNatureIds.MAVEN)) {
            // Spring Boot development start task (uses spring-boot:run)
            val startOptions = SpringBootStartOptions.defaults()
            tasks[SpringBootTaskNames.START] = createSpringBootStartTask(projectPath, startOptions)
            
            // Build container image using Cloud Native Buildpacks
            val packageOptions = SpringBootPackageOptions.defaults(projectPath.fileName.toString())
            tasks[SpringBootTaskNames.PACKAGE] = createSpringBootPackageTask(projectPath, packageOptions)
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