/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WalletSecurityGrantValidationTest {
    private val challenge =
        WalletSecurityChallenge(
            challengeId = "challenge-1",
            kind = WalletSecurityChallengeKind.PIN,
            reasonKey = "wallet.interaction.security.pin",
            arguments =
                mapOf(
                    "wallet_unit_id" to "wu-personal",
                    "audience" to "verifier.example",
                    "operation_binding" to "sha256:operation",
                ),
            requiredAssurance = WalletSecurityAssurance.PIN,
        )

    @Test
    fun acceptsExactFreshOperationBoundGrant() {
        assertIs<WalletSecurityGrantValidation.Valid>(validGrant().validateFor(challenge, "wu-personal", "verifier.example", 100))
    }

    @Test
    fun operationBoundPasskeyReplacesPinAndBiometricAssurance() {
        val passkeyGrant = validGrant().copy(assurance = WalletSecurityAssurance.PASSKEY)
        val passkeyChallenge = challenge.copy(
            kind = WalletSecurityChallengeKind.PASSKEY,
            requiredAssurance = WalletSecurityAssurance.PASSKEY,
        )
        val biometricChallenge = challenge.copy(
            kind = WalletSecurityChallengeKind.BIOMETRIC,
            requiredAssurance = WalletSecurityAssurance.BIOMETRIC,
        )

        assertIs<WalletSecurityGrantValidation.Valid>(passkeyGrant.validateFor(passkeyChallenge, "wu-personal", "verifier.example", 100))
        assertIs<WalletSecurityGrantValidation.Valid>(passkeyGrant.validateFor(challenge, "wu-personal", "verifier.example", 100))
        assertIs<WalletSecurityGrantValidation.Valid>(passkeyGrant.validateFor(biometricChallenge, "wu-personal", "verifier.example", 100))
    }

    @Test
    fun rejectsMissingOrExpiredExpiry() {
        assertInvalid("wallet.interaction.security.grant_expiry_missing", validGrant().copy(expiresAtEpochSeconds = null))
        assertInvalid("wallet.interaction.security.grant_expired", validGrant().copy(expiresAtEpochSeconds = 100))
    }

    @Test
    fun rejectsChallengeAssuranceWalletAudienceAndOperationMismatch() {
        assertInvalid("wallet.interaction.security.challenge_binding_invalid", validGrant().copy(grantId = "other"))
        assertInvalid("wallet.interaction.security.grant_assurance_insufficient", validGrant().copy(assurance = WalletSecurityAssurance.USER_PRESENT))
        assertInvalid("wallet.interaction.security.grant_wallet_unit_invalid", validGrant().copy(evidence = validGrant().evidence + ("wallet_unit_id" to "wu-other")))
        assertInvalid("wallet.interaction.security.grant_audience_invalid", validGrant().copy(evidence = validGrant().evidence + ("audience" to "other")))
        assertInvalid("wallet.interaction.security.grant_operation_binding_invalid", validGrant().copy(evidence = validGrant().evidence + ("operation_binding" to "sha256:other")))
    }

    private fun validGrant() =
        WalletSecurityGrant(
            grantId = "challenge-1",
            assurance = WalletSecurityAssurance.PIN,
            expiresAtEpochSeconds = 200,
            evidence =
                mapOf(
                    "challenge_id" to "challenge-1",
                    "wallet_unit_id" to "wu-personal",
                    "audience" to "verifier.example",
                    "operation_binding" to "sha256:operation",
                ),
        )

    private fun assertInvalid(reason: String, grant: WalletSecurityGrant) {
        val invalid = assertIs<WalletSecurityGrantValidation.Invalid>(grant.validateFor(challenge, "wu-personal", "verifier.example", 100))
        assertEquals(reason, invalid.reasonKey)
    }
}
