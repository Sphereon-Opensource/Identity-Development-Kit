/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.DpopJwtHeader
import com.sphereon.oauth2.common.model.DpopJwtPayload
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

/**
 * Everything [DpopProofAssembly.assemble] needs to build a DPoP proof JWT's signing input (RFC
 * 9449 Section 4.2), independent of who ultimately signs it.
 */
data class DpopProofAssemblyRequest(
    val httpMethod: String,
    val httpUrl: String,
    val nonce: String? = null,
    val accessToken: String? = null,
    val issuedAt: Long? = null,
)

/**
 * Everything needed to finish a DPoP proof once a signature over [signingInput] has been
 * produced. The caller signs [signingInput] however it holds its private key - KMS-backed
 * [com.sphereon.crypto.jose.jws.JwtService] in
 * [com.sphereon.oauth2.client.impl.dpop.CreateDpopProofCommandImpl], or a `Wscd` in the wallet
 * WSCA local-signing path (`LocalWsca`) - and hands the raw signature bytes to
 * [DpopProofAssembly.finish].
 */
data class DpopSigningInput(
    val headerJson: JsonObject,
    val payloadJson: JsonObject,
    /** JSON-serialized claims payload, before base64url encoding. */
    val payloadJsonString: String,
    /** base64url(header JSON). */
    val encodedHeader: String,
    /** base64url(payload JSON). */
    val encodedPayload: String,
    /** `"$encodedHeader.$encodedPayload"` as bytes: what a signer signs. */
    val signingInput: ByteArray,
    val jwkThumbprint: String,
)

/**
 * Shared DPoP proof JWT assembly (RFC 9449): builds the header, payload and signing input ONCE so
 * every signer produces identically-shaped proofs without duplicating jti generation,
 * access-token-hash computation, URL normalization or algorithm inference.
 *
 * This class holds NO signing capability itself: [assemble] never touches key material, and
 * [finish] only concatenates an already-produced signature onto the assembled input. That split
 * is what lets a caller like the wallet WSCA layer - which must never hold a KMS key handle, only
 * a `Wscd` signing surface - reuse this assembly instead of the full
 * [CreateDpopProofCommand]/KMS-backed JWT service path that
 * [com.sphereon.oauth2.client.impl.dpop.CreateDpopProofCommandImpl] uses. Both call sites build
 * their DPoP proofs through this single implementation; there is no second copy of the assembly
 * logic anywhere.
 */
@Inject
@SingleIn(SessionScope::class)
class DpopProofAssembly(
    private val secureRandom: SecureRandom,
) {
    suspend fun assemble(
        request: DpopProofAssemblyRequest,
        publicJwk: Jwk,
    ): DpopSigningInput {
        val jti = secureRandom.newToken(lengthBytes = JTI_RANDOM_BYTES)
        val ath = request.accessToken?.let { calculateAccessTokenHash(it) }
        val normalizedHtu = normalizeUrl(request.httpUrl)
        val iat = request.issuedAt ?: Clock.System.now().epochSeconds
        val algorithm = determineAlgorithm(publicJwk)

        val header = DpopJwtHeader(typ = "dpop+jwt", alg = algorithm, jwk = publicJwk)
        val payload =
            DpopJwtPayload(
                jti = jti,
                htm = request.httpMethod.uppercase(),
                htu = normalizedHtu,
                iat = iat,
                ath = ath,
                nonce = request.nonce,
            )

        val headerJson = Json.encodeToJsonElement(header).jsonObject
        val payloadJson = Json.encodeToJsonElement(payload).jsonObject
        val payloadJsonString = Json.encodeToString(payload)
        val encodedHeader = Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
        val encodedPayload = payloadJsonString.encodeToByteArray().encodeToBase64Url()

        return DpopSigningInput(
            headerJson = headerJson,
            payloadJson = payloadJson,
            payloadJsonString = payloadJsonString,
            encodedHeader = encodedHeader,
            encodedPayload = encodedPayload,
            signingInput = "$encodedHeader.$encodedPayload".encodeToByteArray(),
            jwkThumbprint = generateJwkThumbprint(publicJwk),
        )
    }

    /** Concatenates an already-produced [signature] onto [input] to complete the compact JWT. */
    fun finish(
        input: DpopSigningInput,
        signature: ByteArray,
    ): String = "${input.encodedHeader}.${input.encodedPayload}.${signature.encodeToBase64Url()}"

    /**
     * Calculates the SHA-256 hash of an access token (RFC 9449 Section 4.2), base64url-encoded.
     */
    private fun calculateAccessTokenHash(accessToken: String): String {
        val tokenBytes = accessToken.encodeToByteArray()
        val hashBytes = hash(tokenBytes, DigestAlg.SHA256)
        return hashBytes.encodeToBase64Url()
    }

    /**
     * Normalizes a URL by removing query parameters and fragment. RFC 9449 requires htu to
     * contain only scheme, host, port, and path.
     */
    private fun normalizeUrl(url: String): String {
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#')
        val cutPosition =
            when {
                queryStart != -1 && fragmentStart != -1 -> minOf(queryStart, fragmentStart)
                queryStart != -1 -> queryStart
                fragmentStart != -1 -> fragmentStart
                else -> url.length
            }
        return url.substring(0, cutPosition)
    }

    /**
     * Determines the signing algorithm from the JWK: uses the explicit `alg` if present,
     * otherwise infers it from key type and curve.
     */
    private fun determineAlgorithm(jwk: Jwk): String {
        jwk.alg?.let { return it.value }
        return when (jwk.kty.value) {
            "RSA" -> "RS256"
            "EC" ->
                when (jwk.crv?.value) {
                    "P-256" -> "ES256"
                    "P-384" -> "ES384"
                    "P-521" -> "ES512"
                    "secp256k1" -> "ES256K"
                    else -> throw IllegalArgumentException("Unsupported EC curve: ${jwk.crv?.value}")
                }
            "OKP" ->
                when (jwk.crv?.value) {
                    "Ed25519" -> "EdDSA"
                    else -> throw IllegalArgumentException("Unsupported OKP curve: ${jwk.crv?.value}")
                }
            else -> throw IllegalArgumentException("Unsupported key type for DPoP: ${jwk.kty.value}")
        }
    }

    private companion object {
        const val JTI_RANDOM_BYTES = 16
    }
}
