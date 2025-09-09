package com.frontseat.server

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

object Daemon {
    fun start(args: Array<String>) {
        val logger = LoggerFactory.getLogger("FrontseatServer")
        
        try {
            val server = Server()
            
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
    Daemon.start(args)
}