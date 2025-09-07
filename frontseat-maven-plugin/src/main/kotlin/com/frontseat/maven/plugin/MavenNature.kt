package com.frontseat.maven.plugin

import com.frontseat.nature.*
import com.frontseat.plugin.api.TargetConfiguration
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
    
    override fun createTasks(projectPath: Path, context: NatureContext): Map<String, NatureTargetDefinition> {
        val tasks = mutableMapOf<String, NatureTargetDefinition>()
        
        // Build lifecycle tasks - using official lifecycle phase names
        tasks["validate"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("validate")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.VALIDATE)
        )
        
        tasks["initialize"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("clean")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.INITIALIZE)
        )
        
        // Generate phase - skip if no code generation needed
        
        tasks["compile"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("compile")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.COMPILE)
        )
        
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