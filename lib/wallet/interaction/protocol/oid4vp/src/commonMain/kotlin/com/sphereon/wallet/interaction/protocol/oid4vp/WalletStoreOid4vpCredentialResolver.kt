/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.wallet.credential.CredentialBindingRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityState
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock

class WalletStoreOid4vpCredentialResolver(
    private val credentialStore: WalletCredentialStore,
) : Oid4vpCredentialCandidateResolver,
    Oid4vpSelectedCredentialResolver,
    Oid4vpPresentationSecurityContextResolver {
    override suspend fun candidateCredentialIds(
        context: WalletInteractionContext,
        resolvedRequest: ResolvedOid4vpRequest,
    ): Map<String, List<String>> =
        resolvedRequest.dcqlQuery
            ?.credentials
            .orEmpty()
            .associate { query ->
                query.id to candidateMetadata(context.walletInstanceId, query).map { it.credentialRecordId }
            }

    override suspend fun resolveSelectedCredentials(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedRequest: ResolvedOid4vpRequest,
        selectedCredentialIdsByRequirement: Map<String, List<String>>,
    ): List<SelectedCredential> {
        val queries = resolvedRequest.dcqlQuery?.credentials.orEmpty()
        val selectedCredentials = mutableListOf<SelectedCredential>()
        val now = Clock.System.now()

        for (query in queries) {
            for (credentialRecordId in selectedCredentialIdsByRequirement[query.id].orEmpty().distinct()) {
                val record = credentialStore.requireCredential(context.walletInstanceId, credentialRecordId)
                val instance =
                    record.presentableInstance(now)
                        ?: error("Credential record '$credentialRecordId' has no presentable instance")
                selectedCredentials +=
                    SelectedCredential(
                        credentialQueryId = query.id,
                        credentialId = instance.id,
                        presentation = instance.requireRaw(),
                        format = instance.format.value,
                        holderKeyAlias = instance.holderKeyRef?.alias,
                    )
            }
        }

        return selectedCredentials
    }

    override suspend fun resolve(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vpPresentationSecurityContext =
        Oid4vpPresentationSecurityContext(
            keyRef = selectedHolderKeyAlias(context, state),
            walletUnitId = context.securityAttribute(Oid4vpPresentationSecurityAttributes.WALLET_UNIT_ID),
            walletAccountId = context.securityAttribute(Oid4vpPresentationSecurityAttributes.WALLET_ACCOUNT_ID),
            activationDecisionId = context.securityAttribute(Oid4vpPresentationSecurityAttributes.ACTIVATION_DECISION_ID),
            operationType = context.securityAttribute(Oid4vpPresentationSecurityAttributes.OPERATION_TYPE),
            operationHash = context.securityAttribute(Oid4vpPresentationSecurityAttributes.OPERATION_HASH),
            nonce = context.securityAttribute(Oid4vpPresentationSecurityAttributes.NONCE),
        )

    override suspend fun recordPresentationSubmitted(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedRequest: ResolvedOid4vpRequest,
        selectedCredentialIdsByRequirement: Map<String, List<String>>,
        selectedCredentials: List<SelectedCredential>,
    ) {
        if (selectedCredentials.isEmpty() || selectedCredentialIdsByRequirement.isEmpty()) return

        val selectedInstanceIdsByRequirement =
            selectedCredentials
                .groupBy { it.credentialQueryId }
                .mapValues { (_, credentials) -> credentials.map { it.credentialId }.toSet() }
        val verifierRef = resolvedRequest.verifierInfo.clientId.toVerifierRef()
        val boundAt = Clock.System.now()
        val updates = linkedMapOf<String, CredentialRecord>()

        for ((requirementId, credentialRecordIds) in selectedCredentialIdsByRequirement) {
            val selectedInstanceIds = selectedInstanceIdsByRequirement[requirementId].orEmpty()
            if (selectedInstanceIds.isEmpty()) continue

            for (credentialRecordId in credentialRecordIds.distinct()) {
                val record = credentialStore.requireCredential(context.walletInstanceId, credentialRecordId)
                val updated =
                    record.copy(
                        instances =
                            record.instances.map { instance ->
                                if (instance.id in selectedInstanceIds) {
                                    instance.copy(
                                        bindingRefs =
                                            instance.bindingRefs +
                                                CredentialBindingRef(
                                                    verifierRef = verifierRef,
                                                    boundAt = boundAt,
                                                    presentationId = requirementId,
                                                ),
                                        updatedAt = boundAt,
                                    )
                                } else {
                                    instance
                                }
                            },
                        updatedAt = boundAt,
                        syncState = record.syncState.copy(localRevision = record.syncState.localRevision + 1),
                    )
                if (updated != record) updates[updated.id] = updated
            }
        }

        for (record in updates.values) {
            val putResult = credentialStore.putCredential(context.walletInstanceId, record)
            if (putResult.isErr) error("Failed to update wallet presentation history: ${putResult.error.code}")
        }
    }

    private suspend fun candidateMetadata(
        walletInstanceId: String,
        query: DcqlCredentialQuery,
    ): List<CredentialMetadata> {
        val requestedTypeRefs = query.credentialTypeRefs()
        val metadata =
            if (requestedTypeRefs.isNotEmpty()) {
                val collected = linkedMapOf<String, CredentialMetadata>()
                for (ref in requestedTypeRefs) {
                    val result = credentialStore.findByCredentialTypeRef(walletInstanceId, ref)
                    if (result.isErr) error("Failed to query wallet credential metadata: ${result.error.code}")
                    for (candidate in result.value) {
                        collected[candidate.credentialRecordId] = candidate
                    }
                }
                collected.values.toList()
            } else {
                val format = query.format?.let { CredentialFormat.fromValueLenient(it) }
                val result =
                    credentialStore.listMetadata(
                        walletInstanceId = walletInstanceId,
                        filter =
                            CredentialMetadataFilter(
                                formats = format?.let { setOf(it) }.orEmpty(),
                                lifecycleStates = setOf(CredentialLifecycleState.ACTIVE),
                            ),
                    )
                if (result.isErr) error("Failed to query wallet credential metadata: ${result.error.code}")
                result.value
            }

        return metadata.filter { it.isPresentationCandidate() }
    }

    private suspend fun WalletCredentialStore.requireCredential(
        walletInstanceId: String,
        credentialRecordId: String,
    ): CredentialRecord {
        val result = getCredential(walletInstanceId, credentialRecordId)
        if (result.isErr) error("Failed to open wallet credential '$credentialRecordId': ${result.error.code}")
        return result.value ?: error("Wallet credential '$credentialRecordId' was not found")
    }

    private suspend fun selectedHolderKeyAlias(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): String? {
        val now = Clock.System.now()
        val selectedCredentialIds =
            state.disclosure
                ?.selectedCredentialIds
                .orEmpty()
                .distinct()
        for (credentialRecordId in selectedCredentialIds) {
            val record = credentialStore.requireCredential(context.walletInstanceId, credentialRecordId)
            val alias =
                record
                    .presentableInstance(now)
                    ?.holderKeyRef
                    ?.alias
            if (!alias.isNullOrBlank()) return alias
        }
        return null
    }
}

private fun WalletInteractionContext.securityAttribute(key: String): String? = attributes[key]?.takeIf { it.isNotBlank() }

internal fun DcqlCredentialQuery.credentialTypeRefs(): Set<CredentialTypeRef> {
    val meta = meta ?: return emptySet()
    val format = format?.let { CredentialFormat.fromValueLenient(it) }

    return buildSet {
        val sdJwtFormat = format?.takeIf { it.isSdJwt } ?: CredentialFormat.SD_JWT_DC
        val vctValues = meta["vct_values"]
        if (vctValues is JsonArray) {
            for (entry in vctValues) {
                if (entry is JsonPrimitive && entry.isString) {
                    add(
                        CredentialTypeRef(
                            format = sdJwtFormat,
                            kind = CredentialTypeRefKind.SD_JWT_VCT,
                            value = entry.content,
                            source = CredentialTypeRefSource.IMPORT_METADATA,
                            primary = true,
                        ),
                    )
                }
            }
        }

        val doctypeValue = meta["doctype_value"]
        if (doctypeValue is JsonPrimitive && doctypeValue.isString) {
            add(
                CredentialTypeRef(
                    format = CredentialFormat.MSO_MDOC,
                    kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                    value = doctypeValue.content,
                    source = CredentialTypeRefSource.IMPORT_METADATA,
                    primary = true,
                ),
            )
        }

        val w3cFormat = format?.takeUnless { it.isSdJwt || it.isMdoc } ?: CredentialFormat.JWT_VC_JSON
        addAll(
            credentialW3cTypeRefs(
                format = w3cFormat,
                types = jsonStringList(meta["type_values"]) + jsonStringList(meta["types"]),
            ),
        )
    }
}

private fun CredentialMetadata.isPresentationCandidate(): Boolean =
    activeInstanceCount > 0 &&
        lifecycleSummary.lifecycleState == CredentialLifecycleState.ACTIVE &&
        lifecycleSummary.validityState != CredentialValidityState.NOT_YET_VALID &&
        lifecycleSummary.validityState != CredentialValidityState.EXPIRED

private fun credentialW3cTypeRefs(
    format: CredentialFormat,
    types: List<String>,
): Set<CredentialTypeRef> {
    val cleanTypes = types.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val primaryType = cleanTypes.firstOrNull { it != "VerifiableCredential" } ?: cleanTypes.firstOrNull()
    return cleanTypes
        .map {
            CredentialTypeRef(
                format = format,
                kind = CredentialTypeRefKind.W3C_VC_TYPE,
                value = it,
                source = CredentialTypeRefSource.IMPORT_METADATA,
                primary = it == primaryType,
            )
        }.toSet()
}

private fun jsonStringList(element: JsonElement?): List<String> =
    when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> element.contentOrNull?.let(::listOf).orEmpty()
        else -> emptyList()
    }

private fun String.toVerifierRef(): IdentifierRef {
    val verifierType = if (startsWith("did:")) IdentifierType.DID else IdentifierType("https")
    return IdentifierRef(type = verifierType, value = this)
}
