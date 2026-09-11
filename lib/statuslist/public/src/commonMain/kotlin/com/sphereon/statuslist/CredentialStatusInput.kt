/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Authenticated credential metadata that is relevant to status resolution.
 *
 * This is deliberately separate from disclosed credential claims. In particular, an ISO/IEC
 * 18013 MSO status reference is authenticated issuer metadata and must not be projected into an
 * ordinary mdoc namespace or presented to generic JWT/VC claim processors.
 */
@Serializable
@JsExportCompat
data class CredentialStatusMetadata(
    val mdoc: MdocCredentialStatusMetadata? = null,
)

/** Status references extracted from one or more authenticated ISO/IEC 18013 MSOs. */
@Serializable
@JsExportCompat
data class MdocCredentialStatusMetadata(
    val references: List<CredentialStatusReference> = emptyList(),
)

/**
 * Claims and authenticated metadata supplied to a [CredentialStatusVerifier].
 *
 * [claims] remains the compatibility path for SD-JWT, JWT-VC, and W3C status-list shapes. New
 * credential formats should place authenticated status material in [metadata] instead.
 */
@Serializable
@JsExportCompat
data class CredentialStatusInput(
    val claims: JsonObject = JsonObject(emptyMap()),
    val metadata: CredentialStatusMetadata? = null,
)
