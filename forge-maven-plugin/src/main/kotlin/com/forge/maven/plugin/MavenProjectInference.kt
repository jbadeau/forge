package com.forge.maven.plugin

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.forge.plugin.api.ProjectConfiguration
import com.forge.plugin.api.TargetConfiguration
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.api.DependencyType
import com.forge.plugin.api.ProjectGraphInferrer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Options for Maven project inference
 */
data class MavenInferenceOptions(
    val buildTargetName: String = "compile",
    val testTargetName: String = "test",
    val packageTargetName: String = "package"
)

/**
 * Core Maven project inference logic
 */
class MavenProjectInference : ProjectGraphInferrer<MavenInferenceOptions> {
    
    private val logger = LoggerFactory.getLogger(MavenProjectInference::class.java)
    private val xmlMapper = XmlMapper()
    
    override fun inferProjects(
        configFiles: List<String>,
        options: MavenInferenceOptions,
        context: CreateNodesContext
    ): Map<String, ProjectConfiguration> {
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val pomPath = Path.of(configFile)
                if (pomPath.exists()) {
                    val project = inferProjectFromPom(pomPath, options, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process pom.xml at $configFile: ${e.message}")
            }
        }
        
        return projects
    }
    
    override fun inferProjectDependencies(
        options: MavenInferenceOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Create map of artifactId -> project name for quick lookup
        val projectLookup = context.projects.mapNotNull { (projectName, projectConfig) ->
            val pomPath = context.workspaceRoot.resolve(projectConfig.root).resolve("pom.xml")
            if (pomPath.exists()) {
                try {
                    val pom = parsePom(pomPath.readText())
                    val artifactId = pom["artifactId"] as? String
                    if (artifactId != null) {
                        artifactId to projectName
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to parse pom.xml for dependency lookup: $pomPath")
                    null
                }
            } else null
        }.toMap()
        
        // Process each Maven project to find internal dependencies
        context.projects.values.forEach { project ->
            val pomPath = context.workspaceRoot.resolve(project.root).resolve("pom.xml")
            if (pomPath.exists()) {
                try {
                    val projectDependencies = parsePomDependencies(pomPath, project.name, projectLookup)
                    dependencies.addAll(projectDependencies)
                } catch (e: Exception) {
                    logger.warn("Failed to parse dependencies from $pomPath: ${e.message}")
                }
            }
        }
        
        return dependencies
    }
    
    private fun inferProjectFromPom(
        pomPath: Path,
        options: MavenInferenceOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val pomContent = pomPath.readText()
        val pom = parsePom(pomContent)
        
        val projectRoot = context.workspaceRoot.relativize(pomPath.parent).toString()
        val artifactId = pom["artifactId"] as? String ?: return null
        
        val projectType = inferProjectType(pom, pomPath.parent)
        val tags = extractTags(pom, pomPath.parent)
        val targets = inferTargets(options, projectRoot, pom)
        
        return ProjectConfiguration(
            name = artifactId,
            root = projectRoot,
            sourceRoot = projectRoot,
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun parsePom(pomContent: String): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            xmlMapper.readValue(pomContent, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            logger.warn("Failed to parse POM XML: ${e.message}")
            emptyMap()
        }
    }
    
    private fun inferProjectType(pom: Map<String, Any>, projectDir: Path): String {
        val packaging = pom["packaging"] as? String ?: "jar"
        
        return when (packaging.lowercase()) {
            "pom" -> "library"
            "jar" -> {
                // Check if it has main class or Spring Boot
                val hasMainClass = projectDir.resolve("src/main/java").exists() &&
                                  projectDir.resolve("src/main/java").toFile().walk()
                                      .any { it.name.contains("Main") || it.name.contains("Application") }
                if (hasMainClass) "application" else "library"
            }
            "war" -> "application"
            else -> "library"
        }
    }
    
    private fun extractTags(pom: Map<String, Any>, projectDir: Path): List<String> {
        val tags = mutableListOf<String>()
        
        val packaging = pom["packaging"] as? String ?: "jar"
        tags.add(packaging)
        
        // Check for common Java frameworks
        if (projectDir.resolve("src/main/java").exists()) {
            tags.add("java")
        }
        if (projectDir.resolve("src/main/kotlin").exists()) {
            tags.add("kotlin")
        }
        if (projectDir.resolve("src/main/scala").exists()) {
            tags.add("scala")
        }
        
        // Check dependencies for framework tags
        val dependencies = when (val deps = pom["dependencies"]) {
            is Map<*, *> -> deps.entries.associate { (k, v) -> k.toString() to v.toString() }
            else -> emptyMap<String, String>()
        }
        
        // Spring Boot detection
        if (dependencies.toString().contains("spring-boot")) {
            tags.add("spring-boot")
        }
        
        // Testing framework detection
        if (dependencies.toString().contains("junit")) {
            tags.add("junit")
        }
        
        tags.add("maven")
        return tags.distinct()
    }
    
    private fun inferTargets(
        options: MavenInferenceOptions,
        projectRoot: String,
        pom: Map<String, Any>
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Compile target
        targets[options.buildTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("mvn compile"),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/pom.xml",
                "{projectRoot}/src/main/**/*"
            ),
            outputs = listOf("{projectRoot}/target/classes"),
            cache = true
        )
        
        // Test target
        targets[options.testTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("mvn test"),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/src/test/**/*",
                "{projectRoot}/src/main/**/*"
            ),
            outputs = listOf(
                "{projectRoot}/target/test-classes",
                "{projectRoot}/target/surefire-reports"
            ),
            cache = true,
            dependsOn = listOf(options.buildTargetName)
        )
        
        // Package target
        targets[options.packageTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("mvn package -DskipTests"),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default"
            ),
            outputs = listOf("{projectRoot}/target/*.jar", "{projectRoot}/target/*.war"),
            cache = true,
            dependsOn = listOf(options.buildTargetName)
        )
        
        return targets
    }
    
    private fun parsePomDependencies(
        pomPath: Path,
        sourceProjectName: String,
        projectLookup: Map<String, String>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val pom = parsePom(pomPath.readText())
            val pomDependencies = pom["dependencies"] as? List<*> ?: emptyList<Any>()
            
            pomDependencies.forEach { dep ->
                if (dep is Map<*, *>) {
                    val artifactId = dep["artifactId"] as? String
                    val scope = dep["scope"] as? String ?: "compile"
                    
                    val targetProject = artifactId?.let { projectLookup[it] }
                    
                    if (targetProject != null) {
                        dependencies.add(
                            RawProjectGraphDependency(
                                source = sourceProjectName,
                                target = targetProject,
                                type = DependencyType.STATIC,
                                sourceFile = pomPath.toString()
                            )
                        )
                        logger.debug("Found internal dependency: $sourceProjectName -> $targetProject ($scope)")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing pom.xml dependencies from $pomPath: ${e.message}")
        }
        
        return dependencies
    }
}