package com.frontseat.backstage

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Service for inferring project natures based on files present in the project directory
 */
class NatureInferenceService {
    private val logger = LoggerFactory.getLogger(NatureInferenceService::class.java)
    
    /**
     * Infer project natures from files in the project directory
     */
    fun inferNatures(projectPath: Path, componentType: String? = null): Set<ProjectNature> {
        val natures = mutableSetOf<ProjectNature>()
        
        // Check for build tool files
        when {
            (projectPath / "pom.xml").exists() -> {
                natures.add(ProjectNature.MAVEN)
                logger.debug("Detected Maven nature from pom.xml")
            }
            (projectPath / "build.gradle").exists() || (projectPath / "build.gradle.kts").exists() -> {
                natures.add(ProjectNature.GRADLE)
                logger.debug("Detected Gradle nature from build.gradle")
            }
            (projectPath / "package.json").exists() -> {
                // Determine which JS package manager
                when {
                    (projectPath / "yarn.lock").exists() -> {
                        natures.add(ProjectNature.YARN)
                        logger.debug("Detected Yarn nature from yarn.lock")
                    }
                    (projectPath / "pnpm-lock.yaml").exists() -> {
                        natures.add(ProjectNature.PNPM)
                        logger.debug("Detected PNPM nature from pnpm-lock.yaml")
                    }
                    else -> {
                        natures.add(ProjectNature.NPM)
                        logger.debug("Detected NPM nature from package.json")
                    }
                }
                
                // Check for specific JS frameworks
                inferJavaScriptFrameworks(projectPath, natures)
            }
            (projectPath / "go.mod").exists() -> {
                natures.add(ProjectNature.GO)
                logger.debug("Detected Go nature from go.mod")
            }
            (projectPath / "Cargo.toml").exists() -> {
                natures.add(ProjectNature.RUST)
                logger.debug("Detected Rust nature from Cargo.toml")
            }
            (projectPath / "requirements.txt").exists() || 
            (projectPath / "setup.py").exists() || 
            (projectPath / "pyproject.toml").exists() -> {
                natures.add(ProjectNature.PYTHON)
                logger.debug("Detected Python nature from Python files")
            }
            (projectPath / "*.csproj").exists() || 
            (projectPath / "*.fsproj").exists() || 
            (projectPath / "*.vbproj").exists() -> {
                natures.add(ProjectNature.DOTNET)
                logger.debug("Detected .NET nature from project files")
            }
        }
        
        // Check for Docker
        if ((projectPath / "Dockerfile").exists() || (projectPath / "docker-compose.yml").exists()) {
            natures.add(ProjectNature.DOCKER)
            logger.debug("Detected Docker nature from Dockerfile")
        }
        
        // Check for Kubernetes
        if (hasKubernetesFiles(projectPath)) {
            natures.add(ProjectNature.KUBERNETES)
            logger.debug("Detected Kubernetes nature from K8s manifests")
        }
        
        // Check for Terraform
        if (hasTerraformFiles(projectPath)) {
            natures.add(ProjectNature.TERRAFORM)
            logger.debug("Detected Terraform nature from .tf files")
        }
        
        // For Java/Kotlin projects, check for Spring Boot
        if (natures.contains(ProjectNature.MAVEN) || natures.contains(ProjectNature.GRADLE)) {
            if (componentType == "service" && hasSpringBootApplication(projectPath)) {
                natures.add(ProjectNature.SPRING_BOOT)
                logger.debug("Detected Spring Boot nature from @SpringBootApplication")
            }
        }
        
        // If no natures detected, mark as CUSTOM
        if (natures.isEmpty()) {
            natures.add(ProjectNature.CUSTOM)
            logger.debug("No specific nature detected, marking as CUSTOM")
        }
        
        return natures
    }
    
    /**
     * Check for JavaScript framework files
     */
    private fun inferJavaScriptFrameworks(projectPath: Path, natures: MutableSet<ProjectNature>) {
        // Check package.json for dependencies
        val packageJsonPath = projectPath / "package.json"
        if (packageJsonPath.exists()) {
            try {
                val packageContent = Files.readString(packageJsonPath)
                when {
                    packageContent.contains("\"react\"") || 
                    packageContent.contains("\"react-dom\"") -> {
                        natures.add(ProjectNature.REACT)
                        logger.debug("Detected React from package.json")
                    }
                    packageContent.contains("\"@angular/core\"") -> {
                        natures.add(ProjectNature.ANGULAR)
                        logger.debug("Detected Angular from package.json")
                    }
                    packageContent.contains("\"vue\"") -> {
                        natures.add(ProjectNature.VUE)
                        logger.debug("Detected Vue from package.json")
                    }
                    packageContent.contains("\"next\"") -> {
                        natures.add(ProjectNature.NEXTJS)
                        logger.debug("Detected Next.js from package.json")
                    }
                }
                
                // If no specific framework but has package.json, it's Node.js
                if (natures.none { it in setOf(ProjectNature.REACT, ProjectNature.ANGULAR, ProjectNature.VUE, ProjectNature.NEXTJS) }) {
                    natures.add(ProjectNature.NODEJS)
                    logger.debug("Detected Node.js from package.json")
                }
            } catch (e: Exception) {
                logger.warn("Failed to parse package.json: ${e.message}")
            }
        }
        
        // Check for framework-specific config files
        when {
            (projectPath / "angular.json").exists() -> {
                natures.add(ProjectNature.ANGULAR)
                logger.debug("Detected Angular from angular.json")
            }
            (projectPath / "next.config.js").exists() || 
            (projectPath / "next.config.mjs").exists() -> {
                natures.add(ProjectNature.NEXTJS)
                logger.debug("Detected Next.js from config file")
            }
            (projectPath / "vue.config.js").exists() -> {
                natures.add(ProjectNature.VUE)
                logger.debug("Detected Vue from vue.config.js")
            }
        }
    }
    
    /**
     * Check if project has Spring Boot application
     */
    private fun hasSpringBootApplication(projectPath: Path): Boolean {
        // Look for @SpringBootApplication annotation in Java/Kotlin files
        val sourceRoots = listOf(
            projectPath / "src" / "main" / "java",
            projectPath / "src" / "main" / "kotlin"
        )
        
        for (sourceRoot in sourceRoots) {
            if (!sourceRoot.exists()) continue
            
            try {
                val hasSpringBoot = Files.walk(sourceRoot)
                    .filter { it.extension in setOf("java", "kt") }
                    .anyMatch { file ->
                        try {
                            val content = Files.readString(file)
                            content.contains("@SpringBootApplication") ||
                            content.contains("@EnableAutoConfiguration") ||
                            content.contains("extends SpringBootServletInitializer")
                        } catch (e: Exception) {
                            false
                        }
                    }
                
                if (hasSpringBoot) {
                    return true
                }
            } catch (e: Exception) {
                logger.warn("Error checking for Spring Boot: ${e.message}")
            }
        }
        
        // Alternative: Check for Spring Boot in pom.xml or build.gradle
        val pomFile = projectPath / "pom.xml"
        if (pomFile.exists()) {
            try {
                val content = Files.readString(pomFile)
                if (content.contains("spring-boot-starter") || 
                    content.contains("org.springframework.boot")) {
                    return true
                }
            } catch (e: Exception) {
                logger.warn("Error reading pom.xml: ${e.message}")
            }
        }
        
        val gradleFile = projectPath / "build.gradle"
        val gradleKtsFile = projectPath / "build.gradle.kts"
        for (file in listOf(gradleFile, gradleKtsFile)) {
            if (file.exists()) {
                try {
                    val content = Files.readString(file)
                    if (content.contains("org.springframework.boot") ||
                        content.contains("spring-boot-starter")) {
                        return true
                    }
                } catch (e: Exception) {
                    logger.warn("Error reading gradle file: ${e.message}")
                }
            }
        }
        
        return false
    }
    
    /**
     * Check if project has Kubernetes manifest files
     */
    private fun hasKubernetesFiles(projectPath: Path): Boolean {
        val k8sPatterns = listOf(
            "deployment.yaml", "deployment.yml",
            "service.yaml", "service.yml",
            "configmap.yaml", "configmap.yml",
            "secret.yaml", "secret.yml",
            "ingress.yaml", "ingress.yml",
            "statefulset.yaml", "statefulset.yml",
            "daemonset.yaml", "daemonset.yml"
        )
        
        // Check root and common K8s directories
        val k8sDirs = listOf(
            projectPath,
            projectPath / "k8s",
            projectPath / "kubernetes",
            projectPath / "manifests",
            projectPath / "deploy"
        )
        
        for (dir in k8sDirs) {
            if (!dir.exists()) continue
            
            for (pattern in k8sPatterns) {
                if ((dir / pattern).exists()) {
                    return true
                }
            }
            
            // Check for any YAML with K8s content
            try {
                val hasK8sContent = Files.list(dir)
                    .filter { it.extension in setOf("yaml", "yml") }
                    .anyMatch { file ->
                        try {
                            val content = Files.readString(file)
                            content.contains("apiVersion:") && 
                            content.contains("kind:") &&
                            (content.contains("kind: Deployment") ||
                             content.contains("kind: Service") ||
                             content.contains("kind: ConfigMap") ||
                             content.contains("kind: Secret") ||
                             content.contains("kind: Ingress"))
                        } catch (e: Exception) {
                            false
                        }
                    }
                
                if (hasK8sContent) return true
            } catch (e: Exception) {
                logger.debug("Error checking for K8s files: ${e.message}")
            }
        }
        
        return false
    }
    
    /**
     * Check if project has Terraform files
     */
    private fun hasTerraformFiles(projectPath: Path): Boolean {
        try {
            return Files.walk(projectPath, 2) // Limit depth for performance
                .anyMatch { it.extension == "tf" || it.fileName.toString() == "terraform.tfvars" }
        } catch (e: Exception) {
            logger.debug("Error checking for Terraform files: ${e.message}")
            return false
        }
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
            
            manualNatures.split(",").forEach { nature ->
                try {
                    overriddenNatures.add(ProjectNature.valueOf(nature.trim().uppercase()))
                } catch (e: IllegalArgumentException) {
                    logger.warn("Unknown project nature in override: ${nature.trim()}")
                }
            }
            
            if (overriddenNatures.isEmpty()) {
                // If override parsing failed, return inferred natures
                inferredNatures
            } else {
                overriddenNatures
            }
        } else {
            // No override, use inferred natures
            inferredNatures
        }
    }
}