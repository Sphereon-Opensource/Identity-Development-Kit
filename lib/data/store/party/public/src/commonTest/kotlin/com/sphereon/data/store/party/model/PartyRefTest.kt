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

class PartyRefTest {
    private val json = Json { encodeDefaults = false }

    @Test
    fun partyRefRoundTripsWithDefaults() {
        val ref = PartyRef(partyId = "p-1", type = PartyType.ORGANIZATION)
        val encoded = json.encodeToString(PartyRef.serializer(), ref)
        val decoded = json.decodeFromString(PartyRef.serializer(), encoded)
        assertEquals(ref, decoded)
        assertEquals(null, decoded.displayName)
        assertEquals(emptyMap(), decoded.identifiers)
    }

    @Test
    fun partyRefRoundTripsWithIdentifiers() {
        val ref =
            PartyRef(
                partyId = "p-2",
                type = PartyType.NATURAL_PERSON,
                displayName = "Jane Doe",
                identifiers = mapOf("email" to "jane@example.com"),
            )
        val decoded = json.decodeFromString(PartyRef.serializer(), json.encodeToString(PartyRef.serializer(), ref))
        assertEquals(ref, decoded)
    }
}
