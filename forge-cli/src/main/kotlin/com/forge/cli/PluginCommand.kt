package com.forge.cli

import com.forge.plugin.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice

/**
 * Main plugin command with subcommands
 */
class PluginCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Manage Forge plugins"
    override fun run() {
        // Parent command does nothing, delegates to subcommands
    }

    init {
        subcommands(
            ListCommand(),
            InstallCommand(),
            UninstallCommand(),
            UpdateCommand(),
            SearchCommand(),
            InfoCommand()
        )
    }
}

/**
 * List installed plugins
 */
class ListCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "List installed plugins"
    private val verbose by option("-v", "--verbose", help = "Show detailed information").flag()
    
    override fun run() {
        val pluginManager = PluginManager()
        val cachedPlugins = pluginManager.listInstalled()
        
        if (cachedPlugins.isEmpty()) {
            echo("No plugins installed")
            return
        }
        
        echo("Installed plugins:")
        echo("=".repeat(50))
        
        cachedPlugins.forEach { plugin ->
            if (verbose) {
                echo("📦 ${plugin.id}@${plugin.version}")
                echo("   Path: ${plugin.path}")
                echo("")
            } else {
                echo("📦 ${plugin.id}@${plugin.version}")
            }
        }
        
        echo("")
        echo("Total: ${cachedPlugins.size} plugin(s)")
    }
}

/**
 * Install a plugin
 */
class InstallCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Install a plugin"
    private val pluginSpec by argument(
        name = "plugin",
        help = "Plugin specification (e.g., com.forge.spring-boot@2.1.0, github:myorg/custom-plugin)"
    )
    
    override fun run() {
        val pluginManager = PluginManager()
        
        try {
            val spec = PluginSpec.parse(pluginSpec)
            echo("📦 Installing plugin: ${spec.toCoordinateString()}")
            
            val jarPath = pluginManager.installPlugin(spec)
            echo("✅ Successfully installed plugin to: $jarPath")
            
            // Try to load and show plugin info
            try {
                val plugin = pluginManager.loadPlugin(spec)
                echo("")
                echo("Plugin Information:")
                echo("  Name: ${plugin.metadata.name}")
                echo("  Version: ${plugin.metadata.version}")
                echo("  Description: ${plugin.metadata.description}")
                if (plugin.metadata.author.isNotEmpty()) {
                    echo("  Author: ${plugin.metadata.author}")
                }
                if (plugin.metadata.homepage.isNotEmpty()) {
                    echo("  Homepage: ${plugin.metadata.homepage}")
                }
            } catch (e: Exception) {
                echo("⚠️  Plugin installed but failed to load metadata: ${e.message}")
            }
            
        } catch (e: Exception) {
            echo("❌ Failed to install plugin: ${e.message}", err = true)
            throw CliktError("Plugin installation failed")
        }
    }
}

/**
 * Uninstall a plugin
 */
class UninstallCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Uninstall a plugin"
    private val pluginId by argument(
        name = "plugin",
        help = "Plugin ID to uninstall (e.g., com.forge.spring-boot)"
    )
    
    override fun run() {
        val pluginManager = PluginManager()
        
        try {
            // Find cached plugin
            val cachedPlugins = pluginManager.listInstalled()
            val pluginToRemove = cachedPlugins.find { it.id == pluginId }
            
            if (pluginToRemove == null) {
                echo("❌ Plugin not found: $pluginId")
                return
            }
            
            echo("🗑️  Uninstalling plugin: ${pluginToRemove.id}@${pluginToRemove.version}")
            
            // Unload if currently loaded
            pluginManager.unloadPlugin(pluginId)
            
            // Remove from cache
            pluginToRemove.path.toFile().delete()
            
            echo("✅ Successfully uninstalled plugin: $pluginId")
            
        } catch (e: Exception) {
            echo("❌ Failed to uninstall plugin: ${e.message}", err = true)
            throw CliktError("Plugin uninstallation failed")
        }
    }
}

/**
 * Update plugins
 */
class UpdateCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Update installed plugins"
    private val pluginId by option("--plugin", help = "Specific plugin to update")
    
    override fun run() {
        val pluginManager = PluginManager()
        
        if (pluginId != null) {
            echo("🔄 Updating plugin: $pluginId")
            // TODO: Implement single plugin update
            echo("⚠️  Single plugin update not yet implemented")
        } else {
            echo("🔄 Updating all plugins...")
            // TODO: Implement bulk update
            echo("⚠️  Bulk plugin update not yet implemented")
        }
    }
}

/**
 * Search for available plugins
 */
class SearchCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Search for available plugins"
    private val query by argument(
        name = "query",
        help = "Search query"
    )
    
    override fun run() {
        echo("🔍 Searching for plugins matching: $query")
        // TODO: Implement plugin search (would require a plugin registry)
        echo("⚠️  Plugin search not yet implemented")
        echo("")
        echo("Available official plugins:")
        echo("  • com.forge.js - JavaScript/TypeScript support")
        echo("  • com.forge.maven - Maven/Java support") 
        echo("  • com.forge.gradle - Gradle support")
        echo("  • com.forge.docker - Docker support")
        echo("  • com.forge.go - Go support")
        echo("  • com.forge.rust - Rust support")
        echo("  • com.forge.python - Python support")
        echo("  • com.forge.spring-boot - Spring Boot support")
        echo("  • com.forge.quarkus - Quarkus support")
    }
}

/**
 * Show plugin information
 */
class InfoCommand : CliktCommand() {
    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Show information about a plugin"
    private val pluginSpec by argument(
        name = "plugin",
        help = "Plugin specification"
    )
    
    override fun run() {
        val pluginManager = PluginManager()
        
        try {
            val spec = PluginSpec.parse(pluginSpec)
            echo("📦 Plugin: ${spec.toCoordinateString()}")
            echo("")
            
            // Try to load plugin to get metadata
            val plugin = pluginManager.loadPlugin(spec)
            
            echo("Information:")
            echo("  ID: ${plugin.metadata.id}")
            echo("  Name: ${plugin.metadata.name}")
            echo("  Version: ${plugin.metadata.version}")
            echo("  Description: ${plugin.metadata.description}")
            echo("  File Pattern: ${plugin.metadata.createNodesPattern}")
            echo("  Supported Files: ${plugin.metadata.supportedFiles.joinToString(", ")}")
            
            if (plugin.metadata.author.isNotEmpty()) {
                echo("  Author: ${plugin.metadata.author}")
            }
            
            if (plugin.metadata.homepage.isNotEmpty()) {
                echo("  Homepage: ${plugin.metadata.homepage}")
            }
            
            if (plugin.metadata.tags.isNotEmpty()) {
                echo("  Tags: ${plugin.metadata.tags.joinToString(", ")}")
            }
            
        } catch (e: Exception) {
            echo("❌ Failed to load plugin info: ${e.message}", err = true)
            throw CliktError("Plugin info failed")
        }
    }
}

