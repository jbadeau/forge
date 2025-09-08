package com.frontseat.project.nature

/**
 * Mark a nature class with its metadata
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NatureInfo(
    val id: String,
    val layer: Int  // NatureLayers.BUILD_SYSTEMS, etc.
)