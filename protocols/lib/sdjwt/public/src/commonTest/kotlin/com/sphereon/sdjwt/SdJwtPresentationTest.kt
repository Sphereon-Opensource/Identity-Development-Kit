/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.sdjwt

import com.sphereon.crypto.core.generic.DigestAlg
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SdJwtPresentationTest {
    @Test
    fun keyBindingHeaderContainsOnlyRfc9901Parameters() =
        runTest {
            val input =
                SdJwtPresentation.keyBindingInput(
                    selection =
                        SdJwtPresentation.Selection(
                            presentationWithoutKeyBinding = "issuer.jwt.signature~",
                            disclosedClaims = emptyList(),
                            digestAlgorithm = DigestAlg.SHA256,
                        ),
                    audience = "https://verifier.example",
                    nonce = "nonce",
                    algorithm = "ES256",
                    issuedAtEpochSeconds = 1L,
                )

            assertEquals(setOf("alg", "typ"), input.protectedHeader.keys)
            assertEquals("ES256", input.protectedHeader.getValue("alg").jsonPrimitive.content)
            assertEquals("kb+jwt", input.protectedHeader.getValue("typ").jsonPrimitive.content)
            assertFalse("jwk" in input.protectedHeader)
        }
}
