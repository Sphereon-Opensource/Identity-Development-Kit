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

@file:OptIn(ExperimentalJsExport::class)

package com.sphereon.sdjwt

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.jose.jws.JwsCompact
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.ExperimentalJsExport
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * SD-JWT (Selective Disclosure JSON Web Token) as defined in RFC 9901
 *
 * An SD-JWT consists of:
 * - A JWT with selectively disclosable claims replaced with digests
 * - Zero or more disclosures (base64url-encoded [salt, claim_name, claim_value])
 * - Optionally a Key Binding JWT for holder authentication
 *
 * Serialization format: jwt~disclosure1~disclosure2~...~kbJwt
 *
 * @param JwtType The type of JWT (e.g., JwsCompact, JwsJsonGeneral)
 * @param jwt The signed JWT containing the undisclosed payload with digests
 * @param header The JWT header (parsed from jwt)
 * @param payload The payload containing both undisclosed fields and _sd array
 * @param disclosures List of disclosure objects included with this SD-JWT
 * @param keyBindingJwt Optional KB-JWT for holder proof-of-possession
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwt", exact = true)
@JsExportCompat
data class SdJwt<JwtType>(
    val jwt: JwtType,
    val header: JsonObject,
    val payload: SdJwtPayload,
    val disclosures: List<Disclosure> = emptyList(),
    val keyBindingJwt: KeyBindingJwt? = null,
) where JwtType : Any {
    /**
     * The algorithm from the JWT header
     */
    val algorithm: String?
        get() = header["alg"]?.toString()?.trim('"')

    /**
     * The key ID from the JWT header if present
     */
    val keyId: String?
        get() = header["kid"]?.toString()?.trim('"')

    /**
     * The type from the JWT header
     */
    val type: String?
        get() = header["typ"]?.toString()?.trim('"')

    companion object {
        /**
         * The standard claim name for selective disclosure digests array
         */
        const val SD_CLAIM = "_sd"

        /**
         * The standard claim name for selective disclosure algorithm
         */
        const val SD_ALG_CLAIM = "_sd_alg"

        /**
         * Separator character used in SD-JWT serialization
         */
        const val SEPARATOR = '~'

        /**
         * Default hash algorithm for SD-JWT per RFC 9901 (SHA-256)
         */
        val DEFAULT_HASH_ALG = DigestAlg.SHA256
    }
}

/**
 * Compact representation of SD-JWT using JwsCompact
 */
typealias SdJwtCompact = SdJwt<JwsCompact>

/**
 * Represents the payload of an SD-JWT containing:
 * - The undisclosed payload (with digests in _sd array)
 * - The full payload (with all disclosed claims resolved)
 * - Mapping of digests to disclosures
 *
 * @param undisclosedPayload The JWT payload with selectively disclosable claims removed and replaced with digests
 * @param fullPayload The complete payload with all disclosed claims resolved (may be partial if not all disclosures provided)
 * @param digestedDisclosures Map of disclosure digests to their corresponding Disclosure objects
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtPayload", exact = true)
@JsExportCompat
data class SdJwtPayload(
    val undisclosedPayload: JsonObject,
    val fullPayload: JsonObject,
    val digestedDisclosures: Map<String, Disclosure> = emptyMap(),
)

/**
 * Represents a single disclosure in an SD-JWT
 *
 * A disclosure is an array: [salt, claim_name, claim_value]
 * It is base64url-encoded when included in an SD-JWT
 *
 * @param salt The random salt value
 * @param key The claim name
 * @param value The claim value (can be any JSON type)
 * @param encoded The base64url-encoded disclosure string
 * @param digest The hash digest of the encoded disclosure
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Disclosure", exact = true)
@JsExportCompat
data class Disclosure(
    val salt: String,
    val key: String,
    val value: JsonElement,
    val encoded: String,
    val digest: String? = null,
) {
    companion object {
        /**
         * Creates a disclosure for an object property (named claim).
         * Format: [salt, claim_name, claim_value]
         */
        fun objectProperty(
            saltProvider: SaltProvider,
            claimName: String,
            claimValue: JsonElement,
        ): Disclosure {
            val salt = saltProvider.generateSalt()
            val disclosureArray =
                buildJsonArray {
                    add(salt)
                    add(claimName)
                    add(claimValue)
                }
            val encoded =
                Json
                    .encodeToString(JsonArray.serializer(), disclosureArray)
                    .encodeToByteArray()
                    .encodeToBase64Url()
            return Disclosure(
                salt = salt,
                key = claimName,
                value = claimValue,
                encoded = encoded,
            )
        }

        /**
         * Creates a disclosure for an array element (unnamed value).
         * Format: [salt, claim_value]
         */
        fun arrayElement(
            saltProvider: SaltProvider,
            elementValue: JsonElement,
        ): Disclosure {
            val salt = saltProvider.generateSalt()
            val disclosureArray =
                buildJsonArray {
                    add(salt)
                    add(elementValue)
                }
            val encoded =
                Json
                    .encodeToString(JsonArray.serializer(), disclosureArray)
                    .encodeToByteArray()
                    .encodeToBase64Url()
            return Disclosure(
                salt = salt,
                key = "", // Array elements don't have a key
                value = elementValue,
                encoded = encoded,
            )
        }
    }
}

/**
 * Provider interface for generating cryptographic salts
 * Used during SD-JWT issuance to create random salts for disclosures
 */
interface SaltProvider {
    /**
     * Generate a random salt string
     * @param length The desired length in bytes (default 16)
     */
    fun generateSalt(length: Int = 16): String
}

/**
 * Mode for generating decoy digests
 * Decoy digests are added to the _sd array to obscure the number of actual selective disclosures
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecoyMode", exact = true)
@JsExportCompat
enum class DecoyMode {
    /** No decoy digests */
    NONE,

    /** Fixed number of decoy digests */
    FIXED,

    /** Minimum number of digests (adds decoys to reach minimum) */
    MINIMUM,

    /** Random number of decoy digests (up to specified maximum) */
    RANDOM,
}

/**
 * Configuration for decoy digest generation
 *
 * @param mode The decoy generation mode
 * @param count For FIXED mode: exact count, for RANDOM mode: maximum count
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecoyConfig", exact = true)
@JsExportCompat
@Serializable
data class DecoyConfig(
    val mode: DecoyMode = DecoyMode.NONE,
    val count: Int = 0,
)

/**
 * Describes selective disclosure configuration for a single field
 *
 * @param sd Whether this field should be selectively disclosable
 * @param children Nested selective disclosure configuration for child fields (if this is an object)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdField", exact = true)
@JsExportCompat
@Serializable
data class SdField(
    val sd: Boolean,
    val children: SdMap? = null,
)

/**
 * Map describing selective disclosure configuration for multiple fields
 * Represents a recursive structure defining which fields should be selectively disclosable
 *
 * @param fields Map of field names to their SD configuration
 * @param decoyConfig Configuration for decoy digest generation at this level
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdMap", exact = true)
@JsExportCompat
@Serializable
data class SdMap(
    val fields: Map<String, SdField>,
    val decoyConfig: DecoyConfig = DecoyConfig(),
) {
    operator fun get(key: String): SdField? = fields[key]

    fun containsKey(key: String): Boolean = fields.containsKey(key)

    val keys: Set<String> get() = fields.keys

    val values: Collection<SdField> get() = fields.values

    val entries: Set<Map.Entry<String, SdField>> get() = fields.entries

    fun isEmpty(): Boolean = fields.isEmpty()
}

/**
 * Key Binding JWT (KB-JWT) for holder authentication in SD-JWT presentations
 *
 * The KB-JWT proves possession of the holder key and binds the presentation to a specific verifier
 * It must contain "aud", "nonce", "iat", and "sd_hash" claims
 *
 * @param jwt The signed JWT
 * @param header The JWT header
 * @param payload The JWT payload containing required claims (aud, nonce, iat, sd_hash)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyBindingJwt", exact = true)
@JsExportCompat
data class KeyBindingJwt(
    val jwt: String,
    val header: JsonObject,
    val payload: JsonObject,
) {
    val audience: String?
        get() = payload["aud"]?.toString()?.trim('"')

    val nonce: String?
        get() = payload["nonce"]?.toString()?.trim('"')

    val issuedAt: Long?
        get() = payload["iat"]?.toString()?.toDoubleOrNull()?.toLong()

    val sdHash: String?
        get() = payload["sd_hash"]?.toString()?.trim('"')
}

/**
 * Result of SD-JWT verification
 *
 * @param sdJwt The verified SD-JWT
 * @param signatureValid Whether the JWT signature is valid
 * @param disclosuresValid Whether all disclosure digests match their values
 * @param keyBindingValid Whether the key binding JWT is valid (if present)
 * @param errorMessages List of validation error messages
 * @param verificationTime When the verification was performed
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtVerificationResult", exact = true)
@JsExportCompat
data class SdJwtVerificationResult(
    val sdJwt: SdJwtCompact,
    val signatureValid: Boolean,
    val disclosuresValid: Boolean,
    val keyBindingValid: Boolean = true,
    val errorMessages: List<String> = emptyList(),
    val verificationTime: Long = Clock.System.now().toEpochMilliseconds(),
) {
    /**
     * True if all aspects of verification passed
     */
    val isValid: Boolean
        get() = signatureValid && disclosuresValid && keyBindingValid && errorMessages.isEmpty()
}

/**
 * Configuration for SD-JWT specification compliance
 *
 * @param hashAlgorithm Hash algorithm to use for disclosure digests (default SHA-256 per RFC 9901)
 * @param includeAlgClaim Whether to include the _sd_alg claim in the JWT payload (optional per RFC 9901, but recommended)
 * @param decoyConfig Decoy configuration for this SD-JWT
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtSpec", exact = true)
@JsExportCompat
data class SdJwtSpec(
    val digestAlg: DigestAlg = DigestAlg.SHA256,
    val includeAlgClaim: Boolean = true,
    val decoyConfig: DecoyConfig = DecoyConfig(),
) {
    companion object {
        /**
         * Default SD-JWT specification: SHA-256, include alg claim, no decoys
         */
        val DEFAULT = SdJwtSpec()

        /**
         * Alias for DEFAULT for more idiomatic Kotlin usage
         */
        val Default = DEFAULT
    }
}

/**
 * Represents an unsigned SD-JWT ready for signing.
 *
 * @property jwtPayload The JWT payload with digests (to be signed)
 * @property disclosures The list of disclosures for selectively disclosable claims
 */
data class UnsignedSdJwt(
    val jwtPayload: JsonObject,
    val disclosures: List<Disclosure>,
)
