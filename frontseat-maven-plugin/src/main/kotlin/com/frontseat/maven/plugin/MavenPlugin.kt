package com.frontseat.maven.plugin

import com.frontseat.annotation.Plugin
import com.frontseat.plugin.FrontseatPlugin
import com.frontseat.workspace.Workspace

/**
 * Maven plugin that contributes the Maven nature
 */
@Plugin(id = "maven", name = "Maven Plugin")
class MavenPlugin : FrontseatPlugin {
    // No need to override id, name, or version anymore!
    // No need for manual registration if MavenNature has @AutoRegister
}