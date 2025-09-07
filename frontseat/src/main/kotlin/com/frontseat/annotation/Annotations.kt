package com.frontseat.annotation

/**
 * Mark a plugin class for automatic discovery
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Plugin(
    val id: String,
    val name: String = ""
)

/**
 * Mark a nature class with its metadata
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Nature(
    val id: String,
    val layer: Int  // NatureLayers.BUILD_SYSTEMS, etc.
)

/**
 * Mark a nature for automatic registration
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRegister