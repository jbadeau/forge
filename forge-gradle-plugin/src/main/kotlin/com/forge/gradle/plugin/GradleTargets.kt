package com.forge.gradle.plugin

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for generating Gradle-based targets
 */
object GradleTargets {
    
    /**
     * Generate standard Gradle targets for a project
     */
    fun generateGradleTargets(projectPath: Path): Map<String, Map<String, Any>> {
        val targets = mutableMapOf<String, Map<String, Any>>()
        
        if (!GradleUtils.isGradleProject(projectPath)) {
            return targets
        }
        
        // Standard Gradle lifecycle targets
        targets["build"] = createGradleTarget("build", projectPath)
        targets["test"] = createGradleTarget("test", projectPath)
        targets["clean"] = createGradleTarget("clean", projectPath)
        targets["assemble"] = createGradleTarget("assemble", projectPath)
        targets["check"] = createGradleTarget("check", projectPath)
        
        // Additional targets based on build.gradle analysis
        val buildContent = getBuildContent(projectPath)
        if (buildContent != null) {
            // Add jar target if it's a Java project
            if (buildContent.contains("java") || buildContent.contains("kotlin")) {
                targets["jar"] = createGradleTarget("jar", projectPath)
            }
            
            // Add publish target if publishing plugin is applied
            if (buildContent.contains("maven-publish") || buildContent.contains("publishing")) {
                targets["publish"] = createGradleTarget("publish", projectPath)
            }
            
            // Add application target if application plugin is applied
            if (buildContent.contains("application")) {
                targets["run"] = createGradleTarget("run", projectPath)
            }
            
            // Add test report target if test report plugin is present
            if (buildContent.contains("test-report-aggregation") || 
                buildContent.contains("jacoco")) {
                targets["testReport"] = createGradleTarget("testReport", projectPath)
            }
        }
        
        return targets
    }
    
    /**
     * Create a specific Gradle target configuration
     */
    fun createGradleTarget(
        task: String, 
        projectPath: Path, 
        options: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        val commandOptions = GradleCommandBuilder.build()
            .inProject(projectPath)
            .withTask(task)
            .toOptions()
        
        val finalOptions = commandOptions + options
        
        return mapOf(
            "executor" to "gradle",
            "options" to finalOptions
        )
    }
    
    /**
     * Create a Gradle target with custom tasks and options
     */
    fun createCustomGradleTarget(
        tasks: List<String>,
        projectPath: Path,
        options: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        val builder = GradleCommandBuilder.build().inProject(projectPath)
        tasks.forEach { task -> builder.withTask(task) }
        
        val commandOptions = builder.toOptions()
        val finalOptions = commandOptions + options
        
        return mapOf(
            "executor" to "gradle",
            "options" to finalOptions
        )
    }
    
    /**
     * Get Gradle target dependencies (which targets should run before this one)
     */
    fun getGradleTargetDependencies(targetName: String): List<String> {
        return when (targetName) {
            "test" -> listOf("build")
            "check" -> listOf("test")
            "assemble" -> listOf("build")
            "publish" -> listOf("assemble")
            "run" -> listOf("build")
            "testReport" -> listOf("test")
            else -> emptyList()
        }
    }
    
    /**
     * Check if a Gradle project has specific plugins
     */
    fun hasPlugin(projectPath: Path, pluginId: String): Boolean {
        val buildContent = getBuildContent(projectPath) ?: return false
        return buildContent.contains("id '$pluginId'") || 
               buildContent.contains("id \"$pluginId\"") ||
               buildContent.contains("apply plugin: '$pluginId'") ||
               buildContent.contains("apply plugin: \"$pluginId\"")
    }
    
    /**
     * Check if a Gradle project has specific dependencies
     */
    fun hasDependency(projectPath: Path, group: String, artifact: String): Boolean {
        val buildContent = getBuildContent(projectPath) ?: return false
        return buildContent.contains("$group:$artifact") ||
               buildContent.contains("group: '$group', name: '$artifact'") ||
               buildContent.contains("group: \"$group\", name: \"$artifact\"")
    }
    
    /**
     * Get Gradle project information
     */
    fun getProjectInfo(projectPath: Path): GradleProjectInfo? {
        val buildFile = GradleUtils.getBuildFile(projectPath) ?: return null
        val buildContent = getBuildContent(projectPath) ?: return null
        
        val isKotlinDsl = GradleUtils.usesKotlinDsl(projectPath)
        val hasWrapper = (projectPath / "gradlew").exists()
        val plugins = extractPlugins(buildContent, isKotlinDsl)
        
        return GradleProjectInfo(
            buildFile = buildFile.toString(),
            isKotlinDsl = isKotlinDsl,
            hasWrapper = hasWrapper,
            plugins = plugins
        )
    }
    
    private fun getBuildContent(projectPath: Path): String? {
        val buildFile = GradleUtils.getBuildFile(projectPath)
        return if (buildFile?.exists() == true) {
            try {
                buildFile.readText()
            } catch (e: Exception) {
                null
            }
        } else null
    }
    
    private fun extractPlugins(buildContent: String, isKotlinDsl: Boolean): List<String> {
        val plugins = mutableListOf<String>()
        
        // Extract plugins from plugins {} block
        val pluginRegex = if (isKotlinDsl) {
            Regex("""id\("([^"]+)"\)""")
        } else {
            Regex("""id ['"]([^'"]+)['"]""")
        }
        
        pluginRegex.findAll(buildContent).forEach { match ->
            plugins.add(match.groupValues[1])
        }
        
        // Extract plugins from apply plugin statements
        val applyRegex = Regex("""apply plugin: ['"]([^'"]+)['"]""")
        applyRegex.findAll(buildContent).forEach { match ->
            plugins.add(match.groupValues[1])
        }
        
        return plugins.distinct()
    }
}

/**
 * Data class for Gradle project information
 */
data class GradleProjectInfo(
    val buildFile: String,
    val isKotlinDsl: Boolean,
    val hasWrapper: Boolean,
    val plugins: List<String>
)