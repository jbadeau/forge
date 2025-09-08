package com.frontseat.springboot.tasks

/**
 * Options for Spring Boot start task
 */
data class SpringBootStartOptions(
    val profiles: String = "dev",
    val port: Int = 8080,
    val debug: Boolean = false,
    val debugPort: Int = 5005,
    val jvmArgs: List<String> = emptyList(),
    val args: List<String> = emptyList()
) {
    companion object {
        fun fromMap(options: Map<String, Any>): SpringBootStartOptions {
            return SpringBootStartOptions(
                profiles = options["profiles"] as? String ?: "dev",
                port = options["port"] as? Int ?: 8080,
                debug = options["debug"] as? Boolean ?: false,
                debugPort = options["debugPort"] as? Int ?: 5005,
                jvmArgs = (options["jvmArgs"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                args = (options["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
        }
        
        fun defaults(): SpringBootStartOptions = SpringBootStartOptions()
    }
}