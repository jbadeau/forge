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
    override val dependencies = emptyList<String>()
    override val conflicts = listOf("gradle")
    
    override fun isApplicable(projectPath: Path): Boolean {
        return MavenUtils.isMavenProject(projectPath)
    }
    
    override fun createTargets(projectPath: Path, context: NatureContext): Map<String, NatureTargetDefinition> {
        val targets = mutableMapOf<String, NatureTargetDefinition>()
        
        // Build lifecycle targets - using official lifecycle phase names
        targets["validate"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("validate")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.VALIDATE)
        )
        
        targets["initialize"] = NatureTargetDefinition(
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
        
        targets["compile"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("compile")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.COMPILE)
        )
        
        targets["test"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("test")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.TEST)
        )
        
        targets["bundle"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("package")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.BUNDLE)
        )
        
        targets["verify"] = NatureTargetDefinition(
            configuration = TargetConfiguration(
                executor = "maven",
                options = MavenCommandBuilder.build()
                    .inProject(projectPath)
                    .withPhase("verify")
                    .toOptions()
            ),
            lifecycle = TargetLifecycle.Build(BuildLifecyclePhase.VERIFY)
        )
        
        // Release lifecycle targets - using official lifecycle phase names
        targets["publish"] = NatureTargetDefinition(
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
        
        return targets
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