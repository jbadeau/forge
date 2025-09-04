package com.forge.gradle.plugin

import com.forge.nature.*
import com.forge.plugin.api.TargetConfiguration
import java.nio.file.Path

/**
 * Gradle project nature that provides Gradle build system capabilities
 */
class GradleNature : ProjectNature {
    
    override val id = "gradle"
    override val name = "Gradle"
    override val version = "1.0.0"
    override val description = "Gradle build system support"
    override val dependencies = emptyList<String>()
    override val conflicts = listOf("maven")
    
    override fun isApplicable(projectPath: Path): Boolean {
        return GradleUtils.isGradleProject(projectPath)
    }
    
    override fun createTargets(projectPath: Path, context: NatureContext): Map<String, NatureTargetDefinition> {
        val targets = mutableMapOf<String, NatureTargetDefinition>()
        
        // Build lifecycle targets - using official lifecycle phase names only
        targets["initialize"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "gradle",
                options = GradleCommandBuilder.build()
                    .inProject(projectPath)
                    .withTask("clean")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.INITIALIZE)
        )
        
        // Generate phase - skip if no code generation needed
        
        targets["compile"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "gradle",
                options = GradleCommandBuilder.build()
                    .inProject(projectPath)
                    .withTask("compileJava")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.COMPILE)
        )
        
        targets["test"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "gradle",
                options = GradleCommandBuilder.build()
                    .inProject(projectPath)
                    .withTask("test")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.TEST)
        )
        
        targets["bundle"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "gradle",
                options = GradleCommandBuilder.build()
                    .inProject(projectPath)
                    .withTask("assemble")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.BUNDLE)
        )
        
        targets["verify"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "gradle",
                options = GradleCommandBuilder.build()
                    .inProject(projectPath)
                    .withTask("check")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.VERIFY)
        )
        
        // Release lifecycle targets - using official lifecycle phase names only
        targets["publish"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "gradle",
                options = GradleCommandBuilder.build()
                    .inProject(projectPath)
                    .withTask("publish")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Release(ReleaseLifecyclePhase.PUBLISH),
            cacheable = false
        )
        
        return targets
    }
    
    override fun createDependencies(projectPath: Path, context: NatureContext): List<ProjectDependency> {
        val dependencies = mutableListOf<ProjectDependency>()
        
        // Parse build.gradle/.kts to find project dependencies
        val buildFile = GradleUtils.getBuildFile(projectPath)
        if (buildFile?.exists() == true) {
            try {
                val buildContent = buildFile.readText()
                val currentProjectName = projectPath.fileName.toString()
                
                // Look for other projects that might be referenced
                context.findProjects { project ->
                    project.natures.contains("gradle") && project.name != currentProjectName
                }.forEach { project ->
                    // Check if this project is referenced in build file
                    // Look for project dependencies like: implementation project(":other-project")
                    if (buildContent.contains("project(\":${project.name}\")") ||
                        buildContent.contains("project(':${project.name}')")) {
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