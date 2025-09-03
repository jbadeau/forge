package com.forge.execution.remote

import build.bazel.remote.execution.v2.*
import com.google.longrunning.Operation
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * gRPC-based implementation of RemoteExecutionService
 */
class GrpcRemoteExecutionService(
    private val channel: ManagedChannel,
    private val config: RemoteExecutionConfig
) : RemoteExecutionService {
    
    private val logger = LoggerFactory.getLogger(GrpcRemoteExecutionService::class.java)
    private val stub = ExecutionGrpc.newStub(channel)
    private val blockingStub = ExecutionGrpc.newBlockingStub(channel)
    
    override suspend fun execute(request: ExecuteRequest): Flow<Operation> = flow {
        try {
            logger.debug("Executing remote action: ${request.actionDigest}")
            val responseIterator = blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .execute(request)
            
            for (operation in responseIterator) {
                emit(operation)
                if (operation.done) {
                    break
                }
            }
        } catch (e: StatusRuntimeException) {
            logger.error("Remote execution failed: ${e.status}", e)
            throw RemoteExecutionException("Remote execution failed: ${e.status.description}", e)
        }
    }
    
    override suspend fun waitExecution(request: WaitExecutionRequest): Flow<Operation> = flow {
        try {
            logger.debug("Waiting for execution: ${request.name}")
            val responseIterator = blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .waitExecution(request)
            
            for (operation in responseIterator) {
                emit(operation)
                if (operation.done) {
                    break
                }
            }
        } catch (e: StatusRuntimeException) {
            logger.error("Wait execution failed: ${e.status}", e)
            throw RemoteExecutionException("Wait execution failed: ${e.status.description}", e)
        }
    }
    
    override suspend fun getCapabilities(request: GetCapabilitiesRequest): ServerCapabilities {
        return try {
            logger.debug("Getting server capabilities")
            val capabilitiesStub = CapabilitiesGrpc.newBlockingStub(channel)
            capabilitiesStub
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .getCapabilities(request)
        } catch (e: StatusRuntimeException) {
            logger.error("Failed to get capabilities: ${e.status}", e)
            throw RemoteExecutionException("Failed to get capabilities: ${e.status.description}", e)
        }
    }
}

/**
 * gRPC-based implementation of ContentAddressableStorageService
 */
class GrpcContentAddressableStorageService(
    private val channel: ManagedChannel,
    private val config: RemoteExecutionConfig
) : ContentAddressableStorageService {
    
    private val logger = LoggerFactory.getLogger(GrpcContentAddressableStorageService::class.java)
    private val blockingStub = ContentAddressableStorageGrpc.newBlockingStub(channel)
    
    override suspend fun findMissingBlobs(request: FindMissingBlobsRequest): FindMissingBlobsResponse {
        return try {
            logger.debug("Finding missing blobs: ${request.blobDigestsList.size} digests")
            blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .findMissingBlobs(request)
        } catch (e: StatusRuntimeException) {
            logger.error("Failed to find missing blobs: ${e.status}", e)
            throw RemoteExecutionException("Failed to find missing blobs: ${e.status.description}", e)
        }
    }
    
    override suspend fun batchUpdateBlobs(request: BatchUpdateBlobsRequest): BatchUpdateBlobsResponse {
        return try {
            logger.debug("Uploading ${request.requestsList.size} blobs")
            blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .batchUpdateBlobs(request)
        } catch (e: StatusRuntimeException) {
            logger.error("Failed to upload blobs: ${e.status}", e)
            throw RemoteExecutionException("Failed to upload blobs: ${e.status.description}", e)
        }
    }
    
    override suspend fun batchReadBlobs(request: BatchReadBlobsRequest): BatchReadBlobsResponse {
        return try {
            logger.debug("Downloading ${request.digestsList.size} blobs")
            blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .batchReadBlobs(request)
        } catch (e: StatusRuntimeException) {
            logger.error("Failed to download blobs: ${e.status}", e)
            throw RemoteExecutionException("Failed to download blobs: ${e.status.description}", e)
        }
    }
    
    override suspend fun getTree(request: GetTreeRequest): Flow<GetTreeResponse> = flow {
        try {
            logger.debug("Getting tree: ${request.rootDigest}")
            val responseIterator = blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .getTree(request)
            
            for (response in responseIterator) {
                emit(response)
            }
        } catch (e: StatusRuntimeException) {
            logger.error("Failed to get tree: ${e.status}", e)
            throw RemoteExecutionException("Failed to get tree: ${e.status.description}", e)
        }
    }
}

/**
 * gRPC-based implementation of ActionCacheService
 */
class GrpcActionCacheService(
    private val channel: ManagedChannel,
    private val config: RemoteExecutionConfig
) : ActionCacheService {
    
    private val logger = LoggerFactory.getLogger(GrpcActionCacheService::class.java)
    private val blockingStub = ActionCacheGrpc.newBlockingStub(channel)
    
    override suspend fun getActionResult(request: GetActionResultRequest): ActionResult? {
        return try {
            logger.debug("Getting cached action result: ${request.actionDigest}")
            blockingStub
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .getActionResult(request)
        } catch (e: StatusRuntimeException) {
            if (e.status.code.name == "NOT_FOUND") {
                logger.debug("Action result not found in cache: ${request.actionDigest}")
                return null
            }
            logger.error("Failed to get action result: ${e.status}", e)
            throw RemoteExecutionException("Failed to get action result: ${e.status.description}", e)
        }
    }
    
    override suspend fun updateActionResult(request: UpdateActionResultRequest): ActionResult {
        return try {
            logger.debug("Updating action result: ${request.actionDigest}")
            blockingStub
                .withDeadlineAfter(config.timeoutSeconds, TimeUnit.SECONDS)
                .updateActionResult(request)
        } catch (e: StatusRuntimeException) {
            logger.error("Failed to update action result: ${e.status}", e)
            throw RemoteExecutionException("Failed to update action result: ${e.status.description}", e)
        }
    }
}

/**
 * Factory for creating Remote Execution services
 */
class RemoteExecutionServiceFactory {
    
    companion object {
        fun create(config: RemoteExecutionConfig): RemoteExecutionServices {
            val channelBuilder = ManagedChannelBuilder.forTarget(config.endpoint)
                .maxInboundMessageSize(16 * 1024 * 1024) // 16MB
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
            
            if (!config.useTls) {
                channelBuilder.usePlaintext()
            }
            
            val channel = channelBuilder.build()
            
            return RemoteExecutionServices(
                execution = GrpcRemoteExecutionService(channel, config),
                cas = GrpcContentAddressableStorageService(channel, config),
                actionCache = GrpcActionCacheService(channel, config),
                channel = channel
            )
        }
    }
}

/**
 * Container for all Remote Execution services
 */
data class RemoteExecutionServices(
    val execution: RemoteExecutionService,
    val cas: ContentAddressableStorageService,
    val actionCache: ActionCacheService,
    private val channel: ManagedChannel
) : AutoCloseable {
    
    override fun close() {
        channel.shutdown()
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow()
            }
        } catch (e: InterruptedException) {
            channel.shutdownNow()
        }
    }
}