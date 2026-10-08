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

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CborEncoder>())
class CborEncoderImpl : CborEncoder {
    override fun encode(item: CborItem<*>): ByteArray = CborRuntimeImpl.encode(item)
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CborParser>())
class CborParserImpl : CborParser {
    override fun parse(
        bytes: ByteArray,
        config: CborDecoderConfig,
    ) = CborRuntimeImpl.tryDecode(bytes, config)

    override fun parseWithOffset(
        bytes: ByteArray,
        offset: Int,
        config: CborDecoderConfig,
    ) = CborRuntimeImpl.tryDecodeWithOffset(bytes, offset, config)
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CborDiagnostics>())
class CborDiagnosticsImpl : CborDiagnostics {
    override fun render(
        item: CborItem<*>,
        options: Set<DiagnosticOption>,
    ): String = CborRuntimeImpl.toDiagnostics(item, options)

    override fun renderEncoded(
        encodedItem: ByteArray,
        options: Set<DiagnosticOption>,
    ): String = CborRuntimeImpl.toDiagnosticsEncoded(encodedItem, options)
}
