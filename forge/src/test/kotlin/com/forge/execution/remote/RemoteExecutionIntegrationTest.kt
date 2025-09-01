package com.forge.execution.remote

import build.bazel.remote.execution.v2.BatchUpdateBlobsRequest
import com.google.protobuf.ByteString
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class RemoteExecutionIntegrationTest {
    
    @Test
    fun `test BatchUpdateBlobs API call like RemoteExecutionExecutor does`() = runBlocking {
        val config = RemoteExecutionConfig(
            endpoint = "127.0.0.1:8980",
            instanceName = "default", 
            useTls = false,
            maxConnections = 10,
            timeoutSeconds = 300,
            platform = emptyMap()
        )
        
        println("Testing BatchUpdateBlobs API call with BuildBarn")
        
        try {
            val services = RemoteExecutionServiceFactory.create(config)
            
            // Create a simple test blob like RemoteExecutionExecutor does
            val testData = "test data".toByteArray()
            val digest = RemoteExecutionBuilder(java.nio.file.Paths.get("."), "default")
                .computeDigest(testData)
            
            val blobRequest = BatchUpdateBlobsRequest.Request.newBuilder()
                .setDigest(digest)
                .setData(ByteString.copyFrom(testData))
                .build()
            
            val batchRequest = BatchUpdateBlobsRequest.newBuilder()
                .setInstanceName(config.instanceName)
                .addRequests(blobRequest)
                .build()
            
            println("Making BatchUpdateBlobs call...")
            val response = services.cas.batchUpdateBlobs(batchRequest)
            println("BatchUpdateBlobs succeeded! Response: ${response.responsesList.size} blobs")
            
            services.close()
            
        } catch (e: Exception) {
            println("BatchUpdateBlobs failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
}