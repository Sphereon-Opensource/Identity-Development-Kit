/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.holder.impl

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SignedDigitalCredentialsExpectedOriginsTest {
    @Test
    fun verifiedSingleAndMultipleExpectedOriginsAcceptMatchingOrigin() {
        val single = JsonArray(listOf(JsonPrimitive(ORIGIN_A)))
        val multiple = JsonArray(listOf(JsonPrimitive(ORIGIN_A), JsonPrimitive(ORIGIN_B)))

        assertNull(validateSignedDigitalCredentialsExpectedOrigins(verifiedExpectedOriginsClaim(mapOf("expected_origins" to single)), ORIGIN_A))
        assertNull(validateSignedDigitalCredentialsExpectedOrigins(verifiedExpectedOriginsClaim(mapOf("expected_origins" to multiple)), ORIGIN_B))
    }

    @Test
    fun verifiedExpectedOriginsRejectsUnlistedPlatformOrigin() {
        val expectedOrigins = JsonArray(listOf(JsonPrimitive(ORIGIN_A)))

        assertNotNull(validateSignedDigitalCredentialsExpectedOrigins(verifiedExpectedOriginsClaim(mapOf("expected_origins" to expectedOrigins)), ORIGIN_B))
    }

    @Test
    fun malformedVerifiedExpectedOriginsAreRejectedWithoutCoercion() {
        val malformedClaims =
            listOf(
                verifiedExpectedOriginsClaim(mapOf("expected_origins" to ORIGIN_A)),
                verifiedExpectedOriginsClaim(mapOf("expected_origins" to JsonObject(mapOf("origin" to JsonPrimitive(ORIGIN_A))))),
                verifiedExpectedOriginsClaim(mapOf("expected_origins" to JsonArray(listOf(JsonPrimitive(ORIGIN_A), JsonPrimitive(7))))),
                verifiedExpectedOriginsClaim(mapOf("expected_origins" to JsonArray(emptyList()))),
                verifiedExpectedOriginsClaim(mapOf("expected_origins" to JsonNull)),
            )

        malformedClaims.forEach { expectedOrigins ->
            assertNotNull(validateSignedDigitalCredentialsExpectedOrigins(expectedOrigins, ORIGIN_A))
        }
        val missingClaim = verifiedExpectedOriginsClaim(emptyMap())
        assertNull(missingClaim)
        assertNotNull(validateSignedDigitalCredentialsExpectedOrigins(missingClaim, ORIGIN_A))
    }

    private companion object {
        const val ORIGIN_A = "https://wallet.example"
        const val ORIGIN_B = "https://other.example"
    }
}
