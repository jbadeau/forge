package com.frontseat.client.daemon

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

object ForgeDaemon {
    fun startDaemon(args: Array<String>) {
        val logger = LoggerFactory.getLogger("ForgeDaemon")
        
        try {
            val server = JsonRpcServer()
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Shutting down JSON-RPC server...")
                server.stop()
            })
            
            // Start JSON-RPC server
            runBlocking {
                server.start()
            }
        } catch (e: Exception) {
            logger.error("Failed to start JSON-RPC server", e)
            exitProcess(1)
        }
    }
}

fun main(args: Array<String>) {
    ForgeDaemon.startDaemon(args)
}