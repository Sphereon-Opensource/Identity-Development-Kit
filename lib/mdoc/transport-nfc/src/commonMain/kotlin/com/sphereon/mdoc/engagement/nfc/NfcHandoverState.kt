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

package com.sphereon.mdoc.engagement.nfc

/**
 * State management for negotiated handover process.
 *
 * This enum tracks the progression through the negotiated handover protocol
 * as defined in ISO/IEC 18013-5 and NFC Forum Connection Handover specification.
 */
internal enum class NegotiatedHandoverState {
    /** Initial state - no handover process started */
    NOT_STARTED,

    /** Waiting for service select message from reader */
    EXPECT_SERVICE_SELECT,

    /** Waiting for handover request message from reader */
    EXPECT_HANDOVER_REQUEST_MESSAGE,

    /** Handover complete - no more messages expected */
    HANDOVER_COMPLETE;

    /** Check if the state allows for message processing */
    fun canProcessMessage(): Boolean = this != HANDOVER_COMPLETE

    /** Get the next expected state after successful message processing */
    fun nextState(): NegotiatedHandoverState = when (this) {
        NOT_STARTED -> EXPECT_SERVICE_SELECT
        EXPECT_SERVICE_SELECT -> EXPECT_HANDOVER_REQUEST_MESSAGE
        EXPECT_HANDOVER_REQUEST_MESSAGE -> HANDOVER_COMPLETE
        HANDOVER_COMPLETE -> HANDOVER_COMPLETE
    }
}

/**
 * State tracking for static handover process.
 *
 * This ensures that the handover callback is only invoked AFTER the reader
 * has actually read the handover data via READ BINARY, not just after SELECT FILE.
 * This prevents issues with short NFC taps where the phone is pulled away before
 * the reader receives the complete data.
 *
 * Reference: ISO/IEC 18013-5:2021 clause 8.3.3.1.2
 */
internal enum class StaticHandoverState {
    /** Initial state - no static handover process started */
    NOT_STARTED,

    /** SELECT FILE processed, handover data prepared but not yet read by reader */
    HANDOVER_PREPARED,

    /** READ BINARY completed, reader has received the handover data */
    HANDOVER_READ,

    /** Callback has been invoked, handover is complete */
    HANDOVER_COMPLETE,

    /** An error occurred during handover */
    HANDOVER_ERROR
}
