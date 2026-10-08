/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.integration

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Connector-neutral description of a resource shape.
 *
 * Inventory, governance, workflow routing, forms, credential issuance, and connectors can all use
 * this descriptor without implying that a connector route exists.
 */
@JsExportCompat
@Serializable
data class ResourceDescriptor(
    @SerialName("resourceDescriptorId")
    val resourceDescriptorId: ResourceDescriptorId,
    @SerialName("displayName")
    val displayName: String,
    @SerialName("resourceKind")
    val resourceKind: ResourceKind,
    @SerialName("representationKind")
    val representationKind: RepresentationKind,
    @SerialName("description")
    val description: String? = null,
    @SerialName("shapeKind")
    val shapeKind: ShapeKind? = null,
    @SerialName("contract")
    val contract: ContractRef? = null,
    @SerialName("fields")
    val fields: List<FieldDescriptor> = emptyList(),
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("createdAt")
    val createdAt: Instant? = null,
    @SerialName("updatedAt")
    val updatedAt: Instant? = null,
)

@JsExportCompat
@Serializable
data class FieldDescriptor(
    @SerialName("fieldDescriptorId")
    val fieldDescriptorId: FieldDescriptorId? = null,
    @SerialName("fieldPath")
    val fieldPath: String,
    @SerialName("valueType")
    val valueType: String,
    @SerialName("displayName")
    val displayName: String? = null,
    @SerialName("required")
    val required: Boolean = false,
    @SerialName("multiValued")
    val multiValued: Boolean = false,
    @SerialName("semanticAttributeId")
    val semanticAttributeId: String? = null,
    @SerialName("sensitivity")
    val sensitivity: String? = null,
    @SerialName("retention")
    val retention: RetentionSpec? = null,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

@JsExportCompat
@Serializable
data class ContractRef(
    @SerialName("contractKind")
    val contractKind: ContractKind,
    @SerialName("uri")
    val uri: String? = null,
    @SerialName("version")
    val version: String? = null,
    @SerialName("contentHash")
    val contentHash: String? = null,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)
