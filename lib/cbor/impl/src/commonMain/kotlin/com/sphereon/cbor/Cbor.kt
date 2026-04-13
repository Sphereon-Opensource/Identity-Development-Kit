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
 *
 */

package com.sphereon.cbor

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

object Cbor {
    const val BREAK: UByte = CborRuntimeImpl.BREAK

    @JvmStatic
    fun encode(item: CborItem<*>?): ByteArray = CborRuntimeImpl.encode(item)

    @JvmStatic
    fun tryDecode(
        encodedCbor: ByteArray,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT,
    ): IdkResult<CborItem<*>, IdkError> = CborRuntimeImpl.tryDecode(encodedCbor, config)

    @JvmStatic
    @JsName("tryDecodeWithOffset")
    fun tryDecodeWithOffset(
        encodedCbor: ByteArray,
        offset: Int,
        config: CborDecoderConfig = CborDecoderConfig.DEFAULT,
    ): IdkResult<Pair<Int, CborItem<*>>, IdkError> = CborRuntimeImpl.tryDecodeWithOffset(encodedCbor, offset, config)

    @JvmStatic
    @JsName("decodeWithOffset")
    @Deprecated(
        message = "Use tryDecodeWithOffset() which returns IdkResult instead of throwing exceptions",
        replaceWith = ReplaceWith("tryDecodeWithOffset(encodedCbor, offset)"),
    )
    fun decode(
        encodedCbor: ByteArray,
        offset: Int,
    ): Pair<Int, CborItem<*>> = CborRuntimeImpl.decode(encodedCbor, offset)

    @JvmStatic
    @Deprecated(
        message = "Use tryDecode() which returns IdkResult instead of throwing exceptions",
        replaceWith = ReplaceWith("tryDecode(encodedCbor)"),
    )
    fun <T : CborItem<*>> decode(encodedCbor: ByteArray): T = CborRuntimeImpl.decode(encodedCbor)

    @JvmStatic
    fun toDiagnostics(
        item: CborItem<*>,
        options: Set<DiagnosticOption> = emptySet(),
    ): String = CborRuntimeImpl.toDiagnostics(item, options)

    @JvmStatic
    fun toDiagnosticsEncoded(
        encodedItem: ByteArray,
        options: Set<DiagnosticOption> = emptySet(),
    ): String = CborRuntimeImpl.toDiagnosticsEncoded(encodedItem, options)
}
