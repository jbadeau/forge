package com.frontseat.backstage

import com.frontseat.nature.ProjectNature
import com.frontseat.plugin.api.ProjectConfiguration
import com.frontseat.plugin.api.TargetConfiguration
import com.frontseat.plugin.api.InferenceResult
import com.frontseat.plugin.api.RawProjectGraphDependency
import com.frontseat.plugin.api.DependencyType
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.toList

/**
 * Discovers projects based on Backstage catalog-info.yaml files
 * instead of build tool files (pom.xml, package.json, etc.)
 */
class BackstageProjectDiscoverer {
    private val logger = LoggerFactory.getLogger(BackstageProjectDiscoverer::class.java)
    private val catalogParser = CatalogParser()
    private val natureInference = NatureInferenceService()
    private val dependencyInference = DependencyInferenceService()
    
    companion object {
        const val CATALOG_FILE_NAME = "catalog-info.yaml"
        const val CATALOG_FILE_PATTERN = "**/catalog-info.{yaml,yml}"
    }
    
    /**
     * Discover all projects in the workspace by finding catalog-info.yaml files
     */
    fun discoverProjects(workspaceRoot: Path): InferenceResult {
        logger.info("Discovering projects using Backstage catalog files in: $workspaceRoot")
        
        val projects = mutableMapOf<String, ProjectConfiguration>()
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        val externalNodes = mutableMapOf<String, Any>()
        
        // Find all catalog-info.yaml files
        val catalogFiles = findCatalogFiles(workspaceRoot)
        logger.info("Found ${catalogFiles.size} catalog files")
        
        // Process each catalog file
        catalogFiles.forEach { catalogFile ->
            try {
                processCatalogFile(
                    catalogFile,
                    workspaceRoot,
                    projects,
                    dependencies,
                    externalNodes
                )
            } catch (e: Exception) {
                logger.error("Failed to process catalog file: $catalogFile", e)
            }
        }
        
        logger.info("Discovered ${projects.size} projects from Backstage catalog files")
        
        return InferenceResult(
            projects = projects,
            dependencies = dependencies,
            externalNodes = externalNodes
        )
    }
    
    /**
     * Find all catalog-info.yaml files in the workspace
     */
    private fun findCatalogFiles(workspaceRoot: Path): List<Path> {
        return try {
            Files.walk(workspaceRoot)
                .filter { it.isRegularFile() }
                .filter { path ->
                    val fileName = path.fileName.toString()
                    fileName == "catalog-info.yaml" || fileName == "catalog-info.yml"
                }
                .filter { path ->
                    // Exclude node_modules, target, build directories
                    val pathStr = path.toString()
                    !pathStr.contains("/node_modules/") &&
                    !pathStr.contains("/target/") &&
                    !pathStr.contains("/build/") &&
                    !pathStr.contains("/dist/") &&
                    !pathStr.contains("/.git/")
                }
                .toList()
        } catch (e: Exception) {
            logger.error("Error scanning for catalog files: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Process a single catalog file and extract project information
     */
    private fun processCatalogFile(
        catalogFile: Path,
        workspaceRoot: Path,
        projects: MutableMap<String, ProjectConfiguration>,
        dependencies: MutableList<RawProjectGraphDependency>,
        externalNodes: MutableMap<String, Any>
    ) {
        logger.debug("Processing catalog file: $catalogFile")
        
        // Parse all entities in the catalog file
        val entities = catalogParser.parseMultiDocumentCatalog(catalogFile)
        if (entities.isEmpty()) {
            logger.warn("No valid entities found in: $catalogFile")
            return
        }
        
        // Process each entity
        entities.forEach { entity ->
            when (entity) {
                is Component -> processComponent(
                    entity, 
                    catalogFile, 
                    workspaceRoot, 
                    projects, 
                    dependencies
                )
                is System -> processSystem(
                    entity,
                    externalNodes
                )
                is Api -> processApi(
                    entity,
                    externalNodes
                )
                is Resource -> processResource(
                    entity,
                    externalNodes
                )
                else -> {
                    logger.debug("Skipping non-component entity: ${entity.kind} - ${entity.metadata.name}")
                }
            }
        }
    }
    
    /**
     * Process a Component entity and create a project configuration
     */
    private fun processComponent(
        component: Component,
        catalogFile: Path,
        workspaceRoot: Path,
        projects: MutableMap<String, ProjectConfiguration>,
        dependencies: MutableList<RawProjectGraphDependency>
    ) {
        val projectRoot = catalogFile.parent
        val relativePath = workspaceRoot.relativize(projectRoot).toString()
        val projectName = component.metadata.namespace?.let { 
            "${it}/${component.metadata.name}" 
        } ?: component.metadata.name
        
        logger.info("Processing component: $projectName at $relativePath")
        
        // Infer project natures from files in the project directory + entity type
        val inferredNatures = natureInference.inferNatures(projectRoot, component.spec.type)
        
        // Apply manual overrides from annotations if present
        val natures = natureInference.applyManualOverrides(inferredNatures, component.metadata.annotations)
        
        logger.debug("Inferred natures for $projectName: $inferredNatures")
        if (inferredNatures != natures) {
            logger.info("Applied manual nature override for $projectName: $natures")
        }
        
        // Generate targets based on natures only
        val targets = generateTargets(component, natures)
        
        // Infer dependencies from build files based on natures
        val inferredDependencies = dependencyInference.inferDependencies(projectRoot, projectName, natures)
        
        // Start with inferred dependencies (natures are primary source of truth)
        val allBackstageDependencies = mutableSetOf<String>().apply {
            addAll(inferredDependencies)
        }
        
        // Add explicit Backstage dependencies only if they can't be inferred
        val explicitDeps = component.spec.dependsOn + component.spec.consumesApis + component.spec.providesApis
        explicitDeps.forEach { dep ->
            if (!allBackstageDependencies.contains(dep)) {
                allBackstageDependencies.add(dep)
                logger.debug("Added explicit dependency '$dep' for $projectName (not inferred from build tools)")
            }
        }
        
        logger.debug("Dependencies for $projectName: inferred=${inferredDependencies.size}, explicit=${explicitDeps.size}, total=${allBackstageDependencies.size}")
        
        // Extract tags from metadata
        val tags = mutableSetOf<String>().apply {
            addAll(component.metadata.tags)
            add("type:${component.spec.type}")
            add("lifecycle:${component.spec.lifecycle}")
            natures.forEach { add("nature:${it.name.lowercase()}") }
        }
        
        // Create project configuration
        val projectConfig = ProjectConfiguration(
            root = relativePath,
            name = projectName,
            targets = targets,
            tags = tags.toList()
        )
        
        // Store metadata separately if needed
        val metadata = mapOf(
                "backstage.entity.kind" to component.kind,
                "backstage.entity.type" to component.spec.type,
                "backstage.entity.lifecycle" to component.spec.lifecycle,
                "backstage.entity.owner" to component.spec.owner,
                "backstage.entity.system" to (component.spec.system ?: ""),
                "backstage.entity.namespace" to (component.metadata.namespace ?: "default")
            )
        
        projects[projectName] = projectConfig
        
        // Process all dependencies (explicit + inferred)
        allBackstageDependencies.forEach { dep ->
            dependencies.add(
                RawProjectGraphDependency(
                    source = projectName,
                    target = dep,
                    type = DependencyType.IMPLICIT
                )
            )
        }
    }
    
    /**
     * Generate targets based on project natures only
     * Tasks are generated in-memory from the presence of natures
     */
    private fun generateTargets(
        component: Component,
        natures: Set<ProjectNature>
    ): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Ask each nature to create its tasks
        natures.forEach { nature ->
            try {
                // TODO: Need to implement NatureContext
                // val context = createNatureContext(projectPath, natures)
                // val natureTasks = nature.createTasks(projectPath, context)
                // natureTasks.forEach { (name, definition) ->
                //     targets[name] = definition.configuration
                // }
                logger.debug("Task generation for nature ${nature.id} not yet implemented")
            } catch (e: Exception) {
                logger.error("Failed to generate tasks from nature ${nature.id}", e)
            }
        }
        
        // If no targets were generated, add a default build target
        if (targets.isEmpty()) {
            targets["build"] = TargetConfiguration(
                executor = "frontseat:run-command",
                options = mapOf(
                    "command" to "echo 'No specific build tools detected in project'"
                ),
                dependsOn = emptyList()
            )
        }
        
        return targets
    }
    
    
    /**
     * Process a System entity
     */
    private fun processSystem(
        system: System,
        externalNodes: MutableMap<String, Any>
    ) {
        val systemName = system.metadata.namespace?.let { 
            "${it}/${system.metadata.name}" 
        } ?: system.metadata.name
        
        externalNodes["system:$systemName"] = mapOf(
            "type" to "system",
            "name" to system.metadata.name,
            "owner" to system.spec.owner,
            "domain" to system.spec.domain,
            "description" to system.metadata.description
        )
    }
    
    /**
     * Process an API entity
     */
    private fun processApi(
        api: Api,
        externalNodes: MutableMap<String, Any>
    ) {
        val apiName = api.metadata.namespace?.let { 
            "${it}/${api.metadata.name}" 
        } ?: api.metadata.name
        
        externalNodes["api:$apiName"] = mapOf(
            "type" to "api",
            "name" to api.metadata.name,
            "apiType" to api.spec.type,
            "lifecycle" to api.spec.lifecycle,
            "owner" to api.spec.owner,
            "system" to api.spec.system,
            "description" to api.metadata.description
        )
    }
    
    /**
     * Process a Resource entity
     */
    private fun processResource(
        resource: Resource,
        externalNodes: MutableMap<String, Any>
    ) {
        val resourceName = resource.metadata.namespace?.let { 
            "${it}/${resource.metadata.name}" 
        } ?: resource.metadata.name
        
        externalNodes["resource:$resourceName"] = mapOf(
            "type" to "resource",
            "name" to resource.metadata.name,
            "resourceType" to resource.spec.type,
            "owner" to resource.spec.owner,
            "system" to resource.spec.system,
            "description" to resource.metadata.description
        )
    }
}