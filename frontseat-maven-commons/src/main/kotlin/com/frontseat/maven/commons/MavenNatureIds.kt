package com.frontseat.maven.commons

/**
 * Maven-specific nature and task constants
 */
object MavenNatureIds {
    const val MAVEN = "maven"
}

object MavenTaskNames {
    // Maven lifecycle tasks
    const val VALIDATE = "maven-validate"
    const val INITIALIZE = "maven-initialize"
    const val BUILD = "maven-build"
    const val TEST = "maven-test"
    const val PACKAGE = "maven-package"
    const val VERIFY = "maven-verify"
    const val INSTALL = "maven-install"
    const val DEPLOY = "maven-deploy"
    const val CLEAN = "maven-clean"
    
    // Alias for common tasks
    const val PUBLISH = DEPLOY
}