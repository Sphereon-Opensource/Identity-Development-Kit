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

package com.sphereon.trust.etsi.lote.parser

import com.sphereon.trust.etsi.lote.model.LoTE
import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import com.sphereon.trust.etsi.lote.serialization.LoTEXml

/**
 * Default implementation of [LoTEParser] that delegates to [LoTEJson] and [LoTEXml].
 */
class DefaultLoTEParser : LoTEParser {
    override fun parse(data: String): LoTE =
        when (detectFormat(data)) {
            LoTESerializationFormat.JSON -> parseJson(data)
            LoTESerializationFormat.XML -> parseXml(data)
        }

    override fun parse(data: ByteArray): LoTE = parse(data.decodeToString())

    override fun parseJson(jsonString: String): LoTE = LoTEJson.parse(jsonString)

    override fun parseXml(xmlString: String): LoTE = LoTEXml.parse(xmlString)

    override fun encodeJson(lote: LoTE): String = LoTEJson.encode(lote)

    override fun detectFormat(data: String): LoTESerializationFormat {
        val firstNonWhitespace = data.firstOrNull { !it.isWhitespace() }
        return when (firstNonWhitespace) {
            '{', '[' -> LoTESerializationFormat.JSON
            '<' -> LoTESerializationFormat.XML
            else -> throw IllegalArgumentException("Cannot detect LoTE format: unexpected first character '$firstNonWhitespace'")
        }
    }
}
