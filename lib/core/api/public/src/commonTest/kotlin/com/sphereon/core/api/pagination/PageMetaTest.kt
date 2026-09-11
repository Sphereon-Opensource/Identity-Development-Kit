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

package com.sphereon.core.api.pagination

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PageMetaTest {
    @Test
    fun toPageMetaMapsEveryCanonicalField() {
        val page =
            Page(
                items = listOf("one", "two", "three", "four", "last"),
                totalCount = 25L,
                limit = 10,
                offset = 20,
            )

        assertEquals(
            PageMeta(
                limit = 10,
                offset = 20,
                page = 2,
                size = 10,
                total = 25L,
                totalPages = 3,
                hasMore = false,
            ),
            page.toPageMeta(),
        )
    }

    @Test
    fun pageMetaSerializesWithTheOpenApiFieldNamesAndLongTotal() {
        val total = Int.MAX_VALUE.toLong() + 1L
        val encoded =
            Json.encodeToJsonElement(
                PageMeta.serializer(),
                PageMeta(
                    limit = 50,
                    offset = 100,
                    page = 2,
                    size = 50,
                    total = total,
                    totalPages = 42_949_673,
                    hasMore = true,
                ),
            )
                .jsonObject

        assertEquals(
            setOf("limit", "offset", "page", "size", "total", "totalPages", "hasMore"),
            encoded.keys,
        )
        assertEquals(JsonPrimitive(total), encoded["total"])
    }
}
