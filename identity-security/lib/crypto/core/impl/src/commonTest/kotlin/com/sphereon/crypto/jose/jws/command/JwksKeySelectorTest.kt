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

import com.sphereon.core.api.encodeToBase64Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwksKeySelectorTest {
    private fun jwk(
        kty: String,
        kid: String? = null,
        alg: String? = null,
        use: String? = null,
        crv: String? = null,
        keyOps: List<String>? = null,
    ): JsonObject =
        buildJsonObject {
            put("kty", JsonPrimitive(kty))
            if (kid != null) put("kid", JsonPrimitive(kid))
            if (alg != null) put("alg", JsonPrimitive(alg))
            if (use != null) put("use", JsonPrimitive(use))
            when (kty) {
                "EC" -> {
                    val curve = crv ?: "P-256"
                    val coordinateSize = when (curve) {
                        "P-384" -> 48
                        "P-521" -> 66
                        else -> 32
                    }
                    put("crv", JsonPrimitive(curve))
                    put("x", JsonPrimitive(b64(coordinateSize)))
                    put("y", JsonPrimitive(b64(coordinateSize)))
                }
                "OKP" -> {
                    val curve = crv ?: "Ed25519"
                    put("crv", JsonPrimitive(curve))
                    put("x", JsonPrimitive(b64(if (curve == "Ed448") 57 else 32)))
                }
                "RSA" -> {
                    put("n", JsonPrimitive(rsaModulus()))
                    put("e", JsonPrimitive("AQAB"))
                }
            }
            if (keyOps != null) put("key_ops", JsonArray(keyOps.map { JsonPrimitive(it) }))
        }

    private fun b64(size: Int): String = ByteArray(size) { 1 }.encodeToBase64Url()

    private fun rsaModulus(): String = (byteArrayOf(0x80.toByte()) + ByteArray(255) { 1 }).encodeToBase64Url()

    private fun jwks(vararg keys: JsonObject): JsonObject =
        buildJsonObject {
            put(
                "keys",
                buildJsonArray { keys.forEach { add(it) } },
            )
        }

    @Test
    fun headerKidPresent_matchesByKid_returnsKey() {
        val set = jwks(jwk(kty = "EC", kid = "a"), jwk(kty = "EC", kid = "b"))

        val selected = selectJwk(trustedJwks = set, headerKid = "b", headerAlg = "ES256")

        assertEquals(
            "b",
            selected
                ?.jsonObject
                ?.get("kid")
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun headerKidPresent_noMatchInJwks_returnsNull() {
        val set = jwks(jwk(kty = "EC", kid = "a"))

        val selected = selectJwk(trustedJwks = set, headerKid = "missing", headerAlg = "ES256")

        assertNull(selected)
    }

    @Test
    fun headerKidPresent_algMismatch_returnsNull() {
        val set = jwks(jwk(kty = "EC", kid = "a", alg = "ES384", crv = "P-384"))

        assertNull(selectJwk(set, headerKid = "a", headerAlg = "ES256"))
    }

    @Test
    fun headerKidPresent_ktyMismatch_returnsNull() {
        val set = jwks(jwk(kty = "RSA", kid = "a"))

        assertNull(selectJwk(set, headerKid = "a", headerAlg = "ES256"))
    }

    @Test
    fun headerKidPresent_encUse_returnsNull() {
        val set = jwks(jwk(kty = "EC", kid = "a", use = "enc"))

        assertNull(selectJwk(set, headerKid = "a", headerAlg = "ES256"))
    }

    @Test
    fun headerKidPresent_incompatibleKeyOps_returnsNull() {
        val set = jwks(jwk(kty = "EC", kid = "a", keyOps = listOf("sign")))

        assertNull(selectJwk(set, headerKid = "a", headerAlg = "ES256"))
    }

    @Test
    fun duplicateKid_returnsNull_evenWhenOneKeyIsCompatible() {
        val set = jwks(jwk(kty = "EC", kid = "a"), jwk(kty = "EC", kid = "a"))

        assertNull(selectJwk(set, headerKid = "a", headerAlg = "ES256"))
    }

    @Test
    fun duplicateKid_returnsNull_whenOtherEntryIsMalformed() {
        val malformedDuplicate = buildJsonObject {
            jwk(kty = "EC", kid = "a").forEach { (name, value) -> put(name, value) }
            put("alg", JsonPrimitive(123))
        }
        val set = jwks(jwk(kty = "EC", kid = "a"), malformedDuplicate)

        assertNull(selectJwk(set, headerKid = "a", headerAlg = "ES256"))
    }

    @Test
    fun headerKidAbsent_singleAlgCompatibleCandidate_returnsThatKey() {
        // Only one key whose kty/alg/use are compatible with ES256.
        val set =
            jwks(
                jwk(kty = "RSA", kid = "rsa-1"),
                jwk(kty = "EC", kid = "ec-1", alg = "ES256", use = "sig"),
            )

        val selected = selectJwk(trustedJwks = set, headerKid = null, headerAlg = "ES256")

        assertEquals(
            "ec-1",
            selected
                ?.jsonObject
                ?.get("kid")
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun headerKidAbsent_multipleCompatibleCandidates_returnsNull() {
        val set =
            jwks(
                jwk(kty = "EC", kid = "ec-1"),
                jwk(kty = "EC", kid = "ec-2"),
            )

        val selected = selectJwk(trustedJwks = set, headerKid = null, headerAlg = "ES256")

        assertNull(selected)
    }

    @Test
    fun headerKidAbsent_zeroCompatibleCandidates_returnsNull() {
        val set = jwks(jwk(kty = "RSA", kid = "rsa-1"))

        // ES256 requires EC; the only key is RSA.
        val selected = selectJwk(trustedJwks = set, headerKid = null, headerAlg = "ES256")

        assertNull(selected)
    }

    @Test
    fun headerKidAbsent_useEncRejected() {
        // A `use=enc` key MUST NOT be selected even if kty matches.
        val set = jwks(jwk(kty = "EC", kid = "ec-enc", use = "enc"))

        val selected = selectJwk(trustedJwks = set, headerKid = null, headerAlg = "ES256")

        assertNull(selected)
    }

    @Test
    fun headerKidAbsent_curveMismatchRejected() {
        val set = jwks(jwk(kty = "EC", kid = "ec-384", crv = "P-384"))

        assertNull(selectJwk(set, headerKid = null, headerAlg = "ES256"))
    }

    @Test
    fun supportedAlgorithms_selectCompatiblePublicKey() {
        val cases = listOf(
            "RS256" to jwk("RSA"),
            "PS256" to jwk("RSA"),
            "ES256" to jwk("EC", crv = "P-256"),
            "ES384" to jwk("EC", crv = "P-384"),
            "ES512" to jwk("EC", crv = "P-521"),
            "ES256K" to jwk("EC", crv = "secp256k1"),
            "EdDSA" to jwk("OKP", crv = "Ed25519"),
        )

        cases.forEach { (alg, key) -> assertEquals(key, selectJwk(jwks(key), null, alg), alg) }
    }

    @Test
    fun verifyKeyOperation_isAcceptedWhenExplicitlyConstrained() {
        val key = jwk("EC", keyOps = listOf("verify"))

        assertEquals(key, selectJwk(jwks(key), null, "ES256"))
    }

    @Test
    fun encryptionAndMacAlgorithms_areNotSelectableForJwsVerification() {
        assertNull(selectJwk(jwks(jwk("RSA")), null, "RSA-OAEP"))
        assertNull(selectJwk(jwks(jwk("oct")), null, "HS256"))
    }

    @Test
    fun rsaVerification_rejectsNonJwaPublicMaterial() {
        val smallModulus = buildJsonObject {
            jwk("RSA").forEach { (name, value) -> put(name, value) }
            put("n", JsonPrimitive(b64(255)))
        }
        val evenExponent = buildJsonObject {
            jwk("RSA").forEach { (name, value) -> put(name, value) }
            put("e", JsonPrimitive("Ag==".trimEnd('=')))
        }
        val evenModulus = buildJsonObject {
            jwk("RSA").forEach { (name, value) -> put(name, value) }
            put("n", JsonPrimitive((byteArrayOf(0x80.toByte()) + ByteArray(254) { 1 } + byteArrayOf(2)).encodeToBase64Url()))
        }

        assertNull(selectJwk(jwks(smallModulus), null, "RS256"))
        assertNull(selectJwk(jwks(evenExponent), null, "RS256"))
        assertNull(selectJwk(jwks(evenModulus), null, "RS256"))
    }

    @Test
    fun rsaVerification_rejectsPrivateOtherPrimeParameters() {
        val privateMaterial = buildJsonObject {
            jwk("RSA").forEach { (name, value) -> put(name, value) }
            put("oth", buildJsonArray {})
        }

        assertNull(selectJwk(jwks(privateMaterial), null, "RS256"))
    }
}
