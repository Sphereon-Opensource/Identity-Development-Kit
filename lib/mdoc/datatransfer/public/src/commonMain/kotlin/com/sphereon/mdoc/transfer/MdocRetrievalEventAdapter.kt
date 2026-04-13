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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Adapter for [MdocRetrievalEvent.Listener] with default empty implementations.
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("and", exact = true)
 * Extend this class and override only the events you care about.
 *
 * This adapter provides a convenient way to handle retrieval/transfer events without
 * needing to implement every method in the Listener interface.
 *
 * ## Usage Example
 * ```kotlin
 * val adapter = object : MdocRetrievalEventAdapter() {
 *     override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {
 *         processSessionData(event.data)
 *     }
 *
 *     override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {
 *         showSuccess()
 *     }
 * }
 *
 * // Register with EventHub
 * manager.eventHub.addRetrievalEventListener(adapter)
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocRetrievalEventAdapter", exact = true)
abstract class MdocRetrievalEventAdapter : MdocRetrievalEvent.Listener {
    override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) { // No-op
    }

    override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) { // No-op
    }

    override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) { // No-op
    }

    override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) { // No-op
    }

    override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) { // No-op
    }

    override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) { // No-op
    }

    override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) { // No-op
    }

    override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) { // No-op
    }

    override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) { // No-op
    }

    override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) { // No-op
    }

    override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) { // No-op
    }

    override suspend fun onError(event: MdocRetrievalEvent.Error) { // No-op
    }

    override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) { // No-op
    }
}
