package com.forge.daemon

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

object ForgeDaemon {
    fun startDaemon(args: Array<String>) {
        val logger = LoggerFactory.getLogger("ForgeDaemon")
        
        try {
            val daemon = DaemonServer()
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Shutting down daemon...")
                daemon.stop()
            })
            
            // Start daemon
            runBlocking {
                daemon.start()
            }
        } catch (e: Exception) {
            logger.error("Failed to start daemon", e)
            exitProcess(1)
        }
    }
}

fun main(args: Array<String>) {
    ForgeDaemon.startDaemon(args)
}