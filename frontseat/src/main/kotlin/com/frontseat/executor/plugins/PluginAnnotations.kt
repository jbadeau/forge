package com.frontseat.executor.plugins

/**
 * Mark a plugin class for automatic discovery
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Plugin(
    val id: String,
    val name: String = ""
)