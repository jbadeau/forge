package com.frontseat.inference

import com.frontseat.project.ProjectGraphBuilder
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertTrue

class InferenceIntegrationTest {
    
    private val testWorkspace = Path.of("src/test/resources/test-inference-workspace")
    
    @Test
    fun `should not discover projects without registered plugins`() {
        // Given a workspace with package.json files but no registered plugins
        val natureRegistry = com.frontseat.nature.NatureRegistry()
        val graphBuilder = ProjectGraphBuilder(testWorkspace, emptyList(), true, natureRegistry)
        
        // When discovering projects
        val projectGraph = graphBuilder.buildProjectGraph()
        
        // Then should NOT discover projects since no plugins are registered
        assertTrue(projectGraph.nodes.isEmpty(), "Should not discover projects without registered plugins")
    }
    
    @Test
    fun `should not discover projects when inference is disabled`() {
        // Given inference is disabled
        val natureRegistry = com.frontseat.nature.NatureRegistry()
        val graphBuilder = ProjectGraphBuilder(testWorkspace, emptyList(), false, natureRegistry)
        
        // When discovering projects
        val projectGraph = graphBuilder.buildProjectGraph()
        
        // Then should not discover any projects from package.json (only from project.json files if any)
        // Since our test workspace only has package.json files, this should be empty
        assertTrue(projectGraph.nodes.isEmpty(), "Should not discover projects when inference is disabled")
    }
}