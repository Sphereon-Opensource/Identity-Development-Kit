/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicTs11RemoteBodiesTest {
    @Test
    fun decodeSchemaListKeepsReportedTotalWhenDataIsAPage() {
        val json =
            Json.parseToJsonElement(
                """
                {
                  "total": 250,
                  "limit": 20,
                  "offset": 0,
                  "data": [
                    {
                      "id": "schema-1",
                      "version": "1.0.0",
                      "rulebookURI": "https://example.test/rb",
                      "attestationLoS": "iso_18045_high",
                      "bindingType": "key",
                      "supportedFormats": ["dc+sd-jwt"],
                      "schemaURIs": [{"formatIdentifier":"dc+sd-jwt","uri":"https://example.test/vct"}]
                    }
                  ]
                }
                """.trimIndent(),
            )
        val page = PublicTs11RemoteBodies.decodeSchemaList(json).value
        assertEquals(250, page.total)
        assertEquals(20, page.limit)
        assertEquals(0, page.offset)
        assertEquals(1, page.data.size)
        assertEquals("schema-1", page.data.single().id)
    }

    @Test
    fun decodeSchemaListFallsBackToArraySizeWhenPaginationIsAbsent() {
        val json =
            Json.parseToJsonElement(
                """
                [
                  {
                    "id": "schema-1",
                    "version": "1.0.0",
                    "rulebookURI": "https://example.test/rb",
                    "attestationLoS": "iso_18045_high",
                    "bindingType": "key",
                    "supportedFormats": ["dc+sd-jwt"],
                    "schemaURIs": [{"formatIdentifier":"dc+sd-jwt","uri":"https://example.test/vct"}]
                  }
                ]
                """.trimIndent(),
            )
        val page = PublicTs11RemoteBodies.decodeSchemaList(json).value
        assertEquals(1, page.total)
        assertEquals(1, page.data.size)
    }

    @Test
    fun schemaListUrlsIncludeLimitAndOffset() {
        val urls = PublicTs11RemoteBodies.schemaListUrls("https://tenant.example/public/catalogs/pid/api/v1", 20, 40)
        assertEquals(
            "https://tenant.example/public/catalogs/pid/api/v1/schemas?limit=20&offset=40",
            urls.first(),
        )
        assertTrue(urls.contains("https://tenant.example/public/catalogs/pid/api/v1/schemas.json?limit=20&offset=40"))
    }
}
