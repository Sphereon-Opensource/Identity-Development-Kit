/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WalletInteractionPresentationContractsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun rendererTranscriptAndFailureContainOnlyPresentationTypes() {
        val model =
            WalletScreenModel(
                sessionId = "session-1",
                revision = 2,
                flowKind = WalletInteractionFlowKindPresentation.CREDENTIAL_RECEIVE,
                status = WalletInteractionStatusPresentation.CREDENTIAL_OFFER_REVIEW,
                titleKey = "wallet.interaction.receive",
                projection =
                    WalletInteractionScreenProjection.Progress(
                        WalletInteractionStatusPresentation.CREDENTIAL_OFFER_REVIEW,
                    ),
            )
        val result =
            WalletInteractionPresentationActionResult.Rejected(
                WalletInteractionPresentationFailure(
                    code = "stale_revision",
                    messageKey = "wallet.interaction.action.rejected",
                    retryable = true,
                ),
            )

        val transcript = json.encodeToString(model)
        val failure = json.encodeToString<WalletInteractionPresentationActionResult>(result)

        assertEquals("session-1", model.sessionId)
        assertFalse("IdkError" in transcript + failure)
        assertFalse("ktor" in transcript + failure)
    }
}
