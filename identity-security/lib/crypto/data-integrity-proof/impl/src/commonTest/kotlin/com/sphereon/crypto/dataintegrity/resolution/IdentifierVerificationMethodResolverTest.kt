/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.resolution

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.MultiIdentifierResolutionService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.extern.DIDDocument
import com.sphereon.crypto.resolution.extern.DIDResolutionResult
import com.sphereon.crypto.resolution.extern.ParsedDID
import com.sphereon.crypto.resolution.extern.VerificationMethod
import com.sphereon.crypto.resolution.managed.ManagedIdentifierKeyResult
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentifierVerificationMethodResolverTest {
    @Test
    fun arbitraryHttpsReferenceIsRejectedWithoutExplicitPolicy() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val result = IdentifierVerificationMethodResolver(service).resolve("https://issuer.example/keys#key-1")
        assertTrue(result.isErr)
        assertEquals(null, service.captured)
    }

    @Test
    fun explicitHttpsJwksPolicyUsesCanonicalOptionsAndTrustedMetadata() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val jwk = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ")
        val opts = ExternalIdentifierJwksUrlOpts(
            identifier = "https://issuer.example/keys",
            lookup = com.sphereon.crypto.resolution.AdditionalIdentifierLookup(kid = "key-1"),
        )
        service.result = ExternalIdentifierResult.JwksUrl(
            identifierOpts = opts,
            jwks = arrayOf<ResolvedKeyInfoType<JwkType>>(
                ResolvedKeyInfo(kid = "key-1", key = jwk),
            ),
            keyInfo = ResolvedKeyInfo(kid = "key-1", key = jwk),
            jwksUrl = opts.identifier,
            selectedKid = "key-1",
        )

        val reference = "${opts.identifier}#key-1"
        val policy = VerificationMethodResolutionPolicy.of(
            listOf(
                TrustedVerificationMethod(
                    reference = reference,
                    identifierOpts = opts,
                    controller = "https://issuer.example/",
                    authorizedProofPurposes = setOf(ProofPurpose.ASSERTION_METHOD),
                ),
            ),
        )
        val resolved = IdentifierVerificationMethodResolver(service).resolve(reference, policy)

        assertTrue(resolved.isOk)
        assertIs<ExternalIdentifierJwksUrlOpts>(service.captured)
        assertEquals(opts, service.captured)
        assertEquals("https://issuer.example/", resolved.value.controller)
        assertEquals(setOf(ProofPurpose.ASSERTION_METHOD), resolved.value.authorizedProofPurposes)
    }

    @Test
    fun jwksPolicyRejectsResolverResultWithDifferentKid() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val opts = ExternalIdentifierJwksUrlOpts(
            identifier = "https://issuer.example/keys",
            lookup = com.sphereon.crypto.resolution.AdditionalIdentifierLookup(kid = "key-1"),
        )
        val jwk = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ")
        service.result = ExternalIdentifierResult.JwksUrl(
            identifierOpts = opts,
            jwks = arrayOf(ResolvedKeyInfo(kid = "key-2", key = jwk)),
            keyInfo = ResolvedKeyInfo(kid = "key-2", key = jwk),
            jwksUrl = opts.identifier,
            selectedKid = "key-2",
        )
        val result = IdentifierVerificationMethodResolver(service).resolve(
            "https://issuer.example/keys#key-1",
            trustedPolicy("https://issuer.example/keys#key-1", opts),
        )
        assertTrue(result.isErr)
    }

    @Test
    fun explicitX509PolicyUsesX5cOptions() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val reference = "https://issuer.example/verification-methods/x509-1"
        val policy = trustedPolicy(reference, ExternalIdentifierX5cOpts(listOf("MIIB-test-certificate")))
        val result = IdentifierVerificationMethodResolver(service).resolve(reference, policy)
        assertTrue(result.isErr)
        assertIs<ExternalIdentifierX5cOpts>(service.captured)
        assertEquals(listOf("MIIB-test-certificate"), (service.captured as ExternalIdentifierX5cOpts).identifier)
    }

    @Test
    fun verifiedX509ChainCanBackAnExactPublicVerificationMethodUrl() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val reference = "https://issuer.example/verification-methods/x509-1"
        val opts = ExternalIdentifierX5cOpts(listOf("MIIB-test-certificate"))
        val jwk = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ")
        service.result = ExternalIdentifierResult.X5c(
            identifierOpts = opts,
            jwks = arrayOf(ResolvedKeyInfo(kid = reference, key = jwk)),
            keyInfo = ResolvedKeyInfo(kid = reference, key = jwk),
            x5c = opts.identifier,
            verificationResult = X509VerificationResult(
                certificateChain = emptyArray(),
                publicKey = jwk,
                critical = false,
                message = "certificate chain verified",
                error = false,
            ),
            certificates = emptyList(),
        )

        val result = IdentifierVerificationMethodResolver(service).resolve(
            reference,
            trustedPolicy(reference, opts),
        )

        assertTrue(result.isOk)
        assertEquals(reference, result.value.reference)
        assertEquals("https://issuer.example/", result.value.controller)
    }

    @Test
    fun x509VerificationFailureIsNeverPromotedToAResolvedMethod() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val reference = "https://issuer.example/verification-methods/x509-1"
        val opts = ExternalIdentifierX5cOpts(listOf("MIIB-test-certificate"))
        val jwk = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ")
        service.result = ExternalIdentifierResult.X5c(
            identifierOpts = opts,
            jwks = arrayOf(ResolvedKeyInfo(kid = reference, key = jwk)),
            keyInfo = ResolvedKeyInfo(kid = reference, key = jwk),
            x5c = opts.identifier,
            verificationResult = X509VerificationResult(
                certificateChain = emptyArray(),
                publicKey = jwk,
                critical = true,
                message = "certificate validation failed",
                error = true,
            ),
            certificates = emptyList(),
        )
        val result = IdentifierVerificationMethodResolver(service).resolve(reference, trustedPolicy(reference, opts))
        assertTrue(result.isErr)
    }

    @Test
    fun managedAliasPolicyUsesCanonicalManagedOptionsAndTrustedMetadata() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val jwk = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ")
        service.result = ManagedIdentifierKeyResult(
            context = IdentifierContext(),
            keyInfo = ManagedKeyInfo(
                alias = "issuer-signing-key",
                providerId = "test-kms",
                resolvedKeyInfo = ResolvedKeyInfo(key = jwk),
            ),
            identifier = jwk,
        )

        val reference = "https://issuer.example/verification-methods/managed-1"
        val policy = trustedPolicy(reference, ManagedOptsAlias("issuer-signing-key"))
        val resolved = IdentifierVerificationMethodResolver(service).resolve(reference, policy)

        assertTrue(resolved.isOk)
        assertIs<ManagedOptsAlias>(service.captured)
        assertEquals("issuer-signing-key", (service.captured as ManagedOptsAlias).identifier)
        assertEquals("https://issuer.example/", resolved.value.controller)
        assertEquals(setOf(ProofPurpose.ASSERTION_METHOD), resolved.value.authorizedProofPurposes)
    }

    @Test
    fun didResolutionDerivesControllerAndProofPurposesFromDidDocument() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val reference = "did:example:issuer#key-1"
        val jwk = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ")
        service.result = ExternalIdentifierResult.Did(
            identifierOpts = ExternalIdentifierDidOpts(reference),
            jwks = arrayOf(ResolvedKeyInfo(kid = reference, key = jwk)),
            keyInfo = ResolvedKeyInfo(kid = reference, key = jwk),
            did = "did:example:issuer",
            didDocument = DIDDocument(
                id = "did:example:issuer",
                verificationMethod = listOf(VerificationMethod(reference, "JsonWebKey", "did:example:issuer", publicKeyJwk = jwk)),
                assertionMethod = listOf(reference),
                authentication = listOf(reference),
            ),
            didResolutionResult = DIDResolutionResult(),
            didParsed = ParsedDID("did:example:issuer", "example", "issuer", fragment = "key-1"),
        )
        val resolved = IdentifierVerificationMethodResolver(service).resolve(reference)
        assertTrue(resolved.isOk)
        assertEquals("did:example:issuer", resolved.value.controller)
        assertEquals(setOf(ProofPurpose.ASSERTION_METHOD, ProofPurpose.AUTHENTICATION), resolved.value.authorizedProofPurposes)
    }

    @Test
    fun policyIsExactAndDoesNotAllowControllerOrPurposeMetadataToDrift() = kotlinx.coroutines.test.runTest {
        val service = CapturingIdentifierService()
        val configured = "https://issuer.example/keys#key-1"
        val policy = trustedPolicy(
            configured,
            ExternalIdentifierJwksUrlOpts("https://issuer.example/keys", lookup = com.sphereon.crypto.resolution.AdditionalIdentifierLookup(kid = "key-1")),
        )
        val resolver = IdentifierVerificationMethodResolver(service)
        assertTrue(resolver.resolve("https://issuer.example/keys#other", policy).isErr)
        assertFailsWith<IllegalArgumentException> {
            TrustedVerificationMethod(configured, policy.entry(configured)!!.identifierOpts, "", setOf(ProofPurpose.ASSERTION_METHOD))
        }
    }

    private fun trustedPolicy(reference: String, options: IdentifierOptsOrResult): VerificationMethodResolutionPolicy =
        VerificationMethodResolutionPolicy.of(
            listOf(
                TrustedVerificationMethod(
                    reference = reference,
                    identifierOpts = options,
                    controller = "https://issuer.example/",
                    authorizedProofPurposes = setOf(ProofPurpose.ASSERTION_METHOD),
                ),
            ),
        )
}

private class CapturingIdentifierService : MultiIdentifierResolutionService {
    var captured: IdentifierOptsOrResult? = null
    var result: IdentifierOptsOrResult? = null
    override val supportedIdentifierMethods: List<IIdentifierMethod> = IdentifierMethodDefaults.entries

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean = true
    override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = true
    override suspend fun isSupportedOpts(opts: IdentifierOptsOrResult): Boolean = true
    override suspend fun asSupportedOpts(opts: IdentifierOptsOrResult): IdkResult<IdentifierOptsOrResult, IdkErrorType> = com.sphereon.core.api.Ok(opts)
    override suspend fun resolve(opts: IdentifierOptsOrResult): IdkResult<out IdentifierOptsOrResult, IdkErrorType> {
        captured = opts
        result?.let { return Ok(it) }
        return Err(IdkError.NOT_FOUND_ERROR(resource = opts.identifier.toString(), message = "test capture"))
    }
}
