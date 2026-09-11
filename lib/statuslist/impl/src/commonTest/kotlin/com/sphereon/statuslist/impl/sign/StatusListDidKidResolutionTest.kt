/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.statuslist.impl.sign

import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.resolver.DidResolutionResult
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusListDidKidResolutionTest {
    private val did = "did:web:tenant.example"
    private val signingKey = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = "issuer-x", y = "issuer-y")

    @Test
    fun returnsTheFullAbsoluteIdOfTheMatchingAssertionMethod() {
        val relative = verificationMethod("#issuer-assertion", signingKey)
        val resolution = resolution(relative, assertionIds = listOf(relative.id))

        assertEquals(
            "$did#issuer-assertion",
            findStatusListAssertionMethodId(resolution, signingKey, requiredId = "$did#issuer-assertion"),
        )
    }

    @Test
    fun refusesAConfiguredIdThatIsMissingNotAnAssertionOrBoundToAnotherKey() {
        val actual = verificationMethod("$did#issuer-assertion", signingKey)
        val otherKey = signingKey.copy(x = "other-x", y = "other-y")

        assertNull(findStatusListAssertionMethodId(resolution(actual, assertionIds = listOf(actual.id)), signingKey, "$did#missing"))
        assertNull(findStatusListAssertionMethodId(resolution(actual, assertionIds = emptyList()), signingKey, actual.id))
        assertNull(findStatusListAssertionMethodId(resolution(actual, assertionIds = listOf(actual.id)), otherKey, actual.id))
    }

    @Test
    fun reportsAssertionAndExactKeyMatchCountsWithoutExposingKeyMaterial() {
        val actual = verificationMethod("$did#issuer-assertion", signingKey)
        val other = verificationMethod("$did#other", signingKey.copy(x = "other-x", y = "other-y"))
        val result =
            matchStatusListAssertionMethod(
                resolution(
                    methods = listOf(actual, other),
                    assertionIds = listOf(actual.id, other.id),
                ),
                signingKey,
                requiredId = "$did#missing",
            )

        assertNull(result.id)
        assertEquals(2, result.assertionMethodCount)
        assertEquals(1, result.keyMatchCount)
    }

    @Test
    fun matchesTheHostedDidJsonWireShapeReturnedByTenantDidBootstrap() {
        val document =
            Json.decodeFromString<DidDocument>(
                """{
                  "id":"$did",
                  "verificationMethod":[{
                    "id":"$did#issuer-assertion",
                    "type":"JsonWebKey2020",
                    "controller":"$did",
                    "publicKeyJwk":{
                      "alg":"ES256","crv":"P-256","kty":"EC",
                      "x":"issuer-x","y":"issuer-y","x5c":["public-certificate"]
                    }
                  }],
                  "assertionMethod":["$did#issuer-assertion"]
                }""",
            )
        val resolution = DidResolutionResult.success(document)

        assertEquals(
            "$did#issuer-assertion",
            findStatusListAssertionMethodId(resolution, signingKey, requiredId = "$did#issuer-assertion"),
        )
    }

    @Test
    fun hostedStatusListDidWebAuthorityRetainsANonDefaultPort() {
        assertEquals(
            "tenant.example:25443",
            statusListWebAuthorityOf("https://tenant.example:25443/public/statuslists/revocation"),
        )
        assertNull(statusListWebAuthorityOf("https://user@tenant.example:25443/public/statuslists/revocation"))
    }

    private fun verificationMethod(id: String, key: Jwk) =
        VerificationMethod(
            id = id,
            type = "JsonWebKey2020",
            controller = did,
            publicKeyJwk = key,
        )

    private fun resolution(
        method: VerificationMethod,
        assertionIds: List<String>,
    ): DidResolutionResult = resolution(listOf(method), assertionIds)

    private fun resolution(
        methods: List<VerificationMethod>,
        assertionIds: List<String>,
    ): DidResolutionResult =
        DidResolutionResult.success(
            DidDocument(
                id = did,
                verificationMethod = methods,
                assertionMethod = assertionIds.map { VerificationMethodOrReference.fromReference(it) },
            ),
        )
}
