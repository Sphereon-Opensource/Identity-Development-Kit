/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationRequest
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolutionPlan
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationPolicy
import com.sphereon.wallet.interaction.WalletProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ExternalIdentifierWalletIssuerAuthenticationResolverTest {
    private companion object {
        const val DEFAULT_ISSUER = "https://issuer.example"
        val DEFAULT_KEY = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WKn-ZIGevcwGFOMJ0GeEei2HiO3I-oMECoZQZ0KdLJ0",
            y = "Xch1SqgK8B0K3w6V3a9RbU4HlB1pdP_EEcyLpRZ4Mds",
            kid = "issuer-key",
        )
    }

    private val issuer = DEFAULT_ISSUER
    private val key = DEFAULT_KEY

    @Test
    fun explicitlyPinnedStaticJwkIsIssuerAuthenticationForVcdmProfiles() = runTest {
        val service = RecordingExternalIdentifierService()
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(WalletIssuerAuthenticationResolutionPlan.PinnedJwk(issuer, key)),
                ),
            ),
            service,
        )

        val result = assertNotNull(resolver.resolve(request()))
        assertEquals(issuer, result.issuer)
        assertEquals("issuer-key", result.trustedJwks["keys"]!!.jsonArray.single().jsonObject["kid"]!!.jsonPrimitive.content)
        assertEquals(IdentifierMethodDefaults.JWK, service.lastMethod)
        assertEquals("pinned-jwk", result.provenance.single().source)
    }

    @Test
    fun configuredHttpsJwksIsResolvedButHttpAndUnconfiguredAreRejected() = runTest {
        val service = RecordingExternalIdentifierService()
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(WalletIssuerAuthenticationResolutionPlan.HttpsJwks(issuer, "https://issuer.example/jwks")),
                ),
            ),
            service,
        )
        assertNotNull(resolver.resolve(request()))

        assertEquals(IdentifierMethodDefaults.JWKS_URL, service.lastMethod)

        val httpResolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(WalletIssuerAuthenticationResolutionPlan.HttpsJwks(issuer, "http://issuer.example/jwks")),
                ),
            ),
            service,
        )
        assertNull(httpResolver.resolve(request()))
        assertNull(resolver.resolve(request("https://other.example")))

        val invalidService = RecordingExternalIdentifierService().also { it.returnPrivateKey = true }
        val invalidRemoteResolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(WalletIssuerAuthenticationResolutionPlan.HttpsJwks(issuer, "https://issuer.example/jwks")),
                ),
            ),
            invalidService,
        )
        assertNull(invalidRemoteResolver.resolve(request()))
    }

    @Test
    fun configuredHttpsJwksWithoutRequestedKidRequiresSelectedKeyToBeReturned() = runTest {
        val service = RecordingExternalIdentifierService().also { it.returnDifferentSelectedKey = true }
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer,
                    WalletProtocol.OID4VCI,
                    listOf(WalletIssuerAuthenticationResolutionPlan.HttpsJwks(issuer, "https://issuer.example/jwks")),
                ),
            ),
            service,
        )

        assertNull(resolver.resolve(request()))
    }

    @Test
    fun staticJwksRejectsPrivateSymmetricAndNonSigningKeysAsOneSet() = runTest {
        val invalid = listOf(
            key.copy(d = "private"),
            Jwk(kty = JwaKeyType.oct, k = "c2VjcmV0", kid = "symmetric"),
            key.copy(kid = "encryption", use = "enc", key_ops = arrayOf(JoseKeyOperations.ENCRYPT)),
            Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.X25519, x = "agreement", kid = "agreement"),
        )
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(WalletIssuerAuthenticationResolutionPlan.PinnedJwks(issuer, invalid)),
                ),
            ),
            RecordingExternalIdentifierService(),
        )
        assertNull(resolver.resolve(request()))
    }

    @Test
    fun staticPlanRejectsDifferentOrExtraExternalResultMaterial() = runTest {
        val service = RecordingExternalIdentifierService().also { it.returnDifferentJwk = true }
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer,
                    WalletProtocol.OID4VCI,
                    listOf(WalletIssuerAuthenticationResolutionPlan.PinnedJwk(issuer, key)),
                ),
            ),
            service,
        )
        assertNull(resolver.resolve(request()))

        service.returnDifferentJwk = false
        service.returnExtraJwk = true
        assertNull(resolver.resolve(request()))
    }

    @Test
    fun duplicateKidsAcrossConfiguredPlansFailClosed() = runTest {
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer,
                    WalletProtocol.OID4VCI,
                    listOf(
                        WalletIssuerAuthenticationResolutionPlan.PinnedJwk(issuer, key),
                        WalletIssuerAuthenticationResolutionPlan.PinnedJwk(issuer, key),
                    ),
                ),
            ),
            RecordingExternalIdentifierService(),
        )
        assertNull(resolver.resolve(request()))
    }

    @Test
    fun dispatcherUsesAllConfiguredExternalIdentifierServices() = runTest {
        val dispatcher = WalletExternalIdentifierServiceDispatcher(
            setOf(
                RecordingExternalIdentifierService(listOf(IdentifierMethodDefaults.JWK)),
                RecordingExternalIdentifierService(listOf(IdentifierMethodDefaults.JWKS_URL)),
                RecordingExternalIdentifierService(listOf(IdentifierMethodDefaults.X5C)),
            ),
        )
        assertEquals(3, dispatcher.supportedIdentifierMethods.size)
        assertNotNull(dispatcher.resolve(ExternalIdentifierJwkOpts(key)).getOrNull())
    }

    @Test
    fun tokenSuppliedMaterialIsNotUsedWithoutConfiguredPolicy() = runTest {
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(null),
            RecordingExternalIdentifierService(),
        )
        assertNull(resolver.resolve(request()))
    }

    @Test
    fun x5cRequiresVerifiedAnchoredChainAndExactIssuerBinding() = runTest {
        val service = RecordingExternalIdentifierService()
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(
                        WalletIssuerAuthenticationResolutionPlan.X5c(
                            issuer = issuer,
                            chain = listOf("cert-leaf"),
                            verify = true,
                            trustAnchors = listOf("anchor"),
                            issuerBinding = issuer,
                            keyId = "issuer-key",
                        ),
                    ),
                ),
            ),
            service,
        )
        assertNotNull(resolver.resolve(request()))

        val parseOnly = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer = issuer,
                    protocol = WalletProtocol.OID4VCI,
                    plans = listOf(WalletIssuerAuthenticationResolutionPlan.X5c(issuer, listOf("cert"), false, listOf("anchor"), issuer)),
                ),
            ),
            service,
        )
        assertNull(parseOnly.resolve(request()))
        service.x5cValid = false
        assertNull(resolver.resolve(request()))
    }

    @Test
    fun x5cRequiresIssuerKeyToMatchValidatedPublicKey() = runTest {
        val service = RecordingExternalIdentifierService().also { it.returnDifferentX5cKey = true }
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer,
                    WalletProtocol.OID4VCI,
                    listOf(
                        WalletIssuerAuthenticationResolutionPlan.X5c(
                            issuer = issuer,
                            chain = listOf("cert-leaf"),
                            verify = true,
                            trustAnchors = listOf("anchor"),
                            issuerBinding = issuer,
                            keyId = "issuer-key",
                        ),
                    ),
                ),
            ),
            service,
        )

        assertNull(resolver.resolve(request()))
    }

    @Test
    fun x5cWithoutConfiguredKeyIdAcceptsTheReturnedKeyId() = runTest {
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer,
                    WalletProtocol.OID4VCI,
                    listOf(
                        WalletIssuerAuthenticationResolutionPlan.X5c(
                            issuer = issuer,
                            chain = listOf("cert-leaf"),
                            verify = true,
                            trustAnchors = listOf("anchor"),
                            issuerBinding = issuer,
                        ),
                    ),
                ),
            ),
            RecordingExternalIdentifierService(),
        )

        assertNotNull(resolver.resolve(request()))
    }

    @Test
    fun configuredX5cKeyIdRequiresTheReturnedKeyId() = runTest {
        val service = RecordingExternalIdentifierService().also { it.returnMissingX5cKid = true }
        val resolver = ExternalIdentifierWalletIssuerAuthenticationResolver(
            StaticWalletIssuerAuthenticationPolicyProvider(
                WalletIssuerAuthenticationPolicy(
                    issuer,
                    WalletProtocol.OID4VCI,
                    listOf(
                        WalletIssuerAuthenticationResolutionPlan.X5c(
                            issuer = issuer,
                            chain = listOf("cert-leaf"),
                            verify = true,
                            trustAnchors = listOf("anchor"),
                            issuerBinding = issuer,
                            keyId = "issuer-key",
                        ),
                    ),
                ),
            ),
            service,
        )

        assertNull(resolver.resolve(request()))
    }

    private fun request(id: String = issuer) = WalletIssuerAuthenticationRequest(
        counterparty = WalletCounterpartySummary(WalletCounterpartyRole.ISSUER, id),
        protocol = WalletProtocol.OID4VCI,
    )

    private class StaticWalletIssuerAuthenticationPolicyProvider(
        private val policy: WalletIssuerAuthenticationPolicy?,
    ) : com.sphereon.wallet.interaction.WalletIssuerAuthenticationPolicyProvider {
        override suspend fun policyFor(issuer: String, protocol: WalletProtocol) = policy
    }

    private class RecordingExternalIdentifierService(
        methods: List<com.sphereon.crypto.resolution.IIdentifierMethod> = listOf(IdentifierMethodDefaults.JWK, IdentifierMethodDefaults.JWKS_URL, IdentifierMethodDefaults.X5C),
        private val serviceIssuer: String = DEFAULT_ISSUER,
        private val serviceKey: Jwk = DEFAULT_KEY,
    ) : ExternalIdentifierService {
        var lastMethod: com.sphereon.crypto.resolution.IIdentifierMethod? = null
        var returnPrivateKey: Boolean = false
        var returnDifferentJwk: Boolean = false
        var returnExtraJwk: Boolean = false
        var returnDifferentSelectedKey: Boolean = false
        var x5cValid: Boolean = true
        var returnDifferentX5cKey: Boolean = false
        var returnMissingX5cKid: Boolean = false
        override val supportedIdentifierMethods = methods
        override suspend fun isSupportedIdentifier(identifier: Any) = true
        override suspend fun isSupportedIdentifierMethod(identifierMethod: com.sphereon.crypto.resolution.IIdentifierMethod) = true
        override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult) = opts.method in supportedIdentifierMethods
        override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts, IdkErrorType> = Ok(opts as com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts)
        override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult, IdkErrorType> {
            lastMethod = opts.method
            val jwkOpts = opts as? ExternalIdentifierJwkOpts
            val baseKey: Jwk = Jwk.from(jwkOpts?.identifier ?: serviceKey)
            val resolvedKey: Jwk = baseKey.let {
                when {
                    returnPrivateKey -> it.copy(d = "private")
                    returnDifferentJwk -> it.copy(x = "different")
                    else -> it
                }
            }
            return when (opts) {
                is ExternalIdentifierJwksUrlOpts -> Ok(
                    ExternalIdentifierResult.JwksUrl(
                        identifierOpts = opts,
                        jwks = arrayOf(ResolvedKeyInfo.fromKey(resolvedKey)),
                        keyInfo = ResolvedKeyInfo.fromKey(
                            if (returnDifferentSelectedKey) resolvedKey.copy(kid = "not-returned", x = "different") else resolvedKey,
                        ),
                        jwksUrl = opts.identifier,
                    ),
                )
                is ExternalIdentifierX5cOpts -> {
                    val x5cKey = if (returnMissingX5cKid) resolvedKey.copy(kid = null) else resolvedKey
                    val x5cKeyInfo = if (returnMissingX5cKid) {
                        ResolvedKeyInfo(kid = null, key = x5cKey)
                    } else {
                        ResolvedKeyInfo.fromKey(x5cKey)
                    }
                    val certificate = Certificate(
                        der = byteArrayOf(1),
                        fingerPrint = "fingerprint",
                        issuerDN = serviceIssuer,
                        subjectDN = serviceIssuer,
                        notBefore = kotlin.time.Instant.parse("2020-01-01T00:00:00Z"),
                        notAfter = kotlin.time.Instant.parse("2030-01-01T00:00:00Z"),
                    )
                    Ok(
                        ExternalIdentifierResult.X5c(
                            identifierOpts = opts,
                            jwks = arrayOf(x5cKeyInfo),
                            keyInfo = x5cKeyInfo,
                            x5c = opts.identifier,
                            verificationResult = X509VerificationResult(
                                certificateChain = arrayOf(certificate),
                                publicKey = if (returnDifferentX5cKey) serviceKey.copy(x = "different") else serviceKey,
                                critical = !x5cValid,
                                message = "verified",
                                error = !x5cValid,
                            ),
                            certificates = listOf(certificate),
                        ),
                    )
                }
                else -> {
                    val keyInfo = ResolvedKeyInfo.fromKey(resolvedKey)
                    val jwks: Array<ResolvedKeyInfoType<JwkType>> =
                        if (returnExtraJwk) {
                            arrayOf(keyInfo, ResolvedKeyInfo.fromKey(serviceKey.copy(kid = "extra-key")))
                        } else {
                            arrayOf(keyInfo)
                        }
                    Ok(ExternalIdentifierResult.Jwk(jwkOpts ?: ExternalIdentifierJwkOpts(serviceKey), jwks, keyInfo))
                }
            }
        }
    }
}
