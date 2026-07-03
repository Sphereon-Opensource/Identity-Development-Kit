/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.events.EncryptedPart
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventBuilder
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.SessionEventService
import com.sphereon.core.events.UserEventService
import com.sphereon.core.events.impl.DefaultEventBuilder
import com.sphereon.core.events.impl.EventHubImpl
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.DefaultTempUrlPolicy
import com.sphereon.data.store.blob.InMemoryBlobStoreConfig
import com.sphereon.data.store.blob.impl.BlobStoreService
import com.sphereon.data.store.blob.impl.DefaultBlobService
import com.sphereon.data.store.blob.impl.DefaultRetentionPolicyService
import com.sphereon.data.store.blob.impl.KvBlobMetadataIndex
import com.sphereon.data.store.blob.memory.InMemoryBlobBackingStorageImpl
import com.sphereon.data.store.blob.memory.InMemoryBlobStoreFactoryImpl
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.data.store.kv.memory.InMemoryKvBackingStorageImpl
import com.sphereon.data.store.kv.memory.InMemoryKvStoreFactoryImpl
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.store.BlobWalletCredentialStore
import com.sphereon.wallet.credential.store.WalletCredentialBodyProtector
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionExecutionMode
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletStoreOid4vciCredentialResponseReceiverTest {
    @Test
    fun receiverStoresCredentialResponseInWalletStoreWithoutPuttingBodyInMetadata() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore)
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-store-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        mapOf(
                            "credential_configuration_id" to CREDENTIAL_CONFIGURATION_ID,
                            "holder_key_alias" to "wallet-holder-key-1",
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletInstanceId = WALLET_INSTANCE_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletInstanceId = WALLET_INSTANCE_ID,
                    status = WalletInteractionStatus.ReceivedCredentialReview,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = "header.payload.signature"

            val previews =
                receiver.receiveCredentialResponse(
                    context = context,
                    state = state,
                    resolvedOffer = resolvedOffer(),
                    credentialResponse =
                        CredentialResponse(
                            credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawCredential))),
                            notificationId = "notification-1",
                        ),
                )

            assertEquals(1, previews.size)
            assertEquals("Employee Credential", previews.single().name)
            assertEquals(CredentialFormat.SD_JWT_DC.value, previews.single().format)

            val metadata =
                credentialStore
                    .listMetadata(
                        WALLET_INSTANCE_ID,
                        CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID),
                    ).getOrThrow()
                    .single()

            assertEquals(CREDENTIAL_CONFIGURATION_ID, metadata.credentialConfigurationId)
            assertEquals("https://issuer.example", metadata.issuerRef.value)
            assertEquals(1, metadata.instanceCount)
            assertEquals(1, metadata.activeInstanceCount)
            assertTrue(
                metadata.credentialTypeRefs.any { ref ->
                    ref.format == CredentialFormat.SD_JWT_DC &&
                        ref.kind == CredentialTypeRefKind.SD_JWT_VCT &&
                        ref.source == CredentialTypeRefSource.ISSUER_METADATA &&
                        ref.value == EMPLOYEE_VCT
                },
            )
            assertFalse(metadata.toString().contains(rawCredential), "Credential body must not be present in metadata sidecars")

            val stored =
                credentialStore
                    .getCredential(WALLET_INSTANCE_ID, metadata.credentialRecordId)
                    .getOrThrow()
            assertNotNull(stored)
            assertEquals(rawCredential, stored.instances.single().raw)
            assertEquals(
                "wallet-holder-key-1",
                stored.instances
                    .single()
                    .holderKeyRef
                    ?.alias
            )
            assertEquals(CREDENTIAL_CONFIGURATION_ID, stored.issuanceProvenance?.credentialConfigurationId)
            assertEquals(sessionId.value, stored.issuanceProvenance?.issuanceSessionId)
        }

    @Test
    fun receiverKeepsMdocCredentialConfigurationIdSeparateFromDoctype() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore)
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-mdoc-store-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        mapOf(
                            "credential_configuration_id" to MDOC_CREDENTIAL_CONFIGURATION_ID,
                            "holder_key_alias" to "wallet-mdoc-holder-key-1",
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletInstanceId = WALLET_INSTANCE_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletInstanceId = WALLET_INSTANCE_ID,
                    status = WalletInteractionStatus.ReceivedCredentialReview,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawMdocCredential = "mdoc-cbor-payload"

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedMdocOffer(),
                credentialResponse =
                    CredentialResponse(
                        credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawMdocCredential))),
                    ),
            )

            val metadata =
                credentialStore
                    .listMetadata(
                        WALLET_INSTANCE_ID,
                        CredentialMetadataFilter(credentialConfigurationId = MDOC_CREDENTIAL_CONFIGURATION_ID),
                    ).getOrThrow()
                    .single()

            assertEquals(MDOC_CREDENTIAL_CONFIGURATION_ID, metadata.credentialConfigurationId)
            assertEquals(CredentialFormat.MSO_MDOC, metadata.format)
            assertTrue(
                metadata.credentialTypeRefs.any { ref ->
                    ref.format == CredentialFormat.MSO_MDOC &&
                        ref.kind == CredentialTypeRefKind.MDOC_DOCTYPE &&
                        ref.source == CredentialTypeRefSource.ISSUER_METADATA &&
                        ref.value == MDOC_DOCTYPE &&
                        ref.primary
                },
            )
            assertFalse(
                metadata.credentialTypeRefs.any { ref -> ref.value == MDOC_CREDENTIAL_CONFIGURATION_ID },
                "The credential configuration id must not be reused as the mdoc credential type reference.",
            )

            val stored =
                credentialStore
                    .getCredential(WALLET_INSTANCE_ID, metadata.credentialRecordId)
                    .getOrThrow()
            assertNotNull(stored)
            assertEquals(rawMdocCredential, stored.instances.single().raw)
            assertEquals(MDOC_CREDENTIAL_CONFIGURATION_ID, stored.issuanceProvenance?.credentialConfigurationId)
            assertEquals(
                MDOC_DOCTYPE,
                stored.issuanceProvenance
                    ?.expectedCredentialTypeRefs
                    ?.single()
                    ?.value
            )
        }

    private fun resolvedOffer(): ResolvedCredentialOffer =
        ResolvedCredentialOffer(
            offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example",
                    credentialConfigurationIds = listOf(CREDENTIAL_CONFIGURATION_ID),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-authorized-code",
                                ),
                        ),
                ),
            issuerMetadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example",
                    credentialEndpoint = "https://issuer.example/credential",
                    notificationEndpoint = "https://issuer.example/notification",
                    credentialConfigurationsSupported =
                        mapOf(
                            CREDENTIAL_CONFIGURATION_ID to
                                CredentialConfigurationSupported(
                                    format = CredentialFormat.SD_JWT_DC.value,
                                    vct = EMPLOYEE_VCT,
                                    display = listOf(DisplayProperties(name = "Employee Credential")),
                                ),
                        ),
                    display = listOf(DisplayProperties(name = "Example Issuer")),
                ),
        )

    private fun resolvedMdocOffer(): ResolvedCredentialOffer =
        ResolvedCredentialOffer(
            offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example",
                    credentialConfigurationIds = listOf(MDOC_CREDENTIAL_CONFIGURATION_ID),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-authorized-code",
                                ),
                        ),
                ),
            issuerMetadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example",
                    credentialEndpoint = "https://issuer.example/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            MDOC_CREDENTIAL_CONFIGURATION_ID to
                                CredentialConfigurationSupported(
                                    format = CredentialFormat.MSO_MDOC.value,
                                    doctype = MDOC_DOCTYPE,
                                    display = listOf(DisplayProperties(name = "Mobile Driving Licence")),
                                ),
                        ),
                ),
        )

    private companion object {
        const val WALLET_INSTANCE_ID = "wallet-oid4vci-receiver"
        const val CREDENTIAL_CONFIGURATION_ID = "EmployeeCredential"
        const val EMPLOYEE_VCT = "https://credentials.example.com/employee"
        const val MDOC_CREDENTIAL_CONFIGURATION_ID = "MobileDrivingLicence"
        const val MDOC_DOCTYPE = "org.iso.18013.5.1.mDL"
    }
}

private class ReceiverTestBlobStoreService(
    private val store: BlobStore,
    private val storeId: String = "memory",
) : BlobStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(storeId)

    override fun getStoreConfig(storeId: String) = InMemoryBlobStoreConfig(id = storeId)

    override fun getStore(storeId: String) = store
}

private class ReceiverTestKvStoreService(
    private val store: KvStore,
) : KvStoreService {
    override fun getStoreIds(): Array<String> = arrayOf(KvBlobMetadataIndex.STORE_ID)

    override fun getStoreConfig(storeId: String) = InMemoryKvStoreConfig(id = storeId, scopeBinding = KvStoreScopeBinding.APP)

    override fun getStore(storeId: String) = store
}

private class ReceiverTestSessionExecution : com.sphereon.core.api.context.SessionExecution {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for receiver store tests")
    override val log: SessionLogService = ReceiverTestNoOpLogService(sessionContext)
    override val conf: com.sphereon.core.api.context.ContextConfig
        get() = throw NotImplementedError("Not needed for receiver store tests")
}

private class ReceiverTestNoOpLogService(
    override val sessionContext: SessionContext,
) : SessionLogService {
    override val id: String = "oid4vci-receiver-test-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("Not needed for receiver store tests")

    override suspend fun setConfig(config: LoggerConfig): LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, com.sphereon.core.api.error.IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for receiver store tests")
}

private class ReceiverTestSessionEventService : SessionEventService {
    private val hub = EventHubImpl()
    override val scope: com.sphereon.core.api.context.IdkScope = com.sphereon.core.api.context.IdkScope.SESSION
    override val eventHub: EventHub = hub
    override val parent: UserEventService
        get() = throw NotImplementedError("Not needed for receiver store tests")
    override val sessionContext: SessionContext = NoOpSessionContext

    override suspend fun emit(event: Event) {
        hub.publish(event)
    }

    override suspend fun emit(
        event: Event,
        sign: Boolean,
        encrypt: Boolean,
        keyAlias: String?,
        encryptionKeyAlias: String?,
        encryptParts: Set<EncryptedPart>,
    ) {
        hub.publish(event)
    }

    override fun eventBuilder(): EventBuilder = DefaultEventBuilder(com.sphereon.core.api.context.IdkScope.SESSION)
}

private object ReceiverTestCredentialBodyProtector : WalletCredentialBodyProtector {
    override suspend fun protect(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        plaintext: ByteArray,
    ): IdkResult<ByteArray, IdkError> = Ok("receiver-test-protected:${plaintext.decodeToString().reversed()}".encodeToByteArray())

    override suspend fun open(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        protectedBody: ByteArray,
    ): IdkResult<ByteArray, IdkError> {
        val envelope = protectedBody.decodeToString()
        if (!envelope.startsWith("receiver-test-protected:")) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported receiver test credential body envelope"))
        }
        return Ok(envelope.removePrefix("receiver-test-protected:").reversed().encodeToByteArray())
    }
}

private class ReceiverTestPrivateSessionStore : WalletInteractionPrivateSessionStore {
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
        records.keys.filter { it.first == sessionId }.forEach { records.remove(it) }
    }
}

private fun createOid4vciReceiverTestBlobService(): DefaultBlobService {
    val blobBackingStorage = InMemoryBlobBackingStorageImpl()
    val blobFactory = InMemoryBlobStoreFactoryImpl(blobBackingStorage)
    val memoryStore = blobFactory.create(InMemoryBlobStoreConfig(id = "memory"))

    val kvBackingStorage = InMemoryKvBackingStorageImpl()
    val kvFactory = InMemoryKvStoreFactoryImpl(kvBackingStorage)
    val kvStore = kvFactory.create(InMemoryKvStoreConfig(id = KvBlobMetadataIndex.STORE_ID, scopeBinding = KvStoreScopeBinding.APP))

    return DefaultBlobService(
        blobStoreService = ReceiverTestBlobStoreService(memoryStore),
        metadataIndex = KvBlobMetadataIndex(ReceiverTestKvStoreService(kvStore)),
        retentionPolicyService = DefaultRetentionPolicyService(),
        tempUrlPolicy = DefaultTempUrlPolicy(),
        eventService = ReceiverTestSessionEventService(),
        execution = ReceiverTestSessionExecution(),
    )
}
