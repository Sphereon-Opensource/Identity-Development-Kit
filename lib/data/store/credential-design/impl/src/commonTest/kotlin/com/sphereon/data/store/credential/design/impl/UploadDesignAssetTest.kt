/*
 * Copyright 2023-2026 Sphereon International B.V.
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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.credential.design.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import com.sphereon.data.store.credential.design.config.CredentialDesignConfigProvider
import com.sphereon.data.store.credential.design.impl.DesignExternalFetcher
import com.sphereon.data.store.credential.design.impl.DesignFetchResult
import com.sphereon.data.store.credential.design.model.CredentialDesignModuleConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import com.sphereon.data.store.credential.design.model.DesignAssetType
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.SourceSnapshotRecord
import com.sphereon.data.store.credential.design.model.UploadDesignAssetInput
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.persistence.CredentialDesignRepository
import com.sphereon.data.store.credential.design.persistence.DerivedRenderHintsRepository
import com.sphereon.data.store.credential.design.persistence.IssuerDesignRepository
import com.sphereon.data.store.credential.design.persistence.RenderVariantRepository
import com.sphereon.data.store.credential.design.persistence.SourceSnapshotRepository
import com.sphereon.data.store.credential.design.persistence.VerifierDesignRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for [DefaultCredentialDesignService.uploadDesignAsset] and [getDesignAssetByHash].
 *
 * Verifies the CONTENT-ADDRESSED hosting scheme:
 * - the returned [com.sphereon.data.store.asset.model.AssetReference.uri] ends with
 *   `/public/assets/design/<64-hex-sha256>` (absolute when an external base URL is configured,
 *   relative otherwise), and carries a matching `integrity = "sha256-<base64>"`.
 * - uploading the SAME bytes twice yields the SAME uri/hash (dedup) and does not re-write the blob.
 * - different bytes produce a different hash.
 * - [getDesignAssetByHash] round-trips the stored bytes and content-type.
 */
class UploadDesignAssetTest {
    private val designId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
    private val tenantId = "tenant-test-001"
    private val locale = "en"
    private val assetType = DesignAssetType.LOGO
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val contentType = "image/png"

    // SHA-256 of pngBytes (0x89 0x50 0x4E 0x47), lowercase hex + standard base64.
    private val expectedHash = "0f4636c78f65d3639ece5a064b5ae753e3408614a14fb18ab4d7540d2c248543"
    private val expectedIntegrity = "sha256-D0Y2x49l02OezloGS1rnU+NAhhShT7GKtNdUDSwkhUM="

    // ---------------------------------------------------------------------------
    // Fixture helpers
    // ---------------------------------------------------------------------------

    private fun makeService(
        externalBaseUrl: String?,
        blobService: StubBlobService = StubBlobService(),
    ): DefaultCredentialDesignService {
        val config = CredentialDesignModuleConfig(externalBaseUrl = externalBaseUrl)
        val configProvider =
            object : CredentialDesignConfigProvider {
                override fun getConfig(): CredentialDesignModuleConfig = config
            }
        return DefaultCredentialDesignService(
            credentialDesignRepository = StubCredentialDesignRepository(),
            issuerDesignRepository = StubIssuerDesignRepository(),
            verifierDesignRepository = StubVerifierDesignRepository(),
            renderVariantRepository = StubRenderVariantRepository(),
            sourceSnapshotRepository = StubSourceSnapshotRepository(),
            derivedRenderHintsRepository = StubDerivedRenderHintsRepository(),
            blobService = blobService,
            externalFetcher = StubDesignExternalFetcher(),
            configProvider = configProvider,
        )
    }

    private fun makeInput(): UploadDesignAssetInput =
        UploadDesignAssetInput(
            designId = designId,
            locale = locale,
            assetType = assetType,
            data = pngBytes,
            contentType = contentType,
        )

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun uploadDesignAsset_storesRelativeUriEvenWhenExternalBaseUrlConfigured() =
        runTest {
            // Upload ALWAYS stores the RELATIVE content-addressed URI now: the per-tenant absolute
            // host is applied at SERVE time (PublicDesignAssetPaths.toAbsolute), not pinned to a
            // single static config base — so multi-tenant gateway logos carry each tenant's host.
            val externalBaseUrl = "https://issuer.example.com"
            val service = makeService(externalBaseUrl)

            val result = service.uploadDesignAsset(tenantId, makeInput())

            assertTrue(result.isOk)
            val ref = result.value
            assertEquals("/public/assets/design/$expectedHash.png", ref.uri)
            assertEquals(expectedIntegrity, ref.integrity)
            // Extension is appended; hash is NOT extended — same 64-hex prefix
            assertTrue(ref.uri.endsWith("/$expectedHash.png"))
        }

    @Test
    fun uploadDesignAsset_withoutExternalBaseUrl_returnsRelativeContentAddressedUri() =
        runTest {
            val service = makeService(externalBaseUrl = null)

            val result = service.uploadDesignAsset(tenantId, makeInput())

            assertTrue(result.isOk)
            val ref = result.value
            assertEquals("/public/assets/design/$expectedHash.png", ref.uri)
            assertEquals(expectedIntegrity, ref.integrity)
        }

    @Test
    fun uploadDesignAsset_pngInput_uriEndsWith64HexDotPng() =
        runTest {
            val service = makeService(externalBaseUrl = null)

            val result = service.uploadDesignAsset(tenantId, makeInput())

            assertTrue(result.isOk)
            val uri = result.value.uri
            // URI leaf must be exactly <64-hex-hash>.png
            val leaf = uri.substringAfterLast('/')
            assertTrue(leaf.endsWith(".png"), "Expected leaf to end with .png but was: $leaf")
            val hashPart = leaf.substringBefore('.')
            assertEquals(64, hashPart.length, "Hash part must be 64 hex chars, got: $hashPart")
            assertEquals(expectedHash, hashPart)
        }

    @Test
    fun uploadDesignAsset_unknownContentType_uriHasNoExtension() =
        runTest {
            val service = makeService(externalBaseUrl = null)
            val input =
                UploadDesignAssetInput(
                    designId = designId,
                    locale = locale,
                    assetType = assetType,
                    data = pngBytes,
                    contentType = "application/octet-stream",
                )

            val result = service.uploadDesignAsset(tenantId, input)

            assertTrue(result.isOk)
            val uri = result.value.uri
            val leaf = uri.substringAfterLast('/')
            // No dot in leaf — no extension appended for unknown content-type
            assertTrue(!leaf.contains('.'), "Expected no extension for unknown content-type but got: $leaf")
        }

    @Test
    fun uploadDesignAsset_localBlobPath_isContentAddressedStorageKey_notPublicUri() =
        runTest {
            val service = makeService(externalBaseUrl = "https://issuer.example.com")

            val result = service.uploadDesignAsset(tenantId, makeInput())

            assertTrue(result.isOk)
            val ref = result.value
            // localBlob path is the internal content-addressed storage key (no extension — raw hash only)
            val expectedBlobPath = "vc-designs/$tenantId/assets/by-hash/$expectedHash"
            assertEquals(expectedBlobPath, ref.localBlob?.path)
            // The PUBLIC URI carries the extension and is RELATIVE (host applied at serve time);
            // the internal blob path does NOT carry the extension.
            assertEquals("/public/assets/design/$expectedHash.png", ref.uri)
            assertTrue(ref.uri.endsWith("$expectedHash.png"))
        }

    @Test
    fun uploadDesignAsset_contentType_isPreserved() =
        runTest {
            val service = makeService(externalBaseUrl = null)

            val result = service.uploadDesignAsset(tenantId, makeInput())

            assertTrue(result.isOk)
            assertEquals(contentType, result.value.contentType)
        }

    @Test
    fun uploadDesignAsset_sameBytesTwice_dedupsToSameUriAndWritesOnce() =
        runTest {
            val blob = StubBlobService()
            val service = makeService(externalBaseUrl = null, blobService = blob)

            val first = service.uploadDesignAsset(tenantId, makeInput())
            val second = service.uploadDesignAsset(tenantId, makeInput())

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertEquals(first.value.uri, second.value.uri)
            assertEquals(first.value.integrity, second.value.integrity)
            // Identical bytes must not be written a second time.
            assertEquals(1, blob.storeCount)
        }

    @Test
    fun uploadDesignAsset_differentBytes_produceDifferentHash() =
        runTest {
            val service = makeService(externalBaseUrl = null)

            val first = service.uploadDesignAsset(tenantId, makeInput())
            val otherInput =
                UploadDesignAssetInput(
                    designId = designId,
                    locale = locale,
                    assetType = assetType,
                    data = byteArrayOf(0x01, 0x02, 0x03),
                    contentType = contentType,
                )
            val second = service.uploadDesignAsset(tenantId, otherInput)

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertTrue(first.value.uri != second.value.uri)
        }

    @Test
    fun getDesignAssetByHash_roundTripsBytesAndContentType() =
        runTest {
            val blob = StubBlobService()
            val service = makeService(externalBaseUrl = null, blobService = blob)

            val uploaded = service.uploadDesignAsset(tenantId, makeInput())
            assertTrue(uploaded.isOk)

            val fetched = service.getDesignAssetByHash(tenantId, expectedHash)
            assertTrue(fetched.isOk)
            assertEquals(pngBytes.toList(), fetched.value.data.toList())
            assertEquals(contentType, fetched.value.contentType)
        }

    @Test
    fun getDesignAssetByHash_unknownHash_returnsNotFound() =
        runTest {
            val service = makeService(externalBaseUrl = null)

            val fetched = service.getDesignAssetByHash(tenantId, "deadbeef")
            assertTrue(fetched.isErr)
        }

    @Test
    fun getDesignAssetByHash_pathTraversalInput_returnsNotFoundWithoutCallingBlob() =
        runTest {
            val blob = StubBlobService()
            val service = makeService(externalBaseUrl = null, blobService = blob)

            val fetched = service.getDesignAssetByHash(tenantId, "../../etc/passwd")

            assertTrue(fetched.isErr)
            assertEquals(
                IdkError.NOT_FOUND_ERROR(message = "Design asset not found").code,
                fetched.error.code,
            )
            // Blob store must NOT be called for invalid hashes
            assertEquals(0, blob.storeCount)
            assertEquals(0, blob.getCount)
        }

    @Test
    fun getDesignAssetByHash_shortHash_returnsNotFoundWithoutCallingBlob() =
        runTest {
            val blob = StubBlobService()
            val service = makeService(externalBaseUrl = null, blobService = blob)

            val fetched = service.getDesignAssetByHash(tenantId, "abc")

            assertTrue(fetched.isErr)
            assertEquals(
                IdkError.NOT_FOUND_ERROR(message = "Design asset not found").code,
                fetched.error.code,
            )
            assertEquals(0, blob.storeCount)
            assertEquals(0, blob.getCount)
        }

    @Test
    fun getDesignAssetByHash_validHash_doesNotRejectHashFormat() =
        runTest {
            val blob = StubBlobService()
            val service = makeService(externalBaseUrl = null, blobService = blob)

            // First upload so the blob exists
            service.uploadDesignAsset(tenantId, makeInput())

            // Valid 64-char lowercase hex hash passes format validation and reaches blob store
            val fetched = service.getDesignAssetByHash(tenantId, expectedHash)

            assertTrue(fetched.isOk)
            assertEquals(pngBytes.toList(), fetched.value.data.toList())
        }

    // ---------------------------------------------------------------------------
    // Minimal stubs
    // ---------------------------------------------------------------------------

    /** Minimal in-memory blob service: stores bytes + content-type by path, counts writes and reads. */
    private class StubBlobService : BlobService {
        private val store = mutableMapOf<String, Pair<ByteArray, String?>>()
        var storeCount: Int = 0
            private set
        var getCount: Int = 0
            private set

        override fun defaultStoreId(): String = "default"

        override suspend fun storeBlob(
            target: BlobInfo,
            data: ByteArray,
            options: PutOptions,
        ): IdkResult<BlobDescriptor, IdkError> {
            storeCount++
            val path = target.path ?: ""
            store[path] = data to target.contentType
            return Ok(BlobDescriptor(path = path, storeId = "default", sizeBytes = data.size.toLong(), contentType = target.contentType))
        }

        override suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
            getCount++
            val path = info.path ?: ""
            val entry = store[path] ?: return Err(IdkError.NOT_FOUND_ERROR(message = "blob not found: $path"))
            val descriptor = BlobDescriptor(path = path, storeId = "default", sizeBytes = entry.first.size.toLong(), contentType = entry.second)
            return Ok(ResolvedBlobInfo.fromContent(info = info.toBlobInfo().copy(contentType = entry.second), data = entry.first, descriptor = descriptor))
        }

        override suspend fun getBlobInfo(info: BlobInfoType): IdkResult<BlobDescriptor, IdkError> {
            val path = info.path ?: ""
            val entry = store[path] ?: return Err(IdkError.NOT_FOUND_ERROR(message = "blob not found: $path"))
            return Ok(BlobDescriptor(path = path, storeId = "default", sizeBytes = entry.first.size.toLong(), contentType = entry.second))
        }

        override suspend fun deleteBlob(info: BlobInfoType): IdkResult<Boolean, IdkError> = throw NotImplementedError()

        override suspend fun listBlobs(
            info: BlobInfo,
            options: ListOptions
        ): IdkResult<ListResult, IdkError> = throw NotImplementedError()

        override suspend fun copyBlob(
            source: BlobInfoType,
            destination: BlobInfo
        ): IdkResult<BlobDescriptor, IdkError> = throw NotImplementedError()

        override suspend fun moveBlob(
            source: BlobInfoType,
            destination: BlobInfo
        ): IdkResult<BlobDescriptor, IdkError> = throw NotImplementedError()

        override suspend fun casStore(
            info: BlobInfo,
            data: ByteArray,
            algorithm: DigestAlg
        ): IdkResult<ContentAddressDescriptor, IdkError> = throw NotImplementedError()

        override suspend fun casGet(
            info: BlobInfo,
            address: ContentAddress
        ): IdkResult<ResolvedBlobInfo, IdkError> = throw NotImplementedError()

        override suspend fun casVerify(
            info: BlobInfo,
            address: ContentAddress
        ): IdkResult<Boolean, IdkError> = throw NotImplementedError()

        override suspend fun findByMetadata(
            info: BlobInfo,
            query: MetadataSearchQuery
        ): IdkResult<List<BlobDescriptor>, IdkError> = throw NotImplementedError()

        override suspend fun createTempUrl(
            info: BlobInfoType,
            options: TempUrlOptions
        ): IdkResult<TempUrlResult, IdkError> = throw NotImplementedError()
    }

    private class StubDesignExternalFetcher : DesignExternalFetcher {
        override suspend fun fetch(
            url: String,
            ifNoneMatch: String?,
            maxSizeBytes: Long,
        ): IdkResult<DesignFetchResult, IdkError> = throw NotImplementedError()
    }

    private class StubCredentialDesignRepository : CredentialDesignRepository {
        override suspend fun findById(
            tenantId: String,
            id: Uuid
        ): CredentialDesignRecord? = null

        override suspend fun findByBinding(
            tenantId: String,
            binding: DesignBinding
        ): List<CredentialDesignRecord> = emptyList()

        override suspend fun findByBindingKey(
            tenantId: String,
            bindingKey: DesignBindingKey,
            bindingValue: String
        ): List<CredentialDesignRecord> = emptyList()

        override suspend fun findAll(
            tenantId: String,
            filter: DesignFilter
        ): List<CredentialDesignRecord> = emptyList()

        override suspend fun create(record: CredentialDesignRecord): CredentialDesignRecord = record

        override suspend fun update(record: CredentialDesignRecord): CredentialDesignRecord = record

        override suspend fun delete(
            tenantId: String,
            id: Uuid
        ): Boolean = true
    }

    private class StubIssuerDesignRepository : IssuerDesignRepository {
        override suspend fun findById(
            tenantId: String,
            id: Uuid
        ): IssuerDesignRecord? = null

        override suspend fun findByBinding(
            tenantId: String,
            binding: DesignBinding
        ): List<IssuerDesignRecord> = emptyList()

        override suspend fun findByBindingKey(
            tenantId: String,
            bindingKey: DesignBindingKey,
            bindingValue: String
        ): List<IssuerDesignRecord> = emptyList()

        override suspend fun findAll(
            tenantId: String,
            filter: DesignFilter
        ): List<IssuerDesignRecord> = emptyList()

        override suspend fun create(record: IssuerDesignRecord): IssuerDesignRecord = record

        override suspend fun update(record: IssuerDesignRecord): IssuerDesignRecord = record

        override suspend fun delete(
            tenantId: String,
            id: Uuid
        ): Boolean = true
    }

    private class StubVerifierDesignRepository : VerifierDesignRepository {
        override suspend fun findById(
            tenantId: String,
            id: Uuid
        ): VerifierDesignRecord? = null

        override suspend fun findByBinding(
            tenantId: String,
            binding: DesignBinding
        ): List<VerifierDesignRecord> = emptyList()

        override suspend fun findByBindingKey(
            tenantId: String,
            bindingKey: DesignBindingKey,
            bindingValue: String
        ): List<VerifierDesignRecord> = emptyList()

        override suspend fun findAll(
            tenantId: String,
            filter: DesignFilter
        ): List<VerifierDesignRecord> = emptyList()

        override suspend fun create(record: VerifierDesignRecord): VerifierDesignRecord = record

        override suspend fun update(record: VerifierDesignRecord): VerifierDesignRecord = record

        override suspend fun delete(
            tenantId: String,
            id: Uuid
        ): Boolean = true
    }

    private class StubRenderVariantRepository : RenderVariantRepository {
        override suspend fun findById(
            tenantId: String,
            id: Uuid
        ): RenderVariantRecord? = null

        override suspend fun findAll(
            tenantId: String,
            filter: DesignFilter
        ): List<RenderVariantRecord> = emptyList()

        override suspend fun create(record: RenderVariantRecord): RenderVariantRecord = record

        override suspend fun update(record: RenderVariantRecord): RenderVariantRecord = record

        override suspend fun delete(
            tenantId: String,
            id: Uuid
        ): Boolean = true
    }

    private class StubSourceSnapshotRepository : SourceSnapshotRepository {
        override suspend fun findById(
            tenantId: String,
            id: Uuid
        ): SourceSnapshotRecord? = null

        override suspend fun findBySaid(
            tenantId: String,
            said: String
        ): SourceSnapshotRecord? = null

        override suspend fun create(record: SourceSnapshotRecord): SourceSnapshotRecord = record

        override suspend fun listByDesignId(
            tenantId: String,
            designId: Uuid
        ): List<SourceSnapshotRecord> = emptyList()
    }

    private class StubDerivedRenderHintsRepository : DerivedRenderHintsRepository {
        override suspend fun findById(
            tenantId: String,
            id: Uuid
        ): DerivedRenderHintsRecord? = null

        override suspend fun create(record: DerivedRenderHintsRecord): DerivedRenderHintsRecord = record

        override suspend fun delete(
            tenantId: String,
            id: Uuid
        ): Boolean = true
    }
}
