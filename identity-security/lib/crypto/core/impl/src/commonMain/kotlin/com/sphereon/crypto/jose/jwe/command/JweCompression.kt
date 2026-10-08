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

package com.sphereon.crypto.jose.jwe.command

/**
 * RFC 7516 §4.1.3 JWE compression primitives.
 *
 * The only IANA-registered JWE compression algorithm is `"DEF"` — DEFLATE per RFC 1951
 * (raw DEFLATE without the zlib wrapper from RFC 1950). The `zip` header parameter and the
 * `zip_values_supported` metadata advertise this single algorithm.
 *
 * `deflate` / `inflate` are platform-specific because the standard library's compression
 * primitives differ across KMP targets (java.util.zip on JVM, the WHATWG `CompressionStream`
 * API with `"deflate-raw"` format on JS / wasmJs, `compression_stream_*` with COMPRESSION_ZLIB
 * on Apple, zlib cinterop on Linux native). They are SUSPEND because the WHATWG API is async
 * by design (Promise / ReadableStream); all call sites already live in suspend functions
 * so the contract change is invisible to callers.
 *
 * Throws on input that is not valid DEFLATE — callers should treat that as a malformed JWE.
 */
internal expect suspend fun deflate(plaintext: ByteArray): ByteArray

internal expect suspend fun inflate(compressed: ByteArray): ByteArray
