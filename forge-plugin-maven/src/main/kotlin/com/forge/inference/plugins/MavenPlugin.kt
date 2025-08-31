package com.forge.inference.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.forge.core.ProjectConfiguration
import com.forge.core.TargetConfiguration
import com.forge.inference.CreateNodesContext
import com.forge.inference.CreateNodesResult
import com.forge.inference.CreateDependenciesContext
import com.forge.inference.RawProjectGraphDependency
import com.forge.inference.InferencePlugin
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

data class MavenPluginOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test",
    val lintTargetName: String = "lint",
    val packageTargetName: String = "package"
)

/**
 * Plugin that infers project configuration from pom.xml files
 * Supports Maven-based Java/Kotlin projects
 */
class MavenPlugin : InferencePlugin<MavenPluginOptions> {
    
    override val name: String = "@forge/maven"
    override val createNodesPattern: String = "**/pom.xml"
    
    private val logger = LoggerFactory.getLogger(MavenPlugin::class.java)
    private val xmlMapper = XmlMapper()
    
    override val defaultOptions = MavenPluginOptions()
    
    override val createDependencies = { options: MavenPluginOptions?, context: CreateDependenciesContext ->
        createMavenDependencies(options ?: defaultOptions, context)
    }
    
    override val createNodes = { configFiles: List<String>, options: MavenPluginOptions?, context: CreateNodesContext ->
        val opts = options ?: defaultOptions
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
        
        CreateNodesResult(projects = projects)
    }
    
    private fun inferProjectFromPom(
        pomPath: Path,
        options: MavenPluginOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val pomContent = pomPath.readText()
        val pomXml = xmlMapper.readValue(pomContent, Map::class.java) as Map<String, Any>
        
        val artifactId = pomXml["artifactId"] as? String ?: return null
        val projectRoot = context.workspaceRoot.relativize(pomPath.parent).toString()
        
        val projectType = inferProjectType(pomXml, pomPath.parent)
        val tags = extractTags(pomXml, pomPath.parent)
        val targets = inferTargets(pomXml, options, projectRoot, pomPath.parent)
        
        return ProjectConfiguration(
            name = artifactId,
            root = projectRoot,
            sourceRoot = "$projectRoot/src/main/kotlin",
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun inferProjectType(pomXml: Map<String, Any>, projectDir: Path): String {
        val packaging = pomXml["packaging"] as? String ?: "jar"
        
        // Check for main class or executable configuration
        val hasMainClass = checkForMainClass(pomXml)
        val hasSpringBoot = checkForSpringBoot(pomXml)
        
        return when {
            packaging == "war" -> "application"
            packaging == "ear" -> "application" 
            hasSpringBoot -> "application"
            hasMainClass -> "application"
            packaging == "pom" -> "library" // Multi-module parent
            else -> "library"
        }
    }
    
    private fun checkForMainClass(pomXml: Map<String, Any>): Boolean {
        val build = pomXml["build"] as? Map<*, *>
        val plugins = build?.get("plugins") as? Map<*, *>
        // This is a simplified check - in real implementation we'd parse the XML more thoroughly
        return false // For now, default to library unless explicitly configured
    }
    
    private fun checkForSpringBoot(pomXml: Map<String, Any>): Boolean {
        val dependencies = pomXml["dependencies"] as? Map<*, *>
        // Simplified check for Spring Boot
        return pomXml.toString().contains("spring-boot")
    }
    
    private fun extractTags(pomXml: Map<String, Any>, projectDir: Path): List<String> {
        val tags = mutableListOf<String>()
        
        // Add language tags based on directory structure
        when {
            projectDir.resolve("src/main/kotlin").exists() -> tags.add("kotlin")
            projectDir.resolve("src/main/java").exists() -> tags.add("java")
            projectDir.resolve("src/main/scala").exists() -> tags.add("scala")
        }
        
        // Add framework tags based on dependencies
        val pomString = pomXml.toString().lowercase()
        when {
            pomString.contains("spring-boot") -> tags.add("spring-boot")
            pomString.contains("quarkus") -> tags.add("quarkus")
            pomString.contains("micronaut") -> tags.add("micronaut")
            pomString.contains("junit") -> tags.add("junit")
        }
        
        tags.add("maven")
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        pomXml: Map<String, Any>,
        options: MavenPluginOptions,
        projectRoot: String,
        projectDir: Path
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Build target - presence of pom.xml implies Maven project that can be compiled
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
        
        // Test target - Maven projects typically have tests
        targets[options.testTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("mvn test"),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/pom.xml",
                "{projectRoot}/src/test/**/*"
            ),
            outputs = listOf(
                "{projectRoot}/target/test-classes",
                "{projectRoot}/target/surefire-reports"
            ),
            cache = true
        )
        
        // Package target - create JAR/WAR
        targets[options.packageTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("mvn package"),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/pom.xml",
                "{projectRoot}/src/**/*"
            ),
            outputs = listOf("{projectRoot}/target/*.jar", "{projectRoot}/target/*.war"),
            cache = true,
            dependsOn = listOf("build", "test")
        )
        
        // Lint target - if checkstyle or spotless is configured
        if (hasLintingPlugin(pomXml)) {
            targets[options.lintTargetName] = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf("mvn checkstyle:check"),
                    "cwd" to projectRoot
                ),
                inputs = listOf("default", "{projectRoot}/pom.xml"),
                cache = true
            )
        }
        
        return targets
    }
    
    private fun hasLintingPlugin(pomXml: Map<String, Any>): Boolean {
        val pomString = pomXml.toString()
        return pomString.contains("checkstyle") || 
               pomString.contains("spotless") ||
               pomString.contains("maven-pmd-plugin")
    }
    
    private fun createMavenDependencies(
        options: MavenPluginOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Create map of groupId:artifactId -> project name for quick lookup
        val projectLookup = context.projects.mapNotNull { (projectName, projectConfig) ->
            val pomPath = context.workspaceRoot.resolve(projectConfig.root).resolve("pom.xml")
            if (pomPath.exists()) {
                try {
                    val pomContent = pomPath.readText()
                    val pomXml = xmlMapper.readValue(pomContent, Map::class.java) as Map<String, Any>
                    val groupId = pomXml["groupId"] as? String
                    val artifactId = pomXml["artifactId"] as? String
                    
                    if (groupId != null && artifactId != null) {
                        "$groupId:$artifactId" to projectName
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to parse POM for dependency lookup: ${pomPath}")
                    null
                }
            } else null
        }.toMap()
        
        // Process each Maven project to find dependencies
        context.projects.values.forEach { project ->
            val pomPath = context.workspaceRoot.resolve(project.root).resolve("pom.xml")
            if (pomPath.exists()) {
                try {
                    val projectDependencies = parsePomDependencies(pomPath, project.name, projectLookup)
                    dependencies.addAll(projectDependencies)
                } catch (e: Exception) {
                    logger.warn("Failed to parse dependencies from ${pomPath}: ${e.message}")
                }
            }
        }
        
        return dependencies
    }
    
    private fun parsePomDependencies(
        pomPath: Path,
        sourceProjectName: String,
        projectLookup: Map<String, String>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val pomContent = pomPath.readText()
            val pomXml = xmlMapper.readValue(pomContent, Map::class.java) as Map<String, Any>
            
            // Extract dependencies section
            val dependenciesSection = pomXml["dependencies"] as? Map<*, *>
            if (dependenciesSection != null) {
                val dependencyList = dependenciesSection["dependency"]
                
                // Handle both single dependency and list of dependencies
                val deps = when (dependencyList) {
                    is List<*> -> dependencyList
                    is Map<*, *> -> listOf(dependencyList)
                    else -> emptyList()
                }
                
                deps.forEach { dep ->
                    if (dep is Map<*, *>) {
                        val groupId = dep["groupId"] as? String
                        val artifactId = dep["artifactId"] as? String
                        val scope = dep["scope"] as? String ?: "compile"
                        
                        if (groupId != null && artifactId != null) {
                            val dependencyKey = "$groupId:$artifactId"
                            val targetProject = projectLookup[dependencyKey]
                            
                            if (targetProject != null) {
                                // This is an internal project dependency
                                dependencies.add(
                                    RawProjectGraphDependency(
                                        source = sourceProjectName,
                                        target = targetProject,
                                        type = mapMavenScopeToType(scope),
                                        sourceFile = pomPath.toString()
                                    )
                                )
                                logger.debug("Found internal dependency: $sourceProjectName -> $targetProject ($scope)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing POM dependencies from ${pomPath}: ${e.message}")
        }
        
        return dependencies
    }
    
    private fun mapMavenScopeToType(scope: String): String {
        return when (scope.lowercase()) {
            "compile" -> "static"
            "runtime" -> "static" 
            "provided" -> "static"
            "test" -> "static"
            "import" -> "static"
            else -> "static"
        }
    }
}