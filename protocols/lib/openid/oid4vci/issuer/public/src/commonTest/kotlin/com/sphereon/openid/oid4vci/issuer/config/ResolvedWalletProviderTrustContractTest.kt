/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResolvedWalletProviderTrustContractTest {
    @Test
    fun resolvedTrustCarriesTypedPublicVerifierMaterialAndAttachmentPolicy() {
        val jwk = publicJwk()
        val trust =
            ResolvedWalletProviderTrust(
                policyDigest = SHA256,
                admission = WalletProviderAdmission.RESTRICTED,
                verifierMaterials =
                    listOf(
                        WalletProviderVerifierMaterial.Jwk(
                            publicJwk = jwk,
                            fingerprint = SHA256,
                        ),
                        WalletProviderVerifierMaterial.Issuer("https://wallet-provider.example"),
                        WalletProviderVerifierMaterial.X509(
                            certificateDerBase64 = "AQID",
                            fingerprint = SECOND_SHA256,
                        ),
                        WalletProviderVerifierMaterial.Ts119602(
                            providerId = "wallet-provider-1",
                            serviceTypeIdentifier = ETSI_WALLET_PROVIDER,
                            snapshotId = "snapshot-7",
                            artifactDigest = THIRD_SHA256,
                            certificateDerBase64 = "BAUG",
                            certificateFingerprint = FOURTH_SHA256,
                        ),
                    ),
                requireWalletUnitEvidence = true,
            )

        assertEquals(WalletProviderAdmission.RESTRICTED, trust.admission)
        assertTrue(trust.requireWalletUnitEvidence)
        assertEquals(
            setOf(
                WalletProviderTrustMechanism.JWK,
                WalletProviderTrustMechanism.ISSUER,
                WalletProviderTrustMechanism.X509,
                WalletProviderTrustMechanism.ETSI_TS_119_602,
            ),
            trust.verifierMaterials.map { it.mechanism }.toSet(),
        )
    }

    @Test
    fun restrictedTrustCannotResolveWithoutVerifierMaterial() {
        assertFailsWith<IllegalArgumentException> {
            ResolvedWalletProviderTrust(
                policyDigest = SHA256,
                admission = WalletProviderAdmission.RESTRICTED,
                verifierMaterials = emptyList(),
                requireWalletUnitEvidence = false,
            )
        }
    }

    @Test
    fun privateJwkMaterialIsRejectedAtTheResolvedBoundary() {
        val privateJwk =
            Jwk.fromJsonObject(
                Json.parseToJsonElement(
                    """{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag","d":"Aw","kid":"private"}""",
                ).jsonObject,
            )
        assertFailsWith<IllegalArgumentException> {
            WalletProviderVerifierMaterial.Jwk(privateJwk, SHA256)
        }
    }

    @Test
    fun allPrivateAndSymmetricJwkMembersAreRejectedAtTheResolvedBoundary() {
        val privateMembers = listOf("d", "p", "q", "dP", "dQ", "qInv", "k")
        privateMembers.forEach { member ->
            val privateJwk = Jwk(
                kty = JwaKeyType.EC,
                crv = com.sphereon.crypto.core.jose.JwaCurve.P_256,
                x = "AQ",
                y = "Ag",
                d = if (member == "d") "Aw" else null,
                p = if (member == "p") "Aw" else null,
                q = if (member == "q") "Aw" else null,
                dP = if (member == "dP") "Aw" else null,
                dQ = if (member == "dQ") "Aw" else null,
                qInv = if (member == "qInv") "Aw" else null,
                k = if (member == "k") "Aw" else null,
            )
            assertFailsWith<IllegalArgumentException>("private JWK member $member must be rejected") {
                WalletProviderVerifierMaterial.Jwk(privateJwk, SHA256)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletProviderVerifierMaterial.Jwk(Jwk(kty = JwaKeyType.oct, k = "c2VjcmV0"), SHA256)
        }
    }

    private fun publicJwk(): Jwk =
        Jwk.fromJsonObject(
            Json.parseToJsonElement(
                """{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag","kid":"public"}""",
            ).jsonObject,
        )

    private companion object {
        const val SHA256 = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SECOND_SHA256 = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val THIRD_SHA256 = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val FOURTH_SHA256 = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        const val ETSI_WALLET_PROVIDER = "http://uri.etsi.org/TrstSvc/Svctype/WalletProvider"
    }
}
