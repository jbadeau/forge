package com.forge.integration

import com.forge.discovery.ProjectDiscovery
import com.forge.inference.InferenceEngine
import com.forge.inference.plugins.JavaScriptPlugin
import com.forge.inference.plugins.MavenPlugin
import com.forge.inference.plugins.DockerPlugin
import com.forge.inference.plugins.GoPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealWorkspaceTest {
    
    private val demoWorkspace = Path.of("demo-workspace")
    
    private fun createInferenceEngine(): InferenceEngine {
        return InferenceEngine().apply {
            registerPlugin(JavaScriptPlugin())
            registerPlugin(MavenPlugin())
            registerPlugin(DockerPlugin())
            registerPlugin(GoPlugin())
        }
    }
    
    @Test
    fun `should discover all projects in real demo workspace`() {
        // Given a real demo workspace with multiple project types
        val inferenceEngine = createInferenceEngine()
        val discovery = ProjectDiscovery(demoWorkspace, enableInference = true, inferenceEngine = inferenceEngine)
        
        // When discovering projects
        val projectGraph = discovery.discoverProjects()
        
        // Then should discover all expected projects
        val projectNames = projectGraph.nodes.keys
        assertAll(
            { assertTrue(projectNames.contains("web-app"), "Should discover web-app") },
            { assertTrue(projectNames.contains("mobile-app"), "Should discover mobile-app") },
            { assertTrue(projectNames.contains("@my-org/ui-components"), "Should discover ui-components") },
            { assertTrue(projectNames.contains("@my-org/utils"), "Should discover utils") },
            { assertTrue(projectNames.contains("auth-service"), "Should discover auth-service") },
            { assertTrue(projectNames.contains("user-service"), "Should discover user-service") },
            { assertTrue(projectNames.contains("shared-lib"), "Should discover shared-lib") },
            { assertTrue(projectNames.contains("api-gateway"), "Should discover api-gateway") },
            { assertTrue(projectNames.contains("go-utils"), "Should discover go-utils") },
            { assertTrue(projectNames.contains("@my-org/deployment-cli"), "Should discover deployment-cli") }
        )
        
        // Should discover at least 10 projects
        assertTrue(projectGraph.nodes.size >= 10, "Should discover at least 10 projects, found ${projectGraph.nodes.size}")
    }
    
    @Test
    fun `should infer correct project types for different technologies`() {
        val inferenceEngine = createInferenceEngine()
        val discovery = ProjectDiscovery(demoWorkspace, enableInference = true, inferenceEngine = inferenceEngine)
        val projectGraph = discovery.discoverProjects()
        
        // Web applications might be detected as libraries if they don't have a main entry point
        val webApp = projectGraph.nodes["web-app"]
        assertNotNull(webApp)
        // Note: web-app is inferred as library because it doesn't have a bin field in package.json
        assertEquals("library", webApp.data.projectType)
        
        val mobileApp = projectGraph.nodes["mobile-app"] 
        assertNotNull(mobileApp)
        // Note: mobile-app is also inferred as library because it doesn't have a bin field
        assertEquals("library", mobileApp.data.projectType)
        
        // Libraries should be detected as libraries
        val uiComponents = projectGraph.nodes["@my-org/ui-components"]
        assertNotNull(uiComponents)
        assertEquals("library", uiComponents.data.projectType)
        
        val utils = projectGraph.nodes["@my-org/utils"]
        assertNotNull(utils)
        assertEquals("library", utils.data.projectType)
        
        val sharedLib = projectGraph.nodes["shared-lib"]
        assertNotNull(sharedLib)
        assertEquals("library", sharedLib.data.projectType)
        
        // CLI tools should be detected as applications
        val deploymentCli = projectGraph.nodes["@my-org/deployment-cli"]
        assertNotNull(deploymentCli)
        assertEquals("application", deploymentCli.data.projectType)
    }
    
    @Test
    fun `should infer correct dependencies between projects`() {
        val inferenceEngine = createInferenceEngine()
        val discovery = ProjectDiscovery(demoWorkspace, enableInference = true, inferenceEngine = inferenceEngine)
        val projectGraph = discovery.discoverProjects()
        
        // JavaScript dependencies
        val webAppDeps = projectGraph.dependencies["web-app"] ?: emptyList()
        val webAppDepTargets = webAppDeps.map { it.target }
        assertAll(
            { assertTrue("@my-org/ui-components" in webAppDepTargets, "web-app should depend on ui-components") },
            { assertTrue("@my-org/utils" in webAppDepTargets, "web-app should depend on utils") }
        )
        
        val uiComponentsDeps = projectGraph.dependencies["@my-org/ui-components"] ?: emptyList()
        val uiComponentsDepTargets = uiComponentsDeps.map { it.target }
        assertTrue("@my-org/utils" in uiComponentsDepTargets, "ui-components should depend on utils")
        
        // Maven dependencies
        val authServiceDeps = projectGraph.dependencies["auth-service"] ?: emptyList()
        val authServiceDepTargets = authServiceDeps.map { it.target }
        assertTrue("shared-lib" in authServiceDepTargets, "auth-service should depend on shared-lib")
        
        val userServiceDeps = projectGraph.dependencies["user-service"] ?: emptyList()
        val userServiceDepTargets = userServiceDeps.map { it.target }
        assertAll(
            { assertTrue("shared-lib" in userServiceDepTargets, "user-service should depend on shared-lib") },
            { assertTrue("auth-service" in userServiceDepTargets, "user-service should depend on auth-service") }
        )
        
        // Go dependencies
        val apiGatewayDeps = projectGraph.dependencies["api-gateway"] ?: emptyList()
        val apiGatewayDepTargets = apiGatewayDeps.map { it.target }
        assertTrue("go-utils" in apiGatewayDepTargets, "api-gateway should depend on go-utils")
    }
    
    @Test
    fun `should merge targets from multiple plugins correctly`() {
        val inferenceEngine = createInferenceEngine()
        val discovery = ProjectDiscovery(demoWorkspace, enableInference = true, inferenceEngine = inferenceEngine)
        val projectGraph = discovery.discoverProjects()
        
        // Projects with Docker should have both language-specific and Docker targets
        val authService = projectGraph.nodes["auth-service"]
        assertNotNull(authService)
        val authServiceTargets = authService.data.targets.keys
        assertAll(
            { assertTrue("build" in authServiceTargets, "Should have Maven build target") },
            { assertTrue("test" in authServiceTargets, "Should have Maven test target") },
            { assertTrue("docker-build" in authServiceTargets, "Should have Docker build target") },
            { assertTrue("docker-run" in authServiceTargets, "Should have Docker run target") },
            { assertTrue("docker-push" in authServiceTargets, "Should have Docker push target") }
        )
        
        val webApp = projectGraph.nodes["web-app"]
        assertNotNull(webApp)
        val webAppTargets = webApp.data.targets.keys
        assertAll(
            { assertTrue("build" in webAppTargets, "Should have JavaScript build target") },
            { assertTrue("test" in webAppTargets, "Should have JavaScript test target") },
            { assertTrue("docker-build" in webAppTargets, "Should have Docker build target") }
        )
        
        val apiGateway = projectGraph.nodes["api-gateway"]
        assertNotNull(apiGateway)
        val apiGatewayTargets = apiGateway.data.targets.keys
        assertAll(
            { assertTrue("build" in apiGatewayTargets, "Should have Go build target") },
            { assertTrue("test" in apiGatewayTargets, "Should have Go test target") },
            { assertTrue("docker-build" in apiGatewayTargets, "Should have Docker build target") }
        )
    }
    
    @Test
    fun `should extract tags from package json keywords`() {
        val inferenceEngine = createInferenceEngine()
        val discovery = ProjectDiscovery(demoWorkspace, enableInference = true, inferenceEngine = inferenceEngine)
        val projectGraph = discovery.discoverProjects()
        
        val webApp = projectGraph.nodes["web-app"]
        assertNotNull(webApp)
        val webAppTags = webApp.data.tags
        assertAll(
            { assertTrue("react" in webAppTags, "Should have react tag from package.json keywords") },
            { assertTrue("web" in webAppTags, "Should have web tag from package.json keywords") },
            { assertTrue("frontend" in webAppTags, "Should have frontend tag from package.json keywords") }
        )
        
        val deploymentCli = projectGraph.nodes["@my-org/deployment-cli"]
        assertNotNull(deploymentCli)
        val deploymentCliTags = deploymentCli.data.tags
        assertAll(
            { assertTrue("cli" in deploymentCliTags, "Should have cli tag from package.json keywords") },
            { assertTrue("deployment" in deploymentCliTags, "Should have deployment tag from package.json keywords") },
            { assertTrue("devops" in deploymentCliTags, "Should have devops tag from package.json keywords") }
        )
    }
}