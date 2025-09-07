package com.frontseat.maven.commons

/**
 * Maven-specific nature and task constants
 */
object MavenNatureIds {
    const val MAVEN = "maven"
}

object MavenTaskNames {
    // Maven lifecycle tasks
    const val VALIDATE = "validate"
    const val INITIALIZE = "initialize"
    const val COMPILE = "compile"
    const val TEST = "test"
    const val PACKAGE = "package"
    const val VERIFY = "verify"
    const val INSTALL = "install"
    const val DEPLOY = "deploy"
    const val CLEAN = "clean"
    
    // Alias for common tasks
    const val BUILD = COMPILE
    const val PUBLISH = DEPLOY
}