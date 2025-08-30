package com.forge.inference.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

data class JavaScriptPluginOptions(
    val buildTargetName: String = "build",
    val testTargetName: String = "test",
    val lintTargetName: String = "lint",
    val inferBuildFromScript: Boolean = true,
    val inferTestFromScript: Boolean = true,
    val inferLintFromScript: Boolean = true
)

class JavaScriptPlugin : InferencePlugin<JavaScriptPluginOptions> {
    
    override val name: String = "@forge/js"
    override val createNodesPattern: String = "**/package.json"
    
    private val logger = LoggerFactory.getLogger(JavaScriptPlugin::class.java)
    private val objectMapper = ObjectMapper()
    
    override val defaultOptions = JavaScriptPluginOptions()
    
    override val createDependencies = { options: JavaScriptPluginOptions?, context: CreateDependenciesContext ->
        createPackageJsonDependencies(options ?: defaultOptions, context)
    }
    
    override val createNodes = { configFiles: List<String>, options: JavaScriptPluginOptions?, context: CreateNodesContext ->
        val opts = options ?: defaultOptions
        val projects = mutableMapOf<String, ProjectConfiguration>()
        
        configFiles.forEach { configFile ->
            try {
                val packageJsonPath = Path.of(configFile)
                if (packageJsonPath.exists()) {
                    val project = inferProjectFromPackageJson(packageJsonPath, opts, context)
                    if (project != null) {
                        projects[project.name] = project
                        logger.debug("Inferred project '${project.name}' from $configFile")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to process package.json at $configFile: ${e.message}")
            }
        }
        
        CreateNodesResult(projects = projects)
    }
    
    private fun inferProjectFromPackageJson(
        packageJsonPath: Path,
        options: JavaScriptPluginOptions,
        context: CreateNodesContext
    ): ProjectConfiguration? {
        val packageJsonContent = packageJsonPath.readText()
        val packageJson = objectMapper.readValue<Map<String, Any>>(packageJsonContent)
        
        val name = packageJson["name"] as? String ?: return null
        val projectRoot = context.workspaceRoot.relativize(packageJsonPath.parent).toString()
        
        val projectType = inferProjectType(packageJson)
        val tags = extractTags(packageJson)
        val targets = inferTargets(packageJson, options, projectRoot)
        
        return ProjectConfiguration(
            name = name,
            root = projectRoot,
            sourceRoot = "$projectRoot/src",
            projectType = projectType,
            tags = tags,
            targets = targets
        )
    }
    
    private fun inferProjectType(packageJson: Map<String, Any>): String {
        val main = packageJson["main"] as? String
        val bin = packageJson["bin"]
        val exports = packageJson["exports"]
        val isPrivate = packageJson["private"] as? Boolean ?: false
        
        return when {
            bin != null -> "application"
            isPrivate -> "application" // Private packages are typically applications
            main != null || exports != null -> "library"
            else -> "library"
        }
    }
    
    private fun extractTags(packageJson: Map<String, Any>): List<String> {
        val tags = mutableListOf<String>()
        
        val keywords = packageJson["keywords"] as? List<*>
        keywords?.filterIsInstance<String>()?.let { tags.addAll(it) }
        
        val nx = packageJson["nx"] as? Map<*, *>
        val nxTags = nx?.get("tags") as? List<*>
        nxTags?.filterIsInstance<String>()?.let { tags.addAll(it) }
        
        val dependencies = packageJson["dependencies"] as? Map<*, *> ?: emptyMap<String, Any>()
        val devDependencies = packageJson["devDependencies"] as? Map<*, *> ?: emptyMap<String, Any>()
        val allDeps = dependencies + devDependencies
        
        when {
            allDeps.containsKey("react") -> tags.add("react")
            allDeps.containsKey("vue") -> tags.add("vue")
            allDeps.containsKey("angular") -> tags.add("angular")
            allDeps.containsKey("vite") -> tags.add("vite")
            allDeps.containsKey("webpack") -> tags.add("webpack")
            allDeps.containsKey("rollup") -> tags.add("rollup")
        }
        
        return tags.distinct()
    }
    
    private fun inferTargets(
        packageJson: Map<String, Any>,
        options: JavaScriptPluginOptions,
        projectRoot: String
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        val scripts = packageJson["scripts"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        // Infer build target - presence of package.json implies Node.js project that can be built
        if (options.inferBuildFromScript) {
            val buildCommand = when {
                scripts.containsKey("build") -> "npm run build"
                else -> "npm run build" // Default even if script doesn't exist - many projects add it later
            }
            
            targets[options.buildTargetName] = TargetConfiguration(
                command = buildCommand,
                options = mapOf("cwd" to projectRoot),
                inputs = listOf("default", "^default"),
                outputs = listOf("{workspaceRoot}/dist/$projectRoot"),
                cache = true
            )
        }
        
        // Infer test target - presence of package.json implies testing capability
        if (options.inferTestFromScript) {
            val testCommand = when {
                scripts.containsKey("test") -> "npm run test"
                else -> "npm test" // npm default
            }
            
            targets[options.testTargetName] = TargetConfiguration(
                command = testCommand,
                options = mapOf("cwd" to projectRoot),
                inputs = listOf("default", "^default", "{workspaceRoot}/jest.config.*", "{projectRoot}/jest.config.*"),
                outputs = listOf("{workspaceRoot}/coverage/$projectRoot"),
                cache = true
            )
        }
        
        // Infer lint target - most Node.js projects can be linted
        if (options.inferLintFromScript) {
            val lintCommand = when {
                scripts.containsKey("lint") -> "npm run lint"
                else -> "eslint {projectRoot}/src" // Common default
            }
            
            targets[options.lintTargetName] = TargetConfiguration(
                command = lintCommand,
                options = mapOf("cwd" to projectRoot),
                inputs = listOf("default", "{workspaceRoot}/.eslintrc.*", "{projectRoot}/.eslintrc.*"),
                cache = true
            )
        }
        
        // Infer serve target based on project type and available scripts
        val serveCommand = when {
            scripts.containsKey("dev") -> "npm run dev"
            scripts.containsKey("start") -> "npm run start"
            else -> null
        }
        
        if (serveCommand != null) {
            targets["serve"] = TargetConfiguration(
                command = serveCommand,
                options = mapOf("cwd" to projectRoot),
                cache = false
            )
        }
        
        return targets
    }
    
    private fun createPackageJsonDependencies(
        options: JavaScriptPluginOptions,
        context: CreateDependenciesContext
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        // Create map of package name -> project name for quick lookup
        val packageNameToProjectName = mutableMapOf<String, String>()
        
        context.projects.forEach { (projectName, projectConfig) ->
            val packageJsonPath = context.workspaceRoot.resolve(projectConfig.root).resolve("package.json")
            if (packageJsonPath.exists()) {
                try {
                    val packageJson: Map<String, Any> = objectMapper.readValue(packageJsonPath.toFile())
                    val packageName = packageJson["name"] as? String
                    
                    if (packageName != null) {
                        packageNameToProjectName[packageName] = projectName
                        logger.debug("Mapped package '$packageName' to project '$projectName'")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse package.json for dependency lookup: ${packageJsonPath}")
                }
            }
        }
        
        // Process each Node.js project to find internal dependencies
        context.projects.values.forEach { project ->
            val packageJsonPath = context.workspaceRoot.resolve(project.root).resolve("package.json")
            if (packageJsonPath.exists()) {
                try {
                    val projectDependencies = parsePackageJsonDependencies(
                        packageJsonPath, 
                        project.name, 
                        packageNameToProjectName
                    )
                    dependencies.addAll(projectDependencies)
                } catch (e: Exception) {
                    logger.warn("Failed to parse dependencies from ${packageJsonPath}: ${e.message}")
                }
            }
        }
        
        return dependencies
    }
    
    private fun parsePackageJsonDependencies(
        packageJsonPath: Path,
        sourceProjectName: String,
        packageNameToProjectName: Map<String, String>
    ): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        try {
            val packageJson: Map<String, Any> = objectMapper.readValue(packageJsonPath.toFile())
            
            // Process regular dependencies
            val deps = packageJson["dependencies"] as? Map<String, String> ?: emptyMap()
            deps.keys.forEach { depName ->
                val targetProject = packageNameToProjectName[depName]
                if (targetProject != null) {
                    dependencies.add(
                        RawProjectGraphDependency(
                            source = sourceProjectName,
                            target = targetProject,
                            type = "static",
                            sourceFile = packageJsonPath.toString()
                        )
                    )
                    logger.debug("Found internal dependency: $sourceProjectName -> $targetProject (runtime)")
                }
            }
            
            // Process dev dependencies (could be marked as different type)
            val devDeps = packageJson["devDependencies"] as? Map<String, String> ?: emptyMap()
            devDeps.keys.forEach { depName ->
                val targetProject = packageNameToProjectName[depName]
                if (targetProject != null) {
                    dependencies.add(
                        RawProjectGraphDependency(
                            source = sourceProjectName,
                            target = targetProject,
                            type = "static", // Could use "implicit" for dev deps if needed
                            sourceFile = packageJsonPath.toString()
                        )
                    )
                    logger.debug("Found internal dev dependency: $sourceProjectName -> $targetProject (dev)")
                }
            }
            
            // Process peer dependencies
            val peerDeps = packageJson["peerDependencies"] as? Map<String, String> ?: emptyMap()
            peerDeps.keys.forEach { depName ->
                val targetProject = packageNameToProjectName[depName]
                if (targetProject != null) {
                    dependencies.add(
                        RawProjectGraphDependency(
                            source = sourceProjectName,
                            target = targetProject,
                            type = "static",
                            sourceFile = packageJsonPath.toString()
                        )
                    )
                    logger.debug("Found internal peer dependency: $sourceProjectName -> $targetProject (peer)")
                }
            }
        } catch (e: Exception) {
            logger.warn("Error parsing package.json dependencies from ${packageJsonPath}: ${e.message}")
        }
        
        return dependencies
    }
}