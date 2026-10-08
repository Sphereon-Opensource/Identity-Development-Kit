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

@file:OptIn(ExperimentalForeignApi::class)

package com.sphereon.compression

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DATA_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NEED_DICT
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_STREAM_ERROR
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/**
 * Apple (iOS/macOS) & Linux native raw DEFLATE via Kotlin/Native's built-in `platform.zlib`.
 *
 * `windowBits = -15` selects the raw, header-less DEFLATE stream (RFC 1951) — the same framing
 * the JVM (`Deflater(nowrap=true)`) and JS/Wasm (`CompressionStream("deflate-raw")`) actuals
 * produce, so the common ZLIB/GZIP wrappers in [Compression] frame identically on every target.
 * No cinterop `.def` is needed; `platform.zlib` ships with the Kotlin/Native distribution.
 */
internal actual suspend fun rawDeflate(data: ByteArray): ByteArray = zlibProcess(data, compress = true)

internal actual suspend fun rawInflate(data: ByteArray): ByteArray = zlibProcess(data, compress = false)

private const val RAW_WINDOW_BITS = -15
private const val DEFAULT_MEM_LEVEL = 8
private const val OUT_BUFFER_SIZE = 16384

private fun zlibProcess(
    input: ByteArray,
    compress: Boolean,
): ByteArray =
    memScoped {
        val strm = alloc<z_stream>()
        // The Kotlin/Native zlib bindings expose deflateInit2_/inflateInit2_ with a String?
        // `version` parameter; zlibVersion() returns a C pointer that no longer matches that
        // signature (fails :lib-compression:compileKotlinLinuxX64). zlib only checks the major
        // version, so the compile-time ZLIB_VERSION major ("1") is the correct value to pass.
        val version = "1.2.13"
        val streamSize = sizeOf<z_stream>().toInt()
        val initRc =
            if (compress) {
                deflateInit2_(
                    strm.ptr,
                    Z_DEFAULT_COMPRESSION,
                    Z_DEFLATED,
                    RAW_WINDOW_BITS,
                    DEFAULT_MEM_LEVEL,
                    Z_DEFAULT_STRATEGY,
                    version,
                    streamSize,
                )
            } else {
                inflateInit2_(strm.ptr, RAW_WINDOW_BITS, version, streamSize)
            }
        check(initRc == Z_OK) { "zlib init failed: $initRc" }

        val output = ArrayList<Byte>(maxOf(64, input.size))
        val outBuf = ByteArray(OUT_BUFFER_SIZE)
        try {
            input.usePinned { inPin ->
                outBuf.usePinned { outPin ->
                    strm.next_in = if (input.isEmpty()) null else inPin.addressOf(0).reinterpret()
                    strm.avail_in = input.size.toUInt()
                    while (true) {
                        strm.next_out = outPin.addressOf(0).reinterpret()
                        strm.avail_out = OUT_BUFFER_SIZE.toUInt()
                        val rc = if (compress) deflate(strm.ptr, Z_FINISH) else inflate(strm.ptr, Z_FINISH)
                        if (rc == Z_STREAM_ERROR || rc == Z_DATA_ERROR || rc == Z_NEED_DICT) {
                            throw IllegalArgumentException("zlib ${if (compress) "deflate" else "inflate"} error: $rc")
                        }
                        val produced = OUT_BUFFER_SIZE - strm.avail_out.toInt()
                        for (i in 0 until produced) output.add(outBuf[i])
                        if (rc == Z_STREAM_END) break
                        if (produced == 0 && strm.avail_in.toInt() == 0) {
                            throw IllegalArgumentException("zlib: truncated or incomplete stream")
                        }
                    }
                }
            }
        } finally {
            if (compress) deflateEnd(strm.ptr) else inflateEnd(strm.ptr)
        }
        output.toByteArray()
    }
