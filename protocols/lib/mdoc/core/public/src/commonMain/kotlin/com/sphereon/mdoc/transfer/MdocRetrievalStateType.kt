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
import com.sphereon.mdoc.MdocState
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("provides", exact = true)
@JsExportCompat
 * Represents a state within the context of an mDoc transfer session. This interface provides a way
 * to define various states and their corresponding attributes, which include a state name, an ordinal
 * order, and the ability to map the current state to its engagement counterpart.
 */
sealed interface MdocRetrievalStateType : MdocState {
    /**
     * Represents the current state of the object as a string.
     * This property is intended to store the name of the current state,
     * which can be used to track or identify the specific phase or status.
     */
    override val state: String

    /**
     * Represents the order of the state in the session lifecycle.
     * Used to determine the relative progression of states within
     * the Mdoc transfer session.
     */
    override val order: Int

    /**
     * Retrieves the engagement state of the current transfer session as an instance of [MdocRetrievalState].
     * The engagement state is determined by the `state` property of the session, which is converted
     * into its corresponding enum value in [MdocRetrievalState].
     *
     * This provides a standardized representation of a session's engagement state, enabling consistent handling
     * and comparison across the transfer system.
     */
    val asEngagementState: MdocRetrievalState
        get() = MdocRetrievalState.valueOf(state)

    /**
     * Companion object providing utilities and shared members for `MdocTransferSessionState`.
     */
    companion object {
        /**
         * An array of all available entries in the `MdocTransferSessionState` enumeration.
         * This provides a comprehensive list of all defined states in `MdocTransferSessionState`.
         */
        @JvmStatic
        val entries: Array<MdocRetrievalState> by lazy { MdocRetrievalState.entries.toTypedArray<MdocRetrievalState>() }
    }
}

/**
 * Represents the various states in an mdoc transfer session lifecycle. Each state
 * indicates a particular phase or condition of the session.
 *
 * @property order Defines the order or priority of the state.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocRetrievalState", exact = true)
enum class MdocRetrievalState(
    override val order: Int,
) : MdocRetrievalStateType {
    /**
     * Represents the initial state of the mdoc transfer session.
     * This state is the starting point and is used to signify
     * that the transfer session initialization process has begun.
     *
     * Associated with an order value of 10.
     */
    INIT(110),

    /**
     * Represents the state where the mdoc or reader has selected the transmission type for the session.
     * This state is typically used to determine the type of data transmission technology,
     * such as REST_API, BLE, or NFC.
     * */
    TRANSMISSION_TYPE_SELECTED(120),

    /**
     * Represents the state where the session establishment has been received during an mdoc transfer session.
     * This means the engagement has completed, and the mdoc reader has send the encrypted session establishment data.
     * The mdoc (holder) will not do validation and extract the DeviceRequest and start to perform mdoc selection
     *
     */
    SESSION_ESTABLISHMENT_RECEIVED(130),

    /**
     * Represents a specific state where the selection of documents is started.
     *
     * Typically a user is involved in this process
     */
    DOCUMENTS_SELECTION_PROCESS_START(150),

    /**
     * Represents a specific state where the selection of documents is done and was accepted.
     *
     * Typically a user is involved in this process
     */
    DOCUMENTS_SELECTION_PROCESS_ACCEPTED(160),

    /**
     * Represents the state during a transfer session where session data incorporating the DeviceResponse
     * is being sent to the mdoc reader
     */
    SESSION_DATA_SEND(170),

    SESSION_DATA_RECEIVED(171),

    /**
     * Represents a specific state where the selection of documents is done and was declined by the user.
     * This means the mdoc will return an error to the reader
     *
     * Typically a user is involved in this process
     */
    DOCUMENTS_SELECTION_PROCESS_DECLINED(280),

    /**
     * Represents an error state during the mdoc transfer session.
     * This state indicates that an error has occurred, preventing further progress in the session.
     *
     * Typically used to signal failure scenarios that require cleanup or session termination.
     */
    ERROR(290),

    /**
     * Represents the final state of an `MdocTransferSessionState`.
     * It indicates that the session has been terminated and no further operations
     * can be performed within the current session. This is the final and
     * irreversible state of the session lifecycle.
     *
     * TERMINATED is typically reached when the session has completed successfully
     * or due to a failure that requires the session to end.
     */
    TERMINATED(300),
    ;

    /**
     * Represents the state of the current session in uppercase string format.
     * This value corresponds to the uppercase representation of the session state's name.
     */
    override val state: String = name.uppercase()
}
