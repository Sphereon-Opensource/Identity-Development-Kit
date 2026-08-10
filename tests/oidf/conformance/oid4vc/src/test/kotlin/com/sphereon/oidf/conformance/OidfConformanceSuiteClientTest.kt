/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OidfConformanceSuiteClientTest {
    @Test
    fun `runner error decoding distinguishes JSON null from actual errors`() {
        assertNull(null.asErrorString())
        assertNull(JsonNull.asErrorString())
        assertEquals("plain failure", JsonPrimitive("plain failure").asErrorString())
        assertEquals(
            "{\"error\":\"structured failure\"}",
            buildJsonObject { put("error", "structured failure") }.asErrorString(),
        )
    }
}
