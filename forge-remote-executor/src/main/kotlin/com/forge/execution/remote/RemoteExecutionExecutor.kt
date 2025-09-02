package com.forge.execution.remote

import build.bazel.remote.execution.v2.*
import com.forge.project.ProjectGraph
import com.forge.execution.ExecutionResults
import com.forge.project.Task
import com.forge.project.TaskExecutionPlan
import com.forge.project.TaskResult
import com.forge.project.TaskStatus
import com.google.protobuf.ByteString
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant

/**
 * Remote Execution-based task executor
 * This executor can work with both local and remote execution endpoints
 */
class RemoteExecutionExecutor(
    private val workspaceRoot: Path,
    private val projectGraph: ProjectGraph,
    private val config: RemoteExecutionConfig
) {
    private val logger = LoggerFactory.getLogger(RemoteExecutionExecutor::class.java)
    private val services = RemoteExecutionServiceFactory.create(config)
    private val builder = RemoteExecutionBuilder(workspaceRoot, config.instanceName)
    
    /**
     * Execute a task execution plan using Remote Execution API
     */
    fun execute(executionPlan: TaskExecutionPlan, verbose: Boolean = false, skipCache: Boolean = false): ExecutionResults = runBlocking {
        val results = mutableMapOf<String, TaskResult>()
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting remote execution of ${executionPlan.totalTasks} task(s) across ${executionPlan.getLayerCount()} layer(s)")
        
        for ((layerIndex, layer) in executionPlan.layers.withIndex()) {
            logger.info("Executing layer ${layerIndex + 1} with ${layer.size} task(s)")
            
            // Execute tasks in parallel within each layer using async
            val layerResults = layer.map { task ->
                async { executeTask(task, verbose, skipCache) }
            }.map { it.await() }
            
            // Add results to map
            layerResults.forEach { result ->
                results[result.task.id] = result
            }
            
            // Check if any task in this layer failed
            val failedTasks = layerResults.filter { !it.isSuccess }
            if (failedTasks.isNotEmpty()) {
                logger.error("${failedTasks.size} task(s) failed in layer ${layerIndex + 1}")
                failedTasks.forEach { result ->
                    logger.error("Failed task: ${result.task.id} - ${result.error}")
                }
                // Stop execution on failure
                break
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        val successCount = results.values.count { it.isSuccess }
        val failureCount = results.values.count { !it.isSuccess }
        
        logger.info("Remote execution completed in ${duration}ms - $successCount succeeded, $failureCount failed")
        
        ExecutionResults(
            results = results,
            totalDuration = duration,
            successCount = successCount,
            failureCount = failureCount
        )
    }
    
    /**
     * Execute a single task using Remote Execution API
     */
    private suspend fun executeTask(task: Task, verbose: Boolean, skipCache: Boolean): TaskResult {
        val startTime = System.currentTimeMillis()
        val startInstant = Instant.ofEpochMilli(startTime)
        
        try {
            logger.info("Executing remote task: ${task.id}")
            
            val projectNode = projectGraph.nodes[task.projectName]
            if (projectNode == null) {
                logger.error("Project not found: ${task.projectName}")
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = startInstant,
                    endTime = Instant.now(),
                    error = "Project not found: ${task.projectName}"
                )
            }
            
            // Build the remote execution request
            val executeRequest = builder.buildExecuteRequest(
                task = task,
                projectRoot = projectNode.data.root,
                skipCacheLookup = skipCache
            )
            
            // Check action cache first (unless skipping cache)
            if (!skipCache && task.target.isCacheable()) {
                val cachedResult = checkActionCache(executeRequest.actionDigest)
                if (cachedResult != null) {
                    logger.info("Task ${task.id} found in cache")
                    return TaskResult(
                        task = task,
                        status = TaskStatus.CACHED,
                        startTime = startInstant,
                        endTime = Instant.now(),
                        output = "Cached result",
                        fromCache = true
                    )
                }
            }
            
            // Upload blobs to CAS before execution
            logger.info("Uploading action blobs to CAS for task: ${task.id}")
            uploadActionBlobs(task, projectNode.data.root, executeRequest.actionDigest)
            
            // Execute remotely
            val operation = services.execution.execute(executeRequest).last()
            
            if (!operation.done) {
                logger.error("Remote execution did not complete: ${task.id}")
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = startInstant,
                    endTime = Instant.now(),
                    error = "Remote execution did not complete"
                )
            }
            
            val endTime = System.currentTimeMillis()
            val endInstant = Instant.now()
            val duration = endTime - startTime
            
            if (operation.hasError()) {
                logger.error("Remote execution failed for task ${task.id}: ${operation.error}")
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = startInstant,
                    endTime = endInstant,
                    error = "Remote execution failed: ${operation.error.message}"
                )
            }
            
            // Parse the execution response
            val response = operation.response.unpack(ExecuteResponse::class.java)
            val result = response.result
            
            if (result.exitCode == 0) {
                logger.info("Remote task ${task.id} completed successfully in ${duration}ms")
                
                // Cache the result if caching is enabled
                if (task.target.isCacheable()) {
                    cacheActionResult(executeRequest.actionDigest, result)
                }
                
                return TaskResult(
                    task = task,
                    status = TaskStatus.COMPLETED,
                    startTime = startInstant,
                    endTime = endInstant,
                    output = extractOutput(result),
                    exitCode = result.exitCode
                )
            } else {
                logger.error("Remote task ${task.id} failed with exit code ${result.exitCode}")
                return TaskResult(
                    task = task,
                    status = TaskStatus.FAILED,
                    startTime = startInstant,
                    endTime = endInstant,
                    output = extractOutput(result),
                    error = "Command failed with exit code ${result.exitCode}",
                    exitCode = result.exitCode
                )
            }
            
        } catch (e: RemoteExecutionException) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.error("Remote execution failed for task ${task.id}: ${e.message}", e)
            return TaskResult(
                task = task,
                status = TaskStatus.FAILED,
                startTime = startInstant,
                endTime = Instant.now(),
                error = "Remote execution error: ${e.message}"
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            logger.error("Unexpected error executing task ${task.id}: ${e.message}", e)
            return TaskResult(
                task = task,
                status = TaskStatus.FAILED,
                startTime = startInstant,
                endTime = Instant.now(),
                error = "Unexpected error: ${e.message}"
            )
        }
    }
    
    /**
     * Check if an action result is cached
     */
    private suspend fun checkActionCache(actionDigest: Digest): ActionResult? {
        return try {
            val request = GetActionResultRequest.newBuilder()
                .setInstanceName(config.instanceName)
                .setActionDigest(actionDigest)
                .build()
                
            services.actionCache.getActionResult(request)
        } catch (e: Exception) {
            logger.debug("Action not found in cache: ${e.message}")
            null
        }
    }
    
    /**
     * Cache an action result
     */
    private suspend fun cacheActionResult(actionDigest: Digest, actionResult: ActionResult) {
        try {
            val request = UpdateActionResultRequest.newBuilder()
                .setInstanceName(config.instanceName)
                .setActionDigest(actionDigest)
                .setActionResult(actionResult)
                .build()
                
            services.actionCache.updateActionResult(request)
            logger.debug("Cached action result: $actionDigest")
        } catch (e: Exception) {
            logger.warn("Failed to cache action result: ${e.message}")
        }
    }
    
    /**
     * Extract output from action result
     */
    private fun extractOutput(result: ActionResult): String {
        val output = StringBuilder()
        
        // For now, we'll return a simple message indicating the result status
        // In a full implementation, we'd download the actual stdout/stderr content
        // from CAS using the stdout_digest and stderr_digest fields
        if (result.hasStdoutDigest()) {
            output.append("Stdout available (digest: ${result.stdoutDigest.hash})")
        }
        
        if (result.hasStderrDigest()) {
            if (output.isNotEmpty()) output.append("\n")
            output.append("Stderr available (digest: ${result.stderrDigest.hash})")
        }
        
        return output.toString().ifEmpty { "No output captured" }
    }
    
    /**
     * Upload Action, Command, and Directory blobs to Content Addressable Storage
     */
    private suspend fun uploadActionBlobs(task: Task, projectRoot: String, actionDigest: Digest) {
        try {
            // Build the action and its components
            val action = builder.buildAction(task, projectRoot)
            val command = builder.buildCommand(task, projectRoot)
            val inputRoot = builder.buildInputRoot(task, projectRoot)
            
            // Create blobs to upload
            val blobs = mutableListOf<ByteString>()
            val digests = mutableListOf<Digest>()
            
            // Action blob
            val actionBytes = action.toByteString()
            blobs.add(actionBytes)
            digests.add(actionDigest)
            
            // Command blob
            val commandBytes = command.toByteString()
            val commandDigest = builder.computeDigest(commandBytes.toByteArray())
            blobs.add(commandBytes)
            digests.add(commandDigest)
            
            // Input root directory blob
            val inputRootBytes = inputRoot.toByteString()
            val inputRootDigest = builder.computeDigest(inputRootBytes.toByteArray())
            blobs.add(inputRootBytes)
            digests.add(inputRootDigest)
            
            // Upload all blobs using BatchUpdateBlobs API
            val blobRequests = blobs.mapIndexed { index, blobData ->
                BatchUpdateBlobsRequest.Request.newBuilder()
                    .setDigest(digests[index])
                    .setData(blobData)
                    .build()
            }
            
            val batchRequest = BatchUpdateBlobsRequest.newBuilder()
                .setInstanceName(config.instanceName)
                .addAllRequests(blobRequests)
                .build()
            
            val response = services.cas.batchUpdateBlobs(batchRequest)
            
            // Check if uploads succeeded
            response.responsesList.forEachIndexed { index, blobResponse ->
                if (blobResponse.status.code != 0) {
                    logger.warn("Failed to upload blob ${digests[index].hash}: ${blobResponse.status.message}")
                } else {
                    logger.debug("Successfully uploaded blob: ${digests[index].hash}")
                }
            }
            
            logger.info("Uploaded ${response.responsesList.size} blobs to CAS for task: ${task.id}")
            
        } catch (e: Exception) {
            logger.error("Failed to upload blobs for task ${task.id}: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Close the executor and clean up resources
     */
    fun close() {
        services.close()
    }
}