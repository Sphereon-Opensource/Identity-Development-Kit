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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    ): JsonObject =
        buildJsonObject {
            put("kty", JsonPrimitive(kty))
            if (kid != null) put("kid", JsonPrimitive(kid))
            if (alg != null) put("alg", JsonPrimitive(alg))
            if (use != null) put("use", JsonPrimitive(use))
        }

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
}
