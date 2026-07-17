/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.mobile

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.kms.provider.mobile.MOBILE_KMS_HARDWARE_BACKING_KEY
import com.sphereon.crypto.kms.provider.mobile.MOBILE_KMS_HARDWARE_BACKING_REQUIRED
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProviderConfig
import com.sphereon.crypto.kms.provider.mobile.MobileKmsProviderImpl
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wscd.ActivationProof
import com.sphereon.wallet.wscd.ActivationProofKind
import com.sphereon.wallet.wscd.WscdKeyHandle
import com.sphereon.wallet.wscd.WscdKeySpec
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Real-crypto (mobile-KMS backed) unit tests for [LocalNativeWscd]: the custody-only half of the
 * WSCA/WSCD split for the [WscdProfile.LocalNative] profile. No stubs: keys are generated in, and
 * signatures produced by, [MobileKmsProviderImpl].
 *
 * Placed in jvmTest (mirrors [com.sphereon.wallet.wscd.software.SoftwareWscdTest]'s own jvmTest
 * placement in the sibling wscd/software module) to use [staticMinimalTestAppGraph] for a real
 * [com.sphereon.core.api.context.SessionExecution] without pulling `lib-core-test` (which has no
 * android target) into this module's commonTest.
 *
 * JVM ACTUAL NOTE: `createMobileSigningProvider` on the JVM is JKS-backed (a software keystore file
 * via signum-supreme's `JKSProvider`), NOT a real hardware keystore - there is no Android StrongBox
 * / iOS Secure Enclave available in a JVM unit test. Tests here therefore exercise the
 * [com.sphereon.wallet.wscd.Wscd] CONTRACT (idempotency, alias derivation, signing, deletion,
 * evidence shape) exactly as `SoftwareWscdTest` does for the software profile, over this JVM
 * software-fallback actual. The one test whose name ends in `_jvmSoftwareFallback`,
 * [requireStrongBoxFailsClosedWhenNoHardwareBackingIsAvailable_jvmSoftwareFallback], calls this out
 * explicitly: it verifies the FAIL-CLOSED path precisely BECAUSE the JVM has no hardware keystore
 * (see that test's KDoc) - it is a genuine, fully-runnable negative-path assertion, not a
 * hardware-only path being skipped. No test in this file asserts that a key actually landed in
 * hardware, since that can only be observed on a real Android/iOS device/emulator, out of reach for
 * a jvmTest (left to the controller's android/ios compile-only verification, per the task brief).
 */
class LocalNativeWscdTest {
    @Test
    fun generateKeyIsIdempotentByAliasAndReturnsUsableHandle() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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
            assertEquals(WscdProfile.LocalNative, first.value.profile)
            assertEquals("wallet-a", first.value.walletUnitId)

            // A different wallet unit gets a distinct key.
            val other = wscd.generateKey(spec.copy(walletUnitId = "wallet-b"))
            assertTrue(other.isOk)
            assertNotEquals(first.value.keyRef, other.value.keyRef)
        }

    @Test
    fun generateKeyHonorsCallerSuppliedAliasVerbatim() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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
    fun defaultAliasDerivationMatchesSoftwareWscdsScheme() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-alias",
                    usage = SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = SignatureAlgorithm.ECDSA_SHA384,
                )

            val result = wscd.generateKey(spec)

            assertTrue(result.isOk, "generateKey failed: ${if (result.isErr) result.error else ""}")
            // Pinned: IDENTICAL scheme to SoftwareWscd (binding requirement) -
            // "wallet-units/{walletUnitId}/{usage lowercase}/{jose alg lowercase}".
            assertEquals("wallet-units/wallet-alias/wallet_attestation/es384", result.value.keyRef)
            assertTrue(result.value.publicKeyJwk?.contains("\"kty\"") == true, "handle must carry the public JWK")
            assertTrue(!result.value.keyId.isNullOrBlank(), "handle must carry a keyId")
            assertTrue(!result.value.providerId.isNullOrBlank(), "handle must carry a providerId")
        }

    @Test
    fun generateFreshKeyMintsADistinctKeyOnEveryCall() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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
            assertNotEquals(first.value.keyRef, second.value.keyRef, "each call must mint a distinct key reference")
        }

    @Test
    fun generateFreshKeyIgnoresACallerSuppliedAlias() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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
            val provider = newProvider()
            val wscd = LocalNativeWscd(provider = provider, requireStrongBox = false)
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

            val verified =
                provider.isValidRawSignature(
                    keyInfo = KeyInfo<Nothing>(alias = handle.keyRef),
                    input = signingInput,
                    signature = signature.value,
                )
            assertTrue(verified, "signature must verify against the provisioned key")
        }

    @Test
    fun signDigestAcceptsNoneDevOnlyActivation() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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

            assertTrue(signature.isErr, "local-native WSCD must reject remote activation decisions: it has no SAM")
            assertEquals("WALLET_WSCD_ACTIVATION_UNSUPPORTED", signature.error.code)
        }

    @Test
    fun signDigestFailsForAnUnprovisionedHandle() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
            val handle = WscdKeyHandle(keyRef = "never-provisioned", profile = WscdProfile.LocalNative, walletUnitId = "wallet-unknown")

            val activation = ActivationProof(kind = ActivationProofKind.LOCAL_USER_AUTH, token = "token")
            val signature = wscd.signDigest(handle, "input".encodeToByteArray(), activation)

            assertTrue(signature.isErr)
            assertEquals("WALLET_WSCD_KEY_NOT_PROVISIONED", signature.error.code)
        }

    @Test
    fun deleteKeyRemovesTheProvisionedKeyAndSubsequentOperationsFail() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-delete",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            val handle =
                wscd.generateKey(spec).let {
                    assertTrue(it.isOk, "generateKey failed")
                    it.value
                }

            val deleted = wscd.deleteKey(handle)
            assertTrue(deleted.isOk, "deleteKey failed: ${if (deleted.isErr) deleted.error else ""}")

            val activation = ActivationProof(kind = ActivationProofKind.LOCAL_USER_AUTH, token = "token")
            val signAfterDelete = wscd.signDigest(handle, "input".encodeToByteArray(), activation)
            assertTrue(signAfterDelete.isErr, "signing after deletion must fail")
            assertEquals("WALLET_WSCD_KEY_NOT_PROVISIONED", signAfterDelete.error.code)

            val evidenceAfterDelete = wscd.keyEvidence(handle)
            assertTrue(evidenceAfterDelete.isErr, "keyEvidence after deletion must fail")
        }

    @Test
    fun keyEvidenceReportsTheLocalNativeProfileAndTheHonestAttestationGap() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
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
            assertEquals(WscdProfile.LocalNative, evidence.value.profile)
            // G10 partial: signum-supreme exposes attestation but MobileKmsProvider does not plumb it
            // through yet (see LocalNativeWscd's class KDoc) - the evidence must say so honestly
            // rather than fabricate a chain.
            assertEquals("unavailable-pending-signum", evidence.value.evidence["key_attestation"])
            assertEquals("preferred", evidence.value.evidence["hardware_backing_preference"])
        }

    @Test
    fun keyEvidenceFailsForAnUnprovisionedHandle() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(), requireStrongBox = false)
            val handle = WscdKeyHandle(keyRef = "never-provisioned", profile = WscdProfile.LocalNative, walletUnitId = "wallet-unknown")

            val evidence = wscd.keyEvidence(handle)

            assertTrue(evidence.isErr)
            assertEquals("WALLET_WSCD_KEY_NOT_PROVISIONED", evidence.error.code)
        }

    /**
     * The load-bearing test for `requireStrongBox`: configures the provider
     * with `MOBILE_KMS_HARDWARE_BACKING_KEY = MOBILE_KMS_HARDWARE_BACKING_REQUIRED` (as
     * [LocalNativeWscdFactory] does for `WscdConfig.LocalNative(requireStrongBox = true)`). On the
     * JVM, `MobileKmsProviderImpl`'s `createMobileSigningProvider` actual is signum-supreme's
     * `JKSProvider` (a software keystore file, NOT a hardware keystore); `JKSProvider.createSigningKey`
     * checks `backing == REQUIRED` FIRST and throws
     * `UnsupportedCryptoException("Hardware storage is unsupported on the JVM")` before doing
     * anything else. This test verifies that failure surfaces through [LocalNativeWscd.generateKey]
     * as a FAIL-CLOSED `WALLET_WSCD_HARDWARE_REQUIRED` [com.sphereon.core.api.error.IdkError] - i.e.
     * this exercises the actual JVM software-fallback behavior (a real negative-path assertion), not
     * a hardware-only path being skipped.
     */
    @Test
    fun requireStrongBoxFailsClosedWhenNoHardwareBackingIsAvailable_jvmSoftwareFallback() =
        runTest {
            val wscd = LocalNativeWscd(provider = newProvider(requireStrongBox = true), requireStrongBox = true)
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-strongbox-required",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = wscd.generateKey(spec)

            assertTrue(result.isErr, "requireStrongBox=true must fail closed on the JVM's JKS software-fallback keystore")
            assertEquals("WALLET_WSCD_HARDWARE_REQUIRED", result.error.code)
        }

    @Test
    fun requireStrongBoxFalseSucceedsOnTheJvmSoftwareFallback() =
        runTest {
            // Baseline contrast for the test above: the default (PREFERRED) backing preference
            // succeeds even though the JVM has no hardware keystore, since PREFERRED silently falls
            // back to software storage instead of refusing key creation.
            val wscd = LocalNativeWscd(provider = newProvider(requireStrongBox = false), requireStrongBox = false)
            val spec =
                WscdKeySpec(
                    walletUnitId = "wallet-strongbox-preferred",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = wscd.generateKey(spec)

            assertTrue(result.isOk, "PREFERRED backing must silently succeed on the JVM: ${if (result.isErr) result.error else ""}")
        }

    private fun newProvider(requireStrongBox: Boolean = false): MobileKmsProviderImpl {
        val providerId = "wscd-mobile-test-${Uuid.v4String()}"
        val app =
            staticMinimalTestAppGraph(
                application = "LocalNativeWscdTest",
                appId = "com.sphereon.wallet.wscd-mobile-test",
                profile = "test",
                version = "0.1.0",
            )
        val user = app.userContextManager.getAnonymous()
        val session = user.sessionContextManager.createOrGetFromId(providerId)
        val defaultConfigValues =
            buildMap {
                put("jks.path", "build/wscd-mobile-test/$providerId.p12")
                if (requireStrongBox) put(MOBILE_KMS_HARDWARE_BACKING_KEY, MOBILE_KMS_HARDWARE_BACKING_REQUIRED)
            }
        return MobileKmsProviderImpl(
            MobileKmsProviderConfig(id = providerId, defaultConfigValues = defaultConfigValues),
            execution = session.asCoreApiServiceGraph().serviceExecution,
        )
    }
}
