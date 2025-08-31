package com.forge.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.nio.file.Path

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProjectConfiguration(
    val name: String,
    val root: String = "",
    @JsonProperty("sourceRoot") 
    val sourceRoot: String? = null,
    @JsonProperty("projectType") 
    val projectType: String = "library",
    val tags: List<String> = emptyList(),
    val targets: Map<String, TargetConfiguration> = emptyMap(),
    val generators: Map<String, Any> = emptyMap(),
    @JsonProperty("namedInputs") 
    val namedInputs: Map<String, List<String>> = emptyMap()
) {
    fun getTarget(name: String): TargetConfiguration? = targets[name]
    
    fun hasTarget(name: String): Boolean = targets.containsKey(name)
    
    fun getTargetNames(): Set<String> = targets.keys
    
    fun hasTag(tag: String): Boolean = tags.contains(tag)
    
    fun getSourcePath(): Path = Path.of(sourceRoot ?: "$root/src")
    
    fun getRootPath(): Path = Path.of(root)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TargetConfiguration(
    val executor: String? = null,
    val options: Map<String, Any> = emptyMap(),
    val configurations: Map<String, Map<String, Any>> = emptyMap(),
    @JsonProperty("dependsOn") 
    val dependsOn: List<String> = emptyList(),
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    val cache: Boolean = true,
    @JsonProperty("parallelism") 
    val parallelism: Boolean = true
) {
    fun getDependencies(): List<String> = dependsOn
    
    fun getTaskInputs(): List<String> = inputs.ifEmpty { listOf("default") }
    
    fun getTaskOutputs(): List<String> = outputs
    
    fun isCacheable(): Boolean = cache
    
    fun canRunInParallel(): Boolean = parallelism
    
    fun getConfiguration(name: String): Map<String, Any> = 
        configurations[name] ?: emptyMap()
    
    fun hasConfiguration(name: String): Boolean = configurations.containsKey(name)
}