package com.frontseat.maven.plugin

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for generating Maven-based tasks
 */
object MavenTasks {
    
    /**
     * Generate standard Maven tasks for a project
     */
    fun generateMavenTasks(projectPath: Path): Map<String, Map<String, Any>> {
        val tasks = mutableMapOf<String, Map<String, Any>>()
        
        if (!MavenUtils.isMavenProject(projectPath)) {
            return tasks
        }
        
        // Standard Maven lifecycle tasks
        tasks["build"] = createMavenTask("compile", projectPath)
        tasks["test"] = createMavenTask("test", projectPath) 
        tasks["package"] = createMavenTask("package", projectPath)
        tasks["install"] = createMavenTask("install", projectPath)
        tasks["clean"] = createMavenTask("clean", projectPath)
        tasks["verify"] = createMavenTask("verify", projectPath)
        
        // Additional Maven tasks based on POM analysis
        val pomContent = getPomContent(projectPath)
        if (pomContent != null) {
            // Add site task if maven-site-plugin is present
            if (pomContent.contains("maven-site-plugin")) {
                tasks["site"] = createMavenTask("site", projectPath)
            }
            
            // Add deploy task if distributionManagement is configured
            if (pomContent.contains("<distributionManagement>")) {
                tasks["deploy"] = createMavenTask("deploy", projectPath)
            }
            
            // Add integration test task if failsafe plugin is present
            if (pomContent.contains("maven-failsafe-plugin")) {
                tasks["integration-test"] = createMavenTask("integration-test", projectPath)
            }
        }
        
        return tasks
    }
    
    /**
     * Create a specific Maven task configuration
     */
    fun createMavenTask(goal: String, projectPath: Path, options: Map<String, Any> = emptyMap()): Map<String, Any> {
        val taskOptions = mutableMapOf<String, Any>()
        taskOptions["command"] = goal
        taskOptions.putAll(options)
        
        return mapOf(
            "executor" to "maven",
            "options" to taskOptions
        )
    }
    
    /**
     * Create a Maven task with custom goals and options
     */
    fun createCustomMavenTask(
        goals: String, 
        projectPath: Path, 
        options: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        return createMavenTask(goals, projectPath, options)
    }
    
    /**
     * Get Maven task dependencies (which tasks should run before this one)
     */
    fun getMavenTaskDependencies(taskName: String): List<String> {
        return when (taskName) {
            "test" -> listOf("build")
            "package" -> listOf("build", "test")
            "install" -> listOf("package")
            "deploy" -> listOf("install")
            "integration-test" -> listOf("package")
            "verify" -> listOf("integration-test")
            "site" -> listOf("build")
            else -> emptyList()
        }
    }
    
    /**
     * Check if a Maven project has specific plugins
     */
    fun hasPlugin(projectPath: Path, pluginArtifactId: String): Boolean {
        val pomContent = getPomContent(projectPath) ?: return false
        return pomContent.contains("<artifactId>$pluginArtifactId</artifactId>")
    }
    
    /**
     * Check if a Maven project has specific dependencies
     */
    fun hasDependency(projectPath: Path, groupId: String, artifactId: String): Boolean {
        val pomContent = getPomContent(projectPath) ?: return false
        return pomContent.contains("<groupId>$groupId</groupId>") && 
               pomContent.contains("<artifactId>$artifactId</artifactId>")
    }
    
    /**
     * Get Maven project packaging type
     */
    fun getPackaging(projectPath: Path): String {
        val pomContent = getPomContent(projectPath) ?: return "jar"
        
        val packagingRegex = Regex("<packaging>([^<]+)</packaging>")
        return packagingRegex.find(pomContent)?.groupValues?.get(1) ?: "jar"
    }
    
    /**
     * Get Maven project artifact information
     */
    fun getArtifactInfo(projectPath: Path): MavenArtifactInfo? {
        val pomContent = getPomContent(projectPath) ?: return null
        
        val groupIdRegex = Regex("<groupId>([^<]+)</groupId>")
        val artifactIdRegex = Regex("<artifactId>([^<]+)</artifactId>") 
        val versionRegex = Regex("<version>([^<]+)</version>")
        
        val groupId = groupIdRegex.find(pomContent)?.groupValues?.get(1)
        val artifactId = artifactIdRegex.find(pomContent)?.groupValues?.get(1)
        val version = versionRegex.find(pomContent)?.groupValues?.get(1)
        
        return if (groupId != null && artifactId != null) {
            MavenArtifactInfo(groupId, artifactId, version ?: "unknown")
        } else null
    }
    
    private fun getPomContent(projectPath: Path): String? {
        val pomFile = projectPath / "pom.xml"
        return if (pomFile.exists()) {
            try {
                pomFile.readText()
            } catch (e: Exception) {
                null
            }
        } else null
    }
}

/**
 * Data class for Maven artifact information
 */
data class MavenArtifactInfo(
    val groupId: String,
    val artifactId: String,
    val version: String
)