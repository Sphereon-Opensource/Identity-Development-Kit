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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.data.DeviceResponseDocumentErrorAlias
import com.sphereon.mdoc.oid4vp.Oid4vpSignResult
import com.sphereon.util.stringify
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline
import kotlin.native.ObjCName

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceResponseVersion", exact = true)
value class DeviceResponseVersion(
    private val value: String,
) {
    init {
        require(value == "1.0") { "Version must be '1.0' but was '$value' instead" }
    }

    override fun toString(): String = value
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceResponseStatus", exact = true)
value class DeviceResponseStatus(
    val value: UInt,
) {
    init {
        require(value == 0u || value == 11u || value == 12u || value == 20u) {
            "Status code must be 0, 11, 12 or 20 unsigned but was '$value' instead"
        }
    }

    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentError", exact = true)
value class DocumentError(
    val errorCode: Int,
) {
    override fun toString(): String = errorCode.toString()
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
    val original: ByteArray?,
) {
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    class Builder(
        var documents: Array<Document> = arrayOf(),
        var documentErrors: Array<DeviceResponseDocumentErrorAlias> = arrayOf(),
        var status: DeviceResponseStatus = DeviceResponseStatus(0u),
    ) {
        fun withDocuments(documents: Array<Document>) = apply { this.documents = documents }

        fun addDocument(document: Document) = apply { this.documents.plus(document) }

        fun addDocumentOrErrorFromOid4vpSignResult(signResult: Oid4vpSignResult) =
            apply {
                if (signResult.documentError !== null) {
                    this.addDocumentError(signResult.documentError)
                } else if (signResult.document !== null) {
                    this.addDocument(signResult.document)
                } else {
                    error(
                        "No error or document was returned from the mdoc signing. That should never happen",
                    )
                }
            }

        fun withDocumentErrors(documentErrors: Array<DeviceResponseDocumentErrorAlias>?) = apply { this.documentErrors = documentErrors ?: arrayOf() }

        fun addDocumentError(documentError: DeviceResponseDocumentErrorAlias) = apply { this.documentErrors.plus(documentError) }

        fun withStatus(status: DeviceResponseStatus) = apply { this.status = status }

        fun build(): DeviceResponse =
            DeviceResponse(
                documents = documents,
                documentErrors =
                    if (documentErrors.isNotEmpty()) {
                        documentErrors
                    } else {
                        null
                    },
                version = DeviceResponseVersion("1.0"),
                status = status,
                original = null,
            )
    }

    override fun toString(): String =
        "DeviceResponse(version=$version, documents=${stringify(documents)}, documentErrors=${stringify(documentErrors)}, status=$status, original=${stringify(original)})"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DeviceResponse) {
            return false
        }

        if (version != other.version) {
            return false
        }
        if (documents != null) {
            if (other.documents == null) {
                return false
            }
            if (!documents.contentEquals(other.documents)) {
                return false
            }
        } else if (other.documents != null) {
            return false
        }
        if (documentErrors != null) {
            if (other.documentErrors == null) {
                return false
            }
            if (!documentErrors.contentEquals(other.documentErrors)) {
                return false
            }
        } else if (other.documentErrors != null) {
            return false
        }
        if (status != other.status) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + (documents?.contentHashCode() ?: 0)
        result = 31 * result + (documentErrors?.contentHashCode() ?: 0)
        result = 31 * result + status.hashCode()
        return result
    }

    companion object Decoder {
        @JsStatic
        val VERSION = StringLabel("version")

        @JsStatic
        val DOCUMENTS = StringLabel("documents")

        @JsStatic
        val DOCUMENT_ERRORS = StringLabel("documentErrors")

        @JsStatic
        val STATUS = StringLabel("status")
    }
}
