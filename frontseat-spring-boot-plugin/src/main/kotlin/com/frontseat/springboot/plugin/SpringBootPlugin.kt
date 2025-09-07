package com.frontseat.springboot.plugin

import com.frontseat.annotation.Plugin
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.workspace.Workspace

/**
 * Spring Boot plugin that contributes the Spring Boot nature
 */
@Plugin(id = "spring-boot", name = "Spring Boot Plugin")
class SpringBootPlugin : FrontseatPlugin {
    // No need to override id, name, or version anymore!
    // No need for manual registration if SpringBootNature has @AutoRegister
}