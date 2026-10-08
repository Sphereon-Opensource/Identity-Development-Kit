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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpZipBagItArchiveProviderTest {
    @Test
    fun `common ZIP carrier is deterministic streaming and BagIt compatible`() =
        runTest {
            val builder = BagItPackageBuilder()
            val first = builder.build(listOf(TestPayload("z.txt", byteArrayOf(2)), TestPayload("a.txt", byteArrayOf(1))))
            val second = builder.build(listOf(TestPayload("a.txt", byteArrayOf(1)), TestPayload("z.txt", byteArrayOf(2))))
            val provider = KmpZipBagItArchiveProvider()

            val firstBytes = provider.archive(first).collect()
            val secondBytes = provider.archive(second).collect()
            assertContentEquals(firstBytes, secondBytes)
            assertTrue(firstBytes.size > first.payloadBytes)

            val result = BagItImportValidator().verify(provider.open(ByteArraySource(firstBytes)))
            assertTrue(result.valid, result.issues.joinToString())
        }

    @Test
    fun `common ZIP import rejects traversal encryption data descriptors and compression`() =
        runTest {
            val provider = KmpZipBagItArchiveProvider()
            val packageSource = BagItPackageBuilder().build(listOf(TestPayload("file.txt", "content".encodeToByteArray())))
            val valid = provider.archive(packageSource).collect()

            val traversal = valid.copyOf()
            "../evil.txtx".encodeToByteArray().copyInto(traversal, destinationOffset = 30)
            assertFailsWith<IllegalArgumentException> { provider.open(ByteArraySource(traversal)).nextEntry() }

            val encrypted = valid.copyOf().also { it[6] = 0x01; it[7] = 0x08 }
            assertFailsWith<IllegalArgumentException> { provider.open(ByteArraySource(encrypted)).nextEntry() }

            val descriptor = valid.copyOf().also { it[6] = 0x08; it[7] = 0x08 }
            assertFailsWith<IllegalArgumentException> { provider.open(ByteArraySource(descriptor)).nextEntry() }

            val compressed = valid.copyOf().also { it[8] = 0x08; it[9] = 0x00 }
            assertFailsWith<IllegalArgumentException> { provider.open(ByteArraySource(compressed)).nextEntry() }
        }

    @Test
    fun `common ZIP import enforces declared bounds and CRC`() =
        runTest {
            val packageSource = BagItPackageBuilder().build(listOf(TestPayload("large.bin", ByteArray(128))))
            val bytes = KmpZipBagItArchiveProvider().archive(packageSource).collect()
            val strict =
                KmpZipBagItArchiveProvider(
                    BagItZipArchiveLimits(
                        maxArchiveBytes = 64 * 1024,
                        maxEntryCount = 20,
                        maxEntryBytes = 64,
                        maxTotalBytes = 1024,
                    ),
                )
            val bounded = BagItImportValidator().verify(strict.open(ByteArraySource(bytes)))
            assertFalse(bounded.valid)
            assertTrue(bounded.issues.any { it.code == VaultVerificationIssueCode.IO_ERROR })

            val tampered = bytes.copyOf()
            val nameLength = readUInt16LE(tampered, 26)
            val firstContentOffset = 30 + nameLength
            tampered[firstContentOffset] = (tampered[firstContentOffset].toInt() xor 1).toByte()
            val crcResult = BagItImportValidator().verify(KmpZipBagItArchiveProvider().open(ByteArraySource(tampered)))
            assertFalse(crcResult.valid)
            assertTrue(crcResult.issues.any { it.code == VaultVerificationIssueCode.IO_ERROR || it.code == VaultVerificationIssueCode.TAG_CHECKSUM_MISMATCH })
        }

    @Test
    fun `common ZIP writer and reader preserve cancellation and close ownership`() =
        runTest {
            val tracking = TrackingContent(ByteArray(128 * 1024))
            val packageSource = BagItPackageBuilder().build(listOf(TrackingPayload("large.bin", tracking)))
            assertFailsWith<CancellationException> {
                KmpZipBagItArchiveProvider().archive(packageSource).writeTo(CancellingSink())
            }
            assertTrue(tracking.closedCount == tracking.openCount)

            val bytes = KmpZipBagItArchiveProvider().archive(packageSource).collect()
            val source = ByteArraySource(bytes)
            val archive = KmpZipBagItArchiveProvider().open(source)
            archive.nextEntry()
            archive.close()
            assertTrue(source.closed)
        }

    private suspend fun VaultByteProducer.collect(): ByteArray {
        val sink = CollectingSink()
        writeTo(sink)
        sink.close()
        return sink.bytes()
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private class ByteArraySource(private val bytes: ByteArray) : VaultByteSource {
        private var offset = 0
        var closed = false
        override suspend fun read(maxBytes: Int): ByteArray? {
            if (offset == bytes.size) return null
            val end = minOf(bytes.size, offset + maxBytes)
            return bytes.copyOfRange(offset, end).also { offset = end }
        }
        override suspend fun close() { closed = true }
    }

    private class CancellingSink : VaultByteSink {
        override suspend fun write(bytes: ByteArray) { throw CancellationException("cancel") }
        override suspend fun close() = Unit
    }

    private class TrackingContent(private val bytes: ByteArray) : ReplayableVaultContent {
        override val sizeBytes = bytes.size.toLong()
        var openCount = 0
        var closedCount = 0
        override suspend fun open(): VaultByteSource {
            openCount++
            return object : VaultByteSource {
                private var offset = 0
                private var closed = false
                override suspend fun read(maxBytes: Int): ByteArray? {
                    if (offset == bytes.size) return null
                    val end = minOf(bytes.size, offset + minOf(maxBytes, 4096))
                    return bytes.copyOfRange(offset, end).also { offset = end }
                }
                override suspend fun close() { if (!closed) { closed = true; closedCount++ } }
            }
        }
    }

    private data class TrackingPayload(
        override val path: String,
        override val content: ReplayableVaultContent,
    ) : BagItPayload {
        override val contentClass = VaultExportContentClass.PORTABLE_DATA
    }
}
