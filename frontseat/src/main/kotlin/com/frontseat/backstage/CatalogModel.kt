package com.frontseat.backstage

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Backstage Catalog Entity Model
 * Based on https://backstage.io/docs/features/software-catalog/descriptor-format
 * 
 * Backstage entities follow the Kubernetes Custom Resource pattern,
 * with apiVersion, kind, metadata, and spec fields.
 */

/**
 * Base class for all Backstage catalog entities
 * Follows Kubernetes Custom Resource Definition (CRD) pattern
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "kind",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Component::class, name = "Component"),
    JsonSubTypes.Type(value = System::class, name = "System"),
    JsonSubTypes.Type(value = Domain::class, name = "Domain"),
    JsonSubTypes.Type(value = Api::class, name = "API"),
    JsonSubTypes.Type(value = Resource::class, name = "Resource"),
    JsonSubTypes.Type(value = Location::class, name = "Location"),
    JsonSubTypes.Type(value = User::class, name = "User"),
    JsonSubTypes.Type(value = Group::class, name = "Group"),
    JsonSubTypes.Type(value = Template::class, name = "Template"),
    JsonSubTypes.Type(value = CustomEntity::class, name = "Custom")
)
sealed class CatalogEntity {
    abstract val apiVersion: String
    abstract val kind: String
    abstract val metadata: EntityMetadata
    abstract val spec: EntitySpec?
    
    // Optional status field (similar to K8s resources)
    open val status: EntityStatus? = null
}

/**
 * Entity metadata common to all entities
 */
data class EntityMetadata(
    val name: String,
    val namespace: String? = "default",
    val title: String? = null,
    val description: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val links: List<EntityLink> = emptyList()
)

/**
 * Entity link
 */
data class EntityLink(
    val url: String,
    val title: String? = null,
    val icon: String? = null,
    val type: String? = null
)

/**
 * Base interface for entity specifications
 */
interface EntitySpec

/**
 * Component entity - represents a software component
 */
data class Component(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "Component",
    override val metadata: EntityMetadata,
    override val spec: ComponentSpec
) : CatalogEntity()

data class ComponentSpec(
    val type: String, // service, website, library, etc.
    val lifecycle: String, // production, experimental, deprecated
    val owner: String, // Reference to a User or Group entity
    val system: String? = null, // Reference to a System entity
    val subcomponentOf: String? = null, // Reference to another Component
    val providesApis: List<String> = emptyList(), // References to API entities
    val consumesApis: List<String> = emptyList(), // References to API entities
    val dependsOn: List<String> = emptyList() // References to Component or Resource entities
) : EntitySpec

/**
 * System entity - a collection of components and resources
 */
data class System(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "System",
    override val metadata: EntityMetadata,
    override val spec: SystemSpec
) : CatalogEntity()

data class SystemSpec(
    val owner: String, // Reference to a User or Group entity
    val domain: String? = null // Reference to a Domain entity
) : EntitySpec

/**
 * Domain entity - relates entities and systems to business units
 */
data class Domain(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "Domain",
    override val metadata: EntityMetadata,
    override val spec: DomainSpec
) : CatalogEntity()

data class DomainSpec(
    val owner: String // Reference to a User or Group entity
) : EntitySpec

/**
 * API entity - represents an API exposed by a component
 */
data class Api(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "API",
    override val metadata: EntityMetadata,
    override val spec: ApiSpec
) : CatalogEntity()

data class ApiSpec(
    val type: String, // openapi, asyncapi, graphql, grpc, etc.
    val lifecycle: String, // production, experimental, deprecated
    val owner: String, // Reference to a User or Group entity
    val definition: String, // Inline or reference to API definition
    val system: String? = null // Reference to a System entity
) : EntitySpec

/**
 * Resource entity - represents infrastructure or external resources
 */
data class Resource(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "Resource",
    override val metadata: EntityMetadata,
    override val spec: ResourceSpec
) : CatalogEntity()

data class ResourceSpec(
    val type: String, // database, storage, messaging, etc.
    val owner: String, // Reference to a User or Group entity
    val system: String? = null, // Reference to a System entity
    val dependsOn: List<String> = emptyList() // References to other Resource entities
) : EntitySpec

/**
 * Location entity - represents a source location for catalog entities
 */
data class Location(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "Location",
    override val metadata: EntityMetadata,
    override val spec: LocationSpec
) : CatalogEntity()

data class LocationSpec(
    val type: String, // url, file, etc.
    val target: String? = null, // URL or file path
    val targets: List<String>? = null // Multiple targets
) : EntitySpec

/**
 * User entity - represents a person
 */
data class User(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "User",
    override val metadata: EntityMetadata,
    override val spec: UserSpec
) : CatalogEntity()

data class UserSpec(
    val profile: UserProfile? = null,
    val memberOf: List<String> = emptyList() // References to Group entities
) : EntitySpec

data class UserProfile(
    val displayName: String? = null,
    val email: String? = null,
    val picture: String? = null
)

/**
 * Group entity - represents a team or organizational unit
 */
data class Group(
    override val apiVersion: String = "backstage.io/v1alpha1",
    override val kind: String = "Group",
    override val metadata: EntityMetadata,
    override val spec: GroupSpec
) : CatalogEntity()

data class GroupSpec(
    val type: String, // team, business-unit, product-area, etc.
    val profile: GroupProfile? = null,
    val parent: String? = null, // Reference to parent Group
    val children: List<String> = emptyList(), // References to child Groups
    val members: List<String> = emptyList() // References to User entities
) : EntitySpec

data class GroupProfile(
    val displayName: String? = null,
    val email: String? = null,
    val picture: String? = null
)

/**
 * Template entity - represents a software template for scaffolding
 */
data class Template(
    override val apiVersion: String = "scaffolder.backstage.io/v1beta3",
    override val kind: String = "Template",
    override val metadata: EntityMetadata,
    override val spec: TemplateSpec
) : CatalogEntity()

data class TemplateSpec(
    val type: String,
    val parameters: Any? = null,
    val steps: List<Any> = emptyList(),
    val output: Map<String, Any>? = null
) : EntitySpec

/**
 * Custom entity for extending the catalog with domain-specific types
 * This allows for custom CRDs similar to Kubernetes
 */
data class CustomEntity(
    override val apiVersion: String,
    override val kind: String,
    override val metadata: EntityMetadata,
    override val spec: CustomEntitySpec?
) : CatalogEntity()

data class CustomEntitySpec(
    val data: Map<String, Any> = emptyMap()
) : EntitySpec

/**
 * Entity status (similar to Kubernetes resource status)
 */
data class EntityStatus(
    val items: List<StatusItem> = emptyList()
)

data class StatusItem(
    val type: String,
    val level: String, // info, warning, error
    val message: String,
    val error: Map<String, Any>? = null
)

/**
 * Common Backstage annotations that can be used for nature detection
 */
object BackstageAnnotations {
    // Build system annotations
    const val BUILD_TOOL = "backstage.io/build-tool"
    const val BUILD_SCRIPT = "backstage.io/build-script"
    
    // Technology stack annotations
    const val TECH_STACK = "backstage.io/tech-stack"
    const val LANGUAGE = "backstage.io/language"
    const val FRAMEWORK = "backstage.io/framework"
    
    // CI/CD annotations
    const val CI_PIPELINE = "backstage.io/ci-pipeline"
    const val CD_PIPELINE = "backstage.io/cd-pipeline"
    
    // Source control annotations
    const val SOURCE_LOCATION = "backstage.io/source-location"
    const val MANAGED_BY_LOCATION = "backstage.io/managed-by-location"
    
    // Custom Frontseat annotations for nature detection and overrides
    const val FRONTSEAT_NATURES = "frontseat.io/natures"  // Manual override of inferred natures
}

/**
 * Common project nature IDs that can be inferred
 */
object ProjectNatureIds {
    const val MAVEN = "maven"
    const val GRADLE = "gradle"
    const val NPM = "npm"
    const val YARN = "yarn"
    const val PNPM = "pnpm"
    const val RUST = "rust"
    const val PYTHON = "python"
    const val DOCKER = "docker"
    const val KUBERNETES = "kubernetes"
    const val TERRAFORM = "terraform"
    const val SPRING_BOOT = "spring-boot"
    const val REACT = "react"
    const val ANGULAR = "angular"
    const val VUE = "vue"
    const val NEXTJS = "nextjs"
    const val NODEJS = "nodejs"
    const val DOTNET = "dotnet"
    const val CUSTOM = "custom"
}