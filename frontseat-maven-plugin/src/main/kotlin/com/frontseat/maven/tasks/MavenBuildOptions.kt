package com.frontseat.maven.tasks

/**
 * Options for Maven build task
 */
data class MavenBuildOptions(
    val phase: String = "install",
    val project: String = ""  // Will be set to project name
) {
    companion object {
        fun fromMap(options: Map<String, Any>): MavenBuildOptions {
            return MavenBuildOptions(
                phase = options["phase"] as? String ?: "install",
                project = options["project"] as? String ?: ""
            )
        }

        fun defaults(projectName: String = ""): MavenBuildOptions =
            MavenBuildOptions(project = projectName)
    }
}