package com.forge.inference

import com.forge.discovery.ProjectDiscovery
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InferenceIntegrationTest {
    
    private val testWorkspace = Path.of("src/test/resources/test-inference-workspace")
    
    @Test
    fun `should discover projects from package json files using inference`() {
        // Given a workspace with package.json files
        val discovery = ProjectDiscovery(testWorkspace, enableInference = true)
        
        // When discovering projects
        val projectGraph = discovery.discoverProjects()
        
        // Then should discover all projects from package.json files
        assertAll(
            { assertTrue(projectGraph.nodes.isNotEmpty(), "Should discover projects") },
            { assertTrue(projectGraph.nodes.size >= 4, "Should discover at least 4 projects") }
        )
        
        // Verify specific projects were discovered
        val projectNames = projectGraph.nodes.keys
        assertAll(
            { assertTrue("react-app" in projectNames, "Should discover React app") },
            { assertTrue("@my-org/utils-lib" in projectNames, "Should discover utils library") },
            { assertTrue("api-server" in projectNames, "Should discover API server") },
            { assertTrue("@my-org/cli-tool" in projectNames, "Should discover CLI tool") }
        )
    }
    
    @Test
    fun `should infer correct project types from package json`() {
        val discovery = ProjectDiscovery(testWorkspace, enableInference = true)
        val projectGraph = discovery.discoverProjects()
        
        // React app should be detected as application
        val reactApp = projectGraph.nodes["react-app"]
        assertNotNull(reactApp)
        assertEquals("application", reactApp.data.projectType)
        
        // Utils lib should be detected as library
        val utilsLib = projectGraph.nodes["@my-org/utils-lib"]
        assertNotNull(utilsLib)
        assertEquals("library", utilsLib.data.projectType)
        
        // API server should be detected as application (has bin)
        val apiServer = projectGraph.nodes["api-server"]
        assertNotNull(apiServer)
        assertEquals("application", apiServer.data.projectType)
        
        // CLI tool should be detected as application (has bin)
        val cliTool = projectGraph.nodes["@my-org/cli-tool"]
        assertNotNull(cliTool)
        assertEquals("application", cliTool.data.projectType)
    }
    
    @Test
    fun `should infer targets from npm scripts`() {
        val discovery = ProjectDiscovery(testWorkspace, enableInference = true)
        val projectGraph = discovery.discoverProjects()
        
        val reactApp = projectGraph.nodes["react-app"]
        assertNotNull(reactApp)
        
        // Should have inferred targets from scripts
        val targets = reactApp.data.targets
        assertAll(
            { assertTrue("build" in targets, "Should infer build target") },
            { assertTrue("test" in targets, "Should infer test target") },
            { assertTrue("lint" in targets, "Should infer lint target") },
            { assertTrue("serve" in targets, "Should infer serve target from dev script") }
        )
        
        // Verify build target configuration
        val buildTarget = targets["build"]
        assertNotNull(buildTarget)
        assertEquals("npm run build", buildTarget.command)
        assertTrue(buildTarget.cache)
        assertTrue(buildTarget.outputs.isNotEmpty())
    }
    
    @Test
    fun `should extract tags from package json`() {
        val discovery = ProjectDiscovery(testWorkspace, enableInference = true)
        val projectGraph = discovery.discoverProjects()
        
        val reactApp = projectGraph.nodes["react-app"]
        assertNotNull(reactApp)
        
        val tags = reactApp.data.tags
        assertAll(
            { assertTrue("react" in tags, "Should include framework tag") },
            { assertTrue("frontend" in tags, "Should include keyword tag") },
            { assertTrue("scope:frontend" in tags, "Should include nx tag") },
            { assertTrue("type:app" in tags, "Should include nx type tag") }
        )
        
        val utilsLib = projectGraph.nodes["@my-org/utils-lib"]
        assertNotNull(utilsLib)
        
        val utilsTags = utilsLib.data.tags
        assertAll(
            { assertTrue("rollup" in utilsTags, "Should include rollup dependency tag") },
            { assertTrue("scope:shared" in utilsTags, "Should include nx scope tag") },
            { assertTrue("type:lib" in utilsTags, "Should include nx type tag") },
            { assertTrue("utils" in utilsTags, "Should include keyword tag") }
        )
    }
    
    @Test
    fun `should infer correct project roots`() {
        val discovery = ProjectDiscovery(testWorkspace, enableInference = true)
        val projectGraph = discovery.discoverProjects()
        
        val reactApp = projectGraph.nodes["react-app"]
        assertNotNull(reactApp)
        assertEquals("packages/react-app", reactApp.data.root)
        
        val utilsLib = projectGraph.nodes["@my-org/utils-lib"]
        assertNotNull(utilsLib)
        assertEquals("packages/utils-lib", utilsLib.data.root)
        
        val apiServer = projectGraph.nodes["api-server"]
        assertNotNull(apiServer)
        assertEquals("apps/api-server", apiServer.data.root)
        
        val cliTool = projectGraph.nodes["@my-org/cli-tool"]
        assertNotNull(cliTool)
        assertEquals("tools/cli-tool", cliTool.data.root)
    }
    
    @Test
    fun `should not discover projects when inference is disabled`() {
        // Given inference is disabled
        val discovery = ProjectDiscovery(testWorkspace, enableInference = false)
        
        // When discovering projects
        val projectGraph = discovery.discoverProjects()
        
        // Then should not discover any projects from package.json (only from project.json files if any)
        // Since our test workspace only has package.json files, this should be empty
        assertTrue(projectGraph.nodes.isEmpty(), "Should not discover projects when inference is disabled")
    }
}