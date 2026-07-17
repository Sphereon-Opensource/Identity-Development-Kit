/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Oid4vpSdJwtHolderBindingProviderTest {
    @Test
    fun publicKeyComparisonIgnoresMetadataButRequiresIdenticalEcKeyMaterial() {
        val credentialKey = jwk("""{"kty":"EC","crv":"P-256","x":"x-one","y":"y-one","kid":"issued"}""")
        val sameKey = jwk("""{"kty":"EC","crv":"P-256","x":"x-one","y":"y-one","kid":"runtime","alg":"ES256"}""")
        val differentKey = jwk("""{"kty":"EC","crv":"P-256","x":"x-two","y":"y-two","kid":"issued"}""")

        assertTrue(samePublicKey(credentialKey, sameKey))
        assertFalse(samePublicKey(credentialKey, differentKey))
    }

    @Test
    fun publicKeyComparisonRejectsMissingOrUnsupportedKeyMaterial() {
        assertFalse(samePublicKey(jwk("""{"kty":"EC","crv":"P-256","x":"x"}"""), jwk("""{"kty":"EC","crv":"P-256","x":"x","y":"y"}""")))
        assertFalse(samePublicKey(jwk("""{"kty":"oct","k":"secret"}"""), jwk("""{"kty":"oct","k":"secret"}""")))
    }

    private fun jwk(value: String): JsonObject = Json.parseToJsonElement(value) as JsonObject
}
