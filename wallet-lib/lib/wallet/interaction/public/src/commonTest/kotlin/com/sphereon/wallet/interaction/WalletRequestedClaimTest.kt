/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletRequestedClaimTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun `empty requestedClaims means the labelled form was not produced`() {
        val requirement =
            WalletCredentialRequirement(
                id = "pid",
                requiredClaimPaths = listOf(listOf(JsonPrimitive("address"), JsonPrimitive("street_address"))),
            )
        assertTrue(requirement.requestedClaims.isEmpty())
    }

    @Test
    fun `a catalogue miss leaves the label absent rather than echoing the path`() {
        val claim =
            WalletRequestedClaim(
                path = listOf(JsonPrimitive("x_custom"), JsonPrimitive("vendor_field")),
            )
        assertNull(claim.label, "an unresolved label must stay absent so the consumer can fall back")
        val encoded = json.encodeToString(claim)
        assertFalse(encoded.contains("\"label\""), "null label must be omitted on the wire")
        assertTrue(encoded.contains("vendor_field"))
        assertFalse(encoded.contains("\"label\":\"vendor_field\""))
    }
}
