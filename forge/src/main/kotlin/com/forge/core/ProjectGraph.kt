package com.forge.core

import java.nio.file.Path

data class ProjectGraph(
    val nodes: Map<String, ProjectGraphNode>,
    val dependencies: Map<String, List<ProjectGraphDependency>>,
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

// DependencyType is now imported from forge-plugin via type alias in ProjectConfiguration.kt

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