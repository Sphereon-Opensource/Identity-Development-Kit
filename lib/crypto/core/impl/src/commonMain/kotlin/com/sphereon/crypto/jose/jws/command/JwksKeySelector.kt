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

package com.sphereon.crypto.jose.jws.command

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.jose.JwaAlgorithm
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Selects a JWK from a trusted JWKS document for JWS signature verification.
 *
 * Resolution rules (RFC 7517 §4.5 + OIDC Core §10.1.1 spirit):
 *  - When the JWS header carries a `kid` and the JWKS holds a key with that `kid`, return it.
 *  - When the header carries a `kid` and no JWKS entry matches, return null (caller MUST reject).
 *  - When the header has no `kid` and exactly one JWKS entry is compatible with the header `alg`
 *    (matching `kty`, optional `alg`, optional `use=sig`), return that entry.
 *  - Otherwise (zero or multiple compatible entries when `kid` is absent), return null.
 */
internal fun selectJwk(
    trustedJwks: JsonObject,
    headerKid: String?,
    headerAlg: String,
): JsonElement? {
    val keys = trustedJwks["keys"] as? JsonArray ?: return null
    val requirements = requirementsFor(headerAlg) ?: return null

    // A duplicate kid is ambiguous and must never be resolved by array ordering.
    val candidates = keys.filter { element ->
        val obj = element as? JsonObject ?: return@filter false
        isCompatible(obj, headerAlg, requirements)
    }

    if (headerKid != null) {
        val kidMatches = keys.filter { element ->
            val obj = element as? JsonObject ?: return@filter false
            // Count every object carrying this kid. A malformed or private duplicate still
            // makes the trust-set identifier ambiguous; it must not be hidden by filtering it
            // out before the uniqueness check.
            obj.stringField("kid") == headerKid
        }
        return if (kidMatches.size == 1 && candidates.contains(kidMatches.single())) {
            kidMatches.single()
        } else {
            null
        }
    }
    return if (candidates.size == 1) candidates.single() else null
}

private data class SignatureRequirements(
    val kty: String,
    val curves: Set<String> = emptySet(),
    val coordinateSize: Int? = null,
)

/** Only algorithms supported by the public JWK verification path are selectable here. */
private fun requirementsFor(alg: String): SignatureRequirements? =
    when (JwaAlgorithm.fromValue(alg)) {
        JwaAlgorithm.RS256, JwaAlgorithm.RS384, JwaAlgorithm.RS512,
        JwaAlgorithm.PS256, JwaAlgorithm.PS384, JwaAlgorithm.PS512,
        -> SignatureRequirements(kty = "RSA")
        JwaAlgorithm.ES256 -> SignatureRequirements(kty = "EC", curves = setOf("P-256"), coordinateSize = 32)
        JwaAlgorithm.ES384 -> SignatureRequirements(kty = "EC", curves = setOf("P-384"), coordinateSize = 48)
        JwaAlgorithm.ES512 -> SignatureRequirements(kty = "EC", curves = setOf("P-521"), coordinateSize = 66)
        JwaAlgorithm.ES256K -> SignatureRequirements(kty = "EC", curves = setOf("secp256k1"), coordinateSize = 32)
        JwaAlgorithm.EdDSA -> SignatureRequirements(kty = "OKP", curves = setOf("Ed25519", "Ed448"))
        else -> null
    }

private fun isCompatible(
    jwk: JsonObject,
    headerAlg: String,
    requirements: SignatureRequirements,
): Boolean {
    if (!jwk.hasValidKnownStringFields()) return false
    if (jwk.stringField("kty") != requirements.kty) return false
    if (jwk.stringField("alg")?.let { it != headerAlg } == true) return false
    if (jwk.stringField("use")?.let { it != "sig" } == true) return false

    val keyOps = jwk["key_ops"]
    if (keyOps != null) {
        val operations = (keyOps as? JsonArray)?.map {
            (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
        }
            ?: return false
        if (operations.any { it == null } || "verify" !in operations.filterNotNull()) return false
    }

    // A trusted verification set must never expose or consume private key material.
    if (PRIVATE_FIELDS.any { it in jwk }) return false

    return when (requirements.kty) {
        "RSA" -> validRsaModulus(jwk.stringField("n")) && validRsaExponent(jwk.stringField("e")) &&
            jwk.noFields("crv", "x", "y", "k")
        "EC" -> {
            val curve = jwk.stringField("crv")
            curve in requirements.curves &&
                validBase64Url(jwk.stringField("x"), requirements.coordinateSize) &&
                validBase64Url(jwk.stringField("y"), requirements.coordinateSize) &&
                jwk.noFields("n", "e", "k")
        }
        "OKP" -> {
            val curve = jwk.stringField("crv")
            val expectedSize = if (curve == "Ed25519") 32 else if (curve == "Ed448") 57 else null
            curve in requirements.curves && validBase64Url(jwk.stringField("x"), expectedSize) &&
                jwk.noFields("n", "e", "y", "k")
        }
        else -> false
    }
}

private val PRIVATE_FIELDS = setOf("d", "p", "q", "dp", "dq", "qi", "oth")

private val KNOWN_STRING_FIELDS = setOf("kid", "kty", "alg", "use", "crv", "n", "e", "x", "y", "d", "p", "q", "dp", "dq", "qi", "k")

private fun JsonObject.hasValidKnownStringFields(): Boolean =
    KNOWN_STRING_FIELDS.all { field ->
        val value = this[field]
        value == null || (value as? JsonPrimitive)?.isString == true
    }

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.noFields(vararg fields: String): Boolean = fields.all { this[it] == null }

private fun validBase64Url(value: String?, expectedSize: Int? = null): Boolean {
    if (value.isNullOrEmpty() || value.any { it !in BASE64URL_CHARS } || value.length % 4 == 1) return false
    return runCatching { value.decodeFrom(Encoding.BASE64URL) }
        .getOrNull()
        ?.takeIf { it.encodeToBase64Url() == value }
        ?.let { expectedSize == null || it.size == expectedSize }
        ?: false
}

/** RFC 7518 §3.3 requires RSA verification moduli to be at least 2048 bits. */
private fun validRsaModulus(value: String?): Boolean {
    val bytes = decodeCanonicalBase64Url(value) ?: return false
    return bytes.size >= 256 &&
        bytes.firstOrNull()?.let { it.toInt() and 0x80 != 0 } == true &&
        (bytes.last().toInt() and 1) == 1
}

/** RFC 7518 §3.3 requires a positive odd RSA public exponent >= 3. */
private fun validRsaExponent(value: String?): Boolean {
    val bytes = decodeCanonicalBase64Url(value) ?: return false
    if (bytes.firstOrNull() == 0.toByte()) return false
    val first = bytes.firstOrNull()?.toInt()?.and(0xff) ?: return false
    val atLeastThree = bytes.size > 1 || first >= 3
    return atLeastThree && (bytes.last().toInt() and 1) == 1
}

private fun decodeCanonicalBase64Url(value: String?): ByteArray? {
    if (value.isNullOrEmpty() || value.any { it !in BASE64URL_CHARS } || value.length % 4 == 1) return null
    return runCatching { value.decodeFrom(Encoding.BASE64URL) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() && it.encodeToBase64Url() == value }
}

private val BASE64URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".toSet()
