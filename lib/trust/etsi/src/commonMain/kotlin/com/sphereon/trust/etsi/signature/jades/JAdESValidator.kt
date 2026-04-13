/*
 * Copyright 2025 Sphereon International B.V.
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
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import kotlinx.serialization.json.*
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

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
        options: JAdESValidationOptions = JAdESValidationOptions()
    ): JAdESValidationResult
}

@ContributesTo(SessionScope::class)
interface JAdESValidatorComponent {
    val jadesValidator: JAdESValidator
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JAdESValidator>())
class JAdESValidatorImpl(
    private val keyManagerService: KeyManagerService,
    private val execution: SessionExecution
) : JAdESValidator {

    private val logger = execution.log.logManager.withTagAsync("JAdESValidator")

    override suspend fun validate(
        jwsData: ByteArray,
        detachedContent: ByteArray?,
        options: JAdESValidationOptions
    ): JAdESValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            val jwsString = jwsData.decodeToString().trim()

            // Detect format and parse
            val parsed = if (jwsString.startsWith("{")) {
                parseJsonSerialization(jwsString)
            } else {
                parseCompactSerialization(jwsString, detachedContent)
            }

            if (parsed == null) {
                return JAdESValidationResult(
                    valid = false, signatureValid = false,
                    errors = listOf("Failed to parse JWS data")
                )
            }

            // Parse protected headers for JAdES info
            val headerJson = parsed.protectedHeader.decodeFrom(Encoding.BASE64URL).decodeToString()
            val headerObj = Json.parseToJsonElement(headerJson).jsonObject
            val etsiHeaders = parseJAdESHeaders(headerObj)

            // Extract x5c certificates
            val x5c = etsiHeaders.x5c
                ?: headerObj["x5c"]?.jsonArray?.map { it.jsonPrimitive.content }

            if (x5c.isNullOrEmpty()) {
                errors.add("No x5c certificate chain in JWS header")
                return JAdESValidationResult(
                    valid = false, signatureValid = false,
                    etsiHeaders = etsiHeaders,
                    errors = errors, warnings = warnings
                )
            }

            // Verify JWS signature
            val signingInput = "${parsed.protectedHeader}.${parsed.payload}".encodeToByteArray()
            val signatureBytes = parsed.signature.decodeFrom(Encoding.BASE64URL)

            val keyInfo = KeyInfo<KeyType>(
                key = null,
                x5c = x5c.toTypedArray()
            )

            val signatureValid = try {
                keyManagerService.isValidRawSignature(
                    keyInfo = keyInfo,
                    input = signingInput,
                    signature = signatureBytes
                )
            } catch (e: Exception) {
                logger.error("JWS signature verification failed", exception = e)
                errors.add("Signature verification error: ${e.message}")
                false
            }

            if (!signatureValid) {
                errors.add("JWS signature validation failed")
            }

            // Validate signing certificate if requested
            val signingCertDer = x5c.first().decodeFrom(Encoding.BASE64)
            val chainBytes = x5c.map { it.decodeFrom(Encoding.BASE64) }

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
                val knownCritHeaders = setOf("sigT", "sigD", "srCms", "srAts", "x5t#S256")
                val unsupported = crit.filter { it !in knownCritHeaders }
                if (unsupported.isNotEmpty()) {
                    warnings.add("Unsupported critical headers: $unsupported")
                }
            }

            // Require ETSI headers if requested
            if (options.requireEtsiHeaders && etsiHeaders.sigT == null) {
                errors.add("JAdES sigT (signing time) required but not present")
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
                warnings = warnings
            )

        } catch (e: Exception) {
            logger.error("JAdES validation failed", exception = e)
            return JAdESValidationResult(
                valid = false, signatureValid = false,
                errors = listOf("JAdES validation failed: ${e.message}")
            )
        }
    }

    private data class ParsedJws(
        val protectedHeader: String,
        val payload: String,
        val signature: String
    )

    private fun parseCompactSerialization(jws: String, detachedContent: ByteArray?): ParsedJws? {
        val parts = jws.split(".")
        if (parts.size != 3) return null

        val payload = if (parts[1].isEmpty() && detachedContent != null) {
            detachedContent.encodeTo(Encoding.BASE64URL)
        } else {
            parts[1]
        }

        return ParsedJws(
            protectedHeader = parts[0],
            payload = payload,
            signature = parts[2]
        )
    }

    private fun parseJsonSerialization(jsonString: String): ParsedJws? {
        return try {
            val json = Json.parseToJsonElement(jsonString).jsonObject

            // Flattened JWS
            val protected_ = json["protected"]?.jsonPrimitive?.content
            val payload = json["payload"]?.jsonPrimitive?.content ?: ""
            val signature = json["signature"]?.jsonPrimitive?.content

            if (protected_ != null && signature != null) {
                return ParsedJws(protected_, payload, signature)
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
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJAdESHeaders(headerObj: JsonObject): JAdESProtectedHeaders {
        return JAdESProtectedHeaders(
            sigT = headerObj["sigT"]?.jsonPrimitive?.content,
            x5c = headerObj["x5c"]?.jsonArray?.map { it.jsonPrimitive.content },
            x5tS256 = headerObj["x5t#S256"]?.jsonPrimitive?.content,
            sigD = headerObj["sigD"]?.let { parseSignedDataReference(it.jsonObject) },
            crit = headerObj["crit"]?.jsonArray?.map { it.jsonPrimitive.content },
            srCms = headerObj["srCms"]?.jsonPrimitive?.content,
            srAts = headerObj["srAts"]?.jsonArray?.map { it.jsonObject }
        )
    }

    private fun parseSignedDataReference(obj: JsonObject): SignedDataReference {
        return SignedDataReference(
            mId = obj["mId"]?.jsonPrimitive?.content,
            pars = obj["pars"]?.jsonArray?.map { it.jsonPrimitive.content },
            hashM = obj["hashM"]?.jsonPrimitive?.content,
            hashV = obj["hashV"]?.jsonArray?.map { it.jsonPrimitive.content },
            ctys = obj["ctys"]?.jsonArray?.map { it.jsonPrimitive.content }
        )
    }
}
