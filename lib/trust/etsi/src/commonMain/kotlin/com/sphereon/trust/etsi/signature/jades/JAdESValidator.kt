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

package com.sphereon.trust.etsi.signature.jades

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validates JAdES (JSON Advanced Electronic Signatures) — Baseline-B profile.
 * ETSI TS 119 182-1.
 *
 * JAdES extends JWS with ETSI-specific protected headers (sigT, sigD, etc.).
 * Delegates core JWS verification to KeyManagerService, then layers JAdES
 * header parsing and validation on top.
 *
 * Supports compact, flattened JSON, and general JSON serialization formats.
 */
interface JAdESValidator {
    suspend fun validate(
        jwsData: ByteArray,
        detachedContent: ByteArray? = null,
        options: JAdESValidationOptions = JAdESValidationOptions(),
    ): JAdESValidationResult
}

@ContributesTo(SessionScope::class)
interface JAdESValidatorGraph {
    val jadesValidator: JAdESValidator
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JAdESValidator>())
class JAdESValidatorImpl(
    private val keyManagerService: KeyManagerService,
    private val x509VerifyService: X509VerifyService,
    private val execution: SessionExecution,
) : JAdESValidator {
    private companion object {
        const val JWS_PART_COUNT = 3
        val INTEGER_NUMERIC_DATE = Regex("-?(0|[1-9][0-9]*)")
    }

    private val logger = execution.log.logManager.withTag("JAdESValidator")

    override suspend fun validate(
        jwsData: ByteArray,
        detachedContent: ByteArray?,
        options: JAdESValidationOptions,
    ): JAdESValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val reasonCodes = mutableListOf<String>()
        val serializedData = jwsData.copyOf()

        try {
            val jwsString = jwsData.decodeToString()
            val isJsonSerialization = jwsString.firstOrNull { !it.isWhitespace() } == '{'

            // Detect format and parse
            val parsed =
                if (isJsonSerialization) {
                    parseJsonSerialization(jwsString)
                } else {
                    if (JAdESSerialization.compactSigningInput(jwsData, detachedContent) == null) {
                        return JAdESValidationResult(
                            valid = false,
                            signatureValid = false,
                            errors = listOf("JWS compact serialization is not lossless or valid"),
                            serializedData = serializedData,
                            reasonCodes = listOf(TrustDiagnosticReasonCodes.SERIALIZATION_NOT_LOSSLESS),
                        )
                    }
                    parseCompactSerialization(jwsString, detachedContent)
                }

            if (parsed == null) {
                return JAdESValidationResult(
                    valid = false,
                    signatureValid = false,
                    errors = listOf("Failed to parse JWS data"),
                    serializedData = serializedData,
                    reasonCodes = listOf(TrustDiagnosticReasonCodes.SERIALIZATION_NOT_LOSSLESS),
                )
            }

            // Parse protected headers for JAdES info
            val headerJson = parsed.protectedHeader.decodeFrom(Encoding.BASE64URL).decodeToString()
            val headerObj = Json.parseToJsonElement(headerJson).jsonObject
            val etsiHeaders = parseJAdESHeaders(headerObj)

            // Extract x5c certificates
            val x5c =
                etsiHeaders.x5c
                    ?: headerObj["x5c"]?.jsonArray?.map { it.jsonPrimitive.content }

            if (x5c.isNullOrEmpty()) {
                errors.add("No x5c certificate chain in JWS header")
                return JAdESValidationResult(
                    valid = false,
                    signatureValid = false,
                    etsiHeaders = etsiHeaders,
                    errors = errors,
                    warnings = warnings,
                    serializedData = serializedData,
                    reasonCodes = reasonCodes,
                )
            }

            // Verify JWS signature
            val signingInput =
                if (isJsonSerialization) {
                    "${parsed.protectedHeader}.${parsed.payload}".encodeToByteArray()
                } else {
                    JAdESSerialization.compactSigningInput(jwsData, detachedContent)
                        ?: return JAdESValidationResult(
                            valid = false,
                            signatureValid = false,
                            etsiHeaders = etsiHeaders,
                            errors = listOf("JWS compact serialization is not lossless or valid"),
                            serializedData = serializedData,
                            reasonCodes = listOf(TrustDiagnosticReasonCodes.SERIALIZATION_NOT_LOSSLESS),
                        )
                }
            val signatureBytes = parsed.signature.decodeFrom(Encoding.BASE64URL)

            val keyInfo =
                KeyInfo<KeyType>(
                    key = null,
                    x5c = x5c.toTypedArray(),
                )

            val signatureValid =
                try {
                    keyManagerService.isValidRawSignature(
                        keyInfo = keyInfo,
                        input = signingInput,
                        signature = signatureBytes,
                    )
                } catch (expected: Exception) {
                    logger.error("JWS signature verification failed", exception = expected)
                    errors.add("Signature verification error: ${expected.message}")
                    false
                }

            if (!signatureValid) {
                errors.add("JWS signature validation failed")
                reasonCodes.add(TrustDiagnosticReasonCodes.SIGNATURE_INVALID)
            }

            // Validate signing certificate if requested
            val signingCertDer = x5c.first().decodeFrom(Encoding.BASE64)
            val chainBytes = x5c.map { it.decodeFrom(Encoding.BASE64) }

            if (options.validateCertificateChain) {
                val trustedCertificates = options.trustedCertificates
                when {
                    trustedCertificates.isNullOrEmpty() -> {
                        errors.add("No configured signer root certificates were provided")
                        reasonCodes.add(TrustDiagnosticReasonCodes.SIGNER_ROOT_NOT_CONFIGURED)
                    }
                    trustedCertificates.any { it.contentEquals(signingCertDer) } -> Unit
                    else -> {
                        val chainResult =
                            x509VerifyService.verifyCertificateChain(
                                X509VerificationRequest(
                                    chainDER = chainBytes.toTypedArray(),
                                    trustedCerts = trustedCertificates.map(::derToPem).toTypedArray(),
                                ),
                            )
                        if (chainResult.error) {
                            errors.add("Signer certificate chain validation failed: ${chainResult.message ?: "unknown error"}")
                            reasonCodes.add(TrustDiagnosticReasonCodes.SIGNER_CHAIN_INVALID)
                        }
                    }
                }
            }

            // Validate x5t#S256 if present
            if (options.validateSigningCertificate && etsiHeaders.x5tS256 != null) {
                val thumbprint = hash(signingCertDer, DigestAlg.SHA256)
                val expectedThumbprint = etsiHeaders.x5tS256.decodeFrom(Encoding.BASE64URL)
                if (!thumbprint.contentEquals(expectedThumbprint)) {
                    errors.add("Certificate thumbprint (x5t#S256) does not match signing certificate")
                }
            }

            // Check critical headers
            val crit = etsiHeaders.crit
            if (!crit.isNullOrEmpty()) {
                val knownCritHeaders = setOf("iat", "sigT", "sigD", "srCms", "srAts", "x5t#S256")
                val blank = crit.filter(String::isBlank)
                if (blank.isNotEmpty()) {
                    errors.add("Critical header names must not be blank")
                }
                val duplicates = crit.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                if (duplicates.isNotEmpty()) {
                    errors.add("Duplicate critical header names: $duplicates")
                }
                val missing = crit.filter { it !in headerObj }
                if (missing.isNotEmpty()) {
                    errors.add("Critical headers missing from protected header: $missing")
                }
                val unsupported = crit.filter { it !in knownCritHeaders }
                if (unsupported.isNotEmpty()) {
                    errors.add("Unsupported critical headers: $unsupported")
                    reasonCodes.add(TrustDiagnosticReasonCodes.UNSUPPORTED_CRITICAL_HEADER)
                }
            }

            // TS 119 602 current signatures use integer NumericDate iat. The
            // historical sigT form remains valid, but the two forms must not
            // be combined and malformed values must fail closed.
            val iatElement = headerObj["iat"]
            val hasIat = iatElement != null
            val parsedIat = parseNumericDate(iatElement)
            if (hasIat && parsedIat == null) {
                errors.add("JAdES iat (NumericDate) must be an integer")
            } else if (hasIat && etsiHeaders.getSigningTime() == null) {
                errors.add("JAdES iat (NumericDate) conversion does not round-trip exactly")
            }
            if (hasIat && etsiHeaders.sigT != null) {
                errors.add("JAdES iat and historical sigT cannot both be present")
            }
            if (!hasIat && etsiHeaders.sigT != null && etsiHeaders.getSigningTime() == null) {
                errors.add("JAdES historical sigT signing time is invalid")
            }

            // Require a current or historical ETSI signing-time header when
            // the caller opts into the LoTE/JAdES profile check.
            if (options.requireEtsiHeaders && !hasIat && etsiHeaders.sigT == null) {
                errors.add("JAdES iat (NumericDate) or historical sigT (signing time) required but not present")
            }

            val signingTime = etsiHeaders.getSigningTime()

            return JAdESValidationResult(
                valid = signatureValid && errors.isEmpty(),
                signatureValid = signatureValid,
                signingTime = signingTime,
                signingCertificate = signingCertDer,
                certificateChain = chainBytes,
                etsiHeaders = etsiHeaders,
                errors = errors,
                warnings = warnings,
                serializedData = serializedData,
                reasonCodes = reasonCodes,
            )
        } catch (expected: Exception) {
            logger.error("JAdES validation failed", exception = expected)
            return JAdESValidationResult(
                valid = false,
                signatureValid = false,
                errors = listOf("JAdES validation failed: ${expected.message}"),
                serializedData = serializedData,
                reasonCodes = listOf(TrustDiagnosticReasonCodes.SIGNATURE_INVALID),
            )
        }
    }

    private data class ParsedJws(
        val protectedHeader: String,
        val payload: String,
        val signature: String,
    )

    private fun parseCompactSerialization(
        jws: String,
        detachedContent: ByteArray?,
    ): ParsedJws? {
        val parts = jws.split(".")
        if (parts.size != JWS_PART_COUNT) {
            return null
        }

        val payload =
            if (parts[1].isEmpty() && detachedContent != null) {
                detachedContent.encodeTo(Encoding.BASE64URL)
            } else {
                parts[1]
            }

        return ParsedJws(
            protectedHeader = parts[0],
            payload = payload,
            signature = parts[2],
        )
    }

    private fun parseJsonSerialization(jsonString: String): ParsedJws? {
        return try {
            val json = Json.parseToJsonElement(jsonString).jsonObject

            // Flattened JWS
            val protectedHeader = json["protected"]?.jsonPrimitive?.content
            val payload = json["payload"]?.jsonPrimitive?.content ?: ""
            val signature = json["signature"]?.jsonPrimitive?.content

            if (protectedHeader != null && signature != null) {
                return ParsedJws(protectedHeader, payload, signature)
            }

            // General JWS — use first signature
            val signatures = json["signatures"]?.jsonArray
            if (signatures != null && signatures.isNotEmpty()) {
                val firstSig = signatures[0].jsonObject
                val sigProtected = firstSig["protected"]?.jsonPrimitive?.content ?: return null
                val sigSignature = firstSig["signature"]?.jsonPrimitive?.content ?: return null
                return ParsedJws(sigProtected, payload, sigSignature)
            }

            null
        } catch (expected: Exception) {
            logger.debug("Failed to parse JWS structure: ${expected.message}")
            null
        }
    }

    private fun parseJAdESHeaders(headerObj: JsonObject): JAdESProtectedHeaders =
        JAdESProtectedHeaders(
            sigT = headerObj["sigT"]?.jsonPrimitive?.content,
            iat = parseNumericDate(headerObj["iat"]),
            x5c = headerObj["x5c"]?.jsonArray?.map { it.jsonPrimitive.content },
            x5tS256 = headerObj["x5t#S256"]?.jsonPrimitive?.content,
            sigD = headerObj["sigD"]?.let { parseSignedDataReference(it.jsonObject) },
            crit = headerObj["crit"]?.jsonArray?.map { it.jsonPrimitive.content },
            srCms = headerObj["srCms"]?.jsonPrimitive?.content,
            srAts = headerObj["srAts"]?.jsonArray?.map { it.jsonObject },
        )

    private fun parseNumericDate(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString || !INTEGER_NUMERIC_DATE.matches(primitive.content)) return null
        return primitive.content.toLongOrNull()
    }

    private fun parseSignedDataReference(obj: JsonObject): SignedDataReference =
        SignedDataReference(
            mId = obj["mId"]?.jsonPrimitive?.content,
            pars = obj["pars"]?.jsonArray?.map { it.jsonPrimitive.content },
            hashM = obj["hashM"]?.jsonPrimitive?.content,
            hashV = obj["hashV"]?.jsonArray?.map { it.jsonPrimitive.content },
            ctys = obj["ctys"]?.jsonArray?.map { it.jsonPrimitive.content },
        )

    private fun derToPem(der: ByteArray): String =
        "-----BEGIN CERTIFICATE-----\n${der.encodeTo(Encoding.BASE64)}\n-----END CERTIFICATE-----"
}
