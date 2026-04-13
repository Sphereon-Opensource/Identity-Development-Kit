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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.transfer.device.DataRetrievalTransmissionType
import com.sphereon.mdoc.transport.ConnectionMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a transfer instance for mDoc data exchange operations.
 *
 * ## Usage Guidelines
 *
 * ### For Android/Kotlin Developers:
 * - Use the primary `start()` method for straightforward exception-based error handling
 * - Use `tryOps().start()` when you prefer `IdkResult<T, IdkError>` for functional error handling
 * - Leverage StateFlow and SharedFlow for reactive programming with instance state and events
 *
 * ### For iOS Developers (Swift/Objective-C):
 * - Primary `start()` method throws exceptions that are automatically bridged to Swift errors or NSError in Objective-C
 * - Use `tryOps().start()` to get `IdkResult` objects for explicit success/failure handling
 * - StateFlow and SharedFlow can be observed using Kotlin Multiplatform's flow adapters
 *
 * ### For JS/WASM/Native:
 * - Full coroutines and Flow support for reactive programming
 * - Exception handling works naturally in each platform's idioms
 * - tryOps() provides functional error handling for platforms preferring explicit success/failure
 *
 * ## Method Naming Convention
 * Methods that return concrete types (not `IdkResult`) will throw exceptions on failure.
 * Methods accessed via `tryOps()` return `IdkResult<T, IdkError>` for functional error handling.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransferInstance", exact = true)
interface TransferInstance : MdocRetrievalEvent.Handlers {
    val engagement: EngagementInstance
    val manager: TransferManager
    val type: DataInstanceType

    @OptIn(ExperimentalUuidApi::class)
    val id: Uuid

    val transmissionTypesSupported: Set<DataRetrievalTransmissionType>
    var transmissionTypeSelected: DataRetrievalTransmissionType?

    /**
     * Stream of retrieval events for reactive programming.
     *
     * **Platform Usage:**
     * - **Android/Kotlin:** Use directly with `collect {}` or other Flow operators
     * - **iOS:** Use Kotlin Multiplatform's flow adapters to convert to Combine Publishers or async sequences
     * - **JS/WASM/Native:** Full Flow support for reactive programming
     */
    val events: SharedFlow<MdocRetrievalEvent>

    /**
     * Stream of instance states for reactive state management.
     */
    val states: Flow<MdocRetrievalStateType>

    fun getCurrentState(): MdocRetrievalStateType

    val connectionMethods: Set<ConnectionMethod>

    var transfer: IMdocTransfer<*>

    /**
     * Starts the transfer instance and returns itself for method chaining.
     *
     * **Error Handling:**
     * - **Android/Kotlin:** Throws exceptions on failure - use try/catch or consider using `tryOps().start()` for `IdkResult`
     * - **iOS (Swift):** Throws Swift errors - use do/try/catch or consider using `tryOps().start()` for explicit result handling
     * - **iOS (Objective-C):** Sets NSError on failure - check error parameter or consider using `tryOps().start()`
     * - **JS/WASM/Native:** Throws exceptions following platform conventions
     *
     * @return The transfer instance itself for method chaining
     * @throws Exception on instance start failure
     */
    suspend fun start(): TransferInstance

    /**
     * Provides access to safe, non-throwing variants that return `IdkResult<T, IdkError>` instead of throwing exceptions.
     *
     * **Recommended for:**
     * - Functional programming approaches
     * - iOS developers who prefer explicit success/failure handling over exception catching
     * - Android developers using Result-based error handling patterns
     * - JS/WASM/Native developers who prefer explicit error handling
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Try", exact = true)
    interface Try {
        suspend fun start(): IdkResult<TransferInstance, IdkErrorType>
    }

    fun tryOps(): Try

    /**
     * Explicitly close the transfer instance. Safe to call multiple times.
     *
     * **Important:** Always call this method when done with the transfer instance to properly clean up resources.
     * This is especially important for iOS developers to prevent memory leaks.
     */
    fun close()

    var deviceRequest: DeviceRequest?
    var deviceResponse: DeviceResponse?
}
