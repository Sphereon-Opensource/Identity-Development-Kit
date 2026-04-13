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

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents a JSON Web Encryption in any format (compact, flattened JSON, or general JSON)
 */
@JsExportCompat
sealed interface Jwe

/**
 * JWE Compact Serialization Format per RFC 7516 Section 3.1
 *
 * Format: BASE64URL(UTF8(JWE Protected Header)) || '.' ||
 *         BASE64URL(JWE Encrypted Key) || '.' ||
 *         BASE64URL(JWE Initialization Vector) || '.' ||
 *         BASE64URL(JWE Ciphertext) || '.' ||
 *         BASE64URL(JWE Authentication Tag)
 *
 * Example:
 * eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ.
 * OKOawDo13gRp2ojaHV7LFpZcgV7T6DVZKTyKOMTYUmKoTCVJRgckCL9kiMT03JGe...
 * 48V1_ALb6US04U3b.
 * 5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9.
 * XFBoMYUZodetZdvTiFvSkQ
 *
 * @property header The JWE Protected Header containing algorithm parameters
 * @property encryptedKey The encrypted Content Encryption Key (CEK), may be empty for direct encryption
 * @property iv The Initialization Vector used for content encryption
 * @property ciphertext The encrypted plaintext
 * @property authTag The Authentication Tag for AEAD algorithms
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweCompact", exact = true)
@JsExportCompat
data class JweCompact(
    val header: JweHeader,
    val encryptedKey: ByteArray, // May be empty for "dir" algorithm
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray,
) : Jwe {
    /**
     * Serialize to JWE Compact Serialization format
     *
     * @return JWE compact string with 5 base64url-encoded parts separated by dots
     */
    fun serialize(): String {
        val headerJson = cryptoJsonSerializer.encodeToString(header.toJson())
        val headerB64 = headerJson.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        val encKeyB64 = encryptedKey.encodeTo(Encoding.BASE64URL)
        val ivB64 = iv.encodeTo(Encoding.BASE64URL)
        val ciphertextB64 = ciphertext.encodeTo(Encoding.BASE64URL)
        val tagB64 = authTag.encodeTo(Encoding.BASE64URL)

        return "$headerB64.$encKeyB64.$ivB64.$ciphertextB64.$tagB64"
    }

    /**
     * Convert to string using compact serialization
     */
    override fun toString(): String = serialize()

    // Proper equals/hashCode for ByteArray fields
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as JweCompact

        if (header != other.header) {
            return false
        }
        if (!encryptedKey.contentEquals(other.encryptedKey)) {
            return false
        }
        if (!iv.contentEquals(other.iv)) {
            return false
        }
        if (!ciphertext.contentEquals(other.ciphertext)) {
            return false
        }
        if (!authTag.contentEquals(other.authTag)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + encryptedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }

    companion object {
        /**
         * Parse a JWE Compact Serialization string
         *
         * @param jwe The JWE compact string to parse
         * @return Parsed JweCompact object
         * @throws IllegalArgumentException if the format is invalid
         */
        fun parse(jwe: String): JweCompact {
            val parts = jwe.split(".")
            require(parts.size == 5) {
                "Invalid JWE Compact Serialization: expected 5 parts separated by '.', got ${parts.size}"
            }

            val headerJson = parts[0].decodeFrom(Encoding.BASE64URL).decodeToString()
            val header = JweHeader.fromJson(headerJson)

            // Empty string for encrypted key means direct encryption (alg="dir")
            val encryptedKey =
                if (parts[1].isEmpty()) {
                    ByteArray(0)
                } else {
                    parts[1].decodeFrom(Encoding.BASE64URL)
                }

            val iv = parts[2].decodeFrom(Encoding.BASE64URL)
            val ciphertext = parts[3].decodeFrom(Encoding.BASE64URL)
            val authTag = parts[4].decodeFrom(Encoding.BASE64URL)

            return JweCompact(header, encryptedKey, iv, ciphertext, authTag)
        }

        /**
         * Check if a string matches JWE Compact format (5 base64url parts separated by dots)
         */
        fun isValidCompactFormat(value: String): Boolean {
            val parts = value.split(".")
            return parts.size == 5 && parts.all { it.isEmpty() || it.matches(Regex("^[a-zA-Z0-9_-]+$")) }
        }
    }
}

/**
 * JWE JSON Flattened Serialization Format per RFC 7516 Section 7.2.2
 *
 * Used for single-recipient JWE with optional unprotected header.
 *
 * Example:
 * {
 *   "protected": "eyJlbmMiOiJBMTI4Q0JDLUhTMjU2In0",
 *   "unprotected": {"jku":"https://server.example.com/keys.jwks"},
 *   "encrypted_key": "6KB707dM9YTIgHtLvtgWQ8mKwboJW3of9locizkDTHzBC2IlrT1oOQ",
 *   "iv": "AxY8DCtDaGlsbGljb3RoZQ",
 *   "ciphertext": "KDlTtXchhZTGufMYmOYGS4HffxPSUrfmqCHXaI9wOGY",
 *   "tag": "Mz-VPPyU4RlcuYv1IwIvzw"
 * }
 *
 * @property protected Base64url-encoded JWE Protected Header
 * @property unprotected Optional JWE Shared Unprotected Header (not integrity protected)
 * @property encryptedKey Base64url-encoded encrypted Content Encryption Key
 * @property iv Base64url-encoded Initialization Vector
 * @property ciphertext Base64url-encoded ciphertext
 * @property tag Base64url-encoded Authentication Tag
 * @property aad Optional base64url-encoded Additional Authenticated Data
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweJsonFlattened", exact = true)
@JsExportCompat
@Serializable
data class JweJsonFlattened(
    val protected: String?, // Base64url-encoded protected header
    val unprotected: JsonObject? = null, // Unprotected header (as JsonObject for additional data)
    val encrypted_key: String, // Base64url
    val iv: String, // Base64url
    val ciphertext: String, // Base64url
    val tag: String, // Base64url
    val aad: String? = null, // Base64url (optional AAD)
) : Jwe {
    /**
     * Get the protected header as a JweHeader object
     */
    fun getProtectedHeader(): JweHeader? =
        protected?.let {
            val headerJson = it.decodeFrom(Encoding.BASE64URL).decodeToString()
            JweHeader.fromJson(headerJson)
        }

    /**
     * Get the unprotected header as a JweHeader object
     */
    fun getUnprotectedHeader(): JweHeader? = unprotected?.let { JweHeader.fromJson(it) }

    /**
     * Serialize to JSON string
     */
    fun toJsonString(): String = cryptoJsonSerializer.encodeToString(serializer(), this)

    companion object {
        /**
         * Parse a JWE JSON Flattened Serialization string
         */
        fun fromJson(json: String): JweJsonFlattened = cryptoJsonSerializer.decodeFromString(serializer(), json)
    }
}

/**
 * JWE JSON General Serialization Format per RFC 7516 Section 7.2.1
 *
 * Used for multi-recipient JWE where the same plaintext is encrypted
 * for multiple recipients using different keys.
 *
 * Example:
 * {
 *   "protected": "eyJlbmMiOiJBMTI4Q0JDLUhTMjU2In0",
 *   "unprotected": {"jku":"https://server.example.com/keys.jwks"},
 *   "recipients": [
 *     {"header": {"alg":"RSA1_5","kid":"2011-04-29"},
 *      "encrypted_key": "UGhIOguC7IuEvf_NPVaXsGMoLOmwvc1GyqlIKOK1nN94nHPoltGRhWhw7Zx0-kFm..."},
 *     {"header": {"alg":"A128KW","kid":"7"},
 *      "encrypted_key": "6KB707dM9YTIgHtLvtgWQ8mKwboJW3of9locizkDTHzBC2IlrT1oOQ"}
 *   ],
 *   "iv": "AxY8DCtDaGlsbGljb3RoZQ",
 *   "ciphertext": "KDlTtXchhZTGufMYmOYGS4HffxPSUrfmqCHXaI9wOGY",
 *   "tag": "Mz-VPPyU4RlcuYv1IwIvzw"
 * }
 *
 * @property protected Base64url-encoded JWE Protected Header
 * @property unprotected Optional JWE Shared Unprotected Header
 * @property recipients Array of per-recipient information
 * @property iv Base64url-encoded Initialization Vector
 * @property ciphertext Base64url-encoded ciphertext
 * @property tag Base64url-encoded Authentication Tag
 * @property aad Optional base64url-encoded Additional Authenticated Data
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweJsonGeneral", exact = true)
@JsExportCompat
@Serializable
data class JweJsonGeneral(
    val protected: String?,
    val unprotected: JsonObject? = null,
    val recipients: Array<JweRecipient>, // Multiple recipients
    val iv: String,
    val ciphertext: String,
    val tag: String,
    val aad: String? = null,
) : Jwe {
    /**
     * Get the protected header as a JweHeader object
     */
    fun getProtectedHeader(): JweHeader? =
        protected?.let {
            val headerJson = it.decodeFrom(Encoding.BASE64URL).decodeToString()
            JweHeader.fromJson(headerJson)
        }

    /**
     * Get the unprotected header as a JweHeader object
     */
    fun getUnprotectedHeader(): JweHeader? = unprotected?.let { JweHeader.fromJson(it) }

    /**
     * Serialize to JSON string
     */
    fun toJsonString(): String = cryptoJsonSerializer.encodeToString(serializer(), this)

    // Proper equals for Array field
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as JweJsonGeneral

        if (protected != other.protected) {
            return false
        }
        if (unprotected != other.unprotected) {
            return false
        }
        if (!recipients.contentEquals(other.recipients)) {
            return false
        }
        if (iv != other.iv) {
            return false
        }
        if (ciphertext != other.ciphertext) {
            return false
        }
        if (tag != other.tag) {
            return false
        }
        if (aad != other.aad) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = protected?.hashCode() ?: 0
        result = 31 * result + (unprotected?.hashCode() ?: 0)
        result = 31 * result + recipients.contentHashCode()
        result = 31 * result + iv.hashCode()
        result = 31 * result + ciphertext.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + (aad?.hashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Parse a JWE JSON General Serialization string
         */
        fun fromJson(json: String): JweJsonGeneral = cryptoJsonSerializer.decodeFromString(serializer(), json)
    }
}

/**
 * Per-Recipient Information for JWE JSON General Serialization
 *
 * Contains the per-recipient unprotected header and encrypted key.
 * Each recipient has its own key encryption parameters.
 *
 * @property header Optional per-recipient unprotected header (as JsonObject for additional data)
 * @property encrypted_key Base64url-encoded encrypted Content Encryption Key for this recipient
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweRecipient", exact = true)
@JsExportCompat
@Serializable
data class JweRecipient(
    val header: JsonObject? = null, // Per-recipient unprotected header
    val encrypted_key: String, // Base64url
) {
    /**
     * Get the header as a JweHeader object
     */
    fun getHeader(): JweHeader? = header?.let { JweHeader.fromJson(it) }
}

/**
 * Utility functions for JWE type checking
 */
object JweTypeUtils {
    fun isJweCompact(jwe: Jwe): Boolean = jwe is JweCompact

    fun isJweJsonFlattened(jwe: Jwe): Boolean = jwe is JweJsonFlattened

    fun isJweJsonGeneral(jwe: Jwe): Boolean = jwe is JweJsonGeneral
}
