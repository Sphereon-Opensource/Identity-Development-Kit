/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulebookCatalogMapperTest {
    @Test
    fun mapsSdJwtAndRulebook() {
        val files =
            mapOf(
                "data-schemas/sd-jwt/pid-sd-jwt.json" to
                    """{"properties":{"vct":{"const":"urn:eudi:pid:1"}}}""",
                "rulebooks/rb-pid/README.md" to "# PID",
            )
        val mapped = RulebookCatalogMapper.map(files, "iso_18045_high", "key")
        assertEquals(1, mapped.size)
        assertEquals("pid", mapped[0].slug)
        assertTrue(mapped[0].complete)
        assertEquals(
            "urn:eudi:pid:1",
            mapped[0]
                .schema.schemaURIs
                .first()
                .uri
        )
        assertEquals(2, mapped[0].documents.size)
        val vctDoc = mapped[0].documents.single { it.formatIdentifier == "dc+sd-jwt" }
        assertTrue(vctDoc.bytes.decodeToString().contains("\"vct\""))
        assertTrue(vctDoc.bytes.decodeToString().contains("urn:eudi:pid:1"))
    }

    @Test
    fun unlistedWithoutDefaults() {
        val files =
            mapOf("data-schemas/sd-jwt/company-info-sd-jwt.json" to """{"properties":{}}""")
        val mapped = RulebookCatalogMapper.map(files, null, null)
        assertEquals(1, mapped.size)
        assertEquals(false, mapped[0].complete)
    }
}
