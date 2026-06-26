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

/**
 * How, if at all, data touched by an integration route may be retained or projected.
 */
@JsExportCompat
@Serializable
data class MaterializationPolicy(
    @SerialName("mode")
    val mode: MaterializationMode = MaterializationMode.NONE,
    @SerialName("ttlSeconds")
    val ttlSeconds: Long? = null,
    @SerialName("storageResourceDescriptorId")
    val storageResourceDescriptorId: ResourceDescriptorId? = null,
    @SerialName("retention")
    val retention: RetentionSpec? = null,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

@JsExportCompat
@Serializable
data class RetentionSpec(
    @SerialName("purpose")
    val purpose: String? = null,
    @SerialName("legalBasis")
    val legalBasis: String? = null,
    @SerialName("retentionPeriod")
    val retentionPeriod: String? = null,
    @SerialName("deleteAction")
    val deleteAction: RetentionDeleteAction? = null,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)
