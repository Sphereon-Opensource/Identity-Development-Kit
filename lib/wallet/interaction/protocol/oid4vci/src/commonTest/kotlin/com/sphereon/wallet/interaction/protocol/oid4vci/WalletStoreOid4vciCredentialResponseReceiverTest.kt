/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.TDate
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
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
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
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
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.DeviceKeyInfo
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.data.mso.MsoVersion
import com.sphereon.mdoc.data.mso.ValidityInfo
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialRefreshMethod
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.IssuanceDiagnosticCode
import com.sphereon.wallet.credential.IssuanceProvenance
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.RefreshPolicy
import com.sphereon.wallet.credential.RefreshState
import com.sphereon.wallet.credential.SecretRef
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.credential.store.BlobWalletCredentialStore
import com.sphereon.wallet.credential.store.WalletCredentialBodyProtector
import com.sphereon.wallet.impl.CredentialSubjectExtractorImpl
import com.sphereon.wallet.impl.NoOpWalletIdentityResolver
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Builds a minimal SD-JWT compact serialization (`<issuer-jwt>~`) whose issuer-JWT payload is the
 * given JSON. No real signature/disclosures are used - [Oid4vciIssuedCredentialAcceptance] only
 * needs the structure to be valid for `SdJwtCodec.parse` to read the `vct`/`sub` claims.
 */
internal fun buildTestSdJwt(payloadJson: String): String {
    val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().encodeToBase64Url()
    val payload = payloadJson.encodeToByteArray().encodeToBase64Url()
    return "$header.$payload.fakesig~"
}

/**
 * Builds a minimal, real (base64url-encoded CBOR) mdoc `IssuerSigned` structure carrying
 * [doctype] in its MSO, so [Oid4vciIssuedCredentialAcceptance.actualTypeRefs]'s mdoc branch can
 * decode a real doctype instead of failing to derive one.
 */
internal fun buildTestMdocCredential(doctype: String): String {
    val mobileSecurityObjectCodec = MobileSecurityObjectCborCodecImpl()
    val issuerSignedCodec = IssuerSignedCborCodecImpl()
    val now = TDate("2025-01-20T12:00:00Z")
    val mso =
        MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo =
                DeviceKeyInfo(
                    deviceKey =
                        CoseKeyJson
                            .Builder()
                            .withKty(CoseKeyTypeEnum.EC2)
                            .withCrv(CoseCurve.P_256)
                            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                            .build()
                            .toCbor(),
                    keyAuthorizations = null,
                    keyInfo = null,
                    original = null,
                ),
            docType = DocType(doctype),
            validityInfo =
                ValidityInfo(
                    signed = now,
                    validFrom = now,
                    validUntil = now,
                    expectedUpdate = null,
                ),
            original = null,
        )
    val issuerSigned =
        IssuerSigned(
            nameSpaces = emptyMap(),
            issuerAuth =
                CoseSign1<MobileSecurityObject>(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    unprotectedHeader = CoseHeaderCbor(),
                    payload = CborByteString(mobileSecurityObjectCodec.encodeTag24(mso).getOrThrow()),
                    signature = CborByteString(ByteArray(64) { it.toByte() }),
                ),
            original = null,
        )
    return issuerSignedCodec.encode(issuerSigned).getOrThrow().encodeToBase64Url()
}

/**
 * Real [Oid4vciIssuedCredentialAcceptance] backed by the real [CredentialSubjectExtractorImpl] and
 * [NoOpWalletIdentityResolver] (both reached via the module's commonTest-only lib-wallet-impl
 * dependency) plus a caller-supplied verification command (defaults to an always-accepting fake).
 */
private fun testAcceptance(verify: VerifySdJwtVcCommand = FakeVerifySdJwtVcCommand(accept = true)): Oid4vciIssuedCredentialAcceptance =
    Oid4vciIssuedCredentialAcceptance(
        verifySdJwtVcCommand = verify,
        subjectExtractor = CredentialSubjectExtractorImpl(),
        identityResolver = NoOpWalletIdentityResolver(),
    )

class WalletStoreOid4vciCredentialResponseReceiverTest {
    /**
     * Encodes [state] as the single typed OID4VCI private session state blob under the "state"
     * key, matching how the executor/adapter persist it in production.
     */
    private fun oid4vciStateValues(state: Oid4vciPrivateSessionState): Map<String, String> =
        mapOf("state" to Json.encodeToString(Oid4vciPrivateSessionState.serializer(), state))

    @Test
    fun receiverStoresCredentialResponseInWalletStoreWithoutPuttingBodyInMetadata() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-store-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-1"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-store-test","vct":"$EMPLOYEE_VCT"}""")

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
            assertEquals("#003399", previews.single().branding?.backgroundColor)
            assertEquals("#FFFFFF", previews.single().branding?.textColor)

            val metadata =
                credentialStore
                    .listMetadata(
                        WALLET_UNIT_ID,
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
                        // credentialTypeRefs holds the ACTUAL (payload-derived) set, not the
                        // expected (issuer-metadata) set.
                        ref.source == CredentialTypeRefSource.CREDENTIAL_PAYLOAD &&
                        ref.value == EMPLOYEE_VCT
                },
            )
            assertFalse(metadata.toString().contains(rawCredential), "Credential body must not be present in metadata sidecars")

            val stored =
                credentialStore
                    .getCredential(WALLET_UNIT_ID, metadata.credentialRecordId)
                    .getOrThrow()
            assertNotNull(stored)
            assertEquals(rawCredential, stored.instances.single().raw)
            assertEquals("Employee Credential", stored.display.credentialDisplay.single().name)
            assertEquals("#003399", stored.display.credentialDisplay.single().backgroundColor)
            assertEquals("#FFFFFF", stored.display.credentialDisplay.single().textColor)
            assertEquals(
                "wallet-holder-key-1",
                stored.instances
                    .single()
                    .holderKeyRef
                    ?.alias
            )
            assertEquals(CREDENTIAL_CONFIGURATION_ID, stored.issuanceProvenance?.credentialConfigurationId)
            assertEquals(sessionId.value, stored.issuanceProvenance?.issuanceSessionId)
            assertEquals(emptyList(), stored.issuanceProvenance?.diagnostics, "issued vct matches issuer metadata; no mismatch diagnostic expected")
        }

    @Test
    fun receiverKeepsMdocCredentialConfigurationIdSeparateFromDoctype() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-mdoc-store-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = MDOC_CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-mdoc-holder-key-1"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawMdocCredential = buildTestMdocCredential(MDOC_DOCTYPE)

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
                        WALLET_UNIT_ID,
                        CredentialMetadataFilter(credentialConfigurationId = MDOC_CREDENTIAL_CONFIGURATION_ID),
                    ).getOrThrow()
                    .single()

            assertEquals(MDOC_CREDENTIAL_CONFIGURATION_ID, metadata.credentialConfigurationId)
            assertEquals(CredentialFormat.MSO_MDOC, metadata.format)
            assertTrue(
                metadata.credentialTypeRefs.any { ref ->
                    ref.format == CredentialFormat.MSO_MDOC &&
                        ref.kind == CredentialTypeRefKind.MDOC_DOCTYPE &&
                        // credentialTypeRefs holds the ACTUAL (payload-derived) set, not the
                        // expected (issuer-metadata) set.
                        ref.source == CredentialTypeRefSource.CREDENTIAL_PAYLOAD &&
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
                    .getCredential(WALLET_UNIT_ID, metadata.credentialRecordId)
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

    @Test
    fun receiverPersistsRefreshTokenAndSetsRefreshStateWhenPresent() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val issuanceSessionStore = FakeWalletIssuanceSessionStore()
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, issuanceSessionStore, testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-refresh-token-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-refresh"),
                                tokens =
                                    Oid4vciPrivateSessionState.TokenLeg(
                                        accessToken = "unused-test-access-token",
                                        refreshToken = "refresh-token-value",
                                    ),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse =
                    CredentialResponse(
                        credentials =
                            listOf(
                                CredentialResponseItem(
                                    credential =
                                        JsonPrimitive(
                                            buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-refresh","vct":"$EMPLOYEE_VCT"}"""),
                                        ),
                                ),
                            ),
                    ),
            )

            assertEquals(1, issuanceSessionStore.storedRefreshTokens.size)
            val metadata =
                credentialStore
                    .listMetadata(
                        WALLET_UNIT_ID,
                        CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID),
                    ).getOrThrow()
                    .single()
            val stored =
                credentialStore
                    .getCredential(WALLET_UNIT_ID, metadata.credentialRecordId)
                    .getOrThrow()
            assertNotNull(stored)
            val refreshState = assertNotNull(stored.refreshState, "refreshState must be set when a refresh_token is present")
            assertEquals(CredentialRefreshMethod.OID4VCI_REISSUANCE, refreshState.refreshMethod)
            assertNotNull(refreshState.refreshTokenRef)
            assertEquals("refresh-token-value", issuanceSessionStore.storedRefreshTokens[metadata.credentialRecordId])
        }

    @Test
    fun receiverLeavesRefreshStateNullWhenNoRefreshTokenPresent() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val issuanceSessionStore = FakeWalletIssuanceSessionStore()
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, issuanceSessionStore, testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-no-refresh-token-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-no-refresh"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse =
                    CredentialResponse(
                        credentials =
                            listOf(
                                CredentialResponseItem(
                                    credential =
                                        JsonPrimitive(
                                            buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-no-refresh","vct":"$EMPLOYEE_VCT"}"""),
                                        ),
                                ),
                            ),
                    ),
            )

            assertTrue(issuanceSessionStore.storedRefreshTokens.isEmpty())
            val metadata =
                credentialStore
                    .listMetadata(
                        WALLET_UNIT_ID,
                        CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID),
                    ).getOrThrow()
                    .single()
            val stored =
                credentialStore
                    .getCredential(WALLET_UNIT_ID, metadata.credentialRecordId)
                    .getOrThrow()
            assertNotNull(stored)
            assertEquals(null, stored.refreshState)
        }

    // -----------------------------------------------------------------------------------------
    // Coverage: issuer-verification gate, type-ref reconciliation, and subject extraction on
    // credential receipt.
    // -----------------------------------------------------------------------------------------

    @Test
    fun receiverRejectsCredentialWhenIssuerVerificationFails() =
        runTest {
            // The receiver verifies the issued SD-JWT VC on receipt; when issuer-signature
            // verification fails it must NOT store the credential and must surface an error.
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver =
                WalletStoreOid4vciCredentialResponseReceiver(
                    credentialStore,
                    FakeWalletIssuanceSessionStore(),
                    testAcceptance(verify = FakeVerifySdJwtVcCommand(accept = false)),
                )
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-verification-failed-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-verify-fail"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-verify-fail","vct":"$EMPLOYEE_VCT"}""")

            val thrown =
                assertFailsWith<IllegalStateException> {
                    receiver.receiveCredentialResponse(
                        context = context,
                        state = state,
                        resolvedOffer = resolvedOffer(),
                        credentialResponse =
                            CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawCredential)))),
                    )
                }
            assertTrue(
                thrown.message.orEmpty().contains("issuer-signature verification failed"),
                "expected an issuer-signature verification failure message, got: ${thrown.message}",
            )
            assertEquals(0, credentialStore.listMetadata(WALLET_UNIT_ID).getOrThrow().size, "no document should have been stored")
        }

    @Test
    fun receiverKeepsActualTypeRefsCanonicalAndRecordsMismatchDiagnostic() =
        runTest {
            val actualVct = "https://credentials.example.com/employee-v2"
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-mismatch-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-mismatch"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-mismatch","vct":"$actualVct"}""")

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse = CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawCredential)))),
            )

            val metadata =
                credentialStore
                    .listMetadata(WALLET_UNIT_ID, CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID))
                    .getOrThrow()
                    .single()
            val stored = credentialStore.getCredential(WALLET_UNIT_ID, metadata.credentialRecordId).getOrThrow()
            assertNotNull(stored)
            assertEquals(setOf(actualVct), stored.credentialTypeRefs.map { it.value }.toSet())
            assertEquals(
                setOf(EMPLOYEE_VCT),
                stored.issuanceProvenance
                    ?.expectedCredentialTypeRefs
                    ?.map { it.value }
                    ?.toSet(),
            )
            val diagnostic = stored.issuanceProvenance?.diagnostics?.single()
            assertEquals(IssuanceDiagnosticCode.CREDENTIAL_TYPE_REF_MISMATCH, diagnostic?.code)
            assertEquals(setOf(EMPLOYEE_VCT), diagnostic?.expectedCredentialTypeRefs?.map { it.value }?.toSet())
            assertEquals(setOf(actualVct), diagnostic?.actualCredentialTypeRefs?.map { it.value }?.toSet())
        }

    @Test
    fun receiverRejectsIssuedCredentialWithoutPayloadTypeRefs() =
        runTest {
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-missing-vct-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-missing-vct"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-missing-vct"}""")

            assertFailsWith<IllegalStateException> {
                receiver.receiveCredentialResponse(
                    context = context,
                    state = state,
                    resolvedOffer = resolvedOffer(),
                    credentialResponse =
                        CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawCredential)))),
                )
            }
            assertEquals(0, credentialStore.listMetadata(WALLET_UNIT_ID).getOrThrow().size, "no document should have been stored")
        }

    @Test
    fun receiverOnlyUsesExpectedTypeRefsForSelectedFormat() =
        runTest {
            // Issuer metadata carries vct + doctype + W3C type noise on the SAME configuration;
            // only the vct branch (matching the selected dc+sd-jwt format) may participate in the
            // expected refs, so a payload whose vct matches produces no mismatch diagnostic.
            val noisyOffer =
                ResolvedCredentialOffer(
                    offer =
                        CredentialOffer(
                            credentialIssuer = "https://issuer.example",
                            credentialConfigurationIds = listOf(CREDENTIAL_CONFIGURATION_ID),
                            grants = CredentialOfferGrants(preAuthorizedCode = PreAuthorizedCodeOfferGrant(preAuthorizedCode = "pre-authorized-code")),
                        ),
                    issuerMetadata =
                        CredentialIssuerMetadata(
                            credentialIssuer = "https://issuer.example",
                            credentialEndpoint = "https://issuer.example/credential",
                            credentialConfigurationsSupported =
                                mapOf(
                                    CREDENTIAL_CONFIGURATION_ID to
                                        CredentialConfigurationSupported(
                                            format = CredentialFormat.SD_JWT_DC.value,
                                            vct = EMPLOYEE_VCT,
                                            doctype = MDOC_DOCTYPE,
                                            credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "WrongCredential")),
                                            display = listOf(DisplayProperties(name = "Employee Credential")),
                                        ),
                                ),
                        ),
                )
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-format-scoped-expected-refs-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-noisy"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-noisy","vct":"$EMPLOYEE_VCT"}""")

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = noisyOffer,
                credentialResponse = CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawCredential)))),
            )

            val metadata =
                credentialStore
                    .listMetadata(WALLET_UNIT_ID, CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID))
                    .getOrThrow()
                    .single()
            val stored = credentialStore.getCredential(WALLET_UNIT_ID, metadata.credentialRecordId).getOrThrow()
            assertNotNull(stored)
            assertEquals(
                setOf(EMPLOYEE_VCT),
                stored.issuanceProvenance
                    ?.expectedCredentialTypeRefs
                    ?.map { it.value }
                    ?.toSet(),
            )
            assertEquals(emptyList(), stored.issuanceProvenance?.diagnostics)
        }

    @Test
    fun receiverPopulatesSubjectsFromCredential() =
        runTest {
            val subjectDid = "did:example:holder-subject-42"
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-subjects-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-subject"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val rawCredential = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"$subjectDid","vct":"$EMPLOYEE_VCT"}""")

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse = CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(rawCredential)))),
            )

            val metadata =
                credentialStore
                    .listMetadata(WALLET_UNIT_ID, CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID))
                    .getOrThrow()
                    .single()
            val stored = credentialStore.getCredential(WALLET_UNIT_ID, metadata.credentialRecordId).getOrThrow()
            assertNotNull(stored)
            assertEquals(1, stored.subjectRefs.size, "expected one subject extracted from credential")
            assertEquals(IdentifierType.DID, stored.subjectRefs[0].type)
            assertEquals(subjectDid, stored.subjectRefs[0].value)
            // NoOpWalletIdentityResolver returns the ref unchanged - identityIdentifierId stays null.
            assertEquals(null, stored.subjectRefs[0].identityIdentifierId)
        }

    @Test
    fun receiverAppendsInstancesToExistingRecordAndKeepsActualTypeRefsCanonical() =
        runTest {
            // A second receiveCredentialResponse call for the same issuer+credentialConfigurationId
            // must append an instance to the existing record rather than create a new one, and
            // credentialTypeRefs on append reflects the ACTUAL (payload-derived) set, not the
            // static expected/metadata set.
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, FakeWalletIssuanceSessionStore(), testAcceptance())
            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-append-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-append-1"),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val firstRaw = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-append-1","vct":"$EMPLOYEE_VCT"}""")

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse = CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(firstRaw)))),
            )
            val firstMetadata =
                credentialStore
                    .listMetadata(WALLET_UNIT_ID, CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID))
                    .getOrThrow()
                    .single()
            assertEquals(1, firstMetadata.instanceCount)

            // Second obtain against the SAME session (same credential configuration + issuer):
            // resets holderKeyAliases for the second response item and re-issues a second instance.
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-append-2"),
                            ),
                        ),
                ),
            )
            val secondRaw = buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-append-2","vct":"$EMPLOYEE_VCT"}""")

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse = CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(secondRaw)))),
            )

            val metadata =
                credentialStore
                    .listMetadata(WALLET_UNIT_ID, CredentialMetadataFilter(credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID))
                    .getOrThrow()
                    .single()
            assertEquals(firstMetadata.credentialRecordId, metadata.credentialRecordId, "second obtain must append to the same record")
            assertEquals(2, metadata.instanceCount)
            val stored = credentialStore.getCredential(WALLET_UNIT_ID, metadata.credentialRecordId).getOrThrow()
            assertNotNull(stored)
            assertEquals(2, stored.instances.size)
            assertEquals(setOf(firstRaw, secondRaw), stored.instances.map { it.raw }.toSet())
            assertEquals(setOf(EMPLOYEE_VCT), stored.credentialTypeRefs.map { it.value }.toSet())
            assertEquals(emptyList(), stored.issuanceProvenance?.diagnostics)
        }

    // -----------------------------------------------------------------------------------------
    // Coverage: wallet-initiated refresh supersede semantics.
    // -----------------------------------------------------------------------------------------

    @Test
    fun receiverSupersedesPreviousActiveInstanceOnWalletInitiatedRefresh() =
        runTest {
            // A wallet-initiated refresh targets an EXISTING record by id
            // (sessionState.refreshTargetCredentialRecordId), unlike normal issuance top-up which
            // matches by issuer+credentialConfigurationId. Storage must SUPERSEDE the previously
            // ACTIVE instance (not append a top-up instance), preserve the record's EXISTING refresh
            // policy across a rotated refresh token, and leave issuanceProvenance untouched.
            val credentialStore =
                BlobWalletCredentialStore(
                    blobService = createOid4vciReceiverTestBlobService(),
                    credentialBodyProtector = ReceiverTestCredentialBodyProtector,
                )
            val issuanceSessionStore = FakeWalletIssuanceSessionStore()
            val receiver = WalletStoreOid4vciCredentialResponseReceiver(credentialStore, issuanceSessionStore, testAcceptance())
            val now = Clock.System.now()
            val recordId = "refresh-target-record"
            val originalInstanceId = "refresh-target-instance-original"
            val originalRaw =
                buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-refresh","vct":"$EMPLOYEE_VCT","credential_id":"vc-initial"}""")
            val originalInstance =
                CredentialInstance(
                    id = originalInstanceId,
                    walletUnitId = WALLET_UNIT_ID,
                    credentialRecordId = recordId,
                    format = CredentialFormat.SD_JWT_DC,
                    raw = originalRaw,
                    bodyStorageRef = BodyStorageRef(kind = BodyStorageKind.WALLET_STORE, path = "placeholder"),
                    holderKeyRef = KeyRef(alias = "wallet-holder-key-refresh"),
                    lifecycleState = CredentialLifecycleState.ACTIVE,
                    storedAt = now,
                    updatedAt = now,
                )
            val existingRecord =
                CredentialRecord(
                    id = recordId,
                    walletUnitId = WALLET_UNIT_ID,
                    issuerRef = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example"),
                    format = CredentialFormat.SD_JWT_DC,
                    credentialTypeRefs =
                        setOf(
                            CredentialTypeRef(
                                format = CredentialFormat.SD_JWT_DC,
                                kind = CredentialTypeRefKind.SD_JWT_VCT,
                                value = EMPLOYEE_VCT,
                                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                                primary = true,
                            ),
                        ),
                    instances = listOf(originalInstance),
                    issuanceProvenance = IssuanceProvenance(credentialIssuerUrl = "https://issuer.example", credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID, issuedAt = now),
                    refreshState =
                        RefreshState(
                            refreshMethod = CredentialRefreshMethod.OID4VCI_REISSUANCE,
                            policy = RefreshPolicy(lowWatermark = 2, supersedePreviousActiveInstance = true),
                        ),
                    createdAt = now,
                    updatedAt = now,
                )
            assertTrue(credentialStore.putCredential(WALLET_UNIT_ID, existingRecord).isOk)

            val privateStore = ReceiverTestPrivateSessionStore()
            val sessionId = WalletInteractionSessionId("oid4vci-receiver-refresh-supersede-test")
            privateStore.put(
                sessionId,
                WalletInteractionPrivateSessionData(
                    namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                    values =
                        oid4vciStateValues(
                            Oid4vciPrivateSessionState(
                                credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
                                holderKeyAliases = listOf("wallet-holder-key-refresh"),
                                refreshTargetCredentialRecordId = recordId,
                                tokens =
                                    Oid4vciPrivateSessionState.TokenLeg(
                                        accessToken = "unused-refresh-access-token",
                                        refreshToken = "rotated-refresh-token",
                                    ),
                            ),
                        ),
                ),
            )
            val context =
                WalletInteractionContext(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    executionMode = WalletInteractionExecutionMode.BACKEND,
                    privateSessionStore = privateStore,
                )
            val state =
                WalletInteractionState(
                    sessionId = sessionId,
                    walletUnitId = WALLET_UNIT_ID,
                    status = WalletInteractionStatus.Completed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                )
            val refreshedRaw =
                buildTestSdJwt("""{"iss":"https://issuer.example","sub":"did:example:holder-refresh","vct":"$EMPLOYEE_VCT","credential_id":"vc-refreshed"}""")

            receiver.receiveCredentialResponse(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer(),
                credentialResponse = CredentialResponse(credentials = listOf(CredentialResponseItem(credential = JsonPrimitive(refreshedRaw)))),
            )

            val stored = credentialStore.getCredential(WALLET_UNIT_ID, recordId).getOrThrow()
            assertNotNull(stored)
            assertEquals(2, stored.instances.size, "refresh keeps the superseded instance alongside the new one, it does not replace the list")
            val oldInstance = stored.instances.single { it.id == originalInstanceId }
            val newInstance = stored.instances.single { it.id != originalInstanceId }
            assertEquals(CredentialLifecycleState.SUPERSEDED, oldInstance.lifecycleState, "the previously ACTIVE instance must be superseded")
            assertEquals(CredentialLifecycleState.ACTIVE, newInstance.lifecycleState)
            assertEquals(originalInstanceId, newInstance.replacesInstanceId)
            assertEquals(refreshedRaw, newInstance.raw)
            assertEquals(originalRaw, oldInstance.raw, "the superseded instance body must be preserved, not overwritten")

            val refreshState = assertNotNull(stored.refreshState)
            assertNotNull(refreshState.lastRefreshAt, "withRefreshedInstance must stamp lastRefreshAt")
            assertEquals(emptyList(), refreshState.diagnostics, "same vct on reissue must not produce a refresh diagnostic")
            assertEquals(2, refreshState.policy.lowWatermark, "the existing record's refresh policy must be preserved across a token rotation, not reset to defaults")
            assertEquals(true, refreshState.policy.supersedePreviousActiveInstance)
            assertNotNull(refreshState.refreshTokenRef, "a rotated refresh token must produce an updated refreshTokenRef")
            assertEquals("rotated-refresh-token", issuanceSessionStore.storedRefreshTokens[recordId])

            // issuanceProvenance is untouched by refresh, unlike normal issuance top-up which
            // appends type-ref diagnostics.
            assertEquals(existingRecord.issuanceProvenance?.issuedAt, stored.issuanceProvenance?.issuedAt)
            assertEquals(emptyList(), stored.issuanceProvenance?.diagnostics)
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
                                    display = listOf(DisplayProperties(name = "Offer metadata must not drive the received result")),
                                    credentialMetadata =
                                        CredentialMetadata(
                                            display =
                                                listOf(
                                                    DisplayProperties(
                                                        name = "Employee Credential",
                                                        backgroundColor = "#003399",
                                                        textColor = "#FFFFFF",
                                                    ),
                                                ),
                                        ),
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
        const val WALLET_UNIT_ID = "wallet-oid4vci-receiver"
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
        walletUnitId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        plaintext: ByteArray,
    ): IdkResult<ByteArray, IdkError> = Ok("receiver-test-protected:${plaintext.decodeToString().reversed()}".encodeToByteArray())

    override suspend fun open(
        walletUnitId: String,
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

/**
 * Minimal fake of [WalletIssuanceSessionStore] for receiver tests: records refresh tokens passed to
 * [storeRefreshToken] in [storedRefreshTokens] and returns a stable [SecretRef]; every other method is
 * unsupported/no-op since the receiver under test does not exercise them.
 */
private class FakeWalletIssuanceSessionStore : WalletIssuanceSessionStore {
    val storedRefreshTokens: MutableMap<String, String> = mutableMapOf()

    override suspend fun putSession(
        walletUnitId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> = Ok(session)

    override suspend fun getSession(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> = Ok(null)

    override suspend fun listSessions(
        walletUnitId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> = Ok(emptyList())

    override suspend fun storeDeferredAccessToken(
        walletUnitId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> = Err(IdkError.fromString(code = "FAKE_UNSUPPORTED", message = "Not used by receiver tests"))

    override suspend fun getDeferredAccessToken(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> = Ok(null)

    override suspend fun storeRefreshToken(
        walletUnitId: String,
        credentialRecordId: String,
        refreshToken: String,
    ): IdkResult<SecretRef, IdkError> {
        storedRefreshTokens[credentialRecordId] = refreshToken
        return Ok(SecretRef(id = "secret:test"))
    }

    override suspend fun getRefreshToken(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<String?, IdkError> = Ok(null)

    override suspend fun deleteSession(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> = Ok(true)
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
