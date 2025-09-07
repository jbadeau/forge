package com.frontseat.nature

/**
 * Development lifecycle phases for local development activities
 */
enum class DevelopmentLifecyclePhase(val order: Int) {
    FORMAT(1),          // format source code (prettier, spotless, etc.)
    LINT(2),            // lint source code
    START(3),           // start application locally (Spring Boot, Node dev server, etc.)
    NUKE(4)             // deep clean - remove all generated files, node_modules, etc.
}

/**
 * Build lifecycle phases for creating versionless, cacheable artifacts
 */
enum class BuildLifecyclePhase(val order: Int) {
    VALIDATE(1),        // validate project structure
    GENERATE(2),        // generate source code
    COMPILE(3),         // compile source code
    TEST(4),            // run unit tests
    PACKAGE(5),         // create versionless artifacts (JAR, Docker image, etc.)
    VERIFY(6)           // integration tests, quality checks
}

/**
 * Release lifecycle phases for versioning and distributing artifacts
 */
enum class ReleaseLifecyclePhase(val order: Int) {
    VERSION(1),          // apply version to artifacts (tag, stamp, rename)
    ASSEMBLE(2),         // create platform-specific distributions (jlink, native, etc.)
    SECURE(3),           // sign artifacts, calculate checksums, security checks
    RELEASE(4),          // create release in Git repository with changelog
    PUBLISH(5),          // publish to registries (Maven Central, Docker Hub, etc.)
    ANNOUNCE(6)          // announce release to various channels
}

/**
 * Represents which lifecycle a target belongs to
 */
sealed class TargetLifecycle {
    data class Build(val phase: BuildLifecyclePhase) : TargetLifecycle()
    data class Development(val phase: DevelopmentLifecyclePhase) : TargetLifecycle()
    data class Release(val phase: ReleaseLifecyclePhase) : TargetLifecycle()
}