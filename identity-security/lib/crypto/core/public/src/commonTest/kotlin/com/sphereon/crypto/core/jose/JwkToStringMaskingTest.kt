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
import kotlin.test.assertContains
import kotlin.test.assertFalse

class JwkToStringMaskingTest {
    private val ecPrivateScalar = "JREfJZz9aSu2GlIt9WiR6Vuo8bXAWUlrTNzty8kuvbI"

    @Test
    fun privateEcScalarIsReportedAsPresenceAndLengthOnly() {
        val rendered = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = "public-x", y = "public-y", d = ecPrivateScalar).toString()

        assertContains(rendered, "d=[REDACTED, 43 chars]")
        assertFalse(rendered.contains("JREf"))
        assertFalse(rendered.contains("uvbI"))
        assertFalse(rendered.contains(ecPrivateScalar))
        assertContains(rendered, "x=public-x")
        assertContains(rendered, "y=public-y")
    }

    @Test
    fun everyPrivateParameterIsMaskedAndShortValuesAreHiddenEntirely() {
        val secrets =
            mapOf(
                "d" to "rsa-private-exponent-value",
                "p" to "rsa-first-prime-value-0001",
                "q" to "rsa-second-prime-value-002",
                "dP" to "rsa-first-crt-exponent-003",
                "dQ" to "rsa-second-crt-exponent-04",
                "qInv" to "short",
            )
        val rendered =
            Jwk(
                kty = JwaKeyType.RSA,
                n = "modulus",
                e = "AQAB",
                d = secrets.getValue("d"),
                p = secrets.getValue("p"),
                q = secrets.getValue("q"),
                dP = secrets.getValue("dP"),
                dQ = secrets.getValue("dQ"),
                qInv = secrets.getValue("qInv"),
            ).toString()

        secrets.values.forEach { assertFalse(rendered.contains(it), "unmasked value in $rendered") }
        assertContains(rendered, "qInv=[REDACTED, 5 chars]")
        assertContains(rendered, "d=[REDACTED, 26 chars]")
        assertContains(rendered, "e=AQAB")
        assertContains(rendered, "n=modulus")
    }

    @Test
    fun symmetricKeyIsMasked() {
        val secret = "c2VjcmV0LXN5bW1ldHJpYy1rZXktbWF0ZXJpYWw"
        val rendered = Jwk(kty = JwaKeyType.oct, k = secret).toString()

        assertFalse(rendered.contains(secret))
        assertContains(rendered, "k=[REDACTED, 39 chars]")
    }
}
