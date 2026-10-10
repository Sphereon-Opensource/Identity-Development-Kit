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

package com.sphereon.crypto.core.cose

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class CoseKeyToStringMaskingTest {
    private val privateScalar = "JREfJZz9aSu2GlIt9WiR6Vuo8bXAWUlrTNzty8kuvbI"

    @Test
    fun privateScalarAndAdditionalParametersAreNotPrinted() {
        val rendered = CoseKeyJson(kty = CoseKeyTypeEnum.EC2, x = "public-x", d = privateScalar, additional = "extra-secret-parameter").toString()

        assertContains(rendered, "d=[REDACTED, 43 chars]")
        assertContains(rendered, "additional=[REDACTED]")
        assertContains(rendered, "x=public-x")
        assertFalse(rendered.contains("JREf"))
        assertFalse(rendered.contains("uvbI"))
        assertFalse(rendered.contains("extra-secret-parameter"))
    }
}
