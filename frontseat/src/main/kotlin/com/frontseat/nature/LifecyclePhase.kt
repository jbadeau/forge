package com.frontseat.nature

/**
 * Development lifecycle phases for local development activities
 */
enum class DevelopmentLifecyclePhase(val order: Int) {
    FORMAT(1),          // format source code (prettier, spotless, etc.)
    LINT(2),            // lint source code
    SERVE(3),           // run application locally (Spring Boot, Node dev server, etc.)
    WATCH(4),           // watch files for changes
    RELOAD(5),          // hot reload on changes
    DEBUG(6),           // run in debug mode
    PROFILE(7),         // run with profiling
    MONITOR(8),         // monitor application metrics
    NUKE(9)             // deep clean - remove all generated files, node_modules, etc.
}

/**
 * Build lifecycle phases for creating versionless, cacheable artifacts
 */
enum class BuildLifecyclePhase(val order: Int) {
    VALIDATE(1),        // validate project structure
    INITIALIZE(2),      // setup, clean, prepare workspace
    GENERATE(3),        // generate source code
    COMPILE(4),         // compile source code
    TEST(5),            // run unit tests
    PACKAGE(6),         // create versionless artifacts (JAR, Docker image, etc.)
    VERIFY(7)           // integration tests, quality checks
}

/**
 * Release lifecycle phases matching JReleaser workflow
 * https://jreleaser.org/guide/latest/concepts/workflow.html
 */
enum class ReleaseLifecyclePhase(val order: Int) {
    DOWNLOAD(1),         // download external assets needed for release
    ASSEMBLE(2),         // prepare platform-specific distributions (jlink, native, etc.)
    CHANGELOG(3),        // generate release changelog from commits
    CHECKSUM(4),         // calculate SHA256 checksums for all files
    CATALOG(5),          // create Software Bill of Materials (SBOMs)
    SIGN(6),             // PGP sign artifacts and checksums
    DEPLOY(7),           // deploy staged artifacts to staging repos
    UPLOAD(8),           // upload distribution artifacts and files
    RELEASE(9),          // create release in remote Git repository (GitHub/GitLab)
    PREPARE(10),         // generate files for packagers (Homebrew formula, etc.)
    BUNDLE(11),          // process prepared files into specific bundles
    PUBLISH(12),         // publish bundle to registries (Maven Central, Docker Hub, etc.)
    ANNOUNCE(13)         // announce release to various channels (Twitter, Slack, etc.)
}

/**
 * Represents which lifecycle a target belongs to
 */
sealed class TargetLifecycle {
    data class Build(val phase: BuildLifecyclePhase) : TargetLifecycle()
    data class Development(val phase: DevelopmentLifecyclePhase) : TargetLifecycle()
    data class Release(val phase: ReleaseLifecyclePhase) : TargetLifecycle()
}