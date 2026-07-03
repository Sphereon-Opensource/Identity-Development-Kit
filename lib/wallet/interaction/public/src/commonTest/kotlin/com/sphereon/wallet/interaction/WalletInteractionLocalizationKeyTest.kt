/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class WalletInteractionLocalizationKeyTest {
    @Test
    fun uiChromeFieldsAcceptLocalizationKeys() {
        assertNotNull(
            WalletDisplayMessage(
                titleKey = "wallet.interaction.status.credential_offer_review",
                textKey = "wallet.interaction.error.no_registered_adapter",
            ),
        )
        assertNotNull(WalletInteractionError("wallet.error", messageKey = "wallet.interaction.error.no_adapter"))
        assertNotNull(
            WalletImplementationChoice(
                adapterId = "oid4vci",
                protocol = WalletProtocol.OID4VCI,
                flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
                labelKey = "wallet.interaction.adapter.oid4vci",
            ),
        )
        assertNotNull(WalletTxCodeSpec(descriptionKey = "wallet.interaction.tx_code.required"))
        assertNotNull(WalletClaimDescriptor(path = listOf("given_name"), labelKey = "wallet.interaction.claim.given_name"))
        assertNotNull(
            WalletTrustSource(
                type = WalletTrustSourceType.EUDI_TRUSTED_LIST,
                identifier = "eu",
                labelKey = "wallet.interaction.trust_source.eudi_trusted_list",
            ),
        )
        assertNotNull(
            WalletSecurityChallenge(
                challengeId = "holder-proof",
                kind = WalletSecurityChallengeKind.WALLET_UNIT_POLICY,
                reasonKey = "wallet.interaction.security.wallet_unit_policy",
            ),
        )
    }

    @Test
    fun uiChromeFieldsRejectDisplayText() {
        assertFailsWith<IllegalArgumentException> { WalletDisplayMessage(titleKey = "invalid_title") }
        assertFailsWith<IllegalArgumentException> { WalletDisplayMessage(textKey = "invalid_text") }
        assertFailsWith<IllegalArgumentException> { WalletDisplayMessage(titleKey = "Review credential") }
        assertFailsWith<IllegalArgumentException> { WalletDisplayMessage(textKey = "Enter transaction code") }
        assertFailsWith<IllegalArgumentException> { WalletInteractionError("wallet.error", messageKey = "invalid_message") }
        assertFailsWith<IllegalArgumentException> { WalletInteractionError("wallet.error", messageKey = "Something failed") }
        assertFailsWith<IllegalArgumentException> {
            WalletImplementationChoice(
                adapterId = "oid4vci",
                protocol = WalletProtocol.OID4VCI,
                flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
                labelKey = "invalid_label",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WalletImplementationChoice(
                adapterId = "oid4vci",
                protocol = WalletProtocol.OID4VCI,
                flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
                labelKey = "Continue",
            )
        }
        assertFailsWith<IllegalArgumentException> { WalletTxCodeSpec(descriptionKey = "invalid_description") }
        assertFailsWith<IllegalArgumentException> { WalletClaimDescriptor(path = listOf("given_name"), labelKey = "invalid_claim_label") }
        assertFailsWith<IllegalArgumentException> {
            WalletTrustSource(
                type = WalletTrustSourceType.EUDI_TRUSTED_LIST,
                identifier = "eu",
                labelKey = "invalid_trust_label",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WalletSecurityChallenge(
                challengeId = "holder-proof",
                kind = WalletSecurityChallengeKind.WALLET_UNIT_POLICY,
                reasonKey = "invalid_reason",
            )
        }
        assertFailsWith<IllegalArgumentException> { WalletSecurityGateResult.Denied(reasonKey = "invalid_denied_reason") }
        assertFailsWith<IllegalArgumentException> {
            WalletProtocolCapability(
                adapterId = "oid4vci",
                protocol = WalletProtocol.OID4VCI,
                flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
                labelKey = "invalid_label",
            )
        }
    }

    @Test
    fun protocolMetadataDisplayValuesRemainExternalData() {
        assertNotNull(
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.ISSUER,
                identifier = "https://issuer.example",
                displayName = "issuer.example",
            ),
        )
        assertNotNull(WalletCredentialBranding(credentialConfigurationId = "identity", name = "identity"))
        assertNotNull(WalletCredentialPreview(id = "identity", name = "identity"))
    }
}
