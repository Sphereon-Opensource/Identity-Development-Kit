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

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import com.sphereon.data.link.nfc.model.NdefMessage
import com.sphereon.data.link.nfc.model.NfcConst
import com.sphereon.data.link.nfc.model.ResponseApdu
import com.sphereon.util.getUInt16

/**
 * Result of UPDATE BINARY chunk processing.
 */
internal sealed class UpdateBinaryResult {
    /** Chunk processed successfully, continue accumulating */
    data object Continue : UpdateBinaryResult()

    /** All chunks received, complete NDEF message available */
    data class Complete(val message: NdefMessage) : UpdateBinaryResult()

    /** Error occurred during processing */
    data class Error(val message: String) : UpdateBinaryResult()
}

/**
 * Handles UPDATE BINARY command processing for NFC NDEF writes.
 *
 * This implements the NDEF Write Procedure as specified by
 * Type 4 Tag Technical Specification Version 1.2 section 7.5.5.
 *
 * The handler manages the state machine for receiving fragmented NDEF messages
 * across multiple UPDATE BINARY commands from the NFC reader.
 *
 * ## Protocol Overview:
 * 1. First UPDATE BINARY at offset 0 with 2 bytes of 0x0000 - Reset/initialize
 * 2. Subsequent UPDATE BINARYs at offset 2+ with data chunks
 * 3. Final UPDATE BINARY at offset 0 with 2 bytes containing actual length - Finalize
 *
 * ## Usage:
 * ```kotlin
 * val handler = UpdateBinaryHandler()
 *
 * // Process each UPDATE BINARY command
 * when (val result = handler.processChunk(offset, data)) {
 *     is UpdateBinaryResult.Continue -> // Return success, wait for more
 *     is UpdateBinaryResult.Complete -> // Process the complete message
 *     is UpdateBinaryResult.Error -> // Handle error
 * }
 * ```
 */
internal class UpdateBinaryHandler {

    /** Buffer for accumulating chunked data */
    private var dataBuffer: ByteStringBuilder? = null

    /**
     * Process an UPDATE BINARY chunk.
     *
     * @param offset The offset within the NDEF data file
     * @param data The payload data from the UPDATE BINARY command
     * @return UpdateBinaryResult indicating the state after processing
     */
    fun processChunk(offset: Int, data: ByteString): UpdateBinaryResult {
        return when (offset) {
            0 -> processOffsetZero(data)
            1 -> UpdateBinaryResult.Error("Unexpected offset 1 in UPDATE BINARY")
            else -> processDataChunk(offset, data)
        }
    }

    /**
     * Reset the handler state.
     */
    fun reset() {
        dataBuffer = null
    }

    /**
     * Check if the handler is currently accumulating data.
     */
    fun isActive(): Boolean = dataBuffer != null

    /**
     * Get the current accumulated data size.
     */
    fun currentSize(): Int = dataBuffer?.size ?: 0

    /**
     * Handles data at offset 0 which has special meaning:
     * - 2 bytes of 0x0000: Initialize/reset for new message
     * - 2 bytes with length: Finalize message with accumulated data
     * - More than 2 bytes: Single-chunk message (length prefix + data)
     */
    private fun processOffsetZero(data: ByteString): UpdateBinaryResult {
        return if (data.size == 2) {
            val lengthValue = data.getUInt16(0).toInt()
            if (lengthValue == 0) {
                // Reset command - start accumulating new message
                handleReset()
            } else {
                // Length confirmation - finalize accumulated message
                handleLengthConfirmation(lengthValue)
            }
        } else {
            // Single-chunk message with length prefix
            handleSingleChunk(data)
        }
    }

    /**
     * Handles reset command (2 bytes of 0x0000 at offset 0).
     */
    private fun handleReset(): UpdateBinaryResult {
        if (dataBuffer != null) {
            return UpdateBinaryResult.Error("Got reset but already active")
        }
        dataBuffer = ByteStringBuilder()
        return UpdateBinaryResult.Continue
    }

    /**
     * Handles length confirmation (2-byte length at offset 0 after data).
     */
    private fun handleLengthConfirmation(declaredLength: Int): UpdateBinaryResult {
        val buffer = dataBuffer
            ?: return UpdateBinaryResult.Error("Got length but not active")

        if (declaredLength != buffer.size) {
            return UpdateBinaryResult.Error(
                "Length $declaredLength doesn't match received data of ${buffer.size} bytes"
            )
        }

        val message = try {
            NdefMessage.fromEncoded(buffer.toByteString().toByteArray())
        } catch (e: Exception) {
            return UpdateBinaryResult.Error("Failed to parse NDEF message: ${e.message}")
        }

        dataBuffer = null
        return UpdateBinaryResult.Complete(message)
    }

    /**
     * Handles single-chunk message (more than 2 bytes at offset 0).
     */
    private fun handleSingleChunk(data: ByteString): UpdateBinaryResult {
        if (dataBuffer != null) {
            return UpdateBinaryResult.Error("Got single-chunk data but already active")
        }

        // Skip 2-byte length prefix
        val message = try {
            NdefMessage.fromEncoded(data.toByteArray(2))
        } catch (e: Exception) {
            return UpdateBinaryResult.Error("Failed to parse single-chunk NDEF message: ${e.message}")
        }

        return UpdateBinaryResult.Complete(message)
    }

    /**
     * Handles data chunks at offset > 1.
     * These are sequential writes that must be in order.
     */
    private fun processDataChunk(offset: Int, data: ByteString): UpdateBinaryResult {
        val buffer = dataBuffer
            ?: return UpdateBinaryResult.Error("Got data at offset $offset but not active")

        // Writes must be sequential (offset - 2 because of the 2-byte length prefix)
        val expectedOffset = buffer.size + 2
        if (offset != expectedOffset) {
            return UpdateBinaryResult.Error(
                "Got data at offset $offset but expected $expectedOffset (buffer size: ${buffer.size})"
            )
        }

        buffer.append(data)
        return UpdateBinaryResult.Continue
    }
}

/**
 * Creates a success ResponseApdu for UPDATE BINARY.
 */
internal fun updateBinarySuccessResponse(): ResponseApdu =
    ResponseApdu(NfcConst.RESPONSE_STATUS_SUCCESS)

/**
 * Creates an error ResponseApdu for UPDATE BINARY.
 */
internal fun updateBinaryErrorResponse(): ResponseApdu =
    ResponseApdu(NfcConst.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
