package com.frontseat.project.nature

/**
 * Common task name constants that are used across multiple natures/plugins
 * Specific task names should be defined in their respective commons modules
 */
object TaskNames {
    
    // Generic lifecycle tasks (used by multiple build systems)
    const val BUILD = "build"
    const val TEST = "test"
    const val CLEAN = "clean"
    const val LINT = "lint"
    const val FORMAT = "format"
    
    // Generic development tasks
    const val SERVE = "serve"
    const val DEV = "dev"
    const val START = "start"
    const val WATCH = "watch"
    
    // Generic testing tasks
    const val UNIT_TEST = "unit-test"
    const val INTEGRATION_TEST = "integration-test"
    const val E2E_TEST = "e2e-test"
    
    // Generic release tasks
    const val PUBLISH = "publish"
    const val RELEASE = "release"
    
    // Generic documentation tasks
    const val DOCS = "docs"
}