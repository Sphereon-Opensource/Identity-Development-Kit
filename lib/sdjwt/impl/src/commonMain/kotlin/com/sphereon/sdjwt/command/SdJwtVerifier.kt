/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.sdjwt.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.VerifyJwsCommand
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierCnfOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.extern.CnfExternalIdentifierResolutionService
import com.sphereon.sdjwt.DisclosureDigest
import com.sphereon.sdjwt.DisclosureDigestUtil
import com.sphereon.sdjwt.KeyBindingJwt
import com.sphereon.sdjwt.SdJwt
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.sdjwt.SdJwtCompact
import com.sphereon.sdjwt.SdJwtVerificationResult
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.iterator

/**
 * SD-JWT Verifier implementing RFC 9901 verification requirements
 *
 * Internal implementation detail - use [VerifySdJwtCommand] instead.
 *
 * Verification process (RFC 9901 §6):
 * 1. Ensure SD-JWT format is correct (JWT~disclosure1~disclosure2~...~kbJwt)
 * 2. Verify the JWT signature
 * 3. Verify disclosure digests match values in _sd array
 * 4. If KB-JWT present, verify it and check sd_hash binding
 * 5. Check validity period (exp, nbf, iat) from revealed claims
 *
 * ## Holder Binding (RFC 7800)
 *
 * The CNF claim is resolved using the identifier resolution system which supports:
 * - DID-based kid: Resolves DID to get public key from verification method
 * - Direct JWK: Uses the embedded public key
 * - JKU (JWK Set URL): Fetches the key from a remote JWK Set
 *
 * @param verifyJwsCommand Command for verifying JWT signatures
 * @param cnfResolver CNF identifier resolver for resolving holder binding keys from the CNF claim
 */
internal class SdJwtVerifier(
    private val verifyJwsCommand: VerifyJwsCommand,
    private val cnfResolver: CnfExternalIdentifierResolutionService
) {
    /**
     * Verify an SD-JWT presentation
     *
     * @param sdJwtString The SD-JWT in compact format (JWT~disclosures~kbJwt)
     * @param identifier Optional identifier for verification (if not in JWT header)
     * @param expectedAudience Expected audience for KB-JWT verification (if KB-JWT present)
     * @param expectedNonce Expected nonce for KB-JWT verification (if KB-JWT present)
     * @param validateDisclosures Whether to validate disclosure digests (default true)
     * @return Verification result
     */
    suspend fun verify(
        sdJwtString: String,
        identifier: IdentifierOptsOrResult? = null,
        expectedAudience: String? = null,
        expectedNonce: String? = null,
        validateDisclosures: Boolean = true
    ): IdkResult<SdJwtVerificationResult, IdkError> {
        // Step 1: Parse SD-JWT from compact format
        val parseResult = SdJwtCodec.parse(sdJwtString)
        if (parseResult.isErr) {
            return IdkResult.err(parseResult.error)
        }
        val sdJwt = parseResult.value

        val errorMessages = mutableListOf<String>()

        // Step 2: Verify JWT signature
        val jwsValidation = verifyJwtSignature(sdJwt, identifier)
        val signatureValid = if (jwsValidation.isErr) {
            errorMessages.add("JWT signature verification failed: ${jwsValidation.error.message}")
            false
        } else {
            jwsValidation.value.isValid
        }

        // Step 3: Verify disclosure digests
        val disclosuresValid = if (validateDisclosures) {
            verifyDisclosures(sdJwt, errorMessages)
        } else {
            true
        }

        // Step 4: Verify Key Binding JWT if present
        val keyBindingValid = if (sdJwt.keyBindingJwt != null) {
            verifyKeyBinding(sdJwt, sdJwtString, expectedAudience, expectedNonce, errorMessages)
        } else {
            true
        }

        // Step 5: Validate timing claims from revealed payload
        validateTimingClaims(sdJwt.payload.fullPayload, errorMessages)

        val result = SdJwtVerificationResult(
            sdJwt = sdJwt,
            signatureValid = signatureValid,
            disclosuresValid = disclosuresValid,
            keyBindingValid = keyBindingValid,
            errorMessages = errorMessages,
            verificationTime = Clock.System.now().toEpochMilliseconds()
        )

        return IdkResult.ok(result)
    }

    /**
     * Verify the JWT signature using existing JWS verification infrastructure
     */
    private suspend fun verifyJwtSignature(
        sdJwt: SdJwtCompact,
        identifier: IdentifierOptsOrResult?
    ): IdkResult<JwsValidationResult, IdkError> {
        val jws = sdJwt.jwt
        val args = VerifyJwsArgs(
            jws = jws,
            identifier = identifier
        )
        return verifyJwsCommand.execute(args)
    }

    /**
     * Verify all disclosure digests match their values in the _sd array
     *
     * RFC 9901 §6.2: Verifier must check that each disclosure's digest
     * appears in the appropriate _sd array in the JWT payload
     */
    private fun verifyDisclosures(
        sdJwt: SdJwtCompact,
        errorMessages: MutableList<String>
    ): Boolean {
        try {
            // Extract all digests from the payload recursively
            val expectedDigests = DisclosureDigestUtil.extractDigests(
                sdJwt.payload.undisclosedPayload
            )

            // Get digest algorithm from payload
            val digestAlg = getDigestAlgorithm(sdJwt.payload.undisclosedPayload)

            // Verify each disclosure
            val providedDigests = mutableSetOf<String>()
            for ((digest, disclosure) in sdJwt.payload.digestedDisclosures) {
                // Calculate digest for this disclosure
                val calculatedDigest = DisclosureDigest.calculate(digestAlg, disclosure).value

                // Check if calculated digest matches the key
                if (calculatedDigest != digest) {
                    errorMessages.add(
                        "Disclosure digest mismatch for claim '${disclosure.key}': " +
                        "expected $digest but calculated $calculatedDigest"
                    )
                    return false
                }

                providedDigests.add(digest)
            }

            // Check that all provided disclosures have digests in the payload
            // (Some digests in payload may not have disclosures - that's valid for undisclosed claims)
            val unmatchedDisclosures = providedDigests - expectedDigests
            if (unmatchedDisclosures.isNotEmpty()) {
                errorMessages.add(
                    "Disclosures provided with digests not found in JWT payload: $unmatchedDisclosures"
                )
                return false
            }

            return true
        } catch (e: Exception) {
            errorMessages.add("Disclosure verification failed: ${e.message}")
            return false
        }
    }

    /**
     * Verify Key Binding JWT (RFC 9901 §4.3)
     *
     * Requirements:
     * - Must contain "aud" claim matching expected audience
     * - Must contain "nonce" claim matching expected nonce
     * - Must contain "iat" claim (issued at time)
     * - Must contain "sd_hash" claim binding to the presentation
     * - Signature must be valid
     */
    private suspend fun verifyKeyBinding(
        sdJwt: SdJwtCompact,
        fullSdJwtString: String,
        expectedAudience: String?,
        expectedNonce: String?,
        errorMessages: MutableList<String>
    ): Boolean {
        val kbJwt = sdJwt.keyBindingJwt ?: return true

        var valid = true

        // Verify required claims
        if (kbJwt.audience == null) {
            errorMessages.add("Key Binding JWT missing required 'aud' claim")
            valid = false
        } else if (expectedAudience != null && kbJwt.audience != expectedAudience) {
            errorMessages.add("Key Binding JWT 'aud' claim mismatch: expected '$expectedAudience' but got '${kbJwt.audience}'")
            valid = false
        }

        if (kbJwt.nonce == null) {
            errorMessages.add("Key Binding JWT missing required 'nonce' claim")
            valid = false
        } else if (expectedNonce != null && kbJwt.nonce != expectedNonce) {
            errorMessages.add("Key Binding JWT 'nonce' claim mismatch: expected '$expectedNonce' but got '${kbJwt.nonce}'")
            valid = false
        }

        if (kbJwt.issuedAt == null) {
            errorMessages.add("Key Binding JWT missing required 'iat' claim")
            valid = false
        }

        // Verify sd_hash binding (RFC 9901 §4.3)
        // The sd_hash is the hash of the SD-JWT without the KB-JWT
        val sdJwtWithoutKb = fullSdJwtString.substringBeforeLast(SdJwt.SEPARATOR.toString()).let {
            if (it.endsWith(SdJwt.SEPARATOR)) it else "$it${SdJwt.SEPARATOR}"
        }

        val digestAlg = getDigestAlgorithm(sdJwt.payload.undisclosedPayload)
        val expectedSdHash = DisclosureDigest.calculateDigest(sdJwtWithoutKb, digestAlg)

        if (kbJwt.sdHash == null) {
            errorMessages.add("Key Binding JWT missing required 'sd_hash' claim")
            valid = false
        } else if (kbJwt.sdHash != expectedSdHash) {
            errorMessages.add("Key Binding JWT 'sd_hash' mismatch: expected '$expectedSdHash' but got '${kbJwt.sdHash}'")
            valid = false
        }

        // Verify KB-JWT signature (RFC 9901 §4.3)
        // Extract holder's public key from cnf claim and verify KB-JWT signature
        val kbSignatureValid = verifyKeyBindingJwtSignature(
            sdJwt = sdJwt,
            kbJwt = kbJwt,
            errorMessages = errorMessages
        )

        if (!kbSignatureValid) {
            valid = false
        }

        return valid
    }

    /**
     * Verify KB-JWT signature using holder's public key from cnf claim (RFC 9901 §4.3)
     *
     * Process:
     * 1. Extract holder's public key from cnf claim in main JWT payload
     *    - If cnf.kid is a DID, resolve it to get the public key
     *    - Otherwise, use cnf.jwk directly
     * 2. Parse the KB-JWT as a JWS
     * 3. Verify KB-JWT signature using the holder's key
     *
     * ## DID-based Holder Binding
     *
     * When cnf.kid contains a DID (e.g., "did:key:z6Mk...#key-1"):
     * - The DID is resolved to obtain the DID document
     * - The verification method matching the kid fragment is located
     * - The public key is extracted from the verification method
     * - This key takes precedence over any cnf.jwk that may be present
     *
     * If cnf.kid contains a non-DID value, verification fails because
     * there's no way to resolve the key.
     *
     * @param sdJwt The complete SD-JWT with KB-JWT
     * @param kbJwt The parsed Key Binding JWT
     * @param errorMessages List to add error messages to
     * @return true if signature is valid, false otherwise
     */
    private suspend fun verifyKeyBindingJwtSignature(
        sdJwt: SdJwtCompact,
        kbJwt: KeyBindingJwt,
        errorMessages: MutableList<String>
    ): Boolean {
        // Extract cnf claim from main JWT payload
        val cnfClaim = sdJwt.payload.fullPayload["cnf"]
        if (cnfClaim == null) {
            errorMessages.add("Cannot verify KB-JWT signature: 'cnf' claim missing from main JWT payload")
            return false
        }

        // cnf claim should be a JSON object containing the holder's public key
        // RFC 7800 defines cnf claim structure
        if (cnfClaim !is JsonObject) {
            errorMessages.add("Cannot verify KB-JWT signature: 'cnf' claim is not a JSON object")
            return false
        }

        // Try to resolve holder key - first check for kid (DID), then fall back to jwk
        val holderJwk = resolveHolderKeyFromCnf(cnfClaim, errorMessages)
        if (holderJwk == null) {
            // Error message already added by resolveHolderKeyFromCnf
            return false
        }

        // Parse KB-JWT as JWS compact format to access header and verify signature
        try {
            val kbJws = JwsCompact(kbJwt.jwt)

            // Decode KB-JWT header to check for embedded JWK
            val parts = kbJwt.jwt.split(".")
            if (parts.size != 3) {
                errorMessages.add("KB-JWT has invalid format (expected 3 parts)")
                return false
            }

            // Decode KB-JWT header and extract JWK if present
            val kbJwtHeaderBase64 = parts[0]
            val kbJwtHeader = JwsUtils.decodeBase64UrlToJson(kbJwtHeaderBase64)

            // SECURITY CHECK: Verify KB-JWT header JWK matches resolved holder key (RFC 9901 §4.3)
            // Per RFC 9901 §4.3, if KB-JWT has a JWK in its header, it MUST match the CNF claim JWK
            // This prevents an attacker from substituting a different public key in the KB-JWT
            val kbJwtHeaderJwk = kbJwtHeader["jwk"]
            if (kbJwtHeaderJwk is JsonObject) {
                // KB-JWT has an embedded JWK - it must match the resolved holder key
                // Cast to Jwk since JwkType is a sealed interface with Jwk as its implementation
                val holderJwkJson = (holderJwk as Jwk).toJsonObject().jsonObject
                if (!jwkKeysMatch(kbJwtHeaderJwk, holderJwkJson)) {
                    errorMessages.add(
                        "KB-JWT header JWK does not match holder key from CNF claim. " +
                        "Per RFC 9901 §4.3, the holder key in KB-JWT header must match the CNF claim holder key."
                    )
                    return false
                }
            }

            // Per RFC 9901, the KB-JWT signature MUST be verified using the public key
            // from the SD-JWT's cnf claim, NOT solely from the KB-JWT header (even if present)
            // The KB-JWT header JWK is only for key identification, not the sole verification source

            // Create an external identifier from the resolved holder JWK
            val holderIdentifier = ExternalIdentifierJwkOpts(
                identifier = holderJwk
            )

            // Verify KB-JWT signature using the holder's public key
            val verifyArgs = VerifyJwsArgs(
                jws = kbJws,
                identifier = holderIdentifier
            )

            val verifyResult = verifyJwsCommand.execute(verifyArgs)

            if (verifyResult.isErr) {
                errorMessages.add("KB-JWT signature verification failed: ${verifyResult.error.message}")
                return false
            }

            if (!verifyResult.value.isValid) {
                errorMessages.add("KB-JWT signature is invalid")
                return false
            }

            // KB-JWT signature verified successfully
            return true

        } catch (e: Exception) {
            errorMessages.add("Failed to verify KB-JWT signature: ${e.message}")
            return false
        }
    }

    /**
     * Resolve the holder's public key from the CNF claim using the identifier resolution system.
     *
     * This delegates to the CNF identifier resolution service which handles:
     * - DID-based kid: Resolves DID to get public key from verification method
     * - Direct JWK: Uses the embedded public key
     * - JKU (JWK Set URL): Fetches the key from a remote JWK Set
     *
     * @param cnfClaim The CNF claim from the SD-JWT payload
     * @param errorMessages List to add error messages to
     * @return The resolved holder public key JWK, or null if resolution failed
     */
    private suspend fun resolveHolderKeyFromCnf(
        cnfClaim: JsonObject,
        errorMessages: MutableList<String>
    ): JwkType? {
        // Extract CNF claim components
        val kidElement = cnfClaim["kid"]
        val jwkElement = cnfClaim["jwk"]
        val jkuElement = cnfClaim["jku"]

        val kid = kidElement?.jsonPrimitive?.content
        val jwk = if (jwkElement is JsonObject) {
            try {
                Jwk.fromJsonObject(jwkElement)
            } catch (e: Exception) {
                errorMessages.add("Cannot verify KB-JWT signature: failed to parse 'cnf.jwk': ${e.message}")
                null
            }
        } else null
        val jku = jkuElement?.jsonPrimitive?.content

        // Create CNF opts for resolution
        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = cnfClaim.toMap(),
            kid = kid,
            jwk = jwk,
            jku = jku
        )

        // Resolve using the identifier resolution system
        val result = cnfResolver.resolve(cnfOpts)
        return result.fold(
            success = { cnfResult ->
                cnfResult.keyInfo.key
            },
            failure = { error ->
                errorMessages.add("Cannot verify KB-JWT signature: CNF resolution failed: ${error.message}")
                null
            }
        )
    }

    /**
     * Convert JsonObject to Map for CNF opts.
     */
    private fun JsonObject.toMap(): Map<String, Any?> {
        return this.mapValues { (_, value) ->
            when {
                value is kotlinx.serialization.json.JsonPrimitive && value.isString -> value.content
                value is kotlinx.serialization.json.JsonPrimitive -> value.content
                value is JsonObject -> value.toMap()
                else -> value.toString()
            }
        }
    }

    /**
     * Compare two JWKs to determine if they represent the same key
     *
     * Compares the key type (kty) and key-specific parameters:
     * - For EC keys: compares crv, x, y
     * - For RSA keys: compares n, e
     * - For OKP keys: compares crv, x
     *
     * Per RFC 7517, these are the parameters that uniquely identify a public key.
     *
     * @param jwk1 First JWK to compare
     * @param jwk2 Second JWK to compare
     * @return true if the keys match, false otherwise
     */
    private fun jwkKeysMatch(
        jwk1: JsonObject,
        jwk2: JsonObject
    ): Boolean {
        // Compare key type
        val kty1 = jwk1["kty"]?.jsonPrimitive?.content
        val kty2 = jwk2["kty"]?.jsonPrimitive?.content

        if (kty1 != kty2) {
            return false
        }

        // Compare key-specific parameters based on key type
        return when (kty1) {
            "EC" -> {
                // For EC keys, compare crv, x, y
                jwk1["crv"]?.jsonPrimitive?.content == jwk2["crv"]?.jsonPrimitive?.content &&
                jwk1["x"]?.jsonPrimitive?.content == jwk2["x"]?.jsonPrimitive?.content &&
                jwk1["y"]?.jsonPrimitive?.content == jwk2["y"]?.jsonPrimitive?.content
            }
            "RSA" -> {
                // For RSA keys, compare n, e
                jwk1["n"]?.jsonPrimitive?.content == jwk2["n"]?.jsonPrimitive?.content &&
                jwk1["e"]?.jsonPrimitive?.content == jwk2["e"]?.jsonPrimitive?.content
            }
            "OKP" -> {
                // For OKP (Octet Key Pair) keys, compare crv, x
                jwk1["crv"]?.jsonPrimitive?.content == jwk2["crv"]?.jsonPrimitive?.content &&
                jwk1["x"]?.jsonPrimitive?.content == jwk2["x"]?.jsonPrimitive?.content
            }
            else -> {
                // Unknown key type - cannot compare
                false
            }
        }
    }

    /**
     * Validate timing claims (exp, nbf, iat) from the revealed payload
     *
     * Note: Per RFC 9901 §9.7, timing claims SHOULD be plain (not selectively disclosable)
     * for security reasons, but the spec allows them to be SD.
     */
    private fun validateTimingClaims(
        payload: JsonObject,
        errorMessages: MutableList<String>
    ) {
        val now = Clock.System.now().epochSeconds

        // Check expiration (NumericDate per RFC 7519 may contain fractional seconds)
        payload["exp"]?.let { exp ->
            val expValue = exp.toString().trim('"').toDoubleOrNull()?.toLong()
            if (expValue != null && expValue < now) {
                errorMessages.add("SD-JWT has expired (exp: $expValue, now: $now)")
            }
        }

        // Check not before (NumericDate per RFC 7519 may contain fractional seconds)
        payload["nbf"]?.let { nbf ->
            val nbfValue = nbf.toString().trim('"').toDoubleOrNull()?.toLong()
            if (nbfValue != null && nbfValue > now) {
                errorMessages.add("SD-JWT not yet valid (nbf: $nbfValue, now: $now)")
            }
        }

        // Note: iat is informational, we don't validate it
    }

    /**
     * Extract digest algorithm from JWT payload
     * Falls back to SHA-256 if not specified (RFC 9901 default)
     */
    private fun getDigestAlgorithm(payload: JsonObject): DigestAlg {
        val algClaim = payload[SdJwt.SD_ALG_CLAIM]?.toString()?.trim('"')
        return if (algClaim != null) {
            DigestAlg.entries.find { it.httpHeaderId?.equals(algClaim, ignoreCase = true) == true }
                ?: SdJwt.DEFAULT_HASH_ALG
        } else {
            SdJwt.DEFAULT_HASH_ALG
        }
    }
}
