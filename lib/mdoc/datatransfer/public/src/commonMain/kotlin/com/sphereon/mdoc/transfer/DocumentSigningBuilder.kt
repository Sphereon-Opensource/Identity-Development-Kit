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

package com.sphereon.mdoc.transfer

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Builder for batch document signing with fluent API.
 * Ensures each document is properly associated with its request and key,
 * and automatically binds the session transcript from the TransferManager context.
 *
 * ## Usage
 *
 * ### Simple batch signing:
 * ```kotlin
 * val signedDocs = transferManager.documentsBuilder()
 *     .add(request1, doc1, key1)
 *     .add(request2, doc2, key2)
 *     .signAll()
 * ```
 *
 * ### Build complete response in one step:
 * ```kotlin
 * val response = transferManager.documentsBuilder()
 *     .add(request1, doc1, key1)
 *     .add(request2, doc2, key2)
 *     .buildResponse()
 * ```
 *
 * ### Add pre-constructed requests:
 * ```kotlin
 * val signingRequests = listOf(
 *     DocumentSigningRequest.of(request1, doc1, key1),
 *     DocumentSigningRequest.of(request2, doc2, key2)
 * )
 *
 * val signedDocs = transferManager.documentsBuilder()
 *     .addAll(signingRequests)
 *     .signAll()
 * ```
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentSigningBuilder", exact = true)
interface DocumentSigningBuilder {
    /**
     * Add a document to sign with explicit associations.
     *
     * @param request The DocRequest specifying what to disclose
     * @param document The Document to sign
     * @param deviceKeyInfo The device key (null = derive from MSO)
     * @param deviceNamespaces Device-signed namespaces (usually empty)
     * @return This builder for method chaining
     */
    fun add(
        request: DocRequest,
        document: Document,
        deviceKeyInfo: KeyInfoType<*>? = null,
        deviceNamespaces: DeviceNameSpaces = DeviceNameSpaces(mapOf()),
    ): DocumentSigningBuilder

    /**
     * Add a pre-constructed signing request.
     *
     * @param signingRequest The DocumentSigningRequest with all associations
     * @return This builder for method chaining
     */
    fun add(signingRequest: DocumentSigningRequest): DocumentSigningBuilder

    /**
     * Add multiple signing requests at once.
     *
     * @param signingRequests List of DocumentSigningRequests
     * @return This builder for method chaining
     */
    fun addAll(signingRequests: List<DocumentSigningRequest>): DocumentSigningBuilder

    /**
     * Sign all added documents and return the results.
     * The session transcript is automatically bound from the TransferManager context.
     *
     * @return List of signed documents in the same order as they were added
     * @throws IllegalStateException if session transcript not initialized
     * @throws Exception on signing failure
     */
    suspend fun signAll(): List<Document>

    /**
     * Sign all documents and build a complete DeviceResponse (convenience method).
     * Equivalent to calling `signAll()` and manually building a DeviceResponse.
     *
     * @return A DeviceResponse containing all signed documents
     * @throws IllegalStateException if session transcript not initialized
     * @throws Exception on signing failure
     */
    suspend fun buildResponse(): DeviceResponse
}
