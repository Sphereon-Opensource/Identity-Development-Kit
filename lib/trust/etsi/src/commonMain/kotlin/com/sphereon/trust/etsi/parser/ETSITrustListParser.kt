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

package com.sphereon.trust.etsi.parser

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.core.compat.JsExportCompat

/**
 * Parser for ETSI TS 119 612 Trust Service Status Lists (TSL).
 *
 * Produces [ETSILoTE] domain objects using ETSI TS 119 602 LoTE terminology.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ETSITrustListParser", exact = true)
interface ETSITrustListParser {
    /**
     * Parses an ETSI TSL from XML data.
     *
     * @param xmlData The XML data as a byte array
     * @return The parsed ETSILoTE
     * @throws ETSIParseException if parsing fails
     */
    fun parseFromBytes(xmlData: ByteArray): ETSILoTE

    /**
     * Parses an ETSI TSL from XML string.
     *
     * @param xmlString The XML data as a string
     * @return The parsed ETSILoTE
     * @throws ETSIParseException if parsing fails
     */
    fun parseFromString(xmlString: String): ETSILoTE

    /**
     * Validates the XML structure against the ETSI TS 119 612 schema.
     *
     * @param xmlData The XML data to validate
     * @return true if valid
     * @throws ETSIParseException if validation fails
     */
    fun validate(xmlData: ByteArray): Boolean

    /**
     * Parses an ETSI trust list from JSON data (ETSI TS 119 602 format).
     *
     * Delegates to the 602 LoTE JSON parser and converts to ETSILoTE.
     *
     * @param jsonString The JSON data as a string
     * @return The parsed ETSILoTE
     * @throws ETSIParseException if parsing fails
     */
    fun parseFromJson(jsonString: String): ETSILoTE
}

/**
 * Exception thrown when ETSI TSL parsing fails.
 */
class ETSIParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
