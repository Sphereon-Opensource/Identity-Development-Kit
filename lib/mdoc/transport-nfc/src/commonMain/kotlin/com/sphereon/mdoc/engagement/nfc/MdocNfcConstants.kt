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
 * Constants specific to mDoc NFC engagement according to ISO/IEC 18013-5.
 *
 * These constants define protocol-specific values for NFC handover,
 * NDEF file handling, and connection handover negotiation.
 */
internal object MdocNfcConstants {
    /**
     * Device engagement message type for Handover Select.
     * Reference: ISO/IEC 18013-5:2021 clause 8.2.2.2
     */
    const val HANDOVER_SELECT_MSG_TYPE_DEVICE_ENGAGEMENT = "iso.org:18013:deviceengagement"

    /**
     * Literal string used as auxiliary reference in handover.
     * Reference: ISO/IEC 18013-5:2021 clause 8.2.2.2
     */
    const val MDOC_LITERAL = "mdoc"

    // ========================================
    // NDEF File IDs
    // ========================================

    /**
     * File ID for the NDEF data file.
     * Reference: NFC Forum Type 4 Tag Technical Specification
     */
    const val NDEF_DATA_FILE_ID = 0xe104

    // ========================================
    // TNEP Parameters
    // ========================================

    /**
     * TNEP protocol version (1.0).
     * Reference: NFC Forum TNEP Technical Specification
     */
    const val TNEP_VERSION = 0x10

    /**
     * TNEP communication mode (single response).
     * Reference: NFC Forum TNEP Technical Specification
     */
    const val TNEP_COMMUNICATION_MODE = 0x00

    /**
     * Waiting time integer for TNEP.
     * Reference: NFC Forum TNEP Technical Specification
     */
    const val WT_INT = 0

    /**
     * Maximum number of waiting time extensions.
     * Reference: NFC Forum TNEP Technical Specification
     */
    const val N_WAIT = 15

    /**
     * Maximum NDEF message size supported.
     * Reference: NFC Forum Type 4 Tag Technical Specification
     */
    const val MAX_NDEF_SIZE = 0xffff

    // ========================================
    // Connection Handover
    // ========================================

    /**
     * Connection Handover protocol version (1.5).
     * Reference: NFC Forum Connection Handover Technical Specification
     */
    const val CONNECTION_HANDOVER_VERSION = 0x15

    // ========================================
    // File Access Conditions
    // ========================================

    /**
     * File read access allowed condition byte.
     * Reference: NFC Forum Type 4 Tag Technical Specification
     */
    const val FILE_READ_ACCESS_ALLOW = 0x00.toByte()

    /**
     * File write access allowed condition byte.
     * Reference: NFC Forum Type 4 Tag Technical Specification
     */
    const val FILE_WRITE_ACCESS_ALLOW = 0x00.toByte()

    /**
     * File write access denied condition byte.
     * Reference: NFC Forum Type 4 Tag Technical Specification
     */
    const val FILE_WRITE_ACCESS_DENY = 0xff.toByte()

    // ========================================
    // Session State Constants
    // ========================================

    /**
     * Per ISO 18013-5, device engagement timeout should be no less than 30 seconds.
     * This is the default validity window for captured NFC session state.
     */
    const val DEFAULT_SESSION_VALIDITY_MS = 30_000L
}
