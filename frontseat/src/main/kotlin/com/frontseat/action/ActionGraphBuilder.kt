package com.frontseat.action

import com.frontseat.task.Task
import com.frontseat.task.TaskGraph
import com.frontseat.task.TaskId
import org.slf4j.LoggerFactory

/**
 * Builds fine-grained action graph from high-level task graph.
 * Inspired by Moon's approach where:
 * - Setup actions run once at the beginning
 * - Sync actions ensure consistency
 * - Dependencies are installed only when needed (lockfile changes)
 * - Tasks are decomposed into granular actions
 * - Interactive and persistent tasks get special handling
 */
class ActionGraphBuilder {
    private val logger = LoggerFactory.getLogger(ActionGraphBuilder::class.java)
    
    // Track what setup actions have been added (singleton pattern)
    private var setupWorkspaceAdded = false
    private var setupToolchainManagerAdded = false
    private var syncWorkspaceAdded = false
    private val projectsSynced = mutableSetOf<String>()
    private val projectDepsInstalled = mutableSetOf<String>()
    
    /**
     * Build action graph from task graph
     */
    fun buildActionGraph(taskGraph: TaskGraph): ActionGraph {
        val actionGraph = ActionGraph()
        val taskGates = mutableMapOf<TaskId, ActionId>()
        
        logger.info("Building action graph from ${taskGraph.size()} tasks")
        
        // Process tasks in topological order to ensure dependencies are handled correctly
        taskGraph.getTopologicalOrder().forEach { task ->
            val gateId = buildTaskActions(task, taskGraph, actionGraph, taskGates)
            taskGates[task.id] = gateId
        }
        
        logger.info("Built action graph with ${actionGraph.size()} actions")
        return actionGraph
    }
    
    /**
     * Build action subgraph for a single task
     * Returns the gate action ID that marks task completion
     */
    private fun buildTaskActions(
        task: Task,
        taskGraph: TaskGraph,
        actionGraph: ActionGraph,
        taskGates: Map<TaskId, ActionId>
    ): ActionId {
        val project = task.project
        val target = task.target
        
        logger.debug("Building actions for task ${task.id.value}")
        
        // Create action IDs for this task's pipeline
        val hashId = ActionId.generate(project, target, ActionType.COMPUTE_HASH)
        val checkCacheId = ActionId.generate(project, target, ActionType.CHECK_CACHE)
        val decisionId = ActionId.generate(project, target, ActionType.DECISION, "cache")
        
        // Tool-related actions (if tools are needed)
        val resolveToolIds = mutableListOf<ActionId>()
        val installToolIds = mutableListOf<ActionId>()
        val verifyToolIds = mutableListOf<ActionId>()
        
        // Extract required tools from task configuration
        val requiredTools = extractRequiredTools(task)
        requiredTools.forEach { tool ->
            val resolveId = ActionId.generate(project, target, ActionType.RESOLVE_TOOLCHAIN, tool)
            val installId = ActionId.generate(project, target, ActionType.INSTALL_TOOLCHAIN, tool)
            val verifyId = ActionId.generate(project, target, ActionType.VERIFY_TOOLCHAIN, tool)
            
            resolveToolIds.add(resolveId)
            installToolIds.add(installId)
            verifyToolIds.add(verifyId)
        }
        
        // Execution-related actions
        val sandboxId = ActionId.generate(project, target, ActionType.SETUP_WORKSPACE)
        val envId = ActionId.generate(project, target, ActionType.SYNC_PROJECT)
        val executeId = ActionId.generate(project, target, ActionType.RUN_TASK)
        val captureId = ActionId.generate(project, target, ActionType.CAPTURE_OUTPUTS)
        val fingerprintId = ActionId.generate(project, target, ActionType.FINGERPRINT)
        val saveCacheId = ActionId.generate(project, target, ActionType.SAVE_CACHE)
        val cleanupId = ActionId.generate(project, target, ActionType.CLEANUP)
        val gateId = ActionId.generate(project, target, ActionType.GATE, "complete")
        
        // Get dependencies from other tasks
        val taskDependencies = task.dependencies.mapNotNull { taskGates[it] }.toSet()
        
        // Build the action pipeline
        
        // 1. Hash computation (depends on task dependencies)
        actionGraph.addNode(ActionNode(
            id = hashId,
            type = ActionType.COMPUTE_HASH,
            project = project,
            target = target,
            inputs = mapOf(
                "files" to task.inputs,
                "configuration" to task.configuration
            ),
            dependencies = taskDependencies
        ))
        
        // 2. Cache check (depends on hash)
        actionGraph.addNode(ActionNode(
            id = checkCacheId,
            type = ActionType.CHECK_CACHE,
            project = project,
            target = target,
            dependencies = setOf(hashId),
            metadata = mapOf("cache" to task.cache)
        ))
        
        // 3. Cache decision point
        actionGraph.addNode(ActionNode(
            id = decisionId,
            type = ActionType.DECISION,
            project = project,
            target = target,
            dependencies = setOf(checkCacheId),
            metadata = mapOf("branches" to listOf("hit", "miss"))
        ))
        
        // 4. Tool resolution (parallel, depends on cache miss branch)
        resolveToolIds.forEachIndexed { index, resolveId ->
            actionGraph.addNode(ActionNode(
                id = resolveId,
                type = ActionType.RESOLVE_TOOLCHAIN,
                project = project,
                target = target,
                inputs = mapOf("tool" to requiredTools[index]),
                dependencies = setOf(decisionId)
            ))
        }
        
        // 5. Tool installation (depends on resolution)
        installToolIds.forEachIndexed { index, installId ->
            actionGraph.addNode(ActionNode(
                id = installId,
                type = ActionType.INSTALL_TOOLCHAIN,
                project = project,
                target = target,
                inputs = mapOf("tool" to requiredTools[index]),
                dependencies = setOf(resolveToolIds[index])
            ))
        }
        
        // 6. Tool verification (depends on installation)
        verifyToolIds.forEachIndexed { index, verifyId ->
            actionGraph.addNode(ActionNode(
                id = verifyId,
                type = ActionType.VERIFY_TOOLCHAIN,
                project = project,
                target = target,
                inputs = mapOf("tool" to requiredTools[index]),
                dependencies = setOf(installToolIds[index])
            ))
        }
        
        // 7. Sandbox preparation (depends on tools being ready)
        val sandboxDeps = if (verifyToolIds.isNotEmpty()) {
            verifyToolIds.toSet()
        } else {
            setOf(decisionId)
        }
        
        actionGraph.addNode(ActionNode(
            id = sandboxId,
            type = ActionType.SETUP_WORKSPACE,
            project = project,
            target = target,
            dependencies = sandboxDeps
        ))
        
        // 8. Environment setup
        actionGraph.addNode(ActionNode(
            id = envId,
            type = ActionType.SYNC_PROJECT,
            project = project,
            target = target,
            inputs = mapOf("env" to (task.configuration.options["env"] ?: emptyMap<String, String>())),
            dependencies = setOf(sandboxId)
        ))
        
        // 9. Command execution
        actionGraph.addNode(ActionNode(
            id = executeId,
            type = ActionType.RUN_TASK,
            project = project,
            target = target,
            inputs = mapOf(
                "command" to (task.configuration.options["command"] ?: ""),
                "cwd" to (task.configuration.options["cwd"] ?: ""),
                "timeout" to (task.configuration.options["timeout"] ?: 300)
            ),
            dependencies = setOf(envId),
            resources = extractResourceRequirements(task)
        ))
        
        // 10. Output capture
        actionGraph.addNode(ActionNode(
            id = captureId,
            type = ActionType.CAPTURE_OUTPUTS,
            project = project,
            target = target,
            inputs = mapOf("outputs" to task.outputs),
            dependencies = setOf(executeId)
        ))
        
        // 11. Fingerprinting
        actionGraph.addNode(ActionNode(
            id = fingerprintId,
            type = ActionType.FINGERPRINT,
            project = project,
            target = target,
            dependencies = setOf(captureId)
        ))
        
        // 12. Cache save (if caching enabled)
        if (task.cache) {
            actionGraph.addNode(ActionNode(
                id = saveCacheId,
                type = ActionType.SAVE_CACHE,
                project = project,
                target = target,
                dependencies = setOf(fingerprintId)
            ))
        }
        
        // 13. Cleanup
        val cleanupDeps = if (task.cache) setOf(saveCacheId) else setOf(fingerprintId)
        actionGraph.addNode(ActionNode(
            id = cleanupId,
            type = ActionType.CLEANUP,
            project = project,
            target = target,
            dependencies = cleanupDeps
        ))
        
        // 14. Completion gate
        actionGraph.addNode(ActionNode(
            id = gateId,
            type = ActionType.GATE,
            project = project,
            target = target,
            dependencies = setOf(cleanupId),
            metadata = mapOf("taskId" to task.id.value)
        ))
        
        return gateId
    }
    
    /**
     * Extract required tools from task configuration
     */
    private fun extractRequiredTools(task: Task): List<String> {
        val tools = mutableListOf<String>()
        
        // Only use explicit tool requirements from task metadata
        // Plugin-specific tool detection should be handled by the plugins themselves
        (task.metadata["requiredTools"] as? List<*>)?.forEach { tool ->
            if (tool is String) {
                tools.add(tool)
            }
        }
        
        return tools
    }
    
    /**
     * Extract resource requirements from task
     */
    private fun extractResourceRequirements(task: Task): Set<String> {
        val resources = mutableSetOf<String>()
        
        // Check for resource hints in configuration
        (task.configuration.options["resources"] as? Map<*, *>)?.forEach { (key, value) ->
            resources.add("$key:$value")
        }
        
        // Add default resources based on task type
        if ((task.configuration.options["command"] as? String)?.contains("test") == true) {
            resources.add("test-runner")
        }
        
        if ((task.configuration.options["command"] as? String)?.contains("build") == true) {
            resources.add("cpu:high")
        }
        
        return resources
    }
}