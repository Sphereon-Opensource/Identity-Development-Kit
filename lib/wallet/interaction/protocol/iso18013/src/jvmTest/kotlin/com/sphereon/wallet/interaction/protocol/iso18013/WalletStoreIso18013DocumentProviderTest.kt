/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.TDate
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.DeviceKeyInfo
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.data.mso.MsoVersion
import com.sphereon.mdoc.data.mso.ValidityInfo
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WalletStoreIso18013DocumentProviderTest {
    @Test
    fun providerLoadsRequestedMdocDocumentFromWalletStore() =
        runTest {
            val codec = IssuerSignedCborCodecImpl()
            val store =
                RecordingWalletCredentialStore(
                    credentialRecord(
                        id = "mdl-record",
                        docType = MDL_DOCTYPE,
                        raw = buildTestMdocCredential(MDL_DOCTYPE, codec),
                        holderKeyAlias = "holder-mdl-key",
                    ),
                    credentialRecord(
                        id = "pid-record",
                        docType = PID_DOCTYPE,
                        raw = buildTestMdocCredential(PID_DOCTYPE, codec),
                        holderKeyAlias = "holder-pid-key",
                    ),
                )
            val provider =
                WalletStoreIso18013DocumentProvider(
                    credentialStore = store,
                    walletInstanceId = WALLET_INSTANCE_ID,
                    issuerSignedCborCodec = codec,
                )

            val documents = provider.getDocuments(Iso18013DocumentSelectorData(setOf(MDL_DOCTYPE)))

            assertEquals(1, documents.size)
            val document = documents.single()
            assertEquals(DocType(MDL_DOCTYPE), document.document.docType)
            val storedMso =
                MobileSecurityObjectCborCodecImpl()
                    .decode(
                        document.document.issuerSigned.issuerAuth.payload!!
                            .value
                    ).getOrThrow()
                    .value
            assertEquals(DocType(MDL_DOCTYPE), storedMso.docType)
            assertEquals("holder-mdl-key", document.keyAlias)
            assertEquals(
                listOf(
                    "find:$WALLET_INSTANCE_ID:$MDL_DOCTYPE",
                    "get:$WALLET_INSTANCE_ID:mdl-record",
                ),
                store.calls,
            )
        }

    @Test
    fun providerFallsBackToActiveMdocMetadataWhenNoDoctypeWasRequested() =
        runTest {
            val codec = IssuerSignedCborCodecImpl()
            val store =
                RecordingWalletCredentialStore(
                    credentialRecord(
                        id = "mdl-record",
                        docType = MDL_DOCTYPE,
                        raw = buildTestMdocCredential(MDL_DOCTYPE, codec),
                        holderKeyAlias = "holder-mdl-key",
                    ),
                )
            val provider =
                WalletStoreIso18013DocumentProvider(
                    credentialStore = store,
                    walletInstanceId = WALLET_INSTANCE_ID,
                    issuerSignedCborCodec = codec,
                )

            val documents = provider.getDocuments(selectorData = null)

            assertEquals(1, documents.size)
            assertEquals(DocType(MDL_DOCTYPE), documents.single().document.docType)
            assertEquals(listOf("list:$WALLET_INSTANCE_ID", "get:$WALLET_INSTANCE_ID:mdl-record"), store.calls)
        }

    private class RecordingWalletCredentialStore(
        initialRecords: List<CredentialRecord>,
    ) : WalletCredentialStore {
        constructor(vararg initialRecords: CredentialRecord) : this(initialRecords.toList())

        val calls: MutableList<String> = mutableListOf()
        private val records = initialRecords.associateBy { it.id }.toMutableMap()

        override suspend fun putCredential(
            walletInstanceId: String,
            record: CredentialRecord,
        ): IdkResult<CredentialRecord, IdkError> {
            calls += "put:$walletInstanceId:${record.id}"
            records[record.id] = record
            return Ok(record)
        }

        override suspend fun getCredential(
            walletInstanceId: String,
            credentialRecordId: String,
        ): IdkResult<CredentialRecord?, IdkError> {
            calls += "get:$walletInstanceId:$credentialRecordId"
            return Ok(records[credentialRecordId]?.takeIf { it.walletInstanceId == walletInstanceId })
        }

        override suspend fun getMetadata(
            walletInstanceId: String,
            credentialRecordId: String,
        ): IdkResult<CredentialMetadata?, IdkError> {
            calls += "metadata:$walletInstanceId:$credentialRecordId"
            return Ok(records[credentialRecordId]?.takeIf { it.walletInstanceId == walletInstanceId }?.metadata(NOW))
        }

        override suspend fun listMetadata(
            walletInstanceId: String,
            filter: CredentialMetadataFilter,
        ): IdkResult<List<CredentialMetadata>, IdkError> {
            calls += "list:$walletInstanceId"
            return Ok(metadata(walletInstanceId).filter { it.matches(filter) })
        }

        override suspend fun findByCredentialTypeRef(
            walletInstanceId: String,
            ref: CredentialTypeRef,
        ): IdkResult<List<CredentialMetadata>, IdkError> {
            calls += "find:$walletInstanceId:${ref.value}"
            return Ok(metadata(walletInstanceId).filter { it.hasTypeRef(ref) })
        }

        override suspend fun deleteCredential(
            walletInstanceId: String,
            credentialRecordId: String,
        ): IdkResult<Boolean, IdkError> {
            calls += "delete:$walletInstanceId:$credentialRecordId"
            return Ok(records.remove(credentialRecordId) != null)
        }

        private fun metadata(walletInstanceId: String): List<CredentialMetadata> =
            records
                .values
                .filter { it.walletInstanceId == walletInstanceId }
                .map { it.metadata(NOW) }
    }

    private companion object {
        private const val WALLET_INSTANCE_ID = "wallet-iso18013-provider"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        private val NOW = Instant.fromEpochSeconds(1_800_000_000)

        private fun credentialRecord(
            id: String,
            docType: String,
            raw: String,
            holderKeyAlias: String,
        ): CredentialRecord {
            val ref =
                CredentialTypeRef(
                    format = CredentialFormat.MSO_MDOC,
                    kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                    value = docType,
                    source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                    primary = true,
                )
            return CredentialRecord(
                id = id,
                walletInstanceId = WALLET_INSTANCE_ID,
                issuerRef = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example"),
                format = CredentialFormat.MSO_MDOC,
                credentialTypeRefs = setOf(ref),
                instances =
                    listOf(
                        CredentialInstance(
                            id = "$id-instance",
                            walletInstanceId = WALLET_INSTANCE_ID,
                            credentialRecordId = id,
                            format = CredentialFormat.MSO_MDOC,
                            raw = raw,
                            bodyStorageRef = BodyStorageRef(kind = BodyStorageKind.WALLET_STORE, path = "wallet/$id/body"),
                            holderKeyRef = KeyRef(alias = holderKeyAlias),
                            lifecycleState = CredentialLifecycleState.ACTIVE,
                            validity = CredentialValidityWindow(),
                            issuedAt = NOW,
                            storedAt = NOW,
                            updatedAt = NOW,
                        ),
                    ),
                createdAt = NOW,
                updatedAt = NOW,
            )
        }

        private fun buildTestMdocCredential(
            docType: String,
            issuerSignedCodec: IssuerSignedCborCodecImpl,
        ): String {
            val mobileSecurityObjectCodec = MobileSecurityObjectCborCodecImpl()
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
                    docType = DocType(docType),
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
    }
}
