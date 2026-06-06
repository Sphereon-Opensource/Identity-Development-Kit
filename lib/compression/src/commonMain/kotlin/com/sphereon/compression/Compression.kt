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

package com.sphereon.compression

/**
 * Byte-stream compression framings used across the SDK.
 *
 * All three are built on the single platform primitive [rawDeflate] / [rawInflate] (RFC 1951
 * raw DEFLATE). The ZLIB and GZIP wrappers — headers plus Adler-32 / CRC-32 trailers — are
 * computed here in common code so the framing is byte-identical on every target and only the
 * raw DEFLATE codec is platform-specific.
 */
enum class CompressionAlgorithm {
    /** GZIP (RFC 1952): 10-byte header + raw DEFLATE + CRC-32 + ISIZE. W3C Bitstring Status List. */
    GZIP,

    /** ZLIB (RFC 1950): 2-byte header + raw DEFLATE + Adler-32. IETF Token Status List. */
    DEFLATE_ZLIB,

    /** Raw DEFLATE (RFC 1951): no framing. JWE `zip=DEF`. */
    DEFLATE_RAW,
}

/** Compress [data] using [algorithm]. */
suspend fun compress(
    data: ByteArray,
    algorithm: CompressionAlgorithm,
): ByteArray =
    when (algorithm) {
        CompressionAlgorithm.DEFLATE_RAW -> rawDeflate(data)
        CompressionAlgorithm.DEFLATE_ZLIB -> zlibWrap(rawDeflate(data), data)
        CompressionAlgorithm.GZIP -> gzipWrap(rawDeflate(data), data)
    }

/**
 * Decompress [data] using [algorithm]. Throws [IllegalArgumentException] on a malformed stream
 * or a checksum / length mismatch.
 */
suspend fun decompress(
    data: ByteArray,
    algorithm: CompressionAlgorithm,
): ByteArray =
    when (algorithm) {
        CompressionAlgorithm.DEFLATE_RAW -> rawInflate(data)
        CompressionAlgorithm.DEFLATE_ZLIB -> zlibUnwrap(data)
        CompressionAlgorithm.GZIP -> gzipUnwrap(data)
    }

// region ZLIB (RFC 1950)

private fun zlibWrap(
    deflated: ByteArray,
    original: ByteArray,
): ByteArray {
    // CMF=0x78 (CM=8 deflate, CINFO=7 / 32K window), FLG=0x9C (no preset dict, default level,
    // FCHECK so (CMF<<8|FLG) % 31 == 0). Single allocation: 2-byte header + deflated + Adler-32.
    val out = ByteArray(2 + deflated.size + 4)
    out[0] = 0x78
    out[1] = 0x9C.toByte()
    deflated.copyInto(out, 2)
    beU32(adler32(original)).copyInto(out, 2 + deflated.size)
    return out
}

private suspend fun zlibUnwrap(data: ByteArray): ByteArray {
    require(data.size >= 6) { "ZLIB stream too short" }
    val cmf = data[0].toInt() and 0xFF
    val flg = data[1].toInt() and 0xFF
    require((cmf and 0x0F) == 8) { "ZLIB: unexpected compression method" }
    require(((cmf shl 8) or flg) % 31 == 0) { "ZLIB: bad header checksum" }
    var start = 2
    if (flg and 0x20 != 0) start += 4 // FDICT preset-dictionary id
    val inflated = rawInflate(data.copyOfRange(start, data.size - 4))
    require(adler32(inflated) == beU32(data, data.size - 4)) { "ZLIB: Adler-32 mismatch" }
    return inflated
}

// endregion

// region GZIP (RFC 1952)

private fun gzipWrap(
    deflated: ByteArray,
    original: ByteArray,
): ByteArray {
    // ID1 ID2 CM FLG MTIME(4) XFL OS(0xFF unknown). No FEXTRA/FNAME/FCOMMENT/FHCRC. The MTIME/XFL
    // bytes (4..8) stay 0 from the fresh array. Single allocation: 10-byte header + deflated + CRC + ISIZE.
    val out = ByteArray(10 + deflated.size + 8)
    out[0] = 0x1F
    out[1] = 0x8B.toByte()
    out[2] = 0x08
    out[9] = 0xFF.toByte()
    deflated.copyInto(out, 10)
    val trailerOffset = 10 + deflated.size
    leU32(crc32(original)).copyInto(out, trailerOffset)
    leU32(original.size.toLong() and 0xFFFFFFFFL).copyInto(out, trailerOffset + 4)
    return out
}

private suspend fun gzipUnwrap(data: ByteArray): ByteArray {
    require(data.size >= 18) { "GZIP stream too short" }
    require((data[0].toInt() and 0xFF) == 0x1F && (data[1].toInt() and 0xFF) == 0x8B) { "GZIP: bad magic" }
    require((data[2].toInt() and 0xFF) == 0x08) { "GZIP: unsupported compression method" }
    val flg = data[3].toInt() and 0xFF
    var idx = 10
    if (flg and 0x04 != 0) { // FEXTRA
        val xlen = (data[idx].toInt() and 0xFF) or ((data[idx + 1].toInt() and 0xFF) shl 8)
        idx += 2 + xlen
    }
    if (flg and 0x08 != 0) idx = skipCString(data, idx) // FNAME
    if (flg and 0x10 != 0) idx = skipCString(data, idx) // FCOMMENT
    if (flg and 0x02 != 0) idx += 2 // FHCRC
    val inflated = rawInflate(data.copyOfRange(idx, data.size - 8))
    require(crc32(inflated) == leU32(data, data.size - 8)) { "GZIP: CRC-32 mismatch" }
    require((inflated.size.toLong() and 0xFFFFFFFFL) == leU32(data, data.size - 4)) { "GZIP: ISIZE mismatch" }
    return inflated
}

private fun skipCString(
    data: ByteArray,
    start: Int,
): Int {
    var i = start
    while (i < data.size && data[i].toInt() != 0) i++
    return i + 1
}

// endregion

// region little/big-endian uint32 helpers

private fun leU32(v: Long): ByteArray =
    byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte(),
    )

private fun beU32(v: Long): ByteArray =
    byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

private fun leU32(
    d: ByteArray,
    off: Int,
): Long =
    (d[off].toLong() and 0xFF) or
        ((d[off + 1].toLong() and 0xFF) shl 8) or
        ((d[off + 2].toLong() and 0xFF) shl 16) or
        ((d[off + 3].toLong() and 0xFF) shl 24)

private fun beU32(
    d: ByteArray,
    off: Int,
): Long =
    ((d[off].toLong() and 0xFF) shl 24) or
        ((d[off + 1].toLong() and 0xFF) shl 16) or
        ((d[off + 2].toLong() and 0xFF) shl 8) or
        (d[off + 3].toLong() and 0xFF)

// endregion
