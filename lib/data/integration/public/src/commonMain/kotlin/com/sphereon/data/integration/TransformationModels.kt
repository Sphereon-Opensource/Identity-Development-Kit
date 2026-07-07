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
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

@JsExportCompat
@Serializable
enum class TransformationLanguage {
    ATTRIBUTE_MAPPER,
    JSONATA,
    JQ,
    CEL,
    SQL,
    TEMPLATE,
    CUSTOM,
}

/**
 * Versionable transformation description shared across connectors, forms, workflows, credential
 * issuance, inventory normalization, and semantic import/export.
 */
@JsExportCompat
@Serializable
data class TransformationDefinition(
    @SerialName("transformationId")
    val transformationId: TransformationId,
    @SerialName("displayName")
    val displayName: String,
    @SerialName("language")
    val language: TransformationLanguage,
    @SerialName("description")
    val description: String? = null,
    @SerialName("sourceDescriptorId")
    val sourceDescriptorId: ResourceDescriptorId? = null,
    @SerialName("targetDescriptorId")
    val targetDescriptorId: ResourceDescriptorId? = null,
    @SerialName("steps")
    val steps: List<TransformationStep> = emptyList(),
    @SerialName("expression")
    val expression: String? = null,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("createdAt")
    val createdAt: Instant? = null,
    @SerialName("updatedAt")
    val updatedAt: Instant? = null,
)

@JsExportCompat
@Serializable
enum class TransformationStepType {
    MAP,
    CONSTANT,
    DEFAULT,
    REQUIRE,
    REDACT,
    HASH,
    LOOKUP,
    CUSTOM,
}

@JsExportCompat
@Serializable
data class TransformationStep(
    @SerialName("stepType")
    val stepType: TransformationStepType,
    /**
     * Dot-separated object field path (optionally prefixed with `$` or `$.`) into the input document.
     * Addresses object fields only; arrays are out of scope for v1 and descending into one is an error,
     * not a capability.
     */
    @SerialName("sourcePath")
    val sourcePath: String? = null,
    /**
     * Dot-separated object field path (optionally prefixed with `$` or `$.`) into the output document.
     * Addresses object fields only; arrays are out of scope for v1 and descending into one is an error,
     * not a capability.
     */
    @SerialName("targetPath")
    val targetPath: String? = null,
    @SerialName("value")
    val value: JsonElement? = null,
    @SerialName("expression")
    val expression: String? = null,
    /**
     * When true, a step whose source (MAP) or target leaf (REDACT) is absent succeeds as a no-op
     * instead of failing with a path-not-found error. Defaults to false so a mismatched path fails
     * loudly rather than letting the affected value pass through untransformed. Only governs a genuinely
     * absent leaf whose parent is addressable; an unaddressable path shape always fails regardless.
     */
    @SerialName("allowMissing")
    val allowMissing: Boolean = false,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)
