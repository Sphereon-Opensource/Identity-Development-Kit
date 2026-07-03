/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.openid.oid4vci.common.model.AuthorizationCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
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
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletCredentialRequirement
import com.sphereon.wallet.interaction.WalletCredentialSelection
import com.sphereon.wallet.interaction.WalletCredentialSelectionRequest
import com.sphereon.wallet.interaction.WalletDisclosureSummary
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletNestedPresentationChallenge
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutionResult
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutor
import com.sphereon.wallet.interaction.WalletNestedPresentationRequest
import com.sphereon.wallet.interaction.WalletNestedPresentationResponse
import com.sphereon.wallet.interaction.WalletProtocolMatchStrength
import com.sphereon.wallet.interaction.WalletSecurityContextAttributes
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletSecurityOperation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Oid4vciWalletInteractionProtocolAdapterTest {
    @Test
    fun credentialOfferLinksAreStrongMatches() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter()

            val match = adapter.canHandle(WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret"))

            assertEquals(WalletProtocolMatchStrength.STRONG, match.strength)
        }

    @Test
    fun startProjectsUiSafeOfferState() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter()
            val privateStore = RecordingPrivateSessionStore()
            val rawOffer = "openid-credential-offer://?credential_offer=pre-authorized_code-secret"
            val session =
                adapter.start(
                    WalletInteractionContext(
                        sessionId = WalletInteractionSessionId("s1"),
                        walletInstanceId = "wallet",
                        executionMode = WalletInteractionExecutionMode.LOCAL,
                        privateSessionStore = privateStore,
                    ),
                    WalletEntryPoint.rawQr(rawOffer),
                )

            val encoded = Json.encodeToString(session.state)

            assertEquals(WalletInteractionStatus.CredentialOfferReview, session.state.status)
            assertEquals(rawOffer, privateStore.get(session.state.sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("entry_point.raw"))
            assertFalse(encoded.contains("pre-authorized_code-secret"))
        }

    @Test
    fun continueWithoutIssuanceExecutorDoesNotPretendCredentialWasReceived() =
        runTest {
            val adapter = Oid4vciWalletInteractionProtocolAdapter()
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

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
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.SPLIT,
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

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, next.status)
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
            val executor = RecordingIssuanceExecutor(Oid4vciIssuanceExecutionResult.Received())
            val adapter = Oid4vciWalletInteractionProtocolAdapter(issuanceExecutor = executor)
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.submitTxCode("tx-secret"))
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, next.status)
            assertEquals(1, executor.requestCalls)
            assertEquals("tx-secret", privateStore.get(next.sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("tx_code"))
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
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=x"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())

            assertEquals(WalletInteractionStatus.DeferredRetrievalPending, next.status)
            assertEquals(5, next.deferred?.intervalSeconds)
            assertTrue(next.deferred?.resumable == true)
        }

    @Test
    fun holderIssuanceExecutorRequestsCredentialAndKeepsSecretsPrivate() =
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
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.submitTxCode("tx-secret"))
            val accepted =
                adapter.handle(
                    context,
                    next,
                    WalletInteractionAction.acceptReceivedCredential(),
                )
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, next.status)
            assertEquals(WalletInteractionStatus.Completed, accepted.status)
            assertEquals("tx-secret", holder.exchangedTxCode)
            assertEquals("access-token-secret", holder.requestedAccessToken)
            assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, holder.notificationEvent)
            assertEquals("credential-secret", receiver.receivedCredentialValue)
            assertFalse(encoded.contains("tx-secret"))
            assertFalse(encoded.contains("access-token-secret"))
            assertFalse(encoded.contains("proof-secret"))
            assertFalse(encoded.contains("credential-secret"))
        }

    @Test
    fun holderIssuanceExecutorPollsDeferredCredentialOnRetry() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder =
                RecordingOid4vciHolderService(
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
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=secret-offer"))

            val deferred = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())
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
            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, retrieved.status)
            assertEquals("deferred-transaction-secret", holder.deferredTransactionId)
            assertEquals("access-token-secret", holder.deferredAccessToken)
            assertEquals("deferred-credential-secret", receiver.receivedCredentialValue)
            assertFalse(deferredEncoded.contains("deferred-transaction-secret"))
            assertFalse(deferredEncoded.contains("access-token-secret"))
            assertFalse(retrievedEncoded.contains("deferred-credential-secret"))
        }

    @Test
    fun holderIssuanceExecutorBuildsAuthorizationRedirectForAuthorizationCodeGrant() =
        runTest {
            val privateStore = RecordingPrivateSessionStore()
            val holder = RecordingOid4vciHolderService(offer = RecordingOid4vciHolderService.authorizationCodeOffer())
            val adapter =
                Oid4vciWalletInteractionProtocolAdapter(
                    holder = holder,
                    issuanceExecutor =
                        Oid4vciHolderIssuanceExecutor(
                            holder = holder,
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = RecordingCredentialResponseReceiver(),
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=auth-code"))

            val next = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())
            val encoded = Json.encodeToString(next)

            assertEquals(WalletInteractionStatus.AuthorizationRequired, next.status)
            assertEquals("https://as.example/authorize?request=123", next.authorizationUrl)
            assertEquals("code-verifier-secret", privateStore.get(next.sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("authorization_code_verifier"))
            assertFalse(encoded.contains("code-verifier-secret"))

            val afterCallback =
                adapter.handle(
                    context,
                    next,
                    WalletInteractionAction(
                        type = WalletInteractionActionType.AUTH_CALLBACK,
                        authorizationCallback = "wallet://callback?code=authorization-code-secret&state=oauth-state",
                    ),
                )
            val afterCallbackEncoded = Json.encodeToString(afterCallback)

            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, afterCallback.status)
            assertEquals("authorization-code-secret", holder.exchangedAuthorizationCode)
            assertEquals("code-verifier-secret", holder.exchangedCodeVerifier)
            assertEquals("access-token-from-code-secret", holder.requestedAccessToken)
            assertFalse(afterCallbackEncoded.contains("authorization-code-secret"))
            assertFalse(afterCallbackEncoded.contains("code-verifier-secret"))
            assertFalse(afterCallbackEncoded.contains("access-token-from-code-secret"))
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
                            optionsProvider = StaticIssuanceOptionsProvider(),
                            credentialReceiver = receiver,
                            nestedPresentationExecutor = nestedExecutor,
                        ),
                )
            val context =
                WalletInteractionContext(
                    sessionId = WalletInteractionSessionId("s1"),
                    walletInstanceId = "wallet",
                    executionMode = WalletInteractionExecutionMode.LOCAL,
                    privateSessionStore = privateStore,
                )
            val session = adapter.start(context, WalletEntryPoint.rawQr("openid-credential-offer://?credential_offer=iae"))

            val challenge = adapter.handle(context, session.state, WalletInteractionAction.continueFlow())
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
            assertEquals("auth-session-secret", privateStore.get(challenge.sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)?.values?.get("iae_auth_session"))
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
            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, completed.status)
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

private class StaticIssuanceOptionsProvider : Oid4vciIssuanceOptionsProvider {
    override suspend fun options(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
    ): Oid4vciHolderIssuanceOptions =
        Oid4vciHolderIssuanceOptions(
            signingKeyId = "key-1",
            clientId = "wallet-client",
            redirectUri = "wallet://callback",
            iaeCodeVerifier = "iae-code-verifier-secret",
            iaeCodeChallenge = "iae-code-challenge",
            iaeCodeChallengeMethod = "S256",
        )
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
    private val interactiveAuthorization: Boolean = false,
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
    var exchangedTxCode: String? = null
    var exchangedAuthorizationCode: String? = null
    var exchangedCodeVerifier: String? = null
    var requestedAccessToken: String? = null
    var deferredAccessToken: String? = null
    var deferredTransactionId: String? = null
    var notificationEvent: CredentialNotificationEvent? = null
    var lastInitiateIaeArgs: InitiateIaeArgs? = null
    var lastFollowUpIaeArgs: FollowUpIaeArgs? = null

    override suspend fun parseCredentialOffer(rawOffer: String): IdkResult<CredentialOffer, IdkError> = Ok(offer).asResult()

    override suspend fun resolveCredentialOffer(offer: CredentialOffer): IdkResult<ResolvedCredentialOffer, IdkError> =
        Ok(
            ResolvedCredentialOffer(
                offer = offer,
                issuerMetadata = issuerMetadata(),
            ),
        ).asResult()

    override suspend fun resolveIssuerMetadata(issuerUrl: String): IdkResult<CredentialIssuerMetadata, IdkError> = Ok(issuerMetadata()).asResult()

    override suspend fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        preferredAuthorizationServer: String?,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> =
        Ok(
            ResolvedAuthorizationServer(
                authorizationServerUrl = "https://as.example",
                metadata =
                    buildJsonObject {
                        put("token_endpoint", "https://as.example/token")
                        put("authorization_endpoint", "https://as.example/authorize")
                        if (interactiveAuthorization) {
                            put("interactive_authorization_endpoint", "https://as.example/iae")
                        }
                    },
            ),
        ).asResult()

    override suspend fun requestNonce(nonceEndpoint: String): IdkResult<NonceResponse, IdkError> = error("requestNonce is not used in these tests")

    override suspend fun exchangePreAuthorizedCode(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        exchangedTxCode = txCode
        return Ok(
            TokenResponseWithContext(
                accessToken = "access-token-secret",
                tokenType = "Bearer",
                cNonce = "nonce",
            ),
        ).asResult()
    }

    override suspend fun createCredentialRequestProof(
        issuerUrl: String,
        cNonce: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        clientId: String?,
        count: Int,
        keyInclusionMode: JwsIdentifierMode,
    ): IdkResult<CreatedProof, IdkError> = Ok(CreatedProof(CredentialRequestProofs.jwt("proof-secret"))).asResult()

    override suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
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
        return Ok(credentialResponse).asResult()
    }

    override suspend fun requestDeferredCredential(
        deferredCredentialEndpoint: String,
        accessToken: String,
        transactionId: String,
        credentialResponseEncryption: RequestedCredentialResponseEncryption?,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
        decryptionKeyId: String?,
    ): IdkResult<CredentialResponse, IdkError> {
        deferredAccessToken = accessToken
        deferredTransactionId = transactionId
        return Ok(deferredCredentialResponse).asResult()
    }

    override suspend fun sendNotification(
        notificationEndpoint: String,
        accessToken: String,
        notificationId: String,
        event: CredentialNotificationEvent,
        eventDescription: String?,
    ): IdkResult<Unit, IdkError> {
        notificationEvent = event
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
    ): IdkResult<AuthorizationRequestResult, IdkError> =
        Ok(
            AuthorizationRequestResult(
                authorizationUrl = "https://as.example/authorize?request=123",
                codeVerifier = "code-verifier-secret",
                state = "oauth-state",
            ),
        ).asResult()

    override suspend fun exchangeAuthorizationCode(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        exchangedAuthorizationCode = code
        exchangedCodeVerifier = codeVerifier
        return Ok(
            TokenResponseWithContext(
                accessToken = "access-token-from-code-secret",
                tokenType = "Bearer",
                cNonce = "nonce-from-code",
            ),
        ).asResult()
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
                credentialConfigurationsSupported = mapOf("identity" to CredentialConfigurationSupported(format = "dc+sd-jwt")),
            )
    }
}
