package com.forge.execution.remote

import build.bazel.remote.execution.v2.*
import com.google.longrunning.Operation
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for Remote Execution API
 * This provides a Kotlin-friendly wrapper over the generated gRPC services
 */
interface RemoteExecutionService {
    /**
     * Execute an action remotely and return a stream of operation updates
     */
    suspend fun execute(request: ExecuteRequest): Flow<Operation>
    
    /**
     * Wait for an operation to complete
     */
    suspend fun waitExecution(request: WaitExecutionRequest): Flow<Operation>
    
    /**
     * Get server capabilities
     */
    suspend fun getCapabilities(request: GetCapabilitiesRequest): ServerCapabilities
}

/**
 * Service interface for Content Addressable Storage
 */
interface ContentAddressableStorageService {
    /**
     * Find missing blobs in the CAS
     */
    suspend fun findMissingBlobs(request: FindMissingBlobsRequest): FindMissingBlobsResponse
    
    /**
     * Upload multiple blobs to CAS
     */
    suspend fun batchUpdateBlobs(request: BatchUpdateBlobsRequest): BatchUpdateBlobsResponse
    
    /**
     * Download multiple blobs from CAS
     */
    suspend fun batchReadBlobs(request: BatchReadBlobsRequest): BatchReadBlobsResponse
    
    /**
     * Get a directory tree structure
     */
    suspend fun getTree(request: GetTreeRequest): Flow<GetTreeResponse>
}

/**
 * Service interface for Action Cache
 */
interface ActionCacheService {
    /**
     * Get a cached action result
     */
    suspend fun getActionResult(request: GetActionResultRequest): ActionResult?
    
    /**
     * Update the action cache with a new result
     */
    suspend fun updateActionResult(request: UpdateActionResultRequest): ActionResult
}


/**
 * Exception thrown when remote execution fails
 */
class RemoteExecutionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)