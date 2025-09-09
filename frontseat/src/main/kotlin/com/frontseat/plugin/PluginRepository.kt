package com.frontseat.plugin

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Repository for downloading and caching plugins
 */
class PluginRepository(
    private val cacheDir: Path = getDefaultCacheDir()
) {
    private val logger = LoggerFactory.getLogger(PluginRepository::class.java)
    private val httpClient = HttpClient.newBuilder().build()
    
    companion object {
        private fun getDefaultCacheDir(): Path {
            val userHome = System.getProperty("user.home")
            return Paths.get(userHome, ".forge", "plugins")
        }
    }
    
    init {
        // Ensure cache directory exists
        Files.createDirectories(cacheDir)
    }
    
    /**
     * Resolve a plugin specification to a local JAR path
     */
    fun resolve(spec: PluginSpec): Path {
        logger.debug("Resolving plugin: ${spec.toCoordinateString()}")
        
        val cachedPath = getCachedPath(spec)
        if (cachedPath.exists()) {
            logger.debug("Found cached plugin: $cachedPath")
            return cachedPath
        }
        
        return downloadPlugin(spec)
    }
    
    /**
     * Check if plugin is cached locally
     */
    fun isCached(spec: PluginSpec): Boolean {
        return getCachedPath(spec).exists()
    }
    
    /**
     * Clear plugin cache
     */
    fun clearCache() {
        logger.info("Clearing plugin cache: $cacheDir")
        cacheDir.toFile().deleteRecursively()
        Files.createDirectories(cacheDir)
    }
    
    /**
     * List cached plugins
     */
    fun listCached(): List<CachedPlugin> {
        return cacheDir.toFile()
            .listFiles { file -> file.extension == "jar" }
            ?.mapNotNull { file -> 
                parseCachedPlugin(file.toPath())
            } ?: emptyList()
    }
    
    private fun getCachedPath(spec: PluginSpec): Path {
        val fileName = when (spec.source) {
            PluginSource.MAVEN -> "${spec.id}-${spec.version}.jar"
            PluginSource.NPM -> "${spec.id}-${spec.version}-npm.jar"  
            PluginSource.GITHUB -> "${spec.id}-${spec.version}-github.jar"
            PluginSource.FILE -> spec.location.substringAfterLast("/")
        }
        return cacheDir.resolve(fileName)
    }
    
    private fun downloadPlugin(spec: PluginSpec): Path {
        logger.info("Downloading plugin: ${spec.toCoordinateString()}")
        
        val targetPath = getCachedPath(spec)
        
        when (spec.source) {
            PluginSource.MAVEN -> downloadFromMaven(spec, targetPath)
            PluginSource.NPM -> downloadFromNpm(spec, targetPath)
            PluginSource.GITHUB -> downloadFromGitHub(spec, targetPath)
            PluginSource.FILE -> copyFromFile(spec, targetPath)
        }
        
        logger.info("Plugin downloaded: $targetPath")
        return targetPath
    }
    
    private fun downloadFromMaven(spec: PluginSpec, targetPath: Path) {
        // Convert plugin ID to Maven coordinates
        // e.g., "com.frontseat.js" -> groupId=com.frontseat, artifactId=forge-js-plugin
        val parts = spec.id.split(".")
        val groupId = parts.take(parts.size - 1).joinToString(".")
        val artifactId = "forge-${parts.last()}-plugin"
        
        val version = if (spec.version == "latest") {
            resolveLatestVersion(groupId, artifactId)
        } else {
            spec.version
        }
        
        // First try local Maven repository
        val localMavenPath = getLocalMavenPath(groupId, artifactId, version)
        if (localMavenPath.exists()) {
            logger.debug("Using local Maven repository: $localMavenPath")
            Files.copy(localMavenPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        
        // Fall back to Maven Central
        val url = buildMavenUrl(groupId, artifactId, version)
        downloadFromUrl(url, targetPath)
    }
    
    private fun downloadFromNpm(spec: PluginSpec, targetPath: Path) {
        // NPM plugins would need to be transpiled to JVM
        // For now, throw unsupported
        throw UnsupportedOperationException("NPM plugins not yet supported")
    }
    
    private fun downloadFromGitHub(spec: PluginSpec, targetPath: Path) {
        // Download from GitHub releases
        val url = "https://github.com/${spec.location}/releases/latest/download/plugin.jar"
        downloadFromUrl(url, targetPath)
    }
    
    private fun copyFromFile(spec: PluginSpec, targetPath: Path) {
        val sourcePath = Paths.get(spec.location)
        if (!sourcePath.exists()) {
            throw PluginNotFoundException("Plugin file not found: ${spec.location}")
        }
        
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
    }
    
    private fun downloadFromUrl(url: String, targetPath: Path) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build()
                
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetPath))
            
            if (response.statusCode() != 200) {
                throw PluginDownloadException("Failed to download plugin: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            throw PluginDownloadException("Failed to download plugin from $url", e)
        }
    }
    
    private fun buildMavenUrl(groupId: String, artifactId: String, version: String): String {
        val groupPath = groupId.replace(".", "/")
        return "https://repo1.maven.org/maven2/$groupPath/$artifactId/$version/$artifactId-$version.jar"
    }
    
    private fun resolveLatestVersion(groupId: String, artifactId: String): String {
        // For now, return a default version
        // In a real implementation, query Maven metadata
        return "1.0.0-SNAPSHOT"
    }
    
    private fun getLocalMavenPath(groupId: String, artifactId: String, version: String): Path {
        val userHome = System.getProperty("user.home")
        val localRepo = Paths.get(userHome, ".m2", "repository")
        val groupPath = groupId.replace(".", "/")
        // Use the regular JAR, not the jar-with-dependencies
        return localRepo.resolve("$groupPath/$artifactId/$version/$artifactId-$version.jar")
    }
    
    private fun parseCachedPlugin(path: Path): CachedPlugin? {
        val fileName = path.name
        // Parse filename to extract plugin info
        val parts = fileName.removeSuffix(".jar").split("-")
        
        return if (parts.size >= 2) {
            val id = parts.dropLast(1).joinToString(".")
            val version = parts.last()
            CachedPlugin(id, version, path)
        } else {
            null
        }
    }
}

/**
 * Information about a cached plugin
 */
data class CachedPlugin(
    val id: String,
    val version: String, 
    val path: Path
)

/**
 * Exception thrown when plugin cannot be found
 */
class PluginNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when plugin download fails
 */
class PluginDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)