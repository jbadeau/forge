package com.forge.springboot.plugin

import com.forge.maven.plugin.MavenUtils
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities for working with Spring Boot projects
 */
object SpringBootUtils {
    
    /**
     * Check if a directory contains a Spring Boot project
     */
    fun isSpringBootProject(path: Path): Boolean {
        // Must be a Maven project first
        if (!MavenUtils.isMavenProject(path)) {
            return false
        }
        
        val pomFile = path / "pom.xml"
        if (pomFile.exists()) {
            val content = pomFile.readText()
            return content.contains("spring-boot-starter") ||
                   content.contains("org.springframework.boot")
        }
        
        return false
    }
    
    /**
     * Check if a Maven project has Spring Boot main class
     */
    fun hasSpringBootMainClass(path: Path): Boolean {
        val javaSourceDir = path / "src/main/java"
        val kotlinSourceDir = path / "src/main/kotlin"
        
        return hasSpringBootAnnotationInDir(javaSourceDir) || 
               hasSpringBootAnnotationInDir(kotlinSourceDir)
    }
    
    /**
     * Get Spring Boot version from pom.xml
     */
    fun getSpringBootVersion(path: Path): String? {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return null
        
        val content = pomFile.readText()
        
        // Look for Spring Boot parent version
        val parentVersionRegex = Regex(
            """<parent>\s*<groupId>org\.springframework\.boot</groupId>\s*<artifactId>spring-boot-starter-parent</artifactId>\s*<version>([^<]+)</version>"""
        )
        parentVersionRegex.find(content)?.let { match ->
            return match.groupValues[1]
        }
        
        return null
    }
    
    /**
     * Check if Spring Boot project has specific starter dependency
     */
    fun hasStarter(path: Path, starter: String): Boolean {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return false
        
        val content = pomFile.readText()
        return content.contains("spring-boot-starter-$starter")
    }
    
    /**
     * Get application properties or yml file path
     */
    fun getApplicationConfigPath(path: Path): Path? {
        val resourcesDir = path / "src/main/resources"
        
        listOf("application.properties", "application.yml", "application.yaml").forEach { filename ->
            val configFile = resourcesDir / filename
            if (configFile.exists()) {
                return configFile
            }
        }
        
        return null
    }
    
    /**
     * Find all Spring Boot projects in a directory tree
     */
    fun findSpringBootProjects(root: Path): List<Path> {
        return MavenUtils.findMavenProjects(root)
            .filter { isSpringBootProject(it) }
    }
    
    /**
     * Infer Spring Boot project type based on dependencies
     */
    fun inferProjectType(path: Path): SpringBootProjectType {
        return when {
            hasStarter(path, "webflux") -> SpringBootProjectType.REACTIVE_WEB
            hasStarter(path, "web") -> SpringBootProjectType.WEB
            hasStarter(path, "data-jpa") -> SpringBootProjectType.DATA
            hasStarter(path, "batch") -> SpringBootProjectType.BATCH
            else -> SpringBootProjectType.BASIC
        }
    }
    
    private fun hasSpringBootAnnotationInDir(dir: Path): Boolean {
        if (!dir.exists() || !dir.isDirectory()) return false
        
        return try {
            dir.walk()
                .filter { it.extension in setOf("java", "kt") }
                .any { file ->
                    try {
                        val content = file.readText()
                        content.contains("@SpringBootApplication") ||
                        content.contains("SpringApplication.run")
                    } catch (e: Exception) {
                        false
                    }
                }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Types of Spring Boot projects based on their dependencies
 */
enum class SpringBootProjectType {
    BASIC,          // Basic Spring Boot application
    WEB,            // Web application with spring-boot-starter-web
    REACTIVE_WEB,   // Reactive web with spring-boot-starter-webflux  
    DATA,           // Data application with spring-boot-starter-data-jpa
    BATCH           // Batch application with spring-boot-starter-batch
}