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

import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import com.sphereon.mdoc.transfer.MdocRetrievalStateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/*
 * Convenience extension functions for common UI patterns when working with MdocEventHub.
 *
 * These functions provide an easier way to observe specific event types and state transitions
 * without writing boilerplate filtering code.
 */

/**
 * Observes QR code lifecycle events (show and hide).
 *
 * @param scope CoroutineScope to launch the collection in
 * @param onShow Callback when QR code should be shown, receives the QR code data string
 * @param onHide Callback when QR code should be hidden
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeQrLifecycle(
    scope: CoroutineScope,
    onShow: (String) -> Unit,
    onHide: () -> Unit,
): Job =
    scope.launch {
        engagementEvents.collect { event ->
            when (event) {
                is MdocEngagementEvent.QrShow -> {
                    onShow(event.qrCodeData)
                }

                is MdocEngagementEvent.QrHide -> {
                    onHide()
                }

                else -> { /* ignore other events */ }
            }
        }
    }

/**
 * Observes connection status changes across all engagements.
 *
 * @param scope CoroutineScope to launch the collection in
 * @param onConnecting Callback when an engagement starts connecting
 * @param onConnected Callback when an engagement successfully connects
 * @param onDisconnected Callback when an engagement disconnects, receives reason
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeConnectionStatus(
    scope: CoroutineScope,
    onConnecting: () -> Unit,
    onConnected: () -> Unit,
    onDisconnected: (String) -> Unit,
): Job =
    scope.launch {
        engagementEvents.collect { event ->
            when (event) {
                is MdocEngagementEvent.Connecting -> {
                    onConnecting()
                }

                is MdocEngagementEvent.Connected -> {
                    onConnected()
                }

                is MdocEngagementEvent.Disconnected -> {
                    onDisconnected(event.reason)
                }

                else -> { /* ignore other events */ }
            }
        }
    }

/**
 * Observes transfer progress by tracking transfer state changes.
 *
 * @param scope CoroutineScope to launch the collection in
 * @param onProgress Callback for each transfer state change, receives the current state
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeTransferProgress(
    scope: CoroutineScope,
    onProgress: (MdocRetrievalStateType) -> Unit,
): Job =
    scope.launch {
        transferEvents.collect { event ->
            onProgress(event.state)
        }
    }

/**
 * Observes only NFC engagement events.
 *
 * @param scope CoroutineScope to launch the collection in
 * @param onNfcEngagement Callback when NFC engagement event occurs
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeNfcEngagements(
    scope: CoroutineScope,
    onNfcEngagement: (MdocEngagementEvent.NfcEngagement) -> Unit,
): Job =
    scope.launch {
        engagementEvents
            .filterIsInstance<MdocEngagementEvent.NfcEngagement>()
            .collect(onNfcEngagement)
    }

/**
 * Observes engagement errors.
 *
 * @param scope CoroutineScope to launch the collection in
 * @param onError Callback when an error occurs, receives reason and optional throwable
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeErrors(
    scope: CoroutineScope,
    onError: (reason: String, throwable: Throwable?) -> Unit,
): Job =
    scope.launch {
        allEvents.collect { event ->
            when (event) {
                is MdocEngagementEvent.Error -> {
                    onError(event.reason, event.error)
                }

                is MdocRetrievalEvent.Error -> {
                    onError("Transfer error: ${event.data.decodeToString()}", event.error)
                }

                else -> { /* ignore other events */ }
            }
        }
    }

/**
 * Observes state transitions for a specific engagement state.
 *
 * @param scope CoroutineScope to launch the collection in
 * @param targetState The engagement state to watch for
 * @param onTransition Callback when the target state is reached
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeEngagementState(
    scope: CoroutineScope,
    targetState: MdocEngagementState,
    onTransition: (MdocEngagementEvent) -> Unit,
): Job =
    scope.launch {
        engagementEvents
            .collect { event ->
                if (event.state == targetState) {
                    onTransition(event)
                }
            }
    }

/**
 * Observes state transitions for a specific transfer state.
 *
 * @param scope CoroutineScope to launch the collection in
 * @param targetState The transfer state to watch for
 * @param onTransition Callback when the target state is reached
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeTransferState(
    scope: CoroutineScope,
    targetState: MdocRetrievalStateType,
    onTransition: (MdocRetrievalEvent) -> Unit,
): Job =
    scope.launch {
        transferEvents
            .collect { event ->
                if (event.state == targetState) {
                    onTransition(event)
                }
            }
    }

/**
 * Observes transfer completion events (final states with order >= 200).
 *
 * @param scope CoroutineScope to launch the collection in
 * @param onComplete Callback when a transfer completes
 * @return Job that can be cancelled to stop observing
 */
fun MdocEventHub.observeTransferCompletion(
    scope: CoroutineScope,
    onComplete: (MdocRetrievalEvent) -> Unit,
): Job =
    scope.launch {
        onTransferCompletion.collect(onComplete)
    }
