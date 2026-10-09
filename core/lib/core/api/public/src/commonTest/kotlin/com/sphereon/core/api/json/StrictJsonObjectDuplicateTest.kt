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

package com.sphereon.core.api.json

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Raw-string proof for a duplicate-aware object decoder; never pre-materialize a JSON tree here. */
class StrictJsonObjectDuplicateTest {
    @Test
    fun rejectsRepeatedDecodedMemberNamesBeforeMapInsertion() {
        for ((case, raw) in listOf(
            "literal repeat" to """{"role":1,"role":2}""",
            "null first" to """{"role":null,"role":2}""",
            "escaped equivalent" to """{"role":null,"\u0072ole":2}"""
        )) {
            assertFails("$case must reject the second decoded member name") {
                decodeRaw(raw)
            }
        }
    }

    @Test
    fun acceptsDistinctMembersIncludingNullValue() {
        val decoded = decodeRaw("""{"first":null,"second":2}""")

        assertEquals(setOf("first", "second"), decoded.keys)
        assertEquals(JsonNull, decoded["first"])
        assertEquals(JsonPrimitive(2), decoded["second"])
    }

    @Test
    fun acceptsSameMemberNameInSeparateNestedObjects() {
        val checkedNested = CheckedJsonObjectDeserializer()
        val root = CheckedJsonObjectDeserializer { checkedNested }
        val decoded = decodeRaw("""{"left":{"value":1},"right":{"value":2}}""", root)

        assertEquals(JsonPrimitive(1), decoded["left"]?.jsonObject?.get("value"))
        assertEquals(JsonPrimitive(2), decoded["right"]?.jsonObject?.get("value"))
    }

    @Test
    fun checksOnlyNestedMembersSelectedByTheCaller() {
        val checkedNested = CheckedJsonObjectDeserializer()
        val root = CheckedJsonObjectDeserializer { key ->
            if (key == "checked") checkedNested else JsonElement.serializer()
        }

        assertFails {
            decodeRaw("""{"checked":{"role":1,"role":2}}""", root)
        }

        val decoded = decodeRaw("""{"checked":{"role":1},"unrelated":{"role":1,"role":2}}""", root)
        assertEquals(JsonPrimitive(1), decoded["checked"]?.jsonObject?.get("role"))
        assertEquals(JsonPrimitive(2), decoded["unrelated"]?.jsonObject?.get("role"))
    }

    private fun decodeRaw(
        raw: String,
        deserializer: CheckedJsonObjectDeserializer = CheckedJsonObjectDeserializer(),
    ): JsonObject = JsonSupport.serializer.decodeFromString(deserializer, raw)
}
