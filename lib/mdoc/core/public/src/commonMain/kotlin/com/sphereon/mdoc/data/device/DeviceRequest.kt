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
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.util.stringify
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JvmInline
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRequestVersion", exact = true)
value class DeviceRequestVersion(
    private val value: String,
) {
    init {
        require(value == "1.0") { "Version must be '1.0' but was '$value' instead'" }
    }

    override fun toString(): String = value
}

/**
 * 8.3.2.1.2.1 Device retrieval mdoc request
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRequest", exact = true)
data class DeviceRequest(
    /**
     * version is the version for the DeviceRequest structure: in the current version of this document its value
     * shall be “1.0”. If
     */
    val version: DeviceRequestVersion = DeviceRequestVersion("1.0"),
    /**
     * docRequests contains an array of all requested documents.
     *
     * This is optional when an OID4VP request is used; in that case the
     * effective doc requests are derived from the OID4VP payload.
     */
    val docRequests: Array<DocRequest>? = null,
    /**
     * Used in REST API, where the mdoc reader does not know the key types supported by the mdoc beforehand.
     * This provides the capability for the mdoc reader to send multiple keys with different curves which can be used for mdoc mac authentication.
     * This mitigates the issue of the mdoc reader not knowing which key to send, or when the mdoc contains documents with different curves for mdoc mac authentication.
     *
     */
    val macKeys: Array<CoseKey>? = null,
    val oid4vpRequest: Oid4VPPresentationDefinition? = null,
    val original: ByteArray?,
) {
    /**
     * Swift ergonomics: provide a copyWith(...) instead of relying on Kotlin's named args from Swift.
     */
    fun copyWith(
        version: DeviceRequestVersion = this.version,
        docRequests: Array<DocRequest>? = this.docRequests,
        macKeys: Array<CoseKey>? = this.macKeys,
        oid4vpRequest: Oid4VPPresentationDefinition? = this.oid4vpRequest,
        original: ByteArray? = this.original,
    ): DeviceRequest =
        this.copy(
            version = version,
            docRequests = docRequests,
            macKeys = macKeys,
            oid4vpRequest = oid4vpRequest,
            original = original,
        )

    val hasOid4vpRequest: Boolean = oid4vpRequest != null

    fun effectiveDocRequests(): List<DocRequest> {
        val docRequests = docRequests?.toList().orEmpty()
        if (docRequests.isNotEmpty()) {
            return docRequests
        }
        val presentationDefinition = oid4vpRequest
        return if (presentationDefinition != null) {
            listOf(presentationDefinition.toDocRequest())
        } else {
            emptyList()
        }
    }

    val hasDocRequest: Boolean = effectiveDocRequests().isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DeviceRequest) {
            return false
        }

        if (version != other.version) {
            return false
        }
        if (!docRequests.contentEquals(other.docRequests)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + docRequests.hashCode()
        return result
    }

    override fun toString(): String = "DeviceRequest(version=$version, docRequests=${stringify(docRequests)}, original=${stringify(original)})"

    companion object Decoder {
        @JsStatic
        @JvmStatic
        val VERSION = StringLabel("version")

        @JsStatic
        @JvmStatic
        val DOC_REQUESTS = StringLabel("docRequests")

        @JsStatic
        @JvmStatic
        val MAC_KEYS = StringLabel("macKeys")

        @JsStatic
        @JvmStatic
        val OID4VP_REQUEST = StringLabel("oid4vpRequest")
    }
}
