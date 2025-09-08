package com.frontseat.project.backstage

import com.frontseat.project.nature.NatureRegistry
import com.frontseat.project.nature.Nature
import com.frontseat.task.CommandTask
import com.frontseat.project.nature.InferenceResult
import com.frontseat.project.nature.RawProjectGraphDependency
import com.frontseat.project.nature.DependencyType
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.toList

/**
 * Discovers projects based on Backstage catalog-info.yaml files
 * instead of build tool files (pom.xml, package.json, etc.)
 */
class BackstageProjectDiscoverer(private val natureRegistry: NatureRegistry) {
    private val logger = LoggerFactory.getLogger(BackstageProjectDiscoverer::class.java)
    private val catalogParser = BackstageCatalogParser()
    private val natureInferenceEngine = com.frontseat.project.nature.NatureInferenceEngine(natureRegistry)
    
    companion object {
        const val CATALOG_FILE_NAME = "catalog-info.yaml"
        const val CATALOG_FILE_PATTERN = "**/catalog-info.{yaml,yml}"
    }
    
    /**
     * Discover all projects in the workspace by finding catalog-info.yaml files
     */
    fun discoverProjects(workspaceRoot: Path): InferenceResult {
        logger.info("Discovering projects using Backstage catalog files in: $workspaceRoot")
        
        val projects = mutableMapOf<String, Map<String, CommandTask>>()
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
        projects: MutableMap<String, Map<String, CommandTask>>,
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
        projects: MutableMap<String, Map<String, CommandTask>>,
        dependencies: MutableList<RawProjectGraphDependency>
    ) {
        val projectRoot = catalogFile.parent
        val relativePath = workspaceRoot.relativize(projectRoot).toString()
        val projectName = component.metadata.namespace?.let { 
            "${it}/${component.metadata.name}" 
        } ?: component.metadata.name
        
        logger.info("Processing component: $projectName at $relativePath")
        
        // Use the project inference engine to get natures and tasks
        val inferredProject = natureInferenceEngine.inferProject(projectRoot)
        
        val (natures, tasks) = if (inferredProject != null) {
            logger.debug("Inferred project: ${inferredProject.natures}, tasks: ${inferredProject.tasks.keys}")
            
            // Apply manual nature overrides from annotations if present
            val natures = applyManualNatureOverrides(inferredProject.natures, component.metadata.annotations)
            val tasks = if (natures != inferredProject.natures) {
                // Re-infer with overridden natures
                // TODO: Need to re-run inference with overridden natures
                inferredProject.tasks
            } else {
                inferredProject.tasks
            }
            
            Pair(natures, tasks)
        } else {
            logger.debug("No natures inferred for $projectName")
            Pair(emptySet<String>(), emptyMap<String, CommandTask>())
        }
        
        // For now, use explicit Backstage dependencies
        // TODO: Integrate dependency inference from ProjectInferenceEngine
        val allBackstageDependencies = mutableSetOf<String>().apply {
            addAll(component.spec.dependsOn)
            addAll(component.spec.consumesApis)
            addAll(component.spec.providesApis)
        }
        
        logger.debug("Dependencies for $projectName: ${allBackstageDependencies.size} explicit")
        
        // Store just the command tasks for this project
        projects[projectName] = tasks
        
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
     * Apply manual nature overrides from Backstage annotations
     */
    private fun applyManualNatureOverrides(
        inferredNatures: Set<String>,
        annotations: Map<String, String>
    ): Set<String> {
        val manualNatures = annotations[BackstageAnnotations.FRONTSEAT_NATURES]
        
        return if (manualNatures != null) {
            logger.info("Applying manual nature override: $manualNatures")
            manualNatures.split(",").map { it.trim() }.toSet()
        } else {
            inferredNatures
        }
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