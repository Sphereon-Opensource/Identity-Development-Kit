package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A concrete resource instance with type, identity, and attribute values.
 *
 * This is the runtime counterpart to [ResourceTargetDescriptor] (which is schema-level).
 * Produced by [ResourceMappingProvider] implementations on input DTOs, and consumed by
 * the EDK policy layer to build PolicyResource/AuthZenResource for evaluation.
 */
@Serializable
data class ResourceInstance(
    val resourceType: String,
    val resourceId: String? = null,
    val attributes: Map<String, JsonElement> = emptyMap(),
)

// ========== DSL ==========

fun resourceInstances(block: ResourceInstanceListBuilder.() -> Unit): List<ResourceInstance> = ResourceInstanceListBuilder().apply(block).build()

class ResourceInstanceListBuilder {
    private val instances = mutableListOf<ResourceInstance>()

    fun resource(
        type: String,
        block: ResourceInstanceBuilder.() -> Unit,
    ) {
        instances.add(ResourceInstanceBuilder(type).apply(block).build())
    }

    fun build(): List<ResourceInstance> = instances.toList()
}

class ResourceInstanceBuilder(
    private val type: String,
) {
    private var id: String? = null
    private val attrs = mutableMapOf<String, JsonElement>()

    fun id(resourceId: String) {
        id = resourceId
    }

    fun attr(
        name: String,
        value: String,
    ) {
        attrs[name] = JsonPrimitive(value)
    }

    fun attr(
        name: String,
        value: Int,
    ) {
        attrs[name] = JsonPrimitive(value)
    }

    fun attr(
        name: String,
        value: Long,
    ) {
        attrs[name] = JsonPrimitive(value)
    }

    fun attr(
        name: String,
        value: Boolean,
    ) {
        attrs[name] = JsonPrimitive(value)
    }

    fun attrIfNotNull(
        name: String,
        value: String?,
    ) {
        value?.let { attr(name, it) }
    }

    fun attrIfNotNull(
        name: String,
        value: Int?,
    ) {
        value?.let { attr(name, it) }
    }

    fun attrIfNotNull(
        name: String,
        value: Long?,
    ) {
        value?.let { attr(name, it) }
    }

    fun build() = ResourceInstance(type, id, attrs.toMap())
}
