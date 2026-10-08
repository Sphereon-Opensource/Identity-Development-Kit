/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.common.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import kotlinx.serialization.Serializable

/**
 * DPoP Proof JWT Header (RFC 9449 Section 4.2)
 *
 * The DPoP proof JWT must include:
 * - typ: "dpop+jwt" (REQUIRED)
 * - alg: A signature algorithm identifier (REQUIRED)
 * - jwk: The public key used to sign the proof (REQUIRED)
 */
@JsExportCompat
@Serializable
data class DpopJwtHeader(
    // RFC 9449 §4.1: `typ` MUST be `dpop+jwt`. Modelled as nullable so the verifier can
    // distinguish a missing header (which the spec says MUST be rejected) from a wrong
    // header — defaulting to `"dpop+jwt"` would silently accept a proof that omitted it.
    val typ: String? = null,
    val alg: String,
    val jwk: Jwk,
)

/**
 * DPoP Proof JWT Payload (RFC 9449 Section 4.2)
 *
 * The DPoP proof JWT payload must include:
 * - jti: Unique identifier for the JWT (REQUIRED)
 * - htm: HTTP method of the request (REQUIRED)
 * - htu: HTTP URL of the request (without query and fragment) (REQUIRED)
 * - iat: Creation timestamp (REQUIRED)
 * - ath: Hash of the access token (REQUIRED when presenting access token)
 * - nonce: Server-provided nonce (OPTIONAL but REQUIRED when server returns one)
 */
@JsExportCompat
@Serializable
data class DpopJwtPayload(
    val jti: String,
    val htm: String,
    val htu: String,
    val iat: Long,
    val ath: String? = null,
    val nonce: String? = null,
)

/**
 * Options for creating a DPoP proof JWT
 *
 * @property issuer The managed identifier (key) to use for signing the DPoP proof
 * @property httpMethod The HTTP method (e.g., "POST", "GET")
 * @property httpUrl The HTTP URL (without query parameters and fragment)
 * @property nonce Server-provided nonce (optional, but required if server sends one)
 * @property accessToken The access token to bind (optional, required when using DPoP-bound tokens)
 * @property issuedAt The creation time (optional, defaults to current time)
 * @property additionalClaims Additional claims to include in the payload
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class CreateDpopProofOptions(
    val issuer: ManagedIdentifierOptsOrResult,
    val httpMethod: String,
    val httpUrl: String,
    val nonce: String? = null,
    val accessToken: String? = null,
    val issuedAt: Long? = null,
    val additionalClaims: Map<String, Any>? = null,
)

/**
 * Result of creating a DPoP proof
 *
 * @property dpopProof The compact JWT string
 * @property jwkThumbprint The thumbprint of the public key (for cnf claim)
 */
@JsExportCompat
data class DpopProofResult(
    val dpopProof: String,
    val jwkThumbprint: String,
)

/**
 * Options for verifying a DPoP proof JWT
 *
 * @property dpopProof The compact JWT string to verify
 * @property httpMethod Expected HTTP method
 * @property httpUrl Expected HTTP URL (without query and fragment)
 * @property expectedNonce Expected nonce value (if server sent one)
 * @property accessToken Access token to verify binding (if present)
 * @property expectedJwkThumbprint Expected JWK thumbprint from access token cnf claim
 * @property allowedSigningAlgs Allowed signing algorithms (optional)
 * @property now Current timestamp for validation (optional, defaults to current time)
 */
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class VerifyDpopProofOptions(
    val dpopProof: String,
    val httpMethod: String,
    val httpUrl: String,
    val expectedNonce: String? = null,
    val accessToken: String? = null,
    val expectedJwkThumbprint: String? = null,
    val allowedSigningAlgs: List<String>? = null,
    val now: Long? = null,
)

/**
 * Result of verifying a DPoP proof
 *
 * @property header The verified header
 * @property payload The verified payload
 * @property jwkThumbprint The thumbprint of the public key
 */
@JsExportCompat
data class VerifyDpopProofResult(
    val header: DpopJwtHeader,
    val payload: DpopJwtPayload,
    val jwkThumbprint: String,
)

/**
 * HTTP methods supported by DPoP
 */
object HttpMethod {
    const val GET = "GET"
    const val POST = "POST"
    const val PUT = "PUT"
    const val DELETE = "DELETE"
    const val PATCH = "PATCH"
    const val HEAD = "HEAD"
    const val OPTIONS = "OPTIONS"
}
