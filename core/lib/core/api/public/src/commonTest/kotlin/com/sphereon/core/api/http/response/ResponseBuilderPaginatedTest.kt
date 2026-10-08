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

package com.sphereon.core.api.http.response

import com.sphereon.core.api.pagination.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseBuilderPaginatedTest {
    @Serializable
    private data class Item(
        val id: String
    )

    private fun paginationOf(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject["pagination"]!!.jsonObject

    @Test
    fun paginatedEmitsUnifiedSupersetEnvelope() {
        // 25 total, page of 10 starting at offset 10 -> page index 1, 3 total pages, more available.
        val page =
            Page(
                items = listOf(Item("a"), Item("b")),
                totalCount = 25,
                limit = 10,
                offset = 10,
            )

        val response = ResponseBuilder.paginated(page)
        val pagination = paginationOf(response.body!!)

        // Legacy fields kept EXACTLY as before.
        assertEquals(JsonPrimitive(10), pagination["limit"])
        assertEquals(JsonPrimitive(10), pagination["offset"])
        assertEquals(JsonPrimitive(25), pagination["total"])
        assertEquals(JsonPrimitive(true), pagination["hasMore"])

        // Additive unified fields.
        assertEquals(JsonPrimitive(1), pagination["page"])
        assertEquals(JsonPrimitive(10), pagination["size"])
        assertEquals(JsonPrimitive(3), pagination["totalPages"])
    }

    @Test
    fun paginatedKeepsExactlySevenPaginationFields() {
        val page = Page<Item>(items = emptyList(), totalCount = 0, limit = 20, offset = 0)

        val pagination = paginationOf(ResponseBuilder.paginated(page).body!!)

        assertEquals(
            setOf("limit", "offset", "page", "size", "total", "totalPages", "hasMore"),
            pagination.keys,
        )
    }

    @Test
    fun paginatedRetainsDataArray() {
        val page = Page(items = listOf(Item("only")), totalCount = 1, limit = 20, offset = 0)

        val body = ResponseBuilder.paginated(page).body!!

        assertTrue(body.contains("\"only\""))
    }
}
