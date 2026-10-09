/*
 * (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vp.verifier.federation

import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.openid.oid4vp.common.VpFormatInfo

/**
 * How a verifier instance's protocol keys and response endpoint appear in its `openid_credential_verifier` metadata
 * (OpenID Federation for Wallet Architectures 1.0 §6.3.1) and in the requests it sends under the
 * `openid_federation:` client identifier prefix (OpenID4VP 1.0 §5.9.3).
 *
 * The keys are the verifier's own request-object signing and response-encryption keys. Federation Entity Keys, which
 * sign the verifier's Entity Configuration, are a separate set and never appear here. The verifier and the party that
 * publishes the verifier's metadata derive the same values from the same public keys, so a wallet that resolves the
 * metadata from a Trust Chain finds the `kid` the request names.
 */
object VerifierFederationMetadata {
    /** Instance setting, relative to the verifier's config namespace, naming its OpenID Federation Entity Identifier. */
    const val ENTITY_IDENTIFIER_CONFIG_KEY = "request-object.signing.federation.entity-identifier"

    /** `kid` of a protocol key in the metadata `jwks` and in the requests: its RFC 7638 SHA-256 thumbprint. */
    fun kid(publicJwk: Jwk): String = generateJwkThumbprint(publicJwk)

    /** The public half of a KMS key, or null when the key does not resolve. */
    suspend fun publicJwk(kms: KeyManagerService, keyName: String): Jwk? {
        val result = kms.getKeyResult(KeyInfo<Nothing>(alias = keyName, keyVisibility = KeyVisibility.PUBLIC))
        if (result.isErr) return null
        val key = result.value.key?.key ?: return null
        return CoseJoseKeyMappingService.toJoseJwk(key).toPublicKey()
    }

    /** The Response URI of an instance whose deployment configures none: its external base URL plus `/response`. */
    fun defaultResponseUri(externalBaseUrl: String): String = "${externalBaseUrl.trimEnd('/')}/response"

    /** OpenID4VP 1.0 §11.1 `vp_formats_supported` of the verifier, in requests and in its federation metadata alike. */
    val VP_FORMATS_SUPPORTED: Map<String, VpFormatInfo> = mapOf(
        "dc+sd-jwt" to VpFormatInfo(sdJwtAlgValuesSupported = listOf("ES256"), kbJwtAlgValuesSupported = listOf("ES256")),
        // -7 = ES256 in IANA COSE Algorithms (RFC 8152).
        "mso_mdoc" to VpFormatInfo(issuerAuthAlgValuesSupported = listOf(-7), deviceAuthAlgValuesSupported = listOf(-7)),
        "jwt_vc_json" to VpFormatInfo(algValuesSupported = listOf("ES256")),
        "jwt_vc_json-ld" to VpFormatInfo(algValuesSupported = listOf("ES256")),
    )
}
