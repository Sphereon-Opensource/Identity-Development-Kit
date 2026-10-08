/*
 * Copyright (c) 2026 Sphereon B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.sphereon.openid.oid4vci.issuer.impl.signing

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.resolver.DidResolutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssuerKeyIdResolverTest {
    private val did = "did:web:tenant.example"
    private val signingKey =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "issuer-x",
            y = "issuer-y",
            kid = "private-kms-kid",
            alg = JwaAlgorithm.ES256,
        )

    @Test
    fun hostedDidUsesPublishedAssertionMethodWhoseMaterialMatchesTheSigningKey() {
        val verifier = vm("verifier-request", "verifier-x", "verifier-y")
        val issuer =
            VerificationMethod(
                id = "$did#issuer-assertion-tenant",
                type = "JsonWebKey2020",
                controller = did,
                publicKeyJwk = signingKey.copy(kid = null, alg = null, x5c = arrayOf("certificate-metadata-is-ignored")),
            )
        val result = resolution(verifier, issuer)

        assertEquals("$did#issuer-assertion-tenant", findPublishedAssertionMethodId(result, signingKey))
    }

    @Test
    fun hostedDidRefusesMissingOrAmbiguousSigningMaterial() {
        val unrelated = vm("issuer-assertion-other", "other-x", "other-y")
        assertNull(findPublishedAssertionMethodId(resolution(unrelated), signingKey))

        val first = vm("issuer-assertion-a", "issuer-x", "issuer-y")
        val second = vm("issuer-assertion-b", "issuer-x", "issuer-y")
        assertNull(findPublishedAssertionMethodId(resolution(first, second), signingKey))
    }

    @Test
    fun hostedDidReturnsTheFullAbsoluteAssertionMethodId() {
        val relative =
            VerificationMethod(
                id = "#issuer-assertion-tenant",
                type = "JsonWebKey2020",
                controller = did,
                publicKeyJwk = signingKey.copy(kid = null, alg = null),
            )

        assertEquals("$did#issuer-assertion-tenant", findPublishedAssertionMethodId(resolution(relative), signingKey))
        assertEquals("$did#issuer-assertion-tenant", absoluteDidVerificationMethodId(did, "#issuer-assertion-tenant"))
        assertNull(absoluteDidVerificationMethodId(did, "issuer-assertion-tenant"))
        assertNull(absoluteDidVerificationMethodId(did, "did:web:other.example#issuer-assertion-tenant"))
    }

    @Test
    fun matchingVerificationMethodThatIsNotAnAssertionMethodIsRefused() {
        val method = vm("issuer-assertion-tenant", "issuer-x", "issuer-y")
        val document = DidDocument(id = did, verificationMethod = listOf(method), assertionMethod = emptyList())

        assertNull(findPublishedAssertionMethodId(DidResolutionResult.success(document), signingKey))
    }

    @Test
    fun issuerDidWebAuthorityRetainsANonDefaultPort() {
        assertEquals(
            "tenant.example:25443",
            DefaultIssuerKeyIdResolver.hostOf("https://tenant.example:25443/oid4vci"),
        )
        assertEquals("tenant.example:25443", DefaultIssuerKeyIdResolver.hostOf("tenant.example:25443"))
        assertNull(DefaultIssuerKeyIdResolver.hostOf("https://user@tenant.example:25443/oid4vci"))
    }

    private fun vm(fragment: String, x: String, y: String) =
        VerificationMethod(
            id = "$did#$fragment",
            type = "JsonWebKey2020",
            controller = did,
            publicKeyJwk = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = x, y = y),
        )

    private fun resolution(vararg methods: VerificationMethod): DidResolutionResult {
        val document =
            DidDocument(
                id = did,
                verificationMethod = methods.toList(),
                assertionMethod = methods.map { VerificationMethodOrReference.fromReference(it.id) },
            )
        return DidResolutionResult.success(document)
    }
}
