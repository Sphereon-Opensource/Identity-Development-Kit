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

package com.sphereon.mdoc.engagement.nfc

import com.sphereon.cbor.CborItem
import com.sphereon.mdoc.util.getCurrentTimeMillis
import kotlinx.atomicfu.atomic
import kotlinx.io.bytestring.ByteString
import kotlin.concurrent.Volatile

/**
 * Immutable state container for NFC engagement helper.
 *
 * This class encapsulates all mutable state of the NFC helper into a single
 * immutable data class. State transitions are performed through copy() operations,
 * ensuring thread-safety and preventing inconsistent states.
 *
 * ## Benefits:
 * - Single atomic state update instead of multiple volatile fields
 * - Impossible to have inconsistent state between related fields
 * - Easier testing through explicit state snapshots
 * - Clear state transitions via copy()
 *
 * @property negotiatedHandoverState Current state of negotiated handover process
 * @property staticHandoverState Current state of static handover process
 * @property selectedFileId Currently selected file ID (0 if none)
 * @property selectedFilePayload Payload of the currently selected file
 * @property ndefApplicationSelected Whether NDEF application has been selected
 * @property inError Whether the helper is in an error state
 * @property pendingStaticHandover Pending handover data awaiting READ BINARY completion
 * @property handoverCompleteCalled Whether the handover complete callback has been invoked
 * @property expectedReadSize Expected size of data to be read for static handover
 * @property totalBytesRead Total bytes read so far for static handover tracking
 */
internal data class NfcHelperState(
    val negotiatedHandoverState: NegotiatedHandoverState = NegotiatedHandoverState.NOT_STARTED,
    val staticHandoverState: StaticHandoverState = StaticHandoverState.NOT_STARTED,
    val selectedFileId: Int = 0,
    val selectedFilePayload: ByteString = ByteString(),
    val ndefApplicationSelected: Boolean = false,
    val inError: Boolean = false,
    val pendingStaticHandover: CborItem<*>? = null,
    val handoverCompleteCalled: Boolean = false,
    val expectedReadSize: Int = 0,
    val totalBytesRead: Int = 0,
) {
    companion object {
        /** Initial state for a new NFC helper */
        val INITIAL = NfcHelperState()
    }

    /**
     * Check if the helper is in an unrecoverable error state.
     */
    val isUnrecoverable: Boolean
        get() = inError || staticHandoverState == StaticHandoverState.HANDOVER_ERROR

    /**
     * Check if the handover has been completed.
     */
    val isHandoverComplete: Boolean
        get() =
            handoverCompleteCalled ||
                staticHandoverState == StaticHandoverState.HANDOVER_COMPLETE ||
                negotiatedHandoverState == NegotiatedHandoverState.HANDOVER_COMPLETE

    /**
     * Check if the helper is operational (not in error state).
     */
    val isOperational: Boolean
        get() = !inError

    /**
     * Check if a file is currently selected.
     */
    val hasFileSelected: Boolean
        get() = selectedFileId != 0

    // ===========================================
    // State transition methods (return new state)
    // ===========================================

    /**
     * Transition to error state.
     */
    fun toError(): NfcHelperState = copy(inError = true)

    /**
     * Select NDEF application.
     */
    fun withNdefApplicationSelected(): NfcHelperState = copy(ndefApplicationSelected = true)

    /**
     * Select a file with its payload.
     */
    fun withSelectedFile(
        fileId: Int,
        payload: ByteString,
    ): NfcHelperState = copy(selectedFileId = fileId, selectedFilePayload = payload)

    /**
     * Update negotiated handover state.
     */
    fun withNegotiatedHandoverState(state: NegotiatedHandoverState): NfcHelperState = copy(negotiatedHandoverState = state)

    /**
     * Update static handover state.
     */
    fun withStaticHandoverState(state: StaticHandoverState): NfcHelperState = copy(staticHandoverState = state)

    /**
     * Prepare for static handover with pending data.
     */
    fun withPendingStaticHandover(
        handover: CborItem<*>,
        expectedSize: Int,
    ): NfcHelperState =
        copy(
            pendingStaticHandover = handover,
            staticHandoverState = StaticHandoverState.HANDOVER_PREPARED,
            expectedReadSize = expectedSize,
            totalBytesRead = 0,
        )

    /**
     * Record bytes read during static handover.
     */
    fun withBytesRead(bytesRead: Int): NfcHelperState = copy(totalBytesRead = bytesRead)

    /**
     * Mark handover as complete (callback invoked).
     */
    fun withHandoverComplete(): NfcHelperState =
        copy(
            handoverCompleteCalled = true,
            staticHandoverState =
                if (staticHandoverState == StaticHandoverState.HANDOVER_READ) {
                    StaticHandoverState.HANDOVER_COMPLETE
                } else {
                    staticHandoverState
                },
        )

    /**
     * Mark static handover as read (data sent to reader).
     */
    fun withStaticHandoverRead(): NfcHelperState =
        copy(
            staticHandoverState = StaticHandoverState.HANDOVER_READ,
        )

    /**
     * Mark static handover as errored.
     */
    fun withStaticHandoverError(): NfcHelperState =
        copy(
            staticHandoverState = StaticHandoverState.HANDOVER_ERROR,
        )

    /**
     * Reset to initial state, optionally preserving error state.
     */
    fun reset(clearErrorState: Boolean = false): NfcHelperState =
        if (clearErrorState) {
            INITIAL
        } else {
            INITIAL.copy(inError = inError)
        }

    /**
     * Get state info map for debugging.
     */
    fun toDebugMap(): Map<String, Any> =
        mapOf(
            "inError" to inError,
            "ndefApplicationSelected" to ndefApplicationSelected,
            "selectedFileId" to selectedFileId.toString(16),
            "negotiatedHandoverState" to negotiatedHandoverState.name,
            "staticHandoverState" to staticHandoverState.name,
            "selectedFilePayloadSize" to selectedFilePayload.size,
            "handoverCompleteCalled" to handoverCompleteCalled,
            "hasPendingStaticHandover" to (pendingStaticHandover != null),
            "expectedReadSize" to expectedReadSize,
            "totalBytesRead" to totalBytesRead,
        )
}

/**
 * Thread-safe state holder for NfcHelperState.
 *
 * Uses atomicfu for lock-free atomic state updates. All state transitions
 * go through this holder to ensure consistency.
 */
internal class NfcHelperStateHolder {
    private val atomicState = atomic(NfcHelperState.INITIAL)

    /**
     * Get current state snapshot.
     */
    val value: NfcHelperState
        get() = atomicState.value

    /**
     * Atomically update the state using a transform function.
     * Returns the new state after transformation.
     */
    inline fun update(transform: (NfcHelperState) -> NfcHelperState): NfcHelperState {
        while (true) {
            val current = atomicState.value
            val new = transform(current)
            if (atomicState.compareAndSet(current, new)) {
                return new
            }
        }
    }

    /**
     * Atomically update the state and return both old and new state.
     */
    inline fun getAndUpdate(transform: (NfcHelperState) -> NfcHelperState): Pair<NfcHelperState, NfcHelperState> {
        while (true) {
            val current = atomicState.value
            val new = transform(current)
            if (atomicState.compareAndSet(current, new)) {
                return current to new
            }
        }
    }

    /**
     * Reset to initial state.
     */
    fun reset(clearErrorState: Boolean = false) {
        update { it.reset(clearErrorState) }
    }
}

/**
 * Captured NFC session state for recovery after short taps.
 *
 * Per ISO 18013-5, the device engagement timeout should be no less than 30 seconds.
 * This state can be restored within that window if the user taps again.
 */
internal data class NfcSessionState(
    /** Time when state was captured */
    val captureTimeMs: Long,
    /** The captured helper state */
    val helperState: NfcHelperState,
) {
    // Convenience constructors for backwards compatibility
    constructor(
        captureTimeMs: Long,
        staticHandoverState: StaticHandoverState,
        negotiatedHandoverState: NegotiatedHandoverState,
        selectedFileId: Int,
        selectedFilePayload: ByteString,
        ndefApplicationSelected: Boolean,
        pendingStaticHandover: CborItem<*>?,
        handoverCompleteCalled: Boolean,
        expectedReadSize: Int,
        totalBytesRead: Int,
    ) : this(
        captureTimeMs = captureTimeMs,
        helperState =
            NfcHelperState(
                negotiatedHandoverState = negotiatedHandoverState,
                staticHandoverState = staticHandoverState,
                selectedFileId = selectedFileId,
                selectedFilePayload = selectedFilePayload,
                ndefApplicationSelected = ndefApplicationSelected,
                pendingStaticHandover = pendingStaticHandover,
                handoverCompleteCalled = handoverCompleteCalled,
                expectedReadSize = expectedReadSize,
                totalBytesRead = totalBytesRead,
            ),
    )

    // Delegate properties for backwards compatibility
    val staticHandoverState: StaticHandoverState get() = helperState.staticHandoverState
    val negotiatedHandoverState: NegotiatedHandoverState get() = helperState.negotiatedHandoverState
    val selectedFileId: Int get() = helperState.selectedFileId
    val selectedFilePayload: ByteString get() = helperState.selectedFilePayload
    val ndefApplicationSelected: Boolean get() = helperState.ndefApplicationSelected
    val pendingStaticHandover: CborItem<*>? get() = helperState.pendingStaticHandover
    val handoverCompleteCalled: Boolean get() = helperState.handoverCompleteCalled
    val expectedReadSize: Int get() = helperState.expectedReadSize
    val totalBytesRead: Int get() = helperState.totalBytesRead

    /**
     * Check if this state is still valid (not expired).
     *
     * @param currentTimeMs Current time in milliseconds (defaults to getCurrentTimeMillis())
     * @param validityMs Validity window in milliseconds
     */
    fun isValid(
        currentTimeMs: Long = getCurrentTimeMillis(),
        validityMs: Long = MdocNfcConstants.DEFAULT_SESSION_VALIDITY_MS,
    ): Boolean = currentTimeMs - captureTimeMs < validityMs

    /**
     * Check if this state is recoverable (valid and handover not complete).
     *
     * @param currentTimeMs Current time in milliseconds (defaults to getCurrentTimeMillis())
     * @param validityMs Validity window in milliseconds
     */
    fun isRecoverable(
        currentTimeMs: Long = getCurrentTimeMillis(),
        validityMs: Long = MdocNfcConstants.DEFAULT_SESSION_VALIDITY_MS,
    ): Boolean = isValid(currentTimeMs, validityMs) && !helperState.isHandoverComplete
}
