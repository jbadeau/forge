package com.frontseat.maven.plugin

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for working with Maven projects
 */
object MavenUtils {
    
    /**
     * Check if a directory contains a Maven project
     */
    fun isMavenProject(path: Path): Boolean {
        return (path / "pom.xml").exists()
    }
    
    /**
     * Find all Maven projects in a directory tree
     */
    fun findMavenProjects(root: Path): List<Path> {
        val projects = mutableListOf<Path>()
        
        fun scan(dir: Path) {
            if (isMavenProject(dir)) {
                projects.add(dir)
            }
            
            try {
                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.isDirectory() && entry.fileName.toString() != "target") {
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
     * Get Maven command with wrapper preference
     */
    fun getMavenCommand(projectDir: Path): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val wrapperScript = if (isWindows) "mvnw.bat" else "mvnw"
        val wrapperPath = projectDir / wrapperScript
        
        return if (wrapperPath.exists()) {
            wrapperPath.toString()
        } else {
            "mvn"
        }
    }
}