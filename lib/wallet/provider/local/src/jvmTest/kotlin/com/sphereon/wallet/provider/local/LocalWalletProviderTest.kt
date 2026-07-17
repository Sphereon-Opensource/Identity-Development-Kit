/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider.local

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.oauth2.common.command.DpopProofAssembly
import com.sphereon.data.store.party.model.Party
import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyType
import com.sphereon.wallet.party.WalletBusinessUnit
import com.sphereon.wallet.party.WalletBusinessUnitProvisioningRequest
import com.sphereon.wallet.party.WalletCounterpartyEvidence
import com.sphereon.wallet.party.WalletKnownOrganization
import com.sphereon.wallet.party.WalletPartyDirectory
import com.sphereon.wallet.party.WalletPartyDirectoryAuthority
import com.sphereon.wallet.party.WalletPartyEncounter
import com.sphereon.wallet.party.WalletPartyInteractionRecord
import com.sphereon.wallet.party.WalletPartyScope
import com.sphereon.wallet.provider.RevocationReason
import com.sphereon.wallet.provider.UnitProvisioningRequest
import com.sphereon.wallet.provider.WalletUnitStatus
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletSolutionRef
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.WalletInstanceAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.WalletUnitAttestationProfile
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.impl.LocalWsca
import com.sphereon.wallet.wsca.impl.WalletUserAuthenticator
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import com.sphereon.wallet.wscd.WscdProfile
import com.sphereon.wallet.wscd.software.KmsProviderBootstrap
import com.sphereon.wallet.wscd.software.SoftwareWscd
import com.sphereon.wallet.wscd.testfixtures.createWalletAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid as KotlinUuid

/**
 * Hermetic, real-crypto (software-KMS backed) tests for [LocalWalletProvider], mirroring
 * `LocalWscaTest`'s graph-construction pattern (`lib-wallet-wsca-impl` jvmTest). Covers the exit
 * bar: provision -> issue WIA -> issue KA -> validate BOTH artifacts locally (decode + verify
 * signature against [LocalWalletProvider.trustAnchor], honest Software claims asserted) plus the
 * unitStatus/revoke round-trip and the fail-closed WIA paths.
 */
class LocalWalletProviderTest {
    @Test
    fun provisionUnitMintsPrefixedIdsAndPersistsAnActiveRecord() =
        runTest {
            val provider = newProvider().provider

            val result = provider.provisionUnit(UnitProvisioningRequest(profileId = "alice", wscdProfile = WscdProfile.Software))

            assertTrue(result.isOk, "provisionUnit failed: ${if (result.isErr) result.error else ""}")
            assertEquals("wu-alice", result.value.walletUnitId)
            assertEquals("wi-alice", result.value.walletInstanceId)
            assertEquals(WscdProfile.Software, result.value.wscdProfile)
            assertEquals(WalletUnitStatus.ACTIVE, result.value.status)

            val status = provider.unitStatus("wu-alice")
            assertTrue(status.isOk)
            assertEquals(WalletUnitStatus.ACTIVE, status.value)
        }

    @Test
    fun provisionUnitIsIdempotentPerProfileId() =
        runTest {
            val provider = newProvider().provider
            val request = UnitProvisioningRequest(profileId = "bob", wscdProfile = WscdProfile.Software)

            val first = provider.provisionUnit(request)
            val second = provider.provisionUnit(request)

            assertTrue(first.isOk && second.isOk)
            assertEquals(first.value.walletUnitId, second.value.walletUnitId)
            assertEquals(first.value.walletInstanceId, second.value.walletInstanceId)
        }

    @Test
    fun issueInstanceAttestationSelfSignsAnHonestTs03WiaVerifiableAgainstTrustAnchor() =
        runTest {
            val setup = newProvider()
            val provider = setup.provider
            provider.provisionUnit(UnitProvisioningRequest(profileId = "carol", wscdProfile = WscdProfile.Software))
                .let { assertTrue(it.isOk, "provisionUnit failed") }

            val now = Clock.System.now()
            val result =
                provider.issueInstanceAttestation(
                    WalletInstanceAttestationIssueRequest(
                        walletUnitId = "wu-carol",
                        walletAccountId = "wa-carol",
                        operationBinding = "test:wia:carol",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        walletSolution = WalletSolutionRef(name = "Test Wallet Solution", version = "1.0.0"),
                        audience = "https://issuer.example.com",
                        expiresAt = now + 5.minutes,
                    ),
                )

            assertTrue(result.isOk, "issueInstanceAttestation failed: ${if (result.isErr) result.error else ""}")
            assertNull(result.value.artifact.statusSubject, "D5: no status list - statusSubject stays null absent a caller-supplied one")
            assertNull(result.value.statusRef, "D5: statusRef derives from statusSubject and must stay null")
            assertTrue(result.value.artifact.evidence.ts03Conformant)

            val decoded = decodeCompactJwt(result.value.artifact.material.value)
            assertEquals("ES256", decoded.header["alg"]?.jsonPrimitive?.content)
            assertEquals(setup.providerConfig.providerId, decoded.payload["iss"]?.jsonPrimitive?.content)
            assertEquals("wi-carol", decoded.payload["sub"]?.jsonPrimitive?.content, "WIA sub must be the persisted authoritative walletInstanceId")
            assertEquals("https://issuer.example.com", decoded.payload["aud"]?.jsonPrimitive?.content)
            assertEquals("Test Wallet Solution", decoded.payload["wallet_name"]?.jsonPrimitive?.content)
            assertEquals(
                "local-self-attested",
                decoded.payload["wallet_solution_certification_information"]?.jsonObject?.get("profile")?.jsonPrimitive?.content,
                "no certification supplied - must fall back to the honest self-attested sentinel, never an invented certification",
            )

            assertTrue(verifySignature(setup, decoded), "WIA signature must verify against trustAnchor()'s key")
        }

    @Test
    fun issueInstanceAttestationFailsClosedForAnUnknownWalletUnit() =
        runTest {
            val provider = newProvider().provider

            val result =
                provider.issueInstanceAttestation(
                    WalletInstanceAttestationIssueRequest(
                        walletUnitId = "wu-never-provisioned",
                        walletAccountId = "wa-never-provisioned",
                        operationBinding = "test:wia:never-provisioned",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        walletSolution = WalletSolutionRef(name = "Test Wallet Solution", version = "1.0.0"),
                        audience = "https://issuer.example.com",
                        expiresAt = Clock.System.now() + 5.minutes,
                    ),
                )

            assertTrue(result.isErr, "issueInstanceAttestation must fail closed for an unprovisioned wallet unit")
            assertEquals("NOT_FOUND_ERROR", result.error.code)
        }

    @Test
    fun issueInstanceAttestationFailsClosedWhenExpectedWalletInstanceIdMismatches() =
        runTest {
            val provider = newProvider().provider
            provider.provisionUnit(UnitProvisioningRequest(profileId = "dave", wscdProfile = WscdProfile.Software))
                .let { assertTrue(it.isOk, "provisionUnit failed") }

            val result =
                provider.issueInstanceAttestation(
                    WalletInstanceAttestationIssueRequest(
                        walletUnitId = "wu-dave",
                        walletAccountId = "wa-dave",
                        operationBinding = "test:wia:dave",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        expectedWalletInstanceId = "wi-not-dave",
                        walletSolution = WalletSolutionRef(name = "Test Wallet Solution", version = "1.0.0"),
                        audience = "https://issuer.example.com",
                        expiresAt = Clock.System.now() + 5.minutes,
                    ),
                )

            assertTrue(result.isErr, "issueInstanceAttestation must fail closed on an expectedWalletInstanceId mismatch")
            assertEquals("WALLET_UNIT_INSTANCE_MISMATCH", result.error.code)
        }

    @Test
    fun issueInstanceAttestationRejectsATechnicalTtlOfTwentyFourHoursOrMore() =
        runTest {
            val provider = newProvider().provider
            provider.provisionUnit(UnitProvisioningRequest(profileId = "erin", wscdProfile = WscdProfile.Software))
                .let { assertTrue(it.isOk, "provisionUnit failed") }

            val result =
                provider.issueInstanceAttestation(
                    WalletInstanceAttestationIssueRequest(
                        walletUnitId = "wu-erin",
                        walletAccountId = "wa-erin",
                        operationBinding = "test:wia:erin",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        walletSolution = WalletSolutionRef(name = "Test Wallet Solution", version = "1.0.0"),
                        audience = "https://issuer.example.com",
                        // Comfortably over the 24h boundary (not exactly 24h) so this assertion does
                        // not depend on a race between this line's Clock.System.now() and the one
                        // issueInstanceAttestation calls internally a moment later.
                        expiresAt = Clock.System.now() + 25.hours,
                    ),
                )

            assertTrue(result.isErr, "a 24h+ technical TTL must be rejected - ts03Conformant = true must stay honest")
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        }

    @Test
    fun issueKeyAttestationDelegatesToWscaAttestKeysWithHonestSoftwareClaimsAndSharesTheWiaKey() =
        runTest {
            val setup = newProvider()
            val provider = setup.provider
            provider.provisionUnit(UnitProvisioningRequest(profileId = "frank", wscdProfile = WscdProfile.Software))
                .let { assertTrue(it.isOk, "provisionUnit failed") }

            val holderKey =
                setup.wsca
                    .createCredentialKey("wu-frank", SecureComponentUsage.WALLET_CREDENTIAL_PROOF, SignatureAlgorithm.ECDSA_SHA256)
                    .let {
                        assertTrue(it.isOk, "createCredentialKey failed")
                        it.value
                    }

            val wiaResult =
                provider.issueInstanceAttestation(
                    WalletInstanceAttestationIssueRequest(
                        walletUnitId = "wu-frank",
                        walletAccountId = "wa-frank",
                        operationBinding = "test:wia:frank",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        walletSolution = WalletSolutionRef(name = "Test Wallet Solution", version = "1.0.0"),
                        audience = "https://issuer.example.com",
                        expiresAt = Clock.System.now() + 5.minutes,
                    ),
                )
            assertTrue(wiaResult.isOk, "issueInstanceAttestation failed")
            val decodedWia = decodeCompactJwt(wiaResult.value.artifact.material.value)

            val kaResult =
                provider.issueKeyAttestation(
                    KeyAttestationIssueRequest(
                        walletUnitId = "wu-frank",
                        walletAccountId = "wa-frank",
                        operationBinding = "test:ka:frank",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        attestedKeys = listOf(holderKey),
                        audience = "https://issuer.example.com",
                        nonce = "nonce-frank",
                    ),
                )
            assertTrue(kaResult.isOk, "issueKeyAttestation failed: ${if (kaResult.isErr) kaResult.error else ""}")
            val decodedKa = decodeCompactJwt(kaResult.value.artifact.material.value)

            // Honest Software claims: the lowest ceiling of every WscdProfile, exactly as
            // LocalWscaTest asserts for wsca.attestKeys directly.
            val keyStorage = decodedKa.payload["key_storage"]!!.jsonObject
            val userAuthentication = decodedKa.payload["user_authentication"]!!.jsonObject
            assertEquals("none", keyStorage["security_level"]?.jsonPrimitive?.content)
            assertEquals("local_wscd", keyStorage["secure_component"]?.jsonPrimitive?.content)
            assertFalse(keyStorage["non_exportable"]!!.jsonPrimitive.content.toBoolean())
            assertEquals("low", userAuthentication["assurance_level"]?.jsonPrimitive?.content)

            // WIA and KA are both signed by the SAME provider key (same explicit alias regardless of
            // the walletUnitId scope each path passes to Wsca.ensureKey - see the KDoc on
            // LocalWalletProvider.issueKeyAttestation). verifySignature proves this for each
            // artifact independently: it checks the artifact's signature against the key resolved
            // by the documented alias AND that that key's public JWK matches trustAnchor() - so both
            // succeeding proves both are signed by the identical trustAnchor()-pinned key, without
            // depending on whether the two encoders' JOSE headers happen to spell "kid" the same way
            // (Ts03WalletAttestationEncoder uses the key's `keyId`; LocalWsca.attestKeys uses its
            // `keyRef` - both resolve to the same underlying Wscd-cached key, but are not always the
            // identical string).
            assertTrue(verifySignature(setup, decodedKa), "KA signature must verify against trustAnchor()'s key")
            assertTrue(verifySignature(setup, decodedWia), "WIA signature must verify against trustAnchor()'s key")
        }

    @Test
    fun issueKeyAttestationFailsClosedForAnUnknownWalletUnit() =
        runTest {
            val setup = newProvider()
            val holderKey =
                setup.wsca
                    .createCredentialKey("wu-never-provisioned", SecureComponentUsage.WALLET_CREDENTIAL_PROOF, SignatureAlgorithm.ECDSA_SHA256)
                    .let {
                        assertTrue(it.isOk, "createCredentialKey failed")
                        it.value
                    }

            val result =
                setup.provider.issueKeyAttestation(
                    KeyAttestationIssueRequest(
                        walletUnitId = "wu-never-provisioned",
                        walletAccountId = "wa-never-provisioned",
                        operationBinding = "test:ka:never-provisioned",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        attestedKeys = listOf(holderKey),
                        audience = "https://issuer.example.com",
                        nonce = "nonce-unknown",
                    ),
                )

            assertTrue(result.isErr, "issueKeyAttestation must fail closed for an unprovisioned wallet unit")
            assertEquals("NOT_FOUND_ERROR", result.error.code)
        }

    @Test
    fun unitStatusAndRevokeUnitRoundTrip() =
        runTest {
            val provider = newProvider().provider
            provider.provisionUnit(UnitProvisioningRequest(profileId = "grace", wscdProfile = WscdProfile.Software))
                .let { assertTrue(it.isOk, "provisionUnit failed") }
            assertEquals(WalletUnitStatus.ACTIVE, provider.unitStatus("wu-grace").let { assertTrue(it.isOk); it.value })

            val revoke = provider.revokeUnit("wu-grace", RevocationReason.USER_REQUEST)
            assertTrue(revoke.isOk, "revokeUnit failed: ${if (revoke.isErr) revoke.error else ""}")
            assertEquals(WalletUnitStatus.REVOKED, provider.unitStatus("wu-grace").let { assertTrue(it.isOk); it.value })

            // Idempotent: revoking an already-revoked unit is a no-op success.
            val revokeAgain = provider.revokeUnit("wu-grace", RevocationReason.COMPROMISE)
            assertTrue(revokeAgain.isOk)

            val wiaAfterRevoke =
                provider.issueInstanceAttestation(
                    WalletInstanceAttestationIssueRequest(
                        walletUnitId = "wu-grace",
                        walletAccountId = "wa-grace",
                        operationBinding = "test:wia:grace",
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        walletSolution = WalletSolutionRef(name = "Test Wallet Solution", version = "1.0.0"),
                        audience = "https://issuer.example.com",
                        expiresAt = Clock.System.now() + 5.minutes,
                    ),
                )
            assertTrue(wiaAfterRevoke.isErr, "a revoked unit must not be able to obtain a new WIA")
            assertEquals("WALLET_PROVIDER_UNIT_NOT_ACTIVE", wiaAfterRevoke.error.code)

            // Revocation is terminal: re-provisioning the same profileId must fail closed, never
            // resurrect the unit to ACTIVE or wipe the recorded revocation reason.
            val reprovision = provider.provisionUnit(UnitProvisioningRequest(profileId = "grace", wscdProfile = WscdProfile.Software))
            assertTrue(reprovision.isErr, "provisioning a revoked unit id must fail closed")
            assertEquals("WALLET_PROVIDER_UNIT_REVOKED", reprovision.error.code)
            assertEquals(WalletUnitStatus.REVOKED, provider.unitStatus("wu-grace").let { assertTrue(it.isOk); it.value })
        }

    @Test
    fun unitStatusFailsClosedForAnUnknownWalletUnit() =
        runTest {
            val provider = newProvider().provider
            val result = provider.unitStatus("wu-never-provisioned")
            assertTrue(result.isErr)
            assertEquals("NOT_FOUND_ERROR", result.error.code)
        }

    @Test
    fun trustAnchorIsStableAcrossCalls() =
        runTest {
            val provider = newProvider().provider
            val first = provider.trustAnchor()
            val second = provider.trustAnchor()

            assertTrue(first.isOk && second.isOk)
            // Compares serialized JSON rather than Jwk instances directly (see verifySignature's
            // KDoc note on why): this does not depend on Jwk having value-type equals semantics.
            assertEquals(Json.encodeToString(Jwk.serializer(), first.value), Json.encodeToString(Jwk.serializer(), second.value))
            assertNotNull(first.value.x)
        }

    // ---------------------------------------------------------------------------------------
    // Test harness: mirrors LocalWscaTest's newLocalWsca() graph-construction pattern
    // (lib-wallet-wsca-impl jvmTest) so this module's production code stays KMS-free while its
    // tests still exercise real (software-KMS backed) crypto end to end.
    // ---------------------------------------------------------------------------------------

    private suspend fun newProvider(): ProviderSetup {
        val sessionId = "wallet-provider-${Uuid.v4String()}"
        val app =
            createWalletAppGraph(
                application = "LocalWalletProviderTest",
                appId = "com.sphereon.wallet.provider-local-test",
                profile = "test",
                version = "0.1.0",
            )
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId(sessionId)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        val kmsProviderConfig =
            SoftwareKmsProviderConfig(
                id = "$sessionId-software-kms",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val kmsProvider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(kmsProviderConfig, session.asCoreApiServiceGraph().serviceExecution)
        kms.registerProvider(kmsProvider, makeDefaultKms = true)
        val softwareWscd = SoftwareWscd(kms, providerBootstrap = KmsProviderBootstrap {})
        val wsca =
            LocalWsca(
                softwareWscd,
                DpopProofAssembly(defaultSecureRandom()),
                WalletUserAuthenticator { request ->
                    Ok(
                        ActivationProof(
                            kind = ActivationProofKind.LOCAL_USER_AUTH,
                            token = "test-local-user-auth",
                            digestBinding = request.digestBinding,
                            nonce = request.nonce,
                            evidence = mapOf("factor" to "pin"),
                        ),
                    )
                },
            )
        val providerConfig =
            object : LocalWalletProviderConfig {
                override val providerId: String = "test-wallet-provider-$sessionId"
                override val walletName: String = LocalWalletProviderConfig.DEFAULT_WALLET_NAME
                override val walletVersion: String = LocalWalletProviderConfig.DEFAULT_WALLET_VERSION
                override val providerKeyAlgorithm: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
            }
        val provider =
            LocalWalletProvider(
                wsca = wsca,
                config = providerConfig,
                unitStore = InMemoryWalletUnitRecordStore(),
                partyDirectory = ProvisioningPartyDirectory(),
                execution = session.sessionExecution,
            )
        return ProviderSetup(provider = provider, wsca = wsca, kms = kms, providerConfig = providerConfig)
    }

    /**
     * Verifies a decoded JWT's raw signature against the exact key [LocalWalletProvider] signs
     * with, resolved the SAME way `providerSignerKey`/`trustAnchor` do (idempotent
     * [Wsca.ensureKey] by explicit alias - re-deriving it here returns the identical key, never a
     * new one). This is the mechanical verification tool `LocalWscaTest` itself uses
     * (`KeyManagerService.verifyRawSignatureResult` keyed by the KMS alias/providerId - a real
     * external verifier would not have those, but this proves the same fact: the signature is
     * cryptographically valid over this exact JWS signing input under this exact key). The report
     * documents this test-design choice.
     */
    private suspend fun verifySignature(
        setup: ProviderSetup,
        decoded: DecodedJwt,
    ): Boolean {
        val alg = decoded.header["alg"]?.jsonPrimitive?.content
        val algorithm = if (alg == "ES256") SignatureAlgorithm.ECDSA_SHA256 else error("unexpected test alg '$alg'")
        val keyAlias = "wallet-units/${setup.providerConfig.providerId}/provider/${alg!!.lowercase()}"
        val providerKeyRef =
            setup.wsca
                .ensureKey(
                    walletUnitId = setup.providerConfig.providerId,
                    usage = SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = algorithm,
                    keyAlias = keyAlias,
                ).let {
                    assertTrue(it.isOk, "ensureKey failed while re-deriving the provider key for verification")
                    it.value
                }
        // Not asserted against providerKeyRef.keyId/keyRef specifically: the two encoders this
        // provider uses (Ts03WalletAttestationEncoder for WIA, LocalWsca.attestKeys for KA) spell
        // "kid" from different WalletAttestedKeyRef fields (keyId vs keyRef) - both resolve to the
        // same Wscd-cached key, but are not always the identical string. Presence is enough here;
        // the KMS verify below is what actually proves the signature is over this exact key.
        assertNotNull(decoded.header["kid"]?.jsonPrimitive?.content, "JWT header must carry a kid")

        val trustAnchor = setup.provider.trustAnchor()
        assertTrue(trustAnchor.isOk, "trustAnchor failed")
        // Compares serialized JSON rather than Jwk instances directly via assertEquals: this does
        // not depend on Jwk having value-type equals semantics, only that it carries the same key
        // material as what was actually used to sign.
        assertEquals(
            providerKeyRef.publicKeyJwk!!.let { Json.decodeFromString(Jwk.serializer(), it) }.let { Json.encodeToString(Jwk.serializer(), it) },
            Json.encodeToString(Jwk.serializer(), trustAnchor.value),
            "trustAnchor() must return the exact public key that signed this artifact",
        )

        val verify =
            setup.kms.verifyRawSignatureResult(
                keyInfo = KeyInfo<Nothing>(alias = providerKeyRef.keyRef, providerId = providerKeyRef.keystore?.providerId),
                input = decoded.signingInput,
                signature = decoded.signature,
            )
        assertTrue(verify.isOk, "verifyRawSignatureResult failed: ${if (verify.isErr) verify.error else ""}")
        return verify.value.isValid
    }

    // Plain classes (not data classes): never compared via == or copy()'d, and their ByteArray
    // properties would otherwise get the default reference-equality equals()/hashCode() a data
    // class generates for Array-typed properties, which is misleading dead code here.
    private class ProviderSetup(
        val provider: LocalWalletProvider,
        val wsca: Wsca,
        val kms: KeyManagerService,
        val providerConfig: LocalWalletProviderConfig,
    )

    private class DecodedJwt(
        val header: JsonObject,
        val payload: JsonObject,
        val signature: ByteArray,
        val signingInput: ByteArray,
    )

    @OptIn(ExperimentalUuidApi::class)
    private class ProvisioningPartyDirectory : WalletPartyDirectory {
        override val authority: WalletPartyDirectoryAuthority = WalletPartyDirectoryAuthority.LOCAL

        private val units = mutableMapOf<String, WalletBusinessUnit>()

        override suspend fun provisionBusinessUnit(
            request: WalletBusinessUnitProvisioningRequest,
        ): IdkResult<WalletBusinessUnit, IdkError> =
            Ok(
                units.getOrPut("${request.tenantId}|${request.walletUnitId}") {
                    WalletBusinessUnit(
                        Party(
                            partyId = request.assignedOrganizationUnitRef?.partyId?.let(KotlinUuid::parse) ?: KotlinUuid.random(),
                            tenantId = request.tenantId,
                            partyType = PartyType.ORGANIZATION_UNIT,
                            origin = PartyOrigin.MANAGED,
                            displayName = request.displayName,
                            organizationUnitId = null,
                            createdAt = Clock.System.now(),
                            updatedAt = Clock.System.now(),
                        ),
                    )
                },
            )

        override fun observeBusinessUnit(scope: WalletPartyScope): StateFlow<WalletBusinessUnit?> =
            MutableStateFlow(units["${scope.tenantId}|${scope.walletUnitId}"])

        override fun observeOrganizations(scope: WalletPartyScope): StateFlow<List<WalletKnownOrganization>> = MutableStateFlow(emptyList())

        override suspend fun resolveOrCreateOrganization(
            scope: WalletPartyScope,
            evidence: WalletCounterpartyEvidence,
            encounteredAtEpochSeconds: Long,
        ): IdkResult<WalletPartyEncounter, IdkError> = error("not exercised by LocalWalletProviderTest")

        override suspend fun getOrganization(
            scope: WalletPartyScope,
            partyId: KotlinUuid,
        ): IdkResult<WalletKnownOrganization?, IdkError> = error("not exercised by LocalWalletProviderTest")

        override suspend fun recordInteraction(
            scope: WalletPartyScope,
            partyId: KotlinUuid,
            record: WalletPartyInteractionRecord,
        ): IdkResult<WalletKnownOrganization, IdkError> = error("not exercised by LocalWalletProviderTest")

        override suspend fun associateOrganization(
            scope: WalletPartyScope,
            sourcePartyId: KotlinUuid,
            targetPartyId: KotlinUuid,
        ): IdkResult<WalletKnownOrganization, IdkError> = error("not exercised by LocalWalletProviderTest")

        override suspend fun renameOrganization(
            scope: WalletPartyScope,
            partyId: KotlinUuid,
            displayName: String,
        ): IdkResult<WalletKnownOrganization, IdkError> = error("not exercised by LocalWalletProviderTest")
    }

    /**
     * Decodes a compact JWS AND retains the exact original base64url header/payload segments
     * (concatenated as [DecodedJwt.signingInput]) rather than re-serializing the parsed
     * [JsonObject]s - re-serialization is not guaranteed to byte-for-byte match what was actually
     * signed (key order, whitespace), so [verifySignature] would silently verify against the wrong
     * input if this reused a re-encoded header/payload instead.
     */
    private fun decodeCompactJwt(compactJwt: String): DecodedJwt {
        val parts = compactJwt.split('.')
        assertEquals(3, parts.size, "must be a compact JWS")
        val header = Json.parseToJsonElement(parts[0].decodeFromBase64Url().decodeToString()).jsonObject
        val payload = Json.parseToJsonElement(parts[1].decodeFromBase64Url().decodeToString()).jsonObject
        val signature = parts[2].decodeFromBase64Url()
        val signingInput = "${parts[0]}.${parts[1]}".encodeToByteArray()
        return DecodedJwt(header = header, payload = payload, signature = signature, signingInput = signingInput)
    }
}
