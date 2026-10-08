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

package com.sphereon.data.store.vault.portability

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException

class BagItImportSecurityTest {
    @Test
    fun `malicious hierarchy reports traversal reserved case and unicode collisions`() =
        runTest {
            val entries =
                listOf(
                    entry("data/../escape"),
                    entry("data/CON"),
                    entry("data/exact.txt"),
                    entry("data/exact.txt"),
                    entry("data/Name.txt"),
                    entry("data/name.txt"),
                    entry("data/e\u0301.txt"),
                    entry("data/é.txt"),
                )

            val result = BagItImportValidator().verify(TestArchiveSource(entries))
            val codes = result.issues.map { it.code }.toSet()

            assertTrue(VaultVerificationIssueCode.PATH_TRAVERSAL in codes)
            assertTrue(VaultVerificationIssueCode.RESERVED_PATH in codes)
            assertTrue(VaultVerificationIssueCode.DUPLICATE_PATH in codes)
            assertTrue(VaultVerificationIssueCode.CASE_COLLISION in codes)
            assertTrue(VaultVerificationIssueCode.UNICODE_COLLISION in codes)
        }

    @Test
    fun `import limits reject object count depth entry size and decompression ratio`() =
        runTest {
            val objectLimit =
                BagItImportValidator(limits = VaultImportLimits(maxObjectCount = 1, maxEntryUncompressedBytes = 10, maxTotalUncompressedBytes = 20))
                    .verify(TestArchiveSource(listOf(entry("data/a"), entry("data/b"))))
            assertTrue(objectLimit.issues.any { it.code == VaultVerificationIssueCode.OBJECT_COUNT_LIMIT })

            val depthLimit =
                BagItImportValidator(limits = VaultImportLimits(maxPathDepth = 2, maxEntryUncompressedBytes = 10, maxTotalUncompressedBytes = 20))
                    .verify(TestArchiveSource(listOf(entry("data/a/b"))))
            assertTrue(depthLimit.issues.any { it.code == VaultVerificationIssueCode.PATH_DEPTH_LIMIT })

            val sizeLimit =
                BagItImportValidator(limits = VaultImportLimits(maxEntryUncompressedBytes = 4, maxTotalUncompressedBytes = 8))
                    .verify(TestArchiveSource(listOf(entry("data/large", bytes = ByteArray(5)))))
            assertTrue(sizeLimit.issues.any { it.code == VaultVerificationIssueCode.ENTRY_SIZE_LIMIT })

            val ratioLimit =
                BagItImportValidator(limits = VaultImportLimits(maxEntryUncompressedBytes = 1000, maxTotalUncompressedBytes = 2000, maxDecompressionRatio = 10.0))
                    .verify(TestArchiveSource(listOf(entry("data/bomb", bytes = ByteArray(100), compressedSize = 1))))
            assertTrue(ratioLimit.issues.any { it.code == VaultVerificationIssueCode.DECOMPRESSION_RATIO_LIMIT })

            val totalLimit =
                BagItImportValidator(limits = VaultImportLimits(maxEntryUncompressedBytes = 10, maxTotalUncompressedBytes = 10))
                    .verify(TestArchiveSource(listOf(entry("data/one", ByteArray(6)), entry("data/two", ByteArray(6)))))
            assertTrue(totalLimit.issues.any { it.code == VaultVerificationIssueCode.TOTAL_SIZE_LIMIT })
        }

    @Test
    fun `unlisted payload is rejected`() =
        runTest {
            val packageSource = BagItPackageBuilder().build(listOf(TestPayload("listed", byteArrayOf(1))))
            val entries = packageSource.asArchiveEntries() + entry("data/unlisted", byteArrayOf(2))
            val result = BagItImportValidator().verify(TestArchiveSource(entries))

            assertTrue(result.issues.any { it.code == VaultVerificationIssueCode.UNLISTED_PAYLOAD && it.path == "data/unlisted" })
        }

    @Test
    fun `manifest paths are validated and tag manifest must cover required tags`() =
        runTest {
            val packageSource = BagItPackageBuilder().build(listOf(TestPayload("listed", byteArrayOf(1))))
            val maliciousManifest = "${"00".repeat(32)}  data/../escape\n".encodeToByteArray()
            val missingTags = "${"00".repeat(32)}  bagit.txt\n".encodeToByteArray()
            val result =
                BagItImportValidator().verify(
                    packageSource.asArchive(
                        mapOf(
                            BagItImportValidator.PAYLOAD_MANIFEST to maliciousManifest,
                            BagItImportValidator.TAG_MANIFEST to missingTags,
                        ),
                    ),
                )

            assertTrue(result.issues.any { it.code == VaultVerificationIssueCode.PATH_TRAVERSAL && it.path == "data/../escape" })
            assertTrue(result.issues.any { it.code == VaultVerificationIssueCode.MISSING_TAG_MANIFEST_ENTRY && it.path == "bag-info.txt" })
            assertTrue(result.issues.any { it.code == VaultVerificationIssueCode.MISSING_TAG_MANIFEST_ENTRY && it.path == "manifest-sha256.txt" })
        }

    @Test
    fun `validator closes rejected entry sources`() =
        runTest {
            val source = TestByteSource(byteArrayOf(1))
            val invalid =
                VaultArchiveEntry(
                    path = "data/../escape",
                    isDirectory = false,
                    compressedSize = 1,
                    uncompressedSize = 1,
                    content = source,
                )

            BagItImportValidator().verify(TestArchiveSource(listOf(invalid)))

            assertTrue(source.closed)
        }

    @Test
    fun `validator closes directory sources and archive`() =
        runTest {
            val directorySource = TestByteSource(byteArrayOf(1))
            val archive =
                TestArchiveSource(
                    listOf(
                        VaultArchiveEntry(
                            path = "data/folder",
                            isDirectory = true,
                            compressedSize = 0,
                            uncompressedSize = 0,
                            content = directorySource,
                        ),
                    ),
                )

            BagItImportValidator().verify(archive)

            assertTrue(directorySource.closed)
            assertTrue(archive.closed)
        }

    @Test
    fun `validator rethrows cancellation and still closes archive`() =
        runTest {
            var closed = false
            val archive =
                object : VaultArchiveSource {
                    override suspend fun nextEntry(): VaultArchiveEntry? = throw CancellationException("cancel verification")

                    override suspend fun close() {
                        closed = true
                    }
                }

            assertFailsWith<CancellationException> { BagItImportValidator().verify(archive) }
            assertTrue(closed)
        }

    private fun entry(
        path: String,
        bytes: ByteArray = byteArrayOf(1),
        compressedSize: Long = bytes.size.toLong(),
    ) = VaultArchiveEntry(
        path = path,
        isDirectory = false,
        compressedSize = compressedSize,
        uncompressedSize = bytes.size.toLong(),
        content = TestByteSource(bytes),
    )
}
