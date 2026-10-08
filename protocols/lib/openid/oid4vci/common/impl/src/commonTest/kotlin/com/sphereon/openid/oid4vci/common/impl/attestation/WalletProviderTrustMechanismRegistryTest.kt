/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vci.common.impl.attestation

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.openid.oid4vci.issuer.config.ETSI_TS_119_602_WALLET_PROVIDER_SERVICE_TYPE
import com.sphereon.openid.oid4vci.issuer.config.ResolvedWalletProviderTrust
import com.sphereon.openid.oid4vci.issuer.config.WalletProviderAdmission
import com.sphereon.openid.oid4vci.issuer.config.WalletProviderVerifierMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletProviderTrustMechanismRegistryTest {
    @Test
    fun `restricted registry exposes only exact typed persisted mechanisms`() {
        val registry =
            WalletProviderTrustMechanismRegistry.from(
                ResolvedWalletProviderTrust(
                    DIGEST,
                    WalletProviderAdmission.RESTRICTED,
                    listOf(
                        WalletProviderVerifierMaterial.Jwk(PUBLIC_JWK, FINGERPRINT),
                        WalletProviderVerifierMaterial.Issuer("https://wallet-provider.example"),
                        WalletProviderVerifierMaterial.X509("AQID", FINGERPRINT),
                        WalletProviderVerifierMaterial.Ts119602(
                            "provider-a",
                            ETSI_TS_119_602_WALLET_PROVIDER_SERVICE_TYPE,
                            "snapshot-a",
                            DIGEST,
                            "BAUG",
                            FINGERPRINT,
                        ),
                    ),
                    requireWalletUnitEvidence = true,
                ),
            )

        assertEquals(listOf(PUBLIC_JWK), registry.jwkCandidates(PRESENTED_JWK, PRESENTED_JWK_JSON))
        assertEquals(setOf("https://wallet-provider.example"), registry.issuers)
        assertEquals(listOf("AQID", "BAUG"), registry.x509AnchorDerCertificates("BwgJ"))
        assertTrue(registry.requireWalletUnitEvidence)
    }

    @Test
    fun `unrestricted registry uses only public evidence embedded by the presenter`() {
        val registry =
            WalletProviderTrustMechanismRegistry.from(
                ResolvedWalletProviderTrust(
                    DIGEST,
                    WalletProviderAdmission.UNRESTRICTED,
                    emptyList(),
                    requireWalletUnitEvidence = false,
                ),
            )

        assertEquals(listOf(PRESENTED_JWK), registry.jwkCandidates(PRESENTED_JWK, PRESENTED_JWK_JSON))
        assertTrue(registry.jwkCandidates(null, JsonObject(emptyMap())).isEmpty())
        assertEquals(listOf("BwgJ"), registry.x509AnchorDerCertificates("BwgJ"))
        assertTrue(registry.x509AnchorDerCertificates(null).isEmpty())
        assertTrue(registry.issuers.isEmpty())
    }

    @Test
    fun `unrestricted registry rejects every private and symmetric presented JWK`() {
        val registry =
            WalletProviderTrustMechanismRegistry.from(
                ResolvedWalletProviderTrust(
                    DIGEST,
                    WalletProviderAdmission.UNRESTRICTED,
                    emptyList(),
                    requireWalletUnitEvidence = false,
                ),
            )

        listOf("d", "p", "q", "dP", "dQ", "qInv", "k").forEach { member ->
            val presented = Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
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
            val presentedJson = Json.encodeToJsonElement(Jwk.serializer(), presented).jsonObject
            assertTrue(registry.jwkCandidates(presented, presentedJson).isEmpty(), "private JWK member $member must be rejected")
        }
        val symmetricJwk = Jwk(kty = JwaKeyType.oct, k = "c2VjcmV0")
        assertTrue(
            registry.jwkCandidates(symmetricJwk, Json.encodeToJsonElement(Jwk.serializer(), symmetricJwk).jsonObject).isEmpty(),
        )

        listOf("oth", "OTh").forEach { member ->
            val rsaWithOtherPrimes =
                Json.parseToJsonElement(
                    """{"kty":"RSA","n":"AQ","e":"Ag","$member":[{"r":"Aw"}]}""",
                ).jsonObject
            val parsedRsaWithOtherPrimes = Jwk.fromJsonObject(rsaWithOtherPrimes)
            assertTrue(
                registry.jwkCandidates(parsedRsaWithOtherPrimes, rsaWithOtherPrimes).isEmpty(),
                "RSA $member private material must be rejected before unrestricted admission",
            )
        }
    }

    private companion object {
        const val DIGEST = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val FINGERPRINT = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val PUBLIC_JWK = Jwk.fromJsonObject(Json.parseToJsonElement("""{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag","kid":"persisted"}""").jsonObject)
        val PRESENTED_JWK_JSON = Json.parseToJsonElement("""{"kty":"EC","crv":"P-256","x":"Aw","y":"BA","kid":"presented"}""").jsonObject
        val PRESENTED_JWK = Jwk.fromJsonObject(PRESENTED_JWK_JSON)
    }
}
