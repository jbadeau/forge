package com.forge.plugin.sdk

import com.forge.actions.*
import com.forge.task.Task
import com.forge.task.TaskId
import com.forge.plugin.api.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream

/**
 * Testing utilities for plugins
 */
class PluginTestKit {
    
    /**
     * Create a test action graph
     */
    fun createTestGraph(): TestMutableActionGraph {
        return TestMutableActionGraph()
    }
    
    /**
     * Create a test task
     */
    fun createTestTask(
        project: String = "test-project",
        target: String = "test-target"
    ): Task {
        return Task(
            id = TaskId.create(project, target),
            project = project,
            target = target,
            configuration = TargetConfiguration(),
            dependencies = emptySet(),
            inputs = listOf("src/**"),
            outputs = listOf("dist/**"),
            cache = true
        )
    }
    
    /**
     * Assert that actions are in the expected sequence
     */
    fun assertActionSequence(
        graph: TestMutableActionGraph,
        expected: List<ActionType>
    ) {
        val actual = graph.nodes.map { it.type }
        if (expected != actual) {
            throw AssertionError("Action sequence mismatch. Expected: $expected, Actual: $actual")
        }
    }
    
    /**
     * Assert that an action exists with specific properties
     */
    fun assertActionExists(
        graph: TestMutableActionGraph,
        type: ActionType,
        project: String? = null,
        target: String? = null,
        inputs: Map<String, Any>? = null
    ) {
        val matching = graph.nodes.filter { node ->
            node.type == type &&
            (project == null || node.project == project) &&
            (target == null || node.target == target) &&
            (inputs == null || inputs.all { (k, v) -> node.inputs[k] == v })
        }
        
        if (matching.isEmpty()) {
            throw AssertionError("No action found matching criteria: type=$type, project=$project, target=$target, inputs=$inputs")
        }
    }
}

/**
 * Test implementation of MutableActionGraph
 */
class TestMutableActionGraph : MutableActionGraph {
    val nodes = mutableListOf<ActionNode>()
    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    
    override fun addNode(node: ActionNode) {
        nodes.add(node)
    }
    
    override fun addDependency(from: String, to: String) {
        dependencies.computeIfAbsent(to) { mutableSetOf() }.add(from)
    }
    
    override fun insertBefore(targetId: String, node: ActionNode) {
        val targetIndex = nodes.indexOfFirst { it.id.value == targetId }
        if (targetIndex != -1) {
            nodes.add(targetIndex, node)
        } else {
            nodes.add(node)
        }
    }
    
    override fun insertAfter(targetId: String, node: ActionNode) {
        val targetIndex = nodes.indexOfFirst { it.id.value == targetId }
        if (targetIndex != -1) {
            nodes.add(targetIndex + 1, node)
        } else {
            nodes.add(node)
        }
    }
    
    override fun wrapTask(taskId: String, before: ActionNode, after: ActionNode) {
        nodes.add(0, before)
        nodes.add(after)
    }
    
    override fun replaceAction(actionId: String, newNode: ActionNode) {
        val index = nodes.indexOfFirst { it.id.value == actionId }
        if (index != -1) {
            nodes[index] = newNode
        }
    }
    
    override fun removeAction(actionId: String) {
        nodes.removeIf { it.id.value == actionId }
    }
    
    override fun getTaskActions(taskId: String): List<ActionNode> {
        return nodes.filter { node ->
            node.metadata["taskId"] == taskId ||
            node.id.value.startsWith("$taskId#") ||
            node.id.value.startsWith("$taskId-")
        }
    }
}