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

package com.sphereon.identity.matching.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentifierTypeTest {
    @Test
    fun predefinedTypesHaveExpectedValues() {
        assertEquals("KEY", IdentifierType.KEY.value)
        assertEquals("DID", IdentifierType.DID.value)
        assertEquals("EMAIL", IdentifierType.EMAIL.value)
        assertEquals("SUBJECT_ID", IdentifierType.SUBJECT_ID.value)
    }

    @Test
    fun customTypeIsSupported() {
        val custom = IdentifierType("BIOMETRIC_HASH")
        assertEquals("BIOMETRIC_HASH", custom.value)
    }

    @Test
    fun equalityWorksCorrectly() {
        assertEquals(IdentifierType.KEY, IdentifierType("KEY"))
        assertNotEquals(IdentifierType.KEY, IdentifierType.DID)
    }

    @Test
    fun serializationRoundTrip() {
        val json = Json
        val type = IdentifierType.DID
        val serialized = json.encodeToString(IdentifierType.serializer(), type)
        val deserialized = json.decodeFromString(IdentifierType.serializer(), serialized)
        assertEquals(type, deserialized)
    }

    @Test
    fun customTypeSerializationRoundTrip() {
        val json = Json
        val type = IdentifierType("CUSTOM_X509")
        val serialized = json.encodeToString(IdentifierType.serializer(), type)
        val deserialized = json.decodeFromString(IdentifierType.serializer(), serialized)
        assertEquals(type, deserialized)
    }
}
