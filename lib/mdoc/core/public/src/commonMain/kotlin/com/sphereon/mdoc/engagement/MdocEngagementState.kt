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

package com.sphereon.mdoc.engagement

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.mdoc.MdocState

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementStateType", exact = true)
sealed interface MdocEngagementStateType : MdocState {
    override val state: String

    override val order: Int

    val asEngagementState: MdocEngagementState
        get() = MdocEngagementState.valueOf(state)

    companion object {
        val entries: Array<MdocEngagementState> by lazy { MdocEngagementState.entries.toTypedArray<MdocEngagementState>() }
    }

}


/**
 * Represents the different states of an mDoc engagement process.
 * Each state is associated with an order to determine its progression
 * within the engagement process. Not every state is guaranteed to happen.
 * However, it should not be possible to go from a state with a higher level
 * order to a state with a lower order.
 *
 * Once the state connected is achieved, Mdoc data can be sent across
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementState", exact = true)
enum class MdocEngagementState(override val order: Int) : MdocEngagementStateType {
    /**
     * Represents the initial state within the engagement process.
     *
     * This state is used to signify the starting point of an engagement.
     */
    INIT(10),

    /**
     * Represents the start state of engagement by a user or NFC interaction
     *
     * This state is used to signify the starting point of an engagement.
     */

    START(20),

    /**
     * Represents the BLE scanning state in the mdoc engagement process.
     * This state indicates that the BLE scanning is in progress
     */
    BLE_SCANNING(30),

    /**
     * Represents a Bluetooth Low Energy (BLE) advertising state.
     *
     * This state is part of the engagement process and indicates
     * that the device is advertising its presence over BLE for
     * potential connections or interactions.
     */
    BLE_ADVERTISING(30),

    /**
     * Represents the state where NFC is enabled and ready for engagement.
     */
    NFC_ENABLED(30),


    /**
     * Represents the state where a connection process is actively taking place.
     * Assigned an order value of 50 to signify its relative position in the engagement state flow.
     *
     * Once this event occurs, it means a single transfer method will be used
     */
    CONNECTING(50),

    /**
     * Represents the state where a connection has been successfully established.
     * This is typically reached after the CONNECTING state and before a state
     * such as DISCONNECTED or ERROR occurs.
     * This is where the Transfer / Retrieval phase will happen and where data is being sent across.
     */
    CONNECTED(100),


    /**
     * Represents the state where a connection has been successfully established and data is being exchanged.
     * This is to setup the Transfer / Retrieval phase will happen and where data is being sent across.
     */
    DATA(110),

    TRANSFER(150),


    /**
     * Represents the state when the engagement process has been canceled.
     *
     * This state signifies that the operation has been intentionally stopped
     * and is no longer active within the engagement lifecycle.
     */
    CANCELED(280),

    /**
     * Represents the error state during the mDoc engagement process.
     * This state indicates that an error has occurred, interrupting the engagement flow.
     */
    ERROR(290),

    /**
     * Represents the state where the engagement has been disconnected.
     * This state indicates the end of an interaction or a failure
     * that led to the disengagement, with no active connection currently present.
     */
    DISCONNECTED(300);

    /**
     * Represents the uppercase name of the current enum instance.
     * This value is derived from the enum constant's name converted to uppercase letters.
     */
    override val state: String = name.uppercase()
}

