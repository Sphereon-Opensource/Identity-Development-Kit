package com.sphereon.core.api.pagination

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalPageSerializerTest {
    @Serializable
    private data class Item(val id: String)

    @Test
    fun platformPageUsesTheCanonicalRestEnvelopeAndRoundTrips() {
        val serializer = CanonicalPageSerializer(Item.serializer())
        val original = Page(
            items = listOf(Item("one"), Item("two")),
            totalCount = 5,
            limit = 2,
            offset = 2,
        )

        val encoded = Json.encodeToString(serializer, original)

        assertTrue(encoded.contains("\"pagination\""))
        assertTrue(encoded.contains("\"total\":5"))
        assertTrue(encoded.contains("\"page\":1"))
        assertTrue(encoded.contains("\"hasMore\":true"))
        assertFalse(encoded.contains("total_count"))
        assertEquals(original, Json.decodeFromString(serializer, encoded))
    }
}
