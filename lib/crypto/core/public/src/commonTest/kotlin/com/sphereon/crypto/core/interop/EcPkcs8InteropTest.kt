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

package com.sphereon.crypto.core.interop

import at.asitplus.awesn1.crypto.Pkcs8PrivateKeyInfo
import at.asitplus.awesn1.serialization.DER
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EcPkcs8InteropTest {
    @Test
    fun ecPrivateJwkUsesExplicitSec1SerializerInPkcs8Encoding() {
        val jwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
                y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
                d = "2nqEH-JrPC98gJMsEYFVykqWLMqO_6URt9eZGH19_K0",
                kid = "request-decryption-key",
            )

        val encoded =
            DER.encodeToByteArray(
                Pkcs8PrivateKeyInfo.serializer(),
                jwk.toPkcs8PrivateKeyInfo(),
            )
        val decoded =
            DER.decodeFromByteArray(
                Pkcs8PrivateKeyInfo.serializer(),
                encoded,
            ).toJwk()

        assertTrue(encoded.isNotEmpty())
        assertEquals(jwk.crv, decoded.crv)
        assertEquals(jwk.x, decoded.x)
        assertEquals(jwk.y, decoded.y)
        assertEquals(jwk.d, decoded.d)
    }
}
