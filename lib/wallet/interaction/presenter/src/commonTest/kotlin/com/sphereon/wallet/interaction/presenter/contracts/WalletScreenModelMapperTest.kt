/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

import com.sphereon.wallet.interaction.presenter.WalletScreenModelMapper
import com.sphereon.wallet.interaction.WalletDisplayMessage
import com.sphereon.wallet.interaction.WalletInteractionActivitySummary
import com.sphereon.wallet.interaction.WalletInteractionActivityType
import com.sphereon.wallet.interaction.WalletInteractionError
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletCredentialBranding
import com.sphereon.wallet.interaction.WalletCredentialOfferSummary
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterResult
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTrustStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-status coverage of [WalletScreenModelMapper]'s decision table (all 18
 * [WalletInteractionStatus] values), plus mapper purity and localization-key hygiene.
 */
class WalletScreenModelMapperTest {
    private fun state(
        status: WalletInteractionStatus,
        terminal: Boolean = false,
        activity: WalletInteractionActivitySummary? = null,
    ): WalletInteractionState =
        WalletInteractionState(
            sessionId = WalletInteractionSessionId("s1"),
            walletUnitId = "wallet",
            status = status,
            terminal = terminal,
            activity = activity,
        )

    @Test
    fun resolvingEntryPointHasContinuePrimaryAndNoSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.ResolvingEntryPoint))
        assertEquals("wallet.interaction.status.resolving_entry_point", model.titleKey)
        assertEquals(WalletScreenIntent.CONTINUE, model.primaryAction?.intent)
        assertNull(model.secondaryAction)
    }

    @Test
    fun implementationChoiceRequiredHasNoPrimaryButDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.ImplementationChoiceRequired))
        assertEquals("wallet.interaction.status.implementation_choice_required", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun unsupportedEntryPointHasNoPrimaryAndDeclineSecondaryWhenNotTerminal() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.UnsupportedEntryPoint))
        assertEquals("wallet.interaction.status.unsupported_entry_point", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun counterpartyNoticeHasContinuePrimaryAndDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.CounterpartyNotice))
        assertEquals("wallet.interaction.status.counterparty_notice", model.titleKey)
        assertEquals(WalletScreenIntent.CONTINUE, model.primaryAction?.intent)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun trustReviewHasContinuePrimaryAndDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.TrustReview))
        assertEquals("wallet.interaction.status.trust_review", model.titleKey)
        assertEquals(WalletScreenIntent.CONTINUE, model.primaryAction?.intent)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun partyReviewUsesStablePartyIdAndExplicitEncounterEvidence() {
        val party =
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.ISSUER,
                identifier = "https://issuer.example",
                partyId = "party-issuer",
            )
        val trust =
            WalletCounterpartyTrustSummary(
                counterparty = party,
                status = WalletTrustStatus.UNKNOWN,
                policyAction = WalletTrustPolicyAction.FIRST_CONTACT_PROMPT,
            )
        val firstState =
            state(WalletInteractionStatus.TrustReview).copy(
                counterparty = party,
                trust = trust,
                counterpartyEncounter =
                    WalletCounterpartyEncounterResult(
                        counterparty = party,
                        resolved = true,
                        organizationCreated = true,
                        firstInteraction = true,
                    ),
            )
        val returningState =
            firstState.copy(
                counterpartyEncounter =
                    WalletCounterpartyEncounterResult(
                        counterparty = party,
                        resolved = true,
                        organizationCreated = false,
                        firstInteraction = false,
                        previousInteractionCount = 2,
                        lastInteractionAtEpochSeconds = 123,
                    ),
            )

        val firstProjection = assertIs<WalletInteractionScreenProjection.PartyReview>(WalletScreenModelMapper.map(firstState).projection)
        val returningProjection = assertIs<WalletInteractionScreenProjection.PartyReview>(WalletScreenModelMapper.map(returningState).projection)
        val unresolvedParty = party.copy(partyId = null)
        val unresolvedProjection =
            assertIs<WalletInteractionScreenProjection.PartyReview>(
                WalletScreenModelMapper
                    .map(
                        firstState.copy(
                            counterparty = unresolvedParty,
                            trust = trust.copy(counterparty = unresolvedParty),
                            counterpartyEncounter = WalletCounterpartyEncounterResult.unresolved(unresolvedParty),
                        ),
                    ).projection,
            )

        assertEquals("party-issuer", firstProjection.party?.partyId)
        assertTrue(firstProjection.encounter?.firstInteraction == true)
        assertFalse(returningProjection.encounter?.firstInteraction == true)
        assertNull(unresolvedProjection.party)
        assertNull(unresolvedProjection.encounter)
    }

    @Test
    fun credentialOfferReviewDefersAcceptanceToTypedReceivePresenter() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.CredentialOfferReview))
        assertEquals("wallet.interaction.status.credential_offer_review", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals("wallet.interaction.action.decline", model.secondaryAction?.labelKey)
    }

    @Test
    fun authorizationRequiredDefersHandoffToTypedReceivePresenter() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.AuthorizationRequired))
        assertEquals("wallet.interaction.status.authorization_required", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun txCodeRequiredHasNoPrimaryButDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.TxCodeRequired))
        assertEquals("wallet.interaction.status.tx_code_required", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun credentialPreviewHasContinuePrimaryAndDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.CredentialPreview))
        assertEquals("wallet.interaction.status.credential_preview", model.titleKey)
        assertEquals(WalletScreenIntent.CONTINUE, model.primaryAction?.intent)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun credentialSelectionHasNoPrimaryButDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.CredentialSelection))
        assertEquals("wallet.interaction.status.credential_selection", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun disclosureConsentHasShareOrSignInPrimaryDependingOnActivity() {
        val share = WalletScreenModelMapper.map(state(WalletInteractionStatus.DisclosureConsent))
        assertEquals("wallet.interaction.action.share", share.primaryAction?.labelKey)
        assertEquals(WalletScreenIntent.CONTINUE, share.primaryAction?.intent)
        assertEquals(WalletScreenIntent.DECLINE, share.secondaryAction?.intent)

        val signIn =
            WalletScreenModelMapper.map(
                state(
                    WalletInteractionStatus.DisclosureConsent,
                    activity = WalletInteractionActivitySummary(WalletInteractionActivityType.LOGIN),
                ),
            )
        assertEquals("wallet.interaction.action.sign_in", signIn.primaryAction?.labelKey)
        assertEquals("wallet.interaction.login.status.disclosure_consent", signIn.titleKey)
    }

    @Test
    fun securityUnlockRequiredHasNoPrimaryButDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.SecurityUnlockRequired))
        assertEquals("wallet.interaction.status.security_unlock_required", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun deferredRetrievalPendingHasRetryPrimaryAndDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.DeferredRetrievalPending))
        assertEquals("wallet.interaction.status.deferred_retrieval_pending", model.titleKey)
        assertEquals(WalletScreenIntent.RETRY_DEFERRED_RETRIEVAL, model.primaryAction?.intent)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun sharingHasContinuePrimaryAndDeclineSecondary() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.Sharing))
        assertEquals("wallet.interaction.status.sharing", model.titleKey)
        assertEquals(WalletScreenIntent.CONTINUE, model.primaryAction?.intent)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun completedHasNoPrimaryAndDeclineSecondaryWhenNotYetTerminal() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.Completed))
        assertEquals("wallet.interaction.status.completed", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun cancelledHasNoPrimaryAndDeclineSecondaryWhenNotYetTerminal() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.Cancelled))
        assertEquals("wallet.interaction.status.cancelled", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun failedHasNoPrimaryAndDeclineSecondaryWhenNotYetTerminal() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.Failed))
        assertEquals("wallet.interaction.status.failed", model.titleKey)
        assertNull(model.primaryAction)
        assertEquals(WalletScreenIntent.DECLINE, model.secondaryAction?.intent)
    }

    @Test
    fun resultContainsOnlyAcceptedCredentialRecordsWithTheirResolvedBranding() {
        val offerPreview = WalletCredentialPreview(id = "configuration-id", name = "Offer preview")
        val accepted =
            WalletCredentialPreview(
                id = "credential-record-id",
                name = "Person Identification Data",
                branding =
                    WalletCredentialBranding(
                        credentialConfigurationId = "pid",
                        name = "Person Identification Data",
                        backgroundColor = "#003399",
                        textColor = "#FFFFFF",
                    ),
            )
        val model =
            WalletScreenModelMapper.map(
                state(WalletInteractionStatus.Failed, terminal = true).copy(
                    credentialPreview = listOf(offerPreview),
                    receivedCredentialPreview = listOf(accepted),
                ),
            )
        val result = assertIs<WalletInteractionScreenProjection.Result>(model.projection)

        assertEquals(listOf("credential-record-id"), result.receivedCredentials.map { it.credentialId })
        assertEquals("#003399", result.receivedCredentials.single().face.backgroundColor)
        assertEquals("#FFFFFF", result.receivedCredentials.single().face.textColor)
    }

    @Test
    fun failedIssuanceNeverTurnsOfferPreviewIntoReceivedCredential() {
        val model =
            WalletScreenModelMapper.map(
                state(WalletInteractionStatus.Failed, terminal = true).copy(
                    credentialPreview = listOf(WalletCredentialPreview(id = "configuration-id", name = "Offer preview")),
                ),
            )
        val result = assertIs<WalletInteractionScreenProjection.Result>(model.projection)

        assertEquals(emptyList(), result.receivedCredentials)
    }

    @Test
    fun terminalCredentialReceiveRetainsTheSafeTranscriptForResultRouteReattachment() {
        val issuer =
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.ISSUER,
                identifier = "https://issuer.example",
                partyId = "party-issuer",
                displayName = "Example issuer",
            )
        val terminal =
            state(WalletInteractionStatus.Failed, terminal = true).copy(
                counterparty = issuer,
                trust =
                    WalletCounterpartyTrustSummary(
                        counterparty = issuer,
                        status = WalletTrustStatus.TRUSTED,
                        policyAction = WalletTrustPolicyAction.ALLOW,
                    ),
                counterpartyEncounter =
                    WalletCounterpartyEncounterResult(
                        counterparty = issuer,
                        resolved = true,
                        organizationCreated = true,
                        firstInteraction = true,
                    ),
                credentialOffer =
                    WalletCredentialOfferSummary(
                        issuer = issuer,
                        credentialConfigurationIds = listOf("pid"),
                        branding = listOf(WalletCredentialBranding(credentialConfigurationId = "pid", name = "Person identification data")),
                    ),
            )

        val result = assertIs<WalletInteractionScreenProjection.Result>(WalletScreenModelMapper.map(terminal).projection)
        val context = requireNotNull(result.receiveContext)

        assertEquals("party-issuer", context.issuer.partyId)
        assertEquals("party-issuer", context.offer.issuer?.partyId)
        assertEquals(listOf("pid"), context.offer.offeredCredentials.map { it.configurationId })
        assertEquals(0, context.encounter.previousInteractionCount)
    }

    @Test
    fun terminalStateSuppressesSecondaryActionRegardlessOfStatus() {
        val model = WalletScreenModelMapper.map(state(WalletInteractionStatus.Completed, terminal = true))
        assertNull(model.secondaryAction)
    }

    @Test
    fun loginPresentationOverridesTitleKeysForCoveredStatuses() {
        val activity = WalletInteractionActivitySummary(WalletInteractionActivityType.LOGIN)
        assertEquals(
            "wallet.interaction.login.status.trust_review",
            WalletScreenModelMapper.map(state(WalletInteractionStatus.TrustReview, activity = activity)).titleKey,
        )
        assertEquals(
            "wallet.interaction.login.status.credential_selection",
            WalletScreenModelMapper.map(state(WalletInteractionStatus.CredentialSelection, activity = activity)).titleKey,
        )
        assertEquals(
            "wallet.interaction.login.status.security_unlock_required",
            WalletScreenModelMapper.map(state(WalletInteractionStatus.SecurityUnlockRequired, activity = activity)).titleKey,
        )
        assertEquals(
            "wallet.interaction.login.status.authorization_required",
            WalletScreenModelMapper.map(state(WalletInteractionStatus.AuthorizationRequired, activity = activity)).titleKey,
        )
        assertEquals(
            "wallet.interaction.login.status.signing_in",
            WalletScreenModelMapper.map(state(WalletInteractionStatus.Sharing, activity = activity)).titleKey,
        )
        assertEquals(
            "wallet.interaction.login.status.completed",
            WalletScreenModelMapper.map(state(WalletInteractionStatus.Completed, activity = activity)).titleKey,
        )
    }

    @Test
    fun displayMessageOverridesTitleAndSubtitle() {
        val withMessage =
            state(WalletInteractionStatus.CredentialOfferReview).copy(
                message =
                    WalletDisplayMessage(
                        titleKey = "wallet.interaction.custom.title",
                        textKey = "wallet.interaction.custom.subtitle",
                    ),
            )
        val model = WalletScreenModelMapper.map(withMessage)
        assertEquals("wallet.interaction.custom.title", model.titleKey)
        assertEquals("wallet.interaction.custom.subtitle", model.subtitleKey)
    }

    @Test
    fun errorMessageKeyIsUsedAsSubtitleWhenNoDisplayMessage() {
        val withError =
            state(WalletInteractionStatus.Failed, terminal = true).copy(
                error = WalletInteractionError(code = "boom", messageKey = "wallet.interaction.error.boom"),
            )
        val model = WalletScreenModelMapper.map(withError)
        assertEquals("wallet.interaction.error.boom", model.subtitleKey)
    }

    @Test
    fun mapIsPureAndRepeatableGivenOnlyAStateInput() {
        val input = state(WalletInteractionStatus.DisclosureConsent)
        assertEquals(WalletScreenModelMapper.map(input), WalletScreenModelMapper.map(input))
    }

    @Test
    fun screenModelRejectsHardcodedDisplayTextForUiChrome() {
        val input = state(WalletInteractionStatus.CredentialOfferReview)
        val mapped = WalletScreenModelMapper.map(input)
        assertFailsWith<IllegalArgumentException> {
            WalletScreenModel(
                sessionId = mapped.sessionId,
                revision = mapped.revision,
                flowKind = mapped.flowKind,
                status = mapped.status,
                titleKey = "invalid_title",
                projection = WalletInteractionScreenProjection.Progress(mapped.status),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WalletScreenAction(labelKey = "invalid_label", intent = WalletScreenIntent.CONTINUE)
        }
    }
}
