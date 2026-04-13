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

package com.sphereon.mdoc.experimental.oid4vp

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.json.JsonView
import com.sphereon.cbor.toStringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.json.mdocJsonSerializer
import com.sphereon.mdoc.oid4vp.Oid4VPFormat
import com.sphereon.mdoc.oid4vp.Oid4VPFormatIdentifier
import com.sphereon.mdoc.oid4vp.Oid4VPSupportedAlgorithm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

const val OID4VP_PROTOCOL_INFO_LITERAL = "oid4vp"
val OID4VP_PROTOCOL_INFO_LABEL = OID4VP_PROTOCOL_INFO_LITERAL.toStringLabel()

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialFormatJson", exact = true)
data class CredentialFormatJson(
    val alg: Array<String>,
) : JsonView() {
    override fun toCbor(): Any = CredentialFormat(CborArray(alg.map { CborString(it) }.toMutableList()))

    override fun toJsonString() = mdocJsonSerializer.encodeToString(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is CredentialFormatJson) {
            return false
        }

        if (!alg.contentEquals(other.alg)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int = alg.contentHashCode()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CredentialFormat", exact = true)
data class CredentialFormat(
    val alg: CborArray<CborString>,
) {
    companion object {
        @JsStatic
        val ALG = StringLabel("alg")
    }
}

/*
 * SPHEREON Funke: Experimental credential format extension
 *
 * This file contains some data structures that are an extension to the ISO 18013-5 and -7 specs. They are not official and highly experimental!
 * Obviously there are other areas of the code base impacted by this extension as well. The code paths/extensions are labeled with the above identifier
 * The implementation is based on https://docs.google.com/document/d/1kRrs1fxufY1wXz-WDLkqy3i7jE7Kd8nY/edit
 *
 * It adds support for OID4VP directly in DeviceEngagement, Device Request and Response, allowing different credential formats like SD-JWT next to mdl/mdocs
 */

/**
 * Represents a CBOR encoded OID4VP request protocol.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("facilitates", exact = true)
 * This class facilitates the conversion between CBOR and JSON representations
 * of the OID4VP request protocol, specifically handling the "credentialFormat".
 *
 * @property format Array of CBOR strings representing the credential format.
 */
@JsExportCompat
data class Oid4vpRequestProtocol(
    val format: MutableMap<CborString, CredentialFormat>,
) {
    fun getFormatIdentifiers() = format.map { Oid4VPFormatIdentifier.fromValue(it.key.value)!! }.toSet().toTypedArray()

    fun hasFormatIdentifier(identifier: Oid4VPFormatIdentifier) = getFormatIdentifiers().contains(identifier)

    fun getSupportedAlgorithms(identifier: Oid4VPFormatIdentifier): Oid4VPSupportedAlgorithm? {
        val algs = format[CborString(identifier.value)]?.alg?.value?.map { it.value } ?: return null
        if (algs.isEmpty()) {
            return null
        }
        return Oid4VPSupportedAlgorithm(algs.toTypedArray())
    }

    fun getFormatsAndAlgorithms(): Map<Oid4VPFormatIdentifier, Oid4VPSupportedAlgorithm> = getFormatIdentifiers().associateWith { getSupportedAlgorithms(it)!! }

    fun toOid4vpCredentialFormat(): Oid4VPFormat {
        val algsPerFormat = getFormatIdentifiers()
        val json = Json.encodeToJsonElement(algsPerFormat).jsonObject
        return Json.decodeFromJsonElement(json)
    }

    fun hasCredentialFormat(format: Oid4VPFormat) { // No-op
    }

    /*
     * Constructs a CBOR (Concise Binary Object Representation) map builder for the current instance.
     *
     * This method initializes a [CborMap] builder with the current instance and populates it with
     * the `credentialFormat` associated with the `CREDENTIAL_FORMAT` key, without overwriting
     * any existing entries.
     *
     * @return The final [CborMap] after adding the required entries and calling [MapBuilder.end].
     */

    /**
     * Object that holds static definitions for commonly used constants.
     */
    companion object {
        /**
         * Represents the format for credentials used in the OID4VP request protocol.
         *
         * This label is a key used in CborMap to denote the credential format and is utilized in serializing/
         * deserializing between CBOR and JSON representations.
         */
        @JsStatic
        val CREDENTIAL_FORMAT = StringLabel("credentialFormat")
    }
}
