package com.frontseat.maven.plugin

import com.frontseat.nature.*
import com.frontseat.command.CommandTask
import com.frontseat.command.commandTask
import java.nio.file.Path

/**
 * Maven project nature that provides Maven build system capabilities
 */
class MavenNature : ProjectNature {
    
    override val id = "maven"
    override val name = "Maven"
    override val version = "1.0.0"
    override val description = "Maven build system support"
    override val layer = NatureLayers.BUILD_SYSTEMS
    
    override fun isApplicable(projectPath: Path, context: NatureContext?): Boolean {
        return MavenUtils.isMavenProject(projectPath)
    }
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, CommandTask> {
        val tasks = mutableMapOf<String, CommandTask>()
        
        // Build lifecycle tasks - using official lifecycle phase names
        tasks["validate"] = commandTask("validate", TargetLifecycle.Build(BuildLifecyclePhase.VALIDATE)) {
            description("Validate the project structure and dependencies")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("validate").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks["initialize"] = commandTask("initialize", TargetLifecycle.Build(BuildLifecyclePhase.INITIALIZE)) {
            description("Initialize the project (clean)")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("clean").toCommandString())
            workingDirectory(projectPath)
        }
        
        // Generate phase - skip if no code generation needed
        
        tasks["compile"] = commandTask("compile", TargetLifecycle.Build(BuildLifecyclePhase.COMPILE)) {
            description("Compile source code")
            command(MavenCommandBuilder.build().inProject(projectPath).withPhase("compile").toCommandString())
            workingDirectory(projectPath)
        }
        
        tasks["test"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("test")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.TEST)
        )
        
        tasks["bu = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("package")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.BUNDLE)
        )
        
        tasks["verify"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("verify")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.VERIFY)
        )
        
        // Release lifecycle tasks - using official lifecycle phase names
        tasks["publish"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("deploy")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Release(ReleaseLifecyclePhase.PUBLISH),
            cacheable = false
        )
        
        return tasks
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        val dependencies = mutableListOf<ProjectDependency>()
        
        // Parse pom.xml to find Maven dependencies that reference other projects in workspace
        val pomFile = projectPath.resolve("pom.xml")
        if (pomFile.exists()) {
            try {
                val pomContent = pomFile.readText()
                val currentProjectName = projectPath.fileName.toString()
                
                // Look for other projects in workspace that might be Maven dependencies
                context.findProjects { project ->
                    project.natures.contains("maven") && project.name != currentProjectName
                }.forEach { project ->
                    // Check if this project is referenced in pom.xml
                    // Simple check - could be more sophisticated with XML parsing
                    if (pomContent.contains("<artifactId>${project.name}</artifactId>")) {
                        dependencies.add(
                            ProjectDependency(
                                source = currentProjectName,
                                target = project.name,
                                type = DependencyType.COMPILE,
                                scope = DependencyScope.BUILD
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Log error but continue
            }
        }
        
        return dependencies
    }
}