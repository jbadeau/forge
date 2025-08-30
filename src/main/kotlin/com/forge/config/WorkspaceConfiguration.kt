package com.forge.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.forge.core.TargetConfiguration

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkspaceConfiguration(
    val version: Int = 1,
    @JsonProperty("targetDefaults")
    val targetDefaults: Map<String, TargetConfiguration> = emptyMap(),
    @JsonProperty("namedInputs")
    val namedInputs: Map<String, List<String>> = emptyMap(),
    val plugins: List<PluginConfiguration> = emptyList(),
    val generators: Map<String, Any> = emptyMap(),
    val tasksRunnerOptions: Map<String, Any> = emptyMap(),
    @JsonProperty("defaultProject")
    val defaultProject: String? = null,
    @JsonProperty("workspaceLayout")
    val workspaceLayout: WorkspaceLayout = WorkspaceLayout(),
    val cli: CliConfiguration = CliConfiguration(),
    @JsonProperty("affected")
    val affected: AffectedConfiguration = AffectedConfiguration()
) {
    fun getTargetDefaults(targetName: String): TargetConfiguration? = 
        targetDefaults[targetName]
    
    fun getNamedInputs(inputName: String): List<String> = 
        namedInputs[inputName] ?: emptyList()
    
    fun hasTargetDefaults(targetName: String): Boolean = 
        targetDefaults.containsKey(targetName)
    
    fun hasNamedInputs(inputName: String): Boolean = 
        namedInputs.containsKey(inputName)
    
    fun toMap(): Map<String, Any> = mapOf(
        "version" to version,
        "targetDefaults" to targetDefaults,
        "namedInputs" to namedInputs,
        "plugins" to plugins,
        "generators" to generators,
        "tasksRunnerOptions" to tasksRunnerOptions,
        "defaultProject" to (defaultProject ?: ""),
        "workspaceLayout" to workspaceLayout,
        "cli" to cli,
        "affected" to affected
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkspaceLayout(
    @JsonProperty("appsDir")
    val appsDir: String = "apps",
    @JsonProperty("libsDir") 
    val libsDir: String = "libs"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CliConfiguration(
    @JsonProperty("defaultCollection")
    val defaultCollection: String? = null,
    @JsonProperty("packageManager")
    val packageManager: String = "npm",
    val warnings: Map<String, Boolean> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AffectedConfiguration(
    @JsonProperty("defaultBase")
    val defaultBase: String = "main"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginConfiguration(
    val plugin: String,
    val options: Map<String, Any> = emptyMap()
) {
    fun getOption(key: String): Any? = options[key]
    
    fun getStringOption(key: String, default: String? = null): String? = 
        options[key] as? String ?: default
    
    fun getBooleanOption(key: String, default: Boolean = false): Boolean = 
        options[key] as? Boolean ?: default
    
    fun getIntOption(key: String, default: Int = 0): Int = 
        options[key] as? Int ?: default
}

