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

import com.sphereon.data.store.vault.VaultDigest
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES

internal class TestReplayableContent(
    bytes: ByteArray,
    private val chunkSize: Int = 3,
) : ReplayableVaultContent {
    private val value = bytes.copyOf()
    override val sizeBytes: Long = value.size.toLong()

    override suspend fun open(): VaultByteSource = TestByteSource(value, chunkSize)
}

internal class TestByteSource(
    bytes: ByteArray,
    private val chunkSize: Int = 3,
) : VaultByteSource {
    private val value = bytes.copyOf()
    private var offset = 0
    var closed: Boolean = false
        private set

    override suspend fun read(maxBytes: Int): ByteArray? {
        check(!closed)
        if (offset == value.size) return null
        val end = minOf(value.size, offset + minOf(chunkSize, maxBytes))
        return value.copyOfRange(offset, end).also { offset = end }
    }

    override suspend fun close() {
        closed = true
    }
}

internal data class TestPayload(
    override val path: String,
    val bytes: ByteArray,
    override val contentClass: VaultExportContentClass = VaultExportContentClass.PORTABLE_DATA,
) : BagItPayload {
    override val content: ReplayableVaultContent = TestReplayableContent(bytes)

    override fun equals(other: Any?): Boolean = other is TestPayload && path == other.path && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
}

internal class TestArchiveSource(
    entries: List<VaultArchiveEntry>,
) : VaultArchiveSource {
    private val iterator = entries.iterator()
    var closed: Boolean = false
        private set

    override suspend fun nextEntry(): VaultArchiveEntry? = if (iterator.hasNext()) iterator.next() else null

    override suspend fun close() {
        closed = true
    }
}

internal suspend fun BagItLogicalPackage.asArchive(
    replacements: Map<String, ByteArray> = emptyMap(),
): VaultArchiveSource = TestArchiveSource(asArchiveEntries(replacements))

internal suspend fun BagItLogicalPackage.asArchiveEntries(
    replacements: Map<String, ByteArray> = emptyMap(),
): List<VaultArchiveEntry> =
    entries.map { entry ->
            val content = replacements[entry.path]?.let(::TestByteSource) ?: entry.content.open()
            VaultArchiveEntry(
                path = entry.path,
                isDirectory = false,
                compressedSize = entry.sizeBytes,
                uncompressedSize = replacements[entry.path]?.size?.toLong() ?: entry.sizeBytes,
                content = content,
            )
        }

internal class CollectingSink : VaultByteSink {
    private val chunks = mutableListOf<ByteArray>()
    private var closed = false

    override suspend fun write(bytes: ByteArray) {
        check(!closed)
        chunks += bytes.copyOf()
    }

    override suspend fun close() {
        closed = true
    }

    fun bytes(): ByteArray {
        val result = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}

internal suspend fun VaultByteSource.readAll(): ByteArray {
    val sink = CollectingSink()
    return try {
        copyTo(sink)
        sink.close()
        sink.bytes()
    } finally {
        close()
    }
}

/** Real AES-256-GCM encryption fixture. It is intentionally test-only and does not claim TDF conformance. */
internal class InMemoryAesGcmTestEnvelopeProvider(
    private val key: ByteArray = ByteArray(32) { (it + 1).toByte() },
) : RecipientScopedTdfEnvelopeProvider {
    override val providerId: String = "test-only-aes-gcm"
    override val capabilities =
        TdfEnvelopeCapabilities(
            conformance = TdfProviderConformance.TEST_ONLY,
            specVersion = "TEST-AES-GCM-1",
            mediaType = "application/vnd.sphereon.test-envelope",
            streamingProtect = false,
            streamingOpen = false,
        )

    private val provider = CryptographyProvider.Default
    private var lastRequest: TdfProtectRequest? = null
    private var lastDescriptor: TdfEnvelopeDescriptor? = null

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun protect(
        request: TdfProtectRequest,
        plaintext: VaultByteProducer,
        output: VaultByteSink,
    ): TdfEnvelopeDescriptor {
        val plainSink = CollectingSink()
        plaintext.writeTo(plainSink)
        plainSink.close()
        val plain = plainSink.bytes()
        val iv = ByteArray(12) { it.toByte() }
        val aad = aad(request)
        val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
        val encrypted = aesKey.cipher(tagSize = 128.bits).encryptWithIv(iv, plain, aad)
        val envelope = iv + encrypted
        output.write(envelope)
        output.close()
        return TdfEnvelopeDescriptor(
            providerId = providerId,
            specVersion = capabilities.specVersion,
            mediaType = capabilities.mediaType,
            recipientRef = request.recipient.recipientRef,
            plaintextDigest = request.plaintextDigest,
            protectedSizeBytes = envelope.size.toLong(),
        ).also {
            lastRequest = request
            lastDescriptor = it
        }
    }

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun open(
        request: TdfOpenRequest,
        envelope: VaultByteSource,
    ): TdfOpenedEnvelope {
        val protectRequest = checkNotNull(lastRequest)
        val descriptor = checkNotNull(lastDescriptor)
        require(request.recipientRef == descriptor.recipientRef)
        val bytes = envelope.readAll()
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key)
        val plain = aesKey.cipher(tagSize = 128.bits).decryptWithIv(iv, encrypted, aad(protectRequest))
        return TdfOpenedEnvelope(descriptor, TestByteSource(plain))
    }

    private fun aad(request: TdfProtectRequest): ByteArray =
        listOf(request.recipient.recipientRef, request.policyBinding, request.plaintextDigest.value).joinToString("\n").encodeToByteArray()
}

internal fun testDigest(value: String = "00".repeat(32)) = VaultDigest("sha-256", value)
