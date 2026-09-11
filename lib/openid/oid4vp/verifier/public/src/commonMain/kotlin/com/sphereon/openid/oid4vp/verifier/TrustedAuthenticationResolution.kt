/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Verifier-admitted authentication material for one exact VCDM controller.
 *
 * The controller is the issuer for a VC or the holder for a VP. It is selected from the
 * authenticated VCDM claims and is never taken from JOSE header key material. [identifier] is
 * resolved through the canonical identifier-resolution graph (DID, X.509, JWKS, or managed key),
 * while [trustedJwks] is an already admitted public JWKS. Exactly one list entry must match a
 * controller; ambiguity is rejected by the verifier.
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustedAuthenticationResolution", exact = true)
data class TrustedAuthenticationResolution(
    val controller: String,
    @Transient val identifier: IdentifierOptsOrResult? = null,
    val trustedJwks: JsonObject? = null,
) {
    init {
        require(controller.isNotBlank()) { "trusted_authentication_resolution_controller_blank" }
        require((identifier != null) xor (trustedJwks != null)) {
            "trusted_authentication_resolution_requires_exactly_one_source"
        }
        if (trustedJwks != null) {
            val keys = trustedJwks["keys"]
            require(keys is JsonArray && keys.isNotEmpty() && keys.all { it is JsonObject }) {
                "trusted_authentication_resolution_trusted_jwks_invalid"
            }
            keys.forEach { element ->
                val key = element as JsonObject
                val kid = key["kid"] as? JsonPrimitive
                val parsed = runCatching { Jwk.fromJsonObject(key) }.getOrNull()
                require(
                    kid?.isString == true && kid.content.isNotBlank() && parsed != null &&
                        parsed.d == null && parsed.p == null && parsed.q == null &&
                        parsed.dP == null && parsed.dQ == null && parsed.qInv == null &&
                        parsed.k == null && parsed.x5c == null && parsed.x5t == null &&
                        parsed.x5u == null && parsed.x5t_S256 == null,
                ) {
                    "trusted_authentication_resolution_trusted_jwk_must_be_public_signing_key"
                }
            }
        }
    }
}
