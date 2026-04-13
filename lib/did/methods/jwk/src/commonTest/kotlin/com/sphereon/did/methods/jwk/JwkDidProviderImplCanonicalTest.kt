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

package com.sphereon.did.methods.jwk

import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the canonical did:jwk encoding rule: the base64url-encoded JWK that
 * forms the DID must contain ONLY the public-key members of the JWK (per key type,
 * per JWA / RFC 7518). All non-key-material members (kid, alg, use, key_ops) and
 * any private parameters MUST be stripped before encoding, otherwise two callers
 * with the same public key would produce different DIDs.
 */
class JwkDidProviderImplCanonicalTest {
    private val json = Json { encodeDefaults = false }

    private val publicMembers = setOf("kty", "crv", "x", "y", "n", "e")

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeDidJwk(did: String): JsonObject {
        assertTrue(did.startsWith("did:jwk:"), "Not a did:jwk: $did")
        val encoded = did.removePrefix("did:jwk:").substringBefore('#').substringBefore('?')
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        val bytes = Base64.UrlSafe.decode(padded)
        return json.parseToJsonElement(bytes.decodeToString()) as JsonObject
    }

    @Test
    fun ecKeyWithAttachedKidProducesCanonicalDid() {
        // Simulate a KMS-loaded key: public EC JWK with a kid tacked on
        // (many KMS implementations assign an internal kid as a lookup handle).
        val jwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "f83OJ3D2xF4yVPs6k2lE0_C3lq8GG5GpQ1GkGvI0zGY",
                y = "x_FEzRu9m0cN5yZKkH9VqxcWxLb5Y7EFYqmP9FxbnTc",
                kid = "internal-kms-handle-42",
                use = "sig",
                alg = JwaAlgorithm.ES256,
                key_ops = arrayOf(JoseKeyOperations.SIGN),
            )

        val did = JwkDidProviderImpl.didFromJwk(jwk)

        val embedded = decodeDidJwk(did)
        assertEquals(
            publicMembers.intersect(setOf("kty", "crv", "x", "y")),
            embedded.keys,
            "Embedded JWK must have only EC public-key members. Got: ${embedded.keys}",
        )
        assertFalse("kid" in embedded, "kid must not be embedded inside the did:jwk")
        assertFalse("alg" in embedded, "alg must not be embedded inside the did:jwk")
        assertFalse("use" in embedded, "use must not be embedded inside the did:jwk")
        assertFalse("key_ops" in embedded, "key_ops must not be embedded inside the did:jwk")
    }

    @Test
    fun twoJwksWithSamePublicMaterialProduceSameDid() {
        // Same public key, different non-public metadata. The derived DIDs MUST be equal —
        // otherwise header-kid and JWKS-entry-kid can never align when both sides have their
        // own mutation of the JWK metadata.
        val a =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "TNxjtCVCh7YhMvro7a-bXYTvFmPzkiqh-0VTdGpKs1A",
                y = "ulrq03waXDJQCwHy2QtiCWNptWr_3HngfLLvP1DdOHw",
                kid = "alpha",
                alg = JwaAlgorithm.ES256,
                use = "sig",
            )
        val b =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "TNxjtCVCh7YhMvro7a-bXYTvFmPzkiqh-0VTdGpKs1A",
                y = "ulrq03waXDJQCwHy2QtiCWNptWr_3HngfLLvP1DdOHw",
                kid = "beta",
            )
        assertEquals(JwkDidProviderImpl.didFromJwk(a), JwkDidProviderImpl.didFromJwk(b))
    }

    @Test
    fun privateMembersNeverLeakIntoDid() {
        // If a caller accidentally passes a Jwk that still has private material, we must
        // not publish that material in the DID.
        val jwkWithPrivate =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "TNxjtCVCh7YhMvro7a-bXYTvFmPzkiqh-0VTdGpKs1A",
                y = "ulrq03waXDJQCwHy2QtiCWNptWr_3HngfLLvP1DdOHw",
                d = "secret-private-scalar",
            )
        val embedded = decodeDidJwk(JwkDidProviderImpl.didFromJwk(jwkWithPrivate))
        assertFalse("d" in embedded, "private parameter 'd' leaked into did:jwk")
    }

    @Test
    fun rsaKeyKeepsOnlyKtyNE() {
        val jwk =
            Jwk(
                kty = JwaKeyType.RSA,
                n = "xjlzOl5LI3RzF6KNZ4i-5A",
                e = "AQAB",
                kid = "irrelevant",
                use = "sig",
            )
        val embedded = decodeDidJwk(JwkDidProviderImpl.didFromJwk(jwk))
        assertEquals(setOf("kty", "n", "e"), embedded.keys)
        assertEquals("RSA", embedded["kty"]?.jsonPrimitive?.content)
    }
}
