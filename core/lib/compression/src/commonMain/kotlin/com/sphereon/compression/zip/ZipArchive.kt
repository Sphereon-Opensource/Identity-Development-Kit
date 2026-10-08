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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

const val ZIP_METHOD_STORED = 0
const val ZIP_METHOD_DEFLATE = 8
const val ZIP_METHOD_AES = 99
const val ZIP_FLAG_ENCRYPTED = 0x0001
const val ZIP_FLAG_DATA_DESCRIPTOR = 0x0008
const val ZIP_FLAG_UTF8 = 0x0800

private const val ZIP_LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
private const val ZIP_CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50
private const val ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
private const val ZIP_LOCAL_HEADER_LENGTH = 30
private const val ZIP_CENTRAL_HEADER_LENGTH = 46
private const val ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH = 22
private const val ZIP_MAX_COMMENT_LENGTH = 65_535
private const val ZIP_VERSION_STORED = 20
private const val ZIP_VERSION_AES = 51
private const val AES_EXTRA_FIELD_ID = 0x9901
private const val AES_EXTRA_DATA_LENGTH = 7
private const val AES_VERSION_AE_2 = 2
private const val AES_STRENGTH_256 = 3
private const val UINT32_MASK = 0xffff_ffffL
private const val CRC32_POLYNOMIAL = 0xedb8_8320.toInt()

/** Compatibility model used by existing byte-array ZIP consumers. */
data class ZipEntryRecord(
    val name: String,
    val flags: Int,
    val method: Int,
    val crc32: Int,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val localHeaderOffset: Int,
    val extra: ByteArray,
    val modTime: Int,
    val modDate: Int,
    val externalAttributes: Int,
) {
    val isDirectory: Boolean get() = name.endsWith("/")
    val isEncrypted: Boolean get() = flags and ZIP_FLAG_ENCRYPTED != 0
}

/** Existing common ZIP parser extracted from the protected-license implementation. */
class ZipArchive private constructor(
    private val bytes: ByteArray,
    val entries: List<ZipEntryRecord>,
) {
    fun compressedData(entry: ZipEntryRecord): ByteArray {
        val offset = entry.localHeaderOffset
        require(offset >= 0 && offset + ZIP_LOCAL_HEADER_LENGTH <= bytes.size) { "ZIP local header for entry '${entry.name}' is outside the archive" }
        require(bytes.readInt32LE(offset) == ZIP_LOCAL_FILE_HEADER_SIGNATURE) { "ZIP local header signature is invalid for entry '${entry.name}'" }
        val nameLength = bytes.readUInt16LE(offset + 26)
        val extraLength = bytes.readUInt16LE(offset + 28)
        val dataOffset = offset + ZIP_LOCAL_HEADER_LENGTH + nameLength + extraLength
        val dataEnd = dataOffset + entry.compressedSize
        require(dataOffset >= 0 && dataEnd <= bytes.size) { "ZIP entry '${entry.name}' data is outside the archive" }
        return bytes.copyOfRange(dataOffset, dataEnd)
    }

    companion object {
        fun parse(bytes: ByteArray): ZipArchive {
            val eocdOffset = findEndOfCentralDirectory(bytes)
            val diskNumber = bytes.readUInt16LE(eocdOffset + 4)
            val centralDirectoryDisk = bytes.readUInt16LE(eocdOffset + 6)
            val entriesOnDisk = bytes.readUInt16LE(eocdOffset + 8)
            val totalEntries = bytes.readUInt16LE(eocdOffset + 10)
            val centralDirectorySize = bytes.readUInt32LE(eocdOffset + 12)
            val centralDirectoryOffset = bytes.readUInt32LE(eocdOffset + 16)
            require(diskNumber == 0 && centralDirectoryDisk == 0 && entriesOnDisk == totalEntries) { "Multi-disk ZIP archives are not supported" }
            require(centralDirectorySize != UINT32_MASK && centralDirectoryOffset != UINT32_MASK) { "Zip64 archives are not supported" }
            require(centralDirectoryOffset <= Int.MAX_VALUE && centralDirectorySize <= Int.MAX_VALUE) { "ZIP central directory is too large" }

            var position = centralDirectoryOffset.toInt()
            val centralEnd = position + centralDirectorySize.toInt()
            require(centralEnd <= bytes.size) { "ZIP central directory is outside the archive" }
            val entries = ArrayList<ZipEntryRecord>(totalEntries)
            repeat(totalEntries) {
                require(position + ZIP_CENTRAL_HEADER_LENGTH <= centralEnd) { "ZIP central directory entry is truncated" }
                require(bytes.readInt32LE(position) == ZIP_CENTRAL_FILE_HEADER_SIGNATURE) { "ZIP central directory signature is invalid" }
                val compressedSize = bytes.readUInt32LE(position + 20)
                val uncompressedSize = bytes.readUInt32LE(position + 24)
                val nameLength = bytes.readUInt16LE(position + 28)
                val extraLength = bytes.readUInt16LE(position + 30)
                val commentLength = bytes.readUInt16LE(position + 32)
                val localHeaderOffset = bytes.readUInt32LE(position + 42)
                val nameStart = position + ZIP_CENTRAL_HEADER_LENGTH
                val extraStart = nameStart + nameLength
                val commentStart = extraStart + extraLength
                val next = commentStart + commentLength
                require(compressedSize != UINT32_MASK && uncompressedSize != UINT32_MASK && localHeaderOffset != UINT32_MASK) { "Zip64 entries are not supported" }
                require(compressedSize <= Int.MAX_VALUE && uncompressedSize <= Int.MAX_VALUE && localHeaderOffset <= Int.MAX_VALUE) { "ZIP entry is too large" }
                require(next <= centralEnd) { "ZIP central directory entry is truncated" }
                entries +=
                    ZipEntryRecord(
                        name = bytes.copyOfRange(nameStart, extraStart).decodeToString(throwOnInvalidSequence = true),
                        flags = bytes.readUInt16LE(position + 8),
                        method = bytes.readUInt16LE(position + 10),
                        crc32 = bytes.readInt32LE(position + 16),
                        compressedSize = compressedSize.toInt(),
                        uncompressedSize = uncompressedSize.toInt(),
                        localHeaderOffset = localHeaderOffset.toInt(),
                        extra = bytes.copyOfRange(extraStart, commentStart),
                        modTime = bytes.readUInt16LE(position + 12),
                        modDate = bytes.readUInt16LE(position + 14),
                        externalAttributes = bytes.readInt32LE(position + 38),
                    )
                position = next
            }
            require(position == centralEnd) { "ZIP central directory has trailing data" }
            return ZipArchive(bytes.copyOf(), entries)
        }

        private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
            require(bytes.size >= ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH) { "ZIP archive is too small" }
            val minimum = maxOf(0, bytes.size - ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH - ZIP_MAX_COMMENT_LENGTH)
            for (position in bytes.size - ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH downTo minimum) {
                if (bytes.readInt32LE(position) == ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                    val commentLength = bytes.readUInt16LE(position + 20)
                    if (position + ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH + commentLength == bytes.size) return position
                }
            }
            throw IllegalArgumentException("ZIP end of central directory record was not found")
        }
    }
}

/** Existing common byte-array writer retained for protected-license compatibility. */
class ZipArchiveWriter {
    private val output = ZipByteArrayBuilder()
    private val centralRecords = mutableListOf<ZipCentralRecord>()

    fun addStored(name: String, data: ByteArray, modTime: Int, modDate: Int, externalAttributes: Int) {
        addEntry(name, ZIP_FLAG_UTF8, ZIP_METHOD_STORED, zipCrc32(data), data.size, data.size, ByteArray(0), data, ZIP_VERSION_STORED, modTime, modDate, externalAttributes)
    }

    fun addAes256(
        name: String,
        encryptedData: ByteArray,
        uncompressedSize: Int,
        actualCompressionMethod: Int,
        modTime: Int,
        modDate: Int,
        externalAttributes: Int,
    ) {
        addEntry(
            name,
            ZIP_FLAG_UTF8 or ZIP_FLAG_ENCRYPTED,
            ZIP_METHOD_AES,
            0,
            encryptedData.size,
            uncompressedSize,
            aesExtra(actualCompressionMethod),
            encryptedData,
            ZIP_VERSION_AES,
            modTime,
            modDate,
            externalAttributes,
        )
    }

    private fun addEntry(
        name: String,
        flags: Int,
        method: Int,
        crc32: Int,
        compressedSize: Int,
        uncompressedSize: Int,
        extra: ByteArray,
        data: ByteArray,
        versionNeeded: Int,
        modTime: Int,
        modDate: Int,
        externalAttributes: Int,
    ) {
        require(centralRecords.size < UShort.MAX_VALUE.toInt()) { "ZIP entry count exceeds non-Zip64 limit" }
        require(name.isNotEmpty() && name.encodeToByteArray().size <= UShort.MAX_VALUE.toInt()) { "Invalid ZIP entry name" }
        val nameBytes = name.encodeToByteArray()
        val localOffset = output.size
        output.writeLocalHeader(versionNeeded, flags, method, modTime, modDate, crc32, compressedSize, uncompressedSize, nameBytes, extra)
        output.writeBytes(data)
        centralRecords += ZipCentralRecord(nameBytes, flags, method, crc32, compressedSize, uncompressedSize, extra, versionNeeded, modTime, modDate, externalAttributes, localOffset)
    }

    fun finish(): ByteArray {
        val centralOffset = output.size
        centralRecords.forEach(output::writeCentralRecord)
        val centralSize = output.size - centralOffset
        output.writeEndOfCentralDirectory(centralRecords.size, centralSize, centralOffset)
        return output.toByteArray()
    }

    private fun aesExtra(actualCompressionMethod: Int): ByteArray =
        ZipByteArrayBuilder(11).apply {
            writeShortLE(AES_EXTRA_FIELD_ID)
            writeShortLE(AES_EXTRA_DATA_LENGTH)
            writeShortLE(AES_VERSION_AE_2)
            writeByte('A'.code)
            writeByte('E'.code)
            writeByte(AES_STRENGTH_256)
            writeShortLE(actualCompressionMethod)
        }.toByteArray()
}

interface ZipByteSource {
    suspend fun read(maxBytes: Int = 64 * 1024): ByteArray?
    suspend fun close() = Unit
}

fun interface ZipByteSink {
    suspend fun write(bytes: ByteArray)
}

fun interface ZipByteProducer {
    suspend fun writeTo(sink: ZipByteSink)
}

interface ReplayableZipContent {
    val sizeBytes: Long
    suspend fun open(): ZipByteSource
}

data class StoredZipContent(val name: String, val content: ReplayableZipContent)

data class StreamingZipLimits(
    val maxArchiveBytes: Long = Int.MAX_VALUE.toLong(),
    val maxEntryCount: Int = UShort.MAX_VALUE.toInt(),
    val maxEntryBytes: Long = Int.MAX_VALUE.toLong(),
    val maxTotalBytes: Long = Int.MAX_VALUE.toLong(),
    val maxNameBytes: Int = 4096,
) {
    init {
        require(maxArchiveBytes in 1..Int.MAX_VALUE.toLong() && maxEntryCount > 0 && maxEntryCount <= UShort.MAX_VALUE.toInt())
        require(maxEntryBytes > 0 && maxEntryBytes <= UInt.MAX_VALUE.toLong())
        require(maxTotalBytes >= maxEntryBytes && maxTotalBytes <= Int.MAX_VALUE.toLong())
        require(maxNameBytes in 1..UShort.MAX_VALUE.toInt())
    }
}

/** Deterministic streaming ZIP32 writer for canonical STORED entries only. */
class StreamingStoredZipWriter(private val limits: StreamingZipLimits = StreamingZipLimits()) {
    fun archive(entries: List<StoredZipContent>): ZipByteProducer =
        ZipByteProducer { sink ->
            require(entries.size <= limits.maxEntryCount) { "ZIP entry count exceeds configured limit" }
            val sorted = entries.sortedWith { a, b -> compareUtf8(a.name, b.name) }
            require(sorted.map { it.name }.distinct().size == sorted.size) { "Duplicate ZIP entry name" }
            val output = CountingZipSink(sink, limits.maxArchiveBytes)
            val central = ArrayList<ZipCentralRecord>(sorted.size)
            var total = 0L
            for (entry in sorted) {
                validateSafeRelativeName(entry.name, limits.maxNameBytes)
                require(entry.content.sizeBytes in 0..limits.maxEntryBytes) { "ZIP entry '${entry.name}' exceeds configured size limit" }
                total = checkedAdd(total, entry.content.sizeBytes)
                require(total <= limits.maxTotalBytes) { "ZIP content exceeds configured total-size limit" }
                val (crc, actualSize) = checksum(entry.content, limits.maxEntryBytes)
                require(actualSize == entry.content.sizeBytes) { "ZIP entry '${entry.name}' changed size during checksum" }
                val nameBytes = entry.name.encodeToByteArray()
                val localOffset = output.size
                output.write(localHeader(ZIP_FLAG_UTF8, crc, actualSize, nameBytes))
                var written = 0L
                useZipSource(entry.content.open()) { source ->
                    while (true) {
                        val chunk = source.read() ?: break
                        require(chunk.isNotEmpty()) { "ZIP content source returned an empty non-EOF chunk" }
                        written = checkedAdd(written, chunk.size.toLong())
                        require(written <= actualSize) { "ZIP entry '${entry.name}' exceeded its declared size" }
                        output.write(chunk)
                    }
                }
                require(written == actualSize) { "ZIP entry '${entry.name}' changed between replayable reads" }
                central += ZipCentralRecord(nameBytes, ZIP_FLAG_UTF8, ZIP_METHOD_STORED, crc, actualSize.toInt(), actualSize.toInt(), ByteArray(0), ZIP_VERSION_STORED, 0, STABLE_DOS_DATE, 0, localOffset.toInt())
            }
            val centralOffset = output.size
            central.forEach { output.write(centralHeader(it)) }
            val centralSize = output.size - centralOffset
            output.write(endOfCentralDirectory(central.size, centralSize, centralOffset))
        }

    private suspend fun checksum(content: ReplayableZipContent, maxBytes: Long): Pair<Int, Long> {
        val crc = ZipCrc32()
        var total = 0L
        useZipSource(content.open()) { source ->
            while (true) {
                val chunk = source.read() ?: break
                require(chunk.isNotEmpty()) { "ZIP content source returned an empty non-EOF chunk" }
                total = checkedAdd(total, chunk.size.toLong())
                require(total <= maxBytes) { "ZIP entry exceeds configured size limit" }
                crc.update(chunk)
            }
        }
        return crc.value to total
    }
}

data class StreamingZipEntry(
    val name: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val content: ZipByteSource,
)

/**
 * Sequential canonical STORED ZIP reader. It rejects encryption, data descriptors, compression,
 * extra fields, non-canonical metadata, Zip64, unsafe names, and central-directory disagreement.
 */
class StreamingStoredZipReader(
    source: ZipByteSource,
    private val limits: StreamingZipLimits = StreamingZipLimits(),
) {
    private val input = BufferedZipSource(source, limits.maxArchiveBytes)
    private val localRecords = mutableListOf<ZipCentralRecord>()
    private var current: StoredEntrySource? = null
    private var totalDeclared = 0L
    private var finished = false
    private var previousName: String? = null

    suspend fun nextEntry(): StreamingZipEntry? {
        check(!finished) { "ZIP reader is complete" }
        try {
            current?.close()
            current = null
            val signatureOffset = input.position
            return when (val signature = input.readInt32LE()) {
                ZIP_LOCAL_FILE_HEADER_SIGNATURE -> readLocalEntry(signatureOffset)
                ZIP_CENTRAL_FILE_HEADER_SIGNATURE -> {
                    verifyCentralDirectory(signatureOffset)
                    withContext(NonCancellable) { input.close() }
                    finished = true
                    null
                }
                ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE -> {
                    require(localRecords.isEmpty()) { "ZIP central directory is missing" }
                    verifyEndOfCentralDirectory(0, 0, 0)
                    withContext(NonCancellable) { input.close() }
                    finished = true
                    null
                }
                else -> throw IllegalArgumentException("Unsupported ZIP record signature 0x${signature.toUInt().toString(16)}")
            }
        } catch (error: Throwable) {
            closeUnderlyingAfter(error)
        }
    }

    suspend fun close() {
        var failure: Throwable? = null
        current = null
        try {
            withContext(NonCancellable) { input.close() }
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        finished = true
        failure?.let { throw it }
    }

    private suspend fun readLocalEntry(localOffset: Long): StreamingZipEntry {
        require(localRecords.size < limits.maxEntryCount) { "ZIP entry count exceeds configured limit" }
        val header = input.readExactly(ZIP_LOCAL_HEADER_LENGTH - 4)
        val version = header.readUInt16LE(0)
        val flags = header.readUInt16LE(2)
        val method = header.readUInt16LE(4)
        val modTime = header.readUInt16LE(6)
        val modDate = header.readUInt16LE(8)
        val crc = header.readInt32LE(10)
        val compressed = header.readUInt32LE(14)
        val uncompressed = header.readUInt32LE(18)
        val nameLength = header.readUInt16LE(22)
        val extraLength = header.readUInt16LE(24)
        require(version == ZIP_VERSION_STORED) { "Unsupported ZIP version $version" }
        require(flags and ZIP_FLAG_ENCRYPTED == 0) { "Encrypted ZIP entries are not supported" }
        require(flags and ZIP_FLAG_DATA_DESCRIPTOR == 0) { "ZIP data descriptors are not supported" }
        require(flags == ZIP_FLAG_UTF8) { "Unsupported ZIP flags 0x${flags.toString(16)}" }
        require(method == ZIP_METHOD_STORED) { "Unsupported ZIP compression method $method" }
        require(modTime == 0 && modDate == STABLE_DOS_DATE) { "Non-canonical ZIP entry timestamp" }
        require(compressed == uncompressed) { "Canonical STORED ZIP entry sizes differ" }
        require(uncompressed <= limits.maxEntryBytes) { "ZIP entry exceeds configured size limit" }
        totalDeclared = checkedAdd(totalDeclared, uncompressed)
        require(totalDeclared <= limits.maxTotalBytes) { "ZIP content exceeds configured total-size limit" }
        require(nameLength in 1..limits.maxNameBytes) { "Invalid ZIP entry name length" }
        require(extraLength == 0) { "ZIP extra fields are not supported by the canonical profile" }
        val name = input.readExactly(nameLength).decodeToString(throwOnInvalidSequence = true)
        validateSafeRelativeName(name, limits.maxNameBytes)
        previousName?.let { previous ->
            require(compareUtf8(previous, name) < 0) { "ZIP entries are duplicate or not in canonical UTF-8 order" }
        }
        previousName = name
        val record = ZipCentralRecord(name.encodeToByteArray(), flags, method, crc, compressed.toInt(), uncompressed.toInt(), ByteArray(0), version, modTime, modDate, 0, localOffset.toInt())
        localRecords += record
        val source = StoredEntrySource(input, name, uncompressed, crc)
        current = source
        return StreamingZipEntry(name, compressed, uncompressed, source)
    }

    private suspend fun closeUnderlyingAfter(primary: Throwable): Nothing {
        try {
            withContext(NonCancellable) { input.close() }
        } catch (closeError: Throwable) {
            primary.addSuppressed(closeError)
        }
        finished = true
        throw primary
    }

    private suspend fun verifyCentralDirectory(centralOffset: Long) {
        require(localRecords.isNotEmpty()) { "Unexpected ZIP central directory" }
        var signature = ZIP_CENTRAL_FILE_HEADER_SIGNATURE
        for (expected in localRecords) {
            require(signature == ZIP_CENTRAL_FILE_HEADER_SIGNATURE) { "ZIP central directory entry is missing" }
            val header = input.readExactly(ZIP_CENTRAL_HEADER_LENGTH - 4)
            val madeBy = header.readUInt16LE(0)
            val version = header.readUInt16LE(2)
            val flags = header.readUInt16LE(4)
            val method = header.readUInt16LE(6)
            val modTime = header.readUInt16LE(8)
            val modDate = header.readUInt16LE(10)
            val crc = header.readInt32LE(12)
            val compressed = header.readUInt32LE(16)
            val uncompressed = header.readUInt32LE(20)
            val nameLength = header.readUInt16LE(24)
            val extraLength = header.readUInt16LE(26)
            val commentLength = header.readUInt16LE(28)
            val disk = header.readUInt16LE(30)
            val internalAttributes = header.readUInt16LE(32)
            val externalAttributes = header.readInt32LE(34)
            val localOffset = header.readUInt32LE(38)
            require(madeBy == ZIP_VERSION_STORED && version == ZIP_VERSION_STORED && flags == expected.flags && method == expected.method) { "ZIP central metadata differs from local header" }
            require(modTime == expected.modTime && modDate == expected.modDate && crc == expected.crc32) { "ZIP central integrity metadata differs from local header" }
            require(compressed == expected.compressedSize.toLong() && uncompressed == expected.uncompressedSize.toLong()) { "ZIP central sizes differ from local header" }
            require(extraLength == 0 && commentLength == 0 && disk == 0 && internalAttributes == 0 && externalAttributes == 0) { "Unsupported ZIP central-directory metadata" }
            require(localOffset == expected.localHeaderOffset.toLong()) { "ZIP central local-header offset differs" }
            val name = input.readExactly(nameLength).decodeToString(throwOnInvalidSequence = true)
            require(name == expected.nameBytes.decodeToString()) { "ZIP central entry name differs from local header" }
            signature = input.readInt32LE()
        }
        require(signature == ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE) { "ZIP central directory has unexpected records" }
        val centralSize = input.position - 4 - centralOffset
        verifyEndOfCentralDirectory(localRecords.size, centralSize, centralOffset)
    }

    private suspend fun verifyEndOfCentralDirectory(expectedCount: Int, expectedCentralSize: Long, expectedCentralOffset: Long) {
        val eocd = input.readExactly(ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH - 4)
        require(eocd.readUInt16LE(0) == 0 && eocd.readUInt16LE(2) == 0) { "Multi-disk ZIP archives are not supported" }
        require(eocd.readUInt16LE(4) == expectedCount && eocd.readUInt16LE(6) == expectedCount) { "ZIP entry count differs from central directory" }
        require(eocd.readUInt32LE(8) == expectedCentralSize && eocd.readUInt32LE(12) == expectedCentralOffset) { "ZIP central-directory bounds differ from EOCD" }
        require(eocd.readUInt16LE(16) == 0) { "ZIP comments are not supported by the canonical profile" }
        require(input.readAtMost(1) == null) { "ZIP archive has trailing data" }
    }
}

private class StoredEntrySource(
    private val input: BufferedZipSource,
    private val name: String,
    private var remaining: Long,
    private val expectedCrc: Int,
) : ZipByteSource {
    private val crc = ZipCrc32()
    private var closed = false

    override suspend fun read(maxBytes: Int): ByteArray? {
        require(maxBytes > 0)
        check(!closed) { "ZIP entry source is closed" }
        if (remaining == 0L) {
            require(crc.value == expectedCrc) { "ZIP entry '$name' CRC-32 mismatch" }
            closed = true
            return null
        }
        val bytes = input.readExactly(minOf(remaining, maxBytes.toLong()).toInt())
        remaining -= bytes.size
        crc.update(bytes)
        return bytes
    }

    override suspend fun close() {
        if (closed) return
        while (read() != null) {
            // Drain this bounded STORED entry so the next local header remains aligned.
        }
    }
}

private class BufferedZipSource(private val source: ZipByteSource, private val maxArchiveBytes: Long) {
    private var buffer = ByteArray(0)
    private var offset = 0
    var position: Long = 0
        private set
    private var closed = false

    suspend fun readExactly(count: Int): ByteArray {
        require(count >= 0)
        val result = ByteArray(count)
        var written = 0
        while (written < count) {
            val chunk = readAtMost(count - written) ?: throw IllegalArgumentException("Unexpected end of ZIP archive")
            chunk.copyInto(result, written)
            written += chunk.size
        }
        return result
    }

    suspend fun readInt32LE(): Int = readExactly(4).readInt32LE(0)

    suspend fun readAtMost(maxBytes: Int): ByteArray? {
        require(maxBytes > 0)
        if (offset >= buffer.size) {
            buffer = source.read(maxBytes) ?: return null
            require(buffer.isNotEmpty()) { "ZIP source returned an empty non-EOF chunk" }
            offset = 0
        }
        val count = minOf(maxBytes, buffer.size - offset)
        val result = buffer.copyOfRange(offset, offset + count)
        offset += count
        position = checkedAdd(position, count.toLong())
        require(position <= maxArchiveBytes) { "ZIP archive exceeds configured byte limit" }
        return result
    }

    suspend fun close() {
        if (closed) return
        closed = true
        source.close()
    }
}

private class CountingZipSink(private val sink: ZipByteSink, private val maxBytes: Long) {
    var size: Long = 0
        private set
    suspend fun write(bytes: ByteArray) {
        size = checkedAdd(size, bytes.size.toLong())
        require(size <= maxBytes && size <= Int.MAX_VALUE.toLong()) { "ZIP archive exceeds canonical profile or configured byte limit" }
        sink.write(bytes)
    }
}

private suspend fun <T> useZipSource(
    source: ZipByteSource,
    block: suspend (ZipByteSource) -> T,
): T {
    var failure: Throwable? = null
    try {
        return block(source)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            withContext(NonCancellable) { source.close() }
        } catch (closeError: Throwable) {
            if (failure == null) throw closeError
            failure.addSuppressed(closeError)
        }
    }
}

private class ZipCrc32 {
    private var crc = -1
    fun update(bytes: ByteArray) {
        bytes.forEach { byte ->
            var value = (crc xor (byte.toInt() and 0xff)) and 0xff
            repeat(8) { value = if (value and 1 != 0) (value ushr 1) xor CRC32_POLYNOMIAL else value ushr 1 }
            crc = (crc ushr 8) xor value
        }
    }
    val value: Int get() = crc.inv()
}

private fun zipCrc32(bytes: ByteArray): Int = ZipCrc32().apply { update(bytes) }.value

private data class ZipCentralRecord(
    val nameBytes: ByteArray,
    val flags: Int,
    val method: Int,
    val crc32: Int,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val extra: ByteArray,
    val versionNeeded: Int,
    val modTime: Int,
    val modDate: Int,
    val externalAttributes: Int,
    val localHeaderOffset: Int,
)

private fun localHeader(flags: Int, crc: Int, size: Long, name: ByteArray): ByteArray =
    ZipByteArrayBuilder(ZIP_LOCAL_HEADER_LENGTH + name.size).apply {
        writeLocalHeader(ZIP_VERSION_STORED, flags, ZIP_METHOD_STORED, 0, STABLE_DOS_DATE, crc, size.toInt(), size.toInt(), name, ByteArray(0))
    }.toByteArray()

private fun centralHeader(record: ZipCentralRecord): ByteArray =
    ZipByteArrayBuilder(ZIP_CENTRAL_HEADER_LENGTH + record.nameBytes.size).apply { writeCentralRecord(record) }.toByteArray()

private fun endOfCentralDirectory(count: Int, size: Long, offset: Long): ByteArray =
    ZipByteArrayBuilder(ZIP_END_OF_CENTRAL_DIRECTORY_LENGTH).apply { writeEndOfCentralDirectory(count, size.toInt(), offset.toInt()) }.toByteArray()

private class ZipByteArrayBuilder(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)
    var size: Int = 0
        private set
    fun writeByte(value: Int) { ensure(size + 1); buffer[size++] = value.toByte() }
    fun writeBytes(bytes: ByteArray) { ensure(size + bytes.size); bytes.copyInto(buffer, size); size += bytes.size }
    fun writeShortLE(value: Int) { writeByte(value); writeByte(value ushr 8) }
    fun writeIntLE(value: Int) { writeByte(value); writeByte(value ushr 8); writeByte(value ushr 16); writeByte(value ushr 24) }
    fun writeLocalHeader(version: Int, flags: Int, method: Int, time: Int, date: Int, crc: Int, compressed: Int, uncompressed: Int, name: ByteArray, extra: ByteArray) {
        writeIntLE(ZIP_LOCAL_FILE_HEADER_SIGNATURE); writeShortLE(version); writeShortLE(flags); writeShortLE(method); writeShortLE(time); writeShortLE(date)
        writeIntLE(crc); writeIntLE(compressed); writeIntLE(uncompressed); writeShortLE(name.size); writeShortLE(extra.size); writeBytes(name); writeBytes(extra)
    }
    fun writeCentralRecord(record: ZipCentralRecord) {
        writeIntLE(ZIP_CENTRAL_FILE_HEADER_SIGNATURE); writeShortLE(record.versionNeeded); writeShortLE(record.versionNeeded); writeShortLE(record.flags)
        writeShortLE(record.method); writeShortLE(record.modTime); writeShortLE(record.modDate); writeIntLE(record.crc32); writeIntLE(record.compressedSize)
        writeIntLE(record.uncompressedSize); writeShortLE(record.nameBytes.size); writeShortLE(record.extra.size); writeShortLE(0); writeShortLE(0); writeShortLE(0)
        writeIntLE(record.externalAttributes); writeIntLE(record.localHeaderOffset); writeBytes(record.nameBytes); writeBytes(record.extra)
    }
    fun writeEndOfCentralDirectory(count: Int, centralSize: Int, centralOffset: Int) {
        writeIntLE(ZIP_END_OF_CENTRAL_DIRECTORY_SIGNATURE); writeShortLE(0); writeShortLE(0); writeShortLE(count); writeShortLE(count)
        writeIntLE(centralSize); writeIntLE(centralOffset); writeShortLE(0)
    }
    fun toByteArray(): ByteArray = buffer.copyOf(size)
    private fun ensure(required: Int) { if (required > buffer.size) buffer = buffer.copyOf(maxOf(required, maxOf(32, buffer.size * 2))) }
}

private fun validateSafeRelativeName(name: String, maxNameBytes: Int) {
    val encoded = name.encodeToByteArray()
    require(name.isNotEmpty() && encoded.size <= maxNameBytes && '\u0000' !in name) { "Invalid ZIP entry name" }
    require(!name.startsWith('/') && '\\' !in name && !DRIVE_PREFIX.containsMatchIn(name)) { "Absolute ZIP entry paths are not allowed" }
    require(name.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "ZIP path traversal is not allowed" }
}

private fun compareUtf8(left: String, right: String): Int {
    val a = left.encodeToByteArray(); val b = right.encodeToByteArray(); val common = minOf(a.size, b.size)
    for (index in 0 until common) { val compared = (a[index].toInt() and 0xff).compareTo(b[index].toInt() and 0xff); if (compared != 0) return compared }
    return a.size.compareTo(b.size)
}

private fun checkedAdd(current: Long, increment: Long): Long {
    require(increment >= 0 && current <= Long.MAX_VALUE - increment) { "ZIP byte count overflow" }
    return current + increment
}

private fun ByteArray.readUInt16LE(offset: Int): Int {
    require(offset >= 0 && offset + 2 <= size) { "Unexpected end of ZIP data" }
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}
private fun ByteArray.readInt32LE(offset: Int): Int {
    require(offset >= 0 && offset + 4 <= size) { "Unexpected end of ZIP data" }
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8) or ((this[offset + 2].toInt() and 0xff) shl 16) or ((this[offset + 3].toInt() and 0xff) shl 24)
}
private fun ByteArray.readUInt32LE(offset: Int): Long = readInt32LE(offset).toLong() and UINT32_MASK

private const val STABLE_DOS_DATE = (1 shl 5) or 1
private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
