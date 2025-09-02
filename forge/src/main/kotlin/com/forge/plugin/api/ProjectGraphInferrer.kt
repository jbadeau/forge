package com.forge.plugin.api

/**
 * Interface for inferring project graph structure from configuration files
 */
interface ProjectGraphInferrer<T> {
    
    /**
     * Infer project configurations from configuration files
     * 
     * @param configFiles List of configuration file paths to process
     * @param options Plugin-specific options for inference
     * @param context Context containing workspace information
     * @return Map of project name to project configuration
     */
    fun inferProjects(
        configFiles: List<String>,
        options: T,
        context: CreateNodesContext
    ): Map<String, ProjectConfiguration>
    
    /**
     * Infer dependencies between projects in the workspace
     * 
     * @param options Plugin-specific options for dependency analysis
     * @param context Context containing workspace and project information
     * @return List of raw project graph dependencies
     */
    fun inferProjectDependencies(
        options: T,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency>
}