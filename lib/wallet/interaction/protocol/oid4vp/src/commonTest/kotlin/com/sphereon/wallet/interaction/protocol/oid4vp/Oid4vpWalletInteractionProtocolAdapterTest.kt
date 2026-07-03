/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetOption
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.openid.oid4vp.holder.WalletConfig
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityWindow
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletClaimDescriptor
import com.sphereon.wallet.interaction.WalletCounterpartyTrustRequest
import com.sphereon.wallet.interaction.WalletCounterpartyTrustResolver
import com.sphereon.wallet.interaction.WalletCounterpartyTrustSummary
import com.sphereon.wallet.interaction.WalletCredentialRequirement
import com.sphereon.wallet.interaction.WalletCredentialSelection
import com.sphereon.wallet.interaction.WalletCredentialSelectionRequest
import com.sphereon.wallet.interaction.WalletDisclosureSummary
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutionResult
import com.sphereon.wallet.interaction.WalletNestedPresentationRequest
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolExecutionDecision
import com.sphereon.wallet.interaction.WalletProtocolExecutionPlacement
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolExecutor
import com.sphereon.wallet.interaction.WalletProtocolMatchStrength
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityChallenge
import com.sphereon.wallet.interaction.WalletSecurityChallengeKind
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTrustStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class Oid4vpWalletInteractionProtocolAdapterTest {
    @Test
    fun oid4vpAuthorizationRequestsAreStrongMatches() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()

            val match = adapter.canHandle(WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            assertEquals(WalletProtocolMatchStrength.STRONG, match.strength)
        }

    @Test
    fun dcqlQueryProjectsCredentialRequirementsWithoutClaimValues() {
        val request =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "identity",
                            format = "dc+sd-jwt",
                            multiple = true,
                            claims =
                                listOf(
                                    DcqlClaimQuery(path = listOf("given_name")),
                                    DcqlClaimQuery(path = listOf("address", "locality")),
                                ),
                        ),
                    ),
            ).toCredentialSelectionRequest(candidateCredentialIds = mapOf("identity" to listOf("cred-1")))

        assertEquals(true, request.satisfiable)
        assertEquals(true, request.requirements.single().multipleAllowed)
        assertEquals(listOf("address", "locality"), request.requirements.single().requiredClaimPaths[1])
    }

    @Test
    fun dcqlCredentialSetsProjectSatisfiableOptionsFromActualCandidates() {
        val request =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(id = "pid", format = "dc+sd-jwt"),
                        DcqlCredentialQuery(id = "mdl", format = "mso_mdoc"),
                    ),
                credential_sets =
                    listOf(
                        DcqlCredentialSetQuery(
                            required = true,
                            options =
                                listOf(
                                    DcqlCredentialSetOption(credential_ids = listOf("pid")),
                                    DcqlCredentialSetOption(credential_ids = listOf("mdl")),
                                ),
                        ),
                    ),
            ).toCredentialSelectionRequest(candidateCredentialIds = mapOf("mdl" to listOf("mdl-cred-1")))

        assertEquals(true, request.satisfiable)
        assertEquals(2, request.requirements.size)
        assertEquals(emptyList(), request.requirements.single { it.id == "pid" }.candidateCredentialIds)
        assertEquals(listOf("mdl-cred-1"), request.requirements.single { it.id == "mdl" }.candidateCredentialIds)
        assertEquals(
            false,
            request.credentialSets
                .single()
                .options
                .first()
                .satisfiable
        )
        assertEquals(
            true,
            request.credentialSets
                .single()
                .options
                .last()
                .satisfiable
        )
    }

    @Test
    fun dcqlRequirementsWithoutCandidateCredentialsAreUnsatisfiable() {
        val request =
            DcqlQuery(
                credentials = listOf(DcqlCredentialQuery(id = "identity", format = "dc+sd-jwt")),
            ).toCredentialSelectionRequest(candidateCredentialIds = emptyMap())

        assertEquals(false, request.satisfiable)
        assertEquals(emptyList(), request.requirements.single().candidateCredentialIds)
    }

    @Test
    fun walletStoreCandidateResolverMatchesSdJwtVctWithoutOpeningBodies() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "pid-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                    ),
                    walletCredentialRecord(
                        id = "employee-record",
                        format = CredentialFormat.JWT_VC_JSON,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.JWT_VC_JSON, CredentialTypeRefKind.W3C_VC_TYPE, "EmployeeCredential")),
                    ),
                )
            val resolver = WalletStoreOid4vpCredentialResolver(store)
            val resolved =
                resolvedRequest(
                    DcqlCredentialQuery(
                        id = "identity",
                        format = "dc+sd-jwt",
                        meta =
                            buildJsonObject {
                                put("vct_values", JsonArray(listOf(JsonPrimitive("https://example.com/pid"))))
                            },
                    ),
                )

            val candidates = resolver.candidateCredentialIds(context = walletContext(), resolvedRequest = resolved)

            assertEquals(mapOf("identity" to listOf("pid-record")), candidates)
            assertEquals(emptyList(), store.credentialReads)
            assertEquals(1, store.typeRefQueries.size)
        }

    @Test
    fun walletStoreCandidateResolverMatchesMdocDoctypeWithoutOpeningBodies() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "mdl-record",
                        format = CredentialFormat.MSO_MDOC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.MSO_MDOC, CredentialTypeRefKind.MDOC_DOCTYPE, "org.iso.18013.5.1.mDL")),
                    ),
                )
            val resolver = WalletStoreOid4vpCredentialResolver(store)
            val resolved =
                resolvedRequest(
                    DcqlCredentialQuery(
                        id = "mdl",
                        format = "mso_mdoc",
                        meta =
                            buildJsonObject {
                                put("doctype_value", JsonPrimitive("org.iso.18013.5.1.mDL"))
                            },
                    ),
                )

            val candidates = resolver.candidateCredentialIds(context = walletContext(), resolvedRequest = resolved)

            assertEquals(mapOf("mdl" to listOf("mdl-record")), candidates)
            assertEquals(emptyList(), store.credentialReads)
        }

    @Test
    fun walletStoreCandidateResolverMatchesW3cTypeValuesWithoutOpeningBodies() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "employee-record",
                        format = CredentialFormat.JWT_VC_JSON,
                        typeRefs =
                            setOf(
                                credentialTypeRef(CredentialFormat.JWT_VC_JSON, CredentialTypeRefKind.W3C_VC_TYPE, "VerifiableCredential"),
                                credentialTypeRef(CredentialFormat.JWT_VC_JSON, CredentialTypeRefKind.W3C_VC_TYPE, "EmployeeCredential"),
                            ),
                    ),
                )
            val resolver = WalletStoreOid4vpCredentialResolver(store)
            val resolved =
                resolvedRequest(
                    DcqlCredentialQuery(
                        id = "employee",
                        format = "jwt_vc_json",
                        meta =
                            buildJsonObject {
                                put("type_values", JsonArray(listOf(JsonPrimitive("EmployeeCredential"))))
                            },
                    ),
                )

            val candidates = resolver.candidateCredentialIds(context = walletContext(), resolvedRequest = resolved)

            assertEquals(mapOf("employee" to listOf("employee-record")), candidates)
            assertEquals(emptyList(), store.credentialReads)
        }

    @Test
    fun walletStoreSelectedResolverOpensOnlySelectedCredentialRecords() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "pid-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                        raw = "raw-pid",
                    ),
                    walletCredentialRecord(
                        id = "other-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                        raw = "raw-other",
                    ),
                )
            val resolver = WalletStoreOid4vpCredentialResolver(store)

            val selected =
                resolver.resolveSelectedCredentials(
                    context = walletContext(),
                    state = credentialSelectionState(WalletCredentialSelectionRequest(emptyList(), satisfiable = true)),
                    resolvedRequest = resolvedRequest(DcqlCredentialQuery(id = "identity", format = "dc+sd-jwt")),
                    selectedCredentialIdsByRequirement = mapOf("identity" to listOf("pid-record")),
                )

            assertEquals(listOf("pid-record"), store.credentialReads)
            assertEquals("identity", selected.single().credentialQueryId)
            assertEquals("pid-record-instance", selected.single().credentialId)
            assertEquals("raw-pid", selected.single().presentation)
            assertEquals("holder-pid-record", selected.single().holderKeyAlias)
        }

    @Test
    fun walletStoreBackedAdapterFactoryUsesMetadataCandidates() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "pid-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                    ),
                )
            val adapter =
                Oid4vpWalletInteractionProtocolAdapter.walletStoreBacked(
                    holder =
                        RecordingOid4vpHolderService(
                            resolvedRequest =
                                resolvedRequest(
                                    DcqlCredentialQuery(
                                        id = "identity",
                                        format = "dc+sd-jwt",
                                        meta =
                                            buildJsonObject {
                                                put("vct_values", JsonArray(listOf(JsonPrimitive("https://example.com/pid"))))
                                            },
                                    ),
                                ),
                        ),
                    credentialStore = store,
                )

            val session =
                adapter.start(
                    context = walletContext(),
                    entryPoint = WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"),
                )

            assertEquals(WalletInteractionStatus.CredentialSelection, session.state.status)
            assertEquals(
                listOf("pid-record"),
                session.state.credentialSelection
                    ?.requirements
                    ?.single()
                    ?.candidateCredentialIds
            )
            assertEquals(emptyList(), store.credentialReads)
        }

    @Test
    fun walletStoreBackedAdapterAddsSelectedHolderKeyAndWalletUnitMetadataToSecurityRequest() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "pid-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                    ),
                )
            val adapter =
                Oid4vpWalletInteractionProtocolAdapter.walletStoreBacked(
                    holder =
                        RecordingOid4vpHolderService(
                            resolvedRequest =
                                resolvedRequest(
                                    DcqlCredentialQuery(
                                        id = "identity",
                                        format = "dc+sd-jwt",
                                        meta =
                                            buildJsonObject {
                                                put("vct_values", JsonArray(listOf(JsonPrimitive("https://example.com/pid"))))
                                            },
                                    ),
                                ),
                        ),
                    credentialStore = store,
                )
            val privateStore = RecordingPrivateSessionStore()
            val protocolExecutor = RecordingProtocolExecutor(WalletInteractionExecutionMode.SPLIT)
            val securityGate = RecordingSecurityGate()
            val context =
                walletContext(
                    privateSessionStore = privateStore,
                    protocolExecutor = protocolExecutor,
                    securityGate = securityGate,
                    attributes =
                        mapOf(
                            Oid4vpPresentationSecurityAttributes.WALLET_UNIT_ID to "unit-1",
                            Oid4vpPresentationSecurityAttributes.WALLET_ACCOUNT_ID to "account-1",
                            Oid4vpPresentationSecurityAttributes.ACTIVATION_DECISION_ID to "activation-1",
                            Oid4vpPresentationSecurityAttributes.OPERATION_TYPE to "wallet.sign",
                            Oid4vpPresentationSecurityAttributes.OPERATION_HASH to "hash-1",
                            Oid4vpPresentationSecurityAttributes.NONCE to "nonce-1",
                        ),
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next =
                adapter.handle(
                    context,
                    session.state,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("pid-record"))),
                    ),
                )

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals("holder-pid-record", protocolExecutor.lastRequest?.keyRef)
            assertEquals("unit-1", protocolExecutor.lastRequest?.walletUnitId)
            assertEquals("account-1", protocolExecutor.lastRequest?.walletAccountId)
            assertEquals("activation-1", protocolExecutor.lastRequest?.activationDecisionId)
            assertEquals("wallet.sign", protocolExecutor.lastRequest?.operationType)
            assertEquals("hash-1", protocolExecutor.lastRequest?.operationHash)
            assertEquals("nonce-1", protocolExecutor.lastRequest?.nonce)
            assertEquals("holder-pid-record", securityGate.lastRequest?.keyRef)
            assertEquals("unit-1", securityGate.lastRequest?.walletUnitId)
            assertEquals("account-1", securityGate.lastRequest?.walletAccountId)
            assertEquals("activation-1", securityGate.lastRequest?.activationDecisionId)
            assertEquals("wallet.sign", securityGate.lastRequest?.operationType)
            assertEquals("hash-1", securityGate.lastRequest?.operationHash)
            assertEquals("nonce-1", securityGate.lastRequest?.nonce)
        }

    @Test
    fun walletStoreResolverDoesNotRecordBindingWhenVerifierSubmissionFails() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "pid-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                    ),
                )
            val privateStore = RecordingPrivateSessionStore()
            privateStore.put(
                WalletInteractionSessionId("s1"),
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        mapOf(
                            "entry_point.raw" to "openid4vp://?client_id=verifier&response_type=vp_token",
                            "selected_credential_ids_by_requirement" to
                                Oid4vpWalletInteractionProtocolAdapter.json.encodeToString(mapOf("identity" to listOf("pid-record"))),
                        ),
                ),
            )
            val executor =
                Oid4vpHolderPresentationExecutor(
                    holder = RecordingOid4vpHolderService(SubmissionResult.Error("invalid_request")),
                    selectedCredentialResolver = WalletStoreOid4vpCredentialResolver(store),
                )

            val result =
                executor.submitPresentation(
                    context = walletContext(privateSessionStore = privateStore),
                    state = credentialSelectionState(WalletCredentialSelectionRequest(emptyList(), satisfiable = true)),
                )

            assertTrue(result is Oid4vpPresentationExecutionResult.Failed)
            assertEquals(
                emptyList(),
                store.records
                    .getValue("pid-record")
                    .instances
                    .single()
                    .bindingRefs
            )
        }

    @Test
    fun walletStoreResolverRecordsBindingAfterSuccessfulVerifierSubmission() =
        runTest {
            val store =
                RecordingWalletCredentialStore(
                    walletCredentialRecord(
                        id = "pid-record",
                        format = CredentialFormat.SD_JWT_DC,
                        typeRefs = setOf(credentialTypeRef(CredentialFormat.SD_JWT_DC, CredentialTypeRefKind.SD_JWT_VCT, "https://example.com/pid")),
                    ),
                )
            val privateStore = RecordingPrivateSessionStore()
            privateStore.put(
                WalletInteractionSessionId("s1"),
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        mapOf(
                            "entry_point.raw" to "openid4vp://?client_id=verifier&response_type=vp_token",
                            "selected_credential_ids_by_requirement" to
                                Oid4vpWalletInteractionProtocolAdapter.json.encodeToString(mapOf("identity" to listOf("pid-record"))),
                        ),
                ),
            )
            val executor =
                Oid4vpHolderPresentationExecutor(
                    holder = RecordingOid4vpHolderService(),
                    selectedCredentialResolver = WalletStoreOid4vpCredentialResolver(store),
                )

            val result =
                executor.submitPresentation(
                    context = walletContext(privateSessionStore = privateStore),
                    state = credentialSelectionState(WalletCredentialSelectionRequest(emptyList(), satisfiable = true)),
                )

            assertTrue(result is Oid4vpPresentationExecutionResult.Submitted)
            val binding =
                store.records
                    .getValue("pid-record")
                    .instances
                    .single()
                    .bindingRefs
                    .single()
            assertEquals("verifier", binding.verifierRef.value)
            assertEquals("identity", binding.presentationId)
        }

    @Test
    fun blockedVerifierTrustFailsWithoutExposingProtocolRequest() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()
            val session =
                adapter.start(
                    WalletInteractionContext(
                        sessionId = WalletInteractionSessionId("s1"),
                        walletInstanceId = "wallet",
                        executionMode = WalletInteractionExecutionMode.LOCAL,
                        trustResolver =
                            object : WalletCounterpartyTrustResolver {
                                override suspend fun resolve(input: WalletCounterpartyTrustRequest): WalletCounterpartyTrustSummary =
                                    WalletCounterpartyTrustSummary(
                                        counterparty = input.counterparty,
                                        status = WalletTrustStatus.BLOCKED,
                                        policyAction = WalletTrustPolicyAction.BLOCK,
                                    )
                            },
                    ),
                    WalletEntryPoint.rawQr("openid4vp://?client_id=blocked&response_type=vp_token"),
                )

            assertEquals(WalletInteractionStatus.Failed, session.state.status)
            assertEquals(true, session.state.terminal)
            assertEquals("oid4vp.verifier_blocked", session.state.error?.code)
        }

    @Test
    fun presentationSharingUsesConfiguredSecurityGate() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    securityGate =
                        object : WalletSecurityGate {
                            override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult =
                                WalletSecurityGateResult.ChallengeRequired(
                                    WalletSecurityChallenge(
                                        challengeId = request.operationId,
                                        kind = WalletSecurityChallengeKind.BIOMETRIC,
                                        reasonKey = "wallet.interaction.security.test",
                                    ),
                                )
                        },
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.SecurityUnlockRequired, next.status)
            assertEquals("wallet.interaction.security.test", next.securityChallenge?.reasonKey)
        }

    @Test
    fun unsatisfiedDcqlSelectionStaysInCredentialSelectionWithoutSecurityGate() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()
            val securityGate = RecordingSecurityGate()
            val context = context(securityGate)
            val state =
                credentialSelectionState(
                    request =
                        WalletCredentialSelectionRequest(
                            requirements =
                                listOf(
                                    WalletCredentialRequirement(
                                        id = "identity",
                                        candidateCredentialIds = emptyList(),
                                    ),
                                ),
                            satisfiable = false,
                        ),
                )

            val next =
                adapter.handle(
                    context,
                    state,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("cred-1"))),
                    ),
                )

            assertEquals(WalletInteractionStatus.CredentialSelection, next.status)
            assertEquals("oid4vp.selection_unsatisfiable", next.error?.code)
            assertEquals(true, next.error?.retryable)
            assertEquals(0, securityGate.calls)
        }

    @Test
    fun invalidDcqlMultipleSelectionStaysInCredentialSelectionWithoutSecurityGate() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()
            val securityGate = RecordingSecurityGate()
            val context = context(securityGate)
            val state =
                credentialSelectionState(
                    request =
                        WalletCredentialSelectionRequest(
                            requirements =
                                listOf(
                                    WalletCredentialRequirement(
                                        id = "identity",
                                        multipleAllowed = false,
                                        candidateCredentialIds = listOf("cred-1", "cred-2"),
                                    ),
                                ),
                            satisfiable = true,
                        ),
                )

            val next =
                adapter.handle(
                    context,
                    state,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("cred-1", "cred-2"))),
                    ),
                )

            assertEquals(WalletInteractionStatus.CredentialSelection, next.status)
            assertEquals("oid4vp.selection_multiple_not_allowed", next.error?.code)
            assertEquals("identity", next.error?.arguments?.get("requirementId"))
            assertEquals(0, securityGate.calls)
        }

    @Test
    fun revealClaimValuesOnlyFlipsActiveUiRevealFlagWithoutPersistingValues() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()
            val state =
                WalletInteractionState(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    status = WalletInteractionStatus.DisclosureConsent,
                    flowKind = WalletInteractionFlowKind.CredentialPresent,
                    protocol = WalletProtocol.OID4VP,
                    adapterId = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
                    disclosure =
                        WalletDisclosureSummary(
                            requestedClaims =
                                listOf(
                                    WalletClaimDescriptor(path = listOf("given_name"), valueAvailable = true),
                                    WalletClaimDescriptor(path = listOf("family_name"), valueAvailable = true),
                                ),
                            claimValuesRevealed = false,
                        ),
                )

            val next = adapter.handle(context(RecordingSecurityGate()), state, WalletInteractionAction.revealClaimValues())
            val encoded = Json.encodeToString(next)

            assertEquals(true, next.disclosure?.claimValuesRevealed)
            assertEquals(
                listOf("given_name"),
                next.disclosure
                    ?.requestedClaims
                    ?.first()
                    ?.path
            )
            assertFalse(encoded.contains("forbidden_claim_payload"))
        }

    @Test
    fun continueWithoutPresentationExecutorDoesNotPretendPresentationWasShared() =
        runTest {
            val adapter = Oid4vpWalletInteractionProtocolAdapter()
            val securityGate = RecordingSecurityGate()
            val context = context(securityGate)
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Failed, next.status)
            assertEquals("oid4vp.execution_not_configured", next.error?.code)
            assertEquals(true, next.error?.retryable)
            assertFalse(next.terminal)
            assertEquals(1, securityGate.calls)
        }

    @Test
    fun presentationExecutorSubmittedResultCompletesAfterFinalApproval() =
        runTest {
            val executor = RecordingPresentationExecutor(Oid4vpPresentationExecutionResult.Submitted())
            val adapter = Oid4vpWalletInteractionProtocolAdapter(presentationExecutor = executor)
            val securityGate = RecordingSecurityGate()
            val context = context(securityGate)
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(true, next.terminal)
            assertEquals("wallet.interaction.status.presentation_shared", next.message?.titleKey)
            assertEquals(1, executor.submitCalls)
        }

    @Test
    fun presentationSharingUsesProtocolExecutorDecisionBeforeSecurityGate() =
        runTest {
            val executor = RecordingPresentationExecutor(Oid4vpPresentationExecutionResult.Submitted())
            val adapter = Oid4vpWalletInteractionProtocolAdapter(presentationExecutor = executor)
            val securityGate = RecordingSecurityGate()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.SPLIT,
                    protocolExecutor =
                        StaticDecisionProtocolExecutor(
                            WalletProtocolExecutionDecision(
                                executionMode = WalletInteractionExecutionMode.SPLIT,
                                placement = WalletProtocolExecutionPlacement.SPLIT_LOCAL_SECURITY,
                                securityOperation = WalletSecurityOperation.LOCAL_HSM_UNLOCK,
                                requiredAssurance = WalletSecurityAssurance.HARDWARE_BACKED,
                                walletUnitId = "unit-1",
                                operationHash = "hash-1",
                            ),
                        ),
                    securityGate = securityGate,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(WalletSecurityOperation.LOCAL_HSM_UNLOCK, securityGate.lastRequest?.operation)
            assertEquals(WalletSecurityAssurance.HARDWARE_BACKED, securityGate.lastRequest?.requiredAssurance)
            assertEquals("unit-1", securityGate.lastRequest?.walletUnitId)
            assertEquals("hash-1", securityGate.lastRequest?.operationHash)
        }

    @Test
    fun approvingSecurityChallengeSubmitsPresentationWithoutLeakingGrant() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val executor = RecordingPresentationExecutor(Oid4vpPresentationExecutionResult.Submitted())
            val adapter = Oid4vpWalletInteractionProtocolAdapter(presentationExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    status = WalletInteractionStatus.SecurityUnlockRequired,
                    flowKind = WalletInteractionFlowKind.CredentialPresent,
                    protocol = WalletProtocol.OID4VP,
                    adapterId = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
                )

            val next =
                adapter.handle(
                    context,
                    state,
                    WalletInteractionAction.approveSecurityChallenge(
                        WalletSecurityGrant(
                            grantId = "grant-secret",
                            assurance = WalletSecurityAssurance.USER_PRESENT,
                        ),
                    ),
                )
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals("grant-secret", privateStore.get(next.sessionId, Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("security_grant_id"))
            assertFalse(encoded.contains("grant-secret"))
        }

    @Test
    fun holderPresentationExecutorSubmitsViaHolderServiceUsingPrivateSelection() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder = RecordingOid4vpHolderService()
            val resolver = RecordingSelectedCredentialResolver()
            val adapter =
                Oid4vpWalletInteractionProtocolAdapter(
                    holder = holder,
                    presentationExecutor = Oid4vpHolderPresentationExecutor(holder, resolver),
                    candidateResolver = StaticCandidateResolver(mapOf("identity" to listOf("cred-1"))),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    securityGate = RecordingSecurityGate(),
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next =
                adapter.handle(
                    context,
                    session.state,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("cred-1"))),
                    ),
                )
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.Completed, next.status)
            assertEquals(true, next.terminal)
            assertEquals(mapOf("identity" to listOf("cred-1")), resolver.lastSelection)
            assertEquals(listOf("cred-1"), holder.createdSelectedCredentials.map { it.credentialId })
            assertEquals(1, holder.submitCalls)
            assertFalse(encoded.contains("presentation-cred-1"))
        }

    @Test
    fun holderPresentationExecutorMapsRedirectSubmissionToAuthorizationRequired() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder = RecordingOid4vpHolderService(submissionResult = SubmissionResult.Redirect("https://verifier.example/cb#vp_token=ok"))
            val resolver = RecordingSelectedCredentialResolver()
            val adapter =
                Oid4vpWalletInteractionProtocolAdapter(
                    holder = holder,
                    presentationExecutor = Oid4vpHolderPresentationExecutor(holder, resolver, responseMode = ResponseMode.FRAGMENT),
                    candidateResolver = StaticCandidateResolver(mapOf("identity" to listOf("cred-1"))),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    securityGate = RecordingSecurityGate(),
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid4vp://?client_id=verifier&response_type=vp_token"))

            val next =
                adapter.handle(
                    context,
                    session.state,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("cred-1"))),
                    ),
                )

            assertEquals(WalletInteractionStatus.AuthorizationRequired, next.status)
            assertEquals("https://verifier.example/cb#vp_token=ok", next.authorizationUrl)
            assertEquals(ResponseMode.FRAGMENT, holder.submittedResponseMode)
        }

    @Test
    fun nestedPresentationExecutorCreatesIaeResponseWithoutSubmittingToVerifier() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder = RecordingOid4vpHolderService()
            val resolver = RecordingSelectedCredentialResolver()
            val executor = Oid4vpNestedPresentationExecutor(holder, resolver)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    status = WalletInteractionStatus.CredentialSelection,
                    flowKind = WalletInteractionFlowKind.CredentialPresent,
                    protocol = WalletProtocol.OID4VP,
                    adapterId = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val request =
                WalletNestedPresentationRequest(
                    protocol = WalletProtocol.OID4VP,
                    interactionType = "urn:openid:dcp:iae:openid4vp_presentation",
                    requestObject =
                        buildJsonObject {
                            put("client_id", JsonPrimitive("verifier"))
                            put("response_type", JsonPrimitive("vp_token"))
                            put("response_mode", JsonPrimitive("iae_post"))
                            put("state", JsonPrimitive("state"))
                        },
                )

            val challengeResult = executor.preparePresentation(context, state, request)

            assertTrue(challengeResult is WalletNestedPresentationExecutionResult.Success)
            assertEquals(
                "identity",
                challengeResult.value.credentialSelection
                    ?.requirements
                    ?.single()
                    ?.id
            )

            val responseResult =
                executor.createPresentationResponse(
                    context,
                    state.copy(credentialSelection = challengeResult.value.credentialSelection),
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(mapOf("identity" to listOf("cred-1"))),
                    ),
                )

            assertTrue(responseResult is WalletNestedPresentationExecutionResult.Success)
            assertEquals(
                "vp-token-secret",
                responseResult.value.response["vp_token"]
                    ?.toString()
                    ?.trim('"')
            )
            assertEquals(null, responseResult.value.response["code"])
            assertEquals(0, holder.submitCalls)
            assertEquals(mapOf("identity" to listOf("cred-1")), resolver.lastSelection)
        }

    private fun context(securityGate: WalletSecurityGate): WalletInteractionContext =
        WalletInteractionContext(
            sessionId = WalletInteractionSessionId("s1"),
            walletInstanceId = "wallet",
            executionMode = WalletInteractionExecutionMode.LOCAL,
            securityGate = securityGate,
        )

    private fun credentialSelectionState(request: WalletCredentialSelectionRequest): WalletInteractionState =
        WalletInteractionState(
            sessionId = WalletInteractionSessionId("s1"),
            walletInstanceId = "wallet",
            status = WalletInteractionStatus.CredentialSelection,
            flowKind = WalletInteractionFlowKind.CredentialPresent,
            protocol = WalletProtocol.OID4VP,
            adapterId = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
            credentialSelection = request,
        )

    private fun walletContext(
        privateSessionStore: WalletInteractionPrivateSessionStore = WalletInteractionPrivateSessionStore.none,
        protocolExecutor: WalletProtocolExecutor = WalletProtocolExecutor.local,
        securityGate: WalletSecurityGate = WalletSecurityGate.allow,
        attributes: Map<String, String> = emptyMap(),
    ): WalletInteractionContext =
        WalletInteractionContext(
            sessionId = WalletInteractionSessionId("s1"),
            walletInstanceId = "wallet",
            executionMode = protocolExecutor.executionMode,
            protocolExecutor = protocolExecutor,
            securityGate = securityGate,
            privateSessionStore = privateSessionStore,
            attributes = attributes,
        )

    private fun resolvedRequest(vararg credentials: DcqlCredentialQuery): ResolvedOid4vpRequest =
        ResolvedOid4vpRequest(
            request =
                AuthorizationRequest(
                    clientId = "verifier",
                    responseType = "vp_token",
                    state = "state",
                ),
            dcqlQuery = DcqlQuery(credentials = credentials.toList()),
            verifierInfo =
                VerifierInfo(
                    clientId = "verifier",
                    clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                ),
        )

    private fun credentialTypeRef(
        format: CredentialFormat,
        kind: CredentialTypeRefKind,
        value: String,
    ): CredentialTypeRef =
        CredentialTypeRef(
            format = format,
            kind = kind,
            value = value,
            source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
            primary = true,
        )

    private fun walletCredentialRecord(
        id: String,
        format: CredentialFormat,
        typeRefs: Set<CredentialTypeRef>,
        raw: String = "raw-$id",
    ): CredentialRecord {
        val now = Clock.System.now()
        return CredentialRecord(
            id = id,
            walletInstanceId = "wallet",
            issuerRef = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example"),
            format = format,
            credentialTypeRefs = typeRefs,
            instances =
                listOf(
                    CredentialInstance(
                        id = "$id-instance",
                        walletInstanceId = "wallet",
                        credentialRecordId = id,
                        format = format,
                        raw = raw,
                        bodyStorageRef = BodyStorageRef(kind = BodyStorageKind.WALLET_STORE, path = "wallet/$id/body"),
                        holderKeyRef = KeyRef(alias = "holder-$id"),
                        lifecycleState = CredentialLifecycleState.ACTIVE,
                        validity = CredentialValidityWindow(),
                        issuedAt = now,
                        storedAt = now,
                        updatedAt = now,
                    ),
                ),
            createdAt = now,
            updatedAt = now,
        )
    }

    private class RecordingWalletCredentialStore(
        initialRecords: List<CredentialRecord>,
    ) : WalletCredentialStore {
        constructor(vararg initialRecords: CredentialRecord) : this(initialRecords.toList())

        val records: MutableMap<String, CredentialRecord> = initialRecords.associateBy { it.id }.toMutableMap()
        val metadataQueries: MutableList<CredentialMetadataFilter> = mutableListOf()
        val typeRefQueries: MutableList<CredentialTypeRef> = mutableListOf()
        val credentialReads: MutableList<String> = mutableListOf()
        val putRecords: MutableList<CredentialRecord> = mutableListOf()

        override suspend fun putCredential(
            walletInstanceId: String,
            record: CredentialRecord,
        ): IdkResult<CredentialRecord, IdkError> {
            records[record.id] = record
            putRecords += record
            return Ok(record).asResult()
        }

        override suspend fun getCredential(
            walletInstanceId: String,
            credentialRecordId: String,
        ): IdkResult<CredentialRecord?, IdkError> {
            credentialReads += credentialRecordId
            return Ok(records[credentialRecordId]?.takeIf { it.walletInstanceId == walletInstanceId }).asResult()
        }

        override suspend fun getMetadata(
            walletInstanceId: String,
            credentialRecordId: String,
        ): IdkResult<CredentialMetadata?, IdkError> = Ok(records[credentialRecordId]?.takeIf { it.walletInstanceId == walletInstanceId }?.metadata(Clock.System.now())).asResult()

        override suspend fun listMetadata(
            walletInstanceId: String,
            filter: CredentialMetadataFilter,
        ): IdkResult<List<CredentialMetadata>, IdkError> {
            metadataQueries += filter
            return Ok(metadata(walletInstanceId).filter { it.matches(filter) }).asResult()
        }

        override suspend fun findByCredentialTypeRef(
            walletInstanceId: String,
            ref: CredentialTypeRef,
        ): IdkResult<List<CredentialMetadata>, IdkError> {
            typeRefQueries += ref
            return Ok(metadata(walletInstanceId).filter { it.hasTypeRef(ref) && !it.lifecycleSummary.tombstone }).asResult()
        }

        override suspend fun deleteCredential(
            walletInstanceId: String,
            credentialRecordId: String,
        ): IdkResult<Boolean, IdkError> = Ok(records.remove(credentialRecordId) != null).asResult()

        private fun metadata(walletInstanceId: String): List<CredentialMetadata> =
            records
                .values
                .filter { it.walletInstanceId == walletInstanceId }
                .map { it.metadata(Clock.System.now()) }
    }

    private class RecordingSecurityGate : WalletSecurityGate {
        var calls: Int = 0
        var lastRequest: WalletSecurityGateRequest? = null

        override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult {
            calls += 1
            lastRequest = request
            return WalletSecurityGateResult.Authorized(
                WalletSecurityGrant(
                    grantId = "grant",
                    assurance = WalletSecurityAssurance.USER_PRESENT,
                ),
            )
        }
    }

    private class StaticDecisionProtocolExecutor(
        private val decision: WalletProtocolExecutionDecision,
    ) : WalletProtocolExecutor {
        override val executionMode: WalletInteractionExecutionMode = decision.executionMode

        override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision = decision
    }

    private class RecordingProtocolExecutor(
        override val executionMode: WalletInteractionExecutionMode,
    ) : WalletProtocolExecutor {
        var lastRequest: WalletProtocolExecutionRequest? = null

        override suspend fun plan(request: WalletProtocolExecutionRequest): WalletProtocolExecutionDecision {
            lastRequest = request
            return WalletProtocolExecutionDecision.forMode(executionMode, request)
        }
    }

    private class RecordingOid4vpHolderService(
        private val submissionResult: SubmissionResult = SubmissionResult.Success(),
        private val resolvedRequest: ResolvedOid4vpRequest = defaultResolvedRequest(),
    ) : Oid4vpHolderService {
        var submitCalls: Int = 0
        var submittedResponseMode: ResponseMode? = null
        var createdSelectedCredentials: List<SelectedCredential> = emptyList()

        private val authorizationRequest = resolvedRequest.request

        override suspend fun parseAuthorizationRequest(
            requestUri: String,
            walletConfig: WalletConfig?,
        ): IdkResult<AuthorizationRequest, IdkError> = Ok(authorizationRequest).asResult()

        override suspend fun resolveAuthorizationRequest(request: AuthorizationRequest): IdkResult<ResolvedOid4vpRequest, IdkError> = Ok(resolvedRequest).asResult()

        override suspend fun createAuthorizationResponse(
            request: ResolvedOid4vpRequest,
            selectedCredentials: List<SelectedCredential>,
        ): IdkResult<AuthorizationResponse, IdkError> {
            createdSelectedCredentials = selectedCredentials
            return Ok(
                AuthorizationResponse(
                    code = "vp-response",
                    state = "state",
                    additionalParameters = mapOf("vp_token" to JsonPrimitive("vp-token-secret")),
                ),
            ).asResult()
        }

        override suspend fun submitAuthorizationResponse(
            resolvedRequest: ResolvedOid4vpRequest,
            response: AuthorizationResponse,
            responseMode: ResponseMode?,
        ): IdkResult<SubmissionResult, IdkError> {
            submitCalls += 1
            submittedResponseMode = responseMode
            return Ok(submissionResult).asResult()
        }

        companion object {
            private fun defaultResolvedRequest(): ResolvedOid4vpRequest {
                val authorizationRequest =
                    AuthorizationRequest(
                        clientId = "verifier",
                        responseType = "vp_token",
                        state = "state",
                    )
                return ResolvedOid4vpRequest(
                    request = authorizationRequest,
                    dcqlQuery =
                        DcqlQuery(
                            credentials =
                                listOf(
                                    DcqlCredentialQuery(
                                        id = "identity",
                                        format = "dc+sd-jwt",
                                    ),
                                ),
                        ),
                    verifierInfo =
                        VerifierInfo(
                            clientId = "verifier",
                            clientIdScheme = ClientIdScheme.PRE_REGISTERED,
                        ),
                )
            }
        }
    }

    private class RecordingSelectedCredentialResolver : Oid4vpSelectedCredentialResolver {
        var lastSelection: Map<String, List<String>> = emptyMap()

        override suspend fun resolveSelectedCredentials(
            context: WalletInteractionContext,
            state: WalletInteractionState,
            resolvedRequest: ResolvedOid4vpRequest,
            selectedCredentialIdsByRequirement: Map<String, List<String>>,
        ): List<SelectedCredential> {
            lastSelection = selectedCredentialIdsByRequirement
            return selectedCredentialIdsByRequirement.flatMap { (requirementId, credentialIds) ->
                credentialIds.map { credentialId ->
                    SelectedCredential(
                        credentialQueryId = requirementId,
                        credentialId = credentialId,
                        presentation = "presentation-$credentialId",
                        format = "dc+sd-jwt",
                    )
                }
            }
        }
    }

    private class StaticCandidateResolver(
        private val candidates: Map<String, List<String>>,
    ) : Oid4vpCredentialCandidateResolver {
        override suspend fun candidateCredentialIds(
            context: WalletInteractionContext,
            resolvedRequest: ResolvedOid4vpRequest,
        ): Map<String, List<String>> = candidates
    }

    private class RecordingPresentationExecutor(
        private val result: Oid4vpPresentationExecutionResult,
    ) : Oid4vpPresentationExecutor {
        var submitCalls: Int = 0

        override suspend fun submitPresentation(
            context: WalletInteractionContext,
            state: WalletInteractionState,
        ): Oid4vpPresentationExecutionResult {
            submitCalls += 1
            return result
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
}
