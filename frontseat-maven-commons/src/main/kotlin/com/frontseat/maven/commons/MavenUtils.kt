package com.frontseat.maven.commons

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
    
}