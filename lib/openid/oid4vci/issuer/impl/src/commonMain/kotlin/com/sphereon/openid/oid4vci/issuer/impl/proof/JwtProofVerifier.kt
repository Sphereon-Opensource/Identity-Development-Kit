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
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Clock

/**
 * Verifies JWT key-binding proofs per OID4VCI 1.0 Appendix F.2.1.
 *
 * Delegates cryptographic signature verification to [VerifyJwsCommand] (lib-crypto-core),
 * then performs OID4VCI-specific protocol validation (typ, aud, nonce).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<ProofVerifier>())
class JwtProofVerifier(
    private val nonceManager: NonceManager,
    private val verifyJwsCommand: VerifyJwsCommand,
    private val externalIdentifierResolver: com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService,
) : ProofVerifier {
    override val supportedProofType: String = "jwt"

    override suspend fun verify(
        proofValue: JsonElement,
        expectedAudience: String,
        supportedAlgorithms: List<String>?,
    ): IdkResult<VerifiedProof, IdkError> {
        val jwt = proofValue.jsonPrimitive.content
        // 1. Cryptographic signature verification + key resolution via lib-crypto-core.
        //    VerifyJwsCommand rejects `alg: none` and MAC algorithms as part of JWS verification;
        //    we additionally enforce §F.1 here (alg present, not `none`, not MAC).
        val jwsResult =
            verifyJwsCommand
                .execute(VerifyJwsArgs(jws = JwsCompact(jwt)))
                .getOrElse { return Err(it) }

        if (!jwsResult.isValid) {
            return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: signature verification failed: ${jwsResult.errorMessages.joinToString()}"))
        }

        // 2. Extract protected header from the verified JWS result (already parsed during verification)
        val firstSignature =
            jwsResult.jws.signatures.firstOrNull()
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: no signatures in verified JWS"))
        val protectedHeader = firstSignature.parsedProtectedHeader

        // 3. OID4VCI-specific header validation: typ == openid4vci-proof+jwt (§F.1).
        val typ = protectedHeader["typ"]?.jsonPrimitive?.content
        if (typ != PROOF_TYPE_HEADER) {
            return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: typ must be '$PROOF_TYPE_HEADER', got '$typ'"))
        }

        // 4. §F.1: alg is REQUIRED, MUST NOT be `none`, MUST NOT be a MAC algorithm. When the
        //    credential configuration publishes `proof_signing_alg_values_supported`, alg
        //    MUST match one of the listed values.
        val alg =
            protectedHeader["alg"]?.jsonPrimitive?.content
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: alg header is required"))
        if (alg.equals("none", ignoreCase = true)) {
            return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: alg 'none' is not permitted (§F.1)"))
        }
        if (alg in MAC_ALGORITHMS) {
            return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: symmetric/MAC alg '$alg' is not permitted (§F.1)"))
        }
        if (!supportedAlgorithms.isNullOrEmpty() && alg !in supportedAlgorithms) {
            return Err(
                IdkError.fromString(
                    code = "invalid_proof",
                    message = "Invalid JWT proof: alg '$alg' is not in proof_signing_alg_values_supported $supportedAlgorithms",
                ),
            )
        }

        // 5. Payload claims validation (already decoded during verification)
        val claims = jwsResult.parsedPayload

        val aud = claims["aud"]?.jsonPrimitive?.content
        if (aud != expectedAudience) {
            return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: aud '$aud' does not match expected '$expectedAudience'"))
        }

        val iat =
            claims["iat"]?.jsonPrimitive?.long
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: iat is required"))

        if (iat > Clock.System.now().epochSeconds + CLOCK_SKEW_SECONDS) {
            return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: iat is in the future"))
        }

        // 6. Consume nonce (single-use). Per §F.1 the nonce is REQUIRED when the issuer exposes
        //    a Nonce Endpoint — this deployment always does.
        val nonce =
            claims["nonce"]?.jsonPrimitive?.content
                ?: return Err(IdkError.fromString(code = "invalid_nonce", message = "Invalid JWT proof: nonce is required"))

        val nonceEntry = nonceManager.consume(nonce).getOrElse { return Err(it) }
        if (nonceEntry == null) {
            return Err(IdkError.fromString(code = "invalid_nonce", message = "Invalid JWT proof: nonce is invalid or expired"))
        }

        // 7. §F.1 mutual exclusion of kid / jwk / x5c.
        //
        //    The spec says each MUST NOT be present if another is present. Strict wallets
        //    already comply; some don't. Rather than reject, we apply a deterministic
        //    trust priority and use only the strongest available binding:
        //
        //      x5c  (certificate chain)      — strongest (CA-anchored)
        //      kid  (only when a DID URL)    — anchored in a DID document
        //      jwk  (raw public key)         — weakest, trust-on-first-use
        //
        //    A non-DID `kid` without jwk/x5c has no referent we can resolve in this service
        //    (no JWKS-lookup context) — rejected.
        val jwkHeader = protectedHeader["jwk"]
        val kid = protectedHeader["kid"]?.jsonPrimitive?.content
        val x5cHeader =
            protectedHeader["x5c"]?.let { it as? JsonArray }
                ?: protectedHeader["x5c"]?.let { el ->
                    // Some producers emit a single-string x5c (non-canonical) — normalise.
                    if (el is JsonPrimitive) JsonArray(listOf(el)) else null
                }

        val isDidKid = kid != null && kid.startsWith("did:")

        val (holderBindingKey: JsonElement, effectiveKid: String?) =
            when {
                x5cHeader != null && x5cHeader.isNotEmpty() -> {
                    resolveViaX5c(x5cHeader, kid, jwkHeader)
                        .getOrElse { return Err(it) }
                }

                isDidKid -> {
                    resolveViaDidKid(kid!!, jwkHeader)
                        .getOrElse { return Err(it) }
                }

                jwkHeader != null -> {
                    jwkHeader to null
                }

                kid != null -> {
                    return Err(
                        IdkError.fromString(
                            code = "invalid_proof",
                            message = "Invalid JWT proof: non-DID `kid` without `jwk` or `x5c` is not resolvable by this issuer",
                        ),
                    )
                }

                else -> {
                    return Err(
                        IdkError.fromString(
                            code = "invalid_proof",
                            message = "Invalid JWT proof: one of `jwk`, `kid` (DID URL), or `x5c` MUST be present (§F.1)",
                        ),
                    )
                }
            }

        val holderIdentifier = claims["iss"]?.jsonPrimitive?.content

        return Ok(
            VerifiedProof(
                holderBindingKey = holderBindingKey,
                holderIdentifier = holderIdentifier,
                keyId = effectiveKid ?: kid?.takeIf { isDidKid },
                algorithm = alg,
            ),
        )
    }

    /** Priority-1: resolve holder key from x5c (leaf cert's public key). */
    private suspend fun resolveViaX5c(
        x5c: JsonArray,
        kidIfPresent: String?,
        jwkIfPresent: JsonElement?,
    ): IdkResult<Pair<JsonElement, String?>, IdkError> {
        if (kidIfPresent != null || jwkIfPresent != null) {
            // §F.1 conflict — trust x5c, ignore the rest. Surface via IdkError only when enabled;
            // keep an info log-equivalent comment here since the module has no logger injected.
            // (Deliberately not an error; the spec violation is the wallet's, not ours.)
        }
        val x5cStrings =
            x5c.map {
                (it as? JsonPrimitive)?.content
                    ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: x5c entries must be strings"))
            }
        val opts =
            com.sphereon.crypto.resolution.extern
                .ExternalIdentifierX5cOpts(identifier = x5cStrings)
        val resolved =
            externalIdentifierResolver.resolve(opts).getOrElse {
                return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: failed to resolve x5c: ${it.message}"))
            }
        val resolvedKey =
            resolved.keyInfo.key as? com.sphereon.crypto.core.jose.Jwk
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: x5c leaf did not yield a JWK"))
        // X.509 certificates only carry public key material — no private-parameter filtering needed.
        val publicJwk =
            resolvedKey.toJsonObject() as? JsonObject
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: x5c leaf key did not serialize to a JWK object"))
        return Ok(publicJwk to null)
    }

    /** Priority-2: resolve holder key from DID URL kid. */
    private suspend fun resolveViaDidKid(
        kid: String,
        jwkIfPresent: JsonElement?,
    ): IdkResult<Pair<JsonElement, String?>, IdkError> {
        // §F.1 conflict — when both DID kid and jwk are present, trust the DID and ignore jwk.

        // For did:jwk, the JWK is base64url-embedded in the DID itself. Decode that JWK
        // VERBATIM (preserving the wallet's exact field order) rather than round-tripping
        // through our Kotlin `Jwk` data class. Some wallets re-derive the did:jwk from
        // cnf.jwk to cross-check it against cnf.kid — a field-order change would produce
        // a different did:jwk string and make the wallet report "credential was issued
        // for a key that was not in the credential request".
        if (kid.startsWith("did:jwk:")) {
            decodeDidJwkEmbedded(kid)?.let { embedded ->
                // did:jwk embeds the public JWK directly — no private material possible,
                // no filtering needed. Pass through verbatim to preserve the wallet's
                // exact byte encoding (field order, member set).
                return Ok(embedded to kid)
            }
            // Malformed did:jwk — fall through to the generic resolver, which will produce
            // a precise error for the caller.
        }

        val didOpts =
            com.sphereon.crypto.resolution.extern
                .ExternalIdentifierDidOpts(identifier = kid)
        val resolved =
            externalIdentifierResolver.resolve(didOpts).getOrElse {
                return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: failed to resolve kid '$kid': ${it.message}"))
            }
        val resolvedKey =
            resolved.keyInfo.key as? com.sphereon.crypto.core.jose.Jwk
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: resolved key from '$kid' is not a JWK"))
        // A DID document will never expose private key material, so we pass the
        // resolved JWK through as-is — no defensive private-parameter filtering.
        val publicJwk =
            resolvedKey.toJsonObject() as? JsonObject
                ?: return Err(IdkError.fromString(code = "invalid_proof", message = "Invalid JWT proof: resolved key from '$kid' did not serialize to a JWK object"))
        return Ok(publicJwk to kid)
    }

    /**
     * Extract and parse the JWK embedded inside a `did:jwk:<base64url-of-JWK>#<fragment>` URL.
     * Returns null if the DID can't be parsed as did:jwk.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeDidJwkEmbedded(didUrl: String): JsonObject? {
        val prefix = "did:jwk:"
        if (!didUrl.startsWith(prefix)) return null
        val afterPrefix = didUrl.removePrefix(prefix)
        val methodSpecificId = afterPrefix.substringBefore('#').substringBefore('?')
        if (methodSpecificId.isEmpty()) return null
        return try {
            // RFC 7515 base64url: no padding expected, but tolerate both.
            val padded = methodSpecificId + "=".repeat((4 - methodSpecificId.length % 4) % 4)
            val bytes =
                kotlin.io.encoding.Base64.UrlSafe
                    .decode(padded)
            val parsed =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(bytes.decodeToString())
            parsed as? JsonObject
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        const val PROOF_TYPE_HEADER = "openid4vci-proof+jwt"
        private const val CLOCK_SKEW_SECONDS = 300L

        /**
         * Symmetric / MAC signing algorithms. §F.1 forbids these for proofs because the
         * client would have to share the secret with the server — they are never meaningful
         * for holder-binding proof-of-possession.
         */
        private val MAC_ALGORITHMS = setOf("HS256", "HS384", "HS512")
    }
}
