/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialLifecycleSummary
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialValidityState
import com.sphereon.wallet.credential.LocalWalletCredentialStore
import com.sphereon.wallet.credential.StoreRef
import com.sphereon.wallet.credential.walletCredentialInstanceBodyPath
import com.sphereon.wallet.credential.walletCredentialMetadataPath
import com.sphereon.wallet.credential.walletCredentialMetadataPrefix
import com.sphereon.wallet.credential.walletCredentialRecordEnvelopePath
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val walletJson = Json { ignoreUnknownKeys = true }

private const val CONTENT_TYPE = "application/vnd.sphereon.wallet.credential+json"

private const val META_WALLET_INSTANCE_ID = "walletInstanceId"
private const val META_CREDENTIAL_RECORD_ID = "credentialRecordId"
private const val META_ISSUER_ID = "issuerId"
private const val META_FORMAT = "format"
private const val META_LIFECYCLE = "lifecycle"
private const val META_VALIDITY = "validity"
private const val META_CREDENTIAL_CONFIGURATION_ID = "credentialConfigurationId"
private const val META_TYPE_REF_PREFIX = "credentialTypeRef."

/**
 * Blob-backed [LocalWalletCredentialStore].
 *
 * Credential instance bodies and metadata sidecars are both persisted through [BlobService].
 * Metadata APIs read only sidecars under
 * `wallet-instances/{walletInstanceId}/credentials/{credentialRecordId}/metadata`; credential
 * instance body paths are opened only by [getCredential].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<LocalWalletCredentialStore>())
class BlobWalletCredentialStore(
    private val blobService: BlobService,
    private val credentialBodyProtector: WalletCredentialBodyProtector,
) : LocalWalletCredentialStore {
    override suspend fun putCredential(
        walletInstanceId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError> {
        if (record.walletInstanceId != walletInstanceId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "record.walletInstanceId must match walletInstanceId"))
        }

        val normalizedRecord = record.withBlobBodyRefs()
        for (instance in normalizedRecord.instances) {
            val raw = instance.raw
            if (raw != null) {
                val protectedBody =
                    credentialBodyProtector
                        .protect(walletInstanceId, normalizedRecord.id, instance.id, raw.encodeToByteArray())
                        .getOrElse { return Err(it) }
                val bodyResult =
                    blobService.storeBlob(
                        target = instanceBodyBlobInfo(walletInstanceId, normalizedRecord.id, instance.id),
                        data = protectedBody,
                    )
                if (bodyResult.isErr) return Err(bodyResult.error)
            } else {
                val existingBody = blobService.getBlob(instanceBodyBlobInfo(walletInstanceId, normalizedRecord.id, instance.id))
                if (existingBody.isErr) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Credential instance '${instance.id}' has no raw body and no existing body blob",
                        ),
                    )
                }
            }
        }

        val envelope = normalizedRecord.withoutRawBodies()
        val metadata = normalizedRecord.metadata(Clock.System.now())
        val envelopeResult =
            blobService.storeBlob(
                target = recordEnvelopeBlobInfo(walletInstanceId, normalizedRecord.id),
                data = walletJson.encodeToString(envelope).encodeToByteArray(),
            )
        if (envelopeResult.isErr) return Err(envelopeResult.error)

        val sidecarResult =
            blobService.storeBlob(
                target = metadataBlobInfo(walletInstanceId, normalizedRecord.id, metadata),
                data = walletJson.encodeToString(metadata).encodeToByteArray(),
            )
        return if (sidecarResult.isOk) Ok(normalizedRecord) else Err(sidecarResult.error)
    }

    override suspend fun getCredential(
        walletInstanceId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError> {
        val metadataResult = getMetadata(walletInstanceId, credentialRecordId)
        if (metadataResult.isErr) return Err(metadataResult.error)
        if (metadataResult.value?.lifecycleSummary?.tombstone == true) return Ok(null)

        val envelopeResult = blobService.getBlob(info = recordEnvelopeBlobInfo(walletInstanceId, credentialRecordId))
        if (envelopeResult.isErr) {
            return if (envelopeResult.error.isNotFound()) Ok(null) else Err(envelopeResult.error)
        }

        val envelope = walletJson.decodeFromString<CredentialRecord>(envelopeResult.value.data.decodeToString())
        if (envelope.walletInstanceId != walletInstanceId) return Ok(null)

        val hydratedInstances = mutableListOf<CredentialInstance>()
        for (instance in envelope.instances) {
            val bodyResult = blobService.getBlob(info = instanceBodyBlobInfo(walletInstanceId, envelope.id, instance.id))
            if (bodyResult.isErr) {
                return Err(IdkError.NOT_FOUND_ERROR(message = "Credential instance body '${instance.id}' was not found"))
            }
            val plaintext =
                credentialBodyProtector
                    .open(walletInstanceId, envelope.id, instance.id, bodyResult.value.data)
                    .getOrElse { return Err(it) }
            hydratedInstances += instance.copy(raw = plaintext.decodeToString())
        }
        return Ok(envelope.copy(instances = hydratedInstances))
    }

    override suspend fun getMetadata(
        walletInstanceId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError> {
        val getResult = blobService.getBlob(info = metadataBlobInfoRef(walletInstanceId, credentialRecordId))
        return when {
            getResult.isOk -> {
                val metadata = walletJson.decodeFromString<CredentialMetadata>(getResult.value.data.decodeToString())
                if (metadata.walletInstanceId == walletInstanceId) Ok(metadata) else Ok(null)
            }

            getResult.error.isNotFound() -> {
                Ok(null)
            }

            else -> {
                Err(getResult.error)
            }
        }
    }

    override suspend fun listMetadata(
        walletInstanceId: String,
        filter: CredentialMetadataFilter,
    ): IdkResult<List<CredentialMetadata>, IdkError> {
        val metadataResult =
            fetchSidecarMetadata(
                MetadataSearchQuery(
                    pathPrefix = walletCredentialMetadataPrefix(walletInstanceId),
                    customMetadata = indexedMetadataQuery(walletInstanceId, filter),
                    maxResults = 1000,
                ),
            )
        if (metadataResult.isErr) return Err(metadataResult.error)
        return Ok(metadataResult.value.filter { it.walletInstanceId == walletInstanceId && it.matches(filter) })
    }

    override suspend fun findByCredentialTypeRef(
        walletInstanceId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError> {
        val metadataResult =
            fetchSidecarMetadata(
                MetadataSearchQuery(
                    pathPrefix = walletCredentialMetadataPrefix(walletInstanceId),
                    customMetadata =
                        mapOf(
                            META_WALLET_INSTANCE_ID to walletInstanceId,
                            typeRefIndexKey(ref) to "true",
                        ),
                    maxResults = 1000,
                ),
            )
        if (metadataResult.isErr) return Err(metadataResult.error)
        return Ok(
            metadataResult.value.filter {
                it.walletInstanceId == walletInstanceId &&
                    it.hasTypeRef(ref) &&
                    !it.lifecycleSummary.tombstone
            },
        )
    }

    override suspend fun deleteCredential(
        walletInstanceId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError> {
        val metadataResult = getMetadata(walletInstanceId, credentialRecordId)
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value ?: return Ok(false)

        val envelopeResult = blobService.getBlob(info = recordEnvelopeBlobInfo(walletInstanceId, credentialRecordId))
        if (envelopeResult.isOk) {
            val envelope = walletJson.decodeFromString<CredentialRecord>(envelopeResult.value.data.decodeToString())
            for (instance in envelope.instances) {
                val instanceDelete = blobService.deleteBlob(info = instanceBodyBlobInfo(walletInstanceId, credentialRecordId, instance.id))
                if (instanceDelete.isErr) return Err(instanceDelete.error)
            }
        } else if (!envelopeResult.error.isNotFound()) {
            return Err(envelopeResult.error)
        }
        val envelopeDelete = blobService.deleteBlob(info = recordEnvelopeBlobInfo(walletInstanceId, credentialRecordId))
        if (envelopeDelete.isErr) return Err(envelopeDelete.error)

        val tombstone =
            metadata.copy(
                lifecycleSummary =
                    CredentialLifecycleSummary(
                        lifecycleState = CredentialLifecycleState.DELETED,
                        validityState = CredentialValidityState.UNKNOWN,
                        tombstone = true,
                    ),
                updatedAt = Clock.System.now(),
            )
        val sidecarResult =
            blobService.storeBlob(
                target = metadataBlobInfo(walletInstanceId, credentialRecordId, tombstone),
                data = walletJson.encodeToString(tombstone).encodeToByteArray(),
            )
        return if (sidecarResult.isOk) Ok(true) else Err(sidecarResult.error)
    }

    private suspend fun fetchSidecarMetadata(query: MetadataSearchQuery): IdkResult<List<CredentialMetadata>, IdkError> {
        val findResult = blobService.findByMetadata(info = BlobInfo(), query = query)
        if (findResult.isErr) return Err(findResult.error)

        val metadata = mutableListOf<CredentialMetadata>()
        for (descriptor in findResult.value) {
            val getResult = blobService.getBlob(info = BlobInfo(path = descriptor.path, storeId = descriptor.storeId))
            if (getResult.isOk) {
                metadata.add(walletJson.decodeFromString<CredentialMetadata>(getResult.value.data.decodeToString()))
            } else if (!getResult.error.isNotFound()) {
                return Err(getResult.error)
            }
        }
        return Ok(metadata)
    }

    private fun recordEnvelopeBlobInfo(
        walletInstanceId: String,
        credentialRecordId: String,
    ): BlobInfo =
        BlobInfo(
            path = walletCredentialRecordEnvelopePath(walletInstanceId, credentialRecordId),
            contentType = CONTENT_TYPE,
        )

    private fun instanceBodyBlobInfo(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
    ): BlobInfo =
        BlobInfo(
            path = walletCredentialInstanceBodyPath(walletInstanceId, credentialRecordId, credentialInstanceId),
            contentType = CONTENT_TYPE,
        )

    private fun metadataBlobInfo(
        walletInstanceId: String,
        credentialRecordId: String,
        metadata: CredentialMetadata,
    ): BlobInfo =
        BlobInfo(
            path = walletCredentialMetadataPath(walletInstanceId, credentialRecordId),
            contentType = CONTENT_TYPE,
            metadata = sidecarIndex(metadata),
        )

    private fun metadataBlobInfoRef(
        walletInstanceId: String,
        credentialRecordId: String,
    ): BlobInfo = BlobInfo(path = walletCredentialMetadataPath(walletInstanceId, credentialRecordId))

    private fun sidecarIndex(metadata: CredentialMetadata): Map<String, String> =
        buildMap {
            put(META_WALLET_INSTANCE_ID, metadata.walletInstanceId)
            put(META_CREDENTIAL_RECORD_ID, metadata.credentialRecordId)
            put(META_ISSUER_ID, metadata.issuerRef.value)
            put(META_FORMAT, metadata.format.value)
            put(META_LIFECYCLE, metadata.lifecycleSummary.lifecycleState.name)
            put(META_VALIDITY, metadata.lifecycleSummary.validityState.name)
            metadata.credentialTypeRefs.forEach { put(typeRefIndexKey(it), "true") }
            metadata.credentialConfigurationId?.takeIf { it.isNotBlank() }?.let { put(META_CREDENTIAL_CONFIGURATION_ID, it) }
        }

    private fun indexedMetadataQuery(
        walletInstanceId: String,
        filter: CredentialMetadataFilter,
    ): Map<String, String> =
        buildMap {
            put(META_WALLET_INSTANCE_ID, walletInstanceId)
            filter.issuerRef?.let { put(META_ISSUER_ID, it.value) }
            filter.credentialConfigurationId?.let { put(META_CREDENTIAL_CONFIGURATION_ID, it) }
            filter.formats.singleOrNull()?.let { put(META_FORMAT, it.value) }
            filter.lifecycleStates.singleOrNull()?.let { put(META_LIFECYCLE, it.name) }
            filter.credentialTypeRefs.singleOrNull()?.let { put(typeRefIndexKey(it), "true") }
        }

    private fun CredentialRecord.withBlobBodyRefs(): CredentialRecord =
        copy(
            instances =
                instances.map { instance ->
                    instance.copy(
                        bodyStorageRef =
                            BodyStorageRef(
                                kind = BodyStorageKind.BLOB,
                                path = walletCredentialInstanceBodyPath(walletInstanceId, id, instance.id),
                                storeRef = StoreRef(id = blobService.defaultStoreId(), type = "blob"),
                            ),
                    )
                },
        )

    private fun CredentialRecord.withoutRawBodies(): CredentialRecord = copy(instances = instances.map { it.withoutRaw() })
}

internal fun typeRefIndexKey(ref: CredentialTypeRef): String = "$META_TYPE_REF_PREFIX${ref.format.value}.${ref.kind.name}.${ref.value}"

private fun IdkError.isNotFound(): Boolean = category == ErrorCategory.NOT_FOUND || code == "BLOB_NOT_FOUND" || code == "NOT_FOUND_ERROR"
