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

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.mdoc.MdocEvent
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an engagement event during the lifecycle of a secure mobile engagement process.
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("is", exact = true)
@JsExportCompat
 * This sealed interface is extended by a variety of specific engagement events, which describe
 * distinct phases or states of the process.
 *
 * Extends [MdocEvent] to provide a unified interface for all mdoc interaction events.
 */
@OptIn(ExperimentalUuidApi::class)
sealed interface MdocEngagementEvent : MdocEvent {
    /**
     * Represents the specific role assigned in an engagement context. Either MDOC (HOLDER) or MDOC_READER
     */
    val role: MdocRole

    /**
     * Unique identifier representing a specific engagement event.
     * This ID is used to distinctly track and reference an instance of an engagement.
     */
    val engagementId: Uuid

    /**
     * Represents the current state of the mdoc engagement process.
     * The state indicates the progression of the engagement,
     * from initialization to connection, disconnection, or error.
     */
    override val state: MdocEngagementState

    /**
     * Represents the time at which the engagement event occurred.
     *
     * The time is stored as a `LocalDateTimeKMP` object, which provides a platform-independent way
     * to handle date and time. This includes detailed components such as year, month, day, hour,
     * minute, second, and optional nanoseconds, with support for timezone and comparison operations.
     */
    override val time: LocalDateTimeKMP

    /**
     * Indicates whether this event originates from the currently active engagement.
     *
     * When an engagement moves to Connecting state, it becomes the only active engagement.
     * Events from other (non-active) engagements should be marked with isActive=false to allow
     * the UI layer (SessionUiProjector) to filter them out and prevent UI updates from
     * cleanup/cancellation events of inactive engagements.
     *
     * This property is crucial for proper UI state management when multiple engagements exist
     * but only one is actively progressing through the connection/transfer flow.
     *
     * @return true if this event is from the active engagement, false otherwise
     */
    val isActive: Boolean
        get() = true // Default to true for backward compatibility

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("AbstractEngagementEvent", exact = true)
    abstract class AbstractEngagementEvent : MdocEngagementEvent {
        protected constructor()

        override fun toString(): String = "${this::class.simpleName}(role=$role, engagementId=$engagementId, state=$state, time=$time, isActive=$isActive)"
    }

    /**
     * Represents the initial phase of an engagement process, indicating the engagement is in its
     * preliminary state and has just been created or initialized.
     *
     * @property role Specifies the role of the entity involved in the engagement,
     * indicating if it is a HOLDER or READER.
     * @property engagementId A unique identifier associated with the specific engagement instance.
     * @property state Defines the current state of the engagement, which is set to `INIT` in this class.
     * @property time Represents the local date and time at which this engagement event is initialized.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Initializing", exact = true)
    data class Initializing(
        override val role: MdocRole,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the initial state of an engagement within the `Initializing` phase.
         * This state is set to `MdocTransferSessionState.INIT`, indicating that the engagement
         * process has just started and no further steps have been executed yet.
         */
        override val state = MdocEngagementState.INIT

        /**
         * Represents the timestamp associated with the event.
         *
         * Provides the current local date and time using the default time zone and clock configuration defined
         * in the `DateTimeUtils` class. This value is initialized using `dateTimeLocal()` from the `DateTimeUtils.DEFAULT` instance.
         *
         * This property is typically used to capture the local time when the event originates.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Start", exact = true)
    data class Start(
        override val role: MdocRole,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the initial state of an engagement within the `Initializing` phase.
         * This state is set to `MdocTransferSessionState.INIT`, indicating that the engagement
         * process has just started and no further steps have been executed yet.
         */
        override val state = MdocEngagementState.START

        /**
         * Represents the timestamp associated with the event.
         *
         * Provides the current local date and time using the default time zone and clock configuration defined
         * in the `DateTimeUtils` class. This value is initialized using `dateTimeLocal()` from the `DateTimeUtils.DEFAULT` instance.
         *
         * This property is typically used to capture the local time when the event originates.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents a QR-based engagement event when the QR code should be displayed.
     *
     * @property role The role of the engagement participant, either HOLDER or READER.
     * @property qrCodeData A string containing QR code data used in the engagement process.
     * @property engagement The CBOR-encoded representation of the device engagement.
     * @property engagementId A unique identifier for the engagement session.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("QrShow", exact = true)
    data class QrShow(
        override val role: MdocRole,
        val qrCodeData: String,
        val engagement: DeviceEngagement,
        override val engagementId: Uuid,
    ) : MdocEngagementEvent {
        /**
         * Indicates whether the device engagement supports BLE (Bluetooth Low Energy) as a device retrieval method.
         *
         * This flag is derived from the associated `DeviceEngagement` instance and is used
         * to determine if BLE-related actions should be performed during engagement processing.
         */
        val ble: Boolean = engagement.hasBleDeviceRetrievalMethod

        /**
         * Represents the current state when QR code should be shown. The state is determined by
         * the presence of BLE (Bluetooth Low Energy) capabilities and the assigned role in the engagement.
         *
         * If BLE support is enabled:
         * - When the role is `READER`, the state is set to `BLE_ADVERTISING`.
         * - For other roles, the state is set to `BLE_SCANNING`.
         *
         * If BLE support is not enabled, the state defaults to `INIT`.
         *
         * Note: The state determination currently does not depend on forward or reverse engagement modes
         * and relies solely on BLE support and role assignment.
         *
         * TODO: Does not depend on role. Should depend on forward/reverse engagement
         */
        override val state =
            if (ble) {
                if (role == MdocRole.MDOC_READER) {
                    MdocEngagementState.BLE_ADVERTISING
                } else {
                    MdocEngagementState.BLE_SCANNING
                }
            } else {
                MdocEngagementState.INIT
            }

        /**
         * Represents the timestamp for the engagement event, specifying when the event
         * occurred in local date and time with timezone awareness.
         *
         * The value is initialized using the default settings from `DateTimeUtils.DEFAULT`
         * and provides the local date and time at the moment it is accessed.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents an event indicating that the QR code should be hidden from display.
     * This occurs when a transfer is about to start (CONNECTING state).
     *
     * @property role The role of the engagement participant, either HOLDER or READER.
     * @property engagementId A unique identifier for the engagement session.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("QrHide", exact = true)
    data class QrHide(
        override val role: MdocRole,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the state when QR code should be hidden.
         * Set to CONNECTING as this indicates a transfer is about to start
         * and the QR should no longer be displayed.
         */
        override val state = MdocEngagementState.CONNECTING

        /**
         * Represents the timestamp when the QR hide event occurred.
         *
         * The value is initialized using the default settings from `DateTimeUtils.DEFAULT`
         * and provides the local date and time at the moment it is accessed.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents an NFC engagement event characterized by a specific role, device engagement data,
     * and various engagement-related attributes.
     *
     * @property role Indicates the role in the engagement process. It can be either HOLDER or READER.
     * @property engagement Contains device engagement data encoded in CBOR format, which includes
     * retrieval methods, protocol information, and other associated properties.
     * @property engagementId A unique identifier for the engagement event to differentiate it from others.
     * @property ble A boolean indicating whether the device retrieval method includes Bluetooth Low Energy (BLE).
     * It is determined based on the properties of the `DeviceEngagement` instance.
     * @property state Describes the current engagement state of the object. The state depends on
     * whether BLE is available and on the specified role. For example, if BLE is enabled and the role is READER,
     * the state would be BLE_ADVERTISING. If BLE is not enabled, the state defaults to INIT.
     * @property time Represents the engagement timestamp. The default value is the current local date and time.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("NfcEngagement", exact = true)
    data class NfcEngagement(
        override val role: MdocRole,
        val engagement: DeviceEngagement,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Indicates if the device retrieval method for this engagement is BLE (Bluetooth Low Energy).
         *
         * This property is derived from the `engagement` instance, specifically checking for
         * BLE support in the context of the engagement method.
         */
        val ble: Boolean = engagement.hasBleDeviceRetrievalMethod

        /**
         * Represents the current state of the engagement based on BLE support and the assigned role.
         *
         * The state is determined as follows:
         * - If BLE is supported:
         *   - When the role is `MdocRole.MDOC_READER`, the state is set to `MdocTransferSessionState.BLE_ADVERTISING`.
         *   - Otherwise, the state is set to `MdocTransferSessionState.BLE_SCANNING`.
         * - If BLE is not supported, the state defaults to `MdocTransferSessionState.INIT`.
         *
         * Note: The current implementation determines the state based on the role. However, it should ideally consider
         * forward/reverse engagement mechanisms for better alignment with expected behavior.
         *
         * TODO: Does not depend on role. Should depend on forward/reverse engagement
         */
        override val state =
            if (ble) {
                if (role == MdocRole.MDOC_READER) {
                    MdocEngagementState.BLE_ADVERTISING
                } else {
                    MdocEngagementState.BLE_SCANNING
                }
            } else {
                MdocEngagementState.INIT
            }

        /**
         * Represents the timestamp when the engagement event was created or initialized.
         * This is a `LocalDateTimeKMP` instance, set to the current local date and time
         * using the default time zone settings provided by `DateTimeUtils.DEFAULT`.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("RestApiEngagement", exact = true)
    data class RestApiEngagement(
        override val role: MdocRole,
        val readerEngagement: ReaderEngagement? = null,
        val uri: String,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        override val state = MdocEngagementState.INIT

        /**
         * Represents the timestamp when the engagement event was created or initialized.
         * This is a `LocalDateTimeKMP` instance, set to the current local date and time
         * using the default time zone settings provided by `DateTimeUtils.DEFAULT`.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents a debug event within an engagement context.
     *
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("is", exact = true)
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("Debug", exact = true)
     * This class is a data representation of an event that occurs during an
     * engagement, capturing details such as the role of the party involved,
     * a debug message, the unique identifier of the engagement, the state
     * of the engagement, and the timestamp at which the event occurred.
     *
     * @property role The role of the party involved in the event. It can be either HOLDER or READER.
     * @property message A descriptive message providing details about the debug event.
     * @property engagementId The unique identifier for the engagement associated with this debug event.
     * @property state The current state of the engagement when the event occurred.
     */
    data class Debug(
        override val role: MdocRole,
        val message: String,
        override val engagementId: Uuid,
        override val state: MdocEngagementState,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the timestamp when the event occurred.
         *
         * This property is defined as a `LocalDateTimeKMP` instance and provides
         * the local date and time computed using the default settings of `DateTimeUtils.DEFAULT`.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents a connecting state during an engagement process.
     *
     * @property role The role of the entity in the engagement, either HOLDER or READER.
     * @property engagementId The unique identifier for the ongoing engagement.
     * @property dataRetrievalMethods The methods of data retrieval that can be used, such as NFC, BLE, or WIFI_AWARE.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Connecting", exact = true)
    data class Connecting(
        override val role: MdocRole,
        override val engagementId: Uuid,
        val deviceRetrievalMethods: Array<DeviceRetrievalMethod>,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the current state of an engagement as "CONNECTING".
         * Indicates that the engagement is in the process of establishing a connection.
         */
        override val state = MdocEngagementState.CONNECTING

        /**
         * Represents the timestamp associated with this engagement event.
         *
         * This variable uses the default instance of `DateTimeUtils` to retrieve the
         * current local date and time as an instance of `LocalDateTimeKMP`.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents a connected state in the engagement lifecycle.
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("holds", exact = true)
     * This class holds information about the engagement role, engagement ID,
     * data retrieval method, and the associated state and timestamp.
     *
     * @property role The role of the participant in the engagement, such as HOLDER or READER.
     * @property engagementId The unique identifier for the engagement session.
     * @property deviceRetrievalMethod The retrieval method that is used for the connection
     */
    data class Connected(
        override val role: MdocRole,
        override val engagementId: Uuid,
        val deviceRetrievalMethod: DeviceRetrievalMethod,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the current state of the engagement as CONNECTED.
         * This indicates the engagement has established a successful connection.
         */
        override val state = MdocEngagementState.CONNECTED

        /**
         * Represents the timestamp of when the event occurred.
         *
         * The `time` variable holds the local date and time, initialized using the default `DateTimeUtils`
         * configuration. It is stored as an instance of `LocalDateTimeKMP`, ensuring compatibility across
         * platforms and maintaining timezone-local representation. This timestamp is tied to the event's
         * lifecycle and reflects the creation or state transition time of the associated event.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents an engagement event where the engagement has been canceled.
     *
     * @property role The role of the entity involved in the engagement, such as HOLDER or READER.
     * @property reason An optional reason for the cancellation of the engagement.
     * @property engagementId The unique identifier of the engagement.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Canceled", exact = true)
    data class Canceled(
        override val role: MdocRole,
        val reason: String? = null,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the current state of the engagement as canceled.
         * Indicates that the engagement process has been terminated before completion.
         */
        override val state = MdocEngagementState.CANCELED

        /**
         * Represents the date and time when the event occurred.
         *
         * The value is initialized to the current local date and time using a default instance
         * of `DateTimeUtils`. The local date and time is determined by the system's default
         * time zone unless explicitly overridden within the `DateTimeUtils` configuration.
         *
         * This property is part of an override in classes implementing or extending from a base interface
         * or superclass and is intended to provide a consistent timestamp for event-handling or logging purposes.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents a disconnection event in the engagement lifecycle.
     *
     * @property role The role of the entity involved in the disconnection.
     * @property reason The reason for the disconnection.
     * @property engagementId The unique identifier of the engagement.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Disconnected", exact = true)
    data class Disconnected(
        override val role: MdocRole,
        val reason: String,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the engagement state when the connection has been terminated or is no longer active.
         * This state is part of the {@link MdocTransferSessionState} enumeration, specifically denoting
         * a disconnected state for the engagement.
         */
        override val state = MdocEngagementState.DISCONNECTED

        /**
         * The timestamp representing the moment this event occurred, expressed in the local time zone.
         * This is initialized using the `DateTimeUtils.DEFAULT.dateTimeLocal()` method to capture the
         * current local date and time when the event is created.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /*  data class DeviceRequestEvent(override val role: MdocRole, val deviceRequest: UIntArray) : EngagementEvent

      data class DeviceResponseEvent(override val role: MdocRole, val deviceResponse: UIntArray) : EngagementEvent*/

    /**
     * Represents an error event during an engagement process.
     *
     * @property role Indicates the role associated with the engagement error event, either HOLDER or READER.
     * @property reason Specifies the reason for the error.
     * @property error Contains the underlying throwable cause of the error, if any.
     * @property engagementId The unique identifier of the engagement where the error occurred.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Error", exact = true)
    data class Error(
        override val role: MdocRole,
        val reason: String,
        val error: Throwable? = null,
        override val engagementId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the specific engagement state as `MdocTransferSessionState.ERROR`.
         * This state indicates that an error has occurred during the engagement process.
         */
        override val state = MdocEngagementState.ERROR

        /**
         * Represents the date and time when the `EngagementEvent` occurred, in local time.
         *
         * The value is generated using the default instance of `DateTimeUtils`, which
         * provides the current local date and time based on the system's time zone.
         * This provides a standardized representation of the event's creation or occurrence.
         *
         * @see DateTimeUtils.DEFAULTS
         * @see DateTimeUtils.dateTimeLocal
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Represents a data communication event within an engagement.
     *
     * @property role The role of the entity involved in the engagement (e.g., HOLDER, READER).
     * @property engagementId A unique identifier for the engagement.
     * @property data The byte array representing the communicated data.
     * @property direction The direction of the data communication (INCOMING or OUTGOING).
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Data", exact = true)
    data class Data(
        override val role: MdocRole,
        override val engagementId: Uuid,
        val data: ByteArray,
        val direction: Direction,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the direction of data transmission in an engagement event.
         */
        enum class Direction {
            /**
             * Represents the incoming direction.
             * Typically used to define or handle inbound flow or references.
             */
            INCOMING,

            /**
             * Represents the outgoing direction in the context of the `Direction` enum.
             * Can be used to specify or denote outgoing behavior or flow.
             */
            OUTGOING,
        }

        /**
         * Represents the current state of the engagement event.
         * This specific state indicates that the engagement is established and connected.
         */
        override val state = MdocEngagementState.DATA

        /**
         * Represents the timestamp associated with the event.
         *
         * The value is derived from the default instance of `DateTimeUtils` using the system's local time zone.
         * It is used to capture the local date and time when the event occurred.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        /**
         * Compares this object to the specified object to determine if they are equal.
         *
         * @param other the object to compare this instance with
         * @return `true` if the objects are equal, `false` otherwise
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as Data

            if (role != other.role) {
                return false
            }
            if (engagementId != other.engagementId) {
                return false
            }
            if (!data.contentEquals(other.data)) {
                return false
            }
            if (direction != other.direction) {
                return false
            }
            if (state != other.state) {
                return false
            }
            if (time != other.time) {
                return false
            }

            return true
        }

        /**
         * Computes a hash code for the object based on its properties. This method ensures that
         * objects with the same content produce the same hash code, while different content
         * produces different hash codes.
         *
         * @return An integer value representing the hash code of the object.
         */
        override fun hashCode(): Int {
            var result = role.hashCode()
            result = 31 * result + engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + direction.hashCode()
            result = 31 * result + state.hashCode()
            result = 31 * result + time.hashCode()
            return result
        }

        override fun toString(): String =
            "Data(role=$role, engagementId=$engagementId, data=${data.encodeTo(Encoding.HEX).chunked(HEX_CHUNK_SIZE).joinToString(",")}, direction=$direction, state=$state, time=$time)"

        companion object {
            private const val HEX_CHUNK_SIZE = 2000
        }
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Transfer", exact = true)
    data class Transfer(
        override val role: MdocRole,
        override val engagementId: Uuid,
        val transferId: Uuid,
    ) : AbstractEngagementEvent() {
        /**
         * Represents the current state of the engagement event.
         * This specific state indicates that the engagement is established and connected.
         */
        override val state = MdocEngagementState.TRANSFER

        /**
         * Represents the timestamp associated with the event.
         *
         * The value is derived from the default instance of `DateTimeUtils` using the system's local time zone.
         * It is used to capture the local date and time when the event occurred.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()
    }

    /**
     * Interface that defines listeners for various events occurring during an engagement process.
     * Implementers can handle specific types of events by defining the corresponding methods.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Listener", exact = true)
    interface Listener {
        /**
         * Invoked when an initializing event occurs.
         *
         * @param event The event containing information about the initializing state,
         *              including engagement role, engagement ID, and timestamp.
         */
        suspend fun onInitializing(event: Initializing)

        suspend fun onStart(event: Start)

        /**
         * Handles an event when QR code should be displayed.
         *
         * @param event The instance of [QrShow] containing details about the QR code engagement,
         * including the role, QR code data, device engagement details, engagement ID, and BLE status.
         */
        suspend fun onQrShow(event: QrShow)

        /**
         * Handles an event when QR code should be hidden.
         * This typically occurs when a transfer is starting (CONNECTING state).
         *
         * @param event The instance of [QrHide] containing the role and engagement ID.
         */
        suspend fun onQrHide(event: QrHide)

        /**
         * Handles an NFC engagement event during the interaction process.
         *
         * This method is invoked when an NFC engagement event is encountered. It processes the provided event
         * to manage the state or actions specific to the NFC engagement.
         *
         * @param event The NFC engagement event containing details about the role, engagement data, engagement ID,
         * and BLE-related properties.
         */
        suspend fun onNfcEngagement(event: NfcEngagement)

        /**
         * Handles the debug event within the engagement process.
         *
         * @param event The debug event containing the role, message, engagement ID, state,
         * and timestamp data relevant to the debugging information.
         */
        suspend fun onDebug(event: Debug)

        /**
         * Handles a data event with the specified direction.
         *
         * @param event The data event to be processed, containing details such as the engagement role, engagement ID, data content, and state.
         * @param direction The direction of the data flow, which can either be incoming or outgoing.
         */
        suspend fun onData(
            event: Data,
            direction: Data.Direction,
        )

        /**
         * Handles a transfer event
         *
         * @param event The transfer event to be processed, Indicates the retrieval/transfer phase starts.
         */
        suspend fun onTransfer(event: Transfer)

        /**
         * Invoked when a connecting event occurs during the engagement process.
         *
         * @param event The `Connecting` event containing details such as the role, identifier, engagement ID,
         *              data retrieval method, and other relevant state information.
         */
        suspend fun onConnecting(event: Connecting)

        /**
         * Handles the event when a connection is successfully established.
         *
         * @param event The Connected event containing details about the connection,
         *              such as the role, engagement ID, data retrieval method, state,
         *              and timestamp.
         */
        suspend fun onConnected(event: Connected)

        /**
         * Handles the event when an engagement is canceled.
         *
         * @param event The event containing details about the cancellation, including the role, reason, engagement ID, and state.
         */
        suspend fun onCanceled(event: Canceled)

        /**
         * Handles the event triggered when a disconnected state occurs.
         *
         * @param event The `Disconnected` event containing the details of the disconnection, including the role,
         * reason, engagement ID, state, and timestamp.
         */
        suspend fun onDisconnected(event: Disconnected)

        /**
         * Handles an error event that occurs during the engagement process.
         *
         * @param event The error event containing details such as the role, reason for the error,
         *              the exception (if available), the engagement ID, and the state of the engagement.
         */
        suspend fun onError(event: Error)

        suspend fun onRestApiEngagement(event: RestApiEngagement)
    }

    /**
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("for", exact = true)
     * Default adapter class for handling various engagement events in a listener pattern.
     * This class provides empty default implementations for all methods in the [Listener] interface,
     * allowing subclasses to override only the methods they are interested in.
     */
    abstract class ListenerAdapter : Listener {
        /**
         * Called when an initializing event occurs.
         *
         * @param event The initializing event containing details such as role, engagement ID, state, and timestamp.
         */
        override suspend fun onInitializing(event: Initializing) { // No-op
        }

        /**
         * Handles the QR engagement event.
         *
         * @param event The QR engagement event containing details such as role, QR code data, engagement information, and engagement ID.
         */
        override suspend fun onQrShow(event: QrShow) { // No-op
        }

        /**
         * Handles an event when QR code should be hidden.
         *
         * @param event The QR hide event containing the role and engagement ID.
         */
        override suspend fun onQrHide(event: QrHide) { // No-op
        }

        /**
         * Called when NFC engagement occurs during the process.
         *
         * @param event An instance of [NfcEngagement] that contains details about the NFC engagement.
         */
        override suspend fun onNfcEngagement(event: NfcEngagement) { // No-op
        }

        override suspend fun onRestApiEngagement(event: RestApiEngagement) { // No-op
        }

        /**
         * Invoked when a debug event occurs during the engagement process.
         * This method can handle debug events, allowing the system to process or log debugging information.
         *
         * @param event The debug event containing details such as the message, role, engagement ID,
         * state, and timestamp of the event.
         */
        override suspend fun onDebug(event: Debug) { // No-op
        }

        /**
         * Called when a connection is in the process of being established.
         *
         * @param event The event containing details about the ongoing connection process, such as
         * the engagement role, identifier, engagement ID, data retrieval method, and connection state.
         */
        override suspend fun onConnecting(event: Connecting) { // No-op
        }

        /**
         * Invoked when the engagement reaches the "connected" state.
         *
         * @param event The event representing the connected state, including relevant information such
         * as the role, engagement ID, data retrieval method, and timestamp.
         */
        override suspend fun onConnected(event: Connected) { // No-op
        }

        /**
         * Handles the event when data is transferred during engagement.
         * This method processes the provided data and its associated direction.
         *
         * @param event The data transfer event containing the role, engagement ID, data payload, direction, and state.
         * @param direction The direction of the data transfer, either INCOMING or OUTGOING.
         */
        override suspend fun onData(
            event: Data,
            direction: Data.Direction,
        ) { // No-op
        }

        /**
         * Handles the event when an engagement is canceled.
         *
         * @param event the Canceled event containing details about the canceled engagement,
         * including the role, reason for cancellation (if provided), engagement ID,
         * and the cancellation state.
         */
        override suspend fun onCanceled(event: Canceled) { // No-op
        }

        /**
         * Called when the connection has been disconnected.
         *
         * @param event The Disconnected event containing details about the disconnection,
         * including the role, reason, engagement ID, engagement state, and time of the event.
         */
        override suspend fun onDisconnected(event: Disconnected) { // No-op
        }

        /**
         * Handles an error event during engagement.
         *
         * @param event The error event containing details such as the role, reason,
         *              optional throwable, and engagement ID associated with the error.
         */
        override suspend fun onError(event: Error) { // No-op
        }
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Dispatcher", exact = true)
    interface Dispatcher {
        fun dispatch(event: MdocEngagementEvent)
    }

    /**
     * Defines a mechanism for managing engagement event listeners.
     * It allows adding, removing, and clearing listeners, as well as retrieving the current set of listeners.
     */
    interface Handlers {
        /**
         * Retrieves the current set of engagement event listeners.
         *
         * @return A set of listeners registered for engagement events.
         */
        fun getEngagementEventListeners(): Set<Listener>

        /**
         * Adds one or more engagement event listeners to the current instance.
         *
         * @param listener One or more listeners that will handle engagement events.
         * @return The instance of [Handlers] to allow for method chaining.
         */
        fun addEngagementEventListener(vararg listener: Listener): Handlers

        /**
         * Removes a specified engagement event listener.
         *
         * @param listener The engagement event listener to be removed.
         * @return The instance of Handlers after the listener has been removed.
         */
        fun removeEngagementEventListener(listener: Listener): Handlers

        /**
         * Removes all engagement event listeners previously added to the handlers.
         *
         * @return The instance of Handlers to allow for method chaining.
         */
        fun clearEngagementEventListeners(): Handlers
    }
}

/**
 * Handles the provided engagement event by delegating it to each listener in the collection.
 *
 * @param event The engagement event to be processed by each listener. The event determines
 *              the specific type of action to be executed on the listeners.
 */
suspend fun Collection<MdocEngagementEvent.Listener>.onEngagementEvent(event: MdocEngagementEvent) {
    // Create a copy to avoid ConcurrentModificationException if listeners modify the collection during callbacks
    toList().forEach {
        when (event) {
            is MdocEngagementEvent.Initializing -> {
                it.onInitializing(event)
            }

            is MdocEngagementEvent.Start -> {
                it.onStart(event)
            }

            is MdocEngagementEvent.QrShow -> {
                it.onQrShow(event)
            }

            is MdocEngagementEvent.QrHide -> {
                it.onQrHide(event)
            }

            is MdocEngagementEvent.NfcEngagement -> {
                it.onNfcEngagement(event)
            }

            is MdocEngagementEvent.RestApiEngagement -> {
                it.onRestApiEngagement(event)
            }

            is MdocEngagementEvent.Debug -> {
                it.onDebug(event)
            }

            is MdocEngagementEvent.Connecting -> {
                it.onConnecting(event)
            }

            is MdocEngagementEvent.Connected -> {
                it.onConnected(event)
            }

            is MdocEngagementEvent.Data -> {
                it.onData(event, event.direction)
            }

            is MdocEngagementEvent.Transfer -> {
                it.onTransfer(event)
            }

            is MdocEngagementEvent.Canceled -> {
                it.onCanceled(event)
            }

            is MdocEngagementEvent.Disconnected -> {
                it.onDisconnected(event)
            }

            is MdocEngagementEvent.Error -> {
                it.onError(event)
            }

            is MdocEngagementEvent.AbstractEngagementEvent -> { /* Abstract base type - no handler needed */ }
        }
    }
}
