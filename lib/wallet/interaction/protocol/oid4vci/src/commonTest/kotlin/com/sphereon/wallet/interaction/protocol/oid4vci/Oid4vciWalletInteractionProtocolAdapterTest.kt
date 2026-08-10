/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.openid.oid4vci.common.model.AuthorizationCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.holder.AttestationChallengeResponse
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import com.sphereon.openid.oid4vci.holder.AuthorizationRequestResult
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialRefreshMethod
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.IssuanceProvenance
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.RefreshPolicy
import com.sphereon.wallet.credential.RefreshState
import com.sphereon.wallet.credential.SecretRef
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletCounterpartyAssociationCandidate
import com.sphereon.wallet.interaction.WalletCounterpartyAssociationDecision
import com.sphereon.wallet.interaction.WalletCounterpartyAssociationRequest
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRequest
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterResult
import com.sphereon.wallet.interaction.WalletCounterpartyTrustRequest
import com.sphereon.wallet.interaction.WalletCounterpartyTrustResolver
import com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary
import com.sphereon.wallet.interaction.WalletCredentialRequirement
import com.sphereon.wallet.interaction.WalletCredentialSelection
import com.sphereon.wallet.interaction.WalletCredentialSelectionRequest
import com.sphereon.wallet.interaction.WalletDisclosureSummary
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletNestedPresentationChallenge
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutionResult
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutor
import com.sphereon.wallet.interaction.WalletNestedPresentationRequest
import com.sphereon.wallet.interaction.WalletNestedPresentationResponse
import com.sphereon.wallet.interaction.WalletProtocolMatchStrength
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletSecurityContextAttributes
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletTrustPolicy
import com.sphereon.wallet.interaction.WalletTrustStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class Oid4vciWalletInteractionProtocolAdapterTest {
    @Test
    fun credentialOfferLinksAreStrongMatches() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured)

            val match = adapter.canHandle(WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret"))

            assertEquals(WalletProtocolMatchStrength.STRONG, match.strength)
        }

    @Test
    fun walletInitiatedIssuanceIsClaimedAndConvertedToAnAuthorizationCodeOffer() =
        runTest {
            val holder = RecordingOid4vciHolderService()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.ISSUE_PARSED_TYPE,
                    value =
                        buildJsonObject {
                            put("credentialIssuer", "https://issuer.example")
                            put(
                                "credentialConfigurationIds",
                                kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("pid"))),
                            )
                        },
                    source = "wallet",
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s-wallet-initiated"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )

            val match = adapter.canHandle(entryPoint)
            adapter.start(context, entryPoint)

            assertEquals(WalletProtocolMatchStrength.STRONG, match.strength)
            val parsedOffer = Json.decodeFromString<CredentialOffer>(requireNotNull(holder.lastRawOffer))
            assertEquals("https://issuer.example", parsedOffer.credentialIssuer)
            assertEquals(listOf("pid"), parsedOffer.credentialConfigurationIds)
            assertNotNull(parsedOffer.grants?.authorizationCode)
        }

    @Test
    fun malformedWalletInitiatedIssuanceFailsWithTypedError() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s-wallet-initiated-invalid"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.ISSUE_PARSED_TYPE,
                    value = buildJsonObject { put("credentialIssuer", "https://issuer.example") },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals("oid4vci.wallet_initiated_input_invalid", session.state.error?.code)
            assertTrue(session.state.terminal)
        }

    @Test
    fun digitalCredentialCreateRequestIsClaimedAndParsedAsCredentialOffer() =
        runTest {
            val holder = RecordingOid4vciHolderService()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.DIGITAL_CREDENTIAL_PROTOCOL,
                    value =
                        buildJsonObject {
                            put("credential_issuer", JsonPrimitive("https://issuer.example"))
                            put("credential_configuration_ids", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("pid"))))
                            put("credential_issuer_metadata", buildJsonObject { put("credential_issuer", JsonPrimitive("https://issuer.example")) })
                        },
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s-dc-api"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )

            val match = adapter.canHandle(entryPoint)
            adapter.start(context, entryPoint)

            assertEquals(WalletProtocolMatchStrength.STRONG, match.strength)
            assertTrue(requireNotNull(holder.lastRawOffer).startsWith("{"))
            assertTrue(requireNotNull(holder.lastRawOffer).contains("credential_issuer_metadata"))
        }

    @Test
    fun issuerDisplayMetadataProjectsLogoAndEveryLocaleIntoCounterparty() =
        runTest {
            val metadata =
                RecordingOid4vciHolderService.issuerMetadata().copy(
                    display =
                        listOf(
                            DisplayProperties(
                                name = "Example Issuer",
                                locale = "en-US",
                                logo = LogoProperties("https://issuer.example/assets/logo-en.png", "Example Issuer logo"),
                                description = "English issuer description",
                            ),
                            DisplayProperties(
                                name = "Voorbeeld-uitgever",
                                locale = "nl-NL",
                                logo = LogoProperties("https://issuer.example/assets/logo-nl.png", "Logo van de uitgever"),
                                description = "Nederlandse beschrijving",
                            ),
                        ),
                )
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = RecordingOid4vciHolderService(metadata = metadata),
                    issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("issuer-branding"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = RecordingPrivateSessionStore(),
                )

            val state = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer")).state

            assertEquals("Example Issuer", state.counterparty?.displayName)
            assertEquals("https://issuer.example/assets/logo-en.png", state.counterparty?.logoUri)
            assertEquals(listOf("en-US", "nl-NL"), state.counterparty?.localizedBranding?.map { it.locale })
            assertEquals(listOf("Example Issuer", "Voorbeeld-uitgever"), state.counterparty?.localizedBranding?.map { it.name })
            assertEquals(
                listOf("https://issuer.example/assets/logo-en.png", "https://issuer.example/assets/logo-nl.png"),
                state.counterparty?.localizedBranding?.map { it.logoUri },
            )
        }

    @Test
    fun resolvedIssuerEncounterPrecedesTrustAndControlsFirstInteractionReview() =
        runTest {
            val encounterRequests = mutableListOf<WalletCounterpartyEncounterRequest>()
            val callOrder = mutableListOf<String>()
            val encounterRegistry =
                object : WalletCounterpartyEncounterRegistry {
                    override suspend fun encounter(request: WalletCounterpartyEncounterRequest): WalletCounterpartyEncounterResult {
                        callOrder += "encounter"
                        val previousCount = encounterRequests.size.toLong()
                        encounterRequests += request
                        return WalletCounterpartyEncounterResult(
                            counterparty = request.counterparty.copy(partyId = "party-issuer"),
                            resolved = true,
                            organizationCreated = false,
                            firstInteraction = previousCount == 0L,
                            previousInteractionCount = previousCount,
                            lastInteractionAtEpochSeconds = if (previousCount == 0L) null else 100L,
                        )
                    }

                    override suspend fun resolveAssociation(request: WalletCounterpartyAssociationRequest): WalletCounterpartyEncounterResult =
                        error("unexpected_counterparty_association_resolution")
                }
            val trustCounterparties = mutableListOf<String?>()
            val trustResolver =
                object : WalletCounterpartyTrustResolver {
                    override suspend fun resolve(input: WalletCounterpartyTrustRequest): WalletCounterpartyTrustSummary {
                        callOrder += "trust"
                        trustCounterparties += input.counterparty.partyId
                        return WalletCounterpartyTrustSummary(input.counterparty, WalletTrustStatus.UNKNOWN)
                    }
                }
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = RecordingOid4vciHolderService(),
                    issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
                )

            val first =
                adapter.start(
                    WalletInteractionContext(
                        sessionId = WalletInteractionSessionId("first"),
                        walletUnitId = "wallet",
                        executionOwner = ProtocolExecutionOwner.WALLET_APP,
                        counterpartyEncounterRegistry = encounterRegistry,
                        trustResolver = trustResolver,
                        trustPolicy = WalletTrustPolicy.allow,
                    ),
                    WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=first"),
                ).state
            val returning =
                adapter.start(
                    WalletInteractionContext(
                        sessionId = WalletInteractionSessionId("returning"),
                        walletUnitId = "wallet",
                        executionOwner = ProtocolExecutionOwner.WALLET_APP,
                        counterpartyEncounterRegistry = encounterRegistry,
                        trustResolver = trustResolver,
                        trustPolicy = WalletTrustPolicy.allow,
                    ),
                    WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=returning"),
                ).state

            assertEquals(listOf("encounter", "trust", "encounter", "trust"), callOrder)
            assertEquals(2, encounterRequests.size)
            assertTrue(encounterRequests.all { it.walletUnitId == "wallet" && it.protocol == WalletProtocol.OID4VCI })
            assertEquals(listOf<String?>("party-issuer", "party-issuer"), trustCounterparties)
            assertEquals(WalletInteractionStatus.TrustReview, first.status)
            assertEquals("party-issuer", first.counterparty?.partyId)
            assertTrue(first.counterpartyEncounter?.firstInteraction == true)
            assertEquals(WalletInteractionStatus.CredentialOfferReview, returning.status)
            assertEquals(1L, returning.counterpartyEncounter?.previousInteractionCount)
            assertEquals(100L, returning.counterpartyEncounter?.lastInteractionAtEpochSeconds)
        }

    @Test
    fun issuerUrlAssociationCandidateRequiresResolutionBeforeTrustAndOfferReview() =
        runTest {
            var associationRequest: WalletCounterpartyAssociationRequest? = null
            val encounterRegistry =
                object : WalletCounterpartyEncounterRegistry {
                    override suspend fun encounter(request: WalletCounterpartyEncounterRequest): WalletCounterpartyEncounterResult =
                        WalletCounterpartyEncounterResult(
                            counterparty = request.counterparty.copy(partyId = "party-new-issuer"),
                            resolved = true,
                            organizationCreated = true,
                            firstInteraction = true,
                            associationCandidates =
                                listOf(
                                    WalletCounterpartyAssociationCandidate(
                                        partyId = "party-existing-organization",
                                        displayName = "Existing Organization",
                                        relatedHosts = listOf("issuer.example.com", "verifier.example.com"),
                                    ),
                                ),
                        )

                    override suspend fun resolveAssociation(request: WalletCounterpartyAssociationRequest): WalletCounterpartyEncounterResult {
                        associationRequest = request
                        return request.encounter.copy(
                            counterparty =
                                request.encounter.counterparty.copy(
                                    partyId = "party-existing-organization",
                                    displayName = "Existing Organization",
                                ),
                            associationCandidates = emptyList(),
                        )
                    }
                }
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("issuer-association"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    counterpartyEncounterRegistry = encounterRegistry,
                    trustPolicy = WalletTrustPolicy.allow,
                )
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = RecordingOid4vciHolderService(),
                    issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
                )

            val started =
                adapter.start(
                    context,
                    WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=issuer-association"),
                ).state

            assertEquals(WalletInteractionStatus.CounterpartyNotice, started.status)
            assertEquals("party-new-issuer", started.counterparty?.partyId)
            assertEquals(1, started.counterpartyEncounter?.associationCandidates?.size)

            val resolved =
                adapter.handle(
                    context,
                    started,
                    WalletInteractionAction.resolveCounterpartyContact(
                        WalletCounterpartyAssociationDecision.AssociateExisting("party-existing-organization"),
                    ),
                )

            assertEquals("wallet", associationRequest?.walletUnitId)
            assertEquals(WalletInteractionStatus.TrustReview, resolved.status)
            assertEquals("party-existing-organization", resolved.counterparty?.partyId)
            assertEquals("party-existing-organization", resolved.credentialOffer?.issuer?.partyId)
            assertTrue(resolved.counterpartyEncounter?.associationCandidates?.isEmpty() == true)
            assertEquals(WalletInteractionStatus.CredentialOfferReview, adapter.handle(context, resolved, WalletInteractionAction.continueFlow()).status)
        }

    @Test
    fun newlyCreatedIssuerWithoutAssociationCandidatesStillRequiresContactDecision() =
        runTest {
            val encounterRegistry =
                object : WalletCounterpartyEncounterRegistry {
                    override suspend fun encounter(request: WalletCounterpartyEncounterRequest): WalletCounterpartyEncounterResult =
                        WalletCounterpartyEncounterResult(
                            counterparty = request.counterparty.copy(partyId = "party-new-issuer"),
                            resolved = true,
                            organizationCreated = true,
                            firstInteraction = true,
                        )

                    override suspend fun resolveAssociation(request: WalletCounterpartyAssociationRequest): WalletCounterpartyEncounterResult =
                        request.encounter.copy(
                            counterparty = request.encounter.counterparty.copy(displayName = "My issuer contact"),
                        )
                }
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("new-issuer-without-candidates"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    counterpartyEncounterRegistry = encounterRegistry,
                    trustPolicy = WalletTrustPolicy.allow,
                )
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = RecordingOid4vciHolderService(),
                    issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
                )
            val started = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=new-issuer")).state

            assertEquals(WalletInteractionStatus.CounterpartyNotice, started.status)
            assertTrue(started.counterpartyEncounter?.associationCandidates?.isEmpty() == true)

            val resolved =
                adapter.handle(
                    context,
                    started,
                    WalletInteractionAction.resolveCounterpartyContact(
                        WalletCounterpartyAssociationDecision.KeepSeparate("My issuer contact"),
                    ),
                )

            assertEquals(WalletInteractionStatus.TrustReview, resolved.status)
            assertEquals("My issuer contact", resolved.counterparty?.displayName)
        }

    @Test
    fun startProjectsUiSafeOfferState() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured)
            val privateStore = RecordingPrivateSessionStore()
            val rawOffer = "openid-credential-offer://?credential_offer=pre-authorized_code-secret"
            val session =
                adapter.start(
                    WalletInteractionContext(
                        sessionId = WalletInteractionSessionId("s1"),
                        walletUnitId = "wallet",
                        executionOwner = ProtocolExecutionOwner.WALLET_APP,
                        privateSessionStore = privateStore,
                    ),
                    WalletEntryPoint.rawQr(rawOffer),
                )

            val encoded = Json.encodeToString(session.state)

            assertEquals(WalletInteractionStatus.CredentialOfferReview, session.state.status)
            assertEquals(rawOffer, privateStore.oid4vciState(session.state.sessionId).entryPointRaw)
            assertFalse(encoded.contains("pre-authorized_code-secret"))
        }

    @Test
    fun explicitUnavailableIssuanceExecutorDoesNotPretendCredentialWasReceived() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.Failed, next.status)
            assertEquals("oid4vci.execution_not_configured", next.error?.code)
            assertEquals(true, next.error?.retryable)
            assertFalse(next.terminal)
        }

    @Test
    fun holderProofAuthorizationCarriesWalletUnitSecurityContext() =
        runTest {
            val executor = RecordingIssuanceExecutor(Oid4vciIssuanceExecutionResult.Received())
            val securityGate = RecordingSecurityGate()
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    securityGate = securityGate,
                    attributes =
                        mapOf(
                            WalletSecurityContextAttributes.KEY_REF to "holder-proof-key",
                            WalletSecurityContextAttributes.WALLET_UNIT_ID to "wallet-unit-a",
                            WalletSecurityContextAttributes.WALLET_ACCOUNT_ID to "wallet-account-a",
                            WalletSecurityContextAttributes.ACTIVATION_DECISION_ID to "activation-a",
                            WalletSecurityContextAttributes.OPERATION_TYPE to "wallet.holder-proof",
                            WalletSecurityContextAttributes.OPERATION_HASH to "sha256:holder-proof",
                            WalletSecurityContextAttributes.NONCE to "nonce-a",
                        ),
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(WalletSecurityOperation.HOLDER_PROOF, securityGate.lastRequest?.operation)
            assertEquals("holder-proof-key", securityGate.lastRequest?.keyRef)
            assertEquals("wallet-unit-a", securityGate.lastRequest?.walletUnitId)
            assertEquals("wallet-account-a", securityGate.lastRequest?.walletAccountId)
            assertEquals("activation-a", securityGate.lastRequest?.activationDecisionId)
            assertEquals("wallet.holder-proof", securityGate.lastRequest?.operationType)
            assertEquals("sha256:holder-proof", securityGate.lastRequest?.operationHash)
            assertEquals("nonce-a", securityGate.lastRequest?.nonce)
        }

    @Test
    fun issuanceExecutorReceivedResultProjectsReviewWithoutLeakingTxCode() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val acceptedPreview = WalletCredentialPreview(id = "stored-credential-1", name = "Stored credential")
            val executor = RecordingIssuanceExecutor(Oid4vciIssuanceExecutionResult.Received(listOf(acceptedPreview)))
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(1, executor.requestCalls)
            assertEquals(listOf(acceptedPreview), next.receivedCredentialPreview)
            assertEquals(null, privateStore.oid4vciState(next.sessionId).txCode)
            assertFalse(encoded.contains("tx-secret"))
        }

    @Test
    fun issuanceExecutorDeferredResultProjectsDeferredRetrievalState() =
        runTest {
            val executor =
                RecordingIssuanceExecutor(
                    Oid4vciIssuanceExecutionResult.Deferred(
                        intervalSeconds = 5,
                        attempt = 1,
                    ),
                )
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.DeferredRetrievalPending, next.status)
            assertEquals(5, next.deferred?.intervalSeconds)
            assertTrue(next.deferred?.resumable == true)
        }

    @Test
    fun holderIssuanceExecutorRequestsCredentialAndKeepsSecretsPrivate() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val dpopProofsProvider = RecordingDeferredDpopProofsProvider()
            val holder = RecordingOid4vciHolderService(notificationEndpointDpopNonceChallenge = "notification-dpop-nonce")
            val receiver = RecordingCredentialResponseReceiver()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                            tokenEndpointProofsProvider = dpopProofsProvider,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(null, holder.exchangedTxCode)
            assertEquals("access-token-secret", holder.requestedAccessToken)
            assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, holder.notificationEvent)
            assertEquals(
                listOf<String?>("notification-dpop-initial", "notification-dpop-nonce-notification-dpop-nonce"),
                holder.notificationDpopProofJwtHistory,
            )
            assertEquals("https://issuer.example/notification", dpopProofsProvider.requestedUrls.last())
            assertEquals("notification-dpop-nonce", dpopProofsProvider.requestedNonces.last())
            assertEquals("credential-secret", receiver.receivedCredentialValue)
            assertFalse(encoded.contains("tx-secret"))
            assertFalse(encoded.contains("access-token-secret"))
            assertFalse(encoded.contains("proof-secret"))
            assertFalse(encoded.contains("credential-secret"))
        }

    @Test
    fun holderIssuanceExecutorUsesExactMetadataCredentialIssuerIdentifierForProofAudience() =
        runTest {
            val metadataIssuer = "https://issuer.example/tenant"
            val holder =
                RecordingOid4vciHolderService(
                    offer =
                        RecordingOid4vciHolderService
                            .preAuthorizedOffer()
                            .copy(credentialIssuer = "$metadataIssuer/"),
                    metadata =
                        RecordingOid4vciHolderService
                            .issuerMetadata()
                            .copy(credentialIssuer = metadataIssuer),
                )
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = RecordingCredentialResponseReceiver(),
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = RecordingPrivateSessionStore(),
                )

            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))
            val result = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.Completed, result.status)
            assertEquals(metadataIssuer, holder.receivedCredentialProofIssuerUrl)
        }

    // ---------------------------------------------------------------------------------------
    // KA-on-demand: the executor attaches a key attestation to the credential-request
    // proof only when the resolved credential configuration's `jwt` proof type declares
    // key_attestations_required, obtaining it via the injected Oid4vciKeyAttestationProvider.
    // ---------------------------------------------------------------------------------------

    @Test
    fun holderIssuanceExecutorAttachesKeyAttestationWhenCredentialConfigurationRequiresIt() =
        runTest {
            val holder = RecordingOid4vciHolderService(metadata = RecordingOid4vciHolderService.metadataRequiringKeyAttestation())
            val receiver = RecordingCredentialResponseReceiver()
            val keyAttestationProvider = FixedOid4vciKeyAttestationProvider()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = keyAttestationProvider,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = RecordingPrivateSessionStore(),
                )

            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))
            adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals("key-attestation-jwt-secret", holder.receivedKeyAttestationJwt)
            assertEquals("wallet", keyAttestationProvider.lastRequest?.walletUnitId)
            assertEquals("test:oid4vci-operation", keyAttestationProvider.lastRequest?.operationBinding)
            assertEquals("key-1", keyAttestationProvider.lastRequest?.signingKeyId)
            assertNotNull(keyAttestationProvider.lastRequest?.requirement, "the parsed key_attestations_required policy must reach the provider")
        }

    @Test
    fun holderIssuanceExecutorDoesNotAttachAKeyAttestationWhenNotRequired() =
        runTest {
            val holder = RecordingOid4vciHolderService()
            val receiver = RecordingCredentialResponseReceiver()
            val keyAttestationProvider = FixedOid4vciKeyAttestationProvider()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = keyAttestationProvider,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = RecordingPrivateSessionStore(),
                )

            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))
            adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(null, holder.receivedKeyAttestationJwt, "the wallet must never attach a key attestation uninvited")
            assertEquals(null, keyAttestationProvider.lastRequest, "an unrequired key attestation must never even be requested")
        }

    @Test
    fun holderIssuanceExecutorFailsClosedWhenKeyAttestationIsRequiredButUnsupported() =
        runTest {
            val holder = RecordingOid4vciHolderService(metadata = RecordingOid4vciHolderService.metadataRequiringKeyAttestation())
            val receiver = RecordingCredentialResponseReceiver()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = RecordingPrivateSessionStore(),
                )

            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))
            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.Failed, next.status)
            assertEquals("oid4vci.key_attestation_failed", next.error?.code)
            assertEquals(null, holder.receivedKeyAttestationJwt)
        }

    @Test
    fun holderIssuanceExecutorForwardsHaipTokenProofsToTokenExchange() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder = RecordingOid4vciHolderService()
            val receiver = RecordingCredentialResponseReceiver()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider =
                                StaticIssuanceOptionsProvider(
                                    haipTokenProofs =
                                        Oid4vciHaipTokenProofOptions(
                                            walletUnitId = "wallet-unit",
                                            walletAccountId = "wallet-account",
                                            walletName = "VDX Test Wallet",
                                            walletVersion = "1.0.0",
                                        ),
                                ),
                            credentialReceiver = receiver,
                            tokenEndpointProofsProvider =
                                FixedTokenEndpointProofsProvider(
                                    tokenProofs =
                                        Oid4vciTokenEndpointProofs(
                                            dpopProofJwt = "dpop.jwt",
                                            clientAttestationJwt = "attestation.jwt",
                                            clientAttestationPopJwt = "pop.jwt",
                                            clientAuthentication =
                                                ClientAuthenticationConfig.PrivateKeyJwt(
                                                    ClientAssertion(
                                                        clientId = "wallet-client",
                                                        assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                                                        assertion = "client.assertion.jwt",
                                                    ),
                                                ),
                                        ),
                                    credentialDpopProof = "credential-dpop.jwt",
                                ),
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals("dpop.jwt", holder.exchangedDpopProofJwt)
            assertEquals("attestation.jwt", holder.exchangedClientAttestationJwt)
            assertEquals("pop.jwt", holder.exchangedClientAttestationPopJwt)
            val auth = holder.exchangedClientAuthentication as ClientAuthenticationConfig.PrivateKeyJwt
            assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", auth.assertion.assertionType)
            assertEquals("client.assertion.jwt", auth.assertion.assertion)
            assertEquals("credential-dpop.jwt", holder.requestedDpopProofJwt)
        }

    @Test
    fun holderIssuanceExecutorRegeneratesTokenProofsForDpopNonceRetry() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder = RecordingOid4vciHolderService(tokenEndpointDpopNonceChallenge = "as-dpop-nonce")
            val receiver = RecordingCredentialResponseReceiver()
            val tokenProofsProvider = RotatingTokenEndpointProofsProvider()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            tokenEndpointProofsProvider = tokenProofsProvider,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(2, holder.exchangedDpopProofJwtHistory.size)
            assertEquals(listOf<String?>("token-dpop-1", "token-dpop-2-nonce-as-dpop-nonce"), holder.exchangedDpopProofJwtHistory)
            val assertions =
                holder.exchangedClientAuthenticationHistory.map {
                    (it as ClientAuthenticationConfig.PrivateKeyJwt).assertion.assertion
                }
            assertEquals(listOf("client-assertion-1", "client-assertion-2"), assertions)
            assertEquals(listOf<String?>(null, "as-dpop-nonce"), tokenProofsProvider.requestedNonces)
        }

    @Test
    fun holderIssuanceExecutorPrefersDcSdJwtCredentialConfiguration() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example",
                    credentialConfigurationIds = listOf("w3c-sd-jwt", "identity"),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-authorized-secret",
                                ),
                        ),
                )
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example",
                    credentialEndpoint = "https://issuer.example/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "w3c-sd-jwt" to CredentialConfigurationSupported(format = "vc+sd-jwt"),
                            "identity" to CredentialConfigurationSupported(format = "dc+sd-jwt"),
                        ),
                )
            val holder = RecordingOid4vciHolderService(offer = offer, metadata = metadata)
            val privateStore = RecordingPrivateSessionStore()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(credentialConfigurationId = null),
                            credentialReceiver = RecordingCredentialResponseReceiver(),
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals("identity", holder.requestedCredentialConfigurationId)
        }

    @Test
    fun holderIssuanceExecutorPollsDeferredCredentialOnRetry() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val dpopProofsProvider = RecordingDeferredDpopProofsProvider()
            val holder =
                RecordingOid4vciHolderService(
                    deferredEndpointDpopNonceChallenge = "deferred-dpop-nonce",
                    credentialResponse =
                        CredentialResponse(
                            transactionId = "deferred-transaction-secret",
                            interval = 3,
                        ),
                    deferredCredentialResponse =
                        CredentialResponse(
                            credentials = listOf(CredentialResponseItem(JsonPrimitive("deferred-credential-secret"))),
                            notificationId = "deferred-notification-secret",
                        ),
                )
            val receiver = RecordingCredentialResponseReceiver()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                            tokenEndpointProofsProvider = dpopProofsProvider,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val deferred = adapter.handle(context, session.state, session.state.acceptOfferAction())
            val retrieved =
                adapter.handle(
                    context,
                    deferred,
                    WalletInteractionAction.retryDeferredRetrieval(),
                )
            val deferredEncoded = Json.encodeToString(deferred)
            val retrievedEncoded = Json.encodeToString(retrieved)

            assertEquals(WalletInteractionStatus.DeferredRetrievalPending, deferred.status)
            assertEquals(3, deferred.deferred?.intervalSeconds)
            assertEquals(WalletInteractionStatus.Completed, retrieved.status)
            assertEquals("deferred-transaction-secret", holder.deferredTransactionId)
            assertEquals("access-token-secret", holder.deferredAccessToken)
            assertEquals(
                listOf<String?>("deferred-dpop-initial", "deferred-dpop-nonce-deferred-dpop-nonce"),
                holder.deferredDpopProofJwtHistory,
            )
            assertEquals(
                listOf(
                    "https://issuer.example/credential",
                    "https://issuer.example/deferred",
                    "https://issuer.example/deferred",
                    "https://issuer.example/notification",
                ),
                dpopProofsProvider.requestedUrls,
            )
            assertEquals(listOf<String?>(null, null, "deferred-dpop-nonce", null), dpopProofsProvider.requestedNonces)
            assertEquals("deferred-credential-secret", receiver.receivedCredentialValue)
            assertFalse(deferredEncoded.contains("deferred-transaction-secret"))
            assertFalse(deferredEncoded.contains("access-token-secret"))
            assertFalse(retrievedEncoded.contains("deferred-credential-secret"))
        }

    @Test
    fun blankTransactionIdDoesNotPersistDeferredLegAndFailsFastOnRetrieval() =
        runTest {
            // A credential response carrying a BLANK (non-null, empty string) transaction_id
            // alongside a valid deferred endpoint must not produce a stored DeferredLeg, and the
            // deferred-retrieval path must fail fast rather than send an empty transaction id to
            // the issuer. Proves the executor guard at handleCredentialResponse
            // (`!deferredCredentialEndpoint.isNullOrBlank() && transactionId.isNotBlank()`)
            // end-to-end through the adapter.
            val privateStore = RecordingPrivateSessionStore()
            val holder =
                RecordingOid4vciHolderService(
                    credentialResponse =
                        CredentialResponse(
                            transactionId = "",
                            interval = 3,
                        ),
                )
            val receiver = RecordingCredentialResponseReceiver()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val deferred = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.DeferredRetrievalPending, deferred.status)
            assertEquals(3, deferred.deferred?.intervalSeconds)
            assertEquals(null, privateStore.oid4vciState(deferred.sessionId).deferred, "a blank transaction_id must not persist a DeferredLeg")

            val retried = adapter.handle(context, deferred, WalletInteractionAction.retryDeferredRetrieval())

            assertEquals(WalletInteractionStatus.Failed, retried.status)
            assertEquals("oid4vci.deferred_endpoint_missing", retried.error?.code)
            assertEquals(null, holder.deferredTransactionId, "the holder must never be asked to retrieve with a blank transaction id")
        }

    @Test
    fun holderIssuanceExecutorBuildsAuthorizationRedirectForAuthorizationCodeGrant() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder =
                RecordingOid4vciHolderService(
                    offer = RecordingOid4vciHolderService.authorizationCodeOffer(),
                    requirePar = true,
                )
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = RecordingCredentialResponseReceiver(),
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=auth-code"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.AuthorizationRequired, next.status)
            val handoffRef = assertNotNull(next.authorizationHandoffRef)
            assertEquals(
                "https://as.example/authorize?request=123",
                context.sensitiveInputAuthority.consume(
                    next.sessionId,
                    WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_HANDOFF,
                    handoffRef,
                ),
            )
            assertEquals(
                null,
                context.sensitiveInputAuthority.consume(
                    next.sessionId,
                    WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_HANDOFF,
                    handoffRef,
                ),
            )
            assertEquals("code-verifier-secret", privateStore.oid4vciState(next.sessionId).authorization?.codeVerifier)
            assertEquals(true, holder.authorizationRequestUsePar)
            assertEquals("https://as.example/par", holder.authorizationRequestParEndpoint)
            assertEquals("identity", holder.authorizationRequestScope)
            assertFalse(encoded.contains("code-verifier-secret"))

            val afterCallback =
                adapter.handle(
                    context,
                    next,
                    context.authorizationCallbackAction("wallet://callback?code=authorization-code-secret&state=oauth-state"),
                )
            val afterCallbackEncoded = Json.encodeToString(afterCallback)

            assertEquals(WalletInteractionStatus.Completed, afterCallback.status)
            assertEquals("authorization-code-secret", holder.exchangedAuthorizationCode)
            assertEquals("code-verifier-secret", holder.exchangedCodeVerifier)
            assertEquals("access-token-from-code-secret", holder.requestedAccessToken)
            assertFalse(afterCallbackEncoded.contains("authorization-code-secret"))
            assertFalse(afterCallbackEncoded.contains("code-verifier-secret"))
            assertFalse(afterCallbackEncoded.contains("access-token-from-code-secret"))
        }

    @Test
    fun holderIssuanceExecutorAuthenticatesParAndRegeneratesProofsForDpopNonceRetry() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder =
                RecordingOid4vciHolderService(
                    offer = RecordingOid4vciHolderService.authorizationCodeOffer(),
                    requirePar = true,
                    parEndpointDpopNonceChallenge = "par-dpop-nonce",
                )
            val proofsProvider = RotatingTokenEndpointProofsProvider()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = RecordingCredentialResponseReceiver(),
                            tokenEndpointProofsProvider = proofsProvider,
                            nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=auth-code"))

            val next = adapter.handle(context, session.state, session.state.acceptOfferAction())

            assertEquals(WalletInteractionStatus.AuthorizationRequired, next.status)
            assertEquals(listOf<String?>("token-dpop-1", "token-dpop-2-nonce-par-dpop-nonce"), holder.authorizationRequestDpopProofJwtHistory)
            val assertions =
                holder.authorizationRequestClientAuthenticationHistory.map {
                    (it as ClientAuthenticationConfig.PrivateKeyJwt).assertion.assertion
                }
            assertEquals(listOf("client-assertion-1", "client-assertion-2"), assertions)
            assertEquals(listOf<String?>(null, "par-dpop-nonce"), proofsProvider.requestedNonces)
        }

    @Test
    fun holderIssuanceExecutorRunsIaeNestedPresentationWithoutLeakingSecrets() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder =
                RecordingOid4vciHolderService(
                    offer = RecordingOid4vciHolderService.authorizationCodeOffer(),
                    interactiveAuthorization = true,
                )
            val nestedExecutor = RecordingNestedPresentationExecutor()
            val receiver = RecordingCredentialResponseReceiver()
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            nestedPresentationExecutor = nestedExecutor,
                            credentialStore = RecordingWalletCredentialStore(existingRecord = null),
                            issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                            refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                            keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=iae"))

            val challenge = adapter.handle(context, session.state, session.state.acceptOfferAction())
            val completed =
                adapter.handle(
                    context,
                    challenge,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("presentation-cred-1"))),
                    ),
                )
            val challengeEncoded = Json.encodeToString(challenge)
            val completedEncoded = Json.encodeToString(completed)

            assertEquals(WalletInteractionStatus.CredentialSelection, challenge.status)
            assertEquals("auth-session-secret", privateStore.oid4vciState(challenge.sessionId).iae?.authSession)
            assertEquals("https://as.example/iae", holder.lastInitiateIaeArgs?.iaeEndpoint)
            assertEquals("auth-session-secret", holder.lastFollowUpIaeArgs?.authSession)
            assertEquals(
                "vp-token-secret",
                holder.lastFollowUpIaeArgs
                    ?.openid4vpResponse
                    ?.get("vp_token")
                    ?.toString()
                    ?.trim('"')
            )
            assertEquals("iae-authorization-code-secret", holder.exchangedAuthorizationCode)
            assertEquals("iae-code-verifier-secret", holder.exchangedCodeVerifier)
            assertEquals("access-token-from-code-secret", holder.requestedAccessToken)
            assertEquals("credential-secret", receiver.receivedCredentialValue)
            assertEquals(mapOf("identity" to listOf("presentation-cred-1")), nestedExecutor.lastSelection?.selectedCredentialIdsByRequirement)
            assertEquals(WalletInteractionStatus.Completed, completed.status)
            listOf(
                "auth-session-secret",
                "vp-token-secret",
                "iae-code-verifier-secret",
                "access-token-from-code-secret",
                "credential-secret",
            ).forEach { forbidden ->
                assertFalse(challengeEncoded.contains(forbidden), "Challenge state leaked $forbidden")
                assertFalse(completedEncoded.contains(forbidden), "Completed state leaked $forbidden")
            }
        }

    // -----------------------------------------------------------------------------------------
    // Wallet-initiated credential refresh entry point.
    // -----------------------------------------------------------------------------------------

    @Test
    fun refreshParsedEntryPointIsAStrongMatch() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured)

            val match =
                adapter.canHandle(
                    WalletEntryPoint.parsed(
                        type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                        value = buildJsonObject { put("credentialRecordId", "cred-record-1") },
                        source = "wallet",
                    ),
                )

            assertEquals(WalletProtocolMatchStrength.STRONG, match.strength)
        }

    @Test
    fun refreshEntryPointWithoutCredentialRecordIdFailsFastWithATypedError() =
        runTest {
            // An explicitly unavailable test executor proves the adapter's own entry-point
            // validation rejects a malformed refresh payload before touching the executor.
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = Oid4vciIssuanceExecutor.notConfigured)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                    value = buildJsonObject { },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals("oid4vci.refresh_credential_record_id_missing", session.state.error?.code)
            assertTrue(session.state.terminal)
        }

    @Test
    fun refreshEntryPointDrivesSessionToTerminalCompletedOnSuccessReusingTheExistingHolderKey() =
        runTest {
            val existingRecord = refreshableRecord()
            val credentialStore = RecordingWalletCredentialStore(existingRecord)
            val issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = "stored-refresh-token")
            val grantProvider =
                FixedOid4vciRefreshTokenGrantProvider(
                    Ok(
                        TokenResponseWithContext(
                            accessToken = "refreshed-access-token",
                            tokenType = "Bearer",
                            authorizationDetails =
                                listOf(
                                    buildJsonObject {
                                        put("type", "openid_credential")
                                        put("credential_configuration_id", "identity")
                                        put("credential_identifiers", buildJsonArray { add("reminted-identity-identifier") })
                                    },
                                ),
                        ),
                    ),
                )
            val holder = RecordingOid4vciHolderService()
            val receiver = RecordingCredentialResponseReceiver()
            val executor =
                Oid4vciHolderIssuanceExecutor(
                    holder = holder,
                    credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                    optionsProvider = StaticIssuanceOptionsProvider(),
                    credentialReceiver = receiver,
                    nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                    credentialStore = credentialStore,
                    issuanceSessionStore = issuanceSessionStore,
                    refreshTokenGrantProvider = grantProvider,
                    keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                )
            val adapter = Oid4vciWalletInteractionProtocolAdapter(holder = holder, issuanceExecutor = executor)
            val privateStore = RecordingPrivateSessionStore()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                    privateSessionStore = privateStore,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                    value = buildJsonObject { put("credentialRecordId", existingRecord.id) },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Completed, session.state.status)
            assertTrue(session.state.terminal)
            assertEquals("credential-secret", receiver.receivedCredentialValue)
            assertEquals("refreshed-access-token", holder.requestedAccessToken)
            assertEquals("reminted-identity-identifier", holder.requestedCredentialIdentifier)
            assertEquals(null, holder.requestedCredentialConfigurationId)
            assertEquals("stored-refresh-token", grantProvider.lastRequest?.refreshToken, "the STORED refresh token must be used, not a caller-supplied one")
            // REUSE, not mint: the existing active instance's holder key must be the one presented on
            // reissuance, never a fresh key from the (unlinkability-minting) options provider.
            assertEquals(
                listOf("wallet-holder-key-refresh"),
                privateStore.oid4vciState(session.state.sessionId).holderKeyAliases,
            )
        }

    @Test
    fun refreshEntryPointFailsWithTypedErrorWhenCredentialRecordIsUnknown() =
        runTest {
            val credentialStore = RecordingWalletCredentialStore(existingRecord = null)
            val holder = RecordingOid4vciHolderService()
            val executor =
                Oid4vciHolderIssuanceExecutor(
                    holder = holder,
                    credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                    optionsProvider = StaticIssuanceOptionsProvider(),
                    credentialReceiver = RecordingCredentialResponseReceiver(),
                    nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                    credentialStore = credentialStore,
                    issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = "unused"),
                    refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                    keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                )
            val adapter = Oid4vciWalletInteractionProtocolAdapter(holder = holder, issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                    value = buildJsonObject { put("credentialRecordId", "unknown-record") },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals("oid4vci.refresh_record_not_found", session.state.error?.code)
            assertTrue(session.state.terminal)
        }

    @Test
    fun refreshEntryPointFailsWithTypedErrorWhenRefreshTokenIsMissing() =
        runTest {
            val existingRecord = refreshableRecord()
            val holder = RecordingOid4vciHolderService()
            val executor =
                Oid4vciHolderIssuanceExecutor(
                    holder = holder,
                    credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                    optionsProvider = StaticIssuanceOptionsProvider(),
                    credentialReceiver = RecordingCredentialResponseReceiver(),
                    nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                    credentialStore = RecordingWalletCredentialStore(existingRecord),
                    issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = null),
                    refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                    keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                )
            val adapter = Oid4vciWalletInteractionProtocolAdapter(holder = holder, issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                    value = buildJsonObject { put("credentialRecordId", existingRecord.id) },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals("oid4vci.refresh_token_missing", session.state.error?.code)
            assertTrue(session.state.terminal)
        }

    @Test
    fun refreshEntryPointFailsWithTypedErrorWhenRefreshMethodIsUnsupported() =
        runTest {
            val existingRecord = refreshableRecord(refreshMethod = CredentialRefreshMethod.MANUAL_IMPORT)
            val holder = RecordingOid4vciHolderService()
            val executor =
                Oid4vciHolderIssuanceExecutor(
                    holder = holder,
                    credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                    optionsProvider = StaticIssuanceOptionsProvider(),
                    credentialReceiver = RecordingCredentialResponseReceiver(),
                    nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                    credentialStore = RecordingWalletCredentialStore(existingRecord),
                    issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = "stored-refresh-token"),
                    refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                    keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                )
            val adapter = Oid4vciWalletInteractionProtocolAdapter(holder = holder, issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                    value = buildJsonObject { put("credentialRecordId", existingRecord.id) },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals("oid4vci.refresh_method_unsupported", session.state.error?.code)
            assertTrue(session.state.terminal)
        }

    @Test
    fun refreshEntryPointFailsNonTerminallyWhenNoRefreshTokenGrantProviderIsConfigured() =
        runTest {
            // A fully valid refresh (known record, OID4VCI_REISSUANCE, a stored refresh token) with
            // NO wired grant provider (the wallet-runner's current default, see WalletRunnerDi) must
            // still degrade to a typed, RETRYABLE failure - never an exception, never a silently
            // dropped refresh.
            val existingRecord = refreshableRecord()
            val holder = RecordingOid4vciHolderService()
            val executor =
                Oid4vciHolderIssuanceExecutor(
                    holder = holder,
                    credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                    optionsProvider = StaticIssuanceOptionsProvider(),
                    credentialReceiver = RecordingCredentialResponseReceiver(),
                    nestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
                    credentialStore = RecordingWalletCredentialStore(existingRecord),
                    issuanceSessionStore = RecordingWalletIssuanceSessionStore(refreshToken = "stored-refresh-token"),
                    refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                    keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                )
            val adapter = Oid4vciWalletInteractionProtocolAdapter(holder = holder, issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletUnitId = "wallet",
                    executionOwner = ProtocolExecutionOwner.WALLET_APP,
                )
            val entryPoint =
                WalletEntryPoint.parsed(
                    type = Oid4vciWalletInteractionProtocolAdapter.REFRESH_PARSED_TYPE,
                    value = buildJsonObject { put("credentialRecordId", existingRecord.id) },
                    source = "wallet",
                )

            val session = adapter.start(context, entryPoint)

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals("oid4vci.refresh_token_exchange_failed", session.state.error?.code)
            assertEquals(true, session.state.error?.retryable)
            assertFalse(session.state.terminal, "a retryable failure must not be terminal")
        }
}

private class RecordingPrivateSessionStore : WalletInteractionPrivateSessionStore {
    private val records = mutableMapOf<Pair<WalletInteractionSessionId, String>, WalletInteractionPrivateSessionData>()

    override suspend fun put(
        sessionId: WalletInteractionSessionId,
        data: WalletInteractionPrivateSessionData,
    ) {
        records[sessionId to data.namespace] = data
    }

    override suspend fun get(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ): WalletInteractionPrivateSessionData? = records[sessionId to namespace]

    override suspend fun remove(
        sessionId: WalletInteractionSessionId,
        namespace: String,
    ) {
        records.remove(sessionId to namespace)
    }

    override suspend fun removeSession(sessionId: WalletInteractionSessionId) {
        val keys = records.keys.filter { it.first == sessionId }
        keys.forEach { records.remove(it) }
    }
}

/**
 * Test-only decode of the single typed OID4VCI private session state blob, mirroring the
 * production [oid4vciState] extension but reading from a [RecordingPrivateSessionStore] the
 * test already holds a direct reference to (rather than a [WalletInteractionContext]).
 */
private suspend fun RecordingPrivateSessionStore.oid4vciState(sessionId: WalletInteractionSessionId): Oid4vciPrivateSessionState {
    val raw = get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("state") ?: return Oid4vciPrivateSessionState()
    return Json.decodeFromString(Oid4vciPrivateSessionState.serializer(), raw)
}

/**
 * Builds a wallet-holding-already-issued-credential fixture suitable for
 * [Oid4vciHolderIssuanceExecutor.refreshCredential]: one ACTIVE instance carrying a holder key
 * DISTINCT from anything an issuance options provider would mint, so REUSE-vs-mint assertions are
 * unambiguous, plus `issuanceProvenance` matching [RecordingOid4vciHolderService]'s default fixture
 * (issuer `https://issuer.example`, credential configuration id `identity`) so the fake holder
 * service's canned responses line up without per-test overrides.
 */
private fun refreshableRecord(
    id: String = "cred-record-refresh-1",
    holderKeyAlias: String = "wallet-holder-key-refresh",
    refreshMethod: CredentialRefreshMethod = CredentialRefreshMethod.OID4VCI_REISSUANCE,
): CredentialRecord {
    val now = Clock.System.now()
    val instance =
        CredentialInstance(
            id = "$id-instance-1",
            walletUnitId = "wallet",
            credentialRecordId = id,
            format = CredentialFormat.SD_JWT_VC,
            raw = "existing-credential-body",
            bodyStorageRef = BodyStorageRef(kind = BodyStorageKind.WALLET_STORE, path = "wallet-units/wallet/credentials/$id/instances/$id-instance-1/body"),
            holderKeyRef = KeyRef(alias = holderKeyAlias),
            lifecycleState = CredentialLifecycleState.ACTIVE,
            storedAt = now,
            updatedAt = now,
        )
    return CredentialRecord(
        id = id,
        walletUnitId = "wallet",
        issuerRef = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example"),
        format = CredentialFormat.SD_JWT_VC,
        credentialTypeRefs =
            setOf(
                CredentialTypeRef(
                    format = CredentialFormat.SD_JWT_VC,
                    kind = CredentialTypeRefKind.SD_JWT_VCT,
                    value = "https://credentials.example.com/employee",
                    source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                    primary = true,
                ),
            ),
        instances = listOf(instance),
        issuanceProvenance = IssuanceProvenance(credentialIssuerUrl = "https://issuer.example", credentialConfigurationId = "identity", issuedAt = now),
        refreshState = RefreshState(refreshMethod = refreshMethod, policy = RefreshPolicy()),
        createdAt = now,
        updatedAt = now,
    )
}

/**
 * Minimal in-memory [WalletCredentialStore] fake: seeded with (at most) one record, returned by id
 * from [getCredential]. `putCredential`/other reads are not exercised by the refresh tests (storage
 * on success goes through [RecordingCredentialResponseReceiver], a completely separate fake), so
 * they are implemented but not asserted on.
 */
private class RecordingWalletCredentialStore(
    private var existingRecord: CredentialRecord?,
) : WalletCredentialStore {
    override suspend fun putCredential(
        walletUnitId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError> {
        existingRecord = record
        return Ok(record).asResult()
    }

    override suspend fun getCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError> = Ok(existingRecord?.takeIf { it.id == credentialRecordId }).asResult()

    override suspend fun getMetadata(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError> = Ok(null).asResult()

    override suspend fun listMetadata(
        walletUnitId: String,
        filter: CredentialMetadataFilter,
    ): IdkResult<List<CredentialMetadata>, IdkError> = Ok(emptyList<CredentialMetadata>()).asResult()

    override suspend fun findByCredentialTypeRef(
        walletUnitId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError> = Ok(emptyList<CredentialMetadata>()).asResult()

    override suspend fun deleteCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError> = Ok(false).asResult()
}

/** Minimal [WalletIssuanceSessionStore] fake returning a fixed refresh token for every lookup. */
private class RecordingWalletIssuanceSessionStore(
    private val refreshToken: String?,
) : WalletIssuanceSessionStore {
    override suspend fun putSession(
        walletUnitId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> = Ok(session).asResult()

    override suspend fun getSession(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> = Ok(null).asResult()

    override suspend fun listSessions(
        walletUnitId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> = Ok(emptyList<IssuanceSession>()).asResult()

    override suspend fun storeDeferredAccessToken(
        walletUnitId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> = Err(IdkError.fromString(code = "FAKE_UNSUPPORTED", message = "Not used by adapter refresh tests")).asResult()

    override suspend fun getDeferredAccessToken(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> = Ok(null).asResult()

    override suspend fun storeRefreshToken(
        walletUnitId: String,
        credentialRecordId: String,
        refreshToken: String,
    ): IdkResult<SecretRef, IdkError> = Ok(SecretRef(id = "secret:test")).asResult()

    override suspend fun getRefreshToken(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<String?, IdkError> = Ok(refreshToken).asResult()

    override suspend fun deleteSession(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> = Ok(true).asResult()
}

/** Records the last refresh-token-grant request and returns a fixed, caller-supplied response. */
private class FixedOid4vciRefreshTokenGrantProvider(
    private val response: IdkResult<TokenResponseWithContext, IdkError>,
) : Oid4vciRefreshTokenGrantProvider {
    var lastRequest: Oid4vciRefreshTokenGrantRequest? = null

    override suspend fun exchangeRefreshToken(request: Oid4vciRefreshTokenGrantRequest): IdkResult<TokenResponseWithContext, IdkError> {
        lastRequest = request
        return response
    }
}

private class RecordingIssuanceExecutor(
    private val result: Oid4vciIssuanceExecutionResult,
) : Oid4vciIssuanceExecutor {
    var requestCalls: Int = 0

    override suspend fun requestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction?,
    ): Oid4vciIssuanceExecutionResult {
        requestCalls += 1
        return result
    }
}

private class RecordingSecurityGate : WalletSecurityGate {
    var lastRequest: WalletSecurityGateRequest? = null

    override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult {
        lastRequest = request
        return WalletSecurityGateResult.Authorized(
            WalletSecurityGrant(
                grantId = request.operationId,
                assurance = request.requiredAssurance,
            ),
        )
    }
}

private class StaticIssuanceOptionsProvider(
    private val credentialConfigurationId: String? = null,
    private val haipTokenProofs: Oid4vciHaipTokenProofOptions? = null,
) : Oid4vciIssuanceOptionsProvider {
    override suspend fun options(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
    ): Oid4vciHolderIssuanceOptions =
        Oid4vciHolderIssuanceOptions(
            signingKeyId = "key-1",
            operationBinding = "test:oid4vci-operation",
            clientId = "wallet-client",
            redirectUri = "wallet://callback",
            credentialConfigurationId = credentialConfigurationId,
            iaeCodeVerifier = "iae-code-verifier-secret",
            iaeCodeChallenge = "iae-code-challenge",
            iaeCodeChallengeMethod = "S256",
            haipTokenProofs = haipTokenProofs,
        )
}

private class FixedTokenEndpointProofsProvider(
    private val tokenProofs: Oid4vciTokenEndpointProofs,
    private val credentialDpopProof: String? = null,
) : Oid4vciTokenEndpointProofsProvider {
    override suspend fun proofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError> = Ok(tokenProofs).asResult()

    override suspend fun dpopProof(request: Oid4vciDpopProofRequest): IdkResult<String?, IdkError> = Ok(credentialDpopProof).asResult()
}

private class RotatingTokenEndpointProofsProvider : Oid4vciTokenEndpointProofsProvider {
    private var proofCount = 0
    val requestedNonces = mutableListOf<String?>()

    override suspend fun proofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError> {
        proofCount += 1
        requestedNonces += request.nonce
        val dpop = if (request.nonce == null) "token-dpop-$proofCount" else "token-dpop-$proofCount-nonce-${request.nonce}"
        return Ok(
            Oid4vciTokenEndpointProofs(
                dpopProofJwt = dpop,
                clientAuthentication =
                    ClientAuthenticationConfig.PrivateKeyJwt(
                        ClientAssertion(
                            clientId = "wallet-client",
                            assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            assertion = "client-assertion-$proofCount",
                        ),
                    ),
            ),
        ).asResult()
    }

    override suspend fun dpopProof(request: Oid4vciDpopProofRequest): IdkResult<String?, IdkError> = Ok("credential-dpop.jwt").asResult()
}

private class RecordingDeferredDpopProofsProvider : Oid4vciTokenEndpointProofsProvider {
    val requestedUrls = mutableListOf<String>()
    val requestedNonces = mutableListOf<String?>()

    override suspend fun proofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError> =
        Ok(Oid4vciTokenEndpointProofs()).asResult()

    override suspend fun dpopProof(request: Oid4vciDpopProofRequest): IdkResult<String?, IdkError> {
        requestedUrls += request.httpUrl
        requestedNonces += request.nonce
        val endpoint =
            when {
                request.httpUrl.endsWith("/deferred") -> "deferred"
                request.httpUrl.endsWith("/notification") -> "notification"
                else -> "credential"
            }
        val suffix = request.nonce?.let { "nonce-$it" } ?: "initial"
        return Ok("$endpoint-dpop-$suffix").asResult()
    }
}

private class RecordingCredentialResponseReceiver : Oid4vciCredentialResponseReceiver {
    var receivedCredentialValue: String? = null

    override suspend fun receiveCredentialResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        credentialResponse: CredentialResponse,
    ): List<WalletCredentialPreview> {
        receivedCredentialValue =
            credentialResponse.credentials
                ?.singleOrNull()
                ?.credential
                ?.toString()
                ?.trim('"')
        return listOf(WalletCredentialPreview(id = "cred-1", name = "wallet.interaction.test.credential"))
    }
}

private class RecordingNestedPresentationExecutor : WalletNestedPresentationExecutor {
    var lastRequest: WalletNestedPresentationRequest? = null
    var lastSelection: WalletCredentialSelection? = null

    override suspend fun preparePresentation(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        request: WalletNestedPresentationRequest,
    ): WalletNestedPresentationExecutionResult<WalletNestedPresentationChallenge> {
        lastRequest = request
        return WalletNestedPresentationExecutionResult.Success(
            WalletNestedPresentationChallenge(
                credentialSelection =
                    WalletCredentialSelectionRequest(
                        requirements =
                            listOf(
                                WalletCredentialRequirement(
                                    id = "identity",
                                    format = "dc+sd-jwt",
                                    candidateCredentialIds = listOf("presentation-cred-1"),
                                ),
                            ),
                        satisfiable = true,
                    ),
                disclosure = WalletDisclosureSummary(),
                expiresInSeconds = 60,
            ),
        )
    }

    override suspend fun createPresentationResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletNestedPresentationExecutionResult<WalletNestedPresentationResponse> {
        lastSelection = action.selection
        return WalletNestedPresentationExecutionResult.Success(
            WalletNestedPresentationResponse(
                buildJsonObject {
                    put("vp_token", JsonPrimitive("vp-token-secret"))
                    put("presentation_submission", JsonPrimitive("{}"))
                },
            ),
        )
    }
}

private class RecordingOid4vciHolderService(
    private val offer: CredentialOffer = preAuthorizedOffer(),
    private val metadata: CredentialIssuerMetadata = issuerMetadata(),
    private val interactiveAuthorization: Boolean = false,
    private val requirePar: Boolean = false,
    private val tokenEndpointDpopNonceChallenge: String? = null,
    private val parEndpointDpopNonceChallenge: String? = null,
    private val deferredEndpointDpopNonceChallenge: String? = null,
    private val notificationEndpointDpopNonceChallenge: String? = null,
    private val credentialResponse: CredentialResponse =
        CredentialResponse(
            credentials = listOf(CredentialResponseItem(JsonPrimitive("credential-secret"))),
            notificationId = "notification-secret",
        ),
    private val deferredCredentialResponse: CredentialResponse =
        CredentialResponse(
            credentials = listOf(CredentialResponseItem(JsonPrimitive("deferred-credential-secret"))),
            notificationId = "deferred-notification-secret",
        ),
) : Oid4vciHolderService {
    var lastRawOffer: String? = null
    var exchangedTxCode: String? = null
    var exchangedAuthorizationCode: String? = null
    var exchangedCodeVerifier: String? = null
    var exchangedDpopProofJwt: String? = null
    var exchangedClientAttestationJwt: String? = null
    var exchangedClientAttestationPopJwt: String? = null
    var exchangedClientAuthentication: ClientAuthenticationConfig? = null
    val exchangedDpopProofJwtHistory = mutableListOf<String?>()
    val exchangedClientAuthenticationHistory = mutableListOf<ClientAuthenticationConfig?>()
    var requestedAccessToken: String? = null
    var requestedDpopProofJwt: String? = null
    var requestedCredentialConfigurationId: String? = null
    var requestedCredentialIdentifier: String? = null
    var deferredAccessToken: String? = null
    var deferredTransactionId: String? = null
    val deferredDpopProofJwtHistory = mutableListOf<String?>()
    var notificationEvent: CredentialNotificationEvent? = null
    val notificationDpopProofJwtHistory = mutableListOf<String?>()
    var lastInitiateIaeArgs: InitiateIaeArgs? = null
    var lastFollowUpIaeArgs: FollowUpIaeArgs? = null
    var authorizationRequestUsePar: Boolean? = null
    var authorizationRequestParEndpoint: String? = null
    var authorizationRequestScope: String? = null
    val authorizationRequestDpopProofJwtHistory = mutableListOf<String?>()
    val authorizationRequestClientAuthenticationHistory = mutableListOf<ClientAuthenticationConfig?>()

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> {
        lastRawOffer = rawOffer
        return Ok(offer).asResult()
    }

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> =
        Ok(
            ResolvedCredentialOffer(
                offer = offer,
                issuerMetadata = metadata,
            ),
        ).asResult()

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> = Ok(metadata).asResult()

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> =
        Ok(
            ResolvedAuthorizationServer(
                authorizationServerUrl = "https://as.example",
                metadata =
                    AuthorizationServerMetadata(
                        issuer = "https://as.example",
                        tokenEndpoint = "https://as.example/token",
                        authorizationEndpoint = "https://as.example/authorize",
                        requirePushedAuthorizationRequests = requirePar,
                        pushedAuthorizationRequestEndpoint = if (requirePar) "https://as.example/par" else null,
                        interactiveAuthorizationEndpoint = if (interactiveAuthorization) "https://as.example/iae" else null,
                    ),
            ),
        ).asResult()

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = error("requestNonce is not used in these tests")

    override suspend fun requestAttestationChallenge(challengeEndpoint: String): IdkResult<AttestationChallengeResponse, IdkError> =
        error("requestAttestationChallenge is not used in these tests")

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
        dpopProofJwt: String?,
        clientAttestationJwt: String?,
        clientAttestationPopJwt: String?,
        clientAuthentication: ClientAuthenticationConfig?,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        exchangedTxCode = txCode
        exchangedDpopProofJwt = dpopProofJwt
        exchangedClientAttestationJwt = clientAttestationJwt
        exchangedClientAttestationPopJwt = clientAttestationPopJwt
        exchangedClientAuthentication = clientAuthentication
        exchangedDpopProofJwtHistory += dpopProofJwt
        exchangedClientAuthenticationHistory += clientAuthentication
        if (tokenEndpointDpopNonceChallenge != null && exchangedDpopProofJwtHistory.size == 1) {
            return Err(
                IdkError(
                    code = "use_dpop_nonce",
                    message = IdkError.Message(i18nKey = "use_dpop_nonce", defaultMessage = "Token endpoint requires nonce in DPoP proof"),
                    meta = mapOf("dpop_nonce" to tokenEndpointDpopNonceChallenge),
                ),
            )
        }
        return Ok(
            TokenResponseWithContext(
                accessToken = "access-token-secret",
                tokenType = "Bearer",
                cNonce = "nonce",
            ),
        ).asResult()
    }

    override suspend fun exchangeRefreshToken(
        tokenEndpoint: String,
        refreshToken: String,
        clientId: String?,
        dpopProofJwt: String?,
        clientAttestationJwt: String?,
        clientAttestationPopJwt: String?,
        clientAuthentication: ClientAuthenticationConfig?,
    ): IdkResult<TokenResponseWithContext, IdkError> =
        Ok(
            TokenResponseWithContext(
                accessToken = "refreshed-access-token-secret",
                tokenType = "Bearer",
                cNonce = "refreshed-nonce",
            ),
        ).asResult()

    var receivedKeyAttestationJwt: String? = null
    var receivedCredentialProofIssuerUrl: String? = null

    override suspend fun createCredentialRequestProof(
        walletUnitId: String?,
        operationBinding: String?,
        issuerUrl: String,
        cNonce: String?,
        signingKeyIds: List<String>,
        signingAlgorithm: String,
        clientId: String?,
        keyInclusionMode: JwsIdentifierMode,
        keyAttestationJwt: String?,
        proofType: String,
    ): IdkResult<CreatedProof, IdkError> {
        receivedCredentialProofIssuerUrl = issuerUrl
        receivedKeyAttestationJwt = keyAttestationJwt
        return Ok(CreatedProof(CredentialRequestProofs.jwt("proof-secret"))).asResult()
    }

    override suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
        dpopProofJwt: String?,
        credentialConfigurationId: String?,
        credentialIdentifier: String?,
        proofs: CredentialRequestProofs?,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> {
        requestedAccessToken = accessToken
        requestedDpopProofJwt = dpopProofJwt
        requestedCredentialConfigurationId = credentialConfigurationId
        requestedCredentialIdentifier = credentialIdentifier
        return Ok(credentialResponse).asResult()
    }

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        dpopProofJwt: String?,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> {
        deferredAccessToken = accessToken
        deferredTransactionId = transactionId
        deferredDpopProofJwtHistory += dpopProofJwt
        if (deferredEndpointDpopNonceChallenge != null && deferredDpopProofJwtHistory.size == 1) {
            return Err(
                IdkError(
                    code = "use_dpop_nonce",
                    message = IdkError.Message(i18nKey = "use_dpop_nonce", defaultMessage = "Deferred endpoint requires DPoP nonce"),
                    meta = mapOf("dpop_nonce" to deferredEndpointDpopNonceChallenge),
                ),
            ).asResult()
        }
        return Ok(deferredCredentialResponse).asResult()
    }

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        dpopProofJwt: String?,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?,
    ): IdkResult<Unit, IdkError> {
        notificationEvent = event
        notificationDpopProofJwtHistory += dpopProofJwt
        if (notificationEndpointDpopNonceChallenge != null && notificationDpopProofJwtHistory.size == 1) {
            return Err(
                IdkError(
                    code = "use_dpop_nonce",
                    message = IdkError.Message(i18nKey = "use_dpop_nonce", defaultMessage = "Notification endpoint requires DPoP nonce"),
                    meta = mapOf("dpop_nonce" to notificationEndpointDpopNonceChallenge),
                ),
            ).asResult()
        }
        return Ok(Unit).asResult()
    }

    override suspend fun followUpIae(args: FollowUpIaeArgs): IdkResult<IaeHolderResult, IdkError> {
        lastFollowUpIaeArgs = args
        return Ok(IaeHolderResult.AuthorizationCode("iae-authorization-code-secret")).asResult()
    }

    override suspend fun initiateIae(args: InitiateIaeArgs): IdkResult<IaeHolderResult, IdkError> {
        lastInitiateIaeArgs = args
        return Ok(
            IaeHolderResult.InteractionRequired(
                type = "urn:openid:dcp:iae:openid4vp_presentation",
                authSession = "auth-session-secret",
                openid4vpRequest =
                    buildJsonObject {
                        put("client_id", JsonPrimitive("verifier"))
                        put("response_type", JsonPrimitive("vp_token"))
                        put("response_mode", JsonPrimitive("iae_post"))
                        put("state", JsonPrimitive("presentation-state"))
                    },
                expiresIn = 60,
            ),
        ).asResult()
    }

    override suspend fun buildAuthorizationRequest(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        credentialConfigurationIds: List<String>,
        scope: String?,
        issuerState: String?,
        usePar: Boolean,
        parEndpoint: String?,
        credentialIdentifiers: Map<String, List<String>>?,
        locations: List<String>?,
        clientAuthentication: ClientAuthenticationConfig?,
        dpopProofJwt: String?,
        clientAttestationJwt: String?,
        clientAttestationPopJwt: String?,
    ): IdkResult<AuthorizationRequestResult, IdkError> {
        authorizationRequestUsePar = usePar
        authorizationRequestParEndpoint = parEndpoint
        authorizationRequestScope = scope
        authorizationRequestDpopProofJwtHistory += dpopProofJwt
        authorizationRequestClientAuthenticationHistory += clientAuthentication
        if (parEndpointDpopNonceChallenge != null && authorizationRequestDpopProofJwtHistory.size == 1) {
            return Err(
                IdkError(
                    code = "use_dpop_nonce",
                    message = IdkError.Message(i18nKey = "use_dpop_nonce", defaultMessage = "PAR endpoint requires nonce in DPoP proof"),
                    meta = mapOf("dpop_nonce" to parEndpointDpopNonceChallenge),
                ),
            ).asResult()
        }
        return Ok(
            AuthorizationRequestResult(
                authorizationUrl = "https://as.example/authorize?request=123",
                codeVerifier = "code-verifier-secret",
                state = "oauth-state",
            ),
        ).asResult()
    }

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
        dpopProofJwt: String?,
        clientAttestationJwt: String?,
        clientAttestationPopJwt: String?,
        clientAuthentication: ClientAuthenticationConfig?,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        exchangedAuthorizationCode = code
        exchangedCodeVerifier = codeVerifier
        exchangedDpopProofJwt = dpopProofJwt
        exchangedClientAttestationJwt = clientAttestationJwt
        exchangedClientAttestationPopJwt = clientAttestationPopJwt
        exchangedClientAuthentication = clientAuthentication
        return Ok(
            TokenResponseWithContext(
                accessToken = "access-token-from-code-secret",
                tokenType = "Bearer",
                cNonce = "nonce-from-code",
            ),
        ).asResult()
    }

    override suspend fun parseAndValidateAuthorizationResponse(
        callbackUrl: String,
        expectedState: String?,
        expectedIssuer: String?,
        requireIssuer: Boolean,
    ): IdkResult<AuthorizationResponse, IdkError> {
        val parameters =
            callbackUrl
                .substringAfter('?', "")
                .substringBefore('#')
                .split('&')
                .filter(String::isNotBlank)
                .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val code = parameters["code"]?.takeIf(String::isNotBlank)
            ?: return Err(IdkError.fromString(code = "invalid_request", message = "Authorization response missing code"))
        if (expectedState != null && parameters["state"] != expectedState) {
            return Err(IdkError.fromString(code = "invalid_grant", message = "Authorization response state mismatch"))
        }
        val issuer = parameters["iss"]?.takeIf(String::isNotBlank)
        if (requireIssuer && issuer == null) {
            return Err(IdkError.fromString(code = "invalid_grant", message = "Authorization response missing issuer"))
        }
        if (issuer != null && expectedIssuer != null && issuer != expectedIssuer) {
            return Err(IdkError.fromString(code = "invalid_grant", message = "Authorization response issuer mismatch"))
        }
        return Ok(AuthorizationResponse(code = code, state = parameters["state"])).asResult()
    }

    companion object {
        fun preAuthorizedOffer(): CredentialOffer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = listOf("identity"),
                grants =
                    CredentialOfferGrants(
                        preAuthorizedCode =
                            PreAuthorizedCodeOfferGrant(
                                preAuthorizedCode = "pre-authorized-secret",
                            ),
                    ),
            )

        fun authorizationCodeOffer(): CredentialOffer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = listOf("identity"),
                grants =
                    CredentialOfferGrants(
                        authorizationCode =
                            AuthorizationCodeOfferGrant(
                                issuerState = "issuer-state",
                            ),
                    ),
            )

        fun issuerMetadata(): CredentialIssuerMetadata =
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                notificationEndpoint = "https://issuer.example/notification",
                deferredCredentialEndpoint = "https://issuer.example/deferred",
                credentialConfigurationsSupported = mapOf("identity" to CredentialConfigurationSupported(format = "dc+sd-jwt", scope = "identity")),
            )

        /** Same as [issuerMetadata], except the "identity" config's `jwt` proof type requires a key attestation. */
        fun metadataRequiringKeyAttestation(): CredentialIssuerMetadata =
            issuerMetadata().copy(
                credentialConfigurationsSupported =
                    mapOf(
                        "identity" to
                            CredentialConfigurationSupported(
                                format = "dc+sd-jwt",
                                proofTypesSupported =
                                    mapOf(
                                        "jwt" to
                                            ProofTypeSupported(
                                                proofSigningAlgValuesSupported = listOf("ES256"),
                                                keyAttestationsRequired = KeyAttestationsRequired(),
                                            ),
                                    ),
                            ),
                    ),
            )
    }
}

/** Records the request it received and returns a canned compact key-attestation JWT. */
private class FixedOid4vciKeyAttestationProvider(
    private val result: IdkResult<String, IdkError> = Ok("key-attestation-jwt-secret"),
) : Oid4vciKeyAttestationProvider {
    var lastRequest: Oid4vciKeyAttestationRequest? = null

    override suspend fun attest(request: Oid4vciKeyAttestationRequest): IdkResult<String, IdkError> {
        lastRequest = request
        return result
    }
}

private val Oid4vciRefreshTokenGrantProvider.Companion.unsupported: Oid4vciRefreshTokenGrantProvider
    get() =
        Oid4vciRefreshTokenGrantProvider {
            Err(IdkError.fromString("test.oid4vci.refresh_token_grant_unavailable", "Refresh-token exchange is not exercised by this test"))
        }

private val Oid4vciKeyAttestationProvider.Companion.unsupported: Oid4vciKeyAttestationProvider
    get() =
        Oid4vciKeyAttestationProvider {
            Err(IdkError.fromString("test.oid4vci.key_attestation_unavailable", "Key attestation is not exercised by this test"))
        }
