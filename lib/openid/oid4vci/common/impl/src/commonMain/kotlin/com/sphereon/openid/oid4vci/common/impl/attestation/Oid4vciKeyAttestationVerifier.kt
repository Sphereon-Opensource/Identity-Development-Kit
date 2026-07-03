/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.impl.attestation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.trust.x509.X509TrustAnchorLoader
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock

/**
 * OID4VCI 1.0 section 7.2 key-attestation JWT verifier that is reusable outside
 * the issuer runtime graph.
 */
@Inject
@SingleIn(SessionScope::class)
class Oid4vciKeyAttestationVerifier(
    private val verifyJwsCommand: VerifyJwsCommand,
    private val externalIdentifierResolver: MultiExternalIdentifierService,
    private val x509TrustAnchorLoader: X509TrustAnchorLoader,
) {
    @Suppress("LongMethod", "ReturnCount")
    suspend fun verify(
        keyAttestationJwt: String,
        trustConfig: Oid4vciKeyAttesterTrustConfig?,
        policy: KeyAttestationsRequired?,
        expectedNonce: String? = null,
        clockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    ): IdkResult<ValidatedOid4vciKeyAttestation, IdkError> {
        val (headerJson, _) =
            peekJwsHeaderAndPayload(keyAttestationJwt)
                ?: return invalidProof("key attestation JWT is malformed (expected three base64url segments)")

        val typ = headerJson["typ"]?.jsonPrimitive?.contentOrNull
        if (typ != KEY_ATTESTATION_TYP) {
            return invalidProof("key attestation JWT typ must be '$KEY_ATTESTATION_TYP', got '$typ'")
        }

        val x5cHeader =
            headerJson["x5c"]?.let { it as? JsonArray }
                ?: headerJson["x5c"]?.let { element ->
                    if (element is JsonPrimitive) JsonArray(listOf(element)) else null
                }
        val kid = headerJson["kid"]?.jsonPrimitive?.contentOrNull

        val pinnedJwks = trustConfig?.trustedJwks?.takeIf { it.isNotEmpty() }
        val trustedJwks: JsonObject =
            when {
                x5cHeader != null && x5cHeader.isNotEmpty() -> {
                    resolveAttesterViaX5c(x5cHeader, kid).getOrElse { return Err(it) }
                }

                pinnedJwks != null -> {
                    pinAttesterJwks(pinnedJwks, kid)
                        ?: return invalidProof(
                            "key attestation kid '$kid' does not match any pinned attester JWK",
                        )
                }

                else -> {
                    return invalidProof(
                        "key attestation JWT has no resolvable trust source (no x5c, no pinned attester JWK)",
                    )
                }
            }

        val verifyResult =
            verifyJwsCommand
                .execute(VerifyJwsArgs(jws = JwsCompact(keyAttestationJwt), trustedJwks = trustedJwks))
                .getOrElse { return Err(it) }

        if (!verifyResult.isValid) {
            return invalidProof(
                "key attestation signature verification failed: ${verifyResult.errorMessages.joinToString()}",
            )
        }

        val claims = verifyResult.parsedPayload
        val nowSeconds = Clock.System.now().epochSeconds

        val iat =
            claims["iat"]?.jsonPrimitive?.long
                ?: return invalidProof("key attestation JWT missing required 'iat' claim")
        if (iat > nowSeconds + clockSkewSeconds) {
            return invalidProof("key attestation 'iat' is in the future")
        }

        val exp =
            claims["exp"]?.jsonPrimitive?.long
                ?: return invalidProof("key attestation JWT missing required 'exp' claim")
        if (exp < nowSeconds - clockSkewSeconds) {
            return invalidProof("key attestation has expired")
        }

        val attestedKeysJson = claims["attested_keys"] as? JsonArray
        if (attestedKeysJson == null || attestedKeysJson.isEmpty()) {
            return invalidProof("key attestation JWT 'attested_keys' must be a non-empty array")
        }
        val attestedKeys =
            attestedKeysJson.mapIndexed { index, element ->
                val obj =
                    element as? JsonObject
                        ?: return invalidProof("key attestation 'attested_keys[$index]' is not a JSON object")
                runCatching { Jwk.fromJsonObject(obj) }.getOrElse {
                    return invalidProof("key attestation 'attested_keys[$index]' is not a valid JWK: ${it.message}")
                }
            }

        val attestationNonce = claims["nonce"]?.jsonPrimitive?.contentOrNull
        if (attestationNonce != null && expectedNonce != null && attestationNonce != expectedNonce) {
            return invalidProof("key attestation 'nonce' does not match the expected c_nonce")
        }

        val issClaim = claims["iss"]?.jsonPrimitive?.contentOrNull
        val trustedIssuers = trustConfig?.trustedIssuers
        if (!trustedIssuers.isNullOrEmpty() && (issClaim == null || issClaim !in trustedIssuers)) {
            return invalidProof(
                "key attestation 'iss' '$issClaim' is not in the configured trusted-issuer allow-list",
            )
        }

        if (policy != null) {
            val attestedStorage = claims["key_storage"]?.let { stringList(it) }.orEmpty()
            policy.keyStorage?.forEach { required ->
                if (required !in attestedStorage) {
                    return invalidProof(
                        "key attestation 'key_storage' does not include required level '$required' (got $attestedStorage)",
                    )
                }
            }
            val attestedUserAuth = claims["user_authentication"]?.let { stringList(it) }.orEmpty()
            policy.userAuthentication?.forEach { required ->
                if (required !in attestedUserAuth) {
                    return invalidProof(
                        "key attestation 'user_authentication' does not include required level '$required' (got $attestedUserAuth)",
                    )
                }
            }
        }

        return Ok(ValidatedOid4vciKeyAttestation(attestedKeys = attestedKeys, claims = claims))
    }

    private suspend fun resolveAttesterViaX5c(
        x5c: JsonArray,
        kid: String?,
    ): IdkResult<JsonObject, IdkError> {
        val x5cStrings =
            x5c.map { entry ->
                (entry as? JsonPrimitive)?.contentOrNull
                    ?: return invalidProof("key attestation x5c entries must be strings")
            }
        val trustedAnchors = x509TrustAnchorLoader.loadTrustedCerts()
        val opts =
            ExternalIdentifierX5cOpts(
                identifier = x5cStrings,
                verify = true,
                trustAnchors = trustedAnchors,
            )
        val resolved =
            externalIdentifierResolver.resolve(opts).getOrElse {
                return invalidProof("key attestation x5c resolution failed: ${it.message}")
            } as? ExternalIdentifierResult.X5c
                ?: return invalidProof("key attestation x5c resolution did not return an X5c identifier")

        if (resolved.verificationResult.error) {
            return invalidProof(
                "key attestation x5c chain did not validate against configured X.509 trust anchors: " +
                    (resolved.verificationResult.message ?: "unknown error"),
            )
        }

        val leafJwk =
            (resolved.keyInfo.key as? Jwk)
                ?: return invalidProof("key attestation x5c leaf did not yield a JWK")
        val leafJwkJson =
            (leafJwk.toJsonObject() as? JsonObject)
                ?: return invalidProof("key attestation x5c leaf key did not serialize to a JWK object")

        val pinned =
            if (kid != null) {
                JsonObject(leafJwkJson + ("kid" to JsonPrimitive(kid)))
            } else {
                leafJwkJson
            }
        return Ok(jwksDocument(listOf(pinned)))
    }

    private fun pinAttesterJwks(
        pinned: List<Jwk>,
        kid: String?,
    ): JsonObject? {
        val pinnedJsonObjects =
            pinned.map { jwk ->
                Json.encodeToJsonElement(Jwk.serializer(), jwk).jsonObject
            }
        if (kid == null) {
            return jwksDocument(pinnedJsonObjects)
        }
        val match = pinnedJsonObjects.firstOrNull { it["kid"]?.jsonPrimitive?.contentOrNull == kid }
        if (match != null) return jwksDocument(listOf(match))
        return null
    }

    private fun jwksDocument(keys: List<JsonObject>): JsonObject =
        buildJsonObject {
            putJsonArray("keys") { keys.forEach { add(it) } }
        }

    private fun peekJwsHeaderAndPayload(jwt: String): Pair<JsonObject, JsonObject>? {
        val parts = jwt.split('.')
        if (parts.size != THREE_JWS_SEGMENTS) return null
        return try {
            val headerBytes = parts[0].decodeFromBase64Url()
            val payloadBytes = parts[1].decodeFromBase64Url()
            val header = Json.parseToJsonElement(headerBytes.decodeToString()).jsonObject
            val payload = Json.parseToJsonElement(payloadBytes.decodeToString()).jsonObject
            header to payload
        } catch (t: Throwable) {
            null
        }
    }

    private fun stringList(element: kotlinx.serialization.json.JsonElement): List<String> =
        runCatching { element.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }
            .getOrDefault(emptyList())

    private fun invalidProof(message: String): IdkResult<Nothing, IdkError> =
        Err(IdkError.fromString(code = Oid4vciErrors.INVALID_PROOF, message = message))

    companion object {
        const val KEY_ATTESTATION_TYP: String = "key-attestation+jwt"
        private const val THREE_JWS_SEGMENTS = 3
        private const val DEFAULT_CLOCK_SKEW_SECONDS = 300L
    }
}

data class Oid4vciKeyAttesterTrustConfig(
    val trustedJwks: List<Jwk>? = null,
    val trustedIssuers: List<String>? = null,
    val x509TrustAnchorPaths: List<String>? = null,
)

data class ValidatedOid4vciKeyAttestation(
    val attestedKeys: List<Jwk>,
    val claims: JsonObject,
)
