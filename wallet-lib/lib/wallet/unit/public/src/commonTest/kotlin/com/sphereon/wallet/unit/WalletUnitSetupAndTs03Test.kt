/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.unit

import com.sphereon.wallet.unit.attestation.LocalEvaluationWalletAttestationSigner
import com.sphereon.wallet.unit.attestation.Ts03StatusClaim
import com.sphereon.wallet.unit.attestation.Ts03WalletAttestationEncoder
import com.sphereon.wallet.unit.attestation.Ts03WalletInstanceAttestationClaims
import com.sphereon.wallet.unit.attestation.WalletAttestationSignerProfile
import com.sphereon.wallet.unit.attestation.WalletAttestationSigningAlgorithm
import com.sphereon.wallet.unit.attestation.WalletAttestationSigningRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class WalletUnitSetupAndTs03Test {
    @Test
    fun backendlessMobileWalletAllowsLocalDeviceKeystore() {
        val result =
            WalletSetupValidator.validate(
                WalletSetupOptions(
                    runtime = WalletRuntime.MOBILE,
                    backendMode = WalletBackendMode.NONE,
                    storageProfile = WalletStorageProfile.LOCAL,
                    keystoreProfile = WalletKeystoreProfile.LOCAL_DEVICE,
                    secureComponentProfile = WalletSecureComponentProfile.LOCAL_DEVICE_WSCA_WSCD,
                    attestationProfile = WalletAttestationProfile.NONE,
                    trustPolicy = WalletTrustPolicy.NONE,
                ),
            )

        assertTrue(result.valid, "backendless mobile setup must be allowed for non-wallet-backend operation")
    }

    @Test
    fun localEvaluationAttestationRequiresExplicitEvaluationProfiles() {
        val options =
            WalletSetupOptions(
                runtime = WalletRuntime.MOBILE,
                backendMode = WalletBackendMode.NONE,
                storageProfile = WalletStorageProfile.LOCAL,
                keystoreProfile = WalletKeystoreProfile.LOCAL_DEVICE,
                secureComponentProfile = WalletSecureComponentProfile.LOCAL_DEVICE_WSCA_WSCD,
                attestationProfile = WalletAttestationProfile.LOCAL_EVALUATION_REFERENCE,
                trustPolicy = WalletTrustPolicy.NONE,
            )

        assertTrue(!WalletSetupValidator.validate(options).valid)
        assertTrue(WalletSetupValidator.validate(options.copy(evaluationProfilesEnabled = true)).valid)
    }

    @Test
    fun webWalletAllowsExplicitLocalOssWscdProfile() {
        val result =
            WalletSetupValidator.validate(
                WalletSetupOptions(
                    runtime = WalletRuntime.WEB,
                    backendMode = WalletBackendMode.NONE,
                    storageProfile = WalletStorageProfile.LOCAL,
                    keystoreProfile = WalletKeystoreProfile.WEBCRYPTO,
                    secureComponentProfile = WalletSecureComponentProfile.WEBCRYPTO_WSCD,
                    attestationProfile = WalletAttestationProfile.NONE,
                    trustPolicy = WalletTrustPolicy.NONE,
                    browserSessionBindingPolicy = BrowserSessionBindingPolicy(policyId = "browser-origin-bound"),
                    userAuthenticationPolicy = WalletUserAuthenticationPolicyRef(policyId = "webauthn-or-platform", assuranceLevel = "local"),
                ),
            )

        assertTrue(result.valid, "explicit local OSS web wallet profile must be allowed: ${result.errors}")
    }

    @Test
    fun webWalletLocalOssWscdProfileRequiresExplicitBrowserAndUserPolicies() {
        val result =
            WalletSetupValidator.validate(
                WalletSetupOptions(
                    runtime = WalletRuntime.WEB,
                    backendMode = WalletBackendMode.NONE,
                    storageProfile = WalletStorageProfile.LOCAL,
                    keystoreProfile = WalletKeystoreProfile.WEBCRYPTO,
                    secureComponentProfile = WalletSecureComponentProfile.WEBCRYPTO_WSCD,
                    attestationProfile = WalletAttestationProfile.NONE,
                    trustPolicy = WalletTrustPolicy.NONE,
                ),
            )

        assertTrue(!result.valid)
        assertTrue(result.errors.any { it.contains("browserSessionBindingPolicy") })
        assertTrue(result.errors.any { it.contains("userAuthenticationPolicy") })
    }

    @Test
    fun secureComponentsCanExistWithoutWalletUnitBindingForEidasSignaturesAndSeals() {
        val application =
            WalletSecureCryptographicApplicationRef(
                id = "wsca-eidas-a",
                componentType = WalletSecureComponentType.REMOTE_WSCD,
                usages = setOf(SecureComponentUsage.EIDAS_SIGNATURE, SecureComponentUsage.EIDAS_SEAL),
            )
        val device =
            WalletSecureCryptographicDeviceRef(
                id = "wscd-eidas-a",
                componentType = WalletSecureComponentType.REMOTE_QSCD,
                securityLevel = WalletKeystoreSecurityLevel.ISO_18045_HIGH,
                usages = setOf(SecureComponentUsage.EIDAS_SIGNATURE, SecureComponentUsage.EIDAS_SEAL),
            )

        assertTrue(application.walletBinding == null)
        assertTrue(device.walletBinding == null)
        assertTrue(SecureComponentUsage.EIDAS_SIGNATURE in application.usages)
        assertTrue(SecureComponentUsage.EIDAS_SEAL in device.usages)
    }

    @Test
    fun ts03EncoderCreatesCompactJwtArtifact() =
        runTest {
            val now = Clock.System.now()
            val result =
                Ts03WalletAttestationEncoder()
                    .encodeWalletInstanceAttestation(
                        claims =
                            Ts03WalletInstanceAttestationClaims(
                                iss = "https://wallet-provider.example",
                                sub = "wallet-instance-a",
                                aud = "audience-a",
                                iat = now.epochSeconds,
                                exp = (now + 5.minutes).epochSeconds,
                                jti = "wia-jti-a",
                                walletName = "wallet-a",
                                walletVersion = "1.0",
                                walletSolutionCertificationInformation = mapOf("certification_id" to "cert-a"),
                                clientStatus = Ts03StatusClaim(status = "https://status.example/list#1", exp = (now + 5.minutes).epochSeconds),
                            ),
                        signer = LocalEvaluationWalletAttestationSigner(signerId = "signer-a"),
                        signingRequest =
                            WalletAttestationSigningRequest(
                                algorithm = WalletAttestationSigningAlgorithm.ES256,
                                signerProfile = WalletAttestationSignerProfile.LOCAL_EVALUATION,
                                signerId = "signer-a",
                                signingInput = ByteArray(0),
                                x5c = listOf("cert-chain-a"),
                                keyId = "kid-a",
                            ),
                    ).value

            assertEquals(3, result.compact.split('.').size)
            assertTrue(result.artifactHash.startsWith("sha256:"))
            assertEquals("ES256", result.signingEvidence["alg"])
        }
}
