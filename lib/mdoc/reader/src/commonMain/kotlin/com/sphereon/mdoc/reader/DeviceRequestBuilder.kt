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

package com.sphereon.mdoc.reader

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace

/**
 * Builder for creating DeviceRequest easily.
 *
 * This builder simplifies creating ISO 18013-5 DeviceRequest structures
 * for requesting data from mDL holders.
 *
 * ## Usage Example:
 * ```kotlin
 * val request = DeviceRequestBuilder()
 *     .withVersion("1.0")
 *     .addDocRequest(
 *         docType = "org.iso.18013.5.1.mDL",
 *         nameSpaces = mapOf(
 *             "org.iso.18013.5.1" to mapOf(
 *                 "family_name" to false,
 *                 "given_name" to false,
 *                 "birth_date" to false
 *             )
 *         )
 *     )
 *     .build()
 * ```
 *
 * ## Multiple Documents
 * You can request multiple document types in a single request:
 * ```kotlin
 * val request = DeviceRequestBuilder()
 *     .addDocRequest(docType = "org.iso.18013.5.1.mDL", ...)
 *     .addDocRequest(docType = "eu.europa.ec.eudiw.pid.1", ...)
 *     .build()
 * ```
 *
 * ## Intent to Retain
 * The `intentToRetain` boolean per data element indicates whether
 * the reader intends to retain the data beyond the session:
 * - `true` = Reader intends to store the data
 * - `false` = Reader only uses data in-session, doesn't store
 *
 * Per ISO 18013-5:2021 §8.3.2.1.2.2
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRequestBuilder", exact = true)
class DeviceRequestBuilder {
    private var version: String = "1.0"
    private val docRequests = mutableListOf<DocRequest>()

    /**
     * Set the DeviceRequest version.
     *
     * Default: "1.0"
     *
     * @param version The version string
     * @return This builder for chaining
     */
    fun withVersion(version: String) = apply { this.version = version }

    /**
     * Add a pre-built DocRequest.
     *
     * Use this if you're building the DocRequest manually or
     * have complex requirements not covered by the convenience method.
     *
     * @param docRequest The pre-built DocRequest
     * @return This builder for chaining
     */
    fun addDocRequest(docRequest: DocRequest) = apply {
        docRequests.add(docRequest)
    }

    /**
     * Add a DocRequest with simplified namespace structure.
     *
     * This is a convenience method for the common case of requesting
     * specific data elements from specific namespaces.
     *
     * @param docType The document type (e.g., "org.iso.18013.5.1.mDL")
     * @param nameSpaces Map of namespace -> (elementId -> intentToRetain)
     * @return This builder for chaining
     */
    fun addDocRequest(
        docType: String,
        nameSpaces: Map<String, Map<String, Boolean>>, // namespace -> (elementId -> intentToRetain)
    ) = apply {
        val builder = DocRequest.Builder()
        val itemsBuilder = builder.docType(DocType(docType))

        // Add each namespace with its elements
        nameSpaces.forEach { (ns, elements) ->
            val nsBuilder = itemsBuilder.nameSpace(NameSpace(ns))
            elements.forEach { (elementId, intentToRetain) ->
                nsBuilder.add(DataElementIdentifier(elementId), IntentToRetain(intentToRetain))
            }
            nsBuilder.end()
        }

        docRequests.add(builder.build())
    }

    /**
     * Build the final DeviceRequest.
     *
     * @return The built DeviceRequest
     * @throws IllegalArgumentException if no DocRequests were added
     */
    fun build(): DeviceRequest {
        require(docRequests.isNotEmpty()) { "At least one DocRequest is required" }
        return DeviceRequest(
            docRequests = docRequests.toTypedArray(),
            original = null
        )
    }
}
