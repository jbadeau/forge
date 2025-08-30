package com.forge.integration

import com.forge.discovery.ProjectDiscovery
import com.forge.execution.TaskGraphBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.nio.file.Paths

class ForgeIntegrationTest {
    
    private lateinit var testWorkspaceRoot: Path
    
    @BeforeEach 
    fun setup() {
        val resourcesPath = this::class.java.classLoader.getResource("test-workspace")?.toURI()
        assertNotNull(resourcesPath, "Test workspace not found in resources")
        testWorkspaceRoot = Paths.get(resourcesPath!!)
    }
    
    @Test
    fun `should demonstrate complete forge workflow`() {
        println("🔧 FORGE - Kotlin Build Tool Demonstration")
        println("=" * 50)
        
        // Step 1: Discover projects
        println("\n📁 STEP 1: Discovering Projects")
        println("-" * 30)
        
        val discovery = ProjectDiscovery(testWorkspaceRoot)
        val projectGraph = discovery.discoverProjects()
        
        println("Workspace: $testWorkspaceRoot")
        println("Found ${projectGraph.nodes.size} projects:")
        
        projectGraph.getAllProjects().sortedBy { it.name }.forEach { project ->
            println("  📦 ${project.name}")
            println("    Type: ${project.data.projectType}")
            println("    Root: ${project.data.root}")
            println("    Tags: ${project.data.tags.joinToString(", ")}")
            println("    Targets: ${project.data.getTargetNames().joinToString(", ")}")
            
            val deps = projectGraph.getDependencies(project.name)
            if (deps.isNotEmpty()) {
                println("    Dependencies: ${deps.map { it.target }.joinToString(", ")}")
            }
            println()
        }
        
        // Step 2: Analyze project structure
        println("📊 STEP 2: Project Structure Analysis")
        println("-" * 35)
        
        val libraries = projectGraph.getProjectsByType("library")
        val applications = projectGraph.getProjectsByType("application")
        
        println("Libraries (${libraries.size}):")
        libraries.forEach { lib ->
            println("  📚 ${lib.name} - ${lib.data.tags.joinToString(", ")}")
        }
        
        println("\nApplications (${applications.size}):")
        applications.forEach { app ->
            println("  🚀 ${app.name} - ${app.data.tags.joinToString(", ")}")
        }
        
        // Step 3: Build task graphs for different targets
        println("\n⚙️  STEP 3: Task Graph Construction")
        println("-" * 35)
        
        val taskGraphBuilder = TaskGraphBuilder(projectGraph)
        
        // Build task graph for 'build' target
        println("Building task graph for 'build' target...")
        val buildTaskGraph = taskGraphBuilder.buildTaskGraph("build")
        
        println("Tasks to execute: ${buildTaskGraph.size()}")
        buildTaskGraph.getAllTasks().forEach { task ->
            val deps = buildTaskGraph.getDependencies(task.id)
            println("  ⚡ ${task.id}")
            if (deps.isNotEmpty()) {
                println("    Depends on: ${deps.joinToString(", ")}")
            }
        }
        
        // Show execution plan
        println("\n📋 Execution Plan (Topological Order):")
        val executionPlan = buildTaskGraph.getExecutionPlan()
        executionPlan.layers.forEachIndexed { index, layer ->
            println("  Layer ${index + 1}: ${layer.map { it.id }.joinToString(", ")}")
        }
        println("  Max parallelism: ${executionPlan.maxParallelism} concurrent tasks")
        
        // Step 4: Test affected project calculation
        println("\n🎯 STEP 4: Affected Project Analysis")
        println("-" * 35)
        
        // Simulate changes to the 'utils' library
        val affectedProjects = setOf("utils")
        val affectedTaskGraph = taskGraphBuilder.buildAffectedTaskGraph("build", affectedProjects)
        
        println("If 'utils' library changes, affected projects for 'build' target:")
        affectedTaskGraph.getAllTasks().forEach { task ->
            println("  🔄 ${task.id}")
        }
        
        // Step 5: Test different target types
        println("\n🧪 STEP 5: Different Target Types")
        println("-" * 30)
        
        listOf("test", "lint").forEach { target ->
            val targetTaskGraph = taskGraphBuilder.buildTaskGraph(target)
            if (targetTaskGraph.size() > 0) {
                println("${target.uppercase()} tasks (${targetTaskGraph.size()}):")
                targetTaskGraph.getAllTasks().forEach { task ->
                    println("  📝 ${task.id}")
                }
                println()
            }
        }
        
        // Step 6: Demonstrate filtering and querying
        println("🔍 STEP 6: Project Filtering & Querying")
        println("-" * 35)
        
        println("Projects with 'scope:shared' tag:")
        projectGraph.getProjectsByTag("scope:shared").forEach { project ->
            println("  🏷️  ${project.name}")
        }
        
        println("\nTransitive dependencies of 'web' project:")
        val webDeps = projectGraph.getTransitiveDependencies("web")
        if (webDeps.isEmpty()) {
            println("  (no dependencies - implicit dependencies feature removed)")
        } else {
            webDeps.forEach { dep ->
                println("  ⬅️  $dep")
            }
        }
        
        println("\nProjects that depend on 'utils':")
        val utilsDependents = projectGraph.getTransitiveDependents("utils")
        if (utilsDependents.isEmpty()) {
            println("  (no dependents - implicit dependencies feature removed)")
        } else {
            utilsDependents.forEach { dependent ->
                println("  ➡️  $dependent")
            }
        }
        
        println("\n✅ FORGE Demonstration Complete!")
        println("=" * 50)
        
        // Assertions to ensure everything worked correctly
        assertTrue(projectGraph.nodes.size > 0, "Should discover projects")
        assertTrue(buildTaskGraph.size() > 0, "Should create build tasks")
        assertTrue(executionPlan.layers.isNotEmpty(), "Should create execution plan")
    }
    
    @Test
    fun `should handle project specific task execution`() {
        val discovery = ProjectDiscovery(testWorkspaceRoot)
        val projectGraph = discovery.discoverProjects()
        val taskGraphBuilder = TaskGraphBuilder(projectGraph)
        
        println("\n🎯 Project-Specific Task Execution")
        println("-" * 35)
        
        // Build only specific projects
        val specificProjects = listOf("web", "api")
        val specificTaskGraph = taskGraphBuilder.buildTaskGraphForProjects("build", specificProjects)
        
        println("Building only specific projects: ${specificProjects.joinToString(", ")}")
        println("Tasks in execution order:")
        
        val executionPlan = specificTaskGraph.getExecutionPlan()
        executionPlan.layers.forEachIndexed { index, layer ->
            println("  Layer ${index + 1}:")
            layer.forEach { task ->
                println("    🔨 ${task.id}")
                println("      Project: ${task.projectName}")
                println("      Target: ${task.targetName}")
                println("      Cacheable: ${task.isCacheable()}")
                println("      Can run in parallel: ${task.canRunInParallel()}")
            }
        }
        
        // Verify only explicitly requested projects are included (no implicit dependencies)
        val taskIds = specificTaskGraph.getAllTasks().map { it.id }
        println("\nAll tasks that will be executed:")
        taskIds.sorted().forEach { taskId ->
            println("  ✓ $taskId")
        }
        
        assertTrue(taskIds.contains("web:build"), "Should include web:build as requested")
        assertTrue(taskIds.contains("api:build"), "Should include api:build as requested")
        assertFalse(taskIds.contains("utils:build"), "Should not include utils:build (no implicit dependency)")
        assertFalse(taskIds.contains("ui:build"), "Should not include ui:build (no implicit dependency)")
        assertEquals(2, taskIds.size, "Should only have the 2 explicitly requested tasks")
    }
    
    private operator fun String.times(n: Int): String = this.repeat(n)
}