package com.frontseat.maven.plugin

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.div

/**
 * Builder for constructing Maven commands
 */
class MavenCommandBuilder private constructor() {
    private val phases = mutableListOf<String>()
    private val goals = mutableListOf<String>()
    private val properties = mutableMapOf<String, String>()
    private val profiles = mutableListOf<String>()
    private val args = mutableListOf<String>()
    private var projectDir: Path? = null
    
    companion object {
        fun build(): MavenCommandBuilder = MavenCommandBuilder()
    }
    
    /**
     * Add a Maven phase to execute
     */
    fun withPhase(phase: String): MavenCommandBuilder {
        phases.add(phase)
        return this
    }
    
    /**
     * Add a Maven goal to execute
     */
    fun withGoal(goal: String): MavenCommandBuilder {
        goals.add(goal)
        return this
    }
    
    /**
     * Add a property (-D)
     */
    fun withProperty(key: String, value: String): MavenCommandBuilder {
        properties[key] = value
        return this
    }
    
    /**
     * Add a profile (-P)
     */
    fun withProfile(profile: String): MavenCommandBuilder {
        profiles.add(profile)
        return this
    }
    
    /**
     * Add a custom argument
     */
    fun withArg(arg: String): MavenCommandBuilder {
        args.add(arg)
        return this
    }
    
    /**
     * Set project directory
     */
    fun inProject(projectDir: Path): MavenCommandBuilder {
        this.projectDir = projectDir
        return this
    }
    
    /**
     * Run in quiet mode
     */
    fun quiet(): MavenCommandBuilder {
        args.add("-q")
        return this
    }
    
    /**
     * Skip tests
     */
    fun skipTests(): MavenCommandBuilder {
        properties["skipTests"] = "true"
        return this
    }
    
    /**
     * Enable batch mode (non-interactive)
     */
    fun batchMode(): MavenCommandBuilder {
        args.add("-B")
        return this
    }
    
    /**
     * Get the Maven command (wrapper or regular mvn)
     */
    private fun getMavenCommand(): String {
        if (projectDir != null) {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val wrapperScript = if (isWindows) "mvnw.bat" else "mvnw"
            val wrapperPath = projectDir!! / wrapperScript
            
            if (wrapperPath.exists()) {
                return wrapperPath.toString()
            }
        }
        return "mvn"
    }
    
    /**
     * Convert to executor options map
     */
    fun toOptions(): Map<String, Any> {
        val command = mutableListOf<String>()
        
        // Add executable
        val executable = getMavenCommand()
        command.add(executable)
        
        // Add phases and goals
        command.addAll(phases)
        command.addAll(goals)
        
        // Add properties
        properties.forEach { (key, value) ->
            command.add("-D$key=$value")
        }
        
        // Add profiles
        if (profiles.isNotEmpty()) {
            command.add("-P${profiles.joinToString(",")}")
        }
        
        // Add other arguments
        command.addAll(args)
        
        return mapOf(
            "command" to command.joinToString(" "),
            "phases" to phases,
            "goals" to goals,
            "properties" to properties,
            "profiles" to profiles,
            "workingDirectory" to (projectDir?.toString() ?: ".")
        )
    }
    
    /**
     * Get the command as a string
     */
    fun toCommandString(): String {
        val options = toOptions()
        return options["command"] as String
    }
}