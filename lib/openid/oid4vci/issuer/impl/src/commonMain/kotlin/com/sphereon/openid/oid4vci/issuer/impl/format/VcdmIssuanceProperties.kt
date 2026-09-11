/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Merge the lossless caller-supplied top-level semantic properties into an issuer-built VCDM
 * document. Server-owned fields are rejected explicitly: silently dropping one would make the
 * caller's requested document differ from the signed document and would hide a key-material or
 * issuer substitution attempt.
 */
internal fun mergeVcdmIssuanceProperties(
    context: IssuanceContext,
    version: VcdmVersion,
): IdkResult<JsonObject, IdkError> {
    val properties = context.vcdmProperties
    val protected = properties.keys intersect SERVER_CONTROLLED_VCDM_KEYS
    if (protected.isNotEmpty()) {
        return Err(
            invalidVcdmIssuance(
                "caller-supplied VCDM properties cannot override server-controlled fields: ${protected.sorted().joinToString()}",
            ),
        )
    }

    val versionOnly = when (version) {
        VcdmVersion.V1_1 -> VCDM_2_ONLY_PROPERTIES
        VcdmVersion.V2_0 -> emptySet()
        else -> emptySet()
    }
    val unsupported = properties.keys intersect versionOnly
    if (unsupported.isNotEmpty()) {
        return Err(
            invalidVcdmIssuance(
                "VCDM ${version.value} does not support top-level properties: ${unsupported.sorted().joinToString()}",
            ),
        )
    }

    return Ok(
        buildJsonObject {
            properties.forEach { (name, value) -> put(name, value) }
        },
    )
}

internal fun invalidVcdmIssuance(message: String): IdkError =
    IdkError.fromString(code = "invalid_vcdm_credential", message = message)

/** Fields that issuance always derives from trusted protocol/configuration state. */
internal val SERVER_CONTROLLED_VCDM_KEYS: Set<String> = setOf(
    "@context", "type", "issuer", "credentialSubject", "validFrom", "validUntil",
    "issuanceDate", "expirationDate", "id", "credentialStatus", "status", "proof", "vc", "vp",
    "iss", "sub", "iat", "nbf", "exp", "jti", "cnf", "kid", "jwk", "x5c",
)

private val VCDM_2_ONLY_PROPERTIES: Set<String> = setOf("name", "description", "relatedResource")
