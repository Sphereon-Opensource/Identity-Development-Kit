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
import com.sphereon.statuslist.CredentialStatusInput
import com.sphereon.statuslist.CredentialStatusReference
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialStatusSnapshot
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityWindow
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.WalletCredentialStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Focused provider plumbing tests. [buildTestMdocCredential] assembles a synthetic COSE signature,
 * so these cases prove typed MSO extraction and fail-closed policy dispatch, not independent
 * issuer-signature authentication. The product E2E suite supplies the stronger real KMS signing
 * boundary.
 */
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
                    walletUnitId = WALLET_UNIT_ID,
                    issuerSignedCborCodec = codec,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
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
                    "find:$WALLET_UNIT_ID:$MDL_DOCTYPE",
                    "get:$WALLET_UNIT_ID:mdl-record",
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
                    walletUnitId = WALLET_UNIT_ID,
                    issuerSignedCborCodec = codec,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                )

            val documents = provider.getDocuments(selectorData = null)

            assertEquals(1, documents.size)
            assertEquals(DocType(MDL_DOCTYPE), documents.single().document.docType)
            assertEquals(listOf("list:$WALLET_UNIT_ID", "get:$WALLET_UNIT_ID:mdl-record"), store.calls)
        }

    @Test
    fun providerDoesNotPresentAnMdocWithAKnownNonValidStatus() =
        runTest {
            val codec = IssuerSignedCborCodecImpl()
            val store =
                RecordingWalletCredentialStore(
                    credentialRecord(
                        id = "revoked-record",
                        docType = MDL_DOCTYPE,
                        raw = buildTestMdocCredential(MDL_DOCTYPE, codec),
                        holderKeyAlias = "holder-mdl-key",
                        status = CredentialStatusSnapshot(status = "revoked", checkedAt = NOW, source = "https://status.example"),
                    ),
                )
            val provider =
                WalletStoreIso18013DocumentProvider(
                    credentialStore = store,
                    walletUnitId = WALLET_UNIT_ID,
                    issuerSignedCborCodec = codec,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                )

            assertEquals(emptySet(), provider.getDocuments(Iso18013DocumentSelectorData(setOf(MDL_DOCTYPE))))
        }

    @Test
    fun providerDoesNotPresentAStatusBearingMdocWithoutACompatibleVerifier() =
        runTest {
            val codec = IssuerSignedCborCodecImpl()
            val status = com.sphereon.mdoc.data.mso.Status(
                statusList = com.sphereon.mdoc.data.mso.StatusListInfo(
                    idx = 7u,
                    uri = "https://status.example/mdoc.cwt",
                ),
            )
            val store =
                RecordingWalletCredentialStore(
                    credentialRecord(
                        id = "status-bearing-record",
                        docType = MDL_DOCTYPE,
                        raw = buildTestMdocCredential(MDL_DOCTYPE, codec, status),
                        holderKeyAlias = "holder-mdl-key",
                    ),
                )
            val provider =
                WalletStoreIso18013DocumentProvider(
                    credentialStore = store,
                    walletUnitId = WALLET_UNIT_ID,
                    issuerSignedCborCodec = codec,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                )

            assertEquals(emptySet(), provider.getDocuments(Iso18013DocumentSelectorData(setOf(MDL_DOCTYPE))))
        }

    @Test
    fun providerResolvesStatusBeforePresentingAStatusBearingMdoc() =
        runTest {
            val codec = IssuerSignedCborCodecImpl()
            val status = com.sphereon.mdoc.data.mso.Status(
                statusList = com.sphereon.mdoc.data.mso.StatusListInfo(
                    idx = 7u,
                    uri = "https://status.example/mdoc.cwt",
                ),
            )
            val store =
                RecordingWalletCredentialStore(
                    credentialRecord(
                        id = "status-bearing-record",
                        docType = MDL_DOCTYPE,
                        raw = buildTestMdocCredential(MDL_DOCTYPE, codec, status),
                        holderKeyAlias = "holder-mdl-key",
                    ),
                )
            val verifier = FixedMdocStatusVerifier(StatusValues.VALID)
            val provider =
                WalletStoreIso18013DocumentProvider(
                    credentialStore = store,
                    walletUnitId = WALLET_UNIT_ID,
                    issuerSignedCborCodec = codec,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                    credentialStatusVerifiers = setOf(verifier),
                )

            assertEquals(1, provider.getDocuments(Iso18013DocumentSelectorData(setOf(MDL_DOCTYPE))).size)
            assertEquals(1, verifier.typedReferenceCalls, "authenticated MSO metadata must be inspected exactly once")
            assertEquals(0, verifier.claimReferenceCalls, "MSO status must not be flattened into namespace claims")
            assertEquals(1, verifier.resolveCalls, "the authenticated MSO status reference must be resolved exactly once")
            assertEquals(7, verifier.lastResolvedReference?.index)
            assertEquals("https://status.example/mdoc.cwt", verifier.lastResolvedReference?.uri)
        }

    @Test
    fun providerDoesNotPresentAStatusBearingMdocWhenResolvedStatusIsInvalid() =
        runTest {
            val codec = IssuerSignedCborCodecImpl()
            val status = com.sphereon.mdoc.data.mso.Status(
                statusList = com.sphereon.mdoc.data.mso.StatusListInfo(
                    idx = 7u,
                    uri = "https://status.example/mdoc.cwt",
                ),
            )
            val store =
                RecordingWalletCredentialStore(
                    credentialRecord(
                        id = "status-bearing-record",
                        docType = MDL_DOCTYPE,
                        raw = buildTestMdocCredential(MDL_DOCTYPE, codec, status),
                        holderKeyAlias = "holder-mdl-key",
                    ),
                )
            val verifier = FixedMdocStatusVerifier(StatusValues.INVALID)
            val provider =
                WalletStoreIso18013DocumentProvider(
                    credentialStore = store,
                    walletUnitId = WALLET_UNIT_ID,
                    issuerSignedCborCodec = codec,
                    mobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl(),
                    credentialStatusVerifiers = setOf(verifier),
                )

            assertEquals(emptySet(), provider.getDocuments(Iso18013DocumentSelectorData(setOf(MDL_DOCTYPE))))
            assertEquals(1, verifier.typedReferenceCalls, "invalid authenticated status must still be inspected exactly once")
            assertEquals(0, verifier.claimReferenceCalls, "invalid MSO status must not be projected into claims")
            assertEquals(1, verifier.resolveCalls, "invalid authenticated status must be resolved exactly once before rejection")
        }

    private class FixedMdocStatusVerifier(private val value: Int) : CredentialStatusVerifier {
        override val mechanism: String = "mdoc_status"
        var typedReferenceCalls: Int = 0
            private set
        var claimReferenceCalls: Int = 0
            private set
        var resolveCalls: Int = 0
            private set
        var lastResolvedReference: CredentialStatusReference? = null
            private set

        override fun references(input: CredentialStatusInput): List<CredentialStatusReference> {
            typedReferenceCalls += 1
            return input.metadata?.mdoc?.references?.filter { it.mechanism == mechanism }
                ?: references(input.claims)
        }

        override fun references(credentialClaims: JsonObject): List<CredentialStatusReference> {
            claimReferenceCalls += 1
            return if (credentialClaims["status"] != null) {
                listOf(CredentialStatusReference(mechanism = mechanism, uri = "https://status.example/mdoc.cwt", index = 7))
            } else {
                emptyList()
            }
        }

        override suspend fun resolve(reference: CredentialStatusReference): com.sphereon.core.api.IdkResult<ResolvedStatus, com.sphereon.core.api.error.IdkError> {
            resolveCalls += 1
            lastResolvedReference = reference
            return com.sphereon.core.api.Ok(
                ResolvedStatus(
                    value = value,
                    valid = value == StatusValues.VALID,
                    statusListUri = reference.uri,
                ),
            )
        }
    }

    private class RecordingWalletCredentialStore(
        initialRecords: List<CredentialRecord>,
    ) : WalletCredentialStore {
        constructor(vararg initialRecords: CredentialRecord) : this(initialRecords.toList())

        val calls: MutableList<String> = mutableListOf()
        private val records = initialRecords.associateBy { it.id }.toMutableMap()

        override suspend fun putCredential(
            walletUnitId: String,
            record: CredentialRecord,
        ): IdkResult<CredentialRecord, IdkError> {
            calls += "put:$walletUnitId:${record.id}"
            records[record.id] = record
            return Ok(record)
        }

        override suspend fun getCredential(
            walletUnitId: String,
            credentialRecordId: String,
        ): IdkResult<CredentialRecord?, IdkError> {
            calls += "get:$walletUnitId:$credentialRecordId"
            return Ok(records[credentialRecordId]?.takeIf { it.walletUnitId == walletUnitId })
        }

        override suspend fun getMetadata(
            walletUnitId: String,
            credentialRecordId: String,
        ): IdkResult<CredentialMetadata?, IdkError> {
            calls += "metadata:$walletUnitId:$credentialRecordId"
            return Ok(records[credentialRecordId]?.takeIf { it.walletUnitId == walletUnitId }?.metadata(NOW))
        }

        override suspend fun listMetadata(
            walletUnitId: String,
            filter: CredentialMetadataFilter,
        ): IdkResult<List<CredentialMetadata>, IdkError> {
            calls += "list:$walletUnitId"
            return Ok(metadata(walletUnitId).filter { it.matches(filter) })
        }

        override suspend fun findByCredentialTypeRef(
            walletUnitId: String,
            ref: CredentialTypeRef,
        ): IdkResult<List<CredentialMetadata>, IdkError> {
            calls += "find:$walletUnitId:${ref.value}"
            return Ok(metadata(walletUnitId).filter { it.hasTypeRef(ref) })
        }

        override suspend fun deleteCredential(
            walletUnitId: String,
            credentialRecordId: String,
        ): IdkResult<Boolean, IdkError> {
            calls += "delete:$walletUnitId:$credentialRecordId"
            return Ok(records.remove(credentialRecordId) != null)
        }

        private fun metadata(walletUnitId: String): List<CredentialMetadata> =
            records
                .values
                .filter { it.walletUnitId == walletUnitId }
                .map { it.metadata(NOW) }
    }

    private companion object {
        private const val WALLET_UNIT_ID = "wallet-iso18013-provider"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        private val NOW = Instant.fromEpochSeconds(1_800_000_000)

        private fun credentialRecord(
            id: String,
            docType: String,
            raw: String,
            holderKeyAlias: String,
            status: CredentialStatusSnapshot? = null,
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
                walletUnitId = WALLET_UNIT_ID,
                issuerRef = IdentifierRef(type = IdentifierType("https"), value = "https://issuer.example"),
                format = CredentialFormat.MSO_MDOC,
                credentialTypeRefs = setOf(ref),
                instances =
                    listOf(
                        CredentialInstance(
                            id = "$id-instance",
                            walletUnitId = WALLET_UNIT_ID,
                            credentialRecordId = id,
                            format = CredentialFormat.MSO_MDOC,
                            raw = raw,
                            bodyStorageRef = BodyStorageRef(kind = BodyStorageKind.WALLET_STORE, path = "wallet/$id/body"),
                            holderKeyRef = KeyRef(alias = holderKeyAlias),
                            lifecycleState = CredentialLifecycleState.ACTIVE,
                            validity = CredentialValidityWindow(),
                            status = status,
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
            status: com.sphereon.mdoc.data.mso.Status? = null,
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
                    status = status,
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
