package com.forge.inference.plugins

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

data class GoPluginOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test",
    val vetTargetName: String = "vet",
    val tidyTargetName: String = "mod-tidy",
    val runTargetName: String = "run"
)

/**
 * Plugin that infers Go project configuration from go.mod files
 * Supports Go modules and workspaces (Go 1.18+)
 */
class GoPlugin : InferencePlugin<GoPluginOptions> {
    
    override val name: String = "@forge/go"
    override val createNodesPattern: String = "**/go.mod"
    
    private val logger = LoggerFactory.getLogger(GoPlugin::class.java)
    
    override val defaultOptions = GoPluginOptions()
    
    override val createDependencies = { options: GoPluginOptions?, context: CreateDependenciesContext ->
        createGoDependencies(options ?: defaultOptions, context)
    }
    
    override val createNodes = { configFiles: List<String>, options: GoPluginOptions?, context: CreateNodesContext ->
        val opts = options ?: defaultOptions
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val goModPath = Path.of(configFile)
                if (goModPath.exists()) {
                    val project = inferProjectFromGoMod(goModPath, opts, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process go.mod at $configFile: ${e.message}")
            }
        }
        
        CreateNodesResult(projects = projects)
    }
    
    private fun inferProjectFromGoMod(
        goModPath: Path,
        options: GoPluginOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val goModContent = goModPath.readText()
        val moduleInfo = parseGoMod(goModContent)
        
        val projectRoot = context.workspaceRoot.relativize(goModPath.parent).toString()
        val projectName = inferProjectName(moduleInfo.moduleName, projectRoot)
        
        val projectType = inferProjectType(goModPath.parent)
        val tags = extractTags(moduleInfo, goModPath.parent)
        val targets = inferTargets(options, projectRoot, goModPath.parent)
        
        return ProjectConfiguration(
            name = projectName,
            root = projectRoot,
            sourceRoot = projectRoot,
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun parseGoMod(content: String): GoModInfo {
        val lines = content.lines().map { it.trim() }
        var moduleName = ""
        var goVersion = ""
        val dependencies = mutableListOf<GoDependency>()
        
        var inRequireBlock = false
        
        for (line in lines) {
            when {
                line.startsWith("module ") -> {
                    moduleName = line.substring(7).trim()
                }
                line.startsWith("go ") -> {
                    goVersion = line.substring(3).trim()
                }
                line == "require (" -> {
                    inRequireBlock = true
                }
                line == ")" && inRequireBlock -> {
                    inRequireBlock = false
                }
                inRequireBlock && line.isNotBlank() -> {
                    val parts = line.split(" ", limit = 2)
                    if (parts.size == 2) {
                        dependencies.add(GoDependency(parts[0], parts[1]))
                    }
                }
                line.startsWith("require ") && !inRequireBlock -> {
                    // Single line require
                    val requireContent = line.substring(8).trim()
                    val parts = requireContent.split(" ", limit = 2)
                    if (parts.size == 2) {
                        dependencies.add(GoDependency(parts[0], parts[1]))
                    }
                }
            }
        }
        
        return GoModInfo(moduleName, goVersion, dependencies)
    }
    
    private fun inferProjectName(moduleName: String, projectRoot: String): String {
        // Use the last part of the module name or fall back to directory name
        val moduleBaseName = moduleName.split("/").lastOrNull()?.takeIf { it.isNotBlank() }
        val dirName = projectRoot.split("/").lastOrNull()?.takeIf { it.isNotBlank() }
        
        return moduleBaseName ?: dirName ?: "go-project"
    }
    
    private fun inferProjectType(projectDir: Path): String {
        // Check for main.go in root or cmd/ directory
        val hasMainGo = projectDir.resolve("main.go").exists() || 
                       projectDir.resolve("cmd").exists()
        
        return if (hasMainGo) "application" else "library"
    }
    
    private fun extractTags(moduleInfo: GoModInfo, projectDir: Path): List<String> {
        val tags = mutableListOf<String>()
        
        tags.add("go")
        
        // Add Go version tag
        if (moduleInfo.goVersion.isNotBlank()) {
            tags.add("go${moduleInfo.goVersion}")
        }
        
        // Check for common Go frameworks/libraries
        val frameworkPatterns = mapOf(
            "gin" to "github.com/gin-gonic/gin",
            "echo" to "github.com/labstack/echo",
            "fiber" to "github.com/gofiber/fiber",
            "grpc" to "google.golang.org/grpc",
            "cobra" to "github.com/spf13/cobra",
            "viper" to "github.com/spf13/viper"
        )
        
        moduleInfo.dependencies.forEach { dep ->
            frameworkPatterns.forEach { (tag, pattern) ->
                if (dep.module.contains(pattern)) {
                    tags.add(tag)
                }
            }
        }
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        options: GoPluginOptions,
        projectRoot: String,
        projectDir: Path
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Build target - universal for all Go projects
        targets[options.buildTargetName] = TargetConfiguration(
            command = "go build ./...",
            options = mapOf("cwd" to projectRoot),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/go.mod",
                "{projectRoot}/go.sum",
                "{projectRoot}/**/*.go"
            ),
            outputs = listOf("{projectRoot}/bin"),
            cache = true
        )
        
        // Test target - universal for Go projects
        targets[options.testTargetName] = TargetConfiguration(
            command = "go test ./...",
            options = mapOf("cwd" to projectRoot),
            inputs = listOf(
                "default",
                "^default",
                "{projectRoot}/go.mod",
                "{projectRoot}/**/*.go",
                "{projectRoot}/**/*_test.go"
            ),
            outputs = listOf("{projectRoot}/coverage.out"),
            cache = true
        )
        
        // Vet target - static analysis
        targets[options.vetTargetName] = TargetConfiguration(
            command = "go vet ./...",
            options = mapOf("cwd" to projectRoot),
            inputs = listOf(
                "default",
                "{projectRoot}/go.mod",
                "{projectRoot}/**/*.go"
            ),
            cache = true
        )
        
        // Mod tidy target - dependency management
        targets[options.tidyTargetName] = TargetConfiguration(
            command = "go mod tidy",
            options = mapOf("cwd" to projectRoot),
            inputs = listOf(
                "{projectRoot}/go.mod",
                "{projectRoot}/**/*.go"
            ),
            outputs = listOf("{projectRoot}/go.sum"),
            cache = false
        )
        
        // Run target - only for applications with main.go
        if (projectDir.resolve("main.go").exists()) {
            targets[options.runTargetName] = TargetConfiguration(
                command = "go run .",
                options = mapOf("cwd" to projectRoot),
                inputs = listOf(
                    "{projectRoot}/go.mod",
                    "{projectRoot}/**/*.go"
                ),
                cache = false,
                dependsOn = listOf(options.buildTargetName)
            )
        }
        
        return targets
    }
    
    private fun createGoDependencies(
        options: GoPluginOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Create map of module name -> project name for quick lookup
        val projectLookup = context.projects.mapNotNull { (projectName, projectConfig) ->
            val goModPath = context.workspaceRoot.resolve(projectConfig.root).resolve("go.mod")
            if (goModPath.exists()) {
                try {
                    val goModContent = goModPath.readText()
                    val moduleInfo = parseGoMod(goModContent)
                    
                    if (moduleInfo.moduleName.isNotBlank()) {
                        moduleInfo.moduleName to projectName
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to parse go.mod for dependency lookup: ${goModPath}")
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
                    logger.warn("Failed to parse dependencies from ${goModPath}: ${e.message}")
                }
            }
        }
        
        return dependencies
    }
    
    private fun parseGoModDependencies(
        goModPath: Path,
        sourceProjectName: String,
        projectLookup: Map<String, String>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val goModContent = goModPath.readText()
            val moduleInfo = parseGoMod(goModContent)
            
            moduleInfo.dependencies.forEach { dep ->
                val targetProject = projectLookup[dep.module]
                
                if (targetProject != null) {
                    // This is an internal project dependency
                    dependencies.add(
                        RawProjectGraphDependency(
                            source = sourceProjectName,
                            target = targetProject,
                            type = "static",
                            sourceFile = goModPath.toString()
                        )
                    )
                    logger.debug("Found internal dependency: $sourceProjectName -> $targetProject (${dep.version})")
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing go.mod dependencies from ${goModPath}: ${e.message}")
        }
        
        return dependencies
    }
}

data class GoModInfo(
    val moduleName: String,
    val goVersion: String,
    val dependencies: List<GoDependency>
)

data class GoDependency(
    val module: String,
    val version: String
)