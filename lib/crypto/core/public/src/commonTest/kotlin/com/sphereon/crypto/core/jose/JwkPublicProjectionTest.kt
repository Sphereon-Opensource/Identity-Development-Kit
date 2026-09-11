/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.jose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwkPublicProjectionTest {
    @Test
    fun rsaPublicProjectionRemovesAllPrivateParameters() {
        val privateJwk =
            Jwk(
                kty = JwaKeyType.RSA,
                n = "modulus",
                e = "exponent",
                d = "private-exponent",
                p = "first-prime",
                q = "second-prime",
                dP = "first-factor-crt-exponent",
                dQ = "second-factor-crt-exponent",
                qInv = "first-crt-coefficient",
                kid = "rsa-key",
                alg = JwaAlgorithm.RS256,
            )

        val publicJwk = privateJwk.toPublicKey()

        assertEquals(JwaKeyType.RSA, publicJwk.kty)
        assertEquals("modulus", publicJwk.n)
        assertEquals("exponent", publicJwk.e)
        assertEquals("rsa-key", publicJwk.kid)
        assertEquals(JwaAlgorithm.RS256, publicJwk.alg)
        assertNull(publicJwk.d)
        assertNull(publicJwk.p)
        assertNull(publicJwk.q)
        assertNull(publicJwk.dP)
        assertNull(publicJwk.dQ)
        assertNull(publicJwk.qInv)
    }

    @Test
    fun ecPublicProjectionPreservesPublicCoordinates() {
        val privateJwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "x-coordinate",
                y = "y-coordinate",
                d = "private-scalar",
            )

        val publicJwk = privateJwk.toPublicKey()

        assertEquals("x-coordinate", publicJwk.x)
        assertEquals("y-coordinate", publicJwk.y)
        assertNull(publicJwk.d)
    }
}
