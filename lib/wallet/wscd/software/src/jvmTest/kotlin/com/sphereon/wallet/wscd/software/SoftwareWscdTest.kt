/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.wallet.wscd.testfixtures.createWalletAppGraph
import com.sphereon.wallet.wscd.SoftwareWscdKeyStoreConfiguration
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import com.sphereon.wallet.wscd.WscdKeyHandle
import com.sphereon.wallet.wscd.WscdKeySpec
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Real-crypto (software-KMS backed) unit tests for [SoftwareWscd]: the custody-only half of
 * the WSCA/WSCD split. No stubs: keys are generated in, and signatures produced by, the software
 * KMS that sits behind the WSCA/WSCD boundary.
 *
 * Placed in jvmTest to reuse [createWalletAppGraph] (JVM-only), matching the WSCA policy layer's
 * own `LocalWscaTest` (`lib-wallet-wsca-impl`), which wires a [SoftwareWscd] built exactly like
 * this test's does.
 *
 * [newHarness] never registers the software KMS provider up front: every test below exercises
 * [SoftwareWscd]'s OWN lazy registration ([SoftwareKmsProviderRegistrar], triggered on the first
 * `generateKey`/`generateFreshKey` call), the same path product/runner bootstraps rely on since
 * they never touch KMS themselves.
 */
class SoftwareWscdTest {
    @Test
    fun sameKeyManagerResolvesImmediatelyIndexedDurableReference() =
        runTest {
            val harness = newHarness()
            harness.bootstrap.ensureRegistered()
            val providerId = harness.kms.getProviderIds().single()
            val alias = "same-key-manager-index-diagnostic"
            val generated =
                harness.kms.generateKeyResult(
                    providerId = providerId,
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                    walletUnitId = "wallet-same-key-manager-owner",
                )
            assertTrue(generated.isOk, "GenerateKeyCommand failed: $generated")
            val keyPair = generated.value.keyPair ?: error("GenerateKeyCommand returned no key pair")
            val rows = harness.keyReferenceStore.findAll(harness.tenantId, null).getOrThrow()
            val row = rows.singleOrNull { it.alias == alias }
            assertTrue(
                row != null,
                "Immediate shared-store row missing; storeTenant=${harness.tenantId}, " +
                    "keyManagerTenant=${harness.keyManagerTenantId}, alias=$alias, " +
                    "provider=$providerId, rows=" + rows.joinToString { it.toDiagnosticString() },
            )
            assertEquals(providerId, row!!.providerId)
            assertEquals("wallet-same-key-manager-owner", row.walletUnitId)

            val resolved = harness.kms.findRegisteredKeyReference(alias, keyPair.providerId)
            assertTrue(
                resolved != null,
                "Same KeyManagerService authority lookup missed indexed row; " +
                    "storeTenant=${harness.tenantId}, keyManagerTenant=${harness.keyManagerTenantId}, " +
                    "alias=$alias, keyPairProvider=${keyPair.providerId}, row=${row.toDiagnosticString()}, " +
                    "keyManagerClass=${harness.kms::class.simpleName}, " +
                    "keyStoreClass=${harness.kms.keyStore::class.simpleName}, " +
                    "resolved=$resolved",
            )
            assertEquals("wallet-same-key-manager-owner", resolved!!.walletUnitId)
        }

    @Test
    fun generateKeyCommandIndexesDurableOwnerInSharedReferenceStore() =
        runTest {
            val harness = newHarness()
            harness.bootstrap.ensureRegistered()
            val providerId = harness.kms.getProviderIds().single()
            val provider = harness.kms.getProviderById(providerId)
            assertFalse(
                provider.maintainsKeyReferenceIndex,
                "Software provider must use generic GenerateKeyCommand indexing",
            )

            val alias = "generate-command-index-diagnostic"
            val generated =
                harness.kms.generateKeyResult(
                    providerId = providerId,
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                    walletUnitId = "wallet-command-index-owner",
                )
            assertTrue(generated.isOk, "GenerateKeyCommand failed: $generated")

            val records = harness.keyReferenceStore.findAll(harness.tenantId, null).getOrThrow()
            val matching = records.filter { it.alias == alias }
            assertTrue(
                matching.isNotEmpty(),
                "GenerateKeyCommand index missing immediately after generateKeyResult; " +
                    "providerMaintainsKeyReferenceIndex=${provider.maintainsKeyReferenceIndex}, " +
                    "expected=(tenant=${harness.tenantId}, alias=$alias, provider=$providerId, " +
                    "owner=wallet-command-index-owner), records=" +
                    records.joinToString { record ->
                        "(tenant=${record.tenantId}, alias=${record.alias}, provider=${record.providerId}, owner=${record.walletUnitId})"
                    },
            )
            assertEquals("wallet-command-index-owner", matching.single().walletUnitId)
            assertEquals(providerId, matching.single().providerId)
        }

    @Test
    fun generateKeyIsIdempotentByAliasAndReturnsUsableHandle() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-a",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val first = wscd.generateKey(spec)
            val second = wscd.generateKey(spec)

            assertTrue(first.isOk, "generateKey failed: ${if (first.isErr) first.error else ""}")
            assertTrue(second.isOk)
            // Idempotent: same spec -> same key (same reference).
            assertEquals(first.value.keyRef, second.value.keyRef)
            assertEquals(WscdProfile.Software, first.value.profile)
            assertEquals("wallet-a", first.value.walletUnitId)

            // A different wallet unit gets a distinct key.
            val other = wscd.generateKey(spec.copy(walletUnitId = "wallet-b"))
            assertTrue(other.isOk)
            assertNotEquals(first.value.keyRef, other.value.keyRef)
        }

    @Test
    fun generateKeyHonorsCallerSuppliedAliasVerbatim() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-alias-verbatim",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    alias = "custom-alias",
                )

            val result = wscd.generateKey(spec)

            assertTrue(result.isOk, "generateKey failed: ${if (result.isErr) result.error else ""}")
            assertEquals("custom-alias", result.value.keyRef)
        }

    @Test
    fun aNewWscdInstanceRehydratesTheExistingKeyForTheSameAlias() =
        runTest {
            val harness = newHarness()
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-rehydration",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    alias = "wallet-rehydration-holder-key",
                )
            val first = harness.wscd.generateKey(spec)
            assertTrue(first.isOk, "initial key generation failed")

            val restartedWscd = SoftwareWscd(harness.kms, KmsProviderBootstrap {})
            val recovered = restartedWscd.generateKey(spec)

            assertTrue(recovered.isOk, "existing key recovery failed")
            assertEquals(first.value.keyRef, recovered.value.keyRef)
            assertEquals(first.value.publicKeyJwk, recovered.value.publicKeyJwk)

            val signingInput = "after-wscd-restart".encodeToByteArray()
            val signature =
                restartedWscd.signDigest(
                    recovered.value,
                    signingInput,
                    ActivationProof(ActivationProofKind.LOCAL_USER_AUTH, "local-user-auth-token"),
                )
            assertTrue(signature.isOk, "recovered key could not sign")
            val verified =
                harness.kms.verifyRawSignatureResult(
                    keyInfo = KeyInfo<Nothing>(alias = recovered.value.keyRef),
                    input = signingInput,
                    signature = signature.value,
                )
            assertTrue(verified.isOk && verified.value.isValid, "signature must verify with the original KMS key")
        }

    @Test
    fun restartedWscdCannotRelabelDurableKmsKeyToAnotherWalletUnit() =
        runTest {
            val harness = newHarness()
            val alias = "durable-owner-alias"
            val ownerSpec =
                WscdKeySpec(
                    walletUnitId = "wallet-unit-a",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    alias = alias,
                )
            val created = harness.wscd.generateKey(ownerSpec)
            assertTrue(created.isOk, "initial durable key generation failed")

            val restartedWscd = SoftwareWscd(harness.kms, KmsProviderBootstrap {})
            val foreignLookup = restartedWscd.generateKey(ownerSpec.copy(walletUnitId = "wallet-unit-b"))

            assertTrue(
                foreignLookup.isErr,
                "a foreign wallet unit must not relabel an existing durable KMS alias: $foreignLookup",
            )
            assertEquals("WALLET_WSCD_KEY_OWNER_MISMATCH", foreignLookup.error.code)
        }

    @Test
    fun rehydrationRejectsTamperedDurableOwnerMetadata() =
        runTest {
            val harness = newHarness()
            val alias = "tampered-owner-alias"
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-unit-a",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    alias = alias,
                )
            val created = harness.wscd.generateKey(spec)
            assertTrue(created.isOk, "initial durable key generation failed")
            val reference =
                harness.keyReferenceStore
                    .findByAlias(harness.tenantId, alias, created.value.providerId)
                    .getOrThrow()
            requireNotNull(reference)
            harness.keyReferenceStore.upsert(reference.copy(walletUnitId = "wallet-unit-tampered"))

            val restartedWscd = SoftwareWscd(harness.kms, KmsProviderBootstrap {})
            val result = restartedWscd.generateKey(spec)

            assertTrue(result.isErr)
            assertEquals("WALLET_WSCD_KEY_OWNER_MISMATCH", result.error.code)
        }

    @Test
    fun rehydrationRejectsDurableKeyWhenOwnerMetadataIsMissing() =
        runTest {
            val harness = newHarness()
            val alias = "missing-owner-alias"
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-unit-a",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    alias = alias,
                )
            val created = harness.wscd.generateKey(spec)
            assertTrue(created.isOk, "initial durable key generation failed")
            harness.keyReferenceStore.delete(
                harness.tenantId,
                alias,
                created.value.providerId ?: error("generated durable key has no provider id"),
            )

            val restartedWscd = SoftwareWscd(harness.kms, KmsProviderBootstrap {})
            val result = restartedWscd.generateKey(spec)

            assertTrue(result.isErr)
            assertEquals("WALLET_WSCD_KEY_OWNER_METADATA_MISSING", result.error.code)
        }

    @Test
    fun defaultAliasIsScopedByWalletUnitUsageAndAlgorithm() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-alias",
                    usage = SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = SignatureAlgorithm.ECDSA_SHA384,
                )

            val result = wscd.generateKey(spec)

            assertTrue(result.isOk, "generateKey failed: ${if (result.isErr) result.error else ""}")
            // The alias is a WSCD-owned namespace, scoped by wallet unit, usage, and algorithm.
            assertEquals("wallet-units/wallet-alias/wallet_attestation/es384", result.value.keyRef)
            // The handle must carry public key material for the WSCA layer (DPoP headers,
            // cnf.jwk, WalletAttestedKeyRef) without KMS access.
            assertTrue(result.value.publicKeyJwk?.contains("\"kty\"") == true, "handle must carry the public JWK")
            assertTrue(!result.value.keyId.isNullOrBlank(), "handle must carry a keyId")
        }

    @Test
    fun generateFreshKeyMintsADistinctKeyOnEveryCall() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-credential",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val first = wscd.generateFreshKey(spec)
            val second = wscd.generateFreshKey(spec)

            assertTrue(first.isOk, "generateFreshKey failed: ${if (first.isErr) first.error else ""}")
            assertTrue(second.isOk, "generateFreshKey failed: ${if (second.isErr) second.error else ""}")
            // Non-idempotent: every call mints a brand-new key, even for the same wallet unit.
            assertNotEquals(first.value.keyRef, second.value.keyRef, "each call must mint a distinct key reference")
        }

    @Test
    fun generateFreshKeyIgnoresACallerSuppliedAlias() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-credential-alias",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    alias = "ignored-alias",
                )

            val first = wscd.generateFreshKey(spec)
            val second = wscd.generateFreshKey(spec)

            assertTrue(first.isOk, "generateFreshKey failed")
            assertTrue(second.isOk, "generateFreshKey failed")
            assertNotEquals("ignored-alias", first.value.keyRef)
            assertNotEquals(first.value.keyRef, second.value.keyRef)
        }

    @Test
    fun signDigestProducesSignatureVerifiableAgainstTheProvisionedKey() =
        runTest {
            val harness = newHarness()
            val wscd = harness.wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-sign",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val handle =
                wscd.generateKey(spec).let {
                    assertTrue(it.isOk, "generateKey failed")
                    it.value
                }

            val signingInput = "wallet-wscd-signing-input".encodeToByteArray()
            val activation = ActivationProof(kind = ActivationProofKind.LOCAL_USER_AUTH, token = "local-user-auth-token")
            val signature = wscd.signDigest(handle, signingInput, activation)

            assertTrue(signature.isOk, "signDigest failed: ${if (signature.isErr) signature.error else ""}")

            val verify =
                harness.kms.verifyRawSignatureResult(
                    keyInfo = KeyInfo<Nothing>(alias = handle.keyRef),
                    input = signingInput,
                    signature = signature.value,
                )
            assertTrue(verify.isOk, "verify failed: ${if (verify.isErr) verify.error else ""}")
            assertTrue(verify.value.isValid, "signature must verify against the provisioned key")
        }

    @Test
    fun signDigestAcceptsNoneDevOnlyActivation() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-dev-only",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val handle =
                wscd.generateKey(spec).let {
                    assertTrue(it.isOk, "generateKey failed")
                    it.value
                }

            val activation = ActivationProof(kind = ActivationProofKind.NONE_DEV_ONLY, token = "")
            val signature = wscd.signDigest(handle, "input".encodeToByteArray(), activation)

            assertTrue(signature.isOk, "signDigest failed: ${if (signature.isErr) signature.error else ""}")
        }

    @Test
    fun signDigestRejectsRemoteActivationDecision() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-remote-activation",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val handle =
                wscd.generateKey(spec).let {
                    assertTrue(it.isOk, "generateKey failed")
                    it.value
                }

            val activation =
                ActivationProof(
                    kind = ActivationProofKind.REMOTE_ACTIVATION_DECISION,
                    token = "sad-token",
                    digestBinding = "digest-binding",
                    nonce = "nonce",
                )
            val signature = wscd.signDigest(handle, "input".encodeToByteArray(), activation)

            assertTrue(signature.isErr, "software WSCD must reject remote activation decisions: it has no SAM")
            assertEquals("WALLET_WSCD_ACTIVATION_UNSUPPORTED", signature.error.code)
        }

    @Test
    fun signDigestFailsForAnUnprovisionedHandle() =
        runTest {
            val wscd = newHarness().wscd
            val handle = WscdKeyHandle(keyRef = "never-provisioned", profile = WscdProfile.Software, walletUnitId = "wallet-unknown")

            val activation = ActivationProof(kind = ActivationProofKind.LOCAL_USER_AUTH, token = "token")
            val signature = wscd.signDigest(handle, "input".encodeToByteArray(), activation)

            assertTrue(signature.isErr)
            assertEquals("WALLET_WSCD_KEY_NOT_PROVISIONED", signature.error.code)
        }

    @Test
    fun keyEvidenceIsEmptyForTheSoftwareProfile() =
        runTest {
            val wscd = newHarness().wscd
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-evidence",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val handle =
                wscd.generateKey(spec).let {
                    assertTrue(it.isOk, "generateKey failed")
                    it.value
                }

            val evidence = wscd.keyEvidence(handle)

            assertTrue(evidence.isOk, "keyEvidence failed: ${if (evidence.isErr) evidence.error else ""}")
            assertEquals(WscdProfile.Software, evidence.value.profile)
            // Honest custody: the software profile makes no platform key-attestation claim.
            assertTrue(evidence.value.evidence.isEmpty(), "software custody evidence must be empty")
        }

    @Test
    fun keyEvidenceFailsForAnUnprovisionedHandle() =
        runTest {
            val wscd = newHarness().wscd
            val handle = WscdKeyHandle(keyRef = "never-provisioned", profile = WscdProfile.Software, walletUnitId = "wallet-unknown")

            val evidence = wscd.keyEvidence(handle)

            assertTrue(evidence.isErr)
            assertEquals("WALLET_WSCD_KEY_NOT_PROVISIONED", evidence.error.code)
        }

    private class SoftwareWscdHarness(
        val wscd: SoftwareWscd,
        val kms: KeyManagerService,
        val bootstrap: KmsProviderBootstrap,
        val keyReferenceStore: KeyReferenceStore,
        val tenantId: String,
        val keyManagerTenantId: String,
    )

    /**
     * Builds a [SoftwareWscd] over a real session graph WITHOUT registering the software KMS
     * provider up front: the provider only appears once the test drives [SoftwareWscd] into its
     * first `generateKey`/`generateFreshKey` call, proving the lazy [SoftwareKmsProviderRegistrar]
     * path works standalone, the same way it does when [SoftwareWscd] is resolved via Metro's
     * direct SessionScope binding in product code.
     */
    private suspend fun newHarness(): SoftwareWscdHarness {
        val sessionId = "wallet-wscd-software-test-${Uuid.v4String()}"
        val app =
            createWalletAppGraph(
                application = "SoftwareWscdTest",
                appId = "com.sphereon.wallet.wscd-software-test",
                profile = "test",
                version = "0.1.0",
            )
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        val softwareKmsProviderFactory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val registrar =
            SoftwareKmsProviderRegistrar(
                keyManagerService = kms,
                softwareKmsProviderFactory = softwareKmsProviderFactory,
                execution = session.asCoreApiServiceGraph().serviceExecution,
                app = app,
                keyStoreConfiguration = SoftwareWscdKeyStoreConfiguration.InMemoryForTestingOnly,
                sessionId = sessionId,
            )
        val execution = session.asCoreApiServiceGraph().serviceExecution
        return SoftwareWscdHarness(
            wscd = SoftwareWscd(kms, registrar),
            kms = kms,
            bootstrap = registrar,
            keyReferenceStore = app.keyReferenceStoreFactory.store,
            tenantId = execution.sessionContext.context.tenant.tenantId,
            keyManagerTenantId = execution.sessionContext.context.tenant.tenantId,
        )
    }

    private fun com.sphereon.crypto.key.persistence.KeyReferenceRecord.toDiagnosticString(): String =
        "(tenant=$tenantId, alias=$alias, provider=$providerId, owner=$walletUnitId)"
}
