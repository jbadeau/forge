package com.frontseat.maven.tasks

/**
 * Options for Maven build task
 */
data class MavenBuildOptions(
    val phase: String = "install",
    val project: String = "",  // Will be set to project name
    val inputs: List<String> = listOf("pom.xml", "src/main/**"),
    val outputs: List<String> = listOf("target/classes/**", "target/*.jar", "target/*.war"),
    val cache: Boolean = true
) {
    companion object {
        fun fromMap(options: Map<String, Any>): MavenBuildOptions {
            return MavenBuildOptions(
                phase = options["phase"] as? String ?: "install",
                project = options["project"] as? String ?: "",
                inputs = (options["inputs"] as? List<*>)?.map { it.toString() } 
                    ?: listOf("pom.xml", "src/main/**"),
                outputs = (options["outputs"] as? List<*>)?.map { it.toString() } 
                    ?: listOf("target/classes/**", "target/*.jar", "target/*.war"),
                cache = options["cache"] as? Boolean ?: true
            )
        }
        
        fun defaults(projectName: String = ""): MavenBuildOptions = 
            MavenBuildOptions(project = projectName)
    }
}