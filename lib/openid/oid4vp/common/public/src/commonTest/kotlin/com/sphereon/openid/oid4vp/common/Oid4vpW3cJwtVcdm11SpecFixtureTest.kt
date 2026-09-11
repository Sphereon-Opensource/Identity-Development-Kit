/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vp.common

import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural interoperability fixtures taken from the OpenID4VP final specification.
 *
 * These examples intentionally have no signature or public key and therefore are not
 * cryptographic vectors. They pin the wire shape which a real JWT VP must carry; signature
 * verification is covered by the pinned W3C VC-JOSE-COSE vectors in the VCDM verifier tests.
 *
 * Sources:
 * - OpenID4VP 1.0 Final, Appendix B.1.3.1.4 (JWT VC DCQL request):
 *   https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-presentation-request
 * - OpenID4VP 1.0 Final, Appendix B.1.3.1.5 and Security Section 14.1.2 (JWT VP response,
 *   nonce and audience binding):
 *   https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#preventing-replay-of-verifiable-presentations
 * - W3C VCDM 1.1, Section 6.3.1 (JWT `vc` and `vp` claims):
 *   https://www.w3.org/TR/vc-data-model-1.1/#json-web-token
 */
class Oid4vpW3cJwtVcdm11SpecFixtureTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `OID4VP JWT VC DCQL fixture uses the VCDM 1 point 1 credential subject paths`() {
        val query = json.decodeFromString<DcqlQuery>(JWT_VC_DCQL_REQUEST)
        val credential = query.credentials.single()

        assertEquals("example_jwt_vc", credential.id)
        assertEquals("jwt_vc_json", credential.format)
        assertEquals(
            listOf(
                listOf("IDCredential"),
            ),
            credential.meta["type_values"]!!.jsonArray.map { alternative ->
                alternative.jsonArray.map { it.jsonPrimitive.content }
            },
        )
        assertEquals(
            listOf(
                listOf("credentialSubject", "family_name"),
                listOf("credentialSubject", "given_name"),
            ),
            credential.claims!!.map { claim -> claim.path.components.map { it.jsonPrimitive.content } },
        )
    }

    @Test
    fun `OID4VP VCDM 1 point 1 JWT VP fixture carries nonce and audience outside the vc`() {
        val payload = json.parseToJsonElement(JWT_VP_PAYLOAD).jsonObject
        val vp = assertNotNull(payload["vp"] as? JsonObject)
        val credentials = assertNotNull(vp["verifiableCredential"] as? JsonArray)

        assertEquals("did:example:ebfeb1f712ebc6f1c276e12ec21", payload["iss"]!!.jsonPrimitive.content)
        assertEquals("x509_san_dns:client.example.org", payload["aud"]!!.jsonPrimitive.content)
        assertEquals("n-0S6_WzA2Mj", payload["nonce"]!!.jsonPrimitive.content)
        assertEquals("https://www.w3.org/2018/credentials/v1", vp["@context"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("VerifiablePresentation", vp["type"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals(1, credentials.size)
        assertTrue(credentials.single().jsonPrimitive.isString)
    }

    private companion object {
        // OpenID4VP Appendix B.1.3.1.4. This is a normative-shape fixture only; the example is
        // non-normative prose and deliberately contains no issuer key material or signature.
        const val JWT_VC_DCQL_REQUEST =
            """
            {
              "credentials": [
                {
                  "id": "example_jwt_vc",
                  "format": "jwt_vc_json",
                  "meta": {"type_values": [["IDCredential"]]},
                  "claims": [
                    {"path": ["credentialSubject", "family_name"]},
                    {"path": ["credentialSubject", "given_name"]}
                  ]
                }
              ]
            }
            """

        // OpenID4VP Appendix B.1.3.1.5. The compact JWT in the specification is shortened, so
        // this fixture retains the decoded payload only and tests the claims that bind a VP to
        // its request. It must never be treated as a signed or verifiable artifact.
        const val JWT_VP_PAYLOAD =
            """
            {
              "iss": "did:example:ebfeb1f712ebc6f1c276e12ec21",
              "jti": "urn:uuid:3978344f-8596-4c3a-a978-8fcaba3903c5",
              "aud": "x509_san_dns:client.example.org",
              "nbf": 1541493724,
              "iat": 1541493724,
              "exp": 1573029723,
              "nonce": "n-0S6_WzA2Mj",
              "vp": {
                "@context": ["https://www.w3.org/2018/credentials/v1"],
                "type": ["VerifiablePresentation"],
                "verifiableCredential": ["eyJhb...ssw5c"]
              }
            }
            """
    }
}
