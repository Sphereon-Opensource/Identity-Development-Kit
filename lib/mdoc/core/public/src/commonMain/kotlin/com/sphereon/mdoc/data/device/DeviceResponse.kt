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

package com.sphereon.mdoc.data.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborInt
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborViewArrayToCborItem
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.toCborUIntFromUint
import com.sphereon.core.api.encodeToHex
import com.sphereon.util.stringify
import com.sphereon.mdoc.data.DeviceResponseDocumentErrorCborAlias
import com.sphereon.mdoc.oid4vp.Oid4vpSignResult
import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceResponseVersion", exact = true)
value class DeviceResponseVersion(private val value: String) : HasToCbor<CborString> {
    init {
        require(value == "1.0") { "Version must be '1.0' but was '$value' instead" }
    }

    override fun toCborStructure(): CborString = CborString(value)

    override fun toString(): String {
        return value
    }

    companion object Decoder : HasFromCbor<CborString, DeviceResponseVersion> {
        override fun fromCborStructure(structure: CborString): DeviceResponseVersion = DeviceResponseVersion(structure.value)
    }
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceResponseStatus", exact = true)
value class DeviceResponseStatus(val value: UInt) : HasToCbor<CborUInt> {
    init {
        if (value != 0u && value != 11u && value != 12u && value != 20u) throw IllegalArgumentException(
            "Status code must be 0, 11, 12 or 20 unsigned but was '$value' instead"
        )
    }

    override fun toCborStructure(): CborUInt = value.toCborUIntFromUint()

    override fun toString(): String = value.toString()

    companion object Decoder : HasFromCbor<CborUInt, DeviceResponseStatus> {
        override fun fromCborStructure(structure: CborUInt): DeviceResponseStatus = DeviceResponseStatus(structure.value.toUInt())
    }
}


@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentError", exact = true)
value class DocumentError(val errorCode: Int) : HasToCbor<CborInt> {
    override fun toString(): String {
        return errorCode.toString()
    }

    override fun toCborStructure(): CborInt = CborInt(errorCode.toLong())

    companion object Decoder : HasFromCbor<CborInt, DocumentError> {
        override fun fromCborStructure(structure: CborInt): DocumentError = DocumentError(structure.value.toInt())
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceResponse", exact = true)
data class DeviceResponse(
    /**
     * version is the version for the DeviceResponse structure. In the current version of this document
     * its value shall be “1.0”.
     *
     */
    val version: DeviceResponseVersion = DeviceResponseVersion("1.0"),

    /**
     * documents contains an array of all returned documents. documentErrors can contain error codes for
     * documents that are not returned. status contains a status code according to 8.3.2.1.2.3.
     */
    val documents: Array<Document>?,

    val documentErrors: Array<Map<DocType, DocumentError>>? = arrayOf(),

    val status: DeviceResponseStatus = DeviceResponseStatus(0u),
    override val original: ByteArray?,

    ) : CborStructure<DeviceResponse, CborMap<StringLabel, CborItem<*>>>(CDDL.map, original = original) {
    override fun cborBuilder(): CborBuilder<DeviceResponse> = cborMapBuilder(this) {
        VERSION to version
        optional(DOCUMENTS, documents?.cborViewArrayToCborItem())
        optional(DOCUMENT_ERRORS, if (documentErrors.isNullOrEmpty()) null else documentErrors)
        STATUS to status
    }


    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, DeviceResponse> {
        @JsStatic
        val VERSION = StringLabel("version")

        @JsStatic
        val DOCUMENTS = StringLabel("documents")

        @JsStatic
        val DOCUMENT_ERRORS = StringLabel("documentErrors")

        @JsStatic
        val STATUS = StringLabel("status")

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): DeviceResponse {
            return DeviceResponse(
                version = DeviceResponseVersion.Decoder.fromCborStructure(VERSION.required(structure)),
                documents = Document.fromDeviceResponse(DOCUMENTS.optional(structure)),
                documentErrors = DOCUMENT_ERRORS.optional<CborArray<CborMap<CborString, CborInt>>>(structure)?.value?.map {
                    it.toDocTypeErrorMap()
                }?.toTypedArray(),
                status = DeviceResponseStatus.Decoder.fromCborStructure(STATUS.required(structure)),
                original = null,
            )
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): DeviceResponse {
            return fromCborStructure(structure).copy(original = original)
        }

        private fun CborMap<CborString, CborInt>.toDocTypeErrorMap(): Map<DocType, DocumentError> {
            return value.map { (key, value) -> DocType.Decoder.fromCborStructure(key) to DocumentError.Decoder.fromCborStructure(value) }.toMap()
        }

        override fun decodeCbor(bytes: ByteArray): DeviceResponse = fromCborStructureWithOriginal(Cbor.decode(bytes), bytes)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    class Builder(
        var documents: Array<Document> = arrayOf(),
        var documentErrors: Array<DeviceResponseDocumentErrorCborAlias> = arrayOf(),
        var status: DeviceResponseStatus = DeviceResponseStatus(0u),
    ) {
        fun withDocuments(documents: Array<Document>) = apply { this.documents = documents }
        fun addDocument(document: Document) = apply { this.documents.plus(document) }
        fun addDocumentOrErrorFromOid4vpSignResult(signResult: Oid4vpSignResult) = apply {
            if (signResult.documentError !== null) this.addDocumentError(signResult.documentError) else if (signResult.document !== null) {
                this.addDocument(signResult.document)
            } else throw IllegalStateException("No error or document was returned from the mdoc signing. That should never happen")
        }

        fun withDocumentErrors(documentErrors: Array<DeviceResponseDocumentErrorCborAlias>?) = apply { this.documentErrors = documentErrors ?: arrayOf() }
        fun addDocumentError(documentError: DeviceResponseDocumentErrorCborAlias) = apply { this.documentErrors.plus(documentError) }
        fun withStatus(status: DeviceResponseStatus) = apply { this.status = status }

        fun build(): DeviceResponse = DeviceResponse(
            documents = documents,
            documentErrors = if (documentErrors.isNotEmpty()) documentErrors else null,
            version = DeviceResponseVersion("1.0"),
            status = status,
            original = null,
        )

    }

    override fun toString(): String {
        return "DeviceResponse(version=$version, documents=${stringify(documents)}, documentErrors=${stringify(documentErrors)}, status=$status)\r\n===\r\n${
            encodeCbor().encodeToHex().chunked(2000).joinToString(separator = "\r\n")
        }\r\n===="
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceResponse) return false

        if (version != other.version) return false
        if (documents != null) {
            if (other.documents == null) return false
            if (!documents.contentEquals(other.documents)) return false
        } else if (other.documents != null) return false
        if (documentErrors != null) {
            if (other.documentErrors == null) return false
            if (!documentErrors.contentEquals(other.documentErrors)) return false
        } else if (other.documentErrors != null) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + (documents?.contentHashCode() ?: 0)
        result = 31 * result + (documentErrors?.contentHashCode() ?: 0)
        result = 31 * result + status.hashCode()
        return result
    }
}
