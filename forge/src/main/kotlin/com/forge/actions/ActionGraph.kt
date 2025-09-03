package com.forge.actions

import com.forge.plugin.api.MutableActionGraph
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Fine-grained action graph where tasks are decomposed into multiple actions.
 * Each action represents a single atomic operation in the build process.
 */

// Action types representing different operations in the build
// Inspired by Moon's action graph architecture
enum class ActionType {
    // Setup actions (run once at the beginning)
    SETUP_WORKSPACE,      // One-time workspace initialization
    SETUP_TOOLCHAIN_MANAGER, // Setup proto/nix/asdf etc
    
    // Sync actions (ensure consistency)
    SYNC_WORKSPACE,       // Sync workspace-level configs/tools
    SYNC_PROJECT,        // Sync project-specific configs
    
    // Dependency management
    INSTALL_WORKSPACE_DEPS, // Install workspace-level deps (e.g., root package.json)
    INSTALL_PROJECT_DEPS,   // Install project-level deps
    
    // Hash computation for inputs
    COMPUTE_HASH,
    
    // Cache operations
    CHECK_CACHE,
    RESTORE_CACHE,
    SAVE_CACHE,
    
    // Toolchain management (per-tool actions)
    RESOLVE_TOOLCHAIN,    // Resolve tool version (e.g., node 18.x)
    INSTALL_TOOLCHAIN,    // Install tool (e.g., via Nix, proto, etc.)
    VERIFY_TOOLCHAIN,     // Verify tool is working
    
    // Task execution
    RUN_TASK,            // Execute the actual task (interactive/persistent/normal)
    RUN_PERSISTENT_TASK, // Special handling for servers/watchers
    RUN_INTERACTIVE_TASK, // Special handling for tasks needing stdin
    
    // Output handling
    CAPTURE_OUTPUTS,     // Collect task outputs
    FINGERPRINT,        // Generate fingerprints for outputs
    
    // Cleanup
    CLEANUP,            // Clean up temporary resources
    
    // Control flow
    GATE,              // Synchronization point
    DECISION          // Conditional branch (e.g., based on cache hit)
}

/**
 * Unique identifier for an action
 */
data class ActionId(val value: String) {
    companion object {
        fun generate(project: String, target: String, type: ActionType, suffix: String = ""): ActionId {
            val id = if (suffix.isNotEmpty()) {
                "$project:$target#${type.name.lowercase()}-$suffix"
            } else {
                "$project:$target#${type.name.lowercase()}"
            }
            return ActionId(id)
        }
    }
}

/**
 * Single action node in the graph
 */
data class ActionNode(
    val id: ActionId,
    val type: ActionType,
    val project: String,
    val target: String,
    val inputs: Map<String, Any> = emptyMap(),      // Action-specific inputs
    val metadata: Map<String, Any> = emptyMap(),    // Additional metadata
    val dependencies: Set<ActionId> = emptySet(),   // Actions that must complete before this one
    val resources: Set<String> = emptySet()         // Resource requirements (CPU, memory, etc.)
)

/**
 * Result of executing an action
 */
sealed interface ActionResult {
    data class Success(
        val outputs: Map<String, Any> = emptyMap(),
        val duration: Long = 0
    ) : ActionResult
    
    data class Failed(
        val error: String,
        val cause: Throwable? = null
    ) : ActionResult
    
    data class Skipped(
        val reason: String
    ) : ActionResult
    
    data class CacheHit(
        val cacheKey: String,
        val restoredFrom: String // local/remote
    ) : ActionResult
}

/**
 * The action graph structure with plugin support
 */
class ActionGraph : MutableActionGraph {
    private val nodes = ConcurrentHashMap<ActionId, ActionNode>()
    private val edges = ConcurrentHashMap<ActionId, MutableSet<ActionId>>()
    private val reverseEdges = ConcurrentHashMap<ActionId, MutableSet<ActionId>>()
    
    override fun addNode(node: ActionNode) {
        nodes[node.id] = node
        // Initialize edges
        edges.putIfAbsent(node.id, ConcurrentHashMap.newKeySet())
        reverseEdges.putIfAbsent(node.id, ConcurrentHashMap.newKeySet())
        
        // Add edges from dependencies
        node.dependencies.forEach { depId ->
            edges.computeIfAbsent(depId) { ConcurrentHashMap.newKeySet() }.add(node.id)
            reverseEdges.computeIfAbsent(node.id) { ConcurrentHashMap.newKeySet() }.add(depId)
        }
    }
    
    override fun addDependency(from: String, to: String) {
        val fromId = ActionId(from)
        val toId = ActionId(to)
        edges.computeIfAbsent(fromId) { ConcurrentHashMap.newKeySet() }.add(toId)
        reverseEdges.computeIfAbsent(toId) { ConcurrentHashMap.newKeySet() }.add(fromId)
    }
    
    override fun insertBefore(targetId: String, node: ActionNode) {
        val target = ActionId(targetId)
        val targetNode = nodes[target] ?: throw IllegalArgumentException("Target action not found: $targetId")
        
        // Add the new node
        val newNode = node.copy(dependencies = node.dependencies + target)
        addNode(newNode)
        
        // Update nodes that depend on target to depend on new node instead
        reverseEdges[target]?.forEach { predecessorId ->
            val predecessor = nodes[predecessorId]!!
            val updatedDeps = predecessor.dependencies.map { 
                if (it == target) node.id else it 
            }.toSet()
            nodes[predecessorId] = predecessor.copy(dependencies = updatedDeps)
        }
    }
    
    override fun insertAfter(targetId: String, node: ActionNode) {
        val target = ActionId(targetId)
        val targetNode = nodes[target] ?: throw IllegalArgumentException("Target action not found: $targetId")
        
        // Get successors of target
        val successors = edges[target] ?: emptySet()
        
        // Add new node depending on target
        val newNode = node.copy(dependencies = setOf(target))
        addNode(newNode)
        
        // Update successors to depend on new node instead of target
        successors.forEach { successorId ->
            val successor = nodes[successorId]!!
            val updatedDeps = successor.dependencies + node.id
            nodes[successorId] = successor.copy(dependencies = updatedDeps)
        }
    }
    
    override fun wrapTask(taskId: String, before: ActionNode, after: ActionNode) {
        val taskActions = getTaskActions(taskId)
        if (taskActions.isEmpty()) {
            throw IllegalArgumentException("No actions found for task: $taskId")
        }
        
        val firstAction = taskActions.minByOrNull { it.dependencies.size }!!
        val lastAction = taskActions.maxByOrNull { edges[it.id]?.size ?: 0 }!!
        
        insertBefore(firstAction.id.value, before)
        insertAfter(lastAction.id.value, after)
    }
    
    override fun replaceAction(actionId: String, newNode: ActionNode) {
        val oldId = ActionId(actionId)
        val oldNode = nodes[oldId] ?: throw IllegalArgumentException("Action not found: $actionId")
        
        // Preserve dependencies
        val updatedNode = newNode.copy(
            id = oldId,
            dependencies = oldNode.dependencies
        )
        
        nodes[oldId] = updatedNode
    }
    
    override fun removeAction(actionId: String) {
        val id = ActionId(actionId)
        val node = nodes[id] ?: return
        
        // Connect predecessors directly to successors
        val predecessors = reverseEdges[id] ?: emptySet()
        val successors = edges[id] ?: emptySet()
        
        successors.forEach { successorId ->
            val successor = nodes[successorId]!!
            val updatedDeps = (successor.dependencies - id) + predecessors
            nodes[successorId] = successor.copy(dependencies = updatedDeps)
        }
        
        // Remove the node
        nodes.remove(id)
        edges.remove(id)
        reverseEdges.remove(id)
        
        // Clean up references
        edges.values.forEach { it.remove(id) }
        reverseEdges.values.forEach { it.remove(id) }
    }
    
    override fun getTaskActions(taskId: String): List<ActionNode> {
        return nodes.values.filter { node ->
            node.metadata["taskId"] == taskId ||
            node.id.value.startsWith("$taskId#") ||
            node.id.value.startsWith("$taskId-")
        }
    }
    
    fun getNode(id: ActionId): ActionNode? = nodes[id]
    
    fun getAllNodes(): Collection<ActionNode> = nodes.values
    
    fun getSuccessors(id: ActionId): Set<ActionId> = edges[id] ?: emptySet()
    
    fun getDependencies(id: ActionId): Set<ActionId> = nodes[id]?.dependencies ?: emptySet()
    
    fun getRootNodes(): List<ActionNode> {
        return nodes.values.filter { it.dependencies.isEmpty() }
    }
    
    fun getLeafNodes(): List<ActionNode> {
        return nodes.values.filter { node ->
            edges[node.id]?.isEmpty() ?: true
        }
    }
    
    fun size(): Int = nodes.size
    
    /**
     * Get execution layers (nodes that can run in parallel)
     */
    fun getExecutionLayers(): List<List<ActionNode>> {
        val layers = mutableListOf<List<ActionNode>>()
        val executed = mutableSetOf<ActionId>()
        val remaining = nodes.keys.toMutableSet()
        
        while (remaining.isNotEmpty()) {
            val layer = remaining.filter { id ->
                val node = nodes[id]!!
                node.dependencies.all { it in executed }
            }.mapNotNull { nodes[it] }
            
            if (layer.isEmpty()) {
                throw IllegalStateException("Circular dependency detected in action graph")
            }
            
            layers.add(layer)
            layer.forEach { 
                executed.add(it.id)
                remaining.remove(it.id)
            }
        }
        
        return layers
    }
    
    /**
     * Get critical path (longest path through the graph)
     */
    fun getCriticalPath(): List<ActionNode> {
        val distances = mutableMapOf<ActionId, Int>()
        val predecessors = mutableMapOf<ActionId, ActionId?>()
        
        // Initialize distances
        nodes.keys.forEach { distances[it] = 0 }
        
        // Calculate longest paths
        getExecutionLayers().forEach { layer ->
            layer.forEach { node ->
                val currentDistance = distances[node.id] ?: 0
                edges[node.id]?.forEach { successorId ->
                    val newDistance = currentDistance + 1
                    if (newDistance > (distances[successorId] ?: 0)) {
                        distances[successorId] = newDistance
                        predecessors[successorId] = node.id
                    }
                }
            }
        }
        
        // Find the node with maximum distance
        val endNode = distances.maxByOrNull { it.value }?.key ?: return emptyList()
        
        // Reconstruct path
        val path = mutableListOf<ActionNode>()
        var current: ActionId? = endNode
        while (current != null) {
            nodes[current]?.let { path.add(0, it) }
            current = predecessors[current]
        }
        
        return path
    }
}