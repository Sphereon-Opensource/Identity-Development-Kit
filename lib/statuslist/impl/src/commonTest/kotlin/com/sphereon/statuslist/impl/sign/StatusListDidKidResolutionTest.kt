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
    ): DidResolutionResult =
        DidResolutionResult.success(
            DidDocument(
                id = did,
                verificationMethod = listOf(method),
                assertionMethod = assertionIds.map { VerificationMethodOrReference.fromReference(it) },
            ),
        )
}
