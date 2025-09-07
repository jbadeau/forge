package com.frontseat.backstage

import com.frontseat.nature.NatureRegistry
import com.frontseat.nature.ProjectNature
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Service for inferring project natures based on files present in the project directory
 */
class NatureInferenceService {
    private val logger = LoggerFactory.getLogger(NatureInferenceService::class.java)
    private val natureRegistry = NatureRegistry.instance
    
    /**
     * Infer project natures from files in the project directory
     */
    fun inferNatures(projectPath: Path, componentType: String? = null): Set<ProjectNature> {
        logger.debug("Inferring natures for project at $projectPath")
        
        // Use the registry to find all applicable natures
        val applicableNatures = natureRegistry.findApplicableNatures(projectPath)
        logger.debug("Found ${applicableNatures.size} applicable natures: ${applicableNatures.map { it.id }}")
        
        // Resolve dependencies and conflicts
        val resolvedNatures = natureRegistry.resolveNatures(applicableNatures)
        logger.debug("Resolved to ${resolvedNatures.size} natures: ${resolvedNatures.map { it.id }}")
        
        return resolvedNatures
    }
    
    
    /**
     * Apply manual nature overrides from annotations
     */
    fun applyManualOverrides(
        inferredNatures: Set<ProjectNature>,
        annotations: Map<String, String>
    ): Set<ProjectNature> {
        // Check for manual nature override
        val manualNatures = annotations[BackstageAnnotations.FRONTSEAT_NATURES]
        
        return if (manualNatures != null) {
            logger.info("Applying manual nature override: $manualNatures")
            val overriddenNatures = mutableSetOf<ProjectNature>()
            
            manualNatures.split(",").forEach { natureId ->
                val trimmedId = natureId.trim()
                val nature = natureRegistry.getNature(trimmedId)
                if (nature != null) {
                    overriddenNatures.add(nature)
                } else {
                    logger.warn("Unknown project nature in override: $trimmedId")
                }
            }
            
            if (overriddenNatures.isEmpty()) {
                // If override parsing failed, return inferred natures
                inferredNatures
            } else {
                // Resolve dependencies and conflicts for overridden natures
                natureRegistry.resolveNatures(overriddenNatures)
            }
        } else {
            // No override, use inferred natures
            inferredNatures
        }
    }
}