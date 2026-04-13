package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable

/**
 * Declares the resource type(s) and attribute schema a command targets.
 *
 * This is a compile-time declaration (schema-level). Actual resource IDs and
 * attribute values are resolved at runtime by [ResourceMappingProvider] implementations.
 *
 * Used by:
 * - Policy: Cedar entity schema generation, OPA input structure derivation
 * - Audit: resource type classification in audit events
 * - Documentation: "what resources does command X touch?"
 */
@Serializable
data class ResourceTargetDescriptor(
    /** Domain resource type, e.g., "kms.key", "oauth2.access_token", "eidas.signature" */
    val resourceType: String,
    /** Attribute schema: names and kinds of attributes relevant for policy decisions */
    val attributeSchema: Set<ResourceAttribute> = emptySet(),
    /** Parent resource types for hierarchical authorization (dual-check).
     *  E.g., kms.key operations may also require authorization on kms.provider. */
    val parentResources: List<String> = emptyList(),
) {
    companion object {
        val UNSPECIFIED = ResourceTargetDescriptor("unspecified")
    }
}

/**
 * Declares a single resource attribute relevant for policy decisions.
 */
@Serializable
data class ResourceAttribute(
    /** Attribute name as it appears in policy resource properties */
    val name: String,
    /** Whether this attribute must always be present in the policy resource */
    val required: Boolean = false,
    /** Classification of this attribute for policy evaluation */
    val kind: AttributeKind = AttributeKind.IDENTIFIER,
)

/**
 * Classification of a resource attribute for policy evaluation.
 */
@Serializable
enum class AttributeKind {
    /** Scopes/identifies the resource (provider_id, alias, kid, client_id) */
    IDENTIFIER,

    /** Policy-evaluable constraint value (max_lifetime_days, scope, algorithm) */
    CONSTRAINT,

    /** Framework-injected environmental context (tenant_id — added by transport, not from args) */
    CONTEXT,
}

// ========== DSL ==========

fun resourceTarget(
    resourceType: String,
    block: ResourceTargetBuilder.() -> Unit = {},
): ResourceTargetDescriptor = ResourceTargetBuilder(resourceType).apply(block).build()

class ResourceTargetBuilder(
    private val resourceType: String,
) {
    private val attributes = mutableSetOf<ResourceAttribute>()
    private val parents = mutableListOf<String>()

    fun parent(resourceType: String) {
        parents.add(resourceType)
    }

    fun identifier(
        name: String,
        required: Boolean = false,
    ) {
        attributes.add(ResourceAttribute(name, required, AttributeKind.IDENTIFIER))
    }

    fun constraint(
        name: String,
        required: Boolean = false,
    ) {
        attributes.add(ResourceAttribute(name, required, AttributeKind.CONSTRAINT))
    }

    fun context(name: String) {
        attributes.add(ResourceAttribute(name, false, AttributeKind.CONTEXT))
    }

    fun build() = ResourceTargetDescriptor(resourceType, attributes, parents)
}
