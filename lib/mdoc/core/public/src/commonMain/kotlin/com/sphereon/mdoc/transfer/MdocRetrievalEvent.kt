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

package com.sphereon.mdoc.transfer

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborBaseItem
import com.sphereon.cbor.CborNull
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.mdoc.MdocEvent
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an event in the transfer session lifecycle. Each event holds specific state information,
 * related data, and a timestamp indicating when the event occurred.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
 * Extends [MdocEvent] to provide a unified interface for all mdoc interaction events.
 */
@OptIn(ExperimentalUuidApi::class)
sealed interface MdocRetrievalEvent : MdocEvent {
    /**
     * The unique identifier of the engagement that initiated this transfer/retrieval.
     * A transfer is always associated with the engagement that led to the retrieval phase.
     * This allows easy navigation from any retrieval event back to its originating engagement.
     */
    val engagementId: Uuid

    /**
     * The engagement event associated with this retrieval event.
     *
     * This provides a direct relationship between retrieval events and their corresponding
     * engagement event, allowing easy access to:
     * - Engagement state (e.g., CONNECTING, CONNECTED, TRANSFER)
     * - Engagement metadata (role, transmission type, etc.)
     * - Active status of the engagement
     *
     * Note: The same engagement event may be associated with multiple retrieval events.
     * For example, an engagement event with state TRANSFER is typically associated with
     * many retrieval events throughout the data exchange process.
     *
     * This duplication is acceptable and provides valuable context for each retrieval event
     * without requiring separate lookups.
     *
     * @return The engagement event that was active when this retrieval event was created,
     *         or null if no engagement event context is available
     */
    val engagementEvent: MdocEngagementEvent?
        get() = null  // Default to null for backward compatibility

    /**
     * Represents a byte array associated with a transfer session event, encapsulating
     * the relevant data being transmitted or processed during the session.
     * This bytearray can be converted into a Cbor object by calling the toCbor() function
     */
    val data: ByteArray

    /**
     * A timestamp representing the date and time associated with a `TransferSessionEvent`.
     */
    override val time: LocalDateTimeKMP

    /**
     * Represents the current state of the transfer session in the context of an mDoc transfer process.
     * This state provides contextual information regarding the progression or phase of the ongoing session.
     */
    override val state: MdocRetrievalStateType




    /**
     * Indicates whether this event originates from the currently active transfer.
     *
     * Only the transfer that has reached TransmissionTypeSelected state is considered active.
     * Other transfers may emit cleanup/termination events, but those should be marked with
     * isActive=false to allow the UI layer (SessionUiProjector) to filter them out and prevent
     * UI updates from non-active transfers.
     *
     * This property is crucial for proper UI state management when multiple transfers exist
     * but only one has progressed to the actual data transmission phase.
     *
     * @return true if this event is from the active transfer, false otherwise
     */
    val isActive: Boolean
        get() = true  // Default to true for backward compatibility

    /**
     * Converts the bytearray in the `TransferSessionEvent` into a CBOR (Concise Binary Object Representation) format.
     *
     * @return A `CborBaseItem` representation of the data associated with the `TransferSessionEvent`.
     */
    fun toCbor(): CborBaseItem


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("AbstractRetrievalEvent", exact = true)
    abstract class AbstractRetrievalEvent : MdocRetrievalEvent {
        protected constructor()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()
        override fun toString(): String {
            return "${this::class.simpleName}(engagementId=$engagementId, time=$time, state=$state, isActive=$isActive, isFinal=$isFinal, data=size(${data.size}))"
        }
    }
    

    /**
     * Represents the initializing state of a transfer session.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Initializing", exact = true)
    class Initializing(override val engagementId: Uuid) : AbstractRetrievalEvent() {
        override val data: ByteArray = byteArrayOf()

        /**
         * Converts the current object into a CBOR (Concise Binary Object Representation) format representation.
         *
         * @return A `CborNull` instance, representing the null value in CBOR format.
         */
        override fun toCbor() = CborNull()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * This is the starting state in the lifecycle of an mDoc transfer session, associated with
         * an ordinal value of 10 within the `MdocTransferSessionState` enum.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.INIT
    }

    /**
     * Represents the initializing state of a transfer session.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("TransmissionTypeSelected", exact = true)
    class TransmissionTypeSelected(override val engagementId: Uuid, val deviceRetrievalMethod: DeviceRetrievalMethod) : AbstractRetrievalEvent() {
        override val data: ByteArray = CborNull().encodeCbor()

        /**
         * Converts the current object into a CBOR (Concise Binary Object Representation) format representation.
         *
         * @return A `CborNull` instance, representing the null value in CBOR format.
         */
        override fun toCbor() = CborNull()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * This is the starting state in the lifecycle of an mDoc transfer session, associated with
         * an ordinal value of 10 within the `MdocTransferSessionState` enum.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.TRANSMISSION_TYPE_SELECTED
    }

    /**
     * Represents an event indicating that a session establishment message has been received from the Mdoc Reader
     *
     * @property data The raw CBOR-encoded byte array representing the session establishment data.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("SessionEstablishmentReceived", exact = true)
    data class SessionEstablishmentReceived(override val engagementId: Uuid, override val data: ByteArray) : AbstractRetrievalEvent() {
        /**
         * This method decodes the raw byte array data provided in the session establishment event
         * into a structured `SessionEstablishment` instance using predefined decoding logic.
         *
         * @return The `SessionEstablishment` object decoded from the byte array data.
         */
        override fun toCbor() = SessionEstablishment.Decoder.decodeCbor(data)
        override val time: LocalDateTimeKMP = DateTimeUtils.DEFAULTS.dateTimeLocal()

        /**
         * In this state:
         * - The mdoc holder does not validate but extracts the `DeviceRequest` object from the session establishment data.
         * - Initiates mdoc selection for subsequent processing.
         *
         * This state corresponds to the `SESSION_ESTABLISHMENT_RECEIVED` enumeration value of [MdocRetrievalState],
         * defined with an order value of 30.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SessionEstablishmentReceived
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     * Represents a device request event in the session establishment phase.
     * The device request has been extracted from the session establishment encrypted message
     *
     * @property data Encoded CBOR data representing the device request.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("DeviceRequestReady", exact = true)
    data class DeviceRequestReady(override val engagementId: Uuid, override val data: ByteArray) : AbstractRetrievalEvent() {
        /**
         * Decodes the `data` property of the instance into a `DeviceRequest` object.
         *
         * @return The decoded `DeviceRequest` object derived from the `data` property.
         */
        override fun toCbor() = DeviceRequest.Decoder.decodeCbor(data)
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * The state `SESSION_ESTABLISHMENT_RECEIVED` indicates that the session establishment data has been received
         * from the mdoc reader. At this point in the session:
         * - The initial engagement has been completed.
         * - The encrypted session establishment data has been transmitted by the reader.
         * - The mdoc (holder) will now perform validation and prepare for mdoc selection.
         *
         * This state is an integral part of the session's progression, and its precise order in the session lifecycle is defined as 30.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as DeviceRequestReady
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     * Represents the event that signifies the start of the document selection process during a transfer session.
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("and", exact = true)
     * It is derived from the `TransferSessionEvent` base class and includes session-specific metadata and functionalities.
     */
    data class DocumentsSelectionProcessStart(override val engagementId: Uuid, override val data: ByteArray) : AbstractRetrievalEvent() {
        /**
         * Converts the associated `data` property of the class into a `DeviceRequest` object using the CBOR decoding
         * mechanism provided by `DeviceRequest.decodeCbor`.
         *
         * @return The decoded `DeviceRequest` instance which represents the structured representation of the `data`
         * in CBOR format.
         */
        override fun toCbor() = DeviceRequest.Decoder.decodeCbor(data)
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * The selection process typically engages the user, allowing them to choose the required documents to proceed.
         *
         * This state is part of the `MdocTransferSessionState` enumeration, which categorizes the different phases within an
         * mDoc transfer session, each with a unique order defining its progression.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_START
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as DocumentsSelectionProcessStart
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     * Represents an event indicating that the document selection process
     * within an mdoc transfer session has been successfully accepted.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("DocumentsSelectionProcessAccepted", exact = true)
    data class DocumentsSelectionProcessAccepted(override val engagementId: Uuid, override val data: ByteArray) : AbstractRetrievalEvent() {
        /**
         * Converts the `data` property of the implementing class into an instance of `DeviceResponse`.
         *
         * @return A `DeviceResponse` instance representing the decoded CBOR data.
         */
        override fun toCbor() = DeviceResponse.Decoder.decodeCbor(data)
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * Represents the current state of the transfer session as `DOCUMENTS_SELECTION_PROCESS_ACCEPTED`.
         *
         * This state is part of the overall lifecycle of an mDoc transfer session, where progression
         * through states such as initialization, document selection, and finalization is determined.
         *
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_ACCEPTED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as DocumentsSelectionProcessAccepted
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     * Represents the event triggered when the document selection process is declined during an mDoc transfer session.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("DocumentsSelectionProcessDeclined", exact = true)
    data class DocumentsSelectionProcessDeclined(override val engagementId: Uuid, override val data: ByteArray) : AbstractRetrievalEvent() {
        /**
         * This implementation returns a `CborNull` object, representing a null value in CBOR format.
         *
         * @return A `CborNull` instance.
         */
        override fun toCbor() = CborNull()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * Represents the current state of the transfer session, indicating that the document selection process was started
         * but ultimately declined by the user. This state signifies that the mdoc will return an error back to the reader.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_DECLINED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as DocumentsSelectionProcessDeclined
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("representing", exact = true)
     * Data class representing a session data transfer event.
     * Inherits from the TransferSessionEvent interface.
     */
    data class SessionDataSend(override val engagementId: Uuid, override val data: ByteArray, val deviceResponse: DeviceResponse? = null) : AbstractRetrievalEvent() {
        /**
         * Converts the `data` property of the implementing class into a `DeviceResponse` object.
         *
         * @return A `DeviceResponse` object decoded from the `data` byte array.
         */
        override fun toCbor() = Cbor.decode<CborItem<*>>(data)
        fun toDeviceResponse(): DeviceResponse? =  deviceResponse
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * Represents the state of the current transfer session as `SESSION_DATA_SEND`.
         * This state signifies that session data, including the `DeviceResponse`,
         * is being sent to the mdoc reader during the mdoc transfer process.
         *
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.SESSION_DATA_SEND
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SessionDataSend
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (deviceResponse != other.deviceResponse) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + (deviceResponse?.hashCode() ?: 0)
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("representing", exact = true)
     * Data class representing a session data transfer event.
     * Inherits from the TransferSessionEvent interface.
     */
    data class SessionDataReceived(override val engagementId: Uuid, override val data: ByteArray, val deviceRequest: DeviceRequest? = null) : AbstractRetrievalEvent() {
        /**
         * Converts the `data` property of the implementing class into a `DeviceResponse` object.
         *
         * @return A `DeviceResponse` object decoded from the `data` byte array.
         */
        override fun toCbor() = Cbor.decode<CborItem<*>>(data)
        fun toDeviceRequest(): DeviceRequest? =  deviceRequest
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * Represents the state of the current transfer session as `SESSION_DATA_SEND`.
         * This state signifies that session data, including the `DeviceResponse`,
         * is being sent to the mdoc reader during the mdoc transfer process.
         *
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.SESSION_DATA_SEND
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SessionDataReceived
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (deviceRequest != other.deviceRequest) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + (deviceRequest?.hashCode() ?: 0)
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     * Represents an event that signifies the termination of a session in the mDoc transfer process.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("SessionTerminationSend", exact = true)
    data class SessionTerminationSend(override val engagementId: Uuid, override val data: ByteArray = CborNull().encodeCbor()) : AbstractRetrievalEvent() {
        /**
         * Converts the current object representation into CBOR (Concise Binary Object Representation).
         *
         * @return A `CborNull` instance representing a null value in CBOR format.
         */
        override fun toCbor() = CborNull()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * The `TERMINATED` state indicates the final and irreversible condition of the session
         * lifecycle, where no further actions or transitions can occur. This state is typically reached
         * after completion or upon a failure that necessitates ending the session.
         *
         * Associated with an order value of 100, `TERMINATED` serves as the endpoint in the sequence
         * of session states, providing a clear signal of the session's conclusion.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.TERMINATED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SessionTerminationSend
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return super.toString()
        }
    }


    /**
     * Represents an event in which a session termination has been received during a transfer session.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("SessionTerminationReceived", exact = true)
    data class SessionTerminationReceived(override val engagementId: Uuid, override val data: ByteArray = CborNull().encodeCbor()) : AbstractRetrievalEvent() {
        /**
         *
         * @return The CBOR representation of the object as `CborNull`.
         */
        override fun toCbor() = CborNull()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * Represents the state of the session as `TERMINATED` within the context of an mDoc transfer session lifecycle.
         * This is the final state of the session, indicating that the session has been completed or terminated
         * successfully or due to failure. Once the session is in this state, no further operations can be performed.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.TERMINATED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SessionTerminationReceived
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return super.toString()
        }
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Terminated", exact = true)
    data class Terminated(override val engagementId: Uuid, override val data: ByteArray = CborNull().encodeCbor()): AbstractRetrievalEvent() {
        override fun toCbor() = CborNull()
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()
        override val state: MdocRetrievalStateType = MdocRetrievalState.TERMINATED
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Terminated
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return super.toString()
        }
    }

    /**
     * Represents an error event in an mDoc transfer session.
     *
     * This event corresponds to the `ERROR` state in the transfer session lifecycle.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Error", exact = true)
    data class Error(override val engagementId: Uuid, override val data: ByteArray, val error: Throwable) : AbstractRetrievalEvent() {
        /**
         * Converts the current object to its CBOR (Concise Binary Object Representation) format.
         *
         * @return A CBOR representation of the object as a `CborNull`.
         */
        override fun toCbor() = CborNull()

        /**
         * Represents the timestamp associated with the event.
         *
         * This property holds the date and time when the event occurred, using
         * `LocalDateTimeKMP` for cross-platform date-time representation.
         * The timestamp is initialized with the current local date and time
         * provided by `DateTimeUtils.DEFAULT.dateTimeLocal()`.
         */
        override val time: LocalDateTimeKMP = DateTimeUtils.Companion.DEFAULTS.dateTimeLocal()

        /**
         * Represents the state of the session in the TransferSessionEvent when an error occurs.
         *
         * This property is set to [MdocRetrievalState.ERROR], which denotes a failure scenario
         * in the mDoc transfer session. Being in this state indicates that an error has been encountered,
         * halting further progress.
         *
         * Typically used in scenarios requiring cleanup, error handling, or session termination.
         */
        override val state: MdocRetrievalStateType = MdocRetrievalState.ERROR
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Error
            if (engagementId != other.engagementId) return false

            if (!data.contentEquals(other.data)) return false
            if (error != other.error) return false
            if (time != other.time) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = engagementId.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + error.hashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }
        override fun toString(): String {
            return super.toString()
        }
    }


    /**
     * Interface that defines listeners for various events occurring during a transfer session.
     * Implementers can handle specific types of events by defining the corresponding methods.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Listener", exact = true)
    interface Listener {
        /**
         * Invoked when an initializing event occurs.
         *
         * @param event The event containing information about the initializing state.
         */
        suspend fun onInitializing(event: Initializing)

        suspend fun onTransmissionTypeSelected(event: TransmissionTypeSelected)

        /**
         * Invoked when a session establishment message is received.
         *
         * @param event The event containing session establishment data.
         */
        suspend fun onSessionEstablishmentReceived(event: SessionEstablishmentReceived)

        /**
         * Invoked when a device request is ready.
         *
         * @param event The event containing device request data.
         */
        suspend fun onDeviceRequestReady(event: DeviceRequestReady)

        /**
         * Invoked when the documents selection process starts.
         *
         * @param event The event containing the start of document selection process.
         */
        suspend fun onDocumentsSelectionProcessStart(event: DocumentsSelectionProcessStart)

        /**
         * Invoked when the documents selection process is accepted.
         *
         * @param event The event containing the accepted document selection.
         */
        suspend fun onDocumentsSelectionProcessAccepted(event: DocumentsSelectionProcessAccepted)

        /**
         * Invoked when the documents selection process is declined.
         *
         * @param event The event containing the declined document selection.
         */
        suspend fun onDocumentsSelectionProcessDeclined(event: DocumentsSelectionProcessDeclined)

        /**
         * Invoked when session data is being sent.
         *
         * @param event The event containing session data being sent.
         */
        suspend fun onSessionDataSend(event: SessionDataSend)

        suspend fun onSessionDataReceived(event: SessionDataReceived)

        /**
         * Invoked when session termination is being sent.
         *
         * @param event The event containing session termination data.
         */
        suspend fun onSessionTerminationSend(event: SessionTerminationSend)

        /**
         * Invoked when session termination is received.
         *
         * @param event The event containing received session termination.
         */
        suspend fun onSessionTerminationReceived(event: SessionTerminationReceived)

        /**
         * Invoked when an error occurs during the transfer session.
         *
         * @param event The event containing error information.
         */
        suspend fun onError(event: Error)
        suspend fun onTerminated(event: Terminated)

    }

    /**
     * Provides functionality to register and unregister listeners for transfer session events.
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("allows", exact = true)
     * This interface allows for managing multiple listeners that will be notified of events
     * during the transfer session lifecycle.
     */
    interface Handlers {

        /**
         * Retrieves the current set of engagement event listeners.
         *
         * @return A set of listeners registered for engagement events.
         */
        fun getRetrievalEventListeners(): Set<Listener>

        /**
         * Registers a listener to receive notifications about transfer session events.
         *
         * @param listener The listener to be registered for receiving event notifications.
         */
        fun addRetrievalEventListener(vararg listener: Listener)

        /**
         * Unregisters a previously registered listener from receiving transfer session events.
         *
         * @param listener The listener to be unregistered.
         */
        fun removeRetrievalEventListener(listener: Listener)

        /**
         * Removes all registered listeners from receiving transfer session events.
         */
        fun clearRetrievalEventListeners()
    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Dispatcher", exact = true)
    interface Dispatcher {
        fun dispatch(event: MdocRetrievalEvent)
    }

}

/**
 * Handles the provided retrieval event by delegating it to each listener in the collection.
 *
 * @param event The retrieval event to be processed by each listener. The event determines
 *              the specific type of action to be executed on the listeners.
 */
suspend fun Collection<MdocRetrievalEvent.Listener>.onRetrievalEvent(event: MdocRetrievalEvent) {
    // Create a copy to avoid ConcurrentModificationException if listeners modify the collection during callbacks
    toList().forEach {
        when (event) {
            is MdocRetrievalEvent.Initializing -> it.onInitializing(event)
            is MdocRetrievalEvent.TransmissionTypeSelected -> it.onTransmissionTypeSelected(event)
            is MdocRetrievalEvent.SessionEstablishmentReceived -> it.onSessionEstablishmentReceived(event)
            is MdocRetrievalEvent.DeviceRequestReady -> it.onDeviceRequestReady(event)
            is MdocRetrievalEvent.DocumentsSelectionProcessStart -> it.onDocumentsSelectionProcessStart(event)
            is MdocRetrievalEvent.DocumentsSelectionProcessAccepted -> it.onDocumentsSelectionProcessAccepted(event)
            is MdocRetrievalEvent.DocumentsSelectionProcessDeclined -> it.onDocumentsSelectionProcessDeclined(event)
            is MdocRetrievalEvent.SessionDataSend -> it.onSessionDataSend(event)
            is MdocRetrievalEvent.SessionDataReceived -> it.onSessionDataReceived(event)
            is MdocRetrievalEvent.SessionTerminationSend -> it.onSessionTerminationSend(event)
            is MdocRetrievalEvent.SessionTerminationReceived -> it.onSessionTerminationReceived(event)
            is MdocRetrievalEvent.Terminated -> it.onTerminated(event)
            is MdocRetrievalEvent.Error -> it.onError(event)
            is MdocRetrievalEventWithEngagement -> {
                // Unwrap and dispatch the delegate event
                onRetrievalEvent(event.delegate)
            }

            else -> {print("AbstractTransferSessionEvent not handled in dispatcher")}
        }
    }
}

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("that", exact = true)
 * Wrapper class that adds engagement event context to a retrieval event.
 * This class is used in the datatransfer module to associate retrieval events
 * with their corresponding engagement events.
 *
 * Since this wrapper is defined in the same module as MdocRetrievalEvent,
 * it can properly implement the sealed interface.
 *
 * @param delegate The original retrieval event to wrap
 * @param engagementEvent The engagement event to associate with this retrieval event
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocRetrievalEventWithEngagement", exact = true)
data class MdocRetrievalEventWithEngagement(
    val delegate: MdocRetrievalEvent,
    override val engagementEvent: MdocEngagementEvent
) : MdocRetrievalEvent by delegate {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MdocRetrievalEventWithEngagement

        if (delegate != other.delegate) return false
        if (engagementEvent != other.engagementEvent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + engagementEvent.hashCode()
        return result
    }

    override fun toString(): String {
        return "MdocRetrievalEventWithEngagement(delegate=$delegate, engagementEvent=$engagementEvent)"
    }


}
