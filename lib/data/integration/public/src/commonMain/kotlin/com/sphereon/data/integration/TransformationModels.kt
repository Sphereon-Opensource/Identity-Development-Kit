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
    @SerialName("sourcePath")
    val sourcePath: String? = null,
    @SerialName("targetPath")
    val targetPath: String? = null,
    @SerialName("value")
    val value: JsonElement? = null,
    @SerialName("expression")
    val expression: String? = null,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)
