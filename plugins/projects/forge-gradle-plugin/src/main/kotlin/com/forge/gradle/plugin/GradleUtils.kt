package com.forge.gradle.plugin

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for working with Gradle projects
 */
object GradleUtils {
    
    /**
     * Check if a directory contains a Gradle project
     */
    fun isGradleProject(path: Path): Boolean {
        return (path / "build.gradle").exists() || 
               (path / "build.gradle.kts").exists()
    }
    
    /**
     * Find all Gradle projects in a directory tree
     */
    fun findGradleProjects(root: Path): List<Path> {
        val projects = mutableListOf<Path>()
        
        fun scan(dir: Path) {
            if (isGradleProject(dir)) {
                projects.add(dir)
            }
            
            try {
                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.isDirectory() && 
                        entry.fileName.toString() !in setOf("build", ".gradle", "node_modules")) {
                        scan(entry)
                    }
                }
            } catch (e: Exception) {
                // Ignore unreadable directories
            }
        }
        
        scan(root)
        return projects
    }
    
    /**
     * Get Gradle command with wrapper preference
     */
    fun getGradleCommand(projectDir: Path): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val wrapperScript = if (isWindows) "gradlew.bat" else "gradlew"
        val wrapperPath = projectDir / wrapperScript
        
        return if (wrapperPath.exists()) {
            wrapperPath.toString()
        } else {
            "gradle"
        }
    }
    
    /**
     * Determine if project uses Kotlin DSL
     */
    fun usesKotlinDsl(path: Path): Boolean {
        return (path / "build.gradle.kts").exists()
    }
    
    /**
     * Get build file path (build.gradle or build.gradle.kts)
     */
    fun getBuildFile(path: Path): Path? {
        return when {
            (path / "build.gradle.kts").exists() -> path / "build.gradle.kts"
            (path / "build.gradle").exists() -> path / "build.gradle"
            else -> null
        }
    }
    
    /**
     * Check if project has settings.gradle file
     */
    fun hasSettings(path: Path): Boolean {
        return (path / "settings.gradle").exists() || 
               (path / "settings.gradle.kts").exists()
    }
    
    /**
     * Check if project has gradle.properties
     */
    fun hasProperties(path: Path): Boolean {
        return (path / "gradle.properties").exists()
    }
}