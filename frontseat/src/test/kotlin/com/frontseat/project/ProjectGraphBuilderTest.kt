package com.frontseat.project

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.nio.file.Paths

class ProjectGraphBuilderTest {
    
    private lateinit var testWorkspaceRoot: Path
    private lateinit var projectGraphBuilder: ProjectGraphBuilder
    
    @BeforeEach
    fun setup() {
        // Get the test workspace from resources
        val resourcesPath = this::class.java.classLoader.getResource("test-workspace")?.toURI()
        assertNotNull(resourcesPath, "Test workspace not found in resources")
        testWorkspaceRoot = Paths.get(resourcesPath!!)
        projectGraphBuilder = ProjectGraphBuilder(testWorkspaceRoot)
    }
    
    @Test
    fun `should discover all projects from project json files`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        // Verify we found all 4 projects
        assertEquals(4, projectGraph.nodes.size, "Should discover 4 projects")
        
        val projectNames = projectGraph.nodes.keys
        assertTrue(projectNames.contains("ui"), "Should contain ui project")
        assertTrue(projectNames.contains("utils"), "Should contain utils project") 
        assertTrue(projectNames.contains("web"), "Should contain web project")
        assertTrue(projectNames.contains("api"), "Should contain api project")
        
        println("=== Discovered Projects ===")
        projectGraph.getAllProjects().forEach { project ->
            println("Project: ${project.name}")
            println("  Type: ${project.type}")
            println("  Root: ${project.data.root}")
            println("  Source Root: ${project.data.sourceRoot}")
            println("  Tags: ${project.data.tags.joinToString(", ")}")
            println("  Targets: ${project.data.getTargetNames().joinToString(", ")}")
            println()
        }
    }
    
    @Test
    fun `should correctly parse project configurations`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        // Test UI library project
        val uiProject = projectGraph.getProject("ui")
        assertNotNull(uiProject, "UI project should exist")
        assertEquals("library", uiProject!!.data.projectType)
        assertEquals("libs/ui", uiProject.data.root)
        assertEquals("libs/ui/src", uiProject.data.sourceRoot)
        assertTrue(uiProject.data.tags.contains("scope:shared"))
        assertTrue(uiProject.data.tags.contains("type:ui"))
        assertTrue(uiProject.data.hasTarget("build"))
        assertTrue(uiProject.data.hasTarget("test"))
        assertTrue(uiProject.data.hasTarget("lint"))
        
        // Test Web application project
        val webProject = projectGraph.getProject("web")
        assertNotNull(webProject, "Web project should exist")
        assertEquals("application", webProject!!.data.projectType)
        assertEquals("apps/web", webProject.data.root)
        assertTrue(webProject.data.tags.contains("scope:web"))
        assertTrue(webProject.data.tags.contains("type:app"))
        assertTrue(webProject.data.hasTarget("build"))
        assertTrue(webProject.data.hasTarget("serve"))
        assertTrue(webProject.data.hasTarget("e2e"))
        
        // Test target configurations
        val buildTarget = webProject.data.getTarget("build")
        assertNotNull(buildTarget, "Web project should have build target")
        assertEquals("webpack", buildTarget!!.executor)
        assertTrue(buildTarget.hasConfiguration("production"))
        assertTrue(buildTarget.hasConfiguration("development"))
    }
    
    @Test
    fun `should build correct dependency graph`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        println("=== Project Dependencies ===")
        projectGraph.getAllProjects().forEach { project ->
            val deps = projectGraph.getDependencies(project.name)
            println("${project.name} depends on:")
            if (deps.isEmpty()) {
                println("  (no dependencies)")
            } else {
                deps.forEach { dep ->
                    println("  - ${dep.target} (${dep.type})")
                }
            }
            println()
        }
        
        // Verify all projects have no implicit dependencies (feature removed)
        val webDeps = projectGraph.getDependencies("web")
        assertTrue(webDeps.isEmpty(), "Web should have no dependencies")
        
        val apiDeps = projectGraph.getDependencies("api") 
        assertTrue(apiDeps.isEmpty(), "API should have no dependencies")
        
        val uiDeps = projectGraph.getDependencies("ui")
        assertTrue(uiDeps.isEmpty(), "UI library should have no dependencies")
        
        val utilsDeps = projectGraph.getDependencies("utils")
        assertTrue(utilsDeps.isEmpty(), "Utils library should have no dependencies")
    }
    
    @Test
    fun `should apply workspace target defaults`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        // All projects should have build targets with workspace defaults applied
        projectGraph.getAllProjects().forEach { project ->
            if (project.data.hasTarget("build")) {
                val buildTarget = project.data.getTarget("build")!!
                
                // Should have default dependsOn from workspace config
                assertTrue(
                    buildTarget.dependsOn.contains("^build"),
                    "${project.name} build target should depend on ^build from workspace defaults"
                )
                
                // Should have cache enabled by default
                assertTrue(
                    buildTarget.cache,
                    "${project.name} build target should have cache enabled"
                )
                
                println("${project.name}:build target:")
                println("  Executor: ${buildTarget.executor}")
                println("  DependsOn: ${buildTarget.dependsOn.joinToString(", ")}")
                println("  Inputs: ${buildTarget.inputs.joinToString(", ")}")
                println("  Outputs: ${buildTarget.outputs.joinToString(", ")}")
                println("  Cache: ${buildTarget.cache}")
                println()
            }
        }
    }
    
    @Test
    fun `should filter projects by type`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        val libraries = projectGraph.getProjectsByType("library")
        assertEquals(2, libraries.size, "Should find 2 libraries")
        assertTrue(libraries.any { it.name == "ui" })
        assertTrue(libraries.any { it.name == "utils" })
        
        val applications = projectGraph.getProjectsByType("application")
        assertEquals(2, applications.size, "Should find 2 applications")
        assertTrue(applications.any { it.name == "web" })
        assertTrue(applications.any { it.name == "api" })
        
        println("=== Libraries ===")
        libraries.forEach { project ->
            println("- ${project.name}: ${project.data.tags.joinToString(", ")}")
        }
        
        println("\n=== Applications ===")
        applications.forEach { project ->
            println("- ${project.name}: ${project.data.tags.joinToString(", ")}")
        }
    }
    
    @Test
    fun `should filter projects by tag`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        val sharedProjects = projectGraph.getProjectsByTag("scope:shared")
        assertEquals(2, sharedProjects.size, "Should find 2 shared projects")
        assertTrue(sharedProjects.any { it.name == "ui" })
        assertTrue(sharedProjects.any { it.name == "utils" })
        
        val uiProjects = projectGraph.getProjectsByTag("type:ui") 
        assertEquals(1, uiProjects.size, "Should find 1 UI project")
        assertEquals("ui", uiProjects.first().name)
        
        val appProjects = projectGraph.getProjectsByTag("type:app")
        assertEquals(2, appProjects.size, "Should find 2 app projects")
        
        println("=== Projects by Tag ===")
        println("scope:shared: ${sharedProjects.map { it.name }.joinToString(", ")}")
        println("type:ui: ${uiProjects.map { it.name }.joinToString(", ")}")
        println("type:app: ${appProjects.map { it.name }.joinToString(", ")}")
    }
    
    @Test
    fun `should calculate transitive dependencies`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        // All projects should have no transitive dependencies (implicit dependencies removed)
        val webTransitiveDeps = projectGraph.getTransitiveDependencies("web")
        assertTrue(webTransitiveDeps.isEmpty(), "Web should have no transitive dependencies")
        
        val apiTransitiveDeps = projectGraph.getTransitiveDependencies("api")
        assertTrue(apiTransitiveDeps.isEmpty(), "API should have no transitive dependencies")
        
        val uiTransitiveDeps = projectGraph.getTransitiveDependencies("ui")
        assertTrue(uiTransitiveDeps.isEmpty(), "UI should have no transitive dependencies")
        
        println("=== Transitive Dependencies ===")
        println("web: ${webTransitiveDeps.joinToString(", ")}")
        println("api: ${apiTransitiveDeps.joinToString(", ")}")
        println("ui: ${uiTransitiveDeps.joinToString(", ")}")
    }
    
    @Test
    fun `should calculate transitive dependents`() {
        val projectGraph = projectGraphBuilder.buildProjectGraph()
        
        // All projects should have no dependents (implicit dependencies removed)
        val utilsDependents = projectGraph.getTransitiveDependents("utils")
        assertTrue(utilsDependents.isEmpty(), "Utils should have no dependents")
        
        val uiDependents = projectGraph.getTransitiveDependents("ui") 
        assertTrue(uiDependents.isEmpty(), "UI should have no dependents")
        
        val webDependents = projectGraph.getTransitiveDependents("web")
        assertTrue(webDependents.isEmpty(), "Web should have no dependents")
        
        println("=== Transitive Dependents ===")
        println("utils: ${utilsDependents.joinToString(", ")}")
        println("ui: ${uiDependents.joinToString(", ")}")
        println("web: ${webDependents.joinToString(", ")}")
    }
}