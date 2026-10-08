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

package com.sphereon.data.link.nfc.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.util.appendUInt16
import com.sphereon.util.getUInt16
import kotlinx.coroutines.delay
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration

/**
 * Class representing a ISO/IEC 14443-4 tag.
 *
 * This is an abstract super class intended for OS-specific code to implement the [transceive] method.
 */
@JsExportCompat
abstract class NfcIsoTag {
    /**
     * The maximum size of an APDU that can be sent via [transceive].
     *
     * This varies depending on the NFC tag reader hardware.
     */
    abstract val maxTransceiveLength: Int

    /**
     * Sends an APDU to the tag and waits for a response APDU.
     *
     * @param command the [com.sphereon.data.link.nfc.model.CommandApdu] to send.
     * @return the [ResponseApdu] which was received.
     * @throws NfcTagLostException if the tag was lost.
     */
    abstract suspend fun transceive(command: CommandApdu): ResponseApdu

    /**
     * Selects an application according to ISO 7816-4 clause 11.2.2.
     *
     * @param applicationId the application to select, e.g. [NfcConst.NDEF_APPLICATION_ID].
     * @throws NfcCommandFailedException if the command fails.
     */
    suspend fun selectApplication(applicationId: ByteString) {
        // ISO 7816-4 clause 11.2.2
        val response =
            transceive(
                CommandApdu(
                    cla = 0,
                    ins = NfcConst.INS_SELECT,
                    p1 = NfcConst.INS_SELECT_P1_APPLICATION,
                    p2 = NfcConst.INS_SELECT_P2_APPLICATION,
                    payload = applicationId,
                    le = 0,
                ),
            )
        if (response.status != NfcConst.RESPONSE_STATUS_SUCCESS) {
            throw NfcCommandFailedException("Error selecting application, status ${response.statusHexString}", response.status)
        }
    }

    /**
     * Selects a file according to ISO 7816-4 clause 11.2.2.
     *
     * @param fileId the identifier for the file to select, e.g. [NfcConst.NDEF_CAPABILITY_CONTAINER_FILE_ID].
     * @throws NfcCommandFailedException if the command fails.
     */
    suspend fun selectFile(fileId: Int) {
        val response =
            transceive(
                CommandApdu(
                    cla = 0,
                    ins = NfcConst.INS_SELECT,
                    p1 = NfcConst.INS_SELECT_P1_FILE,
                    p2 = NfcConst.INS_SELECT_P2_FILE,
                    payload = buildByteString { appendUInt16(fileId) },
                    le = 0,
                ),
            )
        if (response.status != NfcConst.RESPONSE_STATUS_SUCCESS) {
            throw NfcCommandFailedException("Error selecting file, status ${response.statusHexString}", response.status)
        }
    }

    /**
     * Reads binary data according to ISO 7816-4 clause 11.3.3.
     *
     * @param offset offset of where to read from.
     * @param length amount of data to read, must be positive.
     * @return the data which was read.
     * @throws NfcCommandFailedException if the command fails.
     */
    suspend fun readBinary(
        offset: Int,
        length: Int,
    ): ByteArray {
        require(length > 0) { "Length must be positive" }
        require(offset in 0..MAX_OFFSET) { "Offset must be between 0 and 0xFFFF" }
        val response =
            transceive(
                CommandApdu(
                    cla = 0,
                    ins = NfcConst.INS_READ_BINARY,
                    p1 = offset shr BYTE_SHIFT, // high byte
                    p2 = offset and BYTE_MASK, // low byte
                    payload = ByteString(),
                    le = length,
                ),
            )
        if (response.status != NfcConst.RESPONSE_STATUS_SUCCESS) {
            throw NfcCommandFailedException("Error READ BINARY, status ${response.statusHexString}", response.status)
        }
        return response.payload.toByteArray()
    }

    /**
     * Updates binary data according to ISO 7816-4 clause 11.3.5.
     *
     * @param offset offset of where to update data.
     * @param data the data to write, cannot be larger than 255 bytes.
     * @throws NfcCommandFailedException if the command fails.
     */
    suspend fun updateBinary(
        offset: Int,
        data: ByteArray,
    ) {
        require(data.isNotEmpty()) { "Data to write must be non-empty" }
        require(data.size < MAX_SHORT_DATA_SIZE) { "Data cannot be larger than 255 bytes" }

        val response =
            transceive(
                CommandApdu(
                    cla = 0,
                    ins = NfcConst.INS_UPDATE_BINARY,
                    p1 = offset shr BYTE_SHIFT, // high byte
                    p2 = offset and BYTE_MASK, // low byte
                    payload = ByteString(data),
                    le = 0,
                ),
            )
        if (response.status != NfcConst.RESPONSE_STATUS_SUCCESS) {
            throw NfcCommandFailedException("Error UPDATE BINARY, status ${response.statusHexString}", response.status)
        }
    }

    /**
     * Reads NDEF data according to NFC Forum Type 4 Tag section 7.5.4.
     *
     * @param wtInt Minimum waiting time as per NFC Forum Tag NDEF Exchange Protocol section 4.1.6.
     * @param nWait Maximum number of waiting time extensions as per NFC Forum Tag NDEF Exchange Protocol section 4.1.7.
     * @return the message that was read.
     */
    @JvmOverloads
    suspend fun ndefReadMessage(
        wtInt: Int = 0,
        nWait: Int = 0,
    ): NdefMessage {
        var nWaitCounter = nWait
        var replyLen: Int
        do {
            replyLen = readBinary(0x0000, 2).getUInt16(0).toInt()
            if (replyLen > 0) {
                break
            }

            // As per [TNEP] 4.1.7 if the tag sends an empty NDEF message it means that
            // it's requesting extra time... honor this if we can.
            if (nWaitCounter > 0) {
                val tWait = Duration.fromWtInt(wtInt)
                delay(tWait)
                nWaitCounter--
            } else {
                error("NDEF message with length 0 but no time extensions left")
            }
        } while (true)
        return NdefMessage.fromEncoded(readBinary(2, replyLen))
    }

    /**
     * Exchanges NDEF messages according to NFC Forum Tag NDEF Exchange Protocol section 5.
     *
     * @param ndefMessage the message to write.
     * @param wtInt Minimum waiting time as per NFC Forum Tag NDEF Exchange Protocol section 4.1.6.
     * @param nWait Maximum number of waiting time extensions as per NFC Forum Tag NDEF Exchange Protocol section 4.1.7.
     * @return the message which was read.
     */
    suspend fun ndefTransact(
        ndefMessage: NdefMessage,
        wtInt: Int,
        nWait: Int,
    ): NdefMessage {
        val encodedNdefMessage = ndefMessage.encode()

        // See Type 4 Tag Technical Specification Version 1.2 section 7.5.5 NDEF Write Procedure
        // for how this is done.

        // Check to see if we can merge the three UPDATE_BINARY messages into a single message.
        // This is allowed as per [T4T] 7.5.5 NDEF Write Procedure:
        //
        //   If the entire NDEF Message can be written with a single UPDATE_BINARY
        //   Command, the Reader/Writer MAY write NLEN and ENLEN (Symbol 6), as
        //   well as the entire NDEF Message (Symbol 5) using a single
        //   UPDATE_BINARY Command. In this case the Reader/Writer SHALL
        //   proceed to Symbol 5 and merge Symbols 5 and 6 operations into a single
        //   UPDATE_BINARY Command.
        //
        // For debugging, this optimization can be turned off by setting this to |true|:
        if (encodedNdefMessage.size < MAX_SINGLE_WRITE_SIZE) {
            updateBinary(
                0,
                buildByteString { appendUInt16(encodedNdefMessage.size).append(encodedNdefMessage) }.toByteArray(),
            )
        } else {
            // First command is UPDATE_BINARY to reset length
            updateBinary(0, buildByteString { appendUInt16(0) }.toByteArray())

            // Subsequent commands are UPDATE_BINARY with payload, chopped into bits no longer
            // than 255 bytes each
            var offset = 0
            var remaining = encodedNdefMessage.size
            while (remaining > 0) {
                val numBytesToWrite = remaining.coerceAtMost(MAX_UPDATE_BINARY_CHUNK)
                val bytesToWrite = encodedNdefMessage.copyOfRange(offset, offset + numBytesToWrite)
                updateBinary(offset + 2, bytesToWrite)
                remaining -= numBytesToWrite
                offset += numBytesToWrite
            }

            // Final command is UPDATE_BINARY to write the length
            updateBinary(
                0,
                buildByteString { appendUInt16(encodedNdefMessage.size) }.toByteArray(),
            )
        }
        val tWait = Duration.fromWtInt(wtInt)
        delay(tWait)

        // Now read NDEF file...
        return ndefReadMessage(wtInt, nWait)
    }

    companion object {
        private const val MAX_OFFSET = 0xFFFF
        private const val BYTE_SHIFT = 8
        private const val BYTE_MASK = 0xFF
        private const val MAX_SHORT_DATA_SIZE = 256
        private const val MAX_SINGLE_WRITE_SIZE = 254
        private const val MAX_UPDATE_BINARY_CHUNK = 255
    }
}
