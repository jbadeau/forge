package com.forge.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.forge.discovery.ProjectDiscovery
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

/**
 * Simple implementation of show project command for demo purposes
 */
object SimpleShowCommand {
    
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    fun showProject(projectName: String, json: Boolean = false) {
        try {
            val workspaceRoot = findWorkspaceRoot()
            
            // Discover projects
            val discovery = ProjectDiscovery(workspaceRoot)
            val projectGraph = discovery.discoverProjects()
            
            // Find the requested project
            val project = projectGraph.nodes[projectName]
            if (project == null) {
                println("❌ Project '$projectName' not found")
                println("Available projects:")
                projectGraph.getAllProjects().sortedBy { it.name }.forEach { p ->
                    println("  • ${p.name}")
                }
                return
            }
            
            if (json) {
                // Output JSON format (similar to nx show project --json)
                val dependencies = projectGraph.getDependencies(project.name)
                val projectData = mapOf(
                    "name" to project.name,
                    "root" to project.data.root,
                    "sourceRoot" to project.data.sourceRoot,
                    "projectType" to project.data.projectType,
                    "tags" to project.data.tags,
                    "targets" to project.data.targets.mapValues { (_, target) ->
                        mapOf(
                            "executor" to target.executor,
                            "command" to target.command,
                            "options" to target.options,
                            "configurations" to target.configurations,
                            "dependsOn" to target.dependsOn,
                            "inputs" to target.inputs,
                            "outputs" to target.outputs,
                            "cache" to target.cache,
                            "parallelism" to target.parallelism
                        )
                    },
                    "dependencies" to dependencies.map { dep ->
                        mapOf(
                            "target" to dep.target,
                            "type" to dep.type.toString().lowercase()
                        )
                    }
                )
                println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(projectData))
            } else {
                // Human-readable format
                showProjectDetails(project, projectGraph, workspaceRoot)
            }
            
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun showProjectDetails(
        project: com.forge.core.ProjectGraphNode, 
        projectGraph: com.forge.core.ProjectGraph,
        workspaceRoot: Path
    ) {
        println("📦 Project: ${project.name}")
        println("━".repeat(50))
        println()
        
        // Basic information
        println("📍 Basic Information:")
        println("  Type: ${project.data.projectType}")
        println("  Root: ${project.data.root}")
        if (project.data.sourceRoot?.isNotEmpty() == true) {
            println("  Source Root: ${project.data.sourceRoot}")
        }
        println()
        
        // Tags
        if (project.data.tags.isNotEmpty()) {
            println("🏷️  Tags:")
            project.data.tags.sorted().forEach { tag ->
                println("  • $tag")
            }
            println()
        }
        
        // Targets
        if (project.data.targets.isNotEmpty()) {
            println("🎯 Targets:")
            project.data.targets.toSortedMap().forEach { (targetName, targetConfig) ->
                println("  📋 $targetName")
                
                if (targetConfig.executor != null) {
                    println("    Executor: ${targetConfig.executor}")
                }
                if (targetConfig.command != null) {
                    println("    Command: ${targetConfig.command}")
                }
                
                if (targetConfig.options.isNotEmpty()) {
                    println("    Options:")
                    targetConfig.options.forEach { (key, value) ->
                        println("      $key: $value")
                    }
                }
                
                if (targetConfig.dependsOn.isNotEmpty()) {
                    println("    Depends On: ${targetConfig.dependsOn.joinToString(", ")}")
                }
                
                if (targetConfig.inputs.isNotEmpty()) {
                    println("    Inputs: ${targetConfig.inputs.joinToString(", ")}")
                }
                
                if (targetConfig.outputs.isNotEmpty()) {
                    println("    Outputs: ${targetConfig.outputs.joinToString(", ")}")
                }
                
                if (targetConfig.configurations.isNotEmpty()) {
                    println("    Configurations:")
                    targetConfig.configurations.forEach { (configName, configOptions) ->
                        println("      $configName:")
                        configOptions.forEach { (key, value) ->
                            println("        $key: $value")
                        }
                    }
                }
                
                println("    Cache: ${if (targetConfig.cache) "✅ enabled" else "❌ disabled"}")
                println("    Parallel: ${if (targetConfig.parallelism) "✅ enabled" else "❌ disabled"}")
                println()
            }
        } else {
            println("🎯 Targets: None")
            println()
        }
        
        // Dependencies
        val dependencies = projectGraph.dependencies[project.name] ?: emptyList()
        if (dependencies.isNotEmpty()) {
            println("🔗 Dependencies:")
            dependencies.forEach { dep ->
                val depType = when (dep.type) {
                    com.forge.core.DependencyType.STATIC -> "static"
                    com.forge.core.DependencyType.DYNAMIC -> "dynamic"
                    com.forge.core.DependencyType.IMPLICIT -> "implicit"
                }
                println("  • ${dep.target} ($depType)")
            }
            println()
        }
        
        // Dependents (projects that depend on this one)
        val dependents = projectGraph.getAllProjects().filter { p ->
            val deps = projectGraph.dependencies[p.name] ?: emptyList()
            deps.any { it.target == project.name }
        }
        if (dependents.isNotEmpty()) {
            println("⬅️  Depended On By:")
            dependents.sortedBy { it.name }.forEach { dependent ->
                println("  • ${dependent.name}")
            }
            println()
        }
        
        // File location
        val projectPath = workspaceRoot.resolve(project.data.root)
        println("📂 Location:")
        println("  Absolute Path: $projectPath")
        
        val configFiles = mutableListOf<String>()
        if (projectPath.resolve("project.json").exists()) {
            configFiles.add("project.json")
        }
        if (projectPath.resolve("package.json").exists()) {
            configFiles.add("package.json")
        }
        if (projectPath.resolve("pom.xml").exists()) {
            configFiles.add("pom.xml")
        }
        if (projectPath.resolve("Dockerfile").exists()) {
            configFiles.add("Dockerfile")
        }
        
        if (configFiles.isNotEmpty()) {
            println("  Configuration Files: ${configFiles.joinToString(", ")}")
        }
        
        println()
        println("💡 Usage Examples:")
        println("  forge --target=build ${project.name}")
        println("  forge --target=test ${project.name}")
        project.data.targets.keys.take(3).forEach { targetName ->
            if (targetName != "build" && targetName != "test") {
                println("  forge --target=$targetName ${project.name}")
            }
        }
    }
    
    private fun findWorkspaceRoot(): Path {
        var current = Path.of("").absolute()
        while (current.parent != null) {
            if (current.resolve("forge.json").exists() || 
                current.resolve("nx.json").exists() ||
                current.resolve(".git").exists()) {
                return current
            }
            current = current.parent
        }
        return Path.of("").absolute()
    }
}