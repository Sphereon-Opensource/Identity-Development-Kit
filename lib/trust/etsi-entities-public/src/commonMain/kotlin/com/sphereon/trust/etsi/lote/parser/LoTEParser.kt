/*
 * © 2025 Sphereon International B.V.
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

/**
 * Serialization format for LoTE data.
 */
enum class LoTESerializationFormat {
    JSON,
    XML
}

/**
 * Parser for ETSI TS 119 602 LoTE (List of Trusted Entities).
 *
 * Supports both JSON and XML formats with automatic format detection.
 */
interface LoTEParser {
    /**
     * Parses a LoTE from a string, auto-detecting the format.
     */
    fun parse(data: String): LoTE

    /**
     * Parses a LoTE from bytes, auto-detecting the format.
     */
    fun parse(data: ByteArray): LoTE

    /**
     * Parses a LoTE from a JSON string.
     */
    fun parseJson(jsonString: String): LoTE

    /**
     * Parses a LoTE from an XML string.
     */
    fun parseXml(xmlString: String): LoTE

    /**
     * Encodes a LoTE to JSON string.
     */
    fun encodeJson(lote: LoTE): String

    /**
     * Detects the serialization format of the given data.
     */
    fun detectFormat(data: String): LoTESerializationFormat
}
