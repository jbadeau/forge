package com.frontseat.springboot.plugin

import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Utility methods for Spring Boot project detection
 */
object SpringBootUtils {
    
    /**
     * Check if a project is a Spring Boot project by looking for the spring-boot-maven-plugin in pom.xml
     */
    fun isSpringBootProject(projectPath: Path): Boolean {
        val pomFile = projectPath.resolve("pom.xml")
        if (!pomFile.exists()) return false
        
        return try {
            val pomContent = pomFile.toFile().readText()
            pomContent.contains("spring-boot-maven-plugin")
        } catch (e: Exception) {
            false
        }
    }
}