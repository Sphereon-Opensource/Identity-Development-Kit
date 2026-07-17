/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.QrCodeOptions
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialSubjectExtractor
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.interaction.WalletCredentialSelection
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityGate
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityGrant
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.WscdAwareExecutionPlanner
import com.sphereon.wallet.interaction.impl.WscdExecutionProfileSource
import com.sphereon.wallet.interaction.protocol.oid4vci.HolderServiceOid4vciCredentialRequestProofProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciHolderIssuanceExecutor
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciHolderIssuanceOptions
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciIssuanceOptionsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciIssuedCredentialAcceptance
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciKeyAttestationProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciRefreshTokenGrantProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciWalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.protocol.oid4vci.WalletStoreOid4vciCredentialResponseReceiver
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpPresentationSecurityAttributes
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpWalletConfigProvider
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpWalletInteractionProtocolAdapter
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.sphereon.openid.oid4vp.holder.WalletConfig as Oid4vpWalletConfig

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletInteractionClient>())
class Oid4vcIntegrationWalletInteractionClient :
    WalletInteractionClient by DefaultWalletInteractionEngine(
        sensitiveInputAuthority = integrationSensitiveInputAuthority(),
    )

@ContributesTo(SessionScope::class)
interface WalletInteractionOid4vpStoreTestGraph {
    val walletCredentialStore: WalletCredentialStore
    val walletIssuanceSessionStore: WalletIssuanceSessionStore
    val walletIdentityResolver: WalletIdentityResolver
    val credentialSubjectExtractor: CredentialSubjectExtractor
    val verifySdJwtVcCommand: VerifySdJwtVcCommand
}

class WalletInteractionOid4vpDcqlRealProtocolE2ETest {
    companion object {
        private const val CREDENTIAL_CONFIG_ID = "UniversityDegree"
        private const val SD_JWT_CONFIG_ID = "UniversityDegreeSdJwt"
        private const val SD_JWT_VCT = "https://issuer.example.com/public/schema/vct/UniversityDegreeSdJwt"
        private const val ISSUER_SIGNING_KEY_ALIAS = "issuer-vc-signing-key"
        private const val HOLDER_SIGNING_KEY_ALIAS = "wallet-interaction-oid4vp-holder-proof-key"
        private const val VERIFIER_SIGNING_KEY_ALIAS = "verifier-jar-signing-key"
        private const val WALLET_CLIENT_ID = "https://wallet.example.com"
        private const val SESSION_WALLET_UNIT_ID = "wallet-interaction-oid4vp-e2e"
        private const val WALLET_UNIT_ID = "wallet-unit-oid4vp-e2e"
        private const val WALLET_ACCOUNT_ID = "wallet-account-oid4vp-e2e"
        private const val ACTIVATION_DECISION_ID = "activation-oid4vp-e2e"
        private const val HSM_OPERATION_TYPE = "wallet.sign"
        private const val HSM_OPERATION_HASH = "sha256:oid4vp-real-protocol-e2e"
        private const val HSM_NONCE = "nonce-oid4vp-e2e"

        init {
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentialConfigurationIds",
                "$CREDENTIAL_CONFIG_ID,$SD_JWT_CONFIG_ID",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].format",
                "jwt_vc_json",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].scope",
                "degree",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].bindingMethods",
                "did:key,did:jwk",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].proofTypes.jwt.signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].validityPeriod",
                "P365D",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].credentialDefinition.types",
                "VerifiableCredential,UniversityDegreeCredential",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingKeyAlias",
                ISSUER_SIGNING_KEY_ALIAS,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingKeyMode",
                "did:jwk",
            )

            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].format",
                "dc+sd-jwt",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].vct",
                SD_JWT_VCT,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].scope",
                "degree_sdjwt",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].bindingMethods",
                "did:key,did:jwk,jwk",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].proofTypes.jwt.signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].validityPeriod",
                "P365D",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].signingKeyAlias",
                ISSUER_SIGNING_KEY_ALIAS,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].signingKeyMode",
                "did:jwk",
            )
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.scopeBinding", "TENANT")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.scopeBinding", "TENANT")
        }
    }

    private val ctx = Oid4vciTestContext(this)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    private val issuerUrl = "https://issuer.example.com"
    private val verifierClientId = "https://verifier.example.com"

    @Test
    fun neutralInteractionEnginePresentsDcqlCredentialEndToEnd() =
        runTest {
            WalletE2ETestRequestObjectSigningConfig.enableDidJwkSigning()
            try {
                wireInProcessAdapters()
                ctx.ensureAsSigningKey()
                ensureIssuerSigningKey()
                ensureHolderSigningKey()
                ensureVerifierSigningKey()

                val credentialStore = (ctx.session.graph as WalletInteractionOid4vpStoreTestGraph).walletCredentialStore
                val sdJwtRecord = issueSdJwtCredential(credentialStore)
                val request = createVerifierAuthorizationRequest()

                val holder = (ctx.session.graph as Oid4vpPresentationTestGraph).oid4vpHolder
                val adapter =
                    Oid4vpWalletInteractionProtocolAdapter.walletStoreBacked(
                        holder = holder,
                        credentialStore = credentialStore,
                        walletConfigProvider =
                            object : Oid4vpWalletConfigProvider {
                                override suspend fun walletConfig(
                                    context: com.sphereon.wallet.interaction.WalletInteractionContext,
                                    state: com.sphereon.wallet.interaction.WalletInteractionState,
                                ): Oid4vpWalletConfig = Oid4vpWalletConfig(audience = WALLET_CLIENT_ID)
                            },
                    )
                val securityGate = RecordingSecurityGate()
                val engine =
                    DefaultWalletInteractionEngine(
                        sensitiveInputAuthority = integrationSensitiveInputAuthority(),
                        adapters = listOf(adapter),
                        protocolExecutor =
                            WscdAwareExecutionPlanner(
                                // No WSCD profile is known for this holder key in-process; the
                                // delegate's default SPLIT decision (PRESENTATION_SHARING /
                                // USER_PRESENT) stands, matching production when the profile
                                // source has no capability-derived hint for the request.
                                profileSource = WscdExecutionProfileSource { null },
                            ),
                        securityGate = securityGate,
                    )

                val input =
                    WalletInteractionInput(
                        walletUnitId = SESSION_WALLET_UNIT_ID,
                        entryPoint = WalletEntryPoint.rawQr(request.requestUri),
                        executionMode = WalletInteractionExecutionMode.SPLIT,
                        metadata =
                            mapOf(
                                Oid4vpPresentationSecurityAttributes.WALLET_UNIT_ID to WALLET_UNIT_ID,
                                Oid4vpPresentationSecurityAttributes.WALLET_ACCOUNT_ID to WALLET_ACCOUNT_ID,
                                Oid4vpPresentationSecurityAttributes.ACTIVATION_DECISION_ID to ACTIVATION_DECISION_ID,
                                Oid4vpPresentationSecurityAttributes.OPERATION_TYPE to HSM_OPERATION_TYPE,
                                Oid4vpPresentationSecurityAttributes.OPERATION_HASH to HSM_OPERATION_HASH,
                                Oid4vpPresentationSecurityAttributes.NONCE to HSM_NONCE,
                            ),
                    )
                val session = engine.start(input)

                assertEquals(WalletInteractionExecutionMode.SPLIT, input.executionMode)
                assertEquals(WalletProtocol.OID4VP, session.state.protocol)
                assertEquals(WalletInteractionStatus.CredentialSelection, session.state.status)
                val requirement =
                    assertNotNull(
                        session.state.credentialSelection
                            ?.requirements
                            ?.singleOrNull()
                    )
                assertEquals(SD_JWT_CONFIG_ID, requirement.id)
                assertEquals(listOf(sdJwtRecord.id), requirement.candidateCredentialIds)

                engine.dispatch(
                    session.sessionId,
                    WalletInteractionAction.selectCredentials(
                        WalletCredentialSelection(
                            selectedCredentialIdsByRequirement = mapOf(requirement.id to listOf(sdJwtRecord.id)),
                        ),
                    ),
                )
                val completedState = engine.observe(session.sessionId).value
                assertEquals(WalletInteractionStatus.Completed, completedState.status, "Final interaction state: $completedState")
                assertTrue(completedState.terminal, "Wallet interaction session should be terminal after successful direct_post submission")
                assertEquals(WalletSecurityOperation.PRESENTATION_SHARING, securityGate.lastRequest?.operation)
                assertEquals(WalletSecurityAssurance.USER_PRESENT, securityGate.lastRequest?.requiredAssurance)
                assertEquals(HOLDER_SIGNING_KEY_ALIAS, securityGate.lastRequest?.keyRef)
                assertEquals(WALLET_UNIT_ID, securityGate.lastRequest?.walletUnitId)
                assertEquals(WALLET_ACCOUNT_ID, securityGate.lastRequest?.walletAccountId)
                assertEquals(ACTIVATION_DECISION_ID, securityGate.lastRequest?.activationDecisionId)
                assertEquals(HSM_OPERATION_TYPE, securityGate.lastRequest?.operationType)
                assertEquals(HSM_OPERATION_HASH, securityGate.lastRequest?.operationHash)
                assertEquals(HSM_NONCE, securityGate.lastRequest?.nonce)

                val postPresentMetaResult = credentialStore.findByCredentialTypeRef(SESSION_WALLET_UNIT_ID, sdJwtRecord.credentialTypeRefs.first())
                assertTrue(
                    postPresentMetaResult.isOk,
                    "findByCredentialTypeRef after interaction should succeed: ${if (postPresentMetaResult.isErr) postPresentMetaResult.error.message.defaultMessage else ""}",
                )
                val sdJwtMeta = postPresentMetaResult.value.single()
                assertEquals(1, sdJwtMeta.boundInstanceCount, "neutral OID4VP adapter should record one presentation binding")
                assertEquals(CredentialLifecycleState.ACTIVE, sdJwtMeta.lifecycleSummary.lifecycleState)

                assertVerifierVerified(request.correlationId)
            } finally {
                WalletE2ETestRequestObjectSigningConfig.disable()
            }
        }

    private fun dispatcher() = (ctx.session.graph as com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher.Graph).httpAdapterDispatcher

    private fun wireInProcessAdapters() {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters
        val holder = (ctx.session.graph as WalletTestAdapterHolderGraph).walletTestAdapterHolder
        holder.adapters = adapters
    }

    private suspend fun issueSdJwtCredential(credentialStore: WalletCredentialStore): CredentialRecord {
        val issuer = (ctx.session.graph as Oid4vciIssuanceTestGraph).oid4vciIssuerService
        val holder = (ctx.session.graph as Oid4vciIssuanceTestGraph).oid4vciHolder
        val storeGraph = ctx.session.graph as WalletInteractionOid4vpStoreTestGraph
        val issuanceSessionStore = storeGraph.walletIssuanceSessionStore
        val acceptance =
            Oid4vciIssuedCredentialAcceptance(
                verifySdJwtVcCommand = storeGraph.verifySdJwtVcCommand,
                subjectExtractor = storeGraph.credentialSubjectExtractor,
                identityResolver = storeGraph.walletIdentityResolver,
            )
        val offerResult =
            issuer.createCredentialOffer(
                CreateCredentialOfferArgs(
                    issuerId = issuerUrl,
                    credentialConfigurationIds = listOf(SD_JWT_CONFIG_ID),
                    preAuthorizedCodeGrant = true,
                ),
            )
        assertTrue(offerResult.isOk, "Offer creation should succeed")

        val adapter =
            Oid4vciWalletInteractionProtocolAdapter(
                holder = holder,
                issuanceExecutor =
                    Oid4vciHolderIssuanceExecutor(
                        holder = holder,
                        optionsProvider =
                            object : Oid4vciIssuanceOptionsProvider {
                                override suspend fun options(
                                    context: com.sphereon.wallet.interaction.WalletInteractionContext,
                                    state: com.sphereon.wallet.interaction.WalletInteractionState,
                                    resolvedOffer: com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer,
                                ): Oid4vciHolderIssuanceOptions =
                                    Oid4vciHolderIssuanceOptions(
                                        signingKeyId = HOLDER_SIGNING_KEY_ALIAS,
                                        signingAlgorithm = "ES256",
                                        clientId = WALLET_CLIENT_ID,
                                        credentialConfigurationId = SD_JWT_CONFIG_ID,
                                    )
                            },
                        credentialReceiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, issuanceSessionStore, acceptance),
                        credentialStore = credentialStore,
                        issuanceSessionStore = issuanceSessionStore,
                        refreshTokenGrantProvider = Oid4vciRefreshTokenGrantProvider.unsupported,
                        keyAttestationProvider = Oid4vciKeyAttestationProvider.unsupported,
                        // HOLDER_SIGNING_KEY_ALIAS is provisioned directly in the KMS (see
                        // ensureHolderSigningKey()), not through Wsca, so the generic
                        // KMS-resolving provider is correct here.
                        credentialRequestProofProvider = HolderServiceOid4vciCredentialRequestProofProvider(holder),
                    ),
            )
        val engine =
            DefaultWalletInteractionEngine(
                sensitiveInputAuthority = integrationSensitiveInputAuthority(),
                adapters = listOf(adapter),
            )

        val session =
            engine.start(
                WalletInteractionInput(
                    walletUnitId = SESSION_WALLET_UNIT_ID,
                    entryPoint = WalletEntryPoint.rawQr(offerResult.value.offerUri),
                ),
            )
        assertEquals(WalletProtocol.OID4VCI, session.state.protocol)
        assertEquals(WalletInteractionStatus.CredentialOfferReview, session.state.status)

        engine.dispatch(
            session.sessionId,
            WalletInteractionAction.selectCredentials(
                WalletCredentialSelection(
                    selectedCredentialIdsByRequirement =
                        mapOf("oid4vci-offer" to session.state.credentialOffer!!.credentialConfigurationIds),
                ),
            ),
        )
        val completedState = engine.observe(session.sessionId).value
        assertEquals(WalletInteractionStatus.Completed, completedState.status, "Credential issuance state: $completedState")
        assertTrue(completedState.terminal, "Credential issuance session should be terminal")

        val metadataResult = credentialStore.listMetadata(SESSION_WALLET_UNIT_ID)
        assertTrue(
            metadataResult.isOk,
            "Issued credential metadata should be readable: ${if (metadataResult.isErr) metadataResult.error.message.defaultMessage else ""}",
        )
        val metadata =
            metadataResult.value.singleOrNull { it.credentialConfigurationId == SD_JWT_CONFIG_ID }
                ?: error("Expected one stored credential for '$SD_JWT_CONFIG_ID', found ${metadataResult.value.map { it.credentialConfigurationId }}")
        val recordResult = credentialStore.getCredential(SESSION_WALLET_UNIT_ID, metadata.credentialRecordId)
        assertTrue(
            recordResult.isOk,
            "Issued credential should be readable: ${if (recordResult.isErr) recordResult.error.message.defaultMessage else ""}",
        )
        return assertNotNull(recordResult.value)
    }

    private suspend fun createVerifierAuthorizationRequest(): VerifierRequest {
        val dcqlQuery =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = SD_JWT_CONFIG_ID,
                            format = "dc+sd-jwt",
                            claims = listOf(DcqlClaimQuery(path = listOf("degree"))),
                        ),
                    ),
            )
        val input =
            CreateAuthorizationRequestInput(
                dcqlQuery = dcqlQuery,
                clientId = verifierClientId,
                state = "wallet-interaction-e2e-state",
                responseUri = "$verifierClientId/oid4vp/auth/response",
                qrCodeOptions = QrCodeOptions(),
            )
        val response =
            dispatcher().dispatch(
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/oid4vp/backend/auth/requests",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = json.encodeToString(CreateAuthorizationRequestInput.serializer(), input),
                ),
            )
        assertEquals(201, response.statusCode, "Verifier authorization request creation should return 201. Body: ${response.body}")
        val output = json.decodeFromString(CreateAuthorizationRequestOutput.serializer(), assertNotNull(response.body))
        return VerifierRequest(
            correlationId = assertNotNull(output.correlationId),
            requestUri = assertNotNull(output.requestUri),
        )
    }

    private suspend fun assertVerifierVerified(correlationId: String) {
        val response =
            dispatcher().dispatch(
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vp/backend/auth/requests/$correlationId",
                ),
            )
        assertEquals(200, response.statusCode, "Status check should return 200. Body: ${response.body}")
        val statusOutput = json.decodeFromString(GetAuthorizationRequestStatusOutput.serializer(), assertNotNull(response.body))
        assertEquals(correlationId, statusOutput.correlationId)
        assertEquals(
            AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
            statusOutput.status,
            "Verifier session should reach AUTHORIZATION_RESPONSE_VERIFIED after neutral wallet interaction presentation",
        )
    }

    private suspend fun ensureIssuerSigningKey() {
        val kms =
            ctx.session.graph
                .asKeyManagerServiceGraph()
                .keyManagerService
        val result =
            kms.generateKeyResult(
                alias = ISSUER_SIGNING_KEY_ALIAS,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
        assertTrue(
            result.isOk,
            "Issuer signing key generation should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
    }

    private suspend fun ensureVerifierSigningKey() {
        val kms =
            ctx.session.graph
                .asKeyManagerServiceGraph()
                .keyManagerService
        val result =
            kms.generateKeyResult(
                alias = VERIFIER_SIGNING_KEY_ALIAS,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
        assertTrue(
            result.isOk,
            "Verifier JAR signing key generation should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
    }

    private suspend fun ensureHolderSigningKey() {
        val kms =
            ctx.session.graph
                .asKeyManagerServiceGraph()
                .keyManagerService
        val result =
            kms.generateKeyResult(
                alias = HOLDER_SIGNING_KEY_ALIAS,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
        assertTrue(
            result.isOk,
            "Holder proof key generation should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
    }

    private class RecordingSecurityGate : WalletSecurityGate {
        var lastRequest: WalletSecurityGateRequest? = null

        override suspend fun authorize(request: WalletSecurityGateRequest): WalletSecurityGateResult {
            lastRequest = request
            return WalletSecurityGateResult.Authorized(
                WalletSecurityGrant(
                    grantId = request.operationId,
                    assurance = request.requiredAssurance,
                    evidence =
                        mapOf(
                            "operation" to request.operation.name,
                            "keyRef" to request.keyRef.orEmpty(),
                            "walletUnitId" to request.walletUnitId.orEmpty(),
                            "operationHash" to request.operationHash.orEmpty(),
                        ),
                ),
            )
        }
    }

    private data class VerifierRequest(
        val correlationId: String,
        val requestUri: String,
    )
}
