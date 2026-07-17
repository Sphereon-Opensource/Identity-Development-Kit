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

/** Pull-based KMP byte source. Null means EOF; empty chunks are forbidden. */
interface VaultByteSource {
    suspend fun read(maxBytes: Int = DEFAULT_CHUNK_SIZE): ByteArray?

    suspend fun close() {
        // Most in-memory sources have nothing to release. Provider sources override this.
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 64 * 1024
    }
}

/** Push-based KMP byte sink. Implementations must reject writes after [close]. */
interface VaultByteSink {
    suspend fun write(bytes: ByteArray)

    suspend fun close()
}

/** Replayable producer used to connect BagIt/archive/TDF providers without materializing payloads. */
fun interface VaultByteProducer {
    suspend fun writeTo(sink: VaultByteSink)
}

interface ReplayableVaultContent {
    val sizeBytes: Long

    suspend fun open(): VaultByteSource
}

suspend fun VaultByteSource.copyTo(
    sink: VaultByteSink,
    maxBytes: Long = Long.MAX_VALUE,
): Long {
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    var total = 0L
    while (true) {
        val chunk = read() ?: break
        require(chunk.isNotEmpty()) { "VaultByteSource returned an empty non-EOF chunk" }
        total = checkedAdd(total, chunk.size.toLong())
        require(total <= maxBytes) { "Stream exceeded the configured byte limit" }
        sink.write(chunk)
    }
    return total
}

internal fun checkedAdd(
    current: Long,
    increment: Long,
): Long {
    require(increment >= 0 && current <= Long.MAX_VALUE - increment) { "Byte count overflow" }
    return current + increment
}

data class VaultArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val compressedSize: Long?,
    val uncompressedSize: Long?,
    val content: VaultByteSource?,
) {
    init {
        require(compressedSize == null || compressedSize >= 0) { "compressedSize must be non-negative" }
        require(uncompressedSize == null || uncompressedSize >= 0) { "uncompressedSize must be non-negative" }
        require(isDirectory || content != null) { "Non-directory entries require content" }
    }
}

interface VaultArchiveSource {
    suspend fun nextEntry(): VaultArchiveEntry?

    suspend fun close() {
        // Provider archive readers override this when they own file/network resources.
    }
}

/**
 * Standards archive boundary. A production implementation is expected to emit/read deterministic
 * ZIP (or another explicitly approved BagIt carrier); this module does not invent a container.
 */
interface BagItArchiveProvider {
    val providerId: String
    val mediaType: String
    val deterministic: Boolean

    fun archive(packageSource: BagItLogicalPackage): VaultByteProducer

    suspend fun open(source: VaultByteSource): VaultArchiveSource
}
