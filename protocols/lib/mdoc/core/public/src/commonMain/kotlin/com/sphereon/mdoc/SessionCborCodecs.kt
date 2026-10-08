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

package com.sphereon.mdoc

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.ReaderAuthentication
import com.sphereon.mdoc.transfer.reader.SessionTranscript

@JsExportCompat
interface SessionEstablishmentCborCodec {
    fun encode(value: SessionEstablishment): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<SessionEstablishment>, IdkError>
}

@JsExportCompat
interface SessionDataCborCodec {
    fun encode(value: SessionData): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<SessionData>, IdkError>
}

@JsExportCompat
interface SessionTranscriptCborCodec {
    fun encode(value: SessionTranscript): IdkResult<ByteArray, IdkError>

    fun encodeItem(value: SessionTranscript): IdkResult<CborEncodedItem<SessionTranscript>, IdkError>

    fun encodeTag24(value: SessionTranscript): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<SessionTranscript>, IdkError>
}

interface HandoverCborCodec {
    fun encode(value: Handover<*, CborItem<*>>): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<Handover<*, CborItem<*>>, IdkError>

    fun decode(item: CborItem<*>): IdkResult<Handover<*, CborItem<*>>, IdkError>
}

@JsExportCompat
interface ReaderAuthenticationCborCodec {
    fun encode(value: ReaderAuthentication): IdkResult<ByteArray, IdkError>

    fun encodeItem(value: ReaderAuthentication): IdkResult<CborEncodedItem<ReaderAuthentication>, IdkError>

    fun encodeTag24(value: ReaderAuthentication): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<ReaderAuthentication>, IdkError>
}
