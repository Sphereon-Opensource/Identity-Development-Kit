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
 * Apple targets (iOS / macOS / etc.) — DEFLATE not yet wired here. The right Apple primitive
 * is `compression_stream_*` from `<compression.h>` with `COMPRESSION_ZLIB` (which is raw
 * DEFLATE / RFC 1951 despite the name). When a wallet flow on iOS needs JWE compression,
 * implement against that and replace the throws below.
 */
internal actual suspend fun deflate(plaintext: ByteArray): ByteArray = throw UnsupportedOperationException("DEFLATE not implemented for Apple targets")

internal actual suspend fun inflate(compressed: ByteArray): ByteArray = throw UnsupportedOperationException("INFLATE not implemented for Apple targets")
