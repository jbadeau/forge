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
        
        // Try to find Java executable
        val javaCmd = findJavaExecutable()
        
        val processBuilder = if (isWindows()) {
            ProcessBuilder("cmd", "/c", javaCmd, "-jar", daemonJar.toString())
        } else {
            ProcessBuilder(javaCmd, "-jar", daemonJar.toString())
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
        // For native binaries, java.class.path might not be available or useful
        // Try to determine client location from system properties or current directory
        val possibleClientDirs = mutableListOf<Path>()
        
        // Try java.class.path first (for JAR execution)
        val classPath = System.getProperty("java.class.path")
        if (classPath != null && classPath.isNotBlank()) {
            val pathSeparator = if (isWindows()) ";" else ":"
            val clientJar = Paths.get(classPath.split(pathSeparator)[0])
            possibleClientDirs.add(clientJar.parent ?: Paths.get("."))
        }
        
        // Add current directory and parent directories
        possibleClientDirs.add(Paths.get("."))
        possibleClientDirs.add(Paths.get(".."))
        possibleClientDirs.add(Paths.get("../.."))
        
        // Check various possible locations
        val possibleLocations = mutableListOf<Path>()
        for (clientDir in possibleClientDirs) {
            possibleLocations.addAll(listOf(
                clientDir.resolve("forge-daemon.jar"),
                clientDir.resolve("forge-daemon/target/forge-daemon.jar"),
                clientDir.resolve("../forge-daemon/target/forge-daemon.jar"),
                clientDir.resolve("forge-github/forge-daemon/target/forge-daemon.jar")
            ))
        }
        
        // Add system location
        possibleLocations.add(Paths.get(System.getProperty("user.home"), ".forge", "daemon", "forge-daemon.jar"))
        
        for (location in possibleLocations) {
            if (Files.exists(location)) {
                return location
            }
        }
        
        throw RuntimeException("Could not find forge-daemon.jar. Tried: ${possibleLocations.joinToString(", ")}")
    }
    
    private fun findJavaExecutable(): String {
        // Try JAVA_HOME first
        System.getenv("JAVA_HOME")?.let { javaHome ->
            val javaPath = Paths.get(javaHome, "bin", "java")
            if (Files.exists(javaPath) && Files.isExecutable(javaPath)) {
                return javaPath.toString()
            }
        }
        
        // Try Mise Java installation
        System.getProperty("user.home")?.let { userHome ->
            val javaPath = Paths.get(userHome, ".local", "share", "mise", "shims", "java")
            if (Files.exists(javaPath) && Files.isExecutable(javaPath)) {
                return javaPath.toString()
            }
        }
        
        // Try common system locations
        val systemJavaLocations = listOf("/usr/bin/java", "/usr/local/bin/java")
        for (location in systemJavaLocations) {
            val javaPath = Paths.get(location)
            if (Files.exists(javaPath) && Files.isExecutable(javaPath)) {
                return location
            }
        }
        
        // Try PATH java as last resort
        try {
            val process = ProcessBuilder("java", "-version").start()
            process.waitFor()
            if (process.exitValue() == 0) {
                return "java"
            }
        } catch (e: Exception) {
            // PATH java not available
        }
        
        throw RuntimeException("Could not find Java executable")
    }
}