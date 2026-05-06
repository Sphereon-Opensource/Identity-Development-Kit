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
 * Linux native target — DEFLATE not yet wired here. The natural primitive is `zlib` via
 * cinterop (`deflateInit2(..., -MAX_WBITS, ...)` to suppress the zlib header and produce
 * RFC 1951 raw DEFLATE). When a Linux native deployment needs JWE compression, add a
 * cinterop def for `zlib` and replace the throws below.
 *
 * Until then `deflate` / `inflate` throw, and `JweCommandErrorPathsTest`'s round-trip test
 * skips on this target via its UnsupportedOperationException probe.
 */
internal actual suspend fun deflate(plaintext: ByteArray): ByteArray = throw UnsupportedOperationException("DEFLATE not implemented for linuxX64 target")

internal actual suspend fun inflate(compressed: ByteArray): ByteArray = throw UnsupportedOperationException("INFLATE not implemented for linuxX64 target")
