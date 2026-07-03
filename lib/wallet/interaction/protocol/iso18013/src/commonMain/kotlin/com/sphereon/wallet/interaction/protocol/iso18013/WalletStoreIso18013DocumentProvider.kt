/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.toException
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.data.device.IssuerSignedCborCodec
import com.sphereon.mdoc.transfer.DocumentProvider
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityState
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlin.time.Clock
import kotlin.time.Instant

class WalletStoreIso18013DocumentProviderResolver(
    private val credentialStore: WalletCredentialStore,
    private val issuerSignedCborCodec: IssuerSignedCborCodec,
) : Iso18013DocumentProviderResolver {
    override suspend fun resolve(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): DocumentProvider =
        WalletStoreIso18013DocumentProvider(
            credentialStore = credentialStore,
            walletInstanceId = context.walletInstanceId,
            issuerSignedCborCodec = issuerSignedCborCodec,
        )
}

class WalletStoreIso18013DocumentProvider(
    private val credentialStore: WalletCredentialStore,
    private val walletInstanceId: String,
    private val issuerSignedCborCodec: IssuerSignedCborCodec,
) : DocumentProvider {
    override suspend fun getDocuments(selectorData: Any?): Set<DocumentWithKeyAlias> {
        val requestedDocTypes = (selectorData as? Iso18013DocumentSelectorData)?.requestedDocTypes.orEmpty()
        val metadata = credentialMetadata(requestedDocTypes)
        val now = Clock.System.now()

        return metadata
            .filter { it.isPresentationCandidate() }
            .mapNotNull { candidate -> candidate.toWalletDocument(now) }
            .toSet()
    }

    private suspend fun credentialMetadata(requestedDocTypes: Set<String>): List<CredentialMetadata> {
        if (requestedDocTypes.isEmpty()) {
            val result =
                credentialStore.listMetadata(
                    walletInstanceId = walletInstanceId,
                    filter =
                        com.sphereon.wallet.credential.CredentialMetadataFilter(
                            formats = setOf(CredentialFormat.MSO_MDOC),
                            lifecycleStates = setOf(CredentialLifecycleState.ACTIVE),
                        ),
                )
            if (result.isErr) error("Failed to list mdoc wallet credentials: ${result.error.code}")
            return result.value
        }

        val collected = linkedMapOf<String, CredentialMetadata>()
        for (docType in requestedDocTypes) {
            val result =
                credentialStore.findByCredentialTypeRef(
                    walletInstanceId = walletInstanceId,
                    ref =
                        CredentialTypeRef(
                            format = CredentialFormat.MSO_MDOC,
                            kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                            value = docType,
                            source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                            primary = true,
                        ),
                )
            if (result.isErr) error("Failed to query mdoc wallet credentials for '$docType': ${result.error.code}")
            for (candidate in result.value) {
                collected[candidate.credentialRecordId] = candidate
            }
        }
        return collected.values.toList()
    }

    private suspend fun CredentialMetadata.toWalletDocument(now: Instant): DocumentWithKeyAlias? {
        val recordResult = credentialStore.getCredential(walletInstanceId, credentialRecordId)
        if (recordResult.isErr) error("Failed to open mdoc wallet credential '$credentialRecordId': ${recordResult.error.code}")
        val record = recordResult.value ?: return null
        val instance =
            record.instances
                .firstOrNull { it.isPresentableMdoc(now) }
                ?: return null
        val docType =
            (record.credentialTypeRefs + credentialTypeRefs)
                .firstOrNull { it.format.isMdoc && it.kind == CredentialTypeRefKind.MDOC_DOCTYPE }
                ?.value
                ?: return null
        val issuerSignedBytes = instance.requireRaw().decodeFromBase64Url()
        val issuerSigned =
            issuerSignedCborCodec
                .decode(issuerSignedBytes)
                .getOrElse { throw it.toException() }
                .value

        return WalletStoreIso18013Document(
            providerId = "",
            keyAlias = instance.holderKeyRef?.alias.orEmpty(),
            document =
                Document(
                    docType = DocType(docType),
                    issuerSigned = issuerSigned,
                    deviceSigned = null,
                    original = null,
                ),
        )
    }
}

private fun CredentialMetadata.isPresentationCandidate(): Boolean =
    activeInstanceCount > 0 &&
        lifecycleSummary.lifecycleState == CredentialLifecycleState.ACTIVE &&
        lifecycleSummary.validityState != CredentialValidityState.NOT_YET_VALID &&
        lifecycleSummary.validityState != CredentialValidityState.EXPIRED

private fun CredentialInstance.isPresentableMdoc(now: Instant): Boolean =
    format.isMdoc &&
        lifecycleState == CredentialLifecycleState.ACTIVE &&
        validity.stateAt(now) != CredentialValidityState.NOT_YET_VALID &&
        validity.stateAt(now) != CredentialValidityState.EXPIRED

private data class WalletStoreIso18013Document(
    override val providerId: String,
    override val keyAlias: String,
    override val document: Document,
) : DocumentWithKeyAlias
