package com.forge.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

// JSON-RPC 2.0 message format (LSP-style)
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val method: String,
    val params: Any?
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

class DaemonClient {
    private val mapper = jacksonObjectMapper()
    private val requestId = AtomicInteger(0)
    
    companion object {
        private const val DAEMON_DIR = ".forge"
        private const val PID_FILE = "daemon.pid"
        
        fun getDaemonDir(): Path {
            return Paths.get(System.getProperty("user.home"), DAEMON_DIR)
        }
        
        fun getPidFile(): Path {
            return getDaemonDir().resolve(PID_FILE)
        }
    }
    
    fun sendRequest(method: String, params: Any? = null): JsonRpcResponse {
        val daemonProcess = ensureDaemonRunning()
        
        val request = JsonRpcRequest(
            id = requestId.incrementAndGet(),
            method = method,
            params = params
        )
        
        return try {
            // Send request to daemon's stdin
            val requestJson = mapper.writeValueAsString(request)
            daemonProcess.outputStream.writer().use { writer ->
                writer.write(requestJson)
                writer.write("\n")
                writer.flush()
            }
            
            // Read response from daemon's stdout
            val response = daemonProcess.inputStream.reader().buffered().readLine()
            mapper.readValue<JsonRpcResponse>(response)
            
        } catch (e: Exception) {
            // Daemon might have died, try to restart
            killDaemon()
            val newProcess = ensureDaemonRunning()
            
            val requestJson = mapper.writeValueAsString(request)
            newProcess.outputStream.writer().use { writer ->
                writer.write(requestJson)
                writer.write("\n")
                writer.flush()
            }
            
            val response = newProcess.inputStream.reader().buffered().readLine()
            mapper.readValue<JsonRpcResponse>(response)
        }
    }
    
    private var cachedDaemonProcess: Process? = null
    
    private fun ensureDaemonRunning(): Process {
        // Check if we have a cached process that's still alive
        cachedDaemonProcess?.let { process ->
            if (process.isAlive) {
                return process
            } else {
                cachedDaemonProcess = null
            }
        }
        
        // Check if daemon is running via PID file
        val existingProcess = getDaemonProcess()
        if (existingProcess?.isAlive == true) {
            cachedDaemonProcess = existingProcess
            return existingProcess
        }
        
        // Start new daemon
        val process = startDaemon()
        cachedDaemonProcess = process
        return process
    }
    
    private fun getDaemonProcess(): Process? {
        val pidFile = getPidFile()
        if (!Files.exists(pidFile)) return null
        
        return try {
            val pid = Files.readString(pidFile).trim().toLongOrNull() ?: return null
            // On Unix systems, we can use ProcessHandle to get process by PID
            ProcessHandle.of(pid).orElse(null)?.let { handle ->
                // This is a bit tricky - we can't easily get the Process object from ProcessHandle
                // For now, return null to force restart
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun startDaemon(): Process {
        val daemonJar = findDaemonJar()
        
        val processBuilder = if (isWindows()) {
            ProcessBuilder("cmd", "/c", "java", "-jar", daemonJar.toString())
        } else {
            ProcessBuilder("java", "-jar", daemonJar.toString())
        }
        
        // Create daemon directory
        val daemonDir = getDaemonDir()
        Files.createDirectories(daemonDir)
        
        val process = processBuilder
            .redirectError(daemonDir.resolve("daemon.log").toFile())
            .start()
        
        // Write PID file
        try {
            Files.write(getPidFile(), process.pid().toString().toByteArray())
        } catch (e: Exception) {
            // PID file write failed, but daemon might still work
        }
        
        // Give daemon a moment to initialize
        Thread.sleep(100)
        
        if (!process.isAlive) {
            throw RuntimeException("Daemon failed to start")
        }
        
        return process
    }
    
    private fun killDaemon() {
        cachedDaemonProcess?.let { process ->
            if (process.isAlive) {
                process.destroy()
                // Give it a moment to shut down gracefully
                Thread.sleep(100)
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
            cachedDaemonProcess = null
        }
        
        // Clean up PID file
        try {
            Files.deleteIfExists(getPidFile())
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }
    
    private fun findDaemonJar(): Path {
        // Look for daemon JAR relative to client
        val pathSeparator = if (isWindows()) ";" else ":"
        val clientJar = Paths.get(System.getProperty("java.class.path").split(pathSeparator)[0])
        val clientDir = clientJar.parent
        
        // Check various possible locations
        val possibleLocations = listOf(
            clientDir.resolve("forge-daemon.jar"),
            clientDir.resolve("../forge-daemon/target/forge-daemon.jar"),
            clientDir.parent?.resolve("forge-daemon/target/forge-daemon.jar"),
            Paths.get(System.getProperty("user.home"), ".forge", "daemon", "forge-daemon.jar")
        )
        
        for (location in possibleLocations) {
            if (location != null && Files.exists(location)) {
                return location
            }
        }
        
        throw RuntimeException("Could not find forge-daemon.jar")
    }
}