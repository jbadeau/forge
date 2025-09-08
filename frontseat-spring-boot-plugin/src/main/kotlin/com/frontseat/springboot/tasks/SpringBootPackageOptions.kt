package com.frontseat.springboot.tasks

/**
 * Options for Spring Boot package (containerize) task
 */
data class SpringBootPackageOptions(
    val imageName: String = "",  // Default to project name (set by task)
    val imageTag: String = "latest",
    val builder: String = "paketobuildpacks/builder:base",
    val runImage: String? = null,
    val env: Map<String, String> = emptyMap(),
    val publish: Boolean = false,
    val registry: String? = null,
    val registryUsername: String? = null,
    val registryPassword: String? = null,
    val args: List<String> = emptyList()
) {
    companion object {
        fun fromMap(options: Map<String, Any>): SpringBootPackageOptions {
            return SpringBootPackageOptions(
                imageName = options["imageName"] as? String ?: "",
                imageTag = options["imageTag"] as? String ?: "latest",
                builder = options["builder"] as? String ?: "paketobuildpacks/builder:base",
                runImage = options["runImage"] as? String,
                env = (options["env"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    k.toString() to v.toString()
                }?.toMap() ?: emptyMap(),
                publish = options["publish"] as? Boolean ?: false,
                registry = options["registry"] as? String,
                registryUsername = options["registryUsername"] as? String,
                registryPassword = options["registryPassword"] as? String,
                args = (options["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
        }
        
        fun defaults(projectName: String = ""): SpringBootPackageOptions = 
            SpringBootPackageOptions(imageName = projectName)
    }
}