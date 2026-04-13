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
import com.sphereon.cbor.dsl.cborArray
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.encodeToHex
import com.sphereon.data.link.nfc.model.CommandApdu
import com.sphereon.data.link.nfc.model.HandoverRequestRecord
import com.sphereon.data.link.nfc.model.NdefMessage
import com.sphereon.data.link.nfc.model.NdefRecord
import com.sphereon.data.link.nfc.model.NfcConst
import com.sphereon.data.link.nfc.model.ResponseApdu
import com.sphereon.data.link.nfc.model.ServiceParameterRecord
import com.sphereon.data.link.nfc.model.ServiceSelectRecord
import com.sphereon.data.link.nfc.model.TnepStatusRecord
import com.sphereon.di.session.SessionGraph
import com.sphereon.mdoc.HandoverCborCodec
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.engagement.NfcEngagementMethod
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.NfcHandover
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.util.getCurrentTimeMillis
import com.sphereon.util.getUInt16
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.encodeToByteString

// Note: NegotiatedHandoverState, StaticHandoverState, MdocNfcConstants, and NfcHelperState
// are now defined in separate files for better organization

/**
 * Helper used for NFC engagement on the mdoc side.
 *
 * This implements NFC engagement according to ISO/IEC 18013-5:2021 and provides
 * a robust interface for handling NFC APDU commands during the engagement process.
 *
 * ## Features:
 * - Static handover support with predefined connection methods
 * - Negotiated handover support with dynamic method selection
 * - Comprehensive error handling and validation
 * - Thread-safe state management
 * - Detailed logging for debugging
 *
 * ## Usage:
 * ```kotlin
 * val helper = MdocNfcEngagementHelper(
 *     instance = engagementInstance,
 *     onHandoverComplete = { instance, handover ->
 *         // Handle successful handover
 *     },
 *     onError = { error ->
 *         // Handle errors
 *     },
 *     negotiatedHandoverPicker = { methods ->
 *         // Select preferred method
 *         methods.first()
 *     },
 *     sessionGraph = sessionGraph
 * )
 *
 * // Process incoming APDUs
 * val response = helper.processApdu(commandApdu)
 * ```
 *
 * @param instance The engagement instance containing device keys and configuration
 * @param onHandoverComplete Callback invoked when handover completes successfully
 * @param onError Callback invoked when an error occurs during processing
 * @param negotiatedHandoverPicker Optional function to select connection method during negotiated handover.
 *        If null, only static handover is supported.
 * @param sessionGraph Session graph for dependency injection and logging
 *
 * @throws IllegalArgumentException if neither static nor negotiated handover is configured
 * @throws IllegalArgumentException if both static and negotiated handover are configured simultaneously
 * @throws IllegalStateException if required components are not properly initialized
 */
class MdocNfcEngagementHelper(
    val instance: EngagementInstance,
    val onHandoverComplete: suspend (
        engagementInstance: EngagementInstance,
        handover: CborItem<*>,
    ) -> Unit,
    val onError: (error: Throwable) -> Unit,
    val negotiatedHandoverPicker: ((connectionMethods: List<ConnectionMethod>) -> ConnectionMethod)? = null,
    val sessionGraph: SessionGraph,
    val handoverCborCodec: HandoverCborCodec,
    val bleHandoverMapper: BleHandoverMapper? = null,
) {
    private val log = sessionGraph.logManager.withTag(TAG)

    companion object {
        private const val TAG = "MdocNfcEngagementHelper"
    }

    /**
     * Thread-safe state holder using atomic operations.
     * All mutable state is encapsulated in NfcHelperState for consistency.
     */
    private val stateHolder = NfcHelperStateHolder()

    /** Handler for UPDATE BINARY chunk assembly */
    private val updateBinaryHandler = UpdateBinaryHandler()

    /** Builder for Handover Select messages */
    private val handoverSelectBuilder =
        HandoverSelectMessageBuilder(
            logCallback = { message -> log.debug("HandoverSelectBuilder: $message") },
            bleHandoverMapper = bleHandoverMapper,
        )

    // Convenience accessors for frequently used state properties
    private val state: NfcHelperState get() = stateHolder.value
    private val inError: Boolean get() = state.inError
    private val ndefApplicationSelected: Boolean get() = state.ndefApplicationSelected
    private val selectedFileId: Int get() = state.selectedFileId
    private val selectedFilePayload: ByteString get() = state.selectedFilePayload
    private val negotiatedHandoverState: NegotiatedHandoverState get() = state.negotiatedHandoverState
    private val staticHandoverState: StaticHandoverState get() = state.staticHandoverState
    private val pendingStaticHandover: CborItem<*>? get() = state.pendingStaticHandover
    private val handoverCompleteCalled: Boolean get() = state.handoverCompleteCalled
    private val expectedReadSize: Int get() = state.expectedReadSize
    private val totalBytesRead: Int get() = state.totalBytesRead

    init {
        log.info("Initializing MdocNfcEngagementHelper with instance ${instance.id}")

        val hasStaticHandover =
            instance
                .getEngagementMethods()
                .any { method -> method is NfcEngagementMethod }
        val hasNegotiatedHandover = negotiatedHandoverPicker != null

        require(hasStaticHandover || hasNegotiatedHandover) {
            "Must configure either static or negotiated handover. " +
                "Provide NFC engagement methods for static handover or negotiatedHandoverPicker for negotiated handover."
        }

        require(!(hasStaticHandover && hasNegotiatedHandover)) {
            "Cannot use both static and negotiated handover simultaneously. Choose one approach."
        }

        if (hasStaticHandover) {
            val nfcMethods = instance.getEngagementMethods().filterIsInstance<NfcEngagementMethod>()
            require(nfcMethods.isNotEmpty()) {
                "Static handover requires at least one NFC engagement method"
            }
            log.info("Configured for static handover with ${nfcMethods.size} NFC methods")
        } else {
            log.info("Configured for negotiated handover")
        }
    }

    /**
     * Safely raises an error and transitions to error state
     */
    private fun raiseError(
        errorMessage: String,
        cause: Throwable? = null,
    ) {
        log.error(errorMessage, exception = cause)
        stateHolder.update { it.toError() }
        onError(Error(errorMessage, cause))
    }

    /**
     * Processes SELECT APPLICATION command
     */
    private suspend fun processSelectApplication(command: CommandApdu): ResponseApdu {
        log.debug("Processing SELECT APPLICATION command")

        val requestedApplicationId = command.payload
        return if (requestedApplicationId == NfcConst.NDEF_APPLICATION_ID) {
            stateHolder.update { it.withNdefApplicationSelected() }
            log.info("NDEF application selected successfully")
            ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS)
        } else {
            val errorMsg = "SELECT APPLICATION: Expected NDEF AID but got ${requestedApplicationId.toByteArray().encodeToHex()}"
            raiseError(errorMsg)
            ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }
    }

    /**
     * Generates capability container file content
     */
    private fun generateCapabilityContainerFile(): ByteString {
        val fileWriteAccessCondition =
            if (negotiatedHandoverPicker != null) {
                MdocNfcConstants.FILE_WRITE_ACCESS_ALLOW
            } else {
                MdocNfcConstants.FILE_WRITE_ACCESS_DENY
            }

        return ByteString(
            byteArrayOf(
                0x00,
                0x0f,
                0x20,
                0x7f,
                0xff.toByte(),
                0x7f,
                0xff.toByte(),
                0x04,
                0x06,
                0xe1.toByte(),
                0x04,
                0x7f,
                0xff.toByte(),
                MdocNfcConstants.FILE_READ_ACCESS_ALLOW, // file read access condition
                fileWriteAccessCondition, // file write access condition
            ),
        )
    }

    /**
     * Processes SELECT FILE command
     */
    private suspend fun processSelectFile(command: CommandApdu): ResponseApdu {
        if (!ndefApplicationSelected) {
            raiseError("NDEF application must be selected before file operations")
            return ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }

        val fileId = command.payload.getUInt16(0).toInt()

        when (fileId) {
            NfcConst.NDEF_CAPABILITY_CONTAINER_FILE_ID -> {
                val payload = generateCapabilityContainerFile()
                stateHolder.update { it.withSelectedFile(fileId, payload) }
            }

            MdocNfcConstants.NDEF_DATA_FILE_ID -> {
                if (negotiatedHandoverPicker != null) {
                    log.info("SelectFile: Using negotiated handover, sending Handover Request message")
                    val initialNdefMessage =
                        NdefMessage(
                            records =
                                listOf(
                                    ServiceParameterRecord(
                                        tnepVersion = MdocNfcConstants.TNEP_VERSION,
                                        serviceNameUri = NfcConst.SERVICE_NAME_CONNECTION_HANDOVER,
                                        tnepCommunicationMode = MdocNfcConstants.TNEP_COMMUNICATION_MODE,
                                        wtInt = MdocNfcConstants.WT_INT,
                                        nWait = MdocNfcConstants.N_WAIT,
                                        maxNdefSize = MdocNfcConstants.MAX_NDEF_SIZE,
                                    ).generateNdefRecord(),
                                ),
                        )
                    val initialNdefMessagePayload = initialNdefMessage.encode()
                    val bsb = ByteStringBuilder()
                    bsb.append((initialNdefMessagePayload.size / 0x100).and(0xff).toByte())
                    bsb.append(initialNdefMessagePayload.size.and(0xff).toByte())
                    bsb.append(initialNdefMessagePayload)
                    val payload = bsb.toByteString()
                    stateHolder.update { current ->
                        current
                            .withSelectedFile(fileId, payload)
                            .withNegotiatedHandoverState(NegotiatedHandoverState.EXPECT_SERVICE_SELECT)
                    }
                } else {
                    // Static handover: Prepare DeviceEngagement but DON'T invoke callback yet.
                    // The callback will be invoked in processReadBinary() AFTER the reader
                    // has actually read the handover data. This ensures short NFC taps
                    // don't result in a callback being invoked when the reader never
                    // received the data.
                    //
                    // BLE setup runs in parallel - by the time the reader processes the NFC response,
                    // scans for our UUID, and connects, BLE will be ready. Stale connections from
                    // previous sessions are rejected (before advertising starts) so the reader
                    // must scan fresh after receiving the UUID via NFC.
                    log.info("SelectFile: Using static handover, generating Handover Select message (deferred callback)")
                    val handoverSelectMessage =
                        generateHandoverSelectMessage(
                            engagementInstance = instance,
                            skipUuids = false,
                        )
                    val hsPayload = handoverSelectMessage.encode()

                    val handover =
                        cborArray {
                            add(hsPayload) // Handover Select message
                            addNull() // Handover Request message
                        }

                    @Suppress("UNCHECKED_CAST")
                    val encodedHandover =
                        NfcHandover(
                            handoverSelectMessage = hsPayload,
                            handoverRequestMessage = null,
                        ) as Handover<*, CborItem<*>>
                    instance.handover = handoverCborCodec.encode(encodedHandover).getOrThrow()

                    val bsb = ByteStringBuilder()
                    bsb.append((hsPayload.size / 0x100).and(0xff).toByte())
                    bsb.append(hsPayload.size.and(0xff).toByte())
                    bsb.append(hsPayload)
                    val payload = bsb.toByteString()

                    // Atomically update all state: file selection + pending handover
                    stateHolder.update { current ->
                        current
                            .withSelectedFile(fileId, payload)
                            .withPendingStaticHandover(handover, payload.size)
                    }

                    log.info("SelectFile: Handover Select payload prepared (${payload.size} bytes), awaiting READ BINARY")
                }
            }

            else -> {
                stateHolder.update { it.withSelectedFile(fileId, ByteString()) }
                raiseError("SelectFile: Unexpected File ID $fileId")
                return ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
            }
        }
        log.info("SelectFile: Selected file ID $fileId returning success status")
        return ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS)
    }

    /**
     * Processes READ BINARY command
     */
    private suspend fun processReadBinary(command: CommandApdu): ResponseApdu {
        // Get current state snapshot for consistent reads
        val currentState = state

        if (!currentState.hasFileSelected) {
            raiseError("No file selected")
            return ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }

        val offset = command.p1 * 0x100 + command.p2
        val length = command.le
        val data = currentState.selectedFilePayload.substring(offset, offset + length)
        log.info("ReadBinary: offset $offset length $length returning ${data.toByteArray().encodeToHex()}")

        val response = ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS, data)

        // Track bytes read for static handover completion detection
        if (currentState.selectedFileId == MdocNfcConstants.NDEF_DATA_FILE_ID &&
            currentState.pendingStaticHandover != null
        ) {
            val newBytesRead = offset + length
            log.debug("ReadBinary: Static handover - read $newBytesRead of ${currentState.expectedReadSize} bytes")

            // Check if the reader has read all the handover data
            // The reader has fully read the data when it has read up to or beyond the expected size
            if (newBytesRead >= currentState.expectedReadSize && !currentState.handoverCompleteCalled) {
                log.info("ReadBinary: Reader has received complete handover data, invoking callback")

                // Atomically update state: mark as read, then complete
                val (oldState, newState) =
                    stateHolder.getAndUpdate { s ->
                        s
                            .withBytesRead(newBytesRead)
                            .withStaticHandoverRead()
                            .withHandoverComplete()
                    }

                // Only invoke callback if we actually transitioned
                if (!oldState.handoverCompleteCalled) {
                    try {
                        onHandoverComplete(instance, currentState.pendingStaticHandover)
                        log.info("ReadBinary: Handover complete callback invoked successfully")
                    } catch (expected: Exception) {
                        stateHolder.update { it.withStaticHandoverError() }
                        raiseError("Error invoking handover complete callback: ${expected.message}", expected)
                    }
                }
            } else {
                // Just update bytes read
                stateHolder.update { it.withBytesRead(newBytesRead) }
            }
        }

        return response
    }

    /**
     * Processes UPDATE BINARY command using the UpdateBinaryHandler.
     *
     * This delegates chunk assembly to UpdateBinaryHandler and processes
     * complete NDEF messages when all chunks are received.
     */
    private suspend fun processUpdateBinaryCommand(command: CommandApdu): ResponseApdu {
        val offset = command.p1 * 0x100 + command.p2
        val data = command.payload

        return try {
            when (val result = updateBinaryHandler.processChunk(offset, data)) {
                is UpdateBinaryResult.Continue -> {
                    updateBinarySuccessResponse()
                }

                is UpdateBinaryResult.Complete -> {
                    processCompletedNdefMessage(result.message)
                }

                is UpdateBinaryResult.Error -> {
                    raiseError("UPDATE BINARY: ${result.message}")
                    updateBinaryErrorResponse()
                }
            }
        } catch (error: Exception) {
            raiseError("Error in UPDATE BINARY operation: ${error.message}", error)
            ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }
    }

    /**
     * Processes a completed NDEF message received during UPDATE BINARY.
     */
    private suspend fun processCompletedNdefMessage(message: NdefMessage): ResponseApdu {
        if (!state.hasFileSelected) {
            raiseError("No file selected")
            return ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }
        try {
            val responseNdefMessage = ndefTransactHandler(message)
            val responseNdefMessagePayload = responseNdefMessage.encode()
            val bsb = ByteStringBuilder()
            bsb.append((responseNdefMessagePayload.size / 0x100).and(0xff).toByte())
            bsb.append(responseNdefMessagePayload.size.and(0xff).toByte())
            bsb.append(responseNdefMessagePayload)
            val newPayload = bsb.toByteString()
            stateHolder.update { current ->
                current.copy(selectedFilePayload = newPayload)
            }
            return ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS)
        } catch (error: Throwable) {
            raiseError(error.message!!, error)
            return ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }
    }

    /**
     * Transacts an NDEF message
     */
    private suspend fun ndefTransactHandler(message: NdefMessage): NdefMessage =
        when (negotiatedHandoverState) {
            NegotiatedHandoverState.NOT_STARTED -> throw Error("Unexpected message - Negotiated Handover not started")
            NegotiatedHandoverState.EXPECT_SERVICE_SELECT -> ndefTransactNegotiatedHandleServiceSelect(message)
            NegotiatedHandoverState.EXPECT_HANDOVER_REQUEST_MESSAGE -> ndefTransactNegotiatedHandleHandoverRequest(message)
            NegotiatedHandoverState.HANDOVER_COMPLETE -> throw Error("Negotiated Handover is complete")
        }

    /**
     * Handles a Service Select message during negotiated handover
     */
    private suspend fun ndefTransactNegotiatedHandleServiceSelect(message: NdefMessage): NdefMessage {
        check(message.records.size == 1) { "Expected just a single record for service select" }
        val serviceSelectRecord =
            ServiceSelectRecord.fromNdefRecord(message.records[0])
                ?: throw Error("Service Select record not found")
        check(serviceSelectRecord.serviceName == NfcConst.SERVICE_NAME_CONNECTION_HANDOVER) {
            "Expected service ${NfcConst.SERVICE_NAME_CONNECTION_HANDOVER} found ${serviceSelectRecord.serviceName}"
        }

        // From NDEF Exchange Protocol 1.0: 4.3 TNEP Status Message
        // If the NFC Tag Device has received a Service Select Message with a known
        // Service, it will return a TNEP Status Message to confirm a successful
        // Service selection.
        //
        stateHolder.update { it.withNegotiatedHandoverState(NegotiatedHandoverState.EXPECT_HANDOVER_REQUEST_MESSAGE) }
        return NdefMessage(listOf(TnepStatusRecord(0).toNdefRecord()))
    }

    /**
     * Handles a Handover Request message during negotiated handover
     */
    private suspend fun ndefTransactNegotiatedHandleHandoverRequest(message: NdefMessage): NdefMessage {
        // Handover Request Record must be the first record in Handover Request Message..
        val hrRecord =
            HandoverRequestRecord.fromNdefRecord(message.records[0])
                ?: throw Error("Handover Request Record not the first in message")
        check(hrRecord.version == MdocNfcConstants.CONNECTION_HANDOVER_VERSION) {
            "Expected Connection Handover version ${MdocNfcConstants.CONNECTION_HANDOVER_VERSION}, got ${byteArrayOf(hrRecord.version.toByte()).encodeToHex()}"
        }

        val availableConnectionMethods = mutableListOf<ConnectionMethod>()
        for (record in message.records.subList(1, message.records.size)) {
            val result =
                connectionMethodFromNdef(
                    record = record,
                    role = MdocRole.MDOC_READER,
                    uuid = null,
                    bleHandoverMapper = bleHandoverMapper,
                )
            if (result.supported && result.connectionMethod != null) {
                availableConnectionMethods.add(result.connectionMethod)
            }
        }
        if (availableConnectionMethods.isEmpty()) {
            throw Error("No supported connection methods found in Handover Request method")
        }
        val disambiguatedConnectionMethods =
            disambiguateConnectionMethods(
                availableConnectionMethods,
                MdocRole.MDOC,
                bleHandoverMapper = bleHandoverMapper,
            )

        val selectedMethod = negotiatedHandoverPicker!!(disambiguatedConnectionMethods)

        // Handover Select message is defined in section 5.2 Handover Select Message
        //
        // When doing Negotiated Handover, the standard says to don't include the UUIDs in Handover Select
        // message for mdoc central client mode:
        //
        //   The following requirements apply for including the UUID field during NFC device engagement:
        //
        //     — for Negotiated Handover, if the mdoc reader supports mdoc central client mode, it shall include a
        //       UUID in the Handover Request message, to be used for mdoc central client mode;
        //     — for Negotiated Handover, if the mdoc chooses to use mdoc peripheral server mode, it shall include a
        //       UUID in the Handover Select message, to be used for mdoc peripheral server mode;
        //     — for Static Handover, the mdoc shall send one UUID in the handover select message, to be used for
        //       mdoc central client mode, mdoc peripheral server mode or both.
        //
        // Reference: ISO/IEC 18013-5:2021 clause 8.3.3.1.1.2 Device engagement contents
        //
        val skipUuids = bleHandoverMapper?.shouldSkipUuids(selectedMethod) == true
        val handoverSelectMessage =
            generateHandoverSelectMessage(
                engagementInstance = instance,
                skipUuids = skipUuids,
            )

        val handover =
            cborArray {
                add(handoverSelectMessage.encode()) // Handover Select message
                add(message.encode()) // Handover Request message
            }

        // Atomically mark handover as complete
        stateHolder.update { current ->
            current
                .withNegotiatedHandoverState(NegotiatedHandoverState.HANDOVER_COMPLETE)
                .withHandoverComplete()
        }

        onHandoverComplete(
            instance,
            handover,
        )

        return handoverSelectMessage
    }

    /**
     * Generates a Handover Select message using the builder.
     */
    private fun generateHandoverSelectMessage(
        engagementInstance: EngagementInstance,
        skipUuids: Boolean,
    ): NdefMessage {
        log.info("==============================================================")
        log.info("Device engagement in NFC session: ${engagementInstance.id}:")
        log.info(
            engagementInstance
                .getDeviceEngagement()
                .value.taggedItem.value
                .encodeTo(Encoding.HEX),
        )
        log.info("==============================================================")

        val result = handoverSelectBuilder.build(engagementInstance, skipUuids)
        return result.message
    }

    /**
     * Process APDUs received from the remote NFC tag reader.
     *
     * This method is the main entry point for handling NFC communication. It processes
     * different types of APDU commands according to ISO 7816-4 specifications.
     *
     * @param command The APDU command received from the NFC reader
     * @return ResponseApdu containing the status and optional response data
     *
     * @throws IllegalStateException if called when the helper is in an error state
     */
    suspend fun processApdu(command: CommandApdu): ResponseApdu {
        if (inError) {
            log.error("processApdu: Already in error state, responding to APDU with status 6f00")
            return ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }

        log.debug("Processing APDU: INS=${command.ins.toString(16)}, P1=${command.p1.toString(16)}, P2=${command.p2.toString(16)}")

        return try {
            when (command.ins) {
                NfcConst.INS_SELECT -> {
                    when (command.p1) {
                        NfcConst.INS_SELECT_P1_FILE -> {
                            processSelectFile(command)
                        }

                        NfcConst.INS_SELECT_P1_APPLICATION -> {
                            processSelectApplication(command)
                        }

                        else -> {
                            raiseError("Unsupported SELECT parameter P1=${command.p1}")
                            ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)
                        }
                    }
                }

                NfcConst.INS_READ_BINARY -> {
                    processReadBinary(command)
                }

                NfcConst.INS_UPDATE_BINARY -> {
                    processUpdateBinaryCommand(command)
                }

                else -> {
                    raiseError("Command APDU INS=${command.ins} not supported")
                    ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)
                }
            }
        } catch (error: Exception) {
            raiseError("Critical error processing APDU: ${error.message}", error)
            ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }.also { response ->
            log.debug("APDU processed with status: ${response.status.toString(16)}")
        }
    }

    /**
     * Resets the helper to its initial state.
     * This can be useful for recovery scenarios or when reusing the helper.
     *
     * @param clearErrorState Whether to clear the error state as well
     */
    fun reset(clearErrorState: Boolean = false) {
        log.info("Resetting NFC engagement helper state")
        stateHolder.reset(clearErrorState)
        updateBinaryHandler.reset()

        if (clearErrorState) {
            log.info("Error state cleared")
        }
    }

    /**
     * Checks if the helper is in an unrecoverable error state.
     *
     * An unrecoverable state means:
     * - We are in an error state, OR
     * - The static handover resulted in an error
     *
     * In such states, all subsequent APDUs should return an error response
     * and the helper should be reset or recreated.
     */
    fun isInUnrecoverableState(): Boolean = state.isUnrecoverable

    /**
     * Checks if the handover has been completed.
     * For static handover, this means READ BINARY was processed and callback invoked.
     * For negotiated handover, this means the handover request was processed.
     */
    fun isHandoverComplete(): Boolean = state.isHandoverComplete

    /**
     * Checks if the helper is currently in a valid operational state
     *
     * @return true if the helper can process commands, false otherwise
     */
    fun isOperational(): Boolean = state.isOperational

    /**
     * Gets the current state information for debugging purposes
     *
     * @return Map containing current state information
     */
    fun getStateInfo(): Map<String, Any> {
        val currentState = state
        return currentState.toDebugMap() +
            mapOf(
                "hasStaticHandover" to (instance.getEngagementMethods().any { it is NfcEngagementMethod }),
                "hasNegotiatedHandover" to (negotiatedHandoverPicker != null),
                "updateBinaryDataSize" to updateBinaryHandler.currentSize(),
            )
    }

    /**
     * Captures the current state for potential restoration after a short NFC tap.
     *
     * This is used when the NFC link is lost (e.g., phone pulled away) before
     * the handover is complete. The state can be restored if the user taps again
     * within the validity window.
     *
     * @return NfcSessionState if state is recoverable, null otherwise
     */
    internal fun captureState(): NfcSessionState? {
        val currentState = state

        // Don't capture if handover is already complete or in error
        if (currentState.isHandoverComplete || currentState.isUnrecoverable) {
            log.debug("captureState: Not capturing - handover complete or in error")
            return null
        }

        // Only capture if we have meaningful state to preserve
        if (currentState.staticHandoverState == StaticHandoverState.NOT_STARTED &&
            currentState.negotiatedHandoverState == NegotiatedHandoverState.NOT_STARTED
        ) {
            log.debug("captureState: Not capturing - no handover started")
            return null
        }

        val sessionState =
            NfcSessionState(
                captureTimeMs = getCurrentTimeMillis(),
                helperState = currentState,
            )

        log.info("captureState: Captured state - staticHandover=${currentState.staticHandoverState}, negotiatedHandover=${currentState.negotiatedHandoverState}")
        return sessionState
    }

    /**
     * Restores a previously captured state.
     *
     * This allows continuing a handover process that was interrupted by a short tap.
     * State is only restored if it's still valid (within the validity window).
     *
     * @param sessionState The state to restore
     * @return true if state was restored, false if state was invalid or expired
     */
    internal fun restoreState(sessionState: NfcSessionState): Boolean {
        val currentTimeMs = getCurrentTimeMillis()
        if (!sessionState.isValid(currentTimeMs)) {
            log.warn("restoreState: State expired (captured ${currentTimeMs - sessionState.captureTimeMs}ms ago)")
            return false
        }

        if (sessionState.helperState.isHandoverComplete) {
            log.warn("restoreState: State indicates handover was already complete")
            return false
        }

        // Atomically restore the entire state
        stateHolder.update { sessionState.helperState }

        log.info("restoreState: Restored state - staticHandover=${sessionState.staticHandoverState}, negotiatedHandover=${sessionState.negotiatedHandoverState}")
        return true
    }

    /**
     * Attempts to recover from an error state if possible.
     *
     * Recovery is only possible if:
     * - The engagement has not progressed beyond a certain point (e.g., BLE not connected)
     * - The error was transient and can be retried
     *
     * @return true if recovery was successful, false if error is unrecoverable
     */
    fun attemptRecovery(): Boolean {
        val currentState = state
        if (!currentState.inError) {
            return true
        }

        // Can't recover if handover was already completed
        if (currentState.handoverCompleteCalled) {
            log.warn("attemptRecovery: Cannot recover - handover was already completed")
            return false
        }

        log.info("attemptRecovery: Attempting recovery from error state")
        reset(clearErrorState = true)
        return true
    }
}
