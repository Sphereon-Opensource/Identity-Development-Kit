/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.data.store.party.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PartyOriginTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun ceremonyDiscoverySerializesAsLowercaseSnake() {
        assertEquals("\"ceremony_discovery\"", json.encodeToString(PartyOrigin.serializer(), PartyOrigin.CEREMONY_DISCOVERY))
        assertEquals(
            PartyOrigin.CEREMONY_DISCOVERY,
            json.decodeFromString(PartyOrigin.serializer(), "\"ceremony_discovery\""),
        )
    }
}
