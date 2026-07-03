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

package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.issuer.config.KeyAttesterTrustConfig
import com.sphereon.openid.oid4vci.issuer.proof.KeyAttestationEvidenceEnforcementRequest
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedKeyAttestation
import com.sphereon.openid.oid4vci.issuer.proof.KeyAttestationEvidenceEnforcer as PersistedKeyAttestationEvidenceEnforcer
import com.sphereon.trust.x509.X509TrustAnchorLoader
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
 * OID4VCI 1.0 §7.2 key-attestation JWT verifier.
 *
 * A key attestation is a JWT signed by an attester (the wallet provider) testifying that a
 * set of holder keys were generated and stored under specified ISO 18045 attack-potential-
 * resistance levels. This verifier validates a single attestation JWT and returns the list
 * of `attested_keys` so the caller can match the proof's binding key against them.
 *
 * Trust resolution (in priority order):
 *  1. `x5c` header → validate chain via the IDK `x509` resolver, anchored at the global
 *     `lib/trust/x509` anchors.
 *  2. `kid` / `jwk` header → match against [KeyAttesterTrustConfig.trustedJwks].
 *  3. When [KeyAttesterTrustConfig.trustedIssuers] is non-empty, also enforce that the
 *     JWT's `iss` claim is in the list.
 *
 * Per-config X.509 anchor *paths* on [KeyAttesterTrustConfig.x509TrustAnchorPaths] are
 * not yet wired in: the conformance flow we target uses JWK-pinned trust, and the global
 * anchor loader covers everything else. When that field is needed, plumb a per-config
 * loader through here without changing the public surface.
 */
@Inject
@SingleIn(SessionScope::class)
class KeyAttestationVerifier(
    private val verifyJwsCommand: VerifyJwsCommand,
    private val externalIdentifierResolver: MultiExternalIdentifierService,
    private val x509TrustAnchorLoader: X509TrustAnchorLoader,
    private val persistedEvidenceEnforcer: PersistedKeyAttestationEvidenceEnforcer? = null,
) {
    private val evidenceEnforcer = KeyAttestationEvidenceEnforcer()

    /**
     * Verify a single key-attestation JWT.
     *
     * @param keyAttestationJwt The compact JWS — `header.payload.signature`.
     * @param trustConfig Per-credential trust override. `null` falls back to global X.509
     *   anchors only (i.e. only `x5c`-bound attestations can succeed).
     * @param policy `key_attestations_required` from the credential configuration. When
     *   non-null, the attestation's `key_storage` and `user_authentication` claim arrays
     *   MUST be supersets of the policy's required levels.
     * @param expectedNonce The issuer's current `c_nonce`, when one is in flight. The
     *   attestation's `nonce` claim — if present — MUST equal this value. The attestation
     *   nonce is OPTIONAL per §7.2; we only enforce equality when the wallet sent one.
     */
    @Suppress("LongMethod", "ReturnCount")
    suspend fun verify(
        keyAttestationJwt: String,
        trustConfig: KeyAttesterTrustConfig?,
        policy: KeyAttestationsRequired?,
        expectedAudience: String? = null,
        expectedNonce: String? = null,
        clockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    ): IdkResult<ValidatedKeyAttestation, IdkError> {
        // 1. Header pre-parse — we need typ + the trust-resolution headers BEFORE the
        //    signature check so we can pick the right verifier key. The JWS is fully re-
        //    parsed inside verifyJws; this peek is just for header fields.
        val (headerJson, _) =
            peekJwsHeaderAndPayload(keyAttestationJwt)
                ?: return invalidProof("key attestation JWT is malformed (expected three base64url segments)")

        val typ = headerJson["typ"]?.jsonPrimitive?.contentOrNull
        if (typ != KEY_ATTESTATION_TYP) {
            return invalidProof("key attestation JWT typ must be '$KEY_ATTESTATION_TYP', got '$typ'")
        }

        // 2. Resolve attester key into a JWKS that verifyJws will treat as the only
        //    acceptable signers. x5c first; then JWK pinning by kid (or thumbprint when no
        //    kid is present); falls through to a precise error if no trust source matches.
        val x5cHeader =
            headerJson["x5c"]?.let { it as? JsonArray }
                ?: headerJson["x5c"]?.let { el ->
                    if (el is JsonPrimitive) JsonArray(listOf(el)) else null
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

        // 3. Cryptographic signature verification, pinned to the resolved attester JWKS.
        val verifyResult =
            verifyJwsCommand
                .execute(VerifyJwsArgs(jws = JwsCompact(keyAttestationJwt), trustedJwks = trustedJwks))
                .getOrElse { return Err(it) }

        if (!verifyResult.isValid) {
            return invalidProof(
                "key attestation signature verification failed: ${verifyResult.errorMessages.joinToString()}",
            )
        }

        // 4. Claim checks.
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

        if (expectedAudience != null && !claims.audienceContains(expectedAudience)) {
            return invalidProof("key attestation 'aud' does not match expected audience '$expectedAudience'")
        }

        val attestedKeysJson = claims["attested_keys"] as? JsonArray
        if (attestedKeysJson == null || attestedKeysJson.isEmpty()) {
            return invalidProof("key attestation JWT 'attested_keys' must be a non-empty array")
        }
        val attestedKeys =
            attestedKeysJson.mapIndexed { index, element ->
                parseAttestedKeyJwk(element, index).getOrElse { return Err(it) }
            }

        val attestationNonce =
            claims["c_nonce"]?.jsonPrimitive?.contentOrNull
                ?: claims["nonce"]?.jsonPrimitive?.contentOrNull
        if (expectedNonce != null && policy != null && attestationNonce == null) {
            return invalidProof("production key attestation must carry 'c_nonce' matching the credential proof nonce")
        }
        if (attestationNonce != null && expectedNonce != null && attestationNonce != expectedNonce) {
            return invalidProof("key attestation 'c_nonce' does not match the expected credential proof nonce")
        }

        // 5. iss allow-list (only when the operator pinned one). The attestation MAY omit
        //    `iss` per §7.2; we only enforce membership when both sides supply a value.
        val issClaim = claims["iss"]?.jsonPrimitive?.contentOrNull
        val trustedIssuers = trustConfig?.trustedIssuers
        if (!trustedIssuers.isNullOrEmpty() && (issClaim == null || issClaim !in trustedIssuers)) {
            return invalidProof(
                "key attestation 'iss' '$issClaim' is not in the configured trusted-issuer allow-list",
            )
        }

        // 6. Policy check: every level the credential config requires MUST appear in the
        //    attestation's claim. Set membership per §11.2.3 (no ISO 18045 ordinal inference).
        val genericKeyAttestationEvidence =
            evidenceEnforcer
                .enforce(header = headerJson, claims = claims, policy = policy, attestedKeyCount = attestedKeys.size)
                .getOrElse { return Err(it) }
        val keyAttestationEvidence =
            if (policy != null && genericKeyAttestationEvidence != null) {
                val enforcer = persistedEvidenceEnforcer
                    ?: return invalidProof("production key attestation requires persisted Wallet Unit evidence enforcement")
                enforcer
                    .enforce(
                        KeyAttestationEvidenceEnforcementRequest(
                            keyAttestationJwt = keyAttestationJwt,
                            header = headerJson,
                            claims = claims,
                            evidence = genericKeyAttestationEvidence,
                        ),
                    ).getOrElse { return Err(it) }
            } else {
                genericKeyAttestationEvidence
            }

        return Ok(
            ValidatedKeyAttestation(
                attestedKeys = attestedKeys,
                claims = claims,
                keyAttestation = keyAttestationEvidence,
            ),
        )
    }

    /**
     * Resolve the attester key from `x5c`. Builds a single-entry trusted JWKS from the leaf
     * certificate's public key, mirroring the AS wallet-attestation flow at
     * `VerifyAttestationClientAuthCommandImpl.verifyAttestationX5cChain` (lines 501–562).
     */
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

        // Pin the JOSE header `kid` onto the leaf JWK so verifyJws's strict kid-match picks
        // this key cleanly when the cert-derived JWK has no kid (or a different one).
        val pinned =
            if (kid != null) {
                JsonObject(leafJwkJson + ("kid" to JsonPrimitive(kid)))
            } else {
                leafJwkJson
            }
        return Ok(jwksDocument(listOf(pinned)))
    }

    /**
     * Build a trusted-JWKS from the operator's pinned attester JWKs. Match by `kid` if the
     * attestation header carries one and any pinned JWK has the same kid; otherwise pass
     * the full pinned list and let verifyJws try them in order. Returns null when a kid
     * was supplied and nothing matched — that's a clean "untrusted attester" signal.
     */
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
        // No kid match — the attestation's signer is not in the allow-list.
        return null
    }

    private fun jwksDocument(keys: List<JsonObject>): JsonObject =
        buildJsonObject {
            putJsonArray("keys") { keys.forEach { add(it) } }
        }

    /**
     * Split the JWS compact form and parse just the protected header + payload as JSON.
     * Returns null on any structural failure; callers convert that into a precise error.
     */
    private fun peekJwsHeaderAndPayload(jwt: String): Pair<JsonObject, JsonObject>? {
        val parts = jwt.split('.')
        if (parts.size != THREE_JWS_SEGMENTS) return null
        return try {
            val headerBytes = base64UrlDecode(parts[0])
            val payloadBytes = base64UrlDecode(parts[1])
            val header = Json.parseToJsonElement(headerBytes.decodeToString()).jsonObject
            val payload = Json.parseToJsonElement(payloadBytes.decodeToString()).jsonObject
            header to payload
        } catch (t: Throwable) {
            null
        }
    }

    private fun base64UrlDecode(encoded: String): ByteArray = encoded.decodeFromBase64Url()

    private fun parseAttestedKeyJwk(
        element: JsonElement,
        index: Int,
    ): IdkResult<Jwk, IdkError> {
        val obj =
            element as? JsonObject
                ?: return invalidProof("key attestation 'attested_keys[$index]' is not a JSON object")
        val jwkObj =
            when {
                obj["kty"] != null -> obj
                else ->
                    parseEmbeddedPublicJwk(obj["publicKeyJwk"] ?: obj["public_key_jwk"] ?: obj["jwk"])
                        ?: return invalidProof(
                            "key attestation 'attested_keys[$index]' does not contain a JWK or publicKeyJwk",
                        )
            }
        val jwk =
            runCatching { Jwk.fromJsonObject(jwkObj) }.getOrElse {
                return invalidProof("key attestation 'attested_keys[$index]' is not a valid JWK: ${it.message}")
            }
        return Ok(jwk)
    }

    private fun parseEmbeddedPublicJwk(element: JsonElement?): JsonObject? =
        when (element) {
            is JsonObject -> element
            is JsonPrimitive ->
                element.contentOrNull
                    ?.let { value -> runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull() }
            else -> null
        }

    private fun JsonObject.audienceContains(expectedAudience: String): Boolean =
        when (val aud = this["aud"]) {
            is JsonArray -> aud.any { (it as? JsonPrimitive)?.contentOrNull == expectedAudience }
            is JsonPrimitive -> aud.contentOrNull == expectedAudience
            else -> false
        }

    private fun invalidProof(message: String): IdkResult<Nothing, IdkError> = Err(IdkError.fromString(code = Oid4vciErrors.INVALID_PROOF, message = message))

    companion object {
        /** Per OID4VCI 1.0 §7.2.1 — JWT `typ` for a key-attestation JWT. */
        const val KEY_ATTESTATION_TYP: String = "key-attestation+jwt"
        private const val THREE_JWS_SEGMENTS = 3
        private const val DEFAULT_CLOCK_SKEW_SECONDS = 300L
    }
}

/**
 * Result of verifying a key-attestation JWT.
 *
 * @property attestedKeys The `attested_keys` array, parsed into typed JWKs. Each one is a
 *   public key the attester vouches for; the caller (proof verifier) MUST match the
 *   proof's binding key against this list before accepting the proof.
 * @property claims Raw decoded JWT payload, for callers that want to inspect optional
 *   claims (`key_storage`, `user_authentication`, `nonce`) without re-parsing.
 */
data class ValidatedKeyAttestation(
    val attestedKeys: List<Jwk>,
    val claims: JsonObject,
    val keyAttestation: VerifiedKeyAttestation? = null,
)
