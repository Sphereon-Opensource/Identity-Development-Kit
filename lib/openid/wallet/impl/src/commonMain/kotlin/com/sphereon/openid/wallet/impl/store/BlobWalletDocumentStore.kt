/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.wallet.impl.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.wallet.WalletDocument
import com.sphereon.openid.wallet.WalletDocumentMetadata
import com.sphereon.openid.wallet.WalletDocumentStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val walletJson = Json { ignoreUnknownKeys = true }

private const val BODY_PATH_PREFIX = "wallet-documents"
private const val SIDECAR_PATH_PREFIX = "wallet-document-meta"
private const val CONTENT_TYPE = "application/json"

private const val META_CREDENTIAL_TYPE_ID = "credentialTypeId"
private const val META_ISSUER_ID = "issuerId"
private const val META_STATUS = "status"

/**
 * Blob-backed implementation of [WalletDocumentStore].
 *
 * Each [WalletDocument] is stored as two blobs:
 *
 * - **Body** at `wallet-documents/{documentId}` — full JSON serialization of the document,
 *   including credential instances, display arrays, claims, and refresh state.
 * - **Sidecar** at `wallet-document-meta/{documentId}` — JSON serialization of the derived
 *   [WalletDocumentMetadata] captured at upsert time, with blob custom metadata
 *   (`credentialTypeId`, `issuerId`, `status`) so [BlobService.findByMetadata] can filter
 *   on the sidecar index without loading the body.
 *
 * Metadata-only operations ([getMetadata], [listMetadata], [findMetadataByCredentialType])
 * read only sidecar blobs — they never load body blobs.
 * [get] reads only the body blob.
 * [delete] removes both blobs.
 *
 * The [BlobService] is SessionScope-aware; tenant context flows through it via
 * the injected [com.sphereon.core.api.context.SessionExecution] — tenantId is
 * NOT passed as a method argument here.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletDocumentStore>())
class BlobWalletDocumentStore(
    private val blobService: BlobService,
) : WalletDocumentStore {
    private fun bodyBlobInfo(documentId: String): BlobInfo =
        BlobInfo(
            path = "$BODY_PATH_PREFIX/$documentId",
            contentType = CONTENT_TYPE,
        )

    private fun sidecarBlobInfo(
        documentId: String,
        meta: WalletDocumentMetadata
    ): BlobInfo =
        BlobInfo(
            path = "$SIDECAR_PATH_PREFIX/$documentId",
            contentType = CONTENT_TYPE,
            metadata =
                mapOf(
                    META_CREDENTIAL_TYPE_ID to meta.credentialType,
                    META_ISSUER_ID to meta.issuer.value,
                    META_STATUS to meta.status.name,
                ),
        )

    private fun sidecarBlobInfoRef(documentId: String): BlobInfo = BlobInfo(path = "$SIDECAR_PATH_PREFIX/$documentId")

    override suspend fun upsert(document: WalletDocument): IdkResult<WalletDocument, IdkError> {
        val meta = document.metadata(Clock.System.now())

        val bodyPayload = walletJson.encodeToString(document).encodeToByteArray()
        val bodyResult =
            blobService.storeBlob(
                target = bodyBlobInfo(document.id),
                data = bodyPayload,
            )
        if (bodyResult.isErr) return Err(bodyResult.error)

        val sidecarPayload = walletJson.encodeToString(meta).encodeToByteArray()
        val sidecarResult =
            blobService.storeBlob(
                target = sidecarBlobInfo(document.id, meta),
                data = sidecarPayload,
            )
        return if (sidecarResult.isOk) Ok(document) else Err(sidecarResult.error)
    }

    override suspend fun get(documentId: String): IdkResult<WalletDocument?, IdkError> {
        val getResult = blobService.getBlob(info = bodyBlobInfo(documentId))
        return when {
            getResult.isOk -> {
                val doc = walletJson.decodeFromString<WalletDocument>(getResult.value.data.decodeToString())
                Ok(doc)
            }

            else -> {
                // Blob not found is a normal case — surface as Ok(null)
                Ok(null)
            }
        }
    }

    override suspend fun getMetadata(documentId: String): IdkResult<WalletDocumentMetadata?, IdkError> {
        val getResult = blobService.getBlob(info = sidecarBlobInfoRef(documentId))
        return when {
            getResult.isOk -> Ok(walletJson.decodeFromString<WalletDocumentMetadata>(getResult.value.data.decodeToString()))
            else -> Ok(null)
        }
    }

    override suspend fun listMetadata(): IdkResult<List<WalletDocumentMetadata>, IdkError> = fetchSidecarMetadata(MetadataSearchQuery(pathPrefix = SIDECAR_PATH_PREFIX))

    override suspend fun findMetadataByCredentialType(credentialTypeId: String): IdkResult<List<WalletDocumentMetadata>, IdkError> =
        fetchSidecarMetadata(
            MetadataSearchQuery(
                pathPrefix = SIDECAR_PATH_PREFIX,
                customMetadata = mapOf(META_CREDENTIAL_TYPE_ID to credentialTypeId),
            ),
        )

    override suspend fun delete(documentId: String): IdkResult<Boolean, IdkError> {
        val bodyResult = blobService.deleteBlob(info = bodyBlobInfo(documentId))
        if (bodyResult.isErr) return Err(bodyResult.error)
        // Best-effort sidecar delete; ignore not-found
        blobService.deleteBlob(info = sidecarBlobInfoRef(documentId))
        return Ok(bodyResult.value)
    }

    private suspend fun fetchSidecarMetadata(query: MetadataSearchQuery): IdkResult<List<WalletDocumentMetadata>, IdkError> {
        val findResult =
            blobService.findByMetadata(
                info = BlobInfo(),
                query = query,
            )
        if (findResult.isErr) return Err(findResult.error)

        val metadata = mutableListOf<WalletDocumentMetadata>()
        for (descriptor in findResult.value) {
            val getResult = blobService.getBlob(info = BlobInfo(path = descriptor.path, storeId = descriptor.storeId))
            if (getResult.isOk) {
                metadata.add(walletJson.decodeFromString<WalletDocumentMetadata>(getResult.value.data.decodeToString()))
            }
        }
        return Ok(metadata)
    }
}
