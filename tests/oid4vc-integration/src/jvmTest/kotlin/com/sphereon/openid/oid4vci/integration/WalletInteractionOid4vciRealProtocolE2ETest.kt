/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciHolderIssuanceExecutor
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciHolderIssuanceOptions
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciIssuanceOptionsProvider
import com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciWalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.protocol.oid4vci.WalletStoreOid4vciCredentialResponseReceiver
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ContributesTo(SessionScope::class)
interface WalletInteractionOid4vciStoreTestGraph {
    val walletCredentialStore: WalletCredentialStore
}

class WalletInteractionOid4vciRealProtocolE2ETest {
    companion object {
        private const val CREDENTIAL_CONFIG_ID = "NeutralOid4vciDegreeSdJwt"
        private const val SD_JWT_VCT = "https://issuer.example.com/public/schema/vct/NeutralOid4vciDegreeSdJwt"
        private const val ISSUER_SIGNING_KEY_ALIAS = "neutral-oid4vci-issuer-signing-key"
        private const val HOLDER_SIGNING_KEY_ALIAS = "neutral-oid4vci-holder-proof-key"
        private const val WALLET_INSTANCE_ID = "wallet-interaction-oid4vci-e2e"
        private const val WALLET_CLIENT_ID = "https://wallet.example.com"

        init {
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentialConfigurationIds",
                CREDENTIAL_CONFIG_ID,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].format",
                "dc+sd-jwt",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].vct",
                SD_JWT_VCT,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].scope",
                "neutral_oid4vci_degree",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].bindingMethods",
                "did:key,did:jwk,jwk",
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
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingKeyAlias",
                ISSUER_SIGNING_KEY_ALIAS,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingKeyMode",
                "did:jwk",
            )
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.scopeBinding", "TENANT")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.scopeBinding", "TENANT")
        }
    }

    private val ctx = Oid4vciTestContext(this)
    private val issuerUrl = "https://issuer.example.com"
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    @Test
    fun neutralInteractionEngineReceivesOid4vciCredentialEndToEnd() =
        runTest {
            wireInProcessAdapters()
            ctx.ensureAsSigningKey()
            ensureIssuerSigningKey()
            ensureHolderSigningKey()

            val graph = ctx.session.graph as Oid4vciIssuanceTestGraph
            val issuer = graph.oid4vciIssuerService
            val holder = graph.oid4vciHolder
            val credentialStore = (ctx.session.graph as WalletInteractionOid4vciStoreTestGraph).walletCredentialStore
            val offerResult =
                issuer.createCredentialOffer(
                    CreateCredentialOfferArgs(
                        issuerId = issuerUrl,
                        credentialConfigurationIds = listOf(CREDENTIAL_CONFIG_ID),
                        preAuthorizedCodeGrant = true,
                    ),
                )
            assertTrue(
                offerResult.isOk,
                "Offer creation should succeed: ${if (offerResult.isErr) offerResult.error.message.defaultMessage else ""}",
            )
            val preAuthorizedCode = offerResult.value.offer.grants!!.preAuthorizedCode!!.preAuthorizedCode

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
                                            credentialConfigurationId = CREDENTIAL_CONFIG_ID,
                                        )
                                },
                            credentialReceiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore),
                        ),
                )
            val engine = DefaultWalletInteractionEngine(adapters = listOf(adapter))

            val session =
                engine.start(
                    WalletInteractionInput(
                        walletInstanceId = WALLET_INSTANCE_ID,
                        entryPoint = WalletEntryPoint.rawQr(offerResult.value.offerUri),
                    ),
                )
            val startStateJson = json.encodeToString(session.state)

            assertEquals(WalletProtocol.OID4VCI, session.state.protocol)
            assertEquals(WalletInteractionStatus.CredentialOfferReview, session.state.status)
            assertFalse(startStateJson.contains(preAuthorizedCode))

            engine.dispatch(session.sessionId, WalletInteractionAction.continueFlow())
            val receivedState = engine.observe(session.sessionId).value
            val receivedStateJson = json.encodeToString(receivedState)

            assertEquals(WalletInteractionStatus.ReceivedCredentialReview, receivedState.status, "Received state: $receivedState")
            assertFalse(receivedState.terminal)
            assertFalse(receivedStateJson.contains("access_token"))
            assertFalse(receivedStateJson.contains(preAuthorizedCode))

            engine.dispatch(session.sessionId, WalletInteractionAction.acceptReceivedCredential())
            val completedState = engine.observe(session.sessionId).value

            assertEquals(WalletInteractionStatus.Completed, completedState.status)
            assertTrue(completedState.terminal)

            val metadataResult = credentialStore.listMetadata(WALLET_INSTANCE_ID)
            assertTrue(
                metadataResult.isOk,
                "Stored credential metadata should be readable: ${if (metadataResult.isErr) metadataResult.error.message.defaultMessage else ""}",
            )
            val storedMetadata = metadataResult.value.single()
            assertEquals(CredentialLifecycleState.ACTIVE, storedMetadata.lifecycleSummary.lifecycleState)
            assertEquals(CREDENTIAL_CONFIG_ID, storedMetadata.credentialConfigurationId)
            assertEquals(setOf(SD_JWT_VCT), storedMetadata.credentialTypeRefs.map { it.value }.toSet())

            val recordResult = credentialStore.getCredential(WALLET_INSTANCE_ID, storedMetadata.credentialRecordId)
            assertTrue(
                recordResult.isOk,
                "Stored credential should be readable: ${if (recordResult.isErr) recordResult.error.message.defaultMessage else ""}",
            )
            val storedRecord = assertNotNull(recordResult.value)
            assertEquals(HOLDER_SIGNING_KEY_ALIAS, storedRecord.instances.single().holderKeyRef?.alias)
        }

    private fun wireInProcessAdapters() {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters
        val holder = (ctx.session.graph as WalletTestAdapterHolderGraph).walletTestAdapterHolder
        holder.adapters = adapters
    }

    private suspend fun ensureIssuerSigningKey() {
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
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

    private suspend fun ensureHolderSigningKey() {
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
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
}
