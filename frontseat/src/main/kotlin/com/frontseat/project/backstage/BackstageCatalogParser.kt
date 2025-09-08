package com.frontseat.project.backstage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Parser for Backstage catalog-info.yaml files
 */
class BackstageCatalogParser {
    private val logger = LoggerFactory.getLogger(BackstageCatalogParser::class.java)
    
    private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        registerModule(KotlinModule.Builder().build())
    }
    
    /**
     * Parse a catalog-info.yaml file
     */
    fun parseCatalogFile(path: Path): CatalogEntity? {
        if (!path.exists() || !path.isRegularFile()) {
            logger.debug("Catalog file does not exist or is not a regular file: $path")
            return null
        }
        
        return try {
            val content = Files.readString(path)
            parseCatalogContent(content)
        } catch (e: Exception) {
            logger.error("Failed to parse catalog file at $path: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse catalog content from a string
     */
    fun parseCatalogContent(content: String): CatalogEntity? {
        return try {
            // First parse as a generic map to determine the kind
            val rawData: Map<String, Any> = yamlMapper.readValue(content)
            val kind = rawData["kind"] as? String
                ?: throw IllegalArgumentException("Missing 'kind' field in catalog entity")
            
            // Parse based on the kind
            when (kind) {
                "Component" -> yamlMapper.readValue<Component>(content)
                "System" -> yamlMapper.readValue<System>(content)
                "Domain" -> yamlMapper.readValue<Domain>(content)
                "API" -> yamlMapper.readValue<Api>(content)
                "Resource" -> yamlMapper.readValue<Resource>(content)
                "Location" -> yamlMapper.readValue<Location>(content)
                "User" -> yamlMapper.readValue<User>(content)
                "Group" -> yamlMapper.readValue<Group>(content)
                else -> {
                    logger.warn("Unknown catalog entity kind: $kind")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse catalog content: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse multiple catalog entities from a single file (separated by ---)
     */
    fun parseMultiDocumentCatalog(path: Path): List<CatalogEntity> {
        if (!path.exists() || !path.isRegularFile()) {
            return emptyList()
        }
        
        return try {
            val content = Files.readString(path)
            parseMultiDocumentContent(content)
        } catch (e: Exception) {
            logger.error("Failed to parse multi-document catalog file at $path: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Parse multiple catalog entities from content
     */
    fun parseMultiDocumentContent(content: String): List<CatalogEntity> {
        val documents = content.split("---").filter { it.isNotBlank() }
        return documents.mapNotNull { doc ->
            parseCatalogContent(doc.trim())
        }
    }
    
}