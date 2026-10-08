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

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.transfer.TransferInstance
import com.sphereon.mdoc.transfer.TransferManager
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transport.ConnectionMethod
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
 * Internal interface for engagement instances that support suspension/resumption.
 * This is used by the manager to suspend/resume engagements when switching between
 * concurrent engagement types (QR, NFC, etc).
 */
@JsExportCompat
interface SuspendableEngagement {
    /**
     * Suspends this engagement, preventing it from responding to connection attempts.
     */
    fun suspend()

    /**
     * Resumes this engagement, allowing it to respond to connection attempts again.
     */
    fun resume()
}

/**
 * Represents an instance of an engagement process with associated data, events, and operations.
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("implements", exact = true)
 * This interface implements the `EngagementEvent.Handlers` to allow handling of engagement-related event listeners.
 *
 * ## Usage Guidelines
 *
 * ### For Android/Kotlin Developers:
 * - Use the primary methods (`start()`, `getEngagementUri()`, `getEphemeralKey()`) for straightforward exception-based error handling
 * - Use `tryOps().methodName()` when you prefer `IdkResult<T, IdkError>` for functional error handling
 * - Leverage Kotlin coroutines and the `events` SharedFlow for reactive programming
 *
 * ### For iOS Developers (Swift/Objective-C):
 * - Primary methods throw exceptions that are automatically bridged to Swift errors or NSError in Objective-C
 * - Use `tryOps().methodName()` to get `IdkResult` objects for explicit success/failure handling
 * - The `events` SharedFlow can be observed using Kotlin Multiplatform's flow adapters
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EngagementInstance", exact = true)
@JsExportCompat
interface EngagementInstance : MdocEngagementEvent.Handlers {
    val sessionCoroutineScope: CoroutineScopeScoped

    /**
     * Represents the engagement data associated with the mDoc (Mobile Document) engagement process.
     * This data contains specific details used to facilitate the interaction between the mobile device
     * and the Relying Party.
     */
    val data: EngagementData

    /**
     * A unique identifier for an engagement instance.
     *
     * The UUID for an engagement instance is consistent throughout the lifecycle of that specific instance.
     */
    @OptIn(ExperimentalUuidApi::class)
    val id: Uuid

    var handover: ByteArray?

    /**
     * Starts the engagement process and initializes the transfer manager.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().start()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().start()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().start()`
     *
     * @return The transfer manager, facilitating data transfer during the engagement process.
     * @throws Exception on engagement initialization failure
     */
    suspend fun start(): TransferManager

    /**
     * Retrieves the QR engagement data as a string, typically used for generating a QR code.
     * Used for sharing connection details through a QR code to establish communication between devices.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().getEngagementUri()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().getEngagementUri()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().getEngagementUri()`
     *
     * @return A string representing the data encoded in the QR engagement.
     * @throws Exception on URI generation failure
     */
    suspend fun getEngagementUri(): String

    /**
     * Retrieves the ephemeral key associated with the engagement instance protecting the engagement session.
     * The key is represented in COSE (CBOR Object Signing and Encryption) format. It will be discarded after the instance is destroyed.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().getEphemeralKey()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().getEphemeralKey()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().getEphemeralKey()`
     *
     * @return An instance of CoseKey, representing the ephemeral key in COSE format.
     * @throws Exception on key retrieval failure
     */
    suspend fun getEphemeralKey(): CoseKeyType

    /**
     * Retrieves the device engagement data in the form of a DeviceEngagement object.
     * Normally you would not need this object as it is being used internally in the process, but we do expose it for developers that have specific needs
     *
     * @return a DeviceEngagement containing the engagement data, including version,
     * security details, retrieval methods, protocol information, and additional data. This object is actually being communicated between devices
     */
    fun getDeviceEngagement(): CborEncodedItem<DeviceEngagement>

    fun getReaderEngagement(): ReaderEngagement?

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
     * val result = engagement.tryOps().start()
     * when (result) {
     *     is IdkResult.Success -> println("Started: ${result.value}")
     *     is IdkResult.Failure -> println("Error: ${result.error}")
     * }
     * ```
     *
     * ```swift
     * // Swift
     * let result = engagement.tryOps().start()
     * switch result {
     * case .success(let transferManager):
     *     print("Started: \(transferManager)")
     * case .failure(let error):
     *     print("Error: \(error)")
     * }
     * ```
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Try", exact = true)
    @JsExportIgnoreCompat
    interface Try {
        suspend fun start(): IdkResult<TransferManager, IdkErrorType>

        suspend fun getEngagementUri(): IdkResult<String, IdkErrorType>

        suspend fun getEphemeralKey(): IdkResult<CoseKeyType, IdkErrorType>
    }

    fun tryOps(): Try

    /**
     * The transfer instance associated with this engagement.
     *
     * This property provides access to the TransferInstance which manages the actual data transfer
     * after the engagement has been established and started.
     *
     * **Important:** You must call [start] before accessing this property, otherwise it will throw
     * an IllegalStateException. Use [isTransferInitialized] to check if the transfer is ready.
     *
     * ## Usage
     * ```kotlin
     * val engagement = manager.createEngagement { qr { } }.value
     * engagement.start()  // Initialize transfer
     *
     * // Now safe to access:
     * val transferInstance = engagement.transferInstance
     * transferInstance.events.collect { event -> ... }
     * ```
     *
     * @throws IllegalStateException if called before [start]
     * @see isTransferInitialized
     * @see start
     */
    val transferInstance: TransferInstance

    /**
     * Represents a stream of engagement events that can be observed over time.
     * Each emitted event is an instance of [MdocEngagementEvent], providing details about the current state or action
     * of the engagement process, such as initialization, connection, error, or data transfer.
     *
     * This flow allows subscribers to react to engagement-related events as they occur, enabling integration
     * with the various stages of the engagement lifecycle.
     *
     * **Platform Usage:**
     * - **Android/Kotlin:** Use directly with `collect {}` or other Flow operators
     * - **iOS:** Use Kotlin Multiplatform's flow adapters to convert to Combine Publishers or async sequences
     *
     * This is a hot SharedFlow with replay=1, ensuring late subscribers receive the most recent event.
     */
    val events: SharedFlow<MdocEngagementEvent>

    /**
     * Indicates whether this engagement instance is currently active.
     *
     * When an engagement is suspended (isActive = false), it will not respond to connection attempts.
     * This allows multiple engagements to coexist without interfering with each other.
     *
     * - When `true`: The engagement will respond to connection attempts normally
     * - When `false`: The engagement is suspended and will ignore connection attempts
     *
     * The manager sets this property based on which engagement is currently active.
     * Only one engagement should be active at a time.
     */
    val isActive: StateFlow<Boolean>

    /**
     * Retrieves the current state of the engagement instance.
     *
     * @return the current engagement state represented by an implementation of [MdocEngagementStateType]
     */
    fun getCurrentState(): MdocEngagementStateType

    /**
     * Retrieves the set of retrieval methods available for the engagement instance.
     *
     * @return A set of objects implementing the DeviceRetrievalMethod interface, representing the available retrieval methods.
     */
    fun getRetrievalMethods(): Set<DeviceRetrievalMethod>

    fun getConnectionMethods(supportedOnly: Boolean = true): Set<ConnectionMethod>

    /**
     * Retrieves the set of engagement methods associated with the current instance.
     *
     * @return A set of supported engagement methods represented by `MdocEngagementMethod`.
     */
    fun getEngagementMethods(): Set<MdocEngagementMethod>

    fun isTransferInitialized(): Boolean

    /**
     * Explicitly close the engagement instance. Safe to call multiple times.
     *
     * **Important:** Always call this method when done with the engagement instance to properly clean up resources.
     * This is especially important for iOS developers to prevent memory leaks.
     */
    fun close()

    @JsExportIgnoreCompat
    companion object Log {
        fun id(
            engagementInstance: EngagementInstance,
            length: Int = 256,
        ): String = engagementInstance.id.toString().take(length)

        fun short(engagementInstance: EngagementInstance): String = id(engagementInstance, 8)
    }
}
