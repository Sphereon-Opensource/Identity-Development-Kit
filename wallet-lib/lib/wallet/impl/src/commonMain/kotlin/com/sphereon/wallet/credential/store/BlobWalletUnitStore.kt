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
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.StorageProfile
import com.sphereon.wallet.credential.StoreRef
import com.sphereon.wallet.credential.WalletUnitProfile
import com.sphereon.wallet.credential.WalletProfilePurpose
import com.sphereon.wallet.credential.WalletStorageMode
import com.sphereon.wallet.credential.WalletStorageProfileResolver
import com.sphereon.wallet.credential.WalletUnitStore
import com.sphereon.wallet.credential.walletStorageProfilePath
import com.sphereon.wallet.credential.walletUnitPath
import com.sphereon.wallet.credential.walletUnitRootPrefix
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock
import kotlin.time.Instant

private val walletUnitJson = Json { ignoreUnknownKeys = true }

private const val WALLET_UNIT_CONTENT_TYPE = "application/vnd.sphereon.wallet.unit+json"
private const val STORAGE_PROFILE_CONTENT_TYPE = "application/vnd.sphereon.wallet.storage-profile+json"
private const val META_ROLE = "walletDocumentRole"
private const val META_WALLET_UNIT_ID = "walletUnitId"
private const val META_OWNER_SUBJECT = "ownerSubject"
private const val META_STORAGE_MODE = "storageMode"
private const val ROLE_WALLET_UNIT = "wallet-unit"
private const val ROLE_STORAGE_PROFILE = "storage-profile"

/**
 * Blob-backed wallet-unit registry.
 *
 * This is the local policy root for credential storage routing. Wallet units and their
 * storage profiles are persisted under `wallet-units/{walletUnitId}` alongside the
 * credential, issuance, and operation stores that consume those profiles.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletUnitStore>())
class BlobWalletUnitStore(
    private val blobService: BlobService,
) : WalletUnitStore {
    private val holderVerificationMethodRegistrationMutex = Mutex()

    override suspend fun putWalletUnit(
        walletInstance: WalletUnitProfile,
        storageProfile: StorageProfile,
    ): IdkResult<WalletUnitProfile, IdkError> {
        if (storageProfile.walletUnitId != walletInstance.id) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "StorageProfile.walletUnitId must match WalletUnitProfile.id"))
        }
        if (walletInstance.storageProfileId != storageProfile.id) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WalletUnitProfile.storageProfileId must match StorageProfile.id"))
        }

        val instanceResult =
            blobService.storeBlob(
                target = walletUnitBlobInfo(walletInstance),
                data = walletUnitJson.encodeToString(walletInstance).encodeToByteArray(),
            )
        if (instanceResult.isErr) return Err(instanceResult.error)

        val profileResult =
            blobService.storeBlob(
                target = storageProfileBlobInfo(storageProfile),
                data = walletUnitJson.encodeToString(storageProfile).encodeToByteArray(),
            )
        return if (profileResult.isOk) Ok(walletInstance) else Err(profileResult.error)
    }

    override suspend fun getWalletUnit(walletUnitId: String): IdkResult<WalletUnitProfile?, IdkError> {
        val result = blobService.getBlob(BlobInfo(path = walletUnitPath(walletUnitId)))
        return if (result.isOk) {
            val instance = walletUnitJson.decodeFromString<WalletUnitProfile>(result.value.data.decodeToString())
            Ok(instance.takeIf { it.id == walletUnitId })
        } else {
            Ok(null)
        }
    }

    override suspend fun registerHolderVerificationMethod(
        walletUnitId: String,
        keyAlias: String,
        method: com.sphereon.wallet.credential.WalletHolderVerificationMethod,
    ): IdkResult<WalletUnitProfile, IdkError> {
        if (walletUnitId.isBlank() || keyAlias.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet unit id and holder key alias must not be blank"))
        }

        holderVerificationMethodRegistrationMutex.lock()
        try {
            val capabilities = blobService.getCapabilities()
            if (!capabilities.supportsConditionalWrites || (!capabilities.supportsEtag && !capabilities.supportsRevisions)) {
                return Err(
                    IdkError.fromString(
                        code = "wallet.holder_verification_method_atomic_store_required",
                        message = "Holder verification-method registration requires conditional blob writes",
                    ),
                )
            }

            repeat(HOLDER_VERIFICATION_METHOD_REGISTRATION_MAX_ATTEMPTS) {
                val resolved =
                    blobService
                        .getBlob(BlobInfo(path = walletUnitPath(walletUnitId)))
                        .getOrElse { return Err(it) }
                val current = walletUnitJson.decodeFromString<WalletUnitProfile>(resolved.data.decodeToString())
                if (current.id != walletUnitId) {
                    return Err(IdkError.NOT_FOUND_ERROR(resource = "Wallet unit '$walletUnitId'"))
                }
                val existing = current.holderVerificationMethods[keyAlias]
                if (existing == method) return Ok(current)
                if (existing != null) {
                    return Err(
                        IdkError.fromString(
                            code = "wallet.holder_verification_method_conflict",
                            message = "Holder key '$keyAlias' already has different verification-method metadata",
                        ),
                    )
                }

                val updated =
                    current.copy(
                        holderVerificationMethods = current.holderVerificationMethods + (keyAlias to method),
                        updatedAt = Clock.System.now(),
                    )
                val descriptor = resolved.descriptor
                val options =
                    if (capabilities.supportsEtag && descriptor.etag != null) {
                        PutOptions(ifMatch = descriptor.etag)
                    } else {
                        PutOptions(
                            expectedRevision =
                                descriptor.revision
                                    ?: return Err(
                                        IdkError.fromString(
                                            code = "wallet.holder_verification_method_atomic_store_required",
                                            message = "Blob store did not return the revision required for atomic holder-key registration",
                                        ),
                                    ),
                        )
                    }
                val stored =
                    blobService.storeBlob(
                        target = walletUnitBlobInfo(updated).copy(storeId = resolved.storeId),
                        data = walletUnitJson.encodeToString(updated).encodeToByteArray(),
                        options = options,
                    )
                if (stored.isOk) return Ok(updated)
                if (stored.error.code != "BLOB_PRECONDITION_FAILED") return Err(stored.error)
            }
            return Err(
                IdkError.fromString(
                    code = "wallet.holder_verification_method_registration_conflict",
                    message = "Concurrent holder verification-method registration did not converge",
                ),
            )
        } finally {
            holderVerificationMethodRegistrationMutex.unlock()
        }
    }

    private companion object {
        const val HOLDER_VERIFICATION_METHOD_REGISTRATION_MAX_ATTEMPTS: Int = 32
    }

    override suspend fun getStorageProfile(walletUnitId: String): IdkResult<StorageProfile?, IdkError> {
        val result = blobService.getBlob(BlobInfo(path = walletStorageProfilePath(walletUnitId)))
        return if (result.isOk) {
            val profile = walletUnitJson.decodeFromString<StorageProfile>(result.value.data.decodeToString())
            Ok(profile.takeIf { it.walletUnitId == walletUnitId })
        } else {
            Ok(null)
        }
    }

    override suspend fun listWalletUnits(includeArchived: Boolean): IdkResult<List<WalletUnitProfile>, IdkError> {
        val findResult =
            blobService.findByMetadata(
                info = BlobInfo(),
                query =
                    MetadataSearchQuery(
                        pathPrefix = walletUnitRootPrefix(),
                        customMetadata = mapOf(META_ROLE to ROLE_WALLET_UNIT),
                        maxResults = 1000,
                    ),
            )
        if (findResult.isErr) return Err(findResult.error)

        val instances = mutableListOf<WalletUnitProfile>()
        for (descriptor in findResult.value) {
            val getResult = blobService.getBlob(BlobInfo(path = descriptor.path, storeId = descriptor.storeId))
            if (getResult.isOk) {
                val instance = walletUnitJson.decodeFromString<WalletUnitProfile>(getResult.value.data.decodeToString())
                if (includeArchived || instance.archivedAt == null) instances += instance
            }
        }
        return Ok(instances.sortedBy { it.id })
    }

    override suspend fun archiveWalletUnit(
        walletUnitId: String,
        archivedAt: Instant,
    ): IdkResult<WalletUnitProfile?, IdkError> {
        val current = getWalletUnit(walletUnitId).getOrElse { return Err(it) } ?: return Ok(null)
        val profile =
            getStorageProfile(walletUnitId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Storage profile for wallet unit '$walletUnitId'"))
        val archived = current.copy(archivedAt = archivedAt, updatedAt = archivedAt)
        val putResult = putWalletUnit(archived, profile)
        return if (putResult.isOk) Ok(archived) else Err(putResult.error)
    }

    override suspend fun resolveStorageProfile(walletUnitId: String): IdkResult<StorageProfile, IdkError> {
        val existing = getStorageProfile(walletUnitId).getOrElse { return Err(it) }
        if (existing != null) return Ok(existing)

        val now = Clock.System.now()
        val profile = defaultLocalProfile(walletUnitId)
        val instance =
            WalletUnitProfile(
                id = walletUnitId,
                ownerSubjectRef = IdentifierRef(type = IdentifierType("wallet-unit"), value = walletUnitId),
                label = walletUnitId,
                purpose = WalletProfilePurpose.PERSONAL,
                storageProfileId = profile.id,
                defaultHolderKeyPolicyId = "wallet-holder-key-default",
                createdAt = now,
                updatedAt = now,
            )
        val putResult = putWalletUnit(instance, profile)
        return if (putResult.isOk) Ok(profile) else Err(putResult.error)
    }

    private fun defaultLocalProfile(walletUnitId: String): StorageProfile =
        StorageProfile(
            id = "$walletUnitId:local",
            walletUnitId = walletUnitId,
            mode = WalletStorageMode.LOCAL,
            localStoreRef = StoreRef(id = blobService.defaultStoreId(), type = "blob"),
            encryptionPolicyId = "wallet-local-default",
        )

    private fun walletUnitBlobInfo(walletInstance: WalletUnitProfile): BlobInfo =
        BlobInfo(
            path = walletUnitPath(walletInstance.id),
            contentType = WALLET_UNIT_CONTENT_TYPE,
            metadata =
                mapOf(
                    META_ROLE to ROLE_WALLET_UNIT,
                    META_WALLET_UNIT_ID to walletInstance.id,
                    META_OWNER_SUBJECT to walletInstance.ownerSubjectRef.value,
                ),
        )

    private fun storageProfileBlobInfo(storageProfile: StorageProfile): BlobInfo =
        BlobInfo(
            path = walletStorageProfilePath(storageProfile.walletUnitId),
            contentType = STORAGE_PROFILE_CONTENT_TYPE,
            metadata =
                mapOf(
                    META_ROLE to ROLE_STORAGE_PROFILE,
                    META_WALLET_UNIT_ID to storageProfile.walletUnitId,
                    META_STORAGE_MODE to storageProfile.mode.name,
                ),
        )
}

/**
 * Storage-profile resolver adapter backed by [WalletUnitStore].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletStorageProfileResolver>())
class BlobWalletStorageProfileResolver(
    private val walletUnitStore: WalletUnitStore,
) : WalletStorageProfileResolver {
    override suspend fun resolveStorageProfile(walletUnitId: String): IdkResult<StorageProfile, IdkError> = walletUnitStore.resolveStorageProfile(walletUnitId)
}
