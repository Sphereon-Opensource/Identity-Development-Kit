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
 * Raw DEFLATE (RFC 1951 — no zlib/gzip framing) primitives. Platform-specific because the
 * standard compression APIs differ per KMP target:
 * - JVM: `java.util.zip.Deflater` / `Inflater` with `nowrap = true`.
 * - JS / wasmJs: WHATWG `CompressionStream` / `DecompressionStream` with format `"deflate-raw"`.
 * - Apple (iOS/macOS) & Linux native: zlib via Kotlin/Native's built-in `platform.zlib`
 *   (`windowBits = -15` selects the raw, header-less DEFLATE stream).
 *
 * SUSPEND because the WHATWG API is async by design; all call sites are already suspend.
 * [rawInflate] throws on input that is not valid DEFLATE.
 */
internal expect suspend fun rawDeflate(data: ByteArray): ByteArray

internal expect suspend fun rawInflate(data: ByteArray): ByteArray
