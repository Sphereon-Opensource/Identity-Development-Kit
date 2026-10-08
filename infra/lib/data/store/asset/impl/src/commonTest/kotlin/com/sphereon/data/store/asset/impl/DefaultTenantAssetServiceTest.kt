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

package com.sphereon.data.store.asset.impl

import com.sphereon.data.store.asset.model.AssetNamespace
import com.sphereon.data.store.asset.model.UploadAssetInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for [DefaultTenantAssetService] over the REAL [com.sphereon.data.store.blob.impl.DefaultBlobService]
 * backed by the in-memory blob store.
 *
 * Verifies the content-addressed scheme: SHA-256 dedup with putIfAbsent semantics per tenant and
 * namespace, tenant isolation (no cross-tenant dedup), stable public URIs, and delete semantics.
 */
class DefaultTenantAssetServiceTest {
    private val tenantA = "tenant-a"
    private val tenantB = "tenant-b"
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val contentType = "image/png"

    // SHA-256 of pngBytes (0x89 0x50 0x4E 0x47), lowercase hex + standard base64.
    private val expectedHash = "0f4636c78f65d3639ece5a064b5ae753e3408614a14fb18ab4d7540d2c248543"
    private val expectedIntegrity = "sha256-D0Y2x49l02OezloGS1rnU+NAhhShT7GKtNdUDSwkhUM="

    private fun makeInput(
        namespace: AssetNamespace = AssetNamespace.BRAND,
        data: ByteArray = pngBytes,
    ): UploadAssetInput = UploadAssetInput(namespace = namespace, data = data, contentType = contentType)

    @Test
    fun uploadAsset_returnsTenantScopedContentAddressedUriAndIntegrity() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val result = service.uploadAsset(tenantA, makeInput())

            assertTrue(result.isOk)
            val ref = result.value
            assertEquals("/public/assets/$tenantA/brand/$expectedHash.png", ref.uri)
            assertEquals(expectedIntegrity, ref.integrity)
            assertEquals(contentType, ref.contentType)
            assertEquals("assets/$tenantA/brand/by-hash/$expectedHash", ref.localBlob?.path)
            assertEquals(tenantA, ref.localBlob?.tenantId)
        }

    @Test
    fun uploadAsset_sameBytesTwiceSameTenant_dedupsToSameUriAndStoresOneBlob() =
        runTest {
            val counting = CountingBlobService(createTestBlobService())
            val service = DefaultTenantAssetService(counting)

            val first = service.uploadAsset(tenantA, makeInput())
            val second = service.uploadAsset(tenantA, makeInput())

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertEquals(first.value.uri, second.value.uri)
            assertEquals(first.value.integrity, second.value.integrity)
            // Identical bytes must not be written a second time (putIfAbsent semantics).
            assertEquals(1, counting.storeCount)

            val listed = service.listAssets(tenantA, AssetNamespace.BRAND)
            assertTrue(listed.isOk)
            assertEquals(1, listed.value.size)
            assertEquals(expectedHash, listed.value.single().hash)
        }

    @Test
    fun uploadAsset_sameBytesDifferentTenants_doesNotDedupAcrossTenants() =
        runTest {
            val counting = CountingBlobService(createTestBlobService())
            val service = DefaultTenantAssetService(counting)

            val first = service.uploadAsset(tenantA, makeInput())
            val second = service.uploadAsset(tenantB, makeInput())

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            // Same content hash, but tenant-scoped URIs and blobs stay distinct.
            assertEquals("/public/assets/$tenantA/brand/$expectedHash.png", first.value.uri)
            assertEquals("/public/assets/$tenantB/brand/$expectedHash.png", second.value.uri)
            assertNotEquals(first.value.uri, second.value.uri)
            assertEquals(2, counting.storeCount)

            val listedA = service.listAssets(tenantA, AssetNamespace.BRAND)
            val listedB = service.listAssets(tenantB, AssetNamespace.BRAND)
            assertTrue(listedA.isOk)
            assertTrue(listedB.isOk)
            assertEquals(1, listedA.value.size)
            assertEquals(1, listedB.value.size)
        }

    @Test
    fun uploadAsset_differentBytes_produceDifferentHash() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val first = service.uploadAsset(tenantA, makeInput())
            val second = service.uploadAsset(tenantA, makeInput(data = byteArrayOf(0x01, 0x02, 0x03)))

            assertTrue(first.isOk)
            assertTrue(second.isOk)
            assertNotEquals(first.value.uri, second.value.uri)
            assertNotEquals(first.value.integrity, second.value.integrity)
        }

    @Test
    fun uploadAsset_namespacesAreIsolatedWithinTenant() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val brand = service.uploadAsset(tenantA, makeInput(namespace = AssetNamespace.BRAND))
            val design = service.uploadAsset(tenantA, makeInput(namespace = AssetNamespace.DESIGN))

            assertTrue(brand.isOk)
            assertTrue(design.isOk)
            assertEquals("/public/assets/$tenantA/brand/$expectedHash.png", brand.value.uri)
            assertEquals("/public/assets/$tenantA/design/$expectedHash.png", design.value.uri)

            val listedBrand = service.listAssets(tenantA, AssetNamespace.BRAND)
            assertTrue(listedBrand.isOk)
            assertEquals(1, listedBrand.value.size)
        }

    @Test
    fun getAsset_returnsDescriptorMatchingUpload() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val uploaded = service.uploadAsset(tenantA, makeInput())
            assertTrue(uploaded.isOk)

            val fetched = service.getAsset(tenantA, AssetNamespace.BRAND, expectedHash)
            assertTrue(fetched.isOk)
            assertEquals(uploaded.value.uri, fetched.value.uri)
            assertEquals(contentType, fetched.value.contentType)
            assertEquals(expectedHash, fetched.value.hash)
            assertEquals(pngBytes.size.toLong(), fetched.value.sizeBytes)
        }

    @Test
    fun getAssetContent_roundTripsBytesAndContentType() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val uploaded = service.uploadAsset(tenantA, makeInput())
            assertTrue(uploaded.isOk)

            val resolved = service.getAssetContent(tenantA, AssetNamespace.BRAND, expectedHash)
            assertTrue(resolved.isOk)
            assertEquals(pngBytes.toList(), resolved.value.data.toList())
            assertEquals(contentType, resolved.value.contentType)
            assertEquals(uploaded.value.uri, resolved.value.info.uri)
        }

    @Test
    fun getAsset_unknownHash_errs() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val fetched = service.getAsset(tenantA, AssetNamespace.BRAND, "0".repeat(64))
            assertTrue(fetched.isErr)
        }

    @Test
    fun getAsset_invalidHashFormat_errsWithoutReachingBlobStore() =
        runTest {
            val counting = CountingBlobService(createTestBlobService())
            val service = DefaultTenantAssetService(counting)

            val traversal = service.getAsset(tenantA, AssetNamespace.BRAND, "../../etc/passwd")
            val short = service.getAsset(tenantA, AssetNamespace.BRAND, "abc")

            assertTrue(traversal.isErr)
            assertTrue(short.isErr)
            assertEquals(0, counting.storeCount)
        }

    @Test
    fun getAsset_otherTenant_doesNotSeeAsset() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val uploaded = service.uploadAsset(tenantA, makeInput())
            assertTrue(uploaded.isOk)

            val fetched = service.getAsset(tenantB, AssetNamespace.BRAND, expectedHash)
            assertTrue(fetched.isErr)
        }

    @Test
    fun listAssets_contentTypeFilter_matchesPrefixCaseInsensitively() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val uploaded = service.uploadAsset(tenantA, makeInput())
            assertTrue(uploaded.isOk)

            val images = service.listAssets(tenantA, AssetNamespace.BRAND, contentType = "IMAGE/")
            assertTrue(images.isOk)
            assertEquals(1, images.value.size)

            val pdfs = service.listAssets(tenantA, AssetNamespace.BRAND, contentType = "application/pdf")
            assertTrue(pdfs.isOk)
            assertEquals(0, pdfs.value.size)
        }

    @Test
    fun deleteAsset_removesAsset_andGetAfterDeleteErrs() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val uploaded = service.uploadAsset(tenantA, makeInput())
            assertTrue(uploaded.isOk)

            val deleted = service.deleteAsset(tenantA, AssetNamespace.BRAND, expectedHash)
            assertTrue(deleted.isOk)

            val fetched = service.getAsset(tenantA, AssetNamespace.BRAND, expectedHash)
            assertTrue(fetched.isErr)
            val content = service.getAssetContent(tenantA, AssetNamespace.BRAND, expectedHash)
            assertTrue(content.isErr)

            val listed = service.listAssets(tenantA, AssetNamespace.BRAND)
            assertTrue(listed.isOk)
            assertEquals(0, listed.value.size)
        }

    @Test
    fun deleteAsset_unknownHash_errs() =
        runTest {
            val service = DefaultTenantAssetService(createTestBlobService())

            val deleted = service.deleteAsset(tenantA, AssetNamespace.BRAND, "0".repeat(64))
            assertTrue(deleted.isErr)
        }
}
