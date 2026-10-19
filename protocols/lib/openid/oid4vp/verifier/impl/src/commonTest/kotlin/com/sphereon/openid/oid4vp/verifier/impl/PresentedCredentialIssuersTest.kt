/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.openid.oid4vp.verifier.CredentialIssuerAuthenticationLookup
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationPurpose
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentedCredentialIssuersTest {
    private fun jwt(payload: String) =
        "${"""{"alg":"ES256"}""".encodeToByteArray().encodeToBase64Url()}.${payload.encodeToByteArray().encodeToBase64Url()}.c2ln"

    private val jwks = Json.parseToJsonElement(
        """{"keys":[{"kty":"EC","crv":"P-256","x":"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU","y":"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0","kid":"k1"}]}""",
    ).jsonObject

    private fun admitted(controller: String, purpose: TrustedAuthenticationPurpose = TrustedAuthenticationPurpose.CREDENTIAL_ISSUER) =
        TrustedAuthenticationResolution(controller, trustedJwks = jwks, purpose = purpose)

    @Test
    fun collectsHttpsIssuersOfSdJwtCompactCredentialsAndPresentationChildren() {
        val sdJwt = jwt("""{"iss":"https://issuer-a.example","vct":"t"}""") + "~disclosure~"
        val child = jwt("""{"iss":"https://issuer-c.example","vc":{"issuer":"https://issuer-c.example"}}""")
        val presentation = jwt("""{"iss":"did:example:holder","vp":{"verifiableCredential":["$child",{"id":"data:application/vc+jwt,${jwt("""{"vc":{"issuer":{"id":"https://issuer-d.example"}}}""")}"}]}}""")
        val issuers = PresentedCredentialIssuers.collect(
            listOf(
                JsonPrimitive(sdJwt),
                JsonPrimitive(jwt("""{"iss":"did:example:issuer"}""")),
                JsonPrimitive(jwt("""{"iss":"http://insecure.example"}""")),
                JsonPrimitive(presentation),
                Json.parseToJsonElement("""{"issuer":"https://ldp.example"}"""),
            ),
        )
        assertEquals(listOf("https://issuer-a.example", "https://issuer-c.example", "https://issuer-d.example"), issuers)
    }

    @Test
    fun resolvesOnlyUnconfiguredIssuersAndKeepsOnlyExactAdmittedMaterial() = runTest {
        val looked = mutableListOf<String>()
        val lookup = CredentialIssuerAuthenticationLookup { issuer ->
            looked += issuer
            when (issuer) {
                "https://admitted.example" -> Ok(admitted(issuer))
                "https://other-controller.example" -> Ok(admitted("https://someone-else.example"))
                "https://holder-purpose.example" -> Ok(admitted(issuer, TrustedAuthenticationPurpose.HOLDER))
                "https://failing.example" -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "refused"))
                // X.509 material would also count as an mdoc status root for every credential; refused on demand.
                "https://x509.example" -> Ok(TrustedAuthenticationResolution(issuer, ExternalIdentifierX5cOpts(listOf("root")), purpose = TrustedAuthenticationPurpose.CREDENTIAL_ISSUER))
                else -> Ok(null)
            }
        }
        val presentations = listOf(
            "https://configured.example", "https://admitted.example", "https://other-controller.example",
            "https://holder-purpose.example", "https://failing.example", "https://x509.example", "https://unknown.example",
        ).map { JsonPrimitive(jwt("""{"iss":"$it"}""")) }

        val resolved = PresentedCredentialIssuers.resolve(presentations, listOf(admitted("https://configured.example")), lookup)

        assertEquals(listOf("https://admitted.example"), resolved.map { it.controller })
        assertEquals(null, mdocStatusTrustAnchors(resolved))
        assertTrue("https://configured.example" !in looked)
    }

    @Test
    fun boundsTheNumberOfLookedUpIssuers() {
        val presentations = (1..40).map { JsonPrimitive(jwt("""{"iss":"https://issuer-$it.example"}""")) }
        assertEquals(PresentedCredentialIssuers.MAX_ISSUERS, PresentedCredentialIssuers.collect(presentations).size)
    }
}
