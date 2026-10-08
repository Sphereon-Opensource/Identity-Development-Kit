/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.oauth2.common.command

import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrivateKeyJwtAssertionAssemblyTest {
    private val assembly = PrivateKeyJwtAssertionAssembly(defaultSecureRandom())
    private val publicJwk =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "test-x",
            y = "test-y",
            kid = "oauth-client-key-1",
        )

    @Test
    fun assembleUsesOpenIdConnectAndFapiClientAssertionClaims() =
        runTest {
            val input =
                assembly.assemble(
                    PrivateKeyJwtAssertionAssemblyRequest(
                        clientId = "wallet-client",
                        audience = "https://as.example.com",
                        issuedAt = 1_700_000_000,
                    ),
                    publicJwk,
                )

            assertEquals("ES256", input.headerJson["alg"]?.jsonPrimitive?.content)
            assertEquals("oauth-client-key-1", input.headerJson["kid"]?.jsonPrimitive?.content)
            assertEquals("wallet-client", input.payloadJson["iss"]?.jsonPrimitive?.content)
            assertEquals("wallet-client", input.payloadJson["sub"]?.jsonPrimitive?.content)
            assertEquals("https://as.example.com", input.payloadJson["aud"]?.jsonPrimitive?.content)
            assertEquals(1_700_000_000, input.payloadJson["iat"]?.jsonPrimitive?.content?.toLong())
            assertEquals(1_700_000_060, input.payloadJson["exp"]?.jsonPrimitive?.content?.toLong())
            assertTrue(input.payloadJson["jti"]?.jsonPrimitive?.content?.isNotBlank() == true)
            assertEquals("${input.encodedHeader}.${input.encodedPayload}", input.signingInput.decodeToString())
        }

    @Test
    fun assembleRejectsAKeyWithoutRegisteredKeyId() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                assembly.assemble(
                    PrivateKeyJwtAssertionAssemblyRequest("wallet-client", "https://as.example.com"),
                    publicJwk.copy(kid = null),
                )
            }
        }
}
