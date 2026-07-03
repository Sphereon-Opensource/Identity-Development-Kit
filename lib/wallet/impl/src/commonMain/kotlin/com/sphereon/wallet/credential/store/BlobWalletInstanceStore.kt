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
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.StorageProfile
import com.sphereon.wallet.credential.StoreRef
import com.sphereon.wallet.credential.WalletInstance
import com.sphereon.wallet.credential.WalletInstancePurpose
import com.sphereon.wallet.credential.WalletInstanceStore
import com.sphereon.wallet.credential.WalletStorageMode
import com.sphereon.wallet.credential.WalletStorageProfileResolver
import com.sphereon.wallet.credential.walletInstancePath
import com.sphereon.wallet.credential.walletInstanceRootPrefix
import com.sphereon.wallet.credential.walletStorageProfilePath
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

private val walletInstanceJson = Json { ignoreUnknownKeys = true }

private const val WALLET_INSTANCE_CONTENT_TYPE = "application/vnd.sphereon.wallet.instance+json"
private const val STORAGE_PROFILE_CONTENT_TYPE = "application/vnd.sphereon.wallet.storage-profile+json"
private const val META_ROLE = "walletDocumentRole"
private const val META_WALLET_INSTANCE_ID = "walletInstanceId"
private const val META_OWNER_SUBJECT = "ownerSubject"
private const val META_STORAGE_MODE = "storageMode"
private const val ROLE_WALLET_INSTANCE = "wallet-instance"
private const val ROLE_STORAGE_PROFILE = "storage-profile"

/**
 * Blob-backed wallet-instance registry.
 *
 * This is the local policy root for credential storage routing. Wallet instances and their
 * storage profiles are persisted under `wallet-instances/{walletInstanceId}` alongside the
 * credential, issuance, and operation stores that consume those profiles.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletInstanceStore>())
class BlobWalletInstanceStore(
    private val blobService: BlobService,
) : WalletInstanceStore {
    override suspend fun putWalletInstance(
        walletInstance: WalletInstance,
        storageProfile: StorageProfile,
    ): IdkResult<WalletInstance, IdkError> {
        if (storageProfile.walletInstanceId != walletInstance.id) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "StorageProfile.walletInstanceId must match WalletInstance.id"))
        }
        if (walletInstance.storageProfileId != storageProfile.id) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WalletInstance.storageProfileId must match StorageProfile.id"))
        }

        val instanceResult =
            blobService.storeBlob(
                target = walletInstanceBlobInfo(walletInstance),
                data = walletInstanceJson.encodeToString(walletInstance).encodeToByteArray(),
            )
        if (instanceResult.isErr) return Err(instanceResult.error)

        val profileResult =
            blobService.storeBlob(
                target = storageProfileBlobInfo(storageProfile),
                data = walletInstanceJson.encodeToString(storageProfile).encodeToByteArray(),
            )
        return if (profileResult.isOk) Ok(walletInstance) else Err(profileResult.error)
    }

    override suspend fun getWalletInstance(walletInstanceId: String): IdkResult<WalletInstance?, IdkError> {
        val result = blobService.getBlob(BlobInfo(path = walletInstancePath(walletInstanceId)))
        return if (result.isOk) {
            val instance = walletInstanceJson.decodeFromString<WalletInstance>(result.value.data.decodeToString())
            Ok(instance.takeIf { it.id == walletInstanceId })
        } else {
            Ok(null)
        }
    }

    override suspend fun getStorageProfile(walletInstanceId: String): IdkResult<StorageProfile?, IdkError> {
        val result = blobService.getBlob(BlobInfo(path = walletStorageProfilePath(walletInstanceId)))
        return if (result.isOk) {
            val profile = walletInstanceJson.decodeFromString<StorageProfile>(result.value.data.decodeToString())
            Ok(profile.takeIf { it.walletInstanceId == walletInstanceId })
        } else {
            Ok(null)
        }
    }

    override suspend fun listWalletInstances(includeArchived: Boolean): IdkResult<List<WalletInstance>, IdkError> {
        val findResult =
            blobService.findByMetadata(
                info = BlobInfo(),
                query =
                    MetadataSearchQuery(
                        pathPrefix = walletInstanceRootPrefix(),
                        customMetadata = mapOf(META_ROLE to ROLE_WALLET_INSTANCE),
                        maxResults = 1000,
                    ),
            )
        if (findResult.isErr) return Err(findResult.error)

        val instances = mutableListOf<WalletInstance>()
        for (descriptor in findResult.value) {
            val getResult = blobService.getBlob(BlobInfo(path = descriptor.path, storeId = descriptor.storeId))
            if (getResult.isOk) {
                val instance = walletInstanceJson.decodeFromString<WalletInstance>(getResult.value.data.decodeToString())
                if (includeArchived || instance.archivedAt == null) instances += instance
            }
        }
        return Ok(instances.sortedBy { it.id })
    }

    override suspend fun archiveWalletInstance(
        walletInstanceId: String,
        archivedAt: Instant,
    ): IdkResult<WalletInstance?, IdkError> {
        val current = getWalletInstance(walletInstanceId).getOrElse { return Err(it) } ?: return Ok(null)
        val profile =
            getStorageProfile(walletInstanceId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Storage profile for wallet instance '$walletInstanceId'"))
        val archived = current.copy(archivedAt = archivedAt, updatedAt = archivedAt)
        val putResult = putWalletInstance(archived, profile)
        return if (putResult.isOk) Ok(archived) else Err(putResult.error)
    }

    override suspend fun resolveStorageProfile(walletInstanceId: String): IdkResult<StorageProfile, IdkError> {
        val existing = getStorageProfile(walletInstanceId).getOrElse { return Err(it) }
        if (existing != null) return Ok(existing)

        val now = Clock.System.now()
        val profile = defaultLocalProfile(walletInstanceId)
        val instance =
            WalletInstance(
                id = walletInstanceId,
                ownerSubjectRef = IdentifierRef(type = IdentifierType("wallet-instance"), value = walletInstanceId),
                label = walletInstanceId,
                purpose = WalletInstancePurpose.PERSONAL,
                storageProfileId = profile.id,
                defaultHolderKeyPolicyId = "wallet-holder-key-default",
                createdAt = now,
                updatedAt = now,
            )
        val putResult = putWalletInstance(instance, profile)
        return if (putResult.isOk) Ok(profile) else Err(putResult.error)
    }

    private fun defaultLocalProfile(walletInstanceId: String): StorageProfile =
        StorageProfile(
            id = "$walletInstanceId:local",
            walletInstanceId = walletInstanceId,
            mode = WalletStorageMode.LOCAL,
            localStoreRef = StoreRef(id = blobService.defaultStoreId(), type = "blob"),
            encryptionPolicyId = "wallet-local-default",
        )

    private fun walletInstanceBlobInfo(walletInstance: WalletInstance): BlobInfo =
        BlobInfo(
            path = walletInstancePath(walletInstance.id),
            contentType = WALLET_INSTANCE_CONTENT_TYPE,
            metadata =
                mapOf(
                    META_ROLE to ROLE_WALLET_INSTANCE,
                    META_WALLET_INSTANCE_ID to walletInstance.id,
                    META_OWNER_SUBJECT to walletInstance.ownerSubjectRef.value,
                ),
        )

    private fun storageProfileBlobInfo(storageProfile: StorageProfile): BlobInfo =
        BlobInfo(
            path = walletStorageProfilePath(storageProfile.walletInstanceId),
            contentType = STORAGE_PROFILE_CONTENT_TYPE,
            metadata =
                mapOf(
                    META_ROLE to ROLE_STORAGE_PROFILE,
                    META_WALLET_INSTANCE_ID to storageProfile.walletInstanceId,
                    META_STORAGE_MODE to storageProfile.mode.name,
                ),
        )
}

/**
 * Storage-profile resolver adapter backed by [WalletInstanceStore].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletStorageProfileResolver>())
class BlobWalletStorageProfileResolver(
    private val walletInstanceStore: WalletInstanceStore,
) : WalletStorageProfileResolver {
    override suspend fun resolveStorageProfile(walletInstanceId: String): IdkResult<StorageProfile, IdkError> = walletInstanceStore.resolveStorageProfile(walletInstanceId)
}
