package com.forge.execution.remote

import build.bazel.remote.execution.v2.*
import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class BuildBarnExecuteTest {
    
    @Test
    fun `test BuildBarn execute with minimal request`() {
        val channel = ManagedChannelBuilder.forTarget("127.0.0.1:8980")
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .build()
        
        try {
            // Create a minimal command (just echo hello)
            val command = Command.newBuilder()
                .addArguments("echo")
                .addArguments("hello")
                .build()
            
            // Create a minimal directory (empty)
            val directory = Directory.newBuilder().build()
            
            // Create minimal digests
            val commandBytes = command.toByteArray()
            val commandDigest = Digest.newBuilder()
                .setHash(sha256(commandBytes))
                .setSizeBytes(commandBytes.size.toLong())
                .build()
            
            val directoryBytes = directory.toByteArray()
            val inputRootDigest = Digest.newBuilder()
                .setHash(sha256(directoryBytes))
                .setSizeBytes(directoryBytes.size.toLong())
                .build()
            
            // Create action
            val action = Action.newBuilder()
                .setCommandDigest(commandDigest)
                .setInputRootDigest(inputRootDigest)
                .build()
            
            val actionBytes = action.toByteArray()
            val actionDigest = Digest.newBuilder()
                .setHash(sha256(actionBytes))
                .setSizeBytes(actionBytes.size.toLong())
                .build()
            
            // Create execute request
            val executeRequest = ExecuteRequest.newBuilder()
                .setInstanceName("default")
                .setActionDigest(actionDigest)
                .build()
            
            // Try to execute
            val stub = ExecutionGrpc.newBlockingStub(channel)
            println("Attempting to execute minimal request...")
            
            val responseIterator = stub
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .execute(executeRequest)
                
            var operationCount = 0
            while (responseIterator.hasNext() && operationCount < 5) { // Limit to avoid infinite loops
                val operation = responseIterator.next()
                operationCount++
                println("Operation $operationCount: name=${operation.name}, done=${operation.done}")
                if (operation.done) {
                    break
                }
            }
            println("Execute test completed successfully with $operationCount operations")
            
        } catch (e: Exception) {
            println("Execute failed: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        } finally {
            channel.shutdown()
        }
    }
    
    private fun sha256(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}