package com.forge.maven.plugin

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for generating Maven-based targets
 */
object MavenTargets {
    
    /**
     * Generate standard Maven targets for a project
     */
    fun generateMavenTargets(projectPath: Path): Map<String, Map<String, Any>> {
        val targets = mutableMapOf<String, Map<String, Any>>()
        
        if (!MavenUtils.isMavenProject(projectPath)) {
            return targets
        }
        
        // Standard Maven lifecycle targets
        targets["build"] = createMavenTarget("compile", projectPath)
        targets["test"] = createMavenTarget("test", projectPath) 
        targets["package"] = createMavenTarget("package", projectPath)
        targets["install"] = createMavenTarget("install", projectPath)
        targets["clean"] = createMavenTarget("clean", projectPath)
        targets["verify"] = createMavenTarget("verify", projectPath)
        
        // Additional Maven targets based on POM analysis
        val pomContent = getPomContent(projectPath)
        if (pomContent != null) {
            // Add site target if maven-site-plugin is present
            if (pomContent.contains("maven-site-plugin")) {
                targets["site"] = createMavenTarget("site", projectPath)
            }
            
            // Add deploy target if distributionManagement is configured
            if (pomContent.contains("<distributionManagement>")) {
                targets["deploy"] = createMavenTarget("deploy", projectPath)
            }
            
            // Add integration test target if failsafe plugin is present
            if (pomContent.contains("maven-failsafe-plugin")) {
                targets["integration-test"] = createMavenTarget("integration-test", projectPath)
            }
        }
        
        return targets
    }
    
    /**
     * Create a specific Maven target configuration
     */
    fun createMavenTarget(goal: String, projectPath: Path, options: Map<String, Any> = emptyMap()): Map<String, Any> {
        val targetOptions = mutableMapOf<String, Any>()
        targetOptions["command"] = goal
        targetOptions.putAll(options)
        
        return mapOf(
            "executor" to "maven",
            "options" to targetOptions
        )
    }
    
    /**
     * Create a Maven target with custom goals and options
     */
    fun createCustomMavenTarget(
        goals: String, 
        projectPath: Path, 
        options: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        return createMavenTarget(goals, projectPath, options)
    }
    
    /**
     * Get Maven target dependencies (which targets should run before this one)
     */
    fun getMavenTargetDependencies(targetName: String): List<String> {
        return when (targetName) {
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