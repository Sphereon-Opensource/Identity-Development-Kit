/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.sdjwt

import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.native.ObjCName

/**
 * Builder for creating RFC 7800 Confirmation (cnf) claims for SD-JWT holder binding.
 *
 * The CNF claim establishes holder binding by proving possession of a private key.
 * This builder supports multiple confirmation methods per RFC 7800:
 *
 * - **jwk**: Include the public key as JWK directly (most common for SD-JWT)
 * - **kid**: Reference a key by identifier (e.g., DID verification method)
 * - **jku**: Reference a JWK Set URL
 *
 * ## DID-based Holder Binding
 *
 * When using DID-based holder binding, the CNF should contain:
 * - `kid`: The DID verification method ID (e.g., "did:key:z6Mk...#key-1")
 * - `jwk`: The public key JWK for backwards compatibility with verifiers that don't support DID resolution
 *
 * This dual approach ensures interoperability:
 * - Verifiers with DID support can resolve the kid to verify the holder
 * - Verifiers without DID support can use the embedded JWK
 *
 * Example:
 * ```kotlin
 * // Create CNF from DID verification method
 * val cnf = cnf {
 *     kid("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 *     jwk(holderPublicKeyJwk)  // Include JWK for backwards compatibility
 * }
 *
 * // Use in SD-JWT payload
 * val payload = JwsPayloadBuilder()
 *     .iss("https://issuer.example.com")
 *     .sub("user123")
 *     .claim("cnf", cnf)
 *     .build()
 * ```
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7800">RFC 7800 - Proof-of-Possession Key Semantics for JWTs</a>
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CnfBuilder", exact = true)
@JsExportCompat
class CnfBuilder {
    private var kid: String? = null
    private var jwk: JsonElement? = null
    private var jku: String? = null

    /**
     * Sets the key ID (kid) for the confirmation method.
     *
     * For DID-based holder binding, this should be the full DID verification method ID
     * (e.g., "did:key:z6Mk...#z6Mk...").
     *
     * @param kid The key identifier
     * @return This builder for chaining
     */
    fun kid(kid: String): CnfBuilder {
        this.kid = kid
        return this
    }

    /**
     * Sets the JWK for the confirmation method.
     *
     * Per RFC 7800 §3.1, the cnf.jwk contains the holder's public key as JWK.
     * This should contain only the public key material (no private key).
     *
     * For EC keys, include: kty, crv, x, y
     * For OKP keys, include: kty, crv, x
     * For RSA keys, include: kty, n, e
     *
     * @param jwk The public key JWK
     * @return This builder for chaining
     */
    fun jwk(jwk: Jwk): CnfBuilder {
        this.jwk = jwk.toJsonObject()
        return this
    }

    /**
     * Sets the JWK for the confirmation method using a JsonObject.
     *
     * @param jwk The public key JWK as JsonObject
     * @return This builder for chaining
     */
    @JsName("jwkJsonObject")
    fun jwk(jwk: JsonObject): CnfBuilder {
        this.jwk = jwk
        return this
    }

    /**
     * Sets the JWK Set URL for the confirmation method.
     *
     * Per RFC 7800 §3.5, the cnf.jku contains a URL to a JWK Set
     * containing the public key.
     *
     * @param jku The JWK Set URL
     * @return This builder for chaining
     */
    fun jku(jku: String): CnfBuilder {
        this.jku = jku
        return this
    }

    /**
     * Builds the CNF claim as a JsonObject.
     *
     * Validation rules:
     * - At least one confirmation method (kid, jwk, or jku) is required
     * - If only kid is provided (no jwk or jku), it MUST be a DID (starting with "did:")
     *   because non-DID kid values cannot be resolved to obtain key material
     * - If kid is a non-DID value, it can only be used as an identifier alongside jwk or jku
     *
     * @return The CNF claim
     * @throws IllegalArgumentException if validation fails
     */
    fun build(): JsonObject {
        require(kid != null || jwk != null || jku != null) {
            "At least one confirmation method (kid, jwk, or jku) is required"
        }

        // If only kid is provided (no jwk or jku), it must be a DID
        // because non-DID kid values cannot be resolved to obtain key material
        if (kid != null && jwk == null && jku == null) {
            require(kid!!.startsWith("did:")) {
                "When only 'kid' is provided without 'jwk' or 'jku', it must be a DID (starting with 'did:'). " +
                "Non-DID kid values cannot be resolved to obtain key material. " +
                "Either use a DID-based kid, or provide a 'jwk' alongside the kid."
            }
        }

        return buildJsonObject {
            kid?.let { put("kid", JsonPrimitive(it)) }
            (jwk as? JsonObject)?.let { put("jwk", it) }
            jku?.let { put("jku", JsonPrimitive(it)) }
        }
    }
}

/**
 * Creates a CNF (Confirmation) claim using a DSL builder.
 *
 * @param block The builder configuration
 * @return The CNF claim as JsonObject
 */
fun cnf(block: CnfBuilder.() -> Unit): JsonObject = CnfBuilder().apply(block).build()

/**
 * Creates a CNF claim from a DID verification method and its public key.
 *
 * This is the recommended approach for DID-based holder binding as it includes
 * both the kid (DID verification method ID) and the jwk (public key) for
 * maximum interoperability.
 *
 * @param verificationMethodId The DID verification method ID (e.g., "did:key:z6Mk...#z6Mk...")
 * @param publicKey The holder's public key JWK
 * @return The CNF claim as JsonObject
 */
fun cnfFromDid(verificationMethodId: String, publicKey: Jwk): JsonObject = cnf {
    kid(verificationMethodId)
    jwk(publicKey)
}

/**
 * Creates a CNF claim from just a JWK.
 *
 * Use this for simple holder binding without DID support.
 *
 * @param publicKey The holder's public key JWK
 * @return The CNF claim as JsonObject
 */
fun cnfFromJwk(publicKey: Jwk): JsonObject = cnf {
    jwk(publicKey)
}
