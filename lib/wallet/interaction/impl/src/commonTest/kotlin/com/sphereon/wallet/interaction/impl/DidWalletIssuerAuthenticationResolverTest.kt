/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.did.methods.jwk.JwkDidResolverImpl
import com.sphereon.did.methods.key.KeyDidResolverImpl
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.resolver.DidDereferenceOptions
import com.sphereon.did.resolver.DidDereferenceResult
import com.sphereon.did.resolver.DidResolutionMetadata
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolver
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.impl.DidResolverRegistryImpl
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationRequest
import com.sphereon.wallet.interaction.WalletProtocol
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

class DidWalletIssuerAuthenticationResolverTest {
    private val resolver =
        DidWalletIssuerAuthenticationResolver(
            DidResolverRegistryImpl(
                setOf(KeyDidResolverImpl(), JwkDidResolverImpl()),
            ),
        )

    @Test
    fun didKeyUsesOnlyAssertionMethodWithExactVerificationMethodKid() =
        runTest {
            val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
            val result = assertNotNull(resolver.resolve(request(did)))

            val key = result.trustedJwks["keys"]!!.jsonArray.single().jsonObject
            assertEquals("$did#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", key["kid"]!!.jsonPrimitive.content)
            assertEquals(did, result.issuer)
            assertEquals("did.assertionMethod", result.provenance.single().source)
            assertEquals(did, result.provenance.single().reference)
            assertTrue("d" !in key, "Issuer authentication must contain public material only")
        }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun didJwkUsesAssertionMethodAndNotAuthenticationOrKeyAgreement() =
        runTest {
            val jwk = """{"kty":"EC","crv":"P-256","x":"WKn-ZIGevcwGFOMJ0GeEei2HiO3I-oMECoZQZ0KdLJ0","y":"Xch1SqgK8B0K3w6V3a9RbU4HlB1pdP_EEcyLpRZ4Mds"}"""
            val did = "did:jwk:${Base64.UrlSafe.encode(jwk.encodeToByteArray()).trimEnd('=')}"
            val result = assertNotNull(resolver.resolve(request(did)))

            val key = result.trustedJwks["keys"]!!.jsonArray.single().jsonObject
            assertEquals("$did#0", key["kid"]!!.jsonPrimitive.content)
            assertEquals("EC", key["kty"]!!.jsonPrimitive.content)
            assertTrue("x5c" !in key, "Transport certificate material must not enter DID-derived authority")
        }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun didJwkKeyAgreementOnlyAndWrongRelationshipAreRejected() =
        runTest {
            val jwk = """{"kty":"EC","crv":"P-256","x":"WKn-ZIGevcwGFOMJ0GeEei2HiO3I-oMECoZQZ0KdLJ0","y":"Xch1SqgK8B0K3w6V3a9RbU4HlB1pdP_EEcyLpRZ4Mds","use":"enc"}"""
            val did = "did:jwk:${Base64.UrlSafe.encode(jwk.encodeToByteArray()).trimEnd('=')}"

            assertNull(resolver.resolve(request(did)))
            assertNull(resolver.resolve(request(did, role = WalletCounterpartyRole.VERIFIER)))
        }

    @Test
    fun compositeResolverLeavesRoomForDeploymentOwnedSources() =
        runTest {
            val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
            val expected = resolver.resolve(request(did))
            val composite = CompositeWalletIssuerAuthenticationResolver(listOf(WalletIssuerAuthenticationResolverProbe(expected)))

            assertEquals(expected, composite.resolve(request(did)))
        }

    @Test
    fun compositeResolverPassesNonDidIssuerToDeploymentExtension() =
        runTest {
            val expected = assertNotNull(resolver.resolve(request("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")))
            var observedIssuer: String? = null
            val nonDidIssuer = "https://issuer.example"
            val extension =
                object : com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolver {
                    override suspend fun resolve(input: WalletIssuerAuthenticationRequest) =
                        if (input.counterparty.identifier == nonDidIssuer) {
                            observedIssuer = input.counterparty.identifier
                            expected
                        } else {
                            null
                        }
                }

            assertNotNull(
                CompositeWalletIssuerAuthenticationResolver(listOf(resolver, extension)).resolve(request(nonDidIssuer)),
            )
            assertEquals(nonDidIssuer, observedIssuer)
        }

    @Test
    fun privateJwkMaterialIsRejectedBeforePublicKeyNormalization() =
        runTest {
            val did = "did:example:issuer"
            val privateJwk = publicJwk.copy(d = "private-material")
            assertNull(resolveStatic(did, verificationMethod(did, privateJwk)))
        }

    @Test
    fun symmetricJwkIsRejectedEvenWhenAssertionMethodReferencesIt() =
        runTest {
            val did = "did:example:issuer"
            val symmetricJwk = Jwk(kty = JwaKeyType.oct, k = "c2VjcmV0")
            assertNull(resolveStatic(did, verificationMethod(did, symmetricJwk)))
        }

    @Test
    fun revokedAndExpiredVerificationMethodsAreRejected() =
        runTest {
            val did = "did:example:issuer"
            val revoked = verificationMethod(did, publicJwk, revokedAt = Instant.parse("2026-01-01T00:00:00Z"))
            val expired = verificationMethod(did, publicJwk, expiresAt = Instant.parse("2020-01-01T00:00:00Z"))

            assertNull(resolveStatic(did, revoked))
            assertNull(resolveStatic(did, expired))
        }

    @Test
    fun expiryBoundaryUsesInjectedClock() =
        runTest {
            val did = "did:example:issuer"
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val vm = verificationMethod(did, publicJwk, expiresAt = now)
            val resolver =
                DidWalletIssuerAuthenticationResolver(
                    StaticDidResolverRegistry(DidResolutionResult.success(document(did, vm))),
                    now = { now },
                )

            assertNull(resolver.resolve(request(did)))
        }

    @Test
    fun resolutionErrorMetadataFailsClosedEvenWhenDocumentIsPresent() =
        runTest {
            val did = "did:example:issuer"
            val document = document(did, verificationMethod(did, publicJwk))
            val registry =
                StaticDidResolverRegistry(
                    DidResolutionResult(
                        didDocument = document,
                        didResolutionMetadata = DidResolutionMetadata(error = DidResolutionMetadata.ERROR_NOT_FOUND),
                    ),
                )

            assertNull(DidWalletIssuerAuthenticationResolver(registry).resolve(request(did)))
        }

    @Test
    fun assertionMethodSigningPurposeRejectsEncryptionAndNonSigningCurves() =
        runTest {
            val did = "did:example:issuer"
            val encryption = publicJwk.copy(use = "enc", key_ops = arrayOf(JoseKeyOperations.ENCRYPT))
            val signingOnly = publicJwk.copy(key_ops = arrayOf(JoseKeyOperations.SIGN))
            val x25519 = publicJwk.copy(kty = JwaKeyType.OKP, crv = JwaCurve.X25519, x = "x")

            assertNull(resolveStatic(did, verificationMethod(did, encryption)))
            assertNull(resolveStatic(did, verificationMethod(did, signingOnly, id = "$did#signing-only")))
            assertNull(resolveStatic(did, verificationMethod(did, x25519, id = "$did#x25519")))
        }

    @Test
    fun vcdm20JwtIssuerUsesPinnedAssertionVerificationKey() =
        runTest {
            val did = "did:example:vcdm20-issuer"
            val vm = verificationMethod(did, publicJwk.copy(use = "sig", key_ops = arrayOf(JoseKeyOperations.VERIFY)))
            val result = assertNotNull(resolveStatic(did, vm))
            val key = result.trustedJwks["keys"]!!.jsonArray.single().jsonObject

            assertEquals(vm.id, key["kid"]!!.jsonPrimitive.content)
            assertEquals("EC", key["kty"]!!.jsonPrimitive.content)
            assertEquals("P-256", key["crv"]!!.jsonPrimitive.content)
        }

    @Test
    fun pinnedDidJwkDoesNotCarryCertificateMaterialIntoTrustedSet() =
        runTest {
            val did = "did:example:issuer"
            val vm = verificationMethod(did, publicJwk.copy(x5c = arrayOf("certificate")))
            val result = assertNotNull(resolveStatic(did, vm))

            assertTrue("x5c" !in result.trustedJwks["keys"]!!.jsonArray.single().jsonObject)
        }

    @Test
    fun deactivatedDidAndMismatchedDocumentIdentityAreRejected() =
        runTest {
            val did = "did:example:issuer"
            val vm = verificationMethod(did, publicJwk)
            val deactivated =
                StaticDidResolverRegistry(
                    DidResolutionResult.success(
                        document(did, vm),
                        metadata = com.sphereon.did.resolver.DidDocumentMetadata(deactivated = true),
                    ),
                )
            val mismatched =
                StaticDidResolverRegistry(
                    DidResolutionResult.success(document("did:example:other", verificationMethod("did:example:other", publicJwk))),
                )

            assertNull(DidWalletIssuerAuthenticationResolver(deactivated).resolve(request(did)))
            assertNull(DidWalletIssuerAuthenticationResolver(mismatched).resolve(request(did)))
        }

    @Test
    fun duplicateAssertionMethodKidsFailClosed() =
        runTest {
            val did = "did:example:issuer"
            val vm = verificationMethod(did, publicJwk)
            val doc =
                DidDocument(
                    id = did,
                    verificationMethod = listOf(vm),
                    assertionMethod = listOf(VerificationMethodOrReference.fromReference(vm.id), VerificationMethodOrReference.fromReference(vm.id)),
                )

            assertNull(DidWalletIssuerAuthenticationResolver(StaticDidResolverRegistry(DidResolutionResult.success(doc))).resolve(request(did)))
        }

    @Test
    fun nonDidVerificationMethodIdentifierIsNotIssuerAuthority() =
        runTest {
            val did = "did:example:issuer"
            val vm = verificationMethod(did, publicJwk, id = "urn:example:key")
            assertNull(resolveStatic(did, vm))
        }

    @Test
    fun verificationMethodControllerMustMatchIssuerDid() =
        runTest {
            val did = "did:example:issuer"
            val vm = verificationMethod(did, publicJwk).copy(controller = "did:example:other")

            assertNull(resolveStatic(did, vm))
        }

    @Test
    fun dereferencedVerificationMethodMustMatchRequestedAbsoluteReference() =
        runTest {
            val did = "did:example:issuer"
            val requested = verificationMethod(did, publicJwk)
            val returned = requested.copy(id = "$did#different")
            val registry =
                StaticDidResolverRegistry(
                    DidResolutionResult.success(document(did, requested)),
                    dereferencedVerificationMethod = returned,
                )

            assertNull(DidWalletIssuerAuthenticationResolver(registry).resolve(request(did)))
        }

    @Test
    fun anyUnresolvableAssertedVerificationMethodFailsClosed() =
        runTest {
            val did = "did:example:issuer"
            val vm = verificationMethod(did, publicJwk)
            val doc =
                document(
                    did,
                    vm,
                    assertionMethods =
                        listOf(
                            VerificationMethodOrReference.fromReference(vm.id),
                            VerificationMethodOrReference.fromReference("$did#missing"),
                        ),
                )

            assertNull(DidWalletIssuerAuthenticationResolver(StaticDidResolverRegistry(DidResolutionResult.success(doc))).resolve(request(did)))
        }

    private suspend fun resolveStatic(did: String, vm: VerificationMethod): com.sphereon.wallet.interaction.WalletIssuerAuthenticationResult? =
        DidWalletIssuerAuthenticationResolver(
            StaticDidResolverRegistry(DidResolutionResult.success(document(did, vm))),
        ).resolve(request(did))

    private fun document(
        did: String,
        vm: VerificationMethod,
        assertionMethods: List<VerificationMethodOrReference> = listOf(VerificationMethodOrReference.fromReference(vm.id)),
    ): DidDocument =
        DidDocument(
            id = did,
            verificationMethod = listOf(vm),
            assertionMethod = assertionMethods,
        )

    private fun verificationMethod(
        did: String,
        jwk: Jwk,
        id: String = "$did#key-1",
        expiresAt: Instant? = null,
        revokedAt: Instant? = null,
    ): VerificationMethod =
        VerificationMethod(
            id = id,
            type = "JsonWebKey2020",
            controller = did,
            publicKeyJwk = jwk,
            expiresAt = expiresAt,
            revokedAt = revokedAt,
        )

    private val publicJwk =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WKn-ZIGevcwGFOMJ0GeEei2HiO3I-oMECoZQZ0KdLJ0",
            y = "Xch1SqgK8B0K3w6V3a9RbU4HlB1pdP_EEcyLpRZ4Mds",
        )

    private class StaticDidResolverRegistry(
        private val resolution: DidResolutionResult,
        private val dereferencedVerificationMethod: VerificationMethod? = null,
    ) : DidResolverRegistry {
        override fun getResolver(method: String): DidResolver? = null

        override fun getCapabilities(method: String) = null

        override fun getSupportedMethods(): List<String> = listOf("example")

        override suspend fun resolve(did: String, options: DidResolutionOptions): IdkResult<DidResolutionResult, IdkError> = Ok(resolution)

        override suspend fun dereference(didUrl: String, options: DidDereferenceOptions): IdkResult<DidDereferenceResult, IdkError> {
            val vm = dereferencedVerificationMethod ?: resolution.didDocument?.verificationMethod?.firstOrNull { it.id == didUrl }
            return Ok(vm?.let(DidDereferenceResult::verificationMethod) ?: DidDereferenceResult.notFound())
        }

        override fun hasResolver(method: String): Boolean = method == "example"
    }

    private fun request(
        did: String,
        role: WalletCounterpartyRole = WalletCounterpartyRole.ISSUER,
    ) =
        WalletIssuerAuthenticationRequest(
            counterparty = WalletCounterpartySummary(role = role, identifier = did),
            protocol = WalletProtocol.OID4VCI,
        )

    private class WalletIssuerAuthenticationResolverProbe(
        private val result: com.sphereon.wallet.interaction.WalletIssuerAuthenticationResult?,
    ) : com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolver {
        override suspend fun resolve(input: WalletIssuerAuthenticationRequest) = result
    }
}
