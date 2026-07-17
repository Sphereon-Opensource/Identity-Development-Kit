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

package com.sphereon.compression.zip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipArchiveTest {
    @Test
    fun `extracted byte array archive remains compatible`() {
        val writer = ZipArchiveWriter()
        writer.addStored("manifest.json", "{}".encodeToByteArray(), modTime = 0, modDate = 0, externalAttributes = 0)
        val bytes = writer.finish()
        val archive = ZipArchive.parse(bytes)
        assertEquals(listOf("manifest.json"), archive.entries.map { it.name })
        assertContentEquals("{}".encodeToByteArray(), archive.compressedData(archive.entries.single()))
    }

    @Test
    fun `streaming stored archive is deterministic and round trips`() =
        runTest {
            val writer = StreamingStoredZipWriter()
            val entries = listOf(content("b.txt", "b"), content("a.txt", "a"))
            val first = writer.archive(entries).collect()
            val second = writer.archive(entries.reversed()).collect()
            assertContentEquals(first, second)

            val source = ByteArraySource(first)
            val reader = StreamingStoredZipReader(source)
            val names = mutableListOf<String>()
            while (true) {
                val entry = reader.nextEntry() ?: break
                names += entry.name
                assertEquals(entry.name.substringBefore('.'), entry.content.readAll().decodeToString())
            }
            reader.close()
            assertEquals(listOf("a.txt", "b.txt"), names)
            assertEquals(1, source.closeCount, "successful EOCD and explicit close must release the source once")
        }

    @Test
    fun `reader rejects duplicate local names and closes source after drain CRC failure`() =
        runTest {
            val valid = StreamingStoredZipWriter().archive(listOf(content("a", "1"), content("b", "2"))).collect()
            val duplicate = valid.copyOf()
            val secondOffset = nextLocalOffset(duplicate, 0)
            duplicate[secondOffset + 30] = 'a'.code.toByte()
            val duplicateReader = StreamingStoredZipReader(ByteArraySource(duplicate))
            duplicateReader.nextEntry()?.content?.readAll()
            assertFailsWith<IllegalArgumentException> { duplicateReader.nextEntry() }
            duplicateReader.close()

            val corrupt = valid.copyOf()
            corrupt[30 + readUInt16LE(corrupt, 26)] = (corrupt[30 + readUInt16LE(corrupt, 26)].toInt() xor 1).toByte()
            val source = ByteArraySource(corrupt)
            val corruptReader = StreamingStoredZipReader(source)
            corruptReader.nextEntry()
            val error = assertFailsWith<IllegalArgumentException> { corruptReader.nextEntry() }
            assertTrue(error.message.orEmpty().contains("CRC-32"))
            assertTrue(source.closed, "drain failure must close the underlying archive source")
        }

    @Test
    fun `writer preserves primary cancellation when source close also fails`() =
        runTest {
            val content =
                object : ReplayableZipContent {
                    override val sizeBytes = 1L
                    override suspend fun open(): ZipByteSource =
                        object : ZipByteSource {
                            override suspend fun read(maxBytes: Int): ByteArray? = throw CancellationException("primary")
                            override suspend fun close() = throw IllegalStateException("close")
                        }
                }
            val error =
                assertFailsWith<CancellationException> {
                    StreamingStoredZipWriter().archive(listOf(StoredZipContent("a", content))).collect()
                }
            assertEquals("primary", error.message)
            assertTrue(error.suppressedExceptions.any { it.message == "close" })
        }

    @Test
    fun `writer closes an open replay source in non cancellable context`() =
        runTest {
            var opens = 0
            var closes = 0
            val content =
                object : ReplayableZipContent {
                    override val sizeBytes = 1L
                    override suspend fun open(): ZipByteSource {
                        opens++
                        return object : ZipByteSource {
                            private var read = false
                            override suspend fun read(maxBytes: Int): ByteArray? =
                                if (read) null else byteArrayOf(1).also { read = true }

                            override suspend fun close() {
                                currentCoroutineContext().ensureActive()
                                closes++
                            }
                        }
                    }
                }
            var writes = 0
            val cancellingSink =
                ZipByteSink {
                    writes++
                    if (writes == 2) {
                        currentCoroutineContext().cancel(CancellationException("cancel output"))
                        currentCoroutineContext().ensureActive()
                    }
                }
            val job = launch {
                StreamingStoredZipWriter().archive(listOf(StoredZipContent("a", content))).writeTo(cancellingSink)
            }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(2, opens)
            assertEquals(2, closes, "checksum and replay sources must both close")
        }

    @Test
    fun `reader parse failure closes the underlying source`() =
        runTest {
            val bytes = StreamingStoredZipWriter().archive(listOf(content("a", "1"))).collect()
            val encrypted = bytes.copyOf().also { it[6] = 0x01; it[7] = 0x08 }
            val source = ByteArraySource(encrypted)
            val reader = StreamingStoredZipReader(source)
            assertFailsWith<IllegalArgumentException> { reader.nextEntry() }
            assertTrue(source.closed)
        }

    @Test
    fun `entry advance cancellation and early archive close are prompt and close once`() =
        runTest {
            val payload = ByteArray(512 * 1024)
            val archive = StreamingStoredZipWriter().archive(listOf(StoredZipContent("large", ByteArrayContent(payload)))).collect()
            val contentOffset = 30 + readUInt16LE(archive, 26)
            val cancellingSource = CancellingArchiveSource(archive, contentOffset + 64 * 1024)
            val job = launch {
                val reader = StreamingStoredZipReader(cancellingSource)
                reader.nextEntry()
                reader.nextEntry()
            }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(cancellingSource.offset < archive.size, "advance cancellation must not drain the whole entry")
            assertEquals(1, cancellingSource.closeCount)

            val earlySource = ByteArraySource(archive)
            val earlyReader = StreamingStoredZipReader(earlySource)
            earlyReader.nextEntry()
            val consumedBeforeClose = earlySource.offset
            earlyReader.close()
            assertEquals(consumedBeforeClose, earlySource.offset, "archive close must not drain unread entry bytes")
            assertEquals(1, earlySource.closeCount)
        }

    @Test
    fun `canonical streaming profile rejects signed Int overflow limits`() {
        assertFailsWith<IllegalArgumentException> {
            StreamingZipLimits(maxArchiveBytes = Int.MAX_VALUE.toLong() + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            StreamingZipLimits(maxEntryBytes = Int.MAX_VALUE.toLong(), maxTotalBytes = Int.MAX_VALUE.toLong() + 1)
        }
    }

    private fun content(name: String, value: String) =
        StoredZipContent(name, ByteArrayContent(value.encodeToByteArray()))

    private suspend fun ZipByteProducer.collect(): ByteArray {
        val sink = ByteArraySink()
        writeTo(sink)
        return sink.bytes()
    }

    private suspend fun ZipByteSource.readAll(): ByteArray {
        val sink = ByteArraySink()
        while (true) sink.write(read() ?: break)
        close()
        return sink.bytes()
    }

    private fun nextLocalOffset(bytes: ByteArray, offset: Int): Int {
        val compressed = readInt32LE(bytes, offset + 18)
        val nameLength = readUInt16LE(bytes, offset + 26)
        val extraLength = readUInt16LE(bytes, offset + 28)
        return offset + 30 + nameLength + extraLength + compressed
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private class ByteArrayContent(private val value: ByteArray) : ReplayableZipContent {
        override val sizeBytes = value.size.toLong()
        override suspend fun open(): ZipByteSource = ByteArraySource(value)
    }

    private class ByteArraySource(private val value: ByteArray) : ZipByteSource {
        var offset = 0
        var closed = false
        var closeCount = 0
        override suspend fun read(maxBytes: Int): ByteArray? {
            if (offset == value.size) return null
            val end = minOf(value.size, offset + maxBytes)
            return value.copyOfRange(offset, end).also { offset = end }
        }
        override suspend fun close() {
            if (!closed) {
                closed = true
                closeCount++
            }
        }
    }

    private class CancellingArchiveSource(
        private val value: ByteArray,
        private val cancelAt: Int,
    ) : ZipByteSource {
        var offset = 0
        var closeCount = 0
        override suspend fun read(maxBytes: Int): ByteArray? {
            if (offset >= cancelAt) {
                currentCoroutineContext().cancel(CancellationException("cancel drain"))
                currentCoroutineContext().ensureActive()
            }
            if (offset == value.size) return null
            val end = minOf(value.size, offset + maxBytes, cancelAt)
            return value.copyOfRange(offset, end).also { offset = end }
        }

        override suspend fun close() {
            currentCoroutineContext().ensureActive()
            closeCount++
        }
    }

    private class ByteArraySink : ZipByteSink {
        private val chunks = mutableListOf<ByteArray>()
        override suspend fun write(bytes: ByteArray) { chunks += bytes.copyOf() }
        fun bytes(): ByteArray {
            val result = ByteArray(chunks.sumOf { it.size })
            var offset = 0
            chunks.forEach { chunk -> chunk.copyInto(result, offset); offset += chunk.size }
            return result
        }
    }
}
