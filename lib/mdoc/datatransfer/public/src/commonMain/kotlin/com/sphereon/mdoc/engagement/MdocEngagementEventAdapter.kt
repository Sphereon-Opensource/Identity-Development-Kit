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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Adapter for [MdocEngagementEvent.Listener] with default empty implementations.
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("and", exact = true)
 * Extend this class and override only the events you care about.
 *
 * This adapter provides a convenient way to handle engagement events without
 * needing to implement every method in the Listener interface.
 *
 * ## Usage Example
 * ```kotlin
 * val adapter = object : MdocEngagementEventAdapter() {
 *     override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
 *         displayQrCode(event.qrCodeData)
 *     }
 *
 *     override suspend fun onConnected(event: MdocEngagementEvent.Connected) {
 *         hideQrCode()
 *     }
 * }
 *
 * // Register with EventHub
 * manager.eventHub.addEngagementEventListener(adapter)
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementEventAdapter", exact = true)
abstract class MdocEngagementEventAdapter : MdocEngagementEvent.Listener {
    override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) { // No-op
    }

    override suspend fun onStart(event: MdocEngagementEvent.Start) { // No-op
    }

    override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) { // No-op
    }

    override suspend fun onQrHide(event: MdocEngagementEvent.QrHide) { // No-op
    }

    override suspend fun onNfcEngagement(event: MdocEngagementEvent.NfcEngagement) { // No-op
    }

    override suspend fun onRestApiEngagement(event: MdocEngagementEvent.RestApiEngagement) { // No-op
    }

    override suspend fun onDebug(event: MdocEngagementEvent.Debug) { // No-op
    }

    override suspend fun onData(
        event: MdocEngagementEvent.Data,
        direction: MdocEngagementEvent.Data.Direction,
    ) { // No-op
    }

    override suspend fun onTransfer(event: MdocEngagementEvent.Transfer) { // No-op
    }

    override suspend fun onConnecting(event: MdocEngagementEvent.Connecting) { // No-op
    }

    override suspend fun onConnected(event: MdocEngagementEvent.Connected) { // No-op
    }

    override suspend fun onCanceled(event: MdocEngagementEvent.Canceled) { // No-op
    }

    override suspend fun onDisconnected(event: MdocEngagementEvent.Disconnected) { // No-op
    }

    override suspend fun onError(event: MdocEngagementEvent.Error) { // No-op
    }
}
