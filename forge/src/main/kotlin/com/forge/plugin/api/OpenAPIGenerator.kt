package com.forge.plugin.api

import org.openapitools.codegen.CodegenConfig
import org.openapitools.codegen.DefaultGenerator
import org.openapitools.codegen.ClientOptInput
import org.openapitools.codegen.config.CodegenConfigurator
import java.nio.file.Path

/**
 * OpenAPI Generator integration for Forge plugins
 * Allows using custom OpenAPI generators within the Forge plugin system
 */
interface OpenAPIGenerator : Generator {
    
    /**
     * Get the OpenAPI CodegenConfig implementation
     * This should return your custom OpenAPI generator class
     */
    fun getCodegenConfig(): CodegenConfig
    
    /**
     * Get the generator name for OpenAPI Generator
     */
    fun getGeneratorName(): String
    
    /**
     * Default implementation using OpenAPI Generator
     * Can be overridden for custom behavior
     */
    override suspend fun generate(
        workspace: WorkspaceContext,
        options: Map<String, Any>
    ): GeneratorResult {
        return try {
            val generator = OpenAPIGeneratorExecutor(
                codegenConfig = getCodegenConfig(),
                generatorName = getGeneratorName()
            )
            generator.execute(workspace, options)
        } catch (e: Exception) {
            workspace.logger.error("OpenAPI generation failed: ${e.message}")
            GeneratorResult(
                success = false,
                errors = listOf("OpenAPI generation failed: ${e.message}")
            )
        }
    }
    
    /**
     * Default validation for OpenAPI specs
     */
    override fun validate(options: Map<String, Any>): ValidationResult {
        val spec = options["spec"] as? String
        val outputDir = options["outputDir"] as? String
        
        val errors = mutableListOf<String>()
        
        if (spec.isNullOrBlank()) {
            errors.add("OpenAPI specification is required")
        }
        
        if (outputDir.isNullOrBlank()) {
            errors.add("Output directory is required")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.valid()
        } else {
            ValidationResult.invalid(*errors.toTypedArray())
        }
    }
}

/**
 * Executor that bridges Forge generators with OpenAPI Generator
 */
class OpenAPIGeneratorExecutor(
    private val codegenConfig: CodegenConfig,
    private val generatorName: String
) {
    
    suspend fun execute(
        workspace: WorkspaceContext,
        options: Map<String, Any>
    ): GeneratorResult {
        workspace.logger.info("Running OpenAPI Generator: $generatorName")
        
        val spec = options["spec"] as? String 
            ?: return GeneratorResult(false, errors = listOf("OpenAPI spec is required"))
        
        val outputDir = options["outputDir"] as? String 
            ?: return GeneratorResult(false, errors = listOf("Output directory is required"))
        
        val specPath = resolveSpecPath(workspace, spec)
        val absoluteOutputDir = workspace.root.resolve(outputDir)
        
        try {
            // Configure OpenAPI Generator
            val configurator = CodegenConfigurator()
                .setGeneratorName(generatorName)
                .setInputSpec(specPath)
                .setOutputDir(absoluteOutputDir.toString())
            
            // Apply additional properties from Forge options
            applyAdditionalProperties(configurator, options)
            
            // Create and run generator
            val input: ClientOptInput = configurator.toClientOptInput()
            val generator = DefaultGenerator()
            val files = generator.opts(input).generate()
            
            workspace.logger.info("Generated ${files.size} files")
            
            // Convert to Forge GeneratorResult
            return GeneratorResult(
                success = true,
                files = files.map { file ->
                    GeneratedFile(
                        path = workspace.root.relativize(Path.of(file.toString())).toString(),
                        action = FileAction.CREATE,
                        content = null // Content already written to disk
                    )
                },
                messages = listOf("Generated ${files.size} files using $generatorName"),
                nextSteps = getNextSteps(generatorName, outputDir)
            )
            
        } catch (e: Exception) {
            workspace.logger.error("OpenAPI generation failed: ${e.message}")
            return GeneratorResult(
                success = false,
                errors = listOf("OpenAPI generation failed: ${e.message}")
            )
        }
    }
    
    private fun resolveSpecPath(workspace: WorkspaceContext, spec: String): String {
        return if (spec.startsWith("http://") || spec.startsWith("https://")) {
            spec // URL
        } else {
            workspace.root.resolve(spec).toString() // File path
        }
    }
    
    private fun applyAdditionalProperties(
        configurator: CodegenConfigurator,
        options: Map<String, Any>
    ) {
        val additionalProperties = mutableMapOf<String, Any>()
        
        // Map common Forge options to OpenAPI Generator properties
        options["packageName"]?.let { additionalProperties["packageName"] = it }
        options["apiPackage"]?.let { additionalProperties["apiPackage"] = it }
        options["modelPackage"]?.let { additionalProperties["modelPackage"] = it }
        options["invokerPackage"]?.let { additionalProperties["invokerPackage"] = it }
        options["groupId"]?.let { additionalProperties["groupId"] = it }
        options["artifactId"]?.let { additionalProperties["artifactId"] = it }
        options["artifactVersion"]?.let { additionalProperties["artifactVersion"] = it }
        options["library"]?.let { additionalProperties["library"] = it }
        
        // Apply custom properties
        options["additionalProperties"]?.let { props ->
            if (props is Map<*, *>) {
                props.forEach { (k, v) ->
                    if (k is String && v != null) {
                        additionalProperties[k] = v
                    }
                }
            }
        }
        
        if (additionalProperties.isNotEmpty()) {
            configurator.setAdditionalProperties(additionalProperties)
        }
        
        // Apply global properties
        options["globalProperties"]?.let { props ->
            if (props is Map<*, *>) {
                val globalProps = props.entries.associate { (k, v) ->
                    k.toString() to v.toString()
                }
                configurator.setGlobalProperties(globalProps)
            }
        }
        
        // Apply system properties
        options["systemProperties"]?.let { props ->
            if (props is Map<*, *>) {
                props.forEach { (k, v) ->
                    if (k is String && v is String) {
                        System.setProperty(k, v)
                    }
                }
            }
        }
    }
    
    private fun getNextSteps(generatorName: String, outputDir: String): List<String> {
        return when {
            generatorName.contains("java") -> listOf(
                "Review generated code in $outputDir",
                "Add generated sources to your build path",
                "Configure your build tool to compile the generated code",
                "Update your dependencies if needed"
            )
            generatorName.contains("kotlin") -> listOf(
                "Review generated Kotlin code in $outputDir",
                "Add generated sources to your Kotlin source sets",
                "Update your dependencies for Kotlin coroutines if using async clients"
            )
            generatorName.contains("typescript") || generatorName.contains("javascript") -> listOf(
                "Review generated code in $outputDir",
                "Install dependencies: npm install",
                "Import the generated API client in your application"
            )
            generatorName.contains("python") -> listOf(
                "Review generated Python code in $outputDir",
                "Install the package: pip install -e $outputDir",
                "Import the generated client in your Python code"
            )
            else -> listOf(
                "Review generated code in $outputDir",
                "Follow the README.md in the generated directory for usage instructions"
            )
        }
    }
}

/**
 * Schema for OpenAPI-based generators in Forge plugins
 */
data class OpenAPIGeneratorSchema(
    val generatorName: String,              // OpenAPI generator name (e.g., "java", "typescript-axios")
    val codegenConfig: String,              // Class name of CodegenConfig implementation
    val schema: JsonSchema,                 // JSON schema for generator options
    val description: String = "",
    val supportedSpecs: List<String> = listOf("openapi", "swagger"), // Supported spec formats
    val examples: List<OpenAPIGeneratorExample> = emptyList(),
    val templates: Map<String, String> = emptyMap() // Custom template overrides
) {
    /**
     * Convert to GeneratorSchema for Forge integration
     */
    fun toGeneratorSchema(): GeneratorSchema {
        return GeneratorSchema(
            implementation = codegenConfig,
            schema = schema,
            description = description,
            examples = examples.map { 
                GeneratorExample(it.name, it.description, it.command) 
            }
        )
    }
}

data class OpenAPIGeneratorExample(
    val name: String,
    val description: String,
    val command: String,
    val spec: String? = null,               // Example OpenAPI spec
    val options: Map<String, Any> = emptyMap()
)

/**
 * Base class for creating Forge plugins that wrap OpenAPI generators
 */
abstract class BaseOpenAPIPlugin : ForgePlugin {
    
    /**
     * Register OpenAPI generators provided by this plugin
     */
    abstract fun getOpenAPIGenerators(): Map<String, OpenAPIGeneratorSchema>
    
    /**
     * Default implementation that converts OpenAPI generators to Forge generators
     */
    override fun getGenerators(): Map<String, GeneratorSchema> {
        return getOpenAPIGenerators().mapValues { it.value.toGeneratorSchema() }
    }
    
    /**
     * Helper method to create OpenAPI generator schemas
     */
    protected fun createOpenAPIGeneratorSchema(
        generatorName: String,
        codegenConfig: String,
        description: String,
        schemaBuilder: JsonSchemaBuilder.() -> Unit
    ): OpenAPIGeneratorSchema {
        val schema = JsonSchemaBuilder().apply(schemaBuilder).build()
        
        return OpenAPIGeneratorSchema(
            generatorName = generatorName,
            codegenConfig = codegenConfig,
            schema = schema,
            description = description
        )
    }
}

/**
 * Builder for JSON schemas with OpenAPI generator common properties
 */
class JsonSchemaBuilder {
    private val properties = mutableMapOf<String, JsonProperty>()
    private val required = mutableListOf<String>()
    
    fun spec(description: String = "OpenAPI specification file path or URL") {
        properties["spec"] = JsonProperty(
            type = "string",
            description = description
        )
        required.add("spec")
    }
    
    fun outputDir(default: String = "generated", description: String = "Output directory for generated code") {
        properties["outputDir"] = JsonProperty(
            type = "string",
            description = description,
            default = default
        )
    }
    
    fun packageName(description: String = "Package name for generated code") {
        properties["packageName"] = JsonProperty(
            type = "string",
            description = description
        )
    }
    
    fun apiPackage(description: String = "Package name for API classes") {
        properties["apiPackage"] = JsonProperty(
            type = "string", 
            description = description
        )
    }
    
    fun modelPackage(description: String = "Package name for model classes") {
        properties["modelPackage"] = JsonProperty(
            type = "string",
            description = description
        )
    }
    
    fun library(options: List<String>, default: String? = null, description: String = "Library variant to use") {
        properties["library"] = JsonProperty(
            type = "string",
            description = description,
            enum = options,
            default = default
        )
    }
    
    fun custom(name: String, type: String, description: String, default: Any? = null, enum: List<Any>? = null) {
        properties[name] = JsonProperty(
            type = type,
            description = description,
            default = default,
            enum = enum
        )
    }
    
    fun required(vararg names: String) {
        required.addAll(names)
    }
    
    fun build(): JsonSchema {
        return JsonSchema(
            type = "object",
            properties = properties.toMap(),
            required = required.toList()
        )
    }
}