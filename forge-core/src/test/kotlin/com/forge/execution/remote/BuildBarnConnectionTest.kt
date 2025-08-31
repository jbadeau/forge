package com.forge.execution.remote

import build.bazel.remote.execution.v2.CapabilitiesGrpc
import build.bazel.remote.execution.v2.GetCapabilitiesRequest
import io.grpc.ManagedChannelBuilder
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class BuildBarnConnectionTest {
    
    @Test
    fun `test BuildBarn gRPC connection`() {
        val channel = ManagedChannelBuilder.forTarget("127.0.0.1:8980")
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .build()
        
        try {
            val stub = CapabilitiesGrpc.newBlockingStub(channel)
            val request = GetCapabilitiesRequest.newBuilder()
                .setInstanceName("default")
                .build()
            
            println("Attempting to connect to BuildBarn...")
            val capabilities = stub.getCapabilities(request)
            println("Successfully connected! Capabilities: $capabilities")
            
        } catch (e: Exception) {
            println("Connection failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        } finally {
            channel.shutdown()
        }
    }
}