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

import com.sphereon.compression.zip.ReplayableZipContent
import com.sphereon.compression.zip.StoredZipContent
import com.sphereon.compression.zip.StreamingStoredZipReader
import com.sphereon.compression.zip.StreamingStoredZipWriter
import com.sphereon.compression.zip.StreamingZipLimits
import com.sphereon.compression.zip.ZipByteSink
import com.sphereon.compression.zip.ZipByteSource

data class BagItZipArchiveLimits(
    val maxArchiveBytes: Long = Int.MAX_VALUE.toLong(),
    val maxEntryCount: Int = 65_535,
    val maxEntryBytes: Long = Int.MAX_VALUE.toLong(),
    val maxTotalBytes: Long = Int.MAX_VALUE.toLong(),
    val maxNameBytes: Int = 4096,
) {
    internal fun toZipLimits() =
        StreamingZipLimits(maxArchiveBytes, maxEntryCount, maxEntryBytes, maxTotalBytes, maxNameBytes)
}

/** Common KMP deterministic, streaming, canonical STORED ZIP carrier for BagIt. */
class KmpZipBagItArchiveProvider(
    limits: BagItZipArchiveLimits = BagItZipArchiveLimits(),
) : BagItArchiveProvider {
    override val providerId: String = "kmp-deterministic-stored-zip"
    override val mediaType: String = "application/zip"
    override val deterministic: Boolean = true

    private val zipLimits = limits.toZipLimits()
    private val writer = StreamingStoredZipWriter(zipLimits)

    override fun archive(packageSource: BagItLogicalPackage): VaultByteProducer {
        val producer =
            writer.archive(
                packageSource.entries.map { entry ->
                    StoredZipContent(entry.path, entry.content.asZipContent())
                },
            )
        return VaultByteProducer { sink -> producer.writeTo(sink.asZipSink()) }
    }

    override suspend fun open(source: VaultByteSource): VaultArchiveSource =
        KmpZipArchiveSource(StreamingStoredZipReader(source.asZipSource(), zipLimits))
}

private class KmpZipArchiveSource(
    private val reader: StreamingStoredZipReader,
) : VaultArchiveSource {
    override suspend fun nextEntry(): VaultArchiveEntry? {
        val entry = reader.nextEntry() ?: return null
        return VaultArchiveEntry(
            path = entry.name,
            isDirectory = false,
            compressedSize = entry.compressedSize,
            uncompressedSize = entry.uncompressedSize,
            content = entry.content.asVaultSource(),
        )
    }

    override suspend fun close() = reader.close()
}

private fun ReplayableVaultContent.asZipContent(): ReplayableZipContent =
    object : ReplayableZipContent {
        override val sizeBytes: Long = this@asZipContent.sizeBytes
        override suspend fun open(): ZipByteSource = this@asZipContent.open().asZipSource()
    }

private fun VaultByteSource.asZipSource(): ZipByteSource =
    object : ZipByteSource {
        override suspend fun read(maxBytes: Int): ByteArray? = this@asZipSource.read(maxBytes)
        override suspend fun close() = this@asZipSource.close()
    }

private fun ZipByteSource.asVaultSource(): VaultByteSource =
    object : VaultByteSource {
        override suspend fun read(maxBytes: Int): ByteArray? = this@asVaultSource.read(maxBytes)
        override suspend fun close() = this@asVaultSource.close()
    }

private fun VaultByteSink.asZipSink(): ZipByteSink = ZipByteSink { bytes -> write(bytes) }
