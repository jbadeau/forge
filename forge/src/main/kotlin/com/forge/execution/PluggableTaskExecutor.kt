package com.forge.execution

import com.forge.project.ProjectGraph
import com.forge.project.TaskExecutionPlan
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Task executor that uses pluggable TaskExecutorPlugin implementations
 */
class PluggableTaskExecutor(
    private val executorPlugin: TaskExecutorPlugin,
    private val projectGraph: ProjectGraph,
    private val workspaceRoot: Path
) : TaskExecutor, AutoCloseable {
    
    private val logger = LoggerFactory.getLogger(PluggableTaskExecutor::class.java)
    
    override fun execute(executionPlan: TaskExecutionPlan, verbose: Boolean): ExecutionResults {
        logger.info("Starting execution with ${executorPlugin.getExecutorId()}")
        return executorPlugin.execute(executionPlan, projectGraph, workspaceRoot, verbose)
    }
    
    override fun close() {
        executorPlugin.close()
    }
}