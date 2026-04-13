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

package com.sphereon.core.api.conf

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonMappingTest {
    val configExample =
        mapOf(
            "user.name.first" to "Alice",
            "user.name.last" to "Smith",
            "user.age" to 30,
            "active" to true,
        )

    @Test
    fun testMultiDimensionalMapToJsonObject() {
        val json = configExample.toJsonObject()
        assertEquals(2, json.size) // user and active
        assertEquals(2, json["user"]?.jsonObject?.size) // age and name
        assertEquals(
            configExample["user.name.first"],
            json["user"]
                ?.jsonObject["name"]
                ?.jsonObject["first"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(configExample["active"], json["active"]?.jsonPrimitive?.boolean)
        assertEquals(configExample["active"].toString(), json["active"]?.jsonPrimitive?.content)
    }

    @Test
    fun testMultiDimensionalMapToJsonString() {
        val json = configExample.toJsonString()
        assertEquals("{\"user\":{\"name\":{\"first\":\"Alice\",\"last\":\"Smith\"},\"age\":30},\"active\":true}", json)
    }
}
