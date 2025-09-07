package com.frontseat.project

import com.frontseat.plugin.api.ProjectConfiguration
import com.frontseat.plugin.api.DependencyType

import java.nio.file.Path

data class ProjectGraph(
    val nodes: Map<String, ProjectGraphNode>,
    val dependencies: Map<String, List<ProjectGraphDependency>>,
    val roots: List<String> = emptyList(),
    val externalNodes: Map<String, ProjectGraphExternalNode> = emptyMap(),
    val version: String? = null
) {
    fun getProject(name: String): ProjectGraphNode? = nodes[name]
    
    fun getDependencies(projectName: String): List<ProjectGraphDependency> = 
        dependencies[projectName] ?: emptyList()
    
    fun getAllProjects(): List<ProjectGraphNode> = nodes.values.toList()
    
    fun getProjectsByTag(tag: String): List<ProjectGraphNode> = 
        nodes.values.filter { it.data.tags.contains(tag) }
    
    fun getProjectsByType(type: String): List<ProjectGraphNode> = 
        nodes.values.filter { it.data.projectType == type }
    
    fun hasProject(name: String): Boolean = nodes.containsKey(name)
    
    /**
     * Returns projects in topologically sorted order (respecting dependencies)
     */
    fun topologicalSort(): List<List<String>> {
        val layers = mutableListOf<List<String>>()
        val inDegree = nodes.keys.associateWith { node ->
            dependencies.values.flatten().count { it.target == node }
        }.toMutableMap()
        val processed = mutableSetOf<String>()
        
        while (processed.size < nodes.size) {
            val ready = inDegree.entries
                .filter { it.value == 0 && !processed.contains(it.key) }
                .map { it.key }
            
            if (ready.isEmpty()) {
                val remaining = nodes.keys - processed
                throw IllegalStateException("Circular dependency detected in project graph. Remaining projects: $remaining")
            }
            
            layers.add(ready)
            processed.addAll(ready)
            
            ready.forEach { projectId ->
                getDependencies(projectId).forEach { dep ->
                    inDegree[dep.target] = inDegree[dep.target]!! - 1
                }
            }
        }
        
        return layers
    }
    
    fun getTransitiveDependencies(projectName: String): Set<String> {
        val visited = mutableSetOf<String>()
        val result = mutableSetOf<String>()
        
        fun dfs(current: String) {
            if (visited.contains(current)) return
            visited.add(current)
            
            getDependencies(current).forEach { dep ->
                result.add(dep.target)
                dfs(dep.target)
            }
        }
        
        dfs(projectName)
        return result
    }
    
    fun getTransitiveDependents(projectName: String): Set<String> {
        val dependents = mutableSetOf<String>()
        
        nodes.keys.forEach { project ->
            if (getTransitiveDependencies(project).contains(projectName)) {
                dependents.add(project)
            }
        }
        
        return dependents
    }
}

data class ProjectGraphNode(
    val name: String,
    val type: String,
    val data: ProjectConfiguration
)

data class ProjectGraphDependency(
    val source: String,
    val target: String,
    val type: DependencyType
)

// DependencyType is now imported from frontseat-plugin via type alias in ProjectConfiguration.kt

data class ProjectGraphExternalNode(
    val name: String,
    val type: String,
    val data: ExternalNodeData
)

data class ExternalNodeData(
    val version: String,
    val packageName: String? = null,
    val hash: String? = null
)