/*
 * (C) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEvidence
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceClientStatusEvidence
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceTrustEvidence
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletInstanceAttestationProductionPolicyTest {
    private fun evidence(
        now: Long = NOW,
        profile: String = "TS03_JWT",
        format: String = "JWT",
        expiresAt: Long = now + 600,
        status: WalletInstanceClientStatusEvidence =
            WalletInstanceClientStatusEvidence(
                statusListUri = "https://status.example.com/wia/status.jwt",
                index = "7",
                status = "VALID",
                maintenanceExpiresAtEpochSeconds = now + 600,
            ),
        trust: WalletInstanceTrustEvidence =
            WalletInstanceTrustEvidence(
                trusted = true,
                decision = "TRUSTED",
                expiresAtEpochSeconds = now + 600,
                signerCertificateProfile = "HARDWARE_SECURE",
            ),
    ): WalletInstanceAttestationEvidence =
        WalletInstanceAttestationEvidence(
            evidenceId = "persisted-wia-1",
            profile = profile,
            format = format,
            attestationExpiresAtEpochSeconds = expiresAt,
            clientStatus = status,
            trust = trust,
            signerCertificateProfile = trust.signerCertificateProfile,
        )

    @Test
    fun trustedWalletInstanceAttestationEvidencePassesProductionPolicy() {
        assertNull(WalletInstanceAttestationProductionPolicy.validate(evidence(), NOW))
    }

    @Test
    fun expiredWalletInstanceAttestationEvidenceFailsProductionPolicy() {
        assertNotNull(WalletInstanceAttestationProductionPolicy.validate(evidence(expiresAt = NOW - 1), NOW))
    }

    @Test
    fun untrustedWalletInstanceAttestationEvidenceFailsProductionPolicy() {
        assertNotNull(
            WalletInstanceAttestationProductionPolicy.validate(
                evidence(
                    trust =
                        WalletInstanceTrustEvidence(
                            trusted = false,
                            decision = "UNTRUSTED",
                        ),
                ),
                NOW,
            ),
        )
    }

    @Test
    fun unsupportedWalletInstanceAttestationProfileFailsProductionPolicy() {
        assertNotNull(WalletInstanceAttestationProductionPolicy.validate(evidence(profile = "UNSUPPORTED_PROFILE"), NOW))
    }

    private companion object {
        const val NOW = 2_000_000L
    }
}
