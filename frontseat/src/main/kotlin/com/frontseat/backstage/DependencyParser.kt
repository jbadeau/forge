package com.frontseat.backstage

import com.frontseat.nature.ProjectNature
import java.nio.file.Path

/**
 * Interface for parsing dependencies from project files.
 * Implementations can handle specific technologies (Maven, NPM, etc.)
 */
interface DependencyParser {
    
    /**
     * Check if this parser can handle the given project
     */
    fun canHandle(projectPath: Path, nature: ProjectNature): Boolean
    
    /**
     * Parse dependencies from the project
     * @return Set of internal dependency names that should be validated
     */
    fun parseDependencies(projectPath: Path): Set<String>
    
    /**
     * Get the name of this parser for error reporting
     */
    fun getParserName(): String
}

/**
 * Registry for dependency parsers
 */
class DependencyParserRegistry {
    private val parsers = mutableListOf<DependencyParser>()
    
    fun registerParser(parser: DependencyParser) {
        parsers.add(parser)
    }
    
    fun getParsers(): List<DependencyParser> = parsers.toList()
}