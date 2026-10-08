/*
 * Â© 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.cbor

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
interface CborEncoder {
    fun encode(item: CborItem<*>): ByteArray
}

/**
 * Result of decoding a single CBOR item from a byte array at a given offset.
 * Uses a dedicated data class instead of [Pair] so the type survives
 * `@JsExport` on generic result wrappers without losing its fields on JS.
 */
@JsExportCompat
data class CborDecodedItem(
    val offset: Int,
    val item: CborItem<*>,
)

@JsExportCompat
interface CborParser {
    fun parse(
        bytes: ByteArray,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT,
    ): IdkResult<CborItem<*>, IdkError>

    fun parseWithOffset(
        bytes: ByteArray,
        offset: Int,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT,
    ): IdkResult<CborDecodedItem, IdkError>
}

@JsExportCompat
interface CborDiagnostics {
    fun render(
        item: CborItem<*>,
        options: Set<DiagnosticOption> = emptySet(),
    ): String

    fun renderEncoded(
        encodedItem: ByteArray,
        options: Set<DiagnosticOption> = emptySet(),
    ): String
}
