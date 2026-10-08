/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt.dsl

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.jose.jws.JwsPayloadBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName

// ============================================================================
// SECURITY NOTE (RFC 9901 Section 9.7):
// The following security-sensitive claims SHOULD NOT be made selectively disclosable:
// - iss (issuer) - Needed for trust establishment and signature verification
// - aud (audience) - Needed for determining if the JWT is intended for the verifier
// - exp (expiration) - Needed for token lifetime validation
// - nbf (not before) - Needed for token lifetime validation
// - cnf (confirmation) - Needed for holder binding validation
//
// For this reason, dedicated *Sd() functions are NOT provided for these claims.
// If you have a specific use case that requires making these claims selectively
// disclosable, you can use the generic claimSd() function:
//
//   claimSd("iss", "https://issuer.example.com")  // NOT RECOMMENDED
//   claimSd("exp", 1234567890L)                   // NOT RECOMMENDED
//
// However, be aware that making these claims SD may prevent verifiers from
// properly validating the JWT's trust, audience, or lifetime.
// ============================================================================

/**
 * Builder for SD-JWT payloads that cleanly separates claims from selective disclosure metadata.
 *
 * This builder wraps [JwsPayloadBuilder] and tracks SD metadata separately, eliminating
 * the need for workarounds like storing metadata as a special claim. The result is a
 * clean [SdJwtPayload] that contains both the claims and the SD configuration.
 *
 * ## Design Principle: SD-JWT = JWT + Selective Disclosure
 *
 * An SD-JWT is fundamentally a JWT with selective disclosure capabilities. This builder
 * reflects that relationship:
 * - Standard JWT methods (`iss()`, `sub()`, `aud()`, etc.) work identically to [JwsPayloadBuilder]
 * - SD-specific methods (`subSd()`, `claimSd()`, etc.) add selective disclosure capability
 * - Naming is consistent: `claim()` for regular claims, `claimSd()` for SD claims
 *
 * ## Usage
 *
 * ```kotlin
 * val payload = sdJwtPayload {
 *     // Standard JWT claims
 *     iss("https://issuer.example.com")
 *     aud("https://verifier.example.com")
 *     exp(System.currentTimeMillis() / 1000 + 3600)
 *
 *     // Selectively disclosable claims
 *     subSd("user-123")
 *     claimSd("email", "user@example.com")
 *     claimSd("age", 25)
 *
 *     // Non-SD custom claims
 *     claim("verified", true)
 *
 *     // Nested SD objects
 *     objSd("address") {
 *         claim("street", "123 Main St")
 *         claim("city", "Springfield")
 *         claimSd("zip", "12345")
 *     }
 *
 *     // Decoy configuration
 *     minimumDigests(5)
 * }
 * ```
 *
 * @see sdJwtPayload Entry point function for this builder
 * @see SdJwtPayload The result type containing claims and SD metadata
 */
@SdJwtDslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtPayloadBuilder", exact = true)
@JsExportCompat
class SdJwtPayloadBuilder {
    // Delegate to JwsPayloadBuilder for standard JWT functionality
    private val jwtBuilder = JwsPayloadBuilder()

    // Track SD metadata separately (NOT as a claim)
    private val sdClaims = mutableSetOf<String>()
    private var minimumDigests: Int? = null

    // ========================================================================
    // Standard JWT Methods (delegate to JwsPayloadBuilder)
    // ========================================================================

    /**
     * Sets the issuer (iss) claim.
     *
     * Note: Per RFC 9901 Section 9.7, the issuer claim SHOULD NOT be made selectively
     * disclosable as it's needed for trust establishment and signature verification.
     *
     * @param value The issuer identifier (typically a URL)
     * @return This builder for chaining
     */
    fun iss(value: String) = apply { jwtBuilder.iss(value) }

    /**
     * Sets the subject (sub) claim as a regular (non-SD) claim.
     *
     * Use [subSd] if you want the subject to be selectively disclosable.
     *
     * @param value The subject identifier
     * @return This builder for chaining
     */
    fun sub(value: String) = apply { jwtBuilder.sub(value) }

    /**
     * Sets the audience (aud) claim with a single value.
     *
     * Note: Per RFC 9901 Section 9.7, the audience claim SHOULD NOT be made selectively
     * disclosable as it's needed for determining if the JWT is intended for the verifier.
     *
     * @param value The audience identifier
     * @return This builder for chaining
     */
    @JsName("audSingle")
    fun aud(value: String) = apply { jwtBuilder.aud(value) }

    /**
     * Sets the audience (aud) claim with multiple values.
     *
     * @param values The audience identifiers
     * @return This builder for chaining
     */
    @JsName("audMultiple")
    fun aud(vararg values: String) = apply { jwtBuilder.aud(*values) }

    /**
     * Sets the expiration time (exp) claim.
     *
     * Note: Per RFC 9901 Section 9.7, the expiration claim SHOULD NOT be made selectively
     * disclosable as it's needed for token lifetime validation.
     *
     * @param value The expiration time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("expLong")
    fun exp(value: Long) = apply { jwtBuilder.exp(value) }

    /**
     * Sets the expiration time (exp) claim.
     *
     * @param value The expiration time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("expNumber")
    fun exp(value: Number) = apply { jwtBuilder.exp(value) }

    /**
     * Sets the not before (nbf) claim.
     *
     * Note: Per RFC 9901 Section 9.7, the not-before claim SHOULD NOT be made selectively
     * disclosable as it's needed for token lifetime validation.
     *
     * @param value The not before time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("nbfLong")
    fun nbf(value: Long) = apply { jwtBuilder.nbf(value) }

    /**
     * Sets the not before (nbf) claim.
     *
     * @param value The not before time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("nbfNumber")
    fun nbf(value: Number) = apply { jwtBuilder.nbf(value) }

    /**
     * Sets the issued at (iat) claim as a regular (non-SD) claim.
     *
     * Use [iatSd] if you want the issued-at time to be selectively disclosable.
     *
     * @param value The issued at time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("iatLong")
    fun iat(value: Long) = apply { jwtBuilder.iat(value) }

    /**
     * Sets the issued at (iat) claim as a regular (non-SD) claim.
     *
     * @param value The issued at time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("iatNumber")
    fun iat(value: Number) = apply { jwtBuilder.iat(value) }

    /**
     * Sets the JWT ID (jti) claim as a regular (non-SD) claim.
     *
     * Use [jtiSd] if you want the JWT ID to be selectively disclosable.
     *
     * @param value The JWT identifier
     * @return This builder for chaining
     */
    fun jti(value: String) = apply { jwtBuilder.jti(value) }

    // ========================================================================
    // Custom Claims (non-SD)
    // ========================================================================

    /**
     * Adds a custom claim (non-SD).
     *
     * Use [claimSd] if you want this claim to be selectively disclosable.
     *
     * @param name The claim name
     * @param value The claim value (String)
     * @return This builder for chaining
     */
    @JsName("claimString")
    fun claim(
        name: String,
        value: String,
    ) = apply { jwtBuilder.claim(name, value) }

    /**
     * Adds a custom claim (non-SD).
     *
     * @param name The claim name
     * @param value The claim value (Number)
     * @return This builder for chaining
     */
    @JsName("claimNumber")
    fun claim(
        name: String,
        value: Number,
    ) = apply { jwtBuilder.claim(name, value) }

    /**
     * Adds a custom claim (non-SD).
     *
     * @param name The claim name
     * @param value The claim value (Boolean)
     * @return This builder for chaining
     */
    @JsName("claimBoolean")
    fun claim(
        name: String,
        value: Boolean,
    ) = apply { jwtBuilder.claim(name, value) }

    /**
     * Adds a custom claim with a JsonElement value (non-SD).
     *
     * @param name The claim name
     * @param value The JsonElement value
     * @return This builder for chaining
     */
    @JsName("claimJsonElement")
    fun claim(
        name: String,
        value: JsonElement,
    ) = apply { jwtBuilder.claim(name, value) }

    // ========================================================================
    // SD-JWT Specific Methods
    // ========================================================================

    /**
     * Sets the subject (sub) claim as selectively disclosable.
     *
     * When the SD-JWT is issued, the subject will be hidden in the JWT payload
     * and replaced with a digest. The actual value will be provided as a disclosure.
     *
     * @param value The subject identifier
     * @return This builder for chaining
     */
    fun subSd(value: String) =
        apply {
            jwtBuilder.sub(value)
            sdClaims += "sub"
        }

    /**
     * Sets the issued at (iat) claim as selectively disclosable.
     *
     * Note: While RFC 9901 Section 9.7 mentions security considerations for timing claims,
     * iat is primarily informational (unlike exp/nbf which are used for validation).
     * Consider your use case carefully.
     *
     * @param value The issued at time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("iatSdLong")
    fun iatSd(value: Long) =
        apply {
            jwtBuilder.iat(value)
            sdClaims += "iat"
        }

    /**
     * Sets the issued at (iat) claim as selectively disclosable.
     *
     * @param value The issued at time in seconds since epoch
     * @return This builder for chaining
     */
    @JsName("iatSdNumber")
    fun iatSd(value: Number) =
        apply {
            jwtBuilder.iat(value)
            sdClaims += "iat"
        }

    /**
     * Sets the JWT ID (jti) claim as selectively disclosable.
     *
     * @param value The JWT identifier
     * @return This builder for chaining
     */
    fun jtiSd(value: String) =
        apply {
            jwtBuilder.jti(value)
            sdClaims += "jti"
        }

    /**
     * Adds a custom claim as selectively disclosable.
     *
     * When the SD-JWT is issued, this claim will be hidden in the JWT payload
     * and replaced with a digest in the `_sd` array. The actual value will be
     * provided as a disclosure.
     *
     * Example:
     * ```kotlin
     * sdJwtPayload {
     *     iss("https://issuer.example.com")
     *     claimSd("email", "user@example.com")  // Selectively disclosable
     *     claimSd("ssn", "123-45-6789")         // Selectively disclosable
     * }
     * ```
     *
     * @param name The claim name
     * @param value The claim value (String)
     * @return This builder for chaining
     */
    @JsName("claimSdString")
    fun claimSd(
        name: String,
        value: String,
    ) = apply {
        jwtBuilder.claim(name, value)
        sdClaims += name
    }

    /**
     * Adds a custom claim as selectively disclosable.
     *
     * @param name The claim name
     * @param value The claim value (Number)
     * @return This builder for chaining
     */
    @JsName("claimSdNumber")
    fun claimSd(
        name: String,
        value: Number,
    ) = apply {
        jwtBuilder.claim(name, value)
        sdClaims += name
    }

    /**
     * Adds a custom claim as selectively disclosable.
     *
     * @param name The claim name
     * @param value The claim value (Boolean)
     * @return This builder for chaining
     */
    @JsName("claimSdBoolean")
    fun claimSd(
        name: String,
        value: Boolean,
    ) = apply {
        jwtBuilder.claim(name, value)
        sdClaims += name
    }

    /**
     * Adds a custom claim as selectively disclosable with a JsonElement value.
     *
     * @param name The claim name
     * @param value The JsonElement value
     * @return This builder for chaining
     */
    @JsName("claimSdJsonElement")
    fun claimSd(
        name: String,
        value: JsonElement,
    ) = apply {
        jwtBuilder.claim(name, value)
        sdClaims += name
    }

    /**
     * Sets a nested object claim as selectively disclosable.
     *
     * The entire object will be hidden by default and only revealed when explicitly disclosed.
     * Nested claims marked with [claimSd] will be tracked for recursive SD handling.
     *
     * Example:
     * ```kotlin
     * sdJwtPayload {
     *     iss("https://issuer.example.com")
     *     objSd("address") {
     *         claim("street", "123 Main St")
     *         claim("city", "Springfield")
     *         claimSd("zip", "12345")  // Nested SD claim
     *     }
     * }
     * ```
     *
     * @param name The claim name for the nested object
     * @param builder Function to configure the nested object
     * @return This builder for chaining
     */
    fun objSd(
        name: String,
        builder: SdJwtPayloadBuilder.() -> Unit,
    ) = apply {
        val nested = SdJwtPayloadBuilder().apply(builder)
        val nestedPayload = nested.build()

        // Add the nested claims to our builder
        jwtBuilder.claim(name, nestedPayload.claims)

        // Mark the object itself as SD
        sdClaims += name

        // Track nested SD claims with dot notation for path-based access
        nestedPayload.sdClaims.forEach { nestedClaim ->
            sdClaims += "$name.$nestedClaim"
        }
    }

    /**
     * Sets the minimum number of digests in the `_sd` array.
     *
     * Decoy digests are fake digest values added to the `_sd` array to prevent
     * correlation attacks by making it harder to determine how many claims are
     * selectively disclosable. The issuer will add decoy digests as needed to
     * reach this minimum.
     *
     * Example:
     * ```kotlin
     * sdJwtPayload {
     *     iss("https://issuer.example.com")
     *     claimSd("email", "user@example.com")
     *     minimumDigests(5)  // Ensure at least 5 digests (1 real + 4 decoys)
     * }
     * ```
     *
     * @param count The minimum number of digests (must be > 0)
     * @return This builder for chaining
     * @throws IllegalArgumentException if count is not positive
     */
    fun minimumDigests(count: Int) =
        apply {
            require(count > 0) { "minimumDigests must be greater than 0" }
            minimumDigests = count
        }

    // ========================================================================
    // Build Methods
    // ========================================================================

    /**
     * Builds just the claims as a JsonObject, without SD metadata.
     *
     * This is useful when you need the raw claims for other purposes.
     * Note that SD metadata is not included in the result.
     *
     * @return The claims as a JsonObject
     */
    fun buildClaims(): JsonObject = jwtBuilder.build()

    /**
     * Builds the complete SD-JWT payload with claims and metadata.
     *
     * @return An [SdJwtPayload] containing the claims, SD claim names, and minimum digests
     */
    fun build(): SdJwtPayload =
        SdJwtPayload(
            claims = jwtBuilder.build(),
            sdClaims = sdClaims.toSet(),
            minimumDigests = minimumDigests,
        )
}
