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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/*
 * JAdES (JSON Advanced Electronic Signatures) model — ETSI TS 119 182-1.
 *
 * JAdES extends JWS with ETSI-specific protected headers for advanced
 * electronic signatures. Used for 602 JSON trust list format validation.
 */

/**
 * JAdES-specific protected headers parsed from the JWS header.
 */
@Serializable
data class JAdESProtectedHeaders(
    /** Signing time (RFC 3339 in "sigT" claim) */
    @SerialName("sigT")
    val sigT: String? = null,
    /** Current TS 119 602 signing time as an integer RFC 7519 NumericDate. */
    @SerialName("iat")
    val iat: Long? = null,
    /** Certificate chain (standard JWS x5c header) */
    @SerialName("x5c")
    val x5c: List<String>? = null,
    /** Certificate thumbprint SHA-256 (standard JWS x5t#S256 header) */
    @SerialName("x5t#S256")
    val x5tS256: String? = null,
    /** Detached content reference */
    @SerialName("sigD")
    val sigD: SignedDataReference? = null,
    /** Critical headers that must be understood */
    @SerialName("crit")
    val crit: List<String>? = null,
    /** Signer attributes (CMS format, base64-encoded) */
    @SerialName("srCms")
    val srCms: String? = null,
    /** Signer attributes (JSON format) */
    @SerialName("srAts")
    val srAts: List<JsonObject>? = null,
) {
    /** Resolve the current NumericDate or the historical RFC 3339 signing time. */
    fun getSigningTime(): Instant? =
        iat?.let { epochSeconds ->
            runCatching { Instant.fromEpochSeconds(epochSeconds) }
                .getOrNull()
                ?.takeIf { it.epochSeconds == epochSeconds && it.nanosecondsOfSecond == 0 }
        }
            ?: sigT?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
}

/**
 * Detached content reference for JAdES detached signatures.
 */
@Serializable
data class SignedDataReference(
    /** Mechanism identifier */
    @SerialName("mId")
    val mId: String? = null,
    /** URI references to signed content */
    @SerialName("pars")
    val pars: List<String>? = null,
    /** Hash method (algorithm URI) */
    @SerialName("hashM")
    val hashM: String? = null,
    /** Hash values (base64url-encoded) */
    @SerialName("hashV")
    val hashV: List<String>? = null,
    /** Content types of signed data objects */
    @SerialName("ctys")
    val ctys: List<String>? = null,
)

/**
 * Options for JAdES validation.
 */
data class JAdESValidationOptions(
    val validateSigningCertificate: Boolean = true,
    val validateCertificateChain: Boolean = true,
    val requireEtsiHeaders: Boolean = false,
    val trustedCertificates: List<ByteArray>? = null,
)

/**
 * Result of JAdES validation.
 */
data class JAdESValidationResult(
    val valid: Boolean,
    val signatureValid: Boolean,
    val signingTime: Instant? = null,
    val signingCertificate: ByteArray? = null,
    val certificateChain: List<ByteArray>? = null,
    val etsiHeaders: JAdESProtectedHeaders? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Exact bytes passed to validation, never trimmed or re-serialized. */
    val serializedData: ByteArray? = null,
    /** Stable machine-readable diagnostics accompanying [errors]. */
    val reasonCodes: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }
        other as JAdESValidationResult
        if (valid != other.valid) {
            return false
        }
        if (signatureValid != other.signatureValid) {
            return false
        }
        if (signingTime != other.signingTime) {
            return false
        }
        if (signingCertificate != null) {
            if (other.signingCertificate == null) {
                return false
            }
            if (!signingCertificate.contentEquals(other.signingCertificate)) {
                return false
            }
        } else if (other.signingCertificate != null) {
            return false
        }
        if (errors != other.errors) {
            return false
        }
        if (warnings != other.warnings) {
            return false
        }
        if (serializedData != null) {
            if (other.serializedData == null || !serializedData.contentEquals(other.serializedData)) {
                return false
            }
        } else if (other.serializedData != null) {
            return false
        }
        if (reasonCodes != other.reasonCodes) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = valid.hashCode()
        result = 31 * result + signatureValid.hashCode()
        result = 31 * result + (signingTime?.hashCode() ?: 0)
        result = 31 * result + (signingCertificate?.contentHashCode() ?: 0)
        result = 31 * result + errors.hashCode()
        result = 31 * result + warnings.hashCode()
        result = 31 * result + (serializedData?.contentHashCode() ?: 0)
        result = 31 * result + reasonCodes.hashCode()
        return result
    }
}
