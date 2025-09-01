package com.forge.js.devkit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
 * Options for JavaScript project inference
 */
data class JavaScriptInferenceOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test", 
    val lintTargetName: String = "lint",
    val serveTargetName: String = "serve"
)

/**
 * Core JavaScript project inference logic
 */
class JavaScriptProjectInference : ProjectGraphInferrer<JavaScriptInferenceOptions> {
    
    private val logger = LoggerFactory.getLogger(JavaScriptProjectInference::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    
    override fun inferProjects(
        configFiles: List<String>,
        options: JavaScriptInferenceOptions,
        context: CreateNodesContext
    ): Map<String, ProjectConfiguration> {
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val packageJsonPath = Path.of(configFile)
                if (packageJsonPath.exists()) {
                    val project = inferProjectFromPackageJson(packageJsonPath, options, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process package.json at $configFile: ${e.message}")
            }
        }
        
        return projects
    }
    
    override fun inferProjectDependencies(
        options: JavaScriptInferenceOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Create map of package name -> project name for quick lookup
        val projectLookup = context.projects.mapNotNull { (projectName, projectConfig) ->
            val packageJsonPath = context.workspaceRoot.resolve(projectConfig.root).resolve("package.json")
            if (packageJsonPath.exists()) {
                try {
                    val packageJson = objectMapper.readValue<Map<String, Any>>(packageJsonPath.readText())
                    val packageName = packageJson["name"] as? String
                    if (packageName != null) {
                        packageName to projectName
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to parse package.json for dependency lookup: $packageJsonPath")
                    null
                }
            } else null
        }.toMap()
        
        // Process each JavaScript project to find internal dependencies
        context.projects.values.forEach { project ->
            val packageJsonPath = context.workspaceRoot.resolve(project.root).resolve("package.json")
            if (packageJsonPath.exists()) {
                try {
                    val projectDependencies = parsePackageJsonDependencies(packageJsonPath, project.name, projectLookup)
                    dependencies.addAll(projectDependencies)
                } catch (e: Exception) {
                    logger.warn("Failed to parse dependencies from $packageJsonPath: ${e.message}")
                }
            }
        }
        
        return dependencies
    }
    
    private fun inferProjectFromPackageJson(
        packageJsonPath: Path,
        options: JavaScriptInferenceOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val packageJson = objectMapper.readValue<Map<String, Any>>(packageJsonPath.readText())
        
        val projectRoot = context.workspaceRoot.relativize(packageJsonPath.parent).toString()
        val projectName = packageJson["name"] as? String ?: return null
        
        val projectType = inferProjectType(packageJson)
        val tags = extractTags(packageJson, packageJsonPath.parent)
        val targets = inferTargets(options, projectRoot, packageJson, packageJsonPath.parent)
        
        return ProjectConfiguration(
            name = projectName,
            root = projectRoot,
            sourceRoot = projectRoot,
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun inferProjectType(packageJson: Map<String, Any>): String {
        val scripts = packageJson["scripts"] as? Map<String, String> ?: emptyMap()
        val dependencies = packageJson["dependencies"] as? Map<String, String> ?: emptyMap()
        val devDependencies = packageJson["devDependencies"] as? Map<String, String> ?: emptyMap()
        
        return when {
            scripts.containsKey("start") || dependencies.containsKey("express") || 
            dependencies.containsKey("koa") || dependencies.containsKey("fastify") -> "application"
            dependencies.containsKey("react") || dependencies.containsKey("vue") ||
            dependencies.containsKey("angular") || dependencies.containsKey("@angular/core") -> "library"
            scripts.containsKey("build") -> "library"
            else -> "library"
        }
    }
    
    private fun extractTags(packageJson: Map<String, Any>, projectDir: Path): List<String> {
        val tags = mutableListOf<String>()
        
        val dependencies = packageJson["dependencies"] as? Map<String, String> ?: emptyMap()
        val devDependencies = packageJson["devDependencies"] as? Map<String, String> ?: emptyMap()
        val allDeps = dependencies + devDependencies
        
        // Framework tags
        when {
            allDeps.containsKey("react") || allDeps.containsKey("@types/react") -> tags.add("react")
            allDeps.containsKey("vue") -> tags.add("vue")
            allDeps.containsKey("@angular/core") -> tags.add("angular")
            allDeps.containsKey("svelte") -> tags.add("svelte")
        }
        
        // Platform tags  
        when {
            allDeps.containsKey("react-native") -> tags.addAll(listOf("react-native", "mobile", "android", "ios"))
            allDeps.containsKey("electron") -> tags.add("desktop")
            allDeps.containsKey("express") || allDeps.containsKey("koa") || allDeps.containsKey("fastify") -> tags.add("backend")
            else -> tags.add("web")
        }
        
        // Technology tags
        if (projectDir.resolve("tsconfig.json").exists()) tags.add("typescript")
        if (allDeps.containsKey("webpack")) tags.add("webpack")
        if (allDeps.containsKey("vite")) tags.add("vite")
        if (allDeps.containsKey("jest")) tags.add("jest")
        if (allDeps.containsKey("eslint")) tags.add("eslint")
        if (allDeps.containsKey("prettier")) tags.add("prettier")
        
        // Purpose tags
        when {
            allDeps.containsKey("@storybook/react") -> tags.add("design-system")
            allDeps.containsKey("commander") || allDeps.containsKey("yargs") -> tags.addAll(listOf("cli", "tool"))
            packageJson["keywords"]?.let { it as? List<*> }?.contains("cli") == true -> tags.add("cli")
        }
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        options: JavaScriptInferenceOptions,
        projectRoot: String,
        packageJson: Map<String, Any>,
        projectDir: Path
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        val scripts = packageJson["scripts"] as? Map<String, String> ?: emptyMap()
        
        // Build target
        if (scripts.containsKey("build")) {
            targets[options.buildTargetName] = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf("npm run build"),
                    "cwd" to projectRoot
                ),
                inputs = listOf(
                    "default",
                    "^default",
                    "{projectRoot}/package.json",
                    "{projectRoot}/src/**/*",
                    "{projectRoot}/public/**/*"
                ),
                outputs = listOf("{projectRoot}/dist", "{projectRoot}/build"),
                cache = true
            )
        }
        
        // Test target
        if (scripts.containsKey("test")) {
            targets[options.testTargetName] = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf("npm run test"),
                    "cwd" to projectRoot
                ),
                inputs = listOf(
                    "default",
                    "^default",
                    "{projectRoot}/src/**/*",
                    "{projectRoot}/**/*.test.*",
                    "{projectRoot}/**/*.spec.*"
                ),
                outputs = listOf("{projectRoot}/coverage"),
                cache = true
            )
        }
        
        // Lint target
        if (scripts.containsKey("lint")) {
            targets[options.lintTargetName] = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf("npm run lint"),
                    "cwd" to projectRoot
                ),
                inputs = listOf(
                    "default",
                    "{projectRoot}/.eslintrc.*",
                    "{projectRoot}/src/**/*"
                ),
                cache = true
            )
        }
        
        // Serve/dev target
        if (scripts.containsKey("start") || scripts.containsKey("dev") || scripts.containsKey("serve")) {
            val command = when {
                scripts.containsKey("dev") -> "npm run dev"
                scripts.containsKey("serve") -> "npm run serve"
                else -> "npm run start"
            }
            
            targets[options.serveTargetName] = TargetConfiguration(
                executor = "forge:run-commands",
                options = mapOf(
                    "commands" to listOf(command),
                    "cwd" to projectRoot
                ),
                cache = false
            )
        }
        
        return targets
    }
    
    private fun parsePackageJsonDependencies(
        packageJsonPath: Path,
        sourceProjectName: String,
        projectLookup: Map<String, String>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val packageJson = objectMapper.readValue<Map<String, Any>>(packageJsonPath.readText())
            
            listOf("dependencies", "devDependencies", "peerDependencies").forEach { depType ->
                val deps = packageJson[depType] as? Map<String, String> ?: emptyMap()
                
                deps.forEach { (packageName, version) ->
                    val targetProject = projectLookup[packageName]
                    
                    if (targetProject != null) {
                        val dependencyType = if (depType == "devDependencies") "development" else "runtime"
                        
                        dependencies.add(
                            RawProjectGraphDependency(
                                source = sourceProjectName,
                                target = targetProject,
                                type = DependencyType.STATIC,
                                sourceFile = packageJsonPath.toString()
                            )
                        )
                        logger.debug("Found internal dependency: $sourceProjectName -> $targetProject ($dependencyType)")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing package.json dependencies from $packageJsonPath: ${e.message}")
        }
        
        return dependencies
    }
}