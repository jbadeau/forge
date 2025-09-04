package com.forge.docker.devkit

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utility functions for Docker operations, similar to Nx's Docker utilities
 */
object DockerUtils {
    
    private val logger = LoggerFactory.getLogger(DockerUtils::class.java)
    
    /**
     * Generate Docker image reference from project root path
     * Similar to Nx's imageRef calculation in plugin.ts
     */
    fun generateImageRef(projectRoot: String): String {
        return projectRoot.removePrefix("/")
            .replace(Regex("[/\\\\\\s]+"), "-")
            .lowercase()
    }
    
    /**
     * Check if a directory contains Docker files
     */
    fun hasDockerSupport(projectPath: Path): Boolean {
        return projectPath.resolve("Dockerfile").exists() ||
               projectPath.resolve("dockerfile").exists()
    }
    
    /**
     * Parse Dockerfile to extract metadata
     */
    fun parseDockerfile(dockerfilePath: Path): DockerfileInfo {
        if (!dockerfilePath.exists()) {
            return DockerfileInfo()
        }
        
        val content = dockerfilePath.readText()
        val lines = content.lines()
        
        var baseImage = ""
        val ports = mutableListOf<String>()
        val volumes = mutableListOf<String>()
        val envVars = mutableMapOf<String, String>()
        var workDir = ""
        var isMultiStage = false
        
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("FROM", ignoreCase = true) -> {
                    if (baseImage.isNotEmpty()) isMultiStage = true
                    baseImage = trimmed.substringAfter("FROM ").split(" ").first()
                }
                trimmed.startsWith("EXPOSE", ignoreCase = true) -> {
                    ports.addAll(trimmed.substringAfter("EXPOSE ").split(" "))
                }
                trimmed.startsWith("VOLUME", ignoreCase = true) -> {
                    volumes.add(trimmed.substringAfter("VOLUME "))
                }
                trimmed.startsWith("ENV", ignoreCase = true) -> {
                    val envPart = trimmed.substringAfter("ENV ")
                    if (envPart.contains("=")) {
                        val (key, value) = envPart.split("=", limit = 2)
                        envVars[key.trim()] = value.trim()
                    }
                }
                trimmed.startsWith("WORKDIR", ignoreCase = true) -> {
                    workDir = trimmed.substringAfter("WORKDIR ")
                }
            }
        }
        
        return DockerfileInfo(
            baseImage = baseImage,
            ports = ports,
            volumes = volumes,
            envVars = envVars,
            workDir = workDir,
            isMultiStage = isMultiStage
        )
    }
    
    /**
     * Generate Docker build command with common options
     */
    fun generateBuildCommand(
        imageRef: String,
        dockerfilePath: String = "Dockerfile",
        buildArgs: Map<String, String> = emptyMap(),
        target: String? = null,
        platform: String? = null
    ): List<String> {
        val command = mutableListOf("docker", "build", ".")
        
        command.addAll(listOf("--tag", imageRef))
        
        if (dockerfilePath != "Dockerfile") {
            command.addAll(listOf("--file", dockerfilePath))
        }
        
        buildArgs.forEach { (key, value) ->
            command.addAll(listOf("--build-arg", "$key=$value"))
        }
        
        target?.let {
            command.addAll(listOf("--target", it))
        }
        
        platform?.let {
            command.addAll(listOf("--platform", it))
        }
        
        return command
    }
    
    /**
     * Generate Docker run command with common options
     */
    fun generateRunCommand(
        imageRef: String,
        ports: Map<String, String> = emptyMap(),
        volumes: Map<String, String> = emptyMap(),
        envVars: Map<String, String> = emptyMap(),
        detached: Boolean = false,
        removeOnExit: Boolean = true
    ): List<String> {
        val command = mutableListOf("docker", "run")
        
        if (removeOnExit) command.add("--rm")
        if (detached) command.add("-d")
        
        ports.forEach { (hostPort, containerPort) ->
            command.addAll(listOf("-p", "$hostPort:$containerPort"))
        }
        
        volumes.forEach { (hostPath, containerPath) ->
            command.addAll(listOf("-v", "$hostPath:$containerPath"))
        }
        
        envVars.forEach { (key, value) ->
            command.addAll(listOf("-e", "$key=$value"))
        }
        
        command.add(imageRef)
        
        return command
    }
    
    /**
     * Detect Docker technology stack from Dockerfile content
     */
    fun detectTechnology(dockerfileInfo: DockerfileInfo): List<String> {
        val technologies = mutableListOf<String>()
        val baseImage = dockerfileInfo.baseImage.lowercase()
        
        when {
            baseImage.contains("node") -> {
                technologies.addAll(listOf("nodejs", "javascript"))
            }
            baseImage.contains("python") -> {
                technologies.add("python")
            }
            baseImage.contains("java") || baseImage.contains("openjdk") -> {
                technologies.add("java")
            }
            baseImage.contains("golang") || baseImage.contains("go:") -> {
                technologies.addAll(listOf("go", "golang"))
            }
            baseImage.contains("nginx") -> {
                technologies.addAll(listOf("nginx", "web"))
            }
            baseImage.contains("postgres") || baseImage.contains("mysql") -> {
                technologies.add("database")
            }
            baseImage.contains("redis") -> {
                technologies.addAll(listOf("redis", "cache"))
            }
        }
        
        return technologies
    }
    
    /**
     * Get default port mapping based on detected technology
     */
    fun getDefaultPorts(technologies: List<String>): Map<String, String> {
        val defaultPorts = mutableMapOf<String, String>()
        
        technologies.forEach { tech ->
            when (tech.lowercase()) {
                "nodejs", "javascript" -> defaultPorts["3000"] = "3000"
                "java" -> defaultPorts["8080"] = "8080"
                "python" -> defaultPorts["8000"] = "8000"
                "go", "golang" -> defaultPorts["8080"] = "8080"
                "nginx" -> defaultPorts["80"] = "80"
                "postgres" -> defaultPorts["5432"] = "5432"
                "mysql" -> defaultPorts["3306"] = "3306"
                "redis" -> defaultPorts["6379"] = "6379"
            }
        }
        
        return defaultPorts
    }
    
}

/**
 * Information parsed from a Dockerfile
 */
data class DockerfileInfo(
    val baseImage: String = "",
    val ports: List<String> = emptyList(),
    val volumes: List<String> = emptyList(),
    val envVars: Map<String, String> = emptyMap(),
    val workDir: String = "",
    val isMultiStage: Boolean = false
)