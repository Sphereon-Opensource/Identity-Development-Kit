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
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.LocalWalletIssuanceSessionStore
import com.sphereon.wallet.credential.SecretRef
import com.sphereon.wallet.credential.StoreRef
import com.sphereon.wallet.credential.walletDeferredAccessTokenPath
import com.sphereon.wallet.credential.walletIssuancePrefix
import com.sphereon.wallet.credential.walletIssuanceSessionPath
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val issuanceJson = Json { ignoreUnknownKeys = true }

private const val SESSION_CONTENT_TYPE = "application/vnd.sphereon.wallet.issuance-session+json"
private const val SECRET_CONTENT_TYPE = "application/vnd.sphereon.wallet.deferred-secret+text"
private const val META_WALLET_INSTANCE_ID = "walletInstanceId"
private const val META_ISSUANCE_SESSION_ID = "issuanceSessionId"
private const val META_ISSUANCE_STATUS = "issuanceStatus"
private const val META_CREDENTIAL_CONFIGURATION_ID = "credentialConfigurationId"

/**
 * Blob-backed wallet issuance session persistence rooted in the wallet namespace.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<LocalWalletIssuanceSessionStore>())
class BlobWalletIssuanceSessionStore(
    private val blobService: BlobService,
) : LocalWalletIssuanceSessionStore {
    override suspend fun putSession(
        walletInstanceId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> {
        if (session.walletInstanceId != walletInstanceId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "session.walletInstanceId must match walletInstanceId"))
        }

        val result =
            blobService.storeBlob(
                target = sessionBlobInfo(walletInstanceId, session),
                data = issuanceJson.encodeToString(session).encodeToByteArray(),
            )
        return if (result.isOk) Ok(session) else Err(result.error)
    }

    override suspend fun getSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> {
        val result = blobService.getBlob(sessionBlobInfoRef(walletInstanceId, issuanceSessionId))
        return when {
            result.isOk -> {
                val session = issuanceJson.decodeFromString<IssuanceSession>(result.value.data.decodeToString())
                if (session.walletInstanceId == walletInstanceId) Ok(session) else Ok(null)
            }

            else -> {
                Ok(null)
            }
        }
    }

    override suspend fun listSessions(
        walletInstanceId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> {
        val query =
            MetadataSearchQuery(
                pathPrefix = walletIssuancePrefix(walletInstanceId),
                customMetadata =
                    buildMap {
                        put(META_WALLET_INSTANCE_ID, walletInstanceId)
                        statuses.singleOrNull()?.let { put(META_ISSUANCE_STATUS, it.name) }
                    },
                maxResults = 1000,
            )
        val findResult = blobService.findByMetadata(info = BlobInfo(), query = query)
        if (findResult.isErr) return Err(findResult.error)

        val sessions = mutableListOf<IssuanceSession>()
        for (descriptor in findResult.value) {
            if (descriptor.path.endsWith("/access-token")) continue
            val getResult = blobService.getBlob(BlobInfo(path = descriptor.path, storeId = descriptor.storeId))
            if (getResult.isOk) {
                val session = issuanceJson.decodeFromString<IssuanceSession>(getResult.value.data.decodeToString())
                if (session.walletInstanceId == walletInstanceId && (statuses.isEmpty() || session.status in statuses)) {
                    sessions += session
                }
            }
        }
        return Ok(sessions)
    }

    override suspend fun storeDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> {
        val path = walletDeferredAccessTokenPath(walletInstanceId, issuanceSessionId)
        val result =
            blobService.storeBlob(
                target =
                    BlobInfo(
                        path = path,
                        contentType = SECRET_CONTENT_TYPE,
                        metadata =
                            mapOf(
                                META_WALLET_INSTANCE_ID to walletInstanceId,
                                META_ISSUANCE_SESSION_ID to issuanceSessionId,
                            ),
                    ),
                data = accessToken.encodeToByteArray(),
            )
        if (result.isErr) return Err(result.error)
        return Ok(
            SecretRef(
                id = path,
                storeRef = StoreRef(id = blobService.defaultStoreId(), type = "blob"),
            ),
        )
    }

    override suspend fun getDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> {
        val result = blobService.getBlob(BlobInfo(path = walletDeferredAccessTokenPath(walletInstanceId, issuanceSessionId)))
        return if (result.isOk) Ok(result.value.data.decodeToString()) else Ok(null)
    }

    override suspend fun deleteSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> {
        val sessionResult = blobService.deleteBlob(sessionBlobInfoRef(walletInstanceId, issuanceSessionId))
        if (sessionResult.isErr) return Err(sessionResult.error)
        val secretResult = blobService.deleteBlob(BlobInfo(path = walletDeferredAccessTokenPath(walletInstanceId, issuanceSessionId)))
        if (secretResult.isErr) return Err(secretResult.error)
        return Ok(sessionResult.value || secretResult.value)
    }

    private fun sessionBlobInfo(
        walletInstanceId: String,
        session: IssuanceSession,
    ): BlobInfo =
        BlobInfo(
            path = walletIssuanceSessionPath(walletInstanceId, session.id),
            contentType = SESSION_CONTENT_TYPE,
            metadata =
                mapOf(
                    META_WALLET_INSTANCE_ID to walletInstanceId,
                    META_ISSUANCE_SESSION_ID to session.id,
                    META_ISSUANCE_STATUS to session.status.name,
                    META_CREDENTIAL_CONFIGURATION_ID to session.credentialConfigurationId,
                ),
        )

    private fun sessionBlobInfoRef(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): BlobInfo = BlobInfo(path = walletIssuanceSessionPath(walletInstanceId, issuanceSessionId))
}
