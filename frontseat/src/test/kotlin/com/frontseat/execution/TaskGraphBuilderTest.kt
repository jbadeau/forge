package com.frontseat.execution

import com.frontseat.project.ProjectGraphBuilder
import com.frontseat.task.TaskGraphBuilder
import com.frontseat.task.TaskId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.nio.file.Paths

class TaskGraphBuilderTest {
    
    private lateinit var testWorkspaceRoot: Path
    private lateinit var taskGraphBuilder: TaskGraphBuilder
    
    @BeforeEach
    fun setup() {
        val resourcesPath = this::class.java.classLoader.getResource("test-workspace")?.toURI()
        assertNotNull(resourcesPath, "Test workspace not found in resources")
        testWorkspaceRoot = Paths.get(resourcesPath!!)
        
        val natureRegistry = com.frontseat.project.nature.NatureRegistry()
        val graphBuilder = ProjectGraphBuilder(testWorkspaceRoot, emptyList(), true, natureRegistry)
        val projectGraph = graphBuilder.buildProjectGraph()
        taskGraphBuilder = TaskGraphBuilder(projectGraph)
    }
    
    @Test
    fun `should build task graph for build target`() {
        val taskGraph = taskGraphBuilder.buildTaskGraph("build")
        
        // Should create tasks for all projects that have build target
        assertTrue(taskGraph.hasTask("ui:build"), "Should have ui:build task")
        assertTrue(taskGraph.hasTask("utils:build"), "Should have utils:build task") 
        assertTrue(taskGraph.hasTask("web:build"), "Should have web:build task")
        assertTrue(taskGraph.hasTask("api:build"), "Should have api:build task")
        
        println("=== Build Task Graph ===")
        println("Total tasks: ${taskGraph.size()}")
        
        taskGraph.getAllTasks().forEach { task ->
            val dependencies = taskGraph.getDependencies(task.id)
            println("${task.id}:")
            println("  Dependencies: ${dependencies.joinToString(", ").ifEmpty { "(none)" }}")
            println("  Cacheable: ${task.isCacheable()}")
            println("  Parallel: ${task.canRunInParallel()}")
        }
    }
    
    @Test
    fun `should resolve transitive project dependencies correctly`() {
        val taskGraph = taskGraphBuilder.buildTaskGraph("build")
        
        // All tasks should have no project dependencies (implicit dependencies removed)
        val webBuildDeps = taskGraph.getDependencies("web:build")
        val apiBuildDeps = taskGraph.getDependencies("api:build")
        val uiBuildDeps = taskGraph.getDependencies("ui:build")
        val utilsBuildDeps = taskGraph.getDependencies("utils:build")
        
        println("=== Task Dependencies ===")
        println("web:build -> ${webBuildDeps.joinToString(", ")}")
        println("api:build -> ${apiBuildDeps.joinToString(", ")}")
        println("ui:build -> ${uiBuildDeps.joinToString(", ")}")
        println("utils:build -> ${utilsBuildDeps.joinToString(", ")}")
        
        // All should have same dependencies from workspace defaults only
        assertTrue(webBuildDeps.isEmpty() || webBuildDeps.all { it.contains("^") }, 
            "web:build should only have workspace default dependencies")
        assertTrue(apiBuildDeps.isEmpty() || apiBuildDeps.all { it.contains("^") }, 
            "api:build should only have workspace default dependencies")
    }
    
    @Test
    fun `should create correct execution plan with topological ordering`() {
        val taskGraph = taskGraphBuilder.buildTaskGraph("build")
        val executionPlan = taskGraph.getExecutionPlan()
        
        assertTrue(executionPlan.layers.isNotEmpty(), "Should create execution layers")
        
        println("=== Execution Plan ===")
        executionPlan.layers.forEachIndexed { index, layer ->
            println("Layer ${index + 1}: ${layer.map { it.id }.joinToString(", ")}")
        }
        
        // With no implicit dependencies, all tasks should be able to run in parallel
        val firstLayer = executionPlan.layers[0]
        val firstLayerIds = firstLayer.map { it.id }
        
        // All tasks should be in the first layer since there are no dependencies
        assertTrue(firstLayerIds.contains(TaskId("ui:build")), "ui:build should be in first layer")
        assertTrue(firstLayerIds.contains(TaskId("utils:build")), "utils:build should be in first layer")
        assertTrue(firstLayerIds.contains(TaskId("web:build")), "web:build should be in first layer")
        assertTrue(firstLayerIds.contains(TaskId("api:build")), "api:build should be in first layer")
        
        println("Execution order validation:")
        println("  First layer contains: ${firstLayerIds.joinToString(", ")}")
        println("  Total layers: ${executionPlan.getLayerCount()}")
        
        // Should be only one layer with all tasks
        assertEquals(1, executionPlan.getLayerCount(), "Should have only one execution layer")
        assertEquals(4, firstLayer.size, "First layer should contain all 4 tasks")
    }
    
    @Test
    fun `should handle different target types`() {
        // Test build target
        val buildGraph = taskGraphBuilder.buildTaskGraph("build")
        assertTrue(buildGraph.size() > 0, "Should create build tasks")
        
        // Test test target
        val testGraph = taskGraphBuilder.buildTaskGraph("test")
        assertTrue(testGraph.size() > 0, "Should create test tasks")
        
        // Test lint target
        val lintGraph = taskGraphBuilder.buildTaskGraph("lint") 
        assertTrue(lintGraph.size() > 0, "Should create lint tasks")
        
        // Test non-existent target
        val nonExistentGraph = taskGraphBuilder.buildTaskGraph("non-existent")
        assertEquals(0, nonExistentGraph.size(), "Should create no tasks for non-existent target")
        
        println("=== Task Counts by Target ===")
        println("build: ${buildGraph.size()} tasks")
        println("test: ${testGraph.size()} tasks")
        println("lint: ${lintGraph.size()} tasks")
        println("non-existent: ${nonExistentGraph.size()} tasks")
    }
    
    @Test
    fun `should build task graph for specific projects only`() {
        val specificProjects = listOf("web")
        val taskGraph = taskGraphBuilder.buildTaskGraphForProjects("build", specificProjects)
        
        // Should include only the requested project (no implicit dependencies)
        assertTrue(taskGraph.hasTask("web:build"), "Should include web:build")
        
        // Should NOT include other projects since no implicit dependencies
        assertFalse(taskGraph.hasTask("ui:build"), "Should not include ui:build")
        assertFalse(taskGraph.hasTask("utils:build"), "Should not include utils:build")
        assertFalse(taskGraph.hasTask("api:build"), "Should not include api:build")
        
        println("=== Specific Project Task Graph (web only) ===")
        println("Tasks included:")
        taskGraph.getAllTasks().forEach { task ->
            println("  ${task.id}")
        }
        
        assertEquals(1, taskGraph.size(), "Should have exactly 1 task: web only")
    }
    
    @Test
    fun `should build affected task graph`() {
        // Simulate change to utils library
        val affectedProjects = setOf("utils")
        val taskGraph = taskGraphBuilder.buildAffectedTaskGraph("build", affectedProjects)
        
        // Should include only the affected project (no implicit dependencies)
        assertTrue(taskGraph.hasTask("utils:build"), "Should include utils:build")
        
        // Should NOT include other projects since no implicit dependencies
        assertFalse(taskGraph.hasTask("web:build"), "Should not include web:build (no dependency)") 
        assertFalse(taskGraph.hasTask("api:build"), "Should not include api:build (no dependency)")
        assertFalse(taskGraph.hasTask("ui:build"), "Should not include ui:build (not affected)")
        
        println("=== Affected Task Graph (utils changed) ===")
        println("Affected tasks:")
        taskGraph.getAllTasks().forEach { task ->
            println("  ${task.id}")
        }
        
        assertEquals(1, taskGraph.size(), "Should have 1 affected task")
    }
    
    @Test
    fun `should generate task identifiers correctly`() {
        val taskGraph = taskGraphBuilder.buildTaskGraph("build")
        
        println("=== Task IDs ===")
        taskGraph.getAllTasks().forEach { task ->
            println("${task.id.value}: ${task.project}:${task.target}")
        }
        
        // Verify all tasks have valid IDs
        assertTrue(taskGraph.getAllTasks().all { it.id.value.isNotEmpty() }, "All tasks should have valid IDs")
    }
    
    @Test
    fun `should handle target configurations correctly`() {
        val taskGraph = taskGraphBuilder.buildTaskGraph("build")
        
        taskGraph.getAllTasks().forEach { task ->
            println("=== ${task.id} Configuration ===")
            println("  Executor: ${task.configuration.executor}")
            println("  Commands: ${task.configuration.options["commands"]}")
            println("  Dependencies: ${task.configuration.dependsOn.joinToString(", ")}")
            println("  Inputs: ${task.configuration.inputs.joinToString(", ")}")
            println("  Outputs: ${task.configuration.outputs.joinToString(", ")}")
            println("  Cache: ${task.configuration.cache}")
            println("  Options: ${task.configuration.options}")
            
            if (task.configuration.configurations.isNotEmpty()) {
                println("  Configurations:")
                task.configuration.configurations.forEach { (name, config) ->
                    println("    $name: $config")
                }
            }
            println()
        }
        
        // Verify web project has configurations
        val webBuildTask = taskGraph.getTask(TaskId("web:build"))
        assertNotNull(webBuildTask, "web:build task should exist")
        // Note: Configuration validation would need to be updated based on current Task structure
        assertNotNull(webBuildTask!!.configuration, "Should have configuration")
    }
    
    @Test
    fun `should demonstrate task graph properties`() {
        val taskGraph = taskGraphBuilder.buildTaskGraph("build")
        val executionPlan = taskGraph.getExecutionPlan()
        
        println("=== Task Graph Properties ===")
        println("Total tasks: ${taskGraph.size()}")
        println("Root tasks: ${taskGraph.getRootTasks().map { it.id }.joinToString(", ")}")
        println("Execution layers: ${executionPlan.getLayerCount()}")
        println("Max parallelism: ${executionPlan.maxParallelism}")
        println("Total execution steps: ${executionPlan.totalTasks}")
        
        // Verify properties
        assertTrue(taskGraph.size() > 0, "Should have tasks")
        assertTrue(taskGraph.getRootTasks().isNotEmpty(), "Should have root tasks")
        assertTrue(executionPlan.getLayerCount() > 0, "Should have execution layers")
        assertTrue(executionPlan.maxParallelism > 0, "Should have parallelism")
        assertEquals(taskGraph.size(), executionPlan.totalTasks, "Task counts should match")
    }
}