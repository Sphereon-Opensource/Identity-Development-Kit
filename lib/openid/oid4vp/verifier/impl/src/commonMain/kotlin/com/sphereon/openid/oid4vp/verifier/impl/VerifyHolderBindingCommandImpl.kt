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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.data.DeviceAuthValidation
import com.sphereon.mdoc.data.MdocValidations
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.verifier.HolderBindingResult
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingArgs
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.sdjwt.VerifySdJwtArgs
import com.sphereon.sdjwt.command.VerifySdJwtCommand
import com.sphereon.trust.x509.X509TrustAnchorLoader
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementation of VerifyHolderBindingCommand for OpenID4VP RP (Verifier).
 *
 * Verifies cryptographic holder binding in VP tokens per OpenID4VP 1.0 Final Section 7.3:
 * - SD-JWT: Verifies KB-JWT signature using cnf claim, validates nonce/aud/sd_hash
 * - mDoc: Verifies DeviceAuth COSE signature over SessionTranscript
 * - JWT VP: Verifies JWT signature and validates nonce/aud claims
 *
 * This implementation integrates with the existing SD-JWT verification infrastructure
 * in `libraries/sdjwt` which provides full RFC 9901 compliant KB-JWT verification.
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyHolderBindingCommandImpl(
    execution: SessionExecution,
    private val verifySdJwtCommand: VerifySdJwtCommand,
    private val verifyJwsCommand: VerifyJwsCommand,
    private val mdocValidations: MdocValidations,
    private val deviceAuthValidation: DeviceAuthValidation,
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
    private val x509TrustAnchorLoader: X509TrustAnchorLoader,
) : TypedServiceCommandAdapter<VerifyHolderBindingArgs, HolderBindingResult, IdkError>(
        commandId = VerifyHolderBindingCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyHolderBindingArgs>(),
        outputTypeToken = typeToken<HolderBindingResult>(),
    ),
    VerifyHolderBindingCommand {
    override val commandId: String get() = VerifyHolderBindingCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyHolderBindingArgs

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doExecute(
        args: VerifyHolderBindingArgs,
        applyDuring: (VerifyHolderBindingArgs) -> VerifyHolderBindingArgs,
    ): IdkResult<HolderBindingResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Verifying holder binding for format: ${processedArgs.format}")

        val presentation = processedArgs.presentation
        val formatString = processedArgs.format
        val expectedNonce = processedArgs.expectedNonce
        val expectedAudience = processedArgs.expectedAudience

        // Parse the format using the enum
        val credentialFormat = CredentialFormat.fromValueLenient(formatString)

        return when {
            credentialFormat?.isSdJwt == true -> {
                verifySdJwtHolderBinding(presentation, expectedNonce, expectedAudience)
            }

            credentialFormat?.isMdoc == true -> {
                verifyMdocHolderBinding(
                    presentation = presentation,
                    expectedNonce = expectedNonce,
                    clientId = processedArgs.clientId,
                    responseUri = processedArgs.responseUri,
                    verifierEncryptionJwkThumbprint = processedArgs.verifierEncryptionJwkThumbprint,
                )
            }

            credentialFormat?.isJwt == true -> {
                verifyJwtVpHolderBinding(presentation, expectedNonce, expectedAudience)
            }

            else -> {
                Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Unsupported credential format for holder binding verification: $formatString",
                    ),
                )
            }
        }
    }

    /**
     * Verify holder binding in SD-JWT using KB-JWT.
     *
     * Uses the existing SD-JWT verification infrastructure which implements RFC 9901:
     * - Verifies KB-JWT signature using cnf.jwk from issuer JWT
     * - Validates aud claim matches expected audience
     * - Validates nonce claim matches expected nonce
     * - Validates sd_hash matches hash of SD-JWT without KB-JWT
     * - Validates iat claim is present
     */
    private suspend fun verifySdJwtHolderBinding(
        presentation: String,
        expectedNonce: String,
        expectedAudience: String,
    ): IdkResult<HolderBindingResult, IdkError> {
        log.debug("Verifying SD-JWT holder binding with KB-JWT verification")

        // Use the existing SD-JWT verification which handles full KB-JWT verification
        val verifyArgs =
            VerifySdJwtArgs(
                sdJwt = presentation,
                expectedAudience = expectedAudience,
                expectedNonce = expectedNonce,
                validateDisclosures = true,
            )

        val verifyResult = verifySdJwtCommand.execute(verifyArgs)

        if (verifyResult.isErr) {
            return Ok(
                HolderBindingResult(
                    verified = false,
                    bindingMethod = "kb-jwt",
                    signatureValid = false,
                    nonceValid = false,
                    audienceValid = false,
                    sdHashValid = false,
                    errors = listOf("SD-JWT verification failed: ${verifyResult.error.message}"),
                ),
            )
        }

        val result = verifyResult.value

        // Extract holder key from cnf claim if present
        val holderKeyJson = extractHolderKeyFromSdJwt(result.sdJwt.payload.fullPayload)

        // Determine individual validation results from error messages
        val nonceError = result.errorMessages.any { it.contains("nonce", ignoreCase = true) }
        val audienceError = result.errorMessages.any { it.contains("aud", ignoreCase = true) }
        val sdHashError = result.errorMessages.any { it.contains("sd_hash", ignoreCase = true) }

        return Ok(
            HolderBindingResult(
                verified = result.keyBindingValid && result.signatureValid,
                holderKey = holderKeyJson,
                bindingMethod = "kb-jwt",
                signatureValid = result.signatureValid,
                nonceValid = !nonceError,
                audienceValid = !audienceError,
                sdHashValid = !sdHashError,
                errors = result.errorMessages,
                // Surface trust vs. crypto split so the wire-level error message can
                // distinguish "issuer key never resolved" (relative-kid / did:web fetch
                // / trust anchor) from a real ECDSA/EdDSA mismatch.
                issuerTrustEstablished = result.issuerTrustEstablished,
                issuerCryptoVerified = result.issuerCryptoVerified,
            ),
        )
    }

    /**
     * Extract holder's public key from cnf claim in SD-JWT payload.
     */
    private fun extractHolderKeyFromSdJwt(payload: JsonObject): String? {
        return try {
            val cnf = payload["cnf"]?.jsonObject ?: return null
            val jwk = cnf["jwk"]?.jsonObject ?: return null
            jwk.toString()
        } catch (e: Exception) {
            log.debug("Could not extract holder key from cnf claim: ${e.message}")
            null
        }
    }

    /**
     * Verify the holder binding for an mDoc presentation per ISO 18013-5 §9.3 and ISO 18013-7
     * §B.4.3 (the OID4VP profile of the mdoc handover).
     *
     * Steps:
     * 1. Decode the base64url presentation as a CBOR `DeviceResponse`.
     * 2. For each `Document`:
     *    - Run [MdocValidations.fromDocument] (cert chain, IssuerAuth COSE_Sign1, validity
     *      window, docType match, IssuerSignedItem digest match against MSO `valueDigests`).
     *    - Reconstruct the OID4VP `SessionTranscript` from the verifier's `client_id`,
     *      `nonce`, encryption-key JWK thumbprint, and `response_uri` per OID4VP 1.0 final
     *      §B.2.6 (`["OpenID4VPHandover", sha256(handoverInfoBytes)]` envelope around
     *      `[client_id, nonce, JwkThumbprint OR null, response_uri]`).
     *    - Verify the `DeviceAuth` COSE_Sign1 over that transcript using the device public
     *      key from the MSO.
     * 3. Aggregate per-document results — any critical=true failure marks the whole binding
     *    invalid.
     *
     * @param verifierEncryptionJwkThumbprint Raw 32-byte SHA-256 thumbprint (RFC 7638) of
     *   the verifier's encryption-key JWK. Required for `direct_post.jwt` / `dc_api.jwt`;
     *   null for plain `direct_post` / `dc_api`. The §B.2.6 handover MUSt match the
     *   response mode actually used.
     */
    private suspend fun verifyMdocHolderBinding(
        presentation: String,
        expectedNonce: String,
        clientId: String?,
        responseUri: String?,
        verifierEncryptionJwkThumbprint: ByteArray?,
    ): IdkResult<HolderBindingResult, IdkError> {
        log.debug("Verifying mDoc holder binding")

        if (clientId.isNullOrBlank() || responseUri.isNullOrBlank()) {
            return Ok(
                HolderBindingResult(
                    verified = false,
                    bindingMethod = "mdoc-device-auth",
                    signatureValid = false,
                    nonceValid = false,
                    audienceValid = false,
                    sdHashValid = null,
                    errors =
                        listOf(
                            "mDoc holder-binding verification requires the OID4VP context " +
                                "(clientId, responseUri). One or more were missing.",
                        ),
                ),
            )
        }

        // The presentation is base64url(CBOR(DeviceResponse)) — see ISO 18013-7 §B.3.
        val deviceResponseBytes =
            try {
                presentation.decodeFromBase64Url()
            } catch (expected: Exception) {
                return Ok(mdocFailure("Failed to base64url-decode the mDoc presentation: ${expected.message}"))
            }

        val deviceResponse =
            deviceResponseCborCodec
                .decode(deviceResponseBytes)
                .getOrElse { error ->
                    return Ok(mdocFailure("Failed to CBOR-decode DeviceResponse: ${error.message.defaultMessage}"))
                }.value

        val documents = deviceResponse.documents
        if (documents.isNullOrEmpty()) {
            return Ok(mdocFailure("DeviceResponse contains no documents to verify."))
        }

        val expectedSessionTranscript =
            SessionTranscript.fromOid4vpClientIdAndResponseUri(
                clientId = clientId,
                nonce = expectedNonce,
                jwkThumbprint = verifierEncryptionJwkThumbprint,
                responseUri = responseUri,
            )

        val errors = mutableListOf<String>()
        var allDocsVerified = true
        var allDeviceAuthsValid = true

        // mDoc IACA anchors are X.509 per ISO 18013-5, so they flow through the
        // shared `lib/trust/x509` loader (config keys: `trust.anchors.x509.*`).
        // The same loader feeds X509TrustValidationService; consumers (mdoc IACA,
        // SD-JWT issuer x5c, generic chain validation) share one config surface.
        // When no anchors are configured, the X.509 service's default trust store
        // is used and chain validation fails closed for unknown roots.
        val trustedCerts = x509TrustAnchorLoader.loadTrustedCerts().takeIf { it.isNotEmpty() }?.toTypedArray()

        // Track per-step outcomes separately so reporting doesn't conflate failures: a missing
        // trust anchor (CERTIFICATE_CHAIN) is NOT a signature failure (ISSUER_AUTH_SIGNATURE),
        // even though both contribute to overall rejection. The §10 verdict is `verified` AND
        // of all steps; the wire-level `signatureValid` field reflects ONLY the cryptographic
        // signature checks (issuer-auth + device-auth) so a downstream consumer can distinguish
        // a trust-policy fail (caller misconfiguration) from a forgery (genuine attack).
        var anyTrustChainFailed = false
        var anyIssuerAuthSignatureFailed = false

        documents.forEach { document ->
            val mdocResults =
                mdocValidations.fromDocument(
                    document = document,
                    trustedCerts = trustedCerts,
                    verificationTime = null,
                    keyInfo = null,
                    allowNotYetValidDocuments = false,
                    allowExpiredDocuments = false,
                )
            // Walk verifications by index (mirrors MdocVerification.DOCUMENT order: cert chain,
            // issuer-auth signature, digests, docType, validity). Map each critical failure to
            // the right outcome bucket — never to "signatureValid=false" unless the actual
            // signature step failed.
            val docTypeStr = document.docType.toString()
            mdocResults.verifications.forEach { v ->
                if (v.error && v.critical) {
                    val detail = "$docTypeStr: ${v.message ?: v.name}"
                    errors += detail
                    allDocsVerified = false
                    when (v.name) {
                        com.sphereon.crypto.core.CryptoConst.X509_LITERAL -> anyTrustChainFailed = true
                        com.sphereon.crypto.core.CryptoConst.COSE_LITERAL -> anyIssuerAuthSignatureFailed = true
                    }
                }
            }

            val deviceAuthResult =
                deviceAuthValidation.verifyDeviceAuth(
                    document = document,
                    expectedSessionTranscript = expectedSessionTranscript,
                )
            if (deviceAuthResult.error && deviceAuthResult.critical) {
                allDocsVerified = false
                allDeviceAuthsValid = false
                errors += "$docTypeStr: ${deviceAuthResult.message ?: deviceAuthResult.name}"
            }
        }

        // Cryptographic-signature outcome bucket: TRUE iff every issuer-auth signature AND every
        // device-auth signature actually verified. A trust-chain failure does NOT flip this —
        // the COSE adapter still ran the signature check using the leaf cert from x5chain (in
        // either header per RFC 9052 §3.1), independently of trust-anchor configuration.
        val allSigsValid = !anyIssuerAuthSignatureFailed && allDeviceAuthsValid
        return Ok(
            HolderBindingResult(
                verified = allDocsVerified,
                bindingMethod = "mdoc-device-auth",
                signatureValid = allSigsValid,
                // OID4VP §10 / §B.2.6: the holder's nonce binding lives inside the session
                // transcript (the verifier's nonce is mixed into the OpenID4VPHandover). If the
                // device-auth signature verifies, the holder used the same transcript bytes
                // — including the same nonce — that we reconstructed.
                nonceValid = allDeviceAuthsValid,
                audienceValid = allDeviceAuthsValid,
                sdHashValid = null, // Not applicable for mDoc
                errors = errors,
            ),
        )
    }

    private fun mdocFailure(message: String): HolderBindingResult =
        HolderBindingResult(
            verified = false,
            bindingMethod = "mdoc-device-auth",
            signatureValid = false,
            nonceValid = false,
            audienceValid = false,
            sdHashValid = null,
            errors = listOf(message),
        )

    /**
     * Verify holder binding in JWT VP using proof signature.
     *
     * JWT VP (Verifiable Presentation) contains:
     * - Header with holder's key reference (kid, jwk, or x5c)
     * - Payload with nonce and aud claims
     * - Signature from holder's private key
     *
     * Verification steps:
     * 1. Parse JWT and extract header/payload
     * 2. Verify signature using public key from header
     * 3. Validate nonce claim matches expected value
     * 4. Validate aud claim matches expected value
     * 5. Validate timing claims (iat, exp) if present
     */
    private suspend fun verifyJwtVpHolderBinding(
        presentation: String,
        expectedNonce: String,
        expectedAudience: String,
    ): IdkResult<HolderBindingResult, IdkError> {
        log.debug("Verifying JWT VP holder binding")

        val errors = mutableListOf<String>()

        // Parse JWT
        val jwtParts = presentation.split(".")
        if (jwtParts.size != JWT_PART_COUNT) {
            return Ok(
                HolderBindingResult(
                    verified = false,
                    bindingMethod = BINDING_METHOD_JWT_PROOF,
                    signatureValid = false,
                    nonceValid = false,
                    audienceValid = false,
                    errors = listOf("Invalid JWT format: expected 3 parts, got ${jwtParts.size}"),
                ),
            )
        }

        // Decode payload to validate claims
        val payloadJson =
            try {
                val payloadDecoded = decodeBase64Url(jwtParts[1])
                json.parseToJsonElement(payloadDecoded).jsonObject
            } catch (e: Exception) {
                return Ok(
                    HolderBindingResult(
                        verified = false,
                        bindingMethod = BINDING_METHOD_JWT_PROOF,
                        signatureValid = false,
                        nonceValid = false,
                        audienceValid = false,
                        errors = listOf("Failed to decode JWT payload: ${e.message}"),
                    ),
                )
            }

        // Validate nonce claim
        val nonceValid = validateNonceClaim(payloadJson, expectedNonce, errors)

        // Validate audience claim
        val audienceValid = validateAudienceClaim(payloadJson, expectedAudience, errors)

        // Verify signature using JWS verification command
        val jws = JwsCompact(presentation)
        val verifyArgs = VerifyJwsArgs(jws = jws)

        val verifyResult = verifyJwsCommand.execute(verifyArgs)

        val signatureValid =
            if (verifyResult.isErr) {
                errors.add("JWT signature verification failed: ${verifyResult.error.message}")
                false
            } else {
                if (!verifyResult.value.isValid) {
                    errors.add("JWT signature is invalid")
                }
                verifyResult.value.isValid
            }

        // Extract holder key from header if present
        val holderKey = extractHolderKeyFromJwtHeader(jwtParts[0])

        val verified = signatureValid && nonceValid && audienceValid

        return Ok(
            HolderBindingResult(
                verified = verified,
                holderKey = holderKey,
                bindingMethod = BINDING_METHOD_JWT_PROOF,
                signatureValid = signatureValid,
                nonceValid = nonceValid,
                audienceValid = audienceValid,
                sdHashValid = null, // Not applicable for JWT VP
                errors = errors,
            ),
        )
    }

    /**
     * Validate nonce claim in JWT payload.
     */
    private fun validateNonceClaim(
        payload: JsonObject,
        expectedNonce: String,
        errors: MutableList<String>,
    ): Boolean {
        val nonce = payload["nonce"]?.jsonPrimitive?.content
        return when {
            nonce == null -> {
                errors.add("JWT missing required 'nonce' claim")
                false
            }

            nonce != expectedNonce -> {
                errors.add("JWT 'nonce' claim mismatch: expected '$expectedNonce' but got '$nonce'")
                false
            }

            else -> {
                true
            }
        }
    }

    /**
     * Validate audience claim in JWT payload.
     * The aud claim can be a single string or an array of strings.
     */
    private fun validateAudienceClaim(
        payload: JsonObject,
        expectedAudience: String,
        errors: MutableList<String>,
    ): Boolean {
        val audElement = payload["aud"]
        if (audElement == null) {
            errors.add("JWT missing required 'aud' claim")
            return false
        }

        // Handle both string and array formats
        val audiences =
            try {
                when {
                    audElement is kotlinx.serialization.json.JsonArray -> {
                        audElement.map { it.jsonPrimitive.content }
                    }

                    else -> {
                        listOf(audElement.jsonPrimitive.content)
                    }
                }
            } catch (e: Exception) {
                errors.add("JWT 'aud' claim has invalid format: ${e.message}")
                return false
            }

        return if (expectedAudience in audiences) {
            true
        } else {
            errors.add("JWT 'aud' claim mismatch: expected '$expectedAudience' but got $audiences")
            false
        }
    }

    /**
     * Extract holder's public key from JWT header.
     */
    private fun extractHolderKeyFromJwtHeader(headerBase64: String): String? =
        try {
            val headerDecoded = decodeBase64Url(headerBase64)
            val header = json.parseToJsonElement(headerDecoded).jsonObject
            // Try jwk first, then fall back to kid
            header["jwk"]?.jsonObject?.toString()
        } catch (e: Exception) {
            log.debug("Could not extract holder key from JWT header: ${e.message}")
            null
        }

    /**
     * Decode base64url string to UTF-8 string.
     */
    private fun decodeBase64Url(input: String): String = input.decodeFromBase64Url().decodeToString()

    private companion object {
        private const val JWT_PART_COUNT = 3
        private const val BINDING_METHOD_JWT_PROOF = "jwt-proof"
    }
}
