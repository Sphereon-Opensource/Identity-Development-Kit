/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vc.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProofTypeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializationRoundTrip() {
        val encoded = json.encodeToString(ProofType.JWT)
        assertEquals("\"jwt\"", encoded)
        val decoded = json.decodeFromString<ProofType>(encoded)
        assertEquals(ProofType.JWT, decoded)
    }

    @Test
    fun fromValueExactMatch() {
        assertEquals(ProofType.JWT, ProofType.fromValue("jwt"))
        assertNull(ProofType.fromValue("cwt"))
        assertNull(ProofType.fromValue("unknown"))
    }
}
