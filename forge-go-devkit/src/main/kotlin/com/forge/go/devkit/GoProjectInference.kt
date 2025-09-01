package com.forge.go.devkit

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.plugin.api.ProjectConfiguration
import com.forge.plugin.api.TargetConfiguration
import com.forge.plugin.api.CreateNodesContext
import com.forge.plugin.api.CreateDependenciesContext
import com.forge.plugin.api.RawProjectGraphDependency
import com.forge.plugin.api.DependencyType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Options for Go project inference
 */
data class GoInferenceOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test",
    val lintTargetName: String = "lint"
)

/**
 * Core Go project inference logic
 */
class GoProjectInference {
    
    private val logger = LoggerFactory.getLogger(GoProjectInference::class.java)
    private val objectMapper = ObjectMapper()
    
    fun createNodes(
        configFiles: List<String>,
        options: GoInferenceOptions,
        context: CreateNodesContext
    ): Map<String, ProjectConfiguration> {
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val goModPath = Path.of(configFile)
                if (goModPath.exists()) {
                    val project = inferProjectFromGoMod(goModPath, options, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process go.mod at $configFile: ${e.message}")
            }
        }
        
        return projects
    }
    
    fun createDependencies(
        options: GoInferenceOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Create map of module path -> project name for quick lookup
        val projectLookup = context.projects.mapNotNull { (projectName, projectConfig) ->
            val goModPath = context.workspaceRoot.resolve(projectConfig.root).resolve("go.mod")
            if (goModPath.exists()) {
                try {
                    val modulePath = parseGoModulePath(goModPath.readText())
                    if (modulePath != null) {
                        modulePath to projectName
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to parse go.mod for dependency lookup: $goModPath")
                    null
                }
            } else null
        }.toMap()
        
        // Process each Go project to find internal dependencies
        context.projects.values.forEach { project ->
            val goModPath = context.workspaceRoot.resolve(project.root).resolve("go.mod")
            if (goModPath.exists()) {
                try {
                    val projectDependencies = parseGoModDependencies(goModPath, project.name, projectLookup)
                    dependencies.addAll(projectDependencies)
                } catch (e: Exception) {
                    logger.warn("Failed to parse dependencies from $goModPath: ${e.message}")
                }
            }
        }
        
        return dependencies
    }
    
    private fun inferProjectFromGoMod(
        goModPath: Path,
        options: GoInferenceOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val goModContent = goModPath.readText()
        val modulePath = parseGoModulePath(goModContent) ?: return null
        
        val projectRoot = context.workspaceRoot.relativize(goModPath.parent).toString()
        
        // Extract project name from module path (last segment)
        val projectName = modulePath.split("/").lastOrNull() ?: return null
        
        val projectType = inferProjectType(goModPath.parent)
        val tags = extractTags(goModPath.parent)
        val targets = inferTargets(options, projectRoot)
        
        return ProjectConfiguration(
            name = projectName,
            root = projectRoot,
            sourceRoot = projectRoot,
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun parseGoModulePath(goModContent: String): String? {
        return try {
            val lines = goModContent.lines()
            val moduleLineRegex = Regex("""module\s+(.+)""")
            
            lines.forEach { line ->
                val trimmedLine = line.trim()
                val match = moduleLineRegex.find(trimmedLine)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
            null
        } catch (e: Exception) {
            logger.warn("Failed to parse go.mod module path: ${e.message}")
            null
        }
    }
    
    private fun inferProjectType(projectDir: Path): String {
        // Check if it has main.go in cmd/ directory (typical for applications)
        if (projectDir.resolve("cmd").exists() || 
            projectDir.resolve("main.go").exists()) {
            return "application"
        }
        
        // Check if it's likely a library (has .go files but no main)
        val hasGoFiles = try {
            projectDir.toFile().walkTopDown()
                .filter { it.isFile && it.extension == "go" }
                .any()
        } catch (e: Exception) {
            false
        }
        
        return if (hasGoFiles) "library" else "application"
    }
    
    private fun extractTags(projectDir: Path): List<String> {
        val tags = mutableListOf<String>()
        
        tags.add("go")
        tags.add("golang")
        
        // Check for common Go patterns
        if (projectDir.resolve("cmd").exists()) {
            tags.add("cli")
        }
        
        if (projectDir.resolve("api").exists() || 
            projectDir.resolve("internal/api").exists()) {
            tags.add("api")
        }
        
        if (projectDir.resolve("web").exists() || 
            projectDir.resolve("static").exists()) {
            tags.add("web")
        }
        
        // Check for testing
        val hasTests = try {
            projectDir.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith("_test.go") }
                .any()
        } catch (e: Exception) {
            false
        }
        
        if (hasTests) {
            tags.add("testing")
        }
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        options: GoInferenceOptions,
        projectRoot: String
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Build target
        targets[options.buildTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("go build ./..."),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/**/*.go",
                "{projectRoot}/go.mod",
                "{projectRoot}/go.sum"
            ),
            outputs = listOf("{projectRoot}/bin/**/*"),
            cache = true
        )
        
        // Test target
        targets[options.testTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("go test ./..."),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/**/*.go",
                "{projectRoot}/**/*_test.go"
            ),
            outputs = listOf(),
            cache = true,
            dependsOn = listOf()
        )
        
        // Lint target
        targets[options.lintTargetName] = TargetConfiguration(
            executor = "forge:run-commands",
            options = mapOf(
                "commands" to listOf("go vet ./...", "go fmt ./..."),
                "cwd" to projectRoot
            ),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/**/*.go"
            ),
            outputs = listOf(),
            cache = true
        )
        
        return targets
    }
    
    private fun parseGoModDependencies(
        goModPath: Path,
        sourceProjectName: String,
        projectLookup: Map<String, String>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val goModContent = goModPath.readText()
            val lines = goModContent.lines()
            
            var inRequireBlock = false
            val requireRegex = Regex("""require\s+(.+)""")
            val dependencyRegex = Regex("""^\s*([^\s]+)\s+[^\s]+.*$""")
            
            lines.forEach { line ->
                val trimmedLine = line.trim()
                
                // Handle single-line require
                val requireMatch = requireRegex.find(trimmedLine)
                if (requireMatch != null) {
                    val requirement = requireMatch.groupValues[1].trim()
                    if (requirement.startsWith("(")) {
                        inRequireBlock = true
                    } else {
                        // Single line require
                        val modulePath = requirement.split(" ")[0]
                        val targetProject = projectLookup[modulePath]
                        if (targetProject != null) {
                            dependencies.add(
                                RawProjectGraphDependency(
                                    source = sourceProjectName,
                                    target = targetProject,
                                    type = DependencyType.STATIC,
                                    sourceFile = goModPath.toString()
                                )
                            )
                        }
                    }
                } else if (inRequireBlock) {
                    if (trimmedLine == ")") {
                        inRequireBlock = false
                    } else {
                        val depMatch = dependencyRegex.find(trimmedLine)
                        if (depMatch != null) {
                            val modulePath = depMatch.groupValues[1]
                            val targetProject = projectLookup[modulePath]
                            if (targetProject != null) {
                                dependencies.add(
                                    RawProjectGraphDependency(
                                        source = sourceProjectName,
                                        target = targetProject,
                                        type = DependencyType.STATIC,
                                        sourceFile = goModPath.toString()
                                    )
                                )
                                logger.debug("Found internal dependency: $sourceProjectName -> $targetProject")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing go.mod dependencies from $goModPath: ${e.message}")
        }
        
        return dependencies
    }
}