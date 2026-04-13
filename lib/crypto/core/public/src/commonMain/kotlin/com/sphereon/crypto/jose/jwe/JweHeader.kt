/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.jose.jwe

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import com.sphereon.core.compat.mergeJsonElement
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JWE (JSON Web Encryption) Header per RFC 7516
 *
 * This class wraps a JsonObject to support both standard JWE header parameters
 * and custom/extension parameters that may be added by different specifications
 * (e.g., OpenID Connect, OAuth extensions).
 *
 * Standard parameters are exposed as typed properties for compile-time safety,
 * while additional parameters can be accessed via the Map interface.
 *
 * RFC References:
 * - RFC 7516 Section 4.1: JWE Header Parameters
 * - RFC 7518 Section 4.6: ECDH-ES Parameters (epk, apu, apv)
 * - RFC 7518 Section 4.7: AES-GCM Key Wrap Parameters (iv, tag)
 * - RFC 7518 Section 4.8: PBES2 Parameters (p2s, p2c)
 *
 * @param underlying The underlying JsonObject that stores all header parameters
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweHeader", exact = true)
class JweHeader(
    initialUnderlying: JsonObject = JsonObject(mutableMapOf())
) {
    var underlying: JsonObject = initialUnderlying
        private set

    // Implement Map interface by delegating to current underlying, not initial
    operator fun get(key: String): JsonElement? = underlying[key]
    fun containsKey(key: String): Boolean = underlying.containsKey(key)
    val entries: Set<Map.Entry<String, JsonElement>> get() = underlying.entries
    val keys: Set<String> get() = underlying.keys
    val values: Collection<JsonElement> get() = underlying.values
    val size: Int get() = underlying.size
    fun isEmpty(): Boolean = underlying.isEmpty()

    // ========== Algorithm Header Parameters (RFC 7516 Section 4.1.1) ==========

    /**
     * "alg" (Algorithm) Header Parameter
     * REQUIRED. The cryptographic algorithm used to encrypt or determine the value
     * of the Content Encryption Key (CEK).
     * Examples: "RSA-OAEP", "RSA-OAEP-256", "A128KW", "ECDH-ES", "dir"
     */
    @Transient
    var alg: String?
        set(value) {
            putString("alg", value)
        }
        get() = get("alg")?.jsonPrimitive?.contentOrNull

    /**
     * "enc" (Encryption Algorithm) Header Parameter
     * REQUIRED. The content encryption algorithm used to encrypt the plaintext.
     * Examples: "A128GCM", "A192GCM", "A256GCM", "A128CBC-HS256", "A256CBC-HS512"
     */
    @Transient
    var enc: String?
        set(value) {
            putString("enc", value)
        }
        get() = get("enc")?.jsonPrimitive?.contentOrNull

    /**
     * "zip" (Compression Algorithm) Header Parameter
     * Optional. The compression algorithm applied to the plaintext before encryption.
     * Currently only "DEF" (DEFLATE) is defined.
     */
    @Transient
    var zip: String?
        set(value) {
            putString("zip", value)
        }
        get() = get("zip")?.jsonPrimitive?.contentOrNull

    // ========== Key Identification Parameters (RFC 7516 Section 4.1.4-4.1.10) ==========

    /**
     * "jku" (JWK Set URL) Header Parameter
     * Optional. A URI that refers to a resource for a set of JSON-encoded public keys,
     * one of which corresponds to the key used to encrypt the JWE.
     */
    @Transient
    var jku: String?
        set(value) {
            putString("jku", value)
        }
        get() = get("jku")?.jsonPrimitive?.contentOrNull

    /**
     * "jwk" (JSON Web Key) Header Parameter
     * Optional. The public key that corresponds to the key used to encrypt the JWE,
     * represented as a JWK.
     */
    @Transient
    var jwk: Jwk?
        set(value) {
            put("jwk", value?.toJsonObject())
        }
        get() = get("jwk")?.let { Jwk.fromJsonObject(it.jsonObject) }

    /**
     * "kid" (Key ID) Header Parameter
     * Optional. A hint indicating which key was used to encrypt the JWE.
     */
    @Transient
    var kid: String?
        set(value) {
            putString("kid", value)
        }
        get() = get("kid")?.jsonPrimitive?.contentOrNull

    /**
     * "x5u" (X.509 URL) Header Parameter
     * Optional. A URI that refers to a resource for the X.509 public key certificate
     * or certificate chain corresponding to the key used to encrypt the JWE.
     */
    @Transient
    var x5u: String?
        set(value) {
            putString("x5u", value)
        }
        get() = get("x5u")?.jsonPrimitive?.contentOrNull

    /**
     * "x5c" (X.509 Certificate Chain) Header Parameter
     * Optional. The X.509 public key certificate or certificate chain corresponding
     * to the key used to encrypt the JWE, represented as an array of base64-encoded
     * DER certificates.
     */
    @Transient
    var x5c: Array<String>?
        set(value) {
            put("x5c", value?.let { JsonArray(it.map { der -> JsonPrimitive(der) }) })
        }
        get() = get("x5c")?.jsonArray?.map { it.jsonPrimitive.content }?.toTypedArray()

    /**
     * "x5t" (X.509 Certificate SHA-1 Thumbprint) Header Parameter
     * Optional. Base64url-encoded SHA-1 thumbprint of the DER encoding of the
     * X.509 certificate corresponding to the key used to encrypt the JWE.
     */
    @Transient
    var x5t: String?
        set(value) {
            putString("x5t", value)
        }
        get() = get("x5t")?.jsonPrimitive?.contentOrNull

    /**
     * "x5t#S256" (X.509 Certificate SHA-256 Thumbprint) Header Parameter
     * Optional. Base64url-encoded SHA-256 thumbprint of the DER encoding of the
     * X.509 certificate corresponding to the key used to encrypt the JWE.
     */
    @Transient
    var x5tS256: String?
        set(value) {
            putString("x5t#S256", value)
        }
        get() = get("x5t#S256")?.jsonPrimitive?.contentOrNull

    // ========== Content Type Parameters (RFC 7516 Section 4.1.11-4.1.12) ==========

    /**
     * "typ" (Type) Header Parameter
     * Optional. The media type of the complete JWE.
     * Example: "JOSE", "JOSE+JSON", "JWT"
     */
    @Transient
    var typ: String?
        set(value) {
            putString("typ", value)
        }
        get() = get("typ")?.jsonPrimitive?.contentOrNull

    /**
     * "cty" (Content Type) Header Parameter
     * Optional. The media type of the secured content (the plaintext).
     * Example: "JWT" when encrypting a nested JWT
     */
    @Transient
    var cty: String?
        set(value) {
            putString("cty", value)
        }
        get() = get("cty")?.jsonPrimitive?.contentOrNull

    /**
     * "crit" (Critical) Header Parameter
     * Optional. An array of header parameter names that MUST be understood and processed.
     * If any listed parameter is not understood, the JWE MUST be rejected.
     */
    @Transient
    var crit: Array<String>?
        set(value) {
            put("crit", value?.let { JsonArray(it.map { name -> JsonPrimitive(name) }) })
        }
        get() = get("crit")?.jsonArray?.map { it.jsonPrimitive.content }?.toTypedArray()

    // ========== ECDH-ES Parameters (RFC 7518 Section 4.6) ==========

    /**
     * "epk" (Ephemeral Public Key) Header Parameter
     * Required for ECDH-ES algorithms. The ephemeral public key created by the originator
     * for the use in key agreement algorithms.
     */
    @Transient
    var epk: Jwk?
        set(value) {
            put("epk", value?.toJsonObject())
        }
        get() = get("epk")?.let { Jwk.fromJsonObject(it.jsonObject) }

    /**
     * "apu" (Agreement PartyUInfo) Header Parameter
     * Optional for ECDH-ES. Base64url-encoded value for use in key derivation.
     * PartyUInfo value for the producer (originator).
     */
    @Transient
    var apu: String?
        set(value) {
            putString("apu", value)
        }
        get() = get("apu")?.jsonPrimitive?.contentOrNull

    /**
     * "apv" (Agreement PartyVInfo) Header Parameter
     * Optional for ECDH-ES. Base64url-encoded value for use in key derivation.
     * PartyVInfo value for the recipient.
     */
    @Transient
    var apv: String?
        set(value) {
            putString("apv", value)
        }
        get() = get("apv")?.jsonPrimitive?.contentOrNull

    // ========== AES-GCM Key Wrap Parameters (RFC 7518 Section 4.7) ==========

    /**
     * "iv" (Initialization Vector) Header Parameter
     * Required for AES-GCMKW algorithms. Base64url-encoded initialization vector
     * used for key encryption (not content encryption).
     */
    @Transient
    var iv: String?
        set(value) {
            putString("iv", value)
        }
        get() = get("iv")?.jsonPrimitive?.contentOrNull

    /**
     * "tag" (Authentication Tag) Header Parameter
     * Required for AES-GCMKW algorithms. Base64url-encoded authentication tag
     * resulting from key encryption (not content encryption).
     */
    @Transient
    var tag: String?
        set(value) {
            putString("tag", value)
        }
        get() = get("tag")?.jsonPrimitive?.contentOrNull

    // ========== PBES2 Parameters (RFC 7518 Section 4.8) ==========

    /**
     * "p2s" (PBES2 Salt Input) Header Parameter
     * Required for PBES2 algorithms. Base64url-encoded Salt Input value used in the
     * PBKDF2 key derivation function.
     */
    @Transient
    var p2s: String?
        set(value) {
            putString("p2s", value)
        }
        get() = get("p2s")?.jsonPrimitive?.contentOrNull

    /**
     * "p2c" (PBES2 Count) Header Parameter
     * Required for PBES2 algorithms. The iteration count used in the PBKDF2
     * key derivation function. Must be a positive integer.
     */
    @Transient
    var p2c: Int?
        set(value) {
            putNumber("p2c", value)
        }
        get() = get("p2c")?.jsonPrimitive?.int

    // ========== Helper Methods ==========

    /**
     * Convert this header to a JsonObject
     */
    fun toJson(): JsonObject = underlying

    /**
     * Convert this header to a JSON string
     */
    fun toJsonString(): String = cryptoJsonSerializer.encodeToString(underlying)

    /**
     * Get a primitive value by key
     */
    fun getPrimitive(key: String): JsonPrimitive? = underlying[key]?.jsonPrimitive

    /**
     * Get a string value by key
     */
    fun getString(key: String): String? = underlying[key]?.jsonPrimitive?.content

    /**
     * Get a boolean value by key
     */
    fun getBoolean(key: String): Boolean? = underlying[key]?.jsonPrimitive?.boolean

    /**
     * Put a JsonElement value
     */
    fun put(key: String, value: JsonElement?) = apply {
        if (value !== null) {
            underlying = underlying.mergeJsonElement(key, value)
        }
    }

    /**
     * Put a string value
     */
    fun putString(key: String, value: String?) = apply {
        put(key, JsonPrimitive(value))
    }

    /**
     * Put a number value
     */
    fun putNumber(key: String, value: Number?) = apply {
        put(key, JsonPrimitive(value))
    }

    companion object {
        /**
         * Create a JweHeader from a JSON string
         */
        @JsName("fromJsonString")
        fun fromJson(json: String): JweHeader {
            val jsonObject = cryptoJsonSerializer.decodeFromString<JsonObject>(json)
            return JweHeader(jsonObject)
        }

        /**
         * Create a JweHeader from a JsonObject
         */
        @JsName("fromJsonObject")
        fun fromJson(json: JsonObject): JweHeader {
            return JweHeader(json)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as JweHeader
        return underlying == other.underlying
    }

    override fun hashCode(): Int {
        return underlying.hashCode()
    }

    override fun toString(): String {
        return toJsonString()
    }
}
