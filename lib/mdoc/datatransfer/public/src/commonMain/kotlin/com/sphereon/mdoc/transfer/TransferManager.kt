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

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.mdoc.engagement.EngagementInstance
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.OutgoingDataChannel
import kotlin.native.ObjCName

/**
 * Interface representing a manager for handling transfer operations.
 * It provides access to the associated engagement instance, transfer instance,
 * and data channel retrieval service.
 *
 * ## Usage Guidelines
 *
 * ### For Android/Kotlin Developers:
 * - Use the primary methods (`start()`, `receiveDeviceRequest()`, `sendDeviceResponse()`, `createResponse()`) for straightforward exception-based error handling
 * - Use `tryOps().methodName()` when you prefer `IdkResult<T, IdkError>` for functional error handling
 * - Leverage coroutines for all async operations
 *
 * ### For iOS Developers (Swift/Objective-C):
 * - Primary methods throw exceptions that are automatically bridged to Swift errors or NSError in Objective-C
 * - Use `tryOps().methodName()` to get `IdkResult` objects for explicit success/failure handling
 * - All async methods are compatible with Swift's async/await when using Kotlin Multiplatform
 *
 * ## Method Naming Convention
 * Methods that return concrete types (not `IdkResult`) will throw exceptions on failure.
 * Methods accessed via `tryOps()` return `IdkResult<T, IdkError>` for functional error handling.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransferManager", exact = true)
interface TransferManager : MdocRetrievalEvent.Handlers, RequestResponseProcessor, AutoCloseable {
    /**
     * Represents the engagement instance associated with the transfer manager.
     *
     * The engagement is an `EngagementInstance` that facilitates and manages the engagement
     * process, including its data, events, and operations. This instance acts as the core
     * facilitator for establishing and managing the interaction between the parties involved
     * during mDoc (Mobile Document) data exchange.
     *
     */
    val engagement: EngagementInstance

    /**
     * Represents the transfer instance associated with the current engagement.
     * This instance is primarily responsible for managing the lifecycle of data transfer,
     * such as maintaining states and transmission types during a specific engagement.
     *
     * The instance facilitates communication via a defined transmission type,
     * supports monitoring of state transitions, and allows interaction with data transfer logic.
     */
    val instance: TransferInstance

    /**
     * Represents the incoming data channel responsible for managing
     * the communication channel for data retrieval in a mobile document transfer system.
     *
     * This service facilitates the exchange of byte data, handling
     * operations like receiving raw data and processing specific CBOR-encoded messages
     * such as session establishments and device responses.
     *
     * The channel type is from the common transport-core module, allowing different
     * transport implementations (BLE, NFC, REST API) to provide their own channel implementations.
     *
     * Note:
     * - The implementation employs a bounded channel for internal data flow.
     * - Handles chunked data processing and maintains consistency between data fragments.
     */
    val incomingDataChannel: IncomingDataChannel

    /**
     * Represents the outgoing data channel for sending data during mdoc transfer.
     *
     * The channel type is from the common transport-core module, allowing different
     * transport implementations (BLE, NFC, REST API) to provide their own channel implementations.
     */
    val outgoingDataChannel: OutgoingDataChannel

    /**
     * Suspend until data channels are initialized.
     * This should be called before accessing channels to ensure they're ready.
     */
    suspend fun awaitChannels()

    /**
     * Starts the transfer manager and initializes the transfer instance.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().start()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().start()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().start()`
     *
     * @return The transfer manager itself for method chaining.
     * @throws Exception on transfer instance initialization failure
     */
    suspend fun start(): TransferManager

    /**
     * Receives a device request from the associated data channel. The device request is a CBOR-based structure
     * representing a request for documents or other related data in the context of a data transfer instance.
     *
     * This method is primarily utilized to handle incoming device requests during the data transfer process.
     * It operates within the context of an engagement instance and retrieves structured request sent by the mdoc reader.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().receiveDeviceRequest()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().receiveDeviceRequest()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().receiveDeviceRequest()`
     *
     * @return the received `DeviceRequest` object encapsulating the details of the device request.
     * @throws Exception on device request reception or decoding failure
     */
    suspend fun receiveDeviceRequest(): DeviceRequest

    /**
     * Sends a device response message encapsulated as `DeviceResponse` through the appropriate transfer channel.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().sendDeviceResponse()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().sendDeviceResponse()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().sendDeviceResponse()`
     *
     * @param deviceResponse An instance of `DeviceResponse` containing the response message to be sent.
     *                       This includes the version, documents, document errors, and status of the response.
     * @return The number of bytes sent.
     * @throws Exception on device response sending failure
     */
    suspend fun sendDeviceResponse(deviceResponse: DeviceResponse): Int

    /**
     * Creates a device response based on the given device request. Throws exception on failure.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().createResponse()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().createResponse()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().createResponse()`
     *
     * @param deviceRequest The device request represented as a `DeviceRequest` object.
     * This input contains data such as version, document requests, and optional OID4VP request.
     * @param documentProvider Optional document provider for retrieving documents.
     * @return A `DeviceResponse` object containing the response.
     * @throws Exception on device response creation failure
     */
    suspend fun createResponse(deviceRequest: DeviceRequest, documentProvider: DocumentProvider? = null): DeviceResponse

    /**
     * Provides access to safe, non-throwing variants of key operations that return `IdkResult<T, IdkError>` instead of throwing exceptions.
     *
     * **Recommended for:**
     * - Functional programming approaches
     * - iOS developers who prefer explicit success/failure handling over exception catching
     * - Android developers using Result-based error handling patterns
     *
     * **Example Usage:**
     * ```kotlin
     * // Kotlin
     * val result = transferManager.tryOps().start()
     * when (result) {
     *     is IdkResult.Success -> println("Started: ${result.value}")
     *     is IdkResult.Failure -> println("Error: ${result.error}")
     * }
     * ```
     *
     * ```swift
     * // Swift
     * let result = transferManager.tryOps().start()
     * switch result {
     * case .success(let manager):
     *     print("Started: \(manager)")
     * case .failure(let error):
     *     print("Error: \(error)")
     * }
     * ```
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Try", exact = true)
    interface Try {
        suspend fun start(): IdkResult<TransferManager, IdkErrorType>
        suspend fun receiveDeviceRequest(): IdkResult<DeviceRequest, IdkErrorType>
        suspend fun sendDeviceResponse(deviceResponse: DeviceResponse): IdkResult<Int, IdkErrorType>
        suspend fun createResponse(deviceRequest: DeviceRequest, documentProvider: DocumentProvider? = null): IdkResult<DeviceResponse, IdkErrorType>
    }

    fun tryOps(): Try

    fun registerCustomResponseSelectors(
        requestResponseProcesser: RequestResponseProcessor?,
        requestDocumentsSelector: RequestDocumentsSelector,
        docRequestSingleDocumentSelector: DocumentRequestSingleDocumentSelector,
    )

    fun getSessionTranscript(): CborEncodedItem<SessionTranscript>

    suspend fun validateReaderAuthentication(docRequest: DocRequest, requireReaderAuthentication: Boolean = false): VerifySignatureResultType<CoseKeyType>

    /**
     * Signs a single document with the session transcript automatically bound from the TransferManager context.
     * This eliminates the need for manual DeviceAuthentication construction and transcript management.
     *
     * The session transcript is cryptographically bound to the signature, preventing replay attacks
     * and ensuring the signature is only valid for this specific transfer instance.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().signDocument()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().signDocument()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().signDocument()`
     *
     * ## Usage
     *
     * ```kotlin
     * val signedDocument = transferManager.signDocument(
     *     request = docRequest,
     *     document = document,
     *     deviceKeyInfo = keyInfo  // or null to derive from MSO
     * )
     * ```
     *
     * @param request The DocRequest specifying what data elements to disclose
     * @param document The Document to sign (contains IssuerSigned data + MSO)
     * @param deviceKeyInfo The device key for signing (null = derive from MSO's deviceKeyInfo)
     * @param deviceNamespaces Device-signed namespaces (usually empty for mDL)
     * @param unprotectedHeader Optional COSE unprotected header
     * @param protectedHeader Optional COSE protected header
     * @param requireDeviceX5Chain Whether to require x5chain in device signature
     * @return The signed Document with DeviceSigned data and signature
     * @throws IllegalStateException if session transcript not initialized
     * @throws IllegalArgumentException if request docType doesn't match document docType
     * @throws Exception on signing failure
     */
    suspend fun signDocument(
        request: DocRequest,
        document: Document,
        deviceKeyInfo: KeyInfoType<*>? = null,
        deviceNamespaces: DeviceNameSpaces = DeviceNameSpaces(mapOf()),
        unprotectedHeader: CoseHeaderCbor? = null,
        protectedHeader: CoseHeaderCbor? = null,
        requireDeviceX5Chain: Boolean = false
    ): Document

    /**
     * Signs multiple documents with the session transcript automatically bound.
     * Each document is signed with its explicitly associated request and key,
     * preventing errors from parallel list management.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().signDocuments()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().signDocuments()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().signDocuments()`
     *
     * ## Usage
     *
     * ```kotlin
     * val signingRequests = listOf(
     *     DocumentSigningRequest.of(request1, doc1, key1),
     *     DocumentSigningRequest.of(request2, doc2, key2)
     * )
     * val signedDocs = transferManager.signDocuments(signingRequests)
     * ```
     *
     * @param signingRequests List of explicit request-document-key associations
     * @return List of signed documents in the same order as input
     * @throws IllegalStateException if session transcript not initialized
     * @throws Exception on signing failure
     */
    suspend fun signDocuments(
        signingRequests: List<DocumentSigningRequest>
    ): List<Document>

    /**
     * Creates a builder for batch document signing with fluent API.
     * The builder provides an ergonomic way to add multiple documents and
     * sign them all at once with automatic transcript binding.
     *
     * ## Usage
     *
     * ```kotlin
     * val response = transferManager.documentsBuilder()
     *     .add(request1, doc1, key1)
     *     .add(request2, doc2, key2)
     *     .buildResponse()
     * ```
     *
     * @return A new DocumentSigningBuilder instance
     */
    fun documentsBuilder(): DocumentSigningBuilder
    val isClosed: Boolean

}
