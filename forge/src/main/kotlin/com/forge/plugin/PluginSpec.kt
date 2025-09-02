package com.forge.plugin

import com.forge.plugin.api.PluginConfiguration

/**
 * Specification for a plugin to be loaded
 */
data class PluginSpec(
    val id: String,                    // "com.forge.js"
    val version: String = "latest",    // "1.2.0" or "latest"
    val source: PluginSource = PluginSource.MAVEN,
    val location: String = "",         // Source-specific location
    val options: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Create PluginSpec from PluginConfiguration
         */
        fun fromConfiguration(config: PluginConfiguration): PluginSpec {
            val spec = parse(config.plugin)
            return spec.copy(options = config.options)
        }
        
        /**
         * Parse plugin specification from string
         * Examples:
         * - "com.forge.js" -> id=com.forge.js, version=latest, source=maven
         * - "com.forge.js@1.2.0" -> id=com.forge.js, version=1.2.0, source=maven
         * - "file:./plugins/custom.jar" -> source=file, location=./plugins/custom.jar
         * - "github:myorg/forge-plugin-custom" -> source=github, location=myorg/forge-plugin-custom
         */
        fun parse(spec: String): PluginSpec {
            return when {
                spec.startsWith("file:") -> {
                    val location = spec.removePrefix("file:")
                    val id = extractIdFromPath(location)
                    PluginSpec(id = id, source = PluginSource.FILE, location = location)
                }
                spec.startsWith("github:") -> {
                    val location = spec.removePrefix("github:")
                    val id = extractIdFromGitHub(location)
                    PluginSpec(id = id, source = PluginSource.GITHUB, location = location)
                }
                spec.startsWith("npm:") -> {
                    val npmSpec = spec.removePrefix("npm:")
                    val (id, version) = parseIdVersion(npmSpec)
                    PluginSpec(id = id, version = version, source = PluginSource.NPM)
                }
                else -> {
                    // Default to Maven
                    val (id, version) = parseIdVersion(spec)
                    PluginSpec(id = id, version = version, source = PluginSource.MAVEN)
                }
            }
        }
        
        private fun parseIdVersion(spec: String): Pair<String, String> {
            val parts = spec.split("@")
            return if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                spec to "latest"
            }
        }
        
        private fun extractIdFromPath(path: String): String {
            val fileName = path.substringAfterLast("/")
            return fileName.removeSuffix(".jar")
        }
        
        private fun extractIdFromGitHub(location: String): String {
            // Extract from "owner/repo" format
            return location.replace("/", ".")
        }
    }
    
    /**
     * Get the full coordinate string for this plugin
     */
    fun toCoordinateString(): String {
        return when (source) {
            PluginSource.MAVEN -> "$id:$version"
            PluginSource.NPM -> "npm:$id@$version"
            PluginSource.GITHUB -> "github:$location"
            PluginSource.FILE -> "file:$location"
        }
    }
}

/**
 * Source types for plugins
 */
enum class PluginSource {
    MAVEN,    // Maven Central or custom Maven repository
    NPM,      // NPM registry
    GITHUB,   // GitHub releases
    FILE      // Local file system
}