package com.frontseat.plugin

import com.frontseat.nature.ProjectInferenceEngine
import com.frontseat.plugin.api.*
import java.nio.file.Path

/**
 * Universal project plugin that uses natures to infer and configure projects
 */
class NatureProjectPlugin : ProjectPlugin {
    
    private val inferenceEngine = ProjectInferenceEngine()
    
    override val metadata = ProjectPluginMetadata(
        id = "com.frontseat.nature.project",
        name = "Nature Project Plugin",
        version = "1.0.0",
        description = "Universal project plugin using composable natures",
        createNodesPattern = "**/{pom.xml,build.gradle,build.gradle.kts,package.json}",
        supportedFiles = listOf("pom.xml", "build.gradle", "build.gradle.kts", "package.json", 
                               "application.properties", "application.yml", "Dockerfile"),
        author = "Forge Team",
        homepage = "https://github.com/frontseat/nature-plugin",
        tags = listOf("nature", "universal", "inference")
    )
    
    override fun createNodes(
        configFiles: List<String>,
        options: Any?,
        context: CreateNodesContext
    ): CreateNodesResult {
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            val projectPath = Path.of(configFile).parent
            
            try {
                val inferredProject = inferenceEngine.inferProject(projectPath)
                
                if (inferredProject != null) {
                    projects[inferredProject.name] = inferredProject.toProjectConfiguration()
                }
            } catch (e: Exception) {
                // Log error but continue processing other projects
                println("Error inferring project at $projectPath: ${e.message}")
            }
        }
        
        return CreateNodesResult(projects)
    }
    
    override fun createEdges(
        options: Any?,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        // TODO: Implement project dependencies based on nature analysis
        return emptyList()
    }
}