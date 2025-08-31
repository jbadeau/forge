package com.forge.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.core.ProjectConfiguration
import com.forge.core.TargetConfiguration
import com.forge.inference.CreateNodesContext
import com.forge.inference.CreateNodesResult
import com.forge.inference.CreateDependenciesContext
import com.forge.inference.RawProjectGraphDependency
import com.forge.core.DependencyType
import com.forge.plugin.ForgePlugin
import com.forge.plugin.PluginMetadata
import com.forge.plugin.ValidationResult
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Options for Maven plugin
 */
data class MavenPluginOptions(
    val buildTargetName: String = "compile",
    val testTargetName: String = "test",
    val packageTargetName: String = "package"
)

/**
 * ForgePlugin implementation for Maven projects
 */
class MavenForgePlugin : ForgePlugin {
    
    private val logger = LoggerFactory.getLogger(MavenForgePlugin::class.java)
    private val xmlMapper = XmlMapper()
    
    override val metadata = PluginMetadata(
        id = "com.forge.maven",
        name = "Maven Plugin",
        version = "1.0.0",
        description = "Support for Maven/Java projects",
        createNodesPattern = "**/pom.xml",
        supportedFiles = listOf("pom.xml"),
        author = "Forge Team",
        homepage = "https://github.com/forge/plugin-maven",
        tags = listOf("maven", "java", "kotlin", "scala")
    )
    
    override val defaultOptions = MavenPluginOptions()
    
    override fun createNodes(
        configFiles: List<String>, 
        options: Any?, 
        context: CreateNodesContext
    ): CreateNodesResult {
        val opts = parseOptions(options)
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val pomPath = Path.of(configFile)
                if (pomPath.exists()) {
                    val project = inferProjectFromPom(pomPath, opts, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process pom.xml at $configFile: ${e.message}")
            }
        }
        
        return CreateNodesResult(projects = projects)
    }
    
    override fun createDependencies(
        options: Any?, 
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
    
    override fun validateOptions(options: Any?): ValidationResult {
        return try {
            parseOptions(options)
            ValidationResult.valid()
        } catch (e: Exception) {
            ValidationResult.invalid("Invalid options: ${e.message}")
        }
    }
    
    private fun parseOptions(options: Any?): MavenPluginOptions {
        return when (options) {
            null -> defaultOptions
            is MavenPluginOptions -> options
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = options as Map<String, Any>
                MavenPluginOptions(
                    buildTargetName = map["buildTargetName"] as? String ?: defaultOptions.buildTargetName,
                    testTargetName = map["testTargetName"] as? String ?: defaultOptions.testTargetName,
                    packageTargetName = map["packageTargetName"] as? String ?: defaultOptions.packageTargetName
                )
            }
            else -> throw IllegalArgumentException("Invalid options type: ${options::class}")
        }
    }
    
    private fun inferProjectFromPom(
        pomPath: Path,
        options: MavenPluginOptions,
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
        val dependencies = pom["dependencies"] as? Map<String, Any> ?: emptyMap()
        
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
        options: MavenPluginOptions,
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