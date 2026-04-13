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
 */

package com.sphereon.crypto.jose.jws

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// ============================================================================
// Header Builder
// ============================================================================

/**
 * Builder for JWS protected or unprotected headers.
 * Provides a fluent API for constructing JWT headers with standard and custom claims.
 *
 * Example:
 * ```kotlin
 * val header = JwsHeaderBuilder()
 *     .algorithm("ES256")
 *     .keyId("key-123")
 *     .type("JWT")
 *     .claim("custom_claim", "custom_value")
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsHeaderBuilder", exact = true)
@JsExportCompat
class JwsHeaderBuilder {
    private val claims = mutableMapOf<String, JsonElement>()

    /**
     * Sets the algorithm (alg) header parameter.
     * @param value The algorithm name (e.g., "ES256", "RS256", "PS256")
     */
    fun alg(value: String): JwsHeaderBuilder {
        claims["alg"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the key ID (kid) header parameter.
     * @param value The key identifier
     */
    fun kid(value: String): JwsHeaderBuilder {
        claims["kid"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the type (typ) header parameter.
     * @param value The token type (e.g., "JWT")
     */
    fun typ(value: String): JwsHeaderBuilder {
        claims["typ"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the content type (cty) header parameter.
     * @param value The content type
     */
    fun cty(value: String): JwsHeaderBuilder {
        claims["cty"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the JWK (jwk) header parameter.
     * @param value The JWK as JsonElement
     */
    fun jwk(value: JsonElement): JwsHeaderBuilder {
        claims["jwk"] = value
        return this
    }

    /**
     * Sets the X.509 URL (jku) header parameter.
     * @param value The JWK Set URL
     */
    fun jku(value: String): JwsHeaderBuilder {
        claims["jku"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the X.509 certificate chain (x5c) header parameter.
     * @param certificates Array of base64-encoded certificates
     */
    fun x5c(vararg certificates: String): JwsHeaderBuilder {
        claims["x5c"] = kotlinx.serialization.json.JsonArray(
            certificates.map { JsonPrimitive(it) }
        )
        return this
    }

    /**
     * Sets the X.509 certificate SHA-1 thumbprint (x5t) header parameter.
     * @param value The base64url-encoded SHA-1 thumbprint
     */
    fun x5t(value: String): JwsHeaderBuilder {
        claims["x5t"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the X.509 certificate SHA-256 thumbprint (x5t#S256) header parameter.
     * @param value The base64url-encoded SHA-256 thumbprint
     */
    fun x5tS256(value: String): JwsHeaderBuilder {
        claims["x5t#S256"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the X.509 URL (x5u) header parameter.
     * @param value The X.509 certificate URL
     */
    fun x5u(value: String): JwsHeaderBuilder {
        claims["x5u"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the critical (crit) header parameter.
     * @param headers Array of header names that must be understood
     */
    fun crit(vararg headers: String): JwsHeaderBuilder {
        claims["crit"] = kotlinx.serialization.json.JsonArray(
            headers.map { JsonPrimitive(it) }
        )
        return this
    }

    /**
     * Adds custom header parameters using pairs.
     * @param pairs Pairs of name to value
     * Example: claim("key1" to "value1", "key2" to "value2")
     */
    @kotlin.js.JsName("claimPairs")
    fun claim(vararg pairs: Pair<String, Any>): JwsHeaderBuilder {
        pairs.forEach { (name, value) ->
            claims[name] = when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        return this
    }

    /**
     * Adds a custom header parameter.
     * @param name The parameter name
     * @param value The parameter value (String)
     */
    @kotlin.js.JsName("claimString")
    fun claim(name: String, value: String): JwsHeaderBuilder {
        claims[name] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds a custom header parameter.
     * @param name The parameter name
     * @param value The parameter value (Number)
     */
    @kotlin.js.JsName("claimNumber")
    fun claim(name: String, value: Number): JwsHeaderBuilder {
        claims[name] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds a custom header parameter.
     * @param name The parameter name
     * @param value The parameter value (Boolean)
     */
    @kotlin.js.JsName("claimBoolean")
    fun claim(name: String, value: Boolean): JwsHeaderBuilder {
        claims[name] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds a custom header parameter with a JsonElement value.
     * @param name The parameter name
     * @param value The JsonElement value
     */
    @kotlin.js.JsName("claimJsonElement")
    fun claim(name: String, value: JsonElement): JwsHeaderBuilder {
        claims[name] = value
        return this
    }

    /**
     * Merges all claims from another JsonObject into this builder.
     * @param headers The headers to merge
     */
    fun merge(headers: JsonObject): JwsHeaderBuilder {
        claims.putAll(headers)
        return this
    }

    /**
     * Builds the header as a JsonObject.
     */
    fun build(): JsonObject {
        return JsonObject(claims.toMap())
    }

    /**
     * Creates a new builder with the same claims.
     */
    fun copy(): JwsHeaderBuilder {
        val copy = JwsHeaderBuilder()
        copy.claims.putAll(this.claims)
        return copy
    }

    companion object {
        /**
         * Creates a new header builder.
         */
        fun create(): JwsHeaderBuilder = JwsHeaderBuilder()

        /**
         * Creates a new header builder from an existing JsonObject.
         */
        fun from(headers: JsonObject): JwsHeaderBuilder {
            return JwsHeaderBuilder().merge(headers)
        }
    }
}

// ============================================================================
// Payload Builder
// ============================================================================

/**
 * Builder for JWS/JWT payload (claims set).
 * Provides a fluent API for constructing JWT payloads with standard and custom claims.
 *
 * Example:
 * ```kotlin
 * val payload = JwsPayloadBuilder()
 *     .issuer("https://issuer.example.com")
 *     .subject("user-123")
 *     .audience("https://audience.example.com")
 *     .expirationTime(Clock.System.now().plus(1.hours))
 *     .claim("email", "user@example.com")
 *     .claim("roles", listOf("admin", "user"))
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsPayloadBuilder", exact = true)
@JsExportCompat
class JwsPayloadBuilder {
    private val claims = mutableMapOf<String, JsonElement>()

    /**
     * Sets the issuer (iss) claim.
     * @param value The issuer identifier
     */
    fun iss(value: String): JwsPayloadBuilder {
        claims["iss"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the subject (sub) claim.
     * @param value The subject identifier
     */
    fun sub(value: String): JwsPayloadBuilder {
        claims["sub"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the audience (aud) claim with a single value.
     * @param value The audience identifier
     */
    @kotlin.js.JsName("audSingle")
    fun aud(value: String): JwsPayloadBuilder {
        claims["aud"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the audience (aud) claim with multiple values.
     * @param values The audience identifiers
     */
    @kotlin.js.JsName("audMultiple")
    fun aud(vararg values: String): JwsPayloadBuilder {
        claims["aud"] = kotlinx.serialization.json.JsonArray(
            values.map { JsonPrimitive(it) }
        )
        return this
    }

    /**
     * Sets the expiration time (exp) claim.
     * @param value The expiration time in seconds since epoch
     */
    @kotlin.js.JsName("expLong")
    fun exp(value: Long): JwsPayloadBuilder {
        claims["exp"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the expiration time (exp) claim.
     * @param value The expiration time in seconds since epoch
     */
    @kotlin.js.JsName("expNumber")
    fun exp(value: Number): JwsPayloadBuilder {
        claims["exp"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the not before (nbf) claim.
     * @param value The not before time in seconds since epoch
     */
    @kotlin.js.JsName("nbfLong")
    fun nbf(value: Long): JwsPayloadBuilder {
        claims["nbf"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the not before (nbf) claim.
     * @param value The not before time in seconds since epoch
     */
    @kotlin.js.JsName("nbfNumber")
    fun nbf(value: Number): JwsPayloadBuilder {
        claims["nbf"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the issued at (iat) claim.
     * @param value The issued at time in seconds since epoch
     */
    @kotlin.js.JsName("iatLong")
    fun iat(value: Long): JwsPayloadBuilder {
        claims["iat"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the issued at (iat) claim.
     * @param value The issued at time in seconds since epoch
     */
    @kotlin.js.JsName("iatNumber")
    fun iat(value: Number): JwsPayloadBuilder {
        claims["iat"] = JsonPrimitive(value)
        return this
    }

    /**
     * Sets the JWT ID (jti) claim.
     * @param value The JWT identifier
     */
    fun jti(value: String): JwsPayloadBuilder {
        claims["jti"] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds custom claims using pairs.
     * @param pairs Pairs of claim name to value
     * Example: claim("email" to "user@example.com", "role" to "admin", "age" to 25)
     */
    @kotlin.js.JsName("claimPairs")
    fun claim(vararg pairs: Pair<String, Any>): JwsPayloadBuilder {
        pairs.forEach { (name, value) ->
            claims[name] = when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                else -> JsonPrimitive(value.toString())
            }
        }
        return this
    }

    /**
     * Adds a custom claim.
     * @param name The claim name
     * @param value The claim value (String)
     */
    @kotlin.js.JsName("claimString")
    fun claim(name: String, value: String): JwsPayloadBuilder {
        claims[name] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds a custom claim.
     * @param name The claim name
     * @param value The claim value (Number)
     */
    @kotlin.js.JsName("claimNumber")
    fun claim(name: String, value: Number): JwsPayloadBuilder {
        claims[name] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds a custom claim.
     * @param name The claim name
     * @param value The claim value (Boolean)
     */
    @kotlin.js.JsName("claimBoolean")
    fun claim(name: String, value: Boolean): JwsPayloadBuilder {
        claims[name] = JsonPrimitive(value)
        return this
    }

    /**
     * Adds a custom claim with a JsonElement value.
     * @param name The claim name
     * @param value The JsonElement value
     */
    @kotlin.js.JsName("claimJsonElement")
    fun claim(name: String, value: JsonElement): JwsPayloadBuilder {
        claims[name] = value
        return this
    }

    /**
     * Merges all claims from another JsonObject into this builder.
     * @param payload The payload to merge
     */
    fun merge(payload: JsonObject): JwsPayloadBuilder {
        claims.putAll(payload)
        return this
    }

    /**
     * Builds the payload as a JsonObject.
     */
    fun build(): JsonObject {
        return JsonObject(claims.toMap())
    }

    /**
     * Creates a new builder with the same claims.
     */
    fun copy(): JwsPayloadBuilder {
        val copy = JwsPayloadBuilder()
        copy.claims.putAll(this.claims)
        return copy
    }

    companion object {
        /**
         * Creates a new payload builder.
         */
        fun create(): JwsPayloadBuilder = JwsPayloadBuilder()

        /**
         * Creates a new payload builder from an existing JsonObject.
         */
        fun from(payload: JsonObject): JwsPayloadBuilder {
            return JwsPayloadBuilder().merge(payload)
        }
    }
}

// ============================================================================
// JWS Options Builder
// ============================================================================

/**
 * Builder for CreateJwsOpts.
 * Provides a fluent API for configuring JWS creation options.
 *
 * Example:
 * ```kotlin
 * val opts = JwsOptsBuilder()
 *     .noIssPayloadUpdate()
 *     .protectedHeader {
 *         algorithm("ES256")
 *         type("JWT")
 *     }
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwsOptsBuilder", exact = true)
@JsExportCompat
class JwsOptsBuilder {
    private var noIssPayloadUpdate: Boolean = false
    private var noIdentifierInHeader: Boolean = false
    private var protectedHeader: JsonObject? = null
    private var unprotectedHeader: JsonObject? = null

    /**
     * Disables automatic issuer/payload updates.
     */
    fun noIssPayloadUpdate(): JwsOptsBuilder {
        this.noIssPayloadUpdate = true
        return this
    }

    /**
     * Prevents identifier from being added to the header.
     */
    fun noIdentifierInHeader(): JwsOptsBuilder {
        this.noIdentifierInHeader = true
        return this
    }

    /**
     * Sets the protected header.
     * @param header The protected header JsonObject
     */
    @kotlin.js.JsName("protectedHeaderObject")
    fun protectedHeader(header: JsonObject): JwsOptsBuilder {
        this.protectedHeader = header
        return this
    }

    /**
     * Sets the protected header using a builder.
     * @param builder Function to configure the header builder
     */
    @kotlin.js.JsName("protectedHeaderBuilder")
    fun protectedHeader(builder: JwsHeaderBuilder.() -> Unit): JwsOptsBuilder {
        this.protectedHeader = JwsHeaderBuilder().apply(builder).build()
        return this
    }

    /**
     * Sets the unprotected header.
     * @param header The unprotected header JsonObject
     */
    @kotlin.js.JsName("unprotectedHeaderObject")
    fun unprotectedHeader(header: JsonObject): JwsOptsBuilder {
        this.unprotectedHeader = header
        return this
    }

    /**
     * Sets the unprotected header using a builder.
     * @param builder Function to configure the header builder
     */
    @kotlin.js.JsName("unprotectedHeaderBuilder")
    fun unprotectedHeader(builder: JwsHeaderBuilder.() -> Unit): JwsOptsBuilder {
        this.unprotectedHeader = JwsHeaderBuilder().apply(builder).build()
        return this
    }

    /**
     * Builds the options.
     */
    fun build(): CreateJwsOpts {
        return CreateJwsOpts(
            noIssPayloadUpdate = noIssPayloadUpdate,
            noIdentifierInHeader = noIdentifierInHeader,
            protectedHeader = protectedHeader,
            unprotectedHeader = unprotectedHeader
        )
    }

    companion object {
        /**
         * Creates a new options builder.
         */
        fun create(): JwsOptsBuilder = JwsOptsBuilder()
    }
}

// ============================================================================
// CreateJwsArgs Builder
// ============================================================================

/**
 * Builder for CreateJwsArgs (compact JWS).
 * Provides a fluent API for constructing JWS creation arguments.
 *
 * Example:
 * ```kotlin
 * val args = CreateJwsArgsBuilder()
 *     .issuer(myManagedKey)
 *     .payload {
 *         issuer("https://issuer.example.com")
 *         subject("user-123")
 *         claim("email", "user@example.com")
 *     }
 *     .mode(JwsIdentifierMode.KID)
 *     .options {
 *         protectedHeader {
 *             type("JWT")
 *         }
 *     }
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsArgsBuilder", exact = true)
@JsExportCompat
class CreateJwsArgsBuilder {
    private var issuer: ManagedIdentifierOptsOrResult? = null
    private var payload: Any? = null
    private var mode: JwsIdentifierMode = JwsIdentifierMode.AUTO
    private var opts: CreateJwsOpts = CreateJwsOpts()

    /**
     * Sets the issuer (signer) for the JWS.
     * @param issuer The managed identifier for signing
     */
    fun issuer(issuer: ManagedIdentifierOptsOrResult): CreateJwsArgsBuilder {
        this.issuer = issuer
        return this
    }

    /**
     * Sets the payload as a JsonObject.
     * @param payload The payload JsonObject
     */
    @kotlin.js.JsName("payloadObject")
    fun payload(payload: JsonObject): CreateJwsArgsBuilder {
        this.payload = payload
        return this
    }

    /**
     * Sets the payload using a builder.
     * @param builder Function to configure the payload builder
     */
    @kotlin.js.JsName("payloadBuilder")
    fun payload(builder: JwsPayloadBuilder.() -> Unit): CreateJwsArgsBuilder {
        this.payload = JwsPayloadBuilder().apply(builder).build()
        return this
    }

    /**
     * Sets the payload as a String.
     * @param payload The payload string
     */
    fun payloadString(payload: String): CreateJwsArgsBuilder {
        this.payload = payload
        return this
    }

    /**
     * Sets the payload as a ByteArray.
     * @param payload The payload bytes
     */
    fun payloadBytes(payload: ByteArray): CreateJwsArgsBuilder {
        this.payload = payload
        return this
    }

    /**
     * Sets the identifier mode.
     * @param mode The JwsIdentifierMode
     */
    fun mode(mode: JwsIdentifierMode): CreateJwsArgsBuilder {
        this.mode = mode
        return this
    }

    /**
     * Sets the JWS creation options.
     * @param opts The CreateJwsOpts
     */
    @kotlin.js.JsName("optionsObject")
    fun options(opts: CreateJwsOpts): CreateJwsArgsBuilder {
        this.opts = opts
        return this
    }

    /**
     * Sets the JWS creation options using a builder.
     * @param builder Function to configure the options builder
     */
    @kotlin.js.JsName("optionsBuilder")
    fun options(builder: JwsOptsBuilder.() -> Unit): CreateJwsArgsBuilder {
        this.opts = JwsOptsBuilder().apply(builder).build()
        return this
    }

    /**
     * Builds the CreateJwsArgs.
     */
    fun build(): CreateJwsArgs {
        return CreateJwsArgs(
            issuer = issuer,
            payload = payload,
            mode = mode,
            opts = opts
        )
    }

    companion object {
        /**
         * Creates a new CreateJwsArgs builder.
         */
        fun create(): CreateJwsArgsBuilder = CreateJwsArgsBuilder()
    }
}

// ============================================================================
// CreateJwsJsonArgs Builder
// ============================================================================

/**
 * Builder for CreateJwsJsonArgs (JSON JWS).
 * Provides a fluent API for constructing JSON JWS creation arguments.
 *
 * Example:
 * ```kotlin
 * val args = CreateJwsJsonArgsBuilder()
 *     .issuer(myManagedKey)
 *     .payload {
 *         issuer("https://issuer.example.com")
 *         subject("user-123")
 *     }
 *     .mode(JwsIdentifierMode.JWK)
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonArgsBuilder", exact = true)
@JsExportCompat
class CreateJwsJsonArgsBuilder {
    private var issuer: ManagedIdentifierOptsOrResult? = null
    private var payload: Any? = null
    private var mode: JwsIdentifierMode = JwsIdentifierMode.AUTO
    private var opts: CreateJwsOpts = CreateJwsOpts()
    private var existingSignatures: List<JwsJsonSignature>? = null

    /**
     * Sets the issuer (signer) for the JWS.
     * @param issuer The managed identifier for signing
     */
    fun issuer(issuer: ManagedIdentifierOptsOrResult): CreateJwsJsonArgsBuilder {
        this.issuer = issuer
        return this
    }

    /**
     * Sets the payload as a JsonObject.
     * @param payload The payload JsonObject
     */
    @kotlin.js.JsName("payloadObject")
    fun payload(payload: JsonObject): CreateJwsJsonArgsBuilder {
        this.payload = payload
        return this
    }

    /**
     * Sets the payload using a builder.
     * @param builder Function to configure the payload builder
     */
    @kotlin.js.JsName("payloadBuilder")
    fun payload(builder: JwsPayloadBuilder.() -> Unit): CreateJwsJsonArgsBuilder {
        this.payload = JwsPayloadBuilder().apply(builder).build()
        return this
    }

    /**
     * Sets the payload as a String.
     * @param payload The payload string
     */
    fun payloadString(payload: String): CreateJwsJsonArgsBuilder {
        this.payload = payload
        return this
    }

    /**
     * Sets the payload as a ByteArray.
     * @param payload The payload bytes
     */
    fun payloadBytes(payload: ByteArray): CreateJwsJsonArgsBuilder {
        this.payload = payload
        return this
    }

    /**
     * Sets the identifier mode.
     * @param mode The JwsIdentifierMode
     */
    fun mode(mode: JwsIdentifierMode): CreateJwsJsonArgsBuilder {
        this.mode = mode
        return this
    }

    /**
     * Sets the JWS creation options.
     * @param opts The CreateJwsOpts
     */
    @kotlin.js.JsName("optionsObject")
    fun options(opts: CreateJwsOpts): CreateJwsJsonArgsBuilder {
        this.opts = opts
        return this
    }

    /**
     * Sets the JWS creation options using a builder.
     * @param builder Function to configure the options builder
     */
    @kotlin.js.JsName("optionsBuilder")
    fun options(builder: JwsOptsBuilder.() -> Unit): CreateJwsJsonArgsBuilder {
        this.opts = JwsOptsBuilder().apply(builder).build()
        return this
    }

    /**
     * Sets existing signatures (for adding to general JSON format).
     * @param signatures List of existing signatures
     */
    fun existingSignatures(signatures: List<JwsJsonSignature>): CreateJwsJsonArgsBuilder {
        this.existingSignatures = signatures
        return this
    }

    /**
     * Builds the CreateJwsJsonArgs.
     */
    fun build(): CreateJwsJsonArgs {
        return CreateJwsJsonArgs(
            issuer = issuer,
            payload = payload,
            mode = mode,
            opts = opts,
            existingSignatures = existingSignatures
        )
    }

    companion object {
        /**
         * Creates a new CreateJwsJsonArgs builder.
         */
        fun create(): CreateJwsJsonArgsBuilder = CreateJwsJsonArgsBuilder()
    }
}

// ============================================================================
// Extension Functions for Convenience
// ============================================================================

/**
 * Creates a JWS header using a builder DSL.
 */
fun jwsHeader(builder: JwsHeaderBuilder.() -> Unit): JsonObject {
    return JwsHeaderBuilder().apply(builder).build()
}

/**
 * Creates a JWS payload using a builder DSL.
 */
fun jwsPayload(builder: JwsPayloadBuilder.() -> Unit): JsonObject {
    return JwsPayloadBuilder().apply(builder).build()
}

/**
 * Creates JWS options using a builder DSL.
 */
fun jwsOptions(builder: JwsOptsBuilder.() -> Unit): CreateJwsOpts {
    return JwsOptsBuilder().apply(builder).build()
}

/**
 * Creates CreateJwsArgs using a builder DSL.
 */
fun createJwsArgs(builder: CreateJwsArgsBuilder.() -> Unit): CreateJwsArgs {
    return CreateJwsArgsBuilder().apply(builder).build()
}

/**
 * Creates CreateJwsJsonArgs using a builder DSL.
 */
fun createJwsJsonArgs(builder: CreateJwsJsonArgsBuilder.() -> Unit): CreateJwsJsonArgs {
    return CreateJwsJsonArgsBuilder().apply(builder).build()
}
