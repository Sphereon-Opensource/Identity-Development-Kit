/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Represents the context in which trust validation should be performed.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustContext", exact = true)
@JsExportCompat
@Serializable
data class TrustContext(
    val type: String,
    val framework: String? = null,
    val parameters: Map<String, String> = emptyMap()
) {
    companion object {
        const val TYPE_ETSI_TSL = "etsi_tsl"
        const val TYPE_X509 = "x509"
        const val TYPE_CA_BUNDLE = "ca_bundle" // alias for TYPE_X509
        const val TYPE_DID = "did"
        const val TYPE_OPENID_FEDERATION = "openid_federation"
        const val TYPE_PUBLIC_KEY = "public_key"
        const val TYPE_CUSTOM = "custom"
    }
}

/**
 * The type of trust anchor.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustAnchorType", exact = true)
enum class TrustAnchorType {
    ETSI_TSL,
    X509_CA_BUNDLE,
    DID,
    OPENID_FEDERATION,
    PUBLIC_KEY,
    CUSTOM
}

/**
 * Request for trust validation.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustValidationRequest", exact = true)
data class TrustValidationRequest(
    @Contextual
    val identifier: IdentifierOptsOrResult,
    val context: TrustContext,
    val validationTime: Instant? = null,
    val checkRevocation: Boolean = true
)

/**
 * Result of trust validation.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustValidationResult", exact = true)
data class TrustValidationResult(
    val trusted: Boolean,
    val status: TrustStatus,
    val trustAnchor: TrustAnchor? = null,
    val validationPath: List<String> = emptyList(),
    val details: String? = null,
    val validatedAt: Instant? = null
)

/**
 * Status of trust validation.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustStatus", exact = true)
enum class TrustStatus {
    TRUSTED,
    UNTRUSTED,
    REVOKED,
    EXPIRED,
    NOT_YET_VALID,
    VALIDATION_ERROR,
    UNKNOWN
}

/**
 * Represents a trust anchor used for validation.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustAnchor", exact = true)
data class TrustAnchor(
    val id: String,
    val type: String,
    val name: String,
    @Contextual
    val keyInfo: ResolvedKeyInfoType<KeyType>,
    val uri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val validFrom: Instant? = null,
    val validUntil: Instant? = null
) {
    companion object {
        const val TYPE_ETSI_TSP = "etsi_tsp"
        const val TYPE_ROOT_CA = "root_ca"
        const val TYPE_DID_DOCUMENT = "did_document"
        const val TYPE_OPENID_FED_ENTITY = "openid_federation_entity"
        const val TYPE_PUBLIC_KEY = "public_key"
    }
}
