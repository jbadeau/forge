package com.frontseat.execution

import com.frontseat.project.ProjectGraph
import com.frontseat.plugin.api.ProjectConfiguration
import com.frontseat.plugin.api.TargetConfiguration
import com.frontseat.task.Task
import com.frontseat.task.TaskGraph
import com.frontseat.task.TaskId
import org.slf4j.LoggerFactory

/**
 * Enhanced task graph builder that creates lifecycle-aware DAGs with local edges only.
 * This implements the "Lifecycle with Local DAGs" strategy for high parallelism.
 */
class LifecycleTaskGraphBuilder(
    private val projectGraph: ProjectGraph,
    private val lifecycleConfig: LifecycleConfiguration = LifecycleConfiguration.DEFAULT
) {
    private val logger = LoggerFactory.getLogger(LifecycleTaskGraphBuilder::class.java)
    
    /**
     * Build a lifecycle-aware task graph with local edges for maximum parallelism
     */
    fun buildLifecycleTaskGraph(
        requestedTargets: Set<String>,
        projectNames: Set<String> = projectGraph.nodes.keys,
        options: LifecycleBuildOptions = LifecycleBuildOptions()
    ): TaskGraph {
        logger.info("Building lifecycle task graph for targets: $requestedTargets, projects: ${projectNames.size}")
        
        val tasks = mutableMapOf<TaskId, Task>()
        val dependencies = mutableMapOf<TaskId, MutableSet<TaskId>>()
        
        // Phase 1: Create all tasks for requested targets and their lifecycle dependencies
        projectNames.forEach { projectName ->
            val project = projectGraph.getProject(projectName) ?: return@forEach
            createLifecycleTasks(
                projectName, 
                project.data, 
                requestedTargets,
                tasks,
                dependencies,
                options
            )
        }
        
        // Phase 2: Add cross-project dependencies with phase matching
        addCrossProjectDependencies(tasks, dependencies, options)
        
        // Phase 3: Build the final task graph
        val taskGraph = TaskGraph()
        tasks.values.forEach { task ->
            val updatedTask = task.copy(dependencies = dependencies[task.id] ?: emptySet())
            taskGraph.addTask(updatedTask)
        }
        
        logger.info("Built lifecycle task graph with ${tasks.size} tasks")
        return taskGraph
    }
    
    /**
     * Create tasks for a project following lifecycle order
     */
    private fun createLifecycleTasks(
        projectName: String,
        projectConfig: ProjectConfiguration,
        requestedTargets: Set<String>,
        tasks: MutableMap<TaskId, Task>,
        dependencies: MutableMap<TaskId, MutableSet<TaskId>>,
        options: LifecycleBuildOptions
    ) {
        // Find all lifecycle phases needed for requested targets
        val requiredPhases = mutableSetOf<String>()
        requestedTargets.forEach { target ->
            if (projectConfig.hasTarget(target)) {
                requiredPhases.add(target)
                // Add prerequisite phases from lifecycle
                lifecycleConfig.getPrerequisites(target).forEach { prereq ->
                    if (projectConfig.hasTarget(prereq)) {
                        requiredPhases.add(prereq)
                    }
                }
            }
        }
        
        // Create tasks for all required phases
        requiredPhases.forEach { phaseName ->
            val taskId = TaskId.create(projectName, phaseName)
            if (tasks.containsKey(taskId)) return@forEach
            
            val targetConfig = projectConfig.getTarget(phaseName)!!
            tasks[taskId] = Task(
                id = taskId,
                project = projectName,
                target = phaseName,
                configuration = targetConfig,
                dependencies = emptySet(), // Will be populated later
                inputs = targetConfig.inputs ?: emptyList(),
                outputs = targetConfig.outputs ?: emptyList(),
                cache = targetConfig.isCacheable()
            )
            dependencies[taskId] = mutableSetOf()
        }
        
        // Add local lifecycle edges (within same project)
        addLocalLifecycleEdges(projectName, requiredPhases, dependencies, options)
    }
    
    /**
     * Add edges between lifecycle phases within the same project
     */
    private fun addLocalLifecycleEdges(
        projectName: String,
        phases: Set<String>,
        dependencies: MutableMap<TaskId, MutableSet<TaskId>>,
        options: LifecycleBuildOptions
    ) {
        phases.forEach { phase ->
            val taskId = TaskId.create(projectName, phase)
            
            // Add edges from prerequisite phases (local barriers only)
            lifecycleConfig.getPrerequisites(phase).forEach { prereq ->
                if (phases.contains(prereq)) {
                    val prereqId = TaskId.create(projectName, prereq)
                    dependencies[taskId]?.add(prereqId)
                }
            }
            
            // Add explicit dependencies from target configuration
            val project = projectGraph.getProject(projectName)
            val targetConfig = project?.data?.getTarget(phase)
            targetConfig?.dependsOn?.forEach { dep ->
                when {
                    dep.startsWith("^") -> {
                        // Upstream dependency - will be handled in cross-project phase
                    }
                    dep.contains(":") -> {
                        // Explicit project:target dependency
                        val parts = dep.split(":")
                        if (parts.size == 2) {
                            val depTaskId = TaskId.create(parts[0], parts[1])
                            dependencies[taskId]?.add(depTaskId)
                        }
                    }
                    else -> {
                        // Local target dependency
                        val depTaskId = TaskId.create(projectName, dep)
                        dependencies[taskId]?.add(depTaskId)
                    }
                }
            }
        }
    }
    
    /**
     * Add cross-project dependencies with intelligent phase matching
     */
    private fun addCrossProjectDependencies(
        tasks: Map<TaskId, Task>,
        dependencies: MutableMap<TaskId, MutableSet<TaskId>>,
        options: LifecycleBuildOptions
    ) {
        tasks.values.forEach { task ->
            val projectDeps = projectGraph.getDependencies(task.project)
            
            projectDeps.forEach { projectDep ->
                val upstreamProject = projectDep.target
                
                // Apply phase matching rules
                val phaseMatchRules = lifecycleConfig.getPhaseMatchingRules(task.target)
                phaseMatchRules.forEach { rule ->
                    val upstreamTaskId = TaskId.create(upstreamProject, rule.upstreamPhase)
                    
                    // Only add edge if upstream task exists
                    if (tasks.containsKey(upstreamTaskId)) {
                        // Check if rule applies based on conditions
                        if (rule.shouldApply(task, tasks[upstreamTaskId])) {
                            dependencies[task.id]?.add(upstreamTaskId)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Configuration for lifecycle phases and their relationships
 */
data class LifecycleConfiguration(
    val phases: List<String>,
    val phaseOrder: Map<String, Int>,
    val prerequisites: Map<String, Set<String>>,
    val phaseMatchingRules: Map<String, List<PhaseMatchingRule>>
) {
    fun getPrerequisites(phase: String): Set<String> = 
        prerequisites[phase] ?: emptySet()
    
    fun getPhaseMatchingRules(phase: String): List<PhaseMatchingRule> =
        phaseMatchingRules[phase] ?: emptyList()
    
    fun getPhaseIndex(phase: String): Int = 
        phaseOrder[phase] ?: Int.MAX_VALUE
    
    companion object {
        val DEFAULT = LifecycleConfiguration(
            phases = listOf("clean", "compile", "test", "package", "publish", "deploy"),
            phaseOrder = mapOf(
                "clean" to 0,
                "compile" to 1,
                "test" to 2,
                "package" to 3,
                "publish" to 4,
                "deploy" to 5
            ),
            prerequisites = mapOf(
                "test" to setOf("compile"),
                "package" to setOf("compile"),
                "publish" to setOf("package"),
                "deploy" to setOf("publish")
            ),
            phaseMatchingRules = mapOf(
                "compile" to listOf(
                    PhaseMatchingRule(
                        upstreamPhase = "compile",
                        condition = PhaseMatchCondition.ALWAYS
                    )
                ),
                "test" to listOf(
                    PhaseMatchingRule(
                        upstreamPhase = "compile",
                        condition = PhaseMatchCondition.IF_INTEGRATION_TEST
                    )
                ),
                "package" to listOf(
                    PhaseMatchingRule(
                        upstreamPhase = "package",
                        condition = PhaseMatchCondition.IF_EMBEDS_DEPENDENCY
                    )
                )
            )
        )
    }
}

/**
 * Rule for matching phases across project boundaries
 */
data class PhaseMatchingRule(
    val upstreamPhase: String,
    val condition: PhaseMatchCondition = PhaseMatchCondition.ALWAYS
) {
    fun shouldApply(downstreamTask: Task, upstreamTask: Task?): Boolean {
        if (upstreamTask == null) return false
        
        return when (condition) {
            PhaseMatchCondition.ALWAYS -> true
            PhaseMatchCondition.IF_INTEGRATION_TEST -> {
                // Check if downstream has integration test marker
                downstreamTask.target.contains("integration")
            }
            PhaseMatchCondition.IF_EMBEDS_DEPENDENCY -> {
                // Check if downstream packages embed upstream artifacts
                downstreamTask.target == "package" || downstreamTask.target == "build"
            }
            PhaseMatchCondition.IF_RUNTIME_DEPENDENCY -> {
                // Check if this is a runtime dependency
                downstreamTask.target == "test" || downstreamTask.target == "run"
            }
            PhaseMatchCondition.NEVER -> false
        }
    }
}

enum class PhaseMatchCondition {
    ALWAYS,              // Always add this edge
    IF_INTEGRATION_TEST, // Only if downstream has integration tests
    IF_EMBEDS_DEPENDENCY,// Only if downstream embeds the dependency
    IF_RUNTIME_DEPENDENCY,// Only if it's a runtime dependency
    NEVER               // Never add (useful for overrides)
}

/**
 * Options for building lifecycle-aware task graphs
 */
data class LifecycleBuildOptions(
    val enableSoftBarriers: Boolean = false,
    val preferPhaseAlignment: Boolean = true,
    val maxPhaseDistance: Int = 1, // How many phases ahead a project can get
    val enableCriticalPath: Boolean = true
)