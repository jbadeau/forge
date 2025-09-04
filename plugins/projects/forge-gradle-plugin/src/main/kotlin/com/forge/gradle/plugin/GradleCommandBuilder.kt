package com.forge.gradle.plugin

import java.nio.file.Path

/**
 * Builder for constructing Gradle commands
 */
class GradleCommandBuilder private constructor() {
    private val tasks = mutableListOf<String>()
    private val args = mutableListOf<String>()
    private val properties = mutableMapOf<String, String>()
    private val systemProperties = mutableMapOf<String, String>()
    private var projectDir: Path? = null
    
    companion object {
        fun build(): GradleCommandBuilder = GradleCommandBuilder()
    }
    
    /**
     * Add a task to execute
     */
    fun withTask(task: String): GradleCommandBuilder {
        tasks.add(task)
        return this
    }
    
    /**
     * Add multiple tasks
     */
    fun withTasks(vararg taskNames: String): GradleCommandBuilder {
        tasks.addAll(taskNames)
        return this
    }
    
    /**
     * Add a project property (-P)
     */
    fun withProperty(key: String, value: String): GradleCommandBuilder {
        properties[key] = value
        return this
    }
    
    /**
     * Add a system property (-D)  
     */
    fun withSystemProperty(key: String, value: String): GradleCommandBuilder {
        systemProperties[key] = value
        return this
    }
    
    /**
     * Add a custom argument
     */
    fun withArg(arg: String): GradleCommandBuilder {
        args.add(arg)
        return this
    }
    
    /**
     * Set project directory
     */
    fun inProject(projectDir: Path): GradleCommandBuilder {
        this.projectDir = projectDir
        return this
    }
    
    /**
     * Run in quiet mode
     */
    fun quiet(): GradleCommandBuilder {
        args.add("--quiet")
        return this
    }
    
    /**
     * Run in parallel mode
     */
    fun parallel(): GradleCommandBuilder {
        args.add("--parallel")
        return this
    }
    
    /**
     * Skip tests
     */
    fun skipTests(): GradleCommandBuilder {
        args.add("-x")
        args.add("test")
        return this
    }
    
    /**
     * Enable build cache
     */
    fun withBuildCache(): GradleCommandBuilder {
        args.add("--build-cache")
        return this
    }
    
    /**
     * Enable configuration cache
     */
    fun withConfigurationCache(): GradleCommandBuilder {
        args.add("--configuration-cache")
        return this
    }
    
    /**
     * Set log level
     */
    fun withLogLevel(level: GradleLogLevel): GradleCommandBuilder {
        when (level) {
            GradleLogLevel.QUIET -> args.add("--quiet")
            GradleLogLevel.INFO -> args.add("--info")
            GradleLogLevel.DEBUG -> args.add("--debug")
            GradleLogLevel.WARN -> args.add("--warn")
        }
        return this
    }
    
    /**
     * Convert to executor options map
     */
    fun toOptions(): Map<String, Any> {
        val command = mutableListOf<String>()
        
        // Add executable (gradlew or gradle)
        val executable = projectDir?.let { GradleUtils.getGradleCommand(it) } ?: "gradle"
        command.add(executable)
        
        // Add tasks
        command.addAll(tasks)
        
        // Add properties
        properties.forEach { (key, value) ->
            command.add("-P$key=$value")
        }
        
        // Add system properties
        systemProperties.forEach { (key, value) ->
            command.add("-D$key=$value")
        }
        
        // Add other arguments
        command.addAll(args)
        
        return mapOf(
            "command" to command.joinToString(" "),
            "tasks" to tasks,
            "properties" to properties,
            "systemProperties" to systemProperties
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

/**
 * Gradle log levels
 */
enum class GradleLogLevel {
    QUIET, INFO, DEBUG, WARN
}