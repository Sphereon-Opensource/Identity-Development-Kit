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

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwtHeader
import com.sphereon.crypto.core.jose.JwtPayload
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Represents a JSON Web Signature in any format (compact, flattened JSON, or general JSON)
 */
@JsExportCompat
sealed interface Jws

/**
 * Compact JWS serialization format: header.payload.signature
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsCompact", exact = true)
@JsExportCompat
@Serializable
data class JwsCompact(
    val value: String,
) : Jws {
    override fun toString(): String = value
}

/**
 * Flattened JWS JSON serialization format
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsJsonFlattened", exact = true)
@JsExportCompat
@Serializable
data class
JwsJsonFlattened
    @JvmOverloads
    constructor(
        val payload: String,
        val protected: String,
        val header: JsonObject? = null,
        val signature: String,
    ) : Jws

/**
 * General JWS JSON serialization format with multiple signatures
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsJsonGeneral", exact = true)
@JsExportCompat
@Serializable
data class JwsJsonGeneral(
    val payload: String,
    val signatures: List<JwsJsonSignature>,
) : Jws

/**
 * Individual signature within a JWS JSON serialization
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsJsonSignature", exact = true)
@JsExportCompat
@Serializable
data class
JwsJsonSignature
    @JvmOverloads
    constructor(
        val protected: String,
        val header: JsonObject? = null,
        val signature: String,
    )

/**
 * JWS JSON General with resolved identifiers for each signature
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsJsonGeneralWithIdentifiers", exact = true)
@JsExportCompat
@Serializable
data class JwsJsonGeneralWithIdentifiers(
    val payload: String,
    val signatures: List<JwsJsonSignatureWithIdentifier>,
) : Jws

/**
 * JWS signature with associated identifier information
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsJsonSignatureWithIdentifier", exact = true)
@JsExportCompat
@Serializable
data class
JwsJsonSignatureWithIdentifier
    @JvmOverloads
    constructor(
        val protected: String,
        @kotlinx.serialization.Transient
        val parsedProtectedHeader: JsonObject = JsonObject(emptyMap()),
        val header: JsonObject? = null,
        val signature: String,
        // Note: IdentifierOptsOrResult cannot be serialized directly, so this is stored as metadata
        @kotlinx.serialization.Transient
        val identifier: IdentifierOptsOrResult? = null,
    )

/**
 * Prepared JWS object ready for signing
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PreparedJws", exact = true)
@JsExportCompat
data class
PreparedJws
    @JvmOverloads
    constructor(
        val protectedHeader: JwtHeader,
        val payload: ByteArray,
        val unprotectedHeader: JwtHeader? = null,
        val existingSignatures: List<JwsJsonSignature>? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as PreparedJws

            if (protectedHeader != other.protectedHeader) {
                return false
            }
            if (!payload.contentEquals(other.payload)) {
                return false
            }
            if (unprotectedHeader != other.unprotectedHeader) {
                return false
            }
            if (existingSignatures != other.existingSignatures) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = protectedHeader.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + (unprotectedHeader?.hashCode() ?: 0)
            result = 31 * result + (existingSignatures?.hashCode() ?: 0)
            return result
        }
    }

/**
 * Prepared JWS object with base64url encoded values and identifier.
 *
 * The [signingInput] contains the bytes to sign (base64url(header).base64url(payload) as UTF-8 bytes).
 * This enables two-step signing: prepare -> sign externally -> assemble.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PreparedJwsObject", exact = true)
@JsExportCompat
data class PreparedJwsObject(
    val jws: PreparedJws,
    val b64: Base64UrlEncoded,
    val identifier: IdentifierOptsOrResult,
    val signingInput: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as PreparedJwsObject

        if (jws != other.jws) {
            return false
        }
        if (b64 != other.b64) {
            return false
        }
        if (identifier != other.identifier) {
            return false
        }
        if (!signingInput.contentEquals(other.signingInput)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = jws.hashCode()
        result = 31 * result + b64.hashCode()
        result = 31 * result + identifier.hashCode()
        result = 31 * result + signingInput.contentHashCode()
        return result
    }
}

/**
 * Base64URL encoded parts of a JWS
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Base64UrlEncoded", exact = true)
@JsExportCompat
data class Base64UrlEncoded(
    val protectedHeader: String,
    val payload: String,
)

/**
 * Result of JWT compact creation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwtCompactResult", exact = true)
@JsExportCompat
@Serializable
data class JwtCompactResult(
    val jwt: String,
)

/**
 * Identifier mode for JWS creation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsIdentifierMode", exact = true)
@JsExportCompat
enum class JwsIdentifierMode {
    X5C, // X.509 certificate chain
    KID, // Key ID
    JWK, // JSON Web Key
    DID, // Decentralized Identifier
    AUTO, // Automatic selection based on identifier
}

/**
 * Validation result for JWS verification
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsValidationResult", exact = true)
@JsExportCompat
data class
JwsValidationResult
    @JvmOverloads
    constructor(
        val jws: JwsJsonGeneralWithIdentifiers,
        val isValid: Boolean,
        val errorMessages: List<String> = emptyList(),
        val verificationTime: Long = Clock.System.now().toEpochMilliseconds(),
        val parsedPayload: JsonObject,
    ) {
        val isCritical: Boolean
            get() = !isValid
    }

/**
 * Utility functions for JWS type checking
 */
object JwsTypeUtils {
    private val COMPACT_JWS_REGEX = Regex("^([a-zA-Z0-9_=-]+)\\.([a-zA-Z0-9_=-]+)?\\.([a-zA-Z0-9_=-]+)?$")

    fun isJwsCompact(jws: Jws): Boolean = jws is JwsCompact

    fun isJwsJsonFlattened(jws: Jws): Boolean = jws is JwsJsonFlattened

    fun isJwsJsonGeneral(jws: Jws): Boolean = jws is JwsJsonGeneral

    fun isValidCompactFormat(value: String): Boolean = value.split("~")[0].matches(COMPACT_JWS_REGEX)
}

/**
 * Assemble a JWS JSON General from a prepared object and externally-computed signature bytes.
 * This is the "complete" step in two-step JWS signing.
 */
fun PreparedJwsObject.assembleGeneral(signatureBytes: ByteArray): JwsJsonGeneral {
    val base64UrlSignature = signatureBytes.encodeTo(Encoding.BASE64URL)
    val signature =
        JwsJsonSignature(
            protected = b64.protectedHeader,
            header = jws.unprotectedHeader?.underlying,
            signature = base64UrlSignature,
        )
    val allSignatures = (jws.existingSignatures ?: emptyList()) + signature
    return JwsJsonGeneral(payload = b64.payload, signatures = allSignatures)
}

/**
 * Assemble a JWS JSON Flattened from a prepared object and externally-computed signature bytes.
 */
fun PreparedJwsObject.assembleFlattened(signatureBytes: ByteArray): JwsJsonFlattened {
    val general = assembleGeneral(signatureBytes)
    require(general.signatures.size == 1) { "Flattened JWS must have exactly one signature" }
    val sig = general.signatures.first()
    return JwsJsonFlattened(
        payload = general.payload,
        protected = sig.protected,
        header = sig.header,
        signature = sig.signature,
    )
}

/**
 * Assemble a compact JWS (JWT) from a prepared object and externally-computed signature bytes.
 */
fun PreparedJwsObject.assembleCompact(signatureBytes: ByteArray): JwtCompactResult {
    val flattened = assembleFlattened(signatureBytes)
    return JwtCompactResult(jwt = "${flattened.protected}.${flattened.payload}.${flattened.signature}")
}
