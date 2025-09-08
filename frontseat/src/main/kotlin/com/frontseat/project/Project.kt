package com.frontseat.project

import com.frontseat.project.nature.Nature
import java.nio.file.Path

/**
 * Represents a single project within a workspace.
 * A project has configuration, applied natures, and can generate tasks.
 */
data class Project(
    val name: String,
    val path: Path,
    val configuration: ProjectConfiguration,
    val natures: Set<Nature> = emptySet(),
    val dependencies: Set<String> = emptySet()
) {
    /**
     * Get the root directory of this project
     */
    fun getRootPath(): Path = path
    
    /**
     * Check if this project has a specific nature applied
     */
    fun hasNature(natureId: String): Boolean = 
        natures.any { it.id == natureId }
    
    /**
     * Check if this project has a specific target
     */
    fun hasTarget(targetName: String): Boolean = 
        configuration.hasTarget(targetName)
    
    /**
     * Get target configuration by name
     */
    fun getTarget(targetName: String): TargetConfiguration? = 
        configuration.getTarget(targetName)
    
    /**
     * Get all target names from this project
     */
    fun getTargetNames(): Set<String> = 
        configuration.getTargetNames()
    
    /**
     * Check if this project has a specific tag
     */
    fun hasTag(tag: String): Boolean = 
        configuration.hasTag(tag)
    
    override fun toString(): String = 
        "Project(name='$name', path='$path', natures=${natures.map { it.id }}, targets=${getTargetNames()})"
}