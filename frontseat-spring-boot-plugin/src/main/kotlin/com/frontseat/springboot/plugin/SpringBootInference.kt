package com.frontseat.springboot.plugin

import com.frontseat.maven.plugin.MavenUtils
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Spring Boot project inference logic
 */
object SpringBootInference {
    
    /**
     * Check if a project path represents a Spring Boot project
     */
    fun isSpringBootProject(projectPath: String): Boolean {
        return SpringBootUtils.isSpringBootProject(Path.of(projectPath))
    }
    
    /**
     * Determine if this is a Spring Boot Application vs Library
     */
    fun inferSpringBootProjectType(path: Path): SpringBootProjectCategory {
        val hasMainClass = hasSpringBootMainClass(path)
        val hasSpringBootPlugin = hasSpringBootMavenPlugin(path)
        val usesSpringBootParent = usesSpringBootParent(path)
        val hasApplicationStarters = hasApplicationStarters(path)
        
        return when {
            hasMainClass && hasSpringBootPlugin -> SpringBootProjectCategory.APPLICATION
            hasMainClass && usesSpringBootParent -> SpringBootProjectCategory.APPLICATION
            hasApplicationStarters && hasSpringBootPlugin -> SpringBootProjectCategory.APPLICATION
            hasSpringBootStarters(path) -> SpringBootProjectCategory.LIBRARY
            else -> SpringBootProjectCategory.UNKNOWN
        }
    }
    
    /**
     * Infer Spring Boot project configuration and targets
     */
    fun inferProject(projectPath: String): SpringBootProjectInfo? {
        val path = Path.of(projectPath)
        
        if (!SpringBootUtils.isSpringBootProject(path)) {
            return null
        }
        
        val projectName = path.fileName.toString()
        val projectType = SpringBootUtils.inferProjectType(path)
        val projectCategory = inferSpringBootProjectType(path)
        val springBootVersion = SpringBootUtils.getSpringBootVersion(path)
        val targets = SpringBootTasks.generateTasks(path)
        
        return SpringBootProjectInfo(
            name = projectName,
            path = projectPath,
            type = projectType,
            category = projectCategory,
            springBootVersion = springBootVersion,
            targets = targets,
            tags = generateProjectTags(path, projectType),
            metadata = generateProjectMetadata(path, projectType, projectCategory, springBootVersion)
        )
    }
    
    /**
     * Infer project language (Java, Kotlin, or mixed)
     */
    fun inferLanguage(path: Path): String {
        val hasKotlin = (path / "src/main/kotlin").exists()
        val hasJava = (path / "src/main/java").exists()
        
        return when {
            hasKotlin && hasJava -> "java-kotlin"
            hasKotlin -> "kotlin"
            else -> "java"
        }
    }
    
    /**
     * Get Spring Boot starters used in the project
     */
    fun getUsedStarters(path: Path): List<String> {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return emptyList()
        
        val content = pomFile.readText()
        val starters = mutableListOf<String>()
        
        // Common starters to check for
        val commonStarters = listOf(
            "web", "webflux", "data-jpa", "data-mongodb", "data-redis",
            "security", "oauth2-client", "oauth2-resource-server",
            "actuator", "test", "validation", "mail", "batch",
            "cloud-starter-gateway", "cloud-starter-config"
        )
        
        commonStarters.forEach { starter ->
            if (content.contains("spring-boot-starter-$starter")) {
                starters.add(starter)
            }
        }
        
        return starters
    }
    
    /**
     * Check if project uses Spring Cloud
     */
    fun isSpringCloudProject(path: Path): Boolean {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return false
        
        val content = pomFile.readText()
        return content.contains("spring-cloud") || content.contains("spring-boot-starter-cloud")
    }
    
    /**
     * Check if project uses Spring Boot parent POM
     */
    private fun usesSpringBootParent(path: Path): Boolean {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return false
        
        val content = pomFile.readText()
        return content.contains("<groupId>org.springframework.boot</groupId>") &&
               content.contains("<artifactId>spring-boot-starter-parent</artifactId>")
    }
    
    /**
     * Check if project has spring-boot-maven-plugin
     */
    private fun hasSpringBootMavenPlugin(path: Path): Boolean {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return false
        
        val content = pomFile.readText()
        return content.contains("spring-boot-maven-plugin")
    }
    
    /**
     * Check if project has any Spring Boot starters
     */
    private fun hasSpringBootStarters(path: Path): Boolean {
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return false
        
        val content = pomFile.readText()
        return content.contains("spring-boot-starter")
    }
    
    /**
     * Check if project has application-type starters (web, webflux, etc)
     */
    private fun hasApplicationStarters(path: Path): Boolean {
        val applicationStarters = listOf(
            "spring-boot-starter-web",
            "spring-boot-starter-webflux", 
            "spring-boot-starter-batch",
            "spring-boot-starter-integration"
        )
        
        val pomFile = path / "pom.xml"
        if (!pomFile.exists()) return false
        
        val content = pomFile.readText()
        return applicationStarters.any { starter -> content.contains(starter) }
    }
    
    /**
     * Enhanced check for Spring Boot main class
     */
    private fun hasSpringBootMainClass(path: Path): Boolean {
        return SpringBootUtils.hasSpringBootMainClass(path)
    }
    
    /**
     * Get the main application class name if it can be determined
     */
    fun findMainClassName(path: Path): String? {
        val javaSourceDir = path / "src/main/java"
        val kotlinSourceDir = path / "src/main/kotlin"
        
        listOf(javaSourceDir, kotlinSourceDir).forEach { sourceDir ->
            if (sourceDir.exists()) {
                sourceDir.walk()
                    .filter { it.extension in setOf("java", "kt") }
                    .forEach { file ->
                        try {
                            val content = file.readText()
                            if (content.contains("@SpringBootApplication")) {
                                val packageMatch = Regex("package\\s+([^;]+);?").find(content)
                                val classMatch = Regex("class\\s+(\\w+)").find(content)
                                
                                if (packageMatch != null && classMatch != null) {
                                    val packageName = packageMatch.groupValues[1].trim()
                                    val className = classMatch.groupValues[1]
                                    return "$packageName.$className"
                                }
                            }
                        } catch (e: Exception) {
                            // Continue searching
                        }
                    }
            }
        }
        
        return null
    }
    
    private fun generateProjectTags(path: Path, type: SpringBootProjectType): Set<String> {
        val tags = mutableSetOf("spring-boot", "java", "maven")
        
        // Add language tags
        when (inferLanguage(path)) {
            "kotlin" -> tags.add("kotlin")
            "java-kotlin" -> tags.addAll(setOf("java", "kotlin"))
            else -> tags.add("java")
        }
        
        // Add type-specific tags
        when (type) {
            SpringBootProjectType.WEB -> tags.addAll(setOf("web", "rest-api"))
            SpringBootProjectType.REACTIVE_WEB -> tags.addAll(setOf("reactive", "webflux"))
            SpringBootProjectType.DATA -> tags.addAll(setOf("database", "jpa"))
            SpringBootProjectType.BATCH -> tags.add("batch")
            else -> {}
        }
        
        // Add Spring Cloud tags if applicable
        if (isSpringCloudProject(path)) {
            tags.add("spring-cloud")
        }
        
        return tags
    }
    
    private fun generateProjectMetadata(
        path: Path, 
        type: SpringBootProjectType,
        category: SpringBootProjectCategory,
        springBootVersion: String?
    ): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        
        metadata["projectType"] = "spring-boot"
        metadata["buildSystem"] = "maven"
        metadata["language"] = inferLanguage(path)
        metadata["springBootProjectType"] = type.name.lowercase().replace('_', '-')
        metadata["springBootCategory"] = category.name.lowercase() // APPLICATION or LIBRARY
        
        springBootVersion?.let { version ->
            metadata["springBootVersion"] = version
        }
        
        val starters = getUsedStarters(path)
        if (starters.isNotEmpty()) {
            metadata["starters"] = starters
        }
        
        val mainClass = findMainClassName(path)
        if (mainClass != null) {
            metadata["mainClass"] = mainClass
        }
        
        val serverPort = SpringBootTasks.getTaskMetadata("serve", path)["port"]
        if (serverPort != null) {
            metadata["serverPort"] = serverPort
        }
        
        metadata["isSpringCloud"] = isSpringCloudProject(path)
        
        return metadata
    }
}

/**
 * Category of Spring Boot project (Application vs Library)
 */
enum class SpringBootProjectCategory {
    APPLICATION,    // Has @SpringBootApplication, executable JAR
    LIBRARY,        // Uses Spring Boot starters but no main class
    UNKNOWN         // Cannot determine category
}

/**
 * Data class representing inferred Spring Boot project information
 */
data class SpringBootProjectInfo(
    val name: String,
    val path: String, 
    val type: SpringBootProjectType,
    val category: SpringBootProjectCategory,
    val springBootVersion: String?,
    val targets: Map<String, Map<String, Any>>,
    val tags: Set<String>,
    val metadata: Map<String, Any>
)