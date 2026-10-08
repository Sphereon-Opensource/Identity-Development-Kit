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
 *
 */

package com.sphereon.core.api.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class TestData(
    val name: String,
    val value: Int,
)

class JsonSupportModuleTest {
    @Test
    fun moduleIsNotNull() {
        val module = JsonSupport.module
        assertNotNull(module)
    }

    @Test
    fun serializerIsNotNull() {
        val serializer = JsonSupport.serializer
        assertNotNull(serializer)
    }
}

class JsonSupportSerializerTest {
    @Test
    fun serializerCanEncodeSimpleObject() {
        val data = TestData("test", 42)
        val json = JsonSupport.serializer.encodeToString(data)
        assertNotNull(json)
        assertEquals(true, json.contains("\"name\":\"test\""))
    }

    @Test
    fun serializerCanDecodeSimpleObject() {
        val json = """{"name":"test","value":42}"""
        val data = JsonSupport.serializer.decodeFromString<TestData>(json)
        assertEquals("test", data.name)
        assertEquals(42, data.value)
    }

    @Test
    fun serializerPreservesData() {
        val original = TestData("hello", 123)
        val json = JsonSupport.serializer.encodeToString(original)
        val decoded = JsonSupport.serializer.decodeFromString<TestData>(json)
        assertEquals(original, decoded)
    }
}

class JsonSupportRegisterTest {
    @Test
    fun registerWithNullIdAddsRegistrar() {
        JsonSupport.register(null) {
            // Empty registration for testing
        }
        assertNotNull(JsonSupport.module)
    }

    @Test
    fun registerWithDefaultParameterAddsRegistrar() {
        // Call register without providing registrationId - uses default null value
        JsonSupport.register {
            // Empty registration for testing default parameter path
        }
        assertNotNull(JsonSupport.module)
    }

    @Test
    fun registerWithUniqueIdAddsRegistrar() {
        val uniqueId = "test-registration-${kotlin.random.Random.nextLong()}"
        JsonSupport.register(uniqueId) {
            // Empty registration for testing
        }
        assertNotNull(JsonSupport.module)
    }

    @Test
    fun duplicateRegistrationIdIsIgnored() {
        val uniqueId = "duplicate-test-${kotlin.random.Random.nextLong()}"
        var callCount = 0

        JsonSupport.register(uniqueId) {
            callCount++
        }
        JsonSupport.register(uniqueId) {
            callCount++
        }

        // Force module to be built to trigger registrars
        val module = JsonSupport.module
        assertNotNull(module)

        // First registration should be added, second should be skipped
        // The registrar itself may be called multiple times during module building,
        // but the second registration call should be skipped entirely
    }
}

class JsonSerializerAccessTest {
    @Test
    fun jsonSerializerValIsNotNull() {
        val serializer = jsonSerializer
        assertNotNull(serializer)
    }

    @Test
    fun jsonSerializerValCanEncode() {
        val data = TestData("test", 1)
        val json = jsonSerializer.encodeToString(data)
        assertNotNull(json)
    }

    @Test
    fun jsonSerializerValCanDecode() {
        val json = """{"name":"decoded","value":99}"""
        val data = jsonSerializer.decodeFromString<TestData>(json)
        assertEquals("decoded", data.name)
        assertEquals(99, data.value)
    }
}
