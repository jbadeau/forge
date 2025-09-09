package com.frontseat.maven.plugin

import com.frontseat.executor.plugins.Plugin
import com.frontseat.maven.commons.MavenNatureIds
import com.frontseat.executor.plugins.FrontseatPlugin
import com.frontseat.workspace.Workspace

/**
 * Maven plugin that contributes the Maven nature
 */
@Plugin(id = MavenNatureIds.MAVEN, name = "Maven Plugin")
class MavenPlugin : FrontseatPlugin {
    // No need to override id, name, or version anymore!
}