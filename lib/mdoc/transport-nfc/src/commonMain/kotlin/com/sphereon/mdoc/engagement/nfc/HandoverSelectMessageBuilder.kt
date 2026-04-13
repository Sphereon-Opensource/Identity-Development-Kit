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
import kotlinx.io.bytestring.encodeToByteString
import com.sphereon.data.link.nfc.model.HandoverSelectRecord
import com.sphereon.data.link.nfc.model.NdefMessage
import com.sphereon.data.link.nfc.model.NdefRecord
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.transport.ConnectionMethod

/**
 * Result of building a Handover Select message.
 */
internal data class HandoverSelectBuildResult(
    /** The complete NDEF message for Handover Select */
    val message: NdefMessage,
    /** List of carrier configuration records generated */
    val carrierRecords: List<NdefRecord>,
    /** List of alternative carrier records generated */
    val alternativeCarrierRecords: List<NdefRecord>
)

/**
 * Builder for creating NFC Handover Select messages.
 *
 * This implements Handover Select message generation according to:
 * - ISO/IEC 18013-5:2021 clause 8.3.3.1.2 (Device engagement contents)
 * - NFC Forum Connection Handover Technical Specification
 *
 * ## Message Structure:
 * 1. Handover Select Record (with embedded Alternative Carrier records)
 * 2. Device Engagement Record (external type with CBOR payload)
 * 3. Carrier Configuration Records (e.g., BLE OOB data)
 *
 * ## Usage:
 * ```kotlin
 * val builder = HandoverSelectMessageBuilder()
 * val result = builder.build(
 *     engagementInstance = instance,
 *     skipUuids = false
 * )
 * val encodedMessage = result.message.encode()
 * ```
 */
internal class HandoverSelectMessageBuilder(
    private val logCallback: ((String) -> Unit)? = null,
    private val bleHandoverMapper: BleHandoverMapper? = null
) {

    /**
     * Builds a Handover Select message for the given engagement instance.
     *
     * @param engagementInstance The engagement instance containing device keys and connection methods
     * @param skipUuids Whether to skip including UUIDs in carrier records
     *        (per ISO 18013-5, UUIDs are skipped in negotiated handover for central client mode)
     * @return HandoverSelectBuildResult containing the message and record details
     * @throws IllegalArgumentException if no connection methods are provided
     * @throws IllegalStateException if no supported carrier records could be generated
     */
    fun build(
        engagementInstance: EngagementInstance,
        skipUuids: Boolean
    ): HandoverSelectBuildResult {
        require(engagementInstance.getEngagementMethods().isNotEmpty()) {
            "No engagement methods provided"
        }

        val auxiliaryReferences = mutableListOf(MdocNfcConstants.MDOC_LITERAL)
        val carrierConfigurationRecords = mutableListOf<NdefRecord>()
        val alternativeCarrierRecords = mutableListOf<NdefRecord>()

        val connectionMethods = engagementInstance.getConnectionMethods(true)
        log("Building Handover Select with ${connectionMethods.size} connection methods")

        for (connectionMethod in connectionMethods) {
            log("Processing connection method: $connectionMethod")
            val ndefResult = connectionMethod.toNdef(
                alternativeCarrierReferences = auxiliaryReferences,
                role = MdocRole.MDOC,
                skipUuids = skipUuids,
                bleHandoverMapper = bleHandoverMapper
            )

            if (ndefResult.supported && ndefResult.ndefRecord != null && ndefResult.acRecord != null) {
                carrierConfigurationRecords.add(ndefResult.ndefRecord)
                alternativeCarrierRecords.add(ndefResult.acRecord)
                log("Added carrier configuration for $connectionMethod")
            } else {
                log("Connection method $connectionMethod not supported for NFC handover")
            }
        }

        require(carrierConfigurationRecords.isNotEmpty()) {
            "No carrier configuration records generated - no supported connection methods"
        }
        require(alternativeCarrierRecords.isNotEmpty()) {
            "No alternative carrier records generated"
        }

        val handoverSelectRecord = HandoverSelectRecord(
            version = MdocNfcConstants.CONNECTION_HANDOVER_VERSION,
            embeddedMessage = NdefMessage(alternativeCarrierRecords)
        )

        val deviceEngagementRecord = NdefRecord(
            tnf = NdefRecord.Tnf.EXTERNAL_TYPE,
            type = MdocNfcConstants.HANDOVER_SELECT_MSG_TYPE_DEVICE_ENGAGEMENT.encodeToByteString(),
            id = MdocNfcConstants.MDOC_LITERAL.encodeToByteString(),
            payload = ByteString(engagementInstance.getDeviceEngagement().data().encodeCbor())
        )

        val message = NdefMessage(
            listOf(
                handoverSelectRecord.generateNdefRecord(),
                deviceEngagementRecord,
                *carrierConfigurationRecords.toTypedArray()
            )
        )

        return HandoverSelectBuildResult(
            message = message,
            carrierRecords = carrierConfigurationRecords,
            alternativeCarrierRecords = alternativeCarrierRecords
        )
    }

    private fun log(message: String) {
        logCallback?.invoke(message)
    }
}

/**
 * Extension function to convert connection methods to NDEF with logging.
 */
internal fun List<ConnectionMethod>.toNdefRecords(
    auxiliaryReferences: List<String>,
    skipUuids: Boolean,
    bleHandoverMapper: BleHandoverMapper? = null,
    logCallback: ((String) -> Unit)? = null
): Pair<List<NdefRecord>, List<NdefRecord>> {
    val carrierRecords = mutableListOf<NdefRecord>()
    val acRecords = mutableListOf<NdefRecord>()

    for (method in this) {
        val result = method.toNdef(
            alternativeCarrierReferences = auxiliaryReferences,
            role = MdocRole.MDOC,
            skipUuids = skipUuids,
            bleHandoverMapper = bleHandoverMapper
        )
        if (result.supported && result.ndefRecord != null && result.acRecord != null) {
            carrierRecords.add(result.ndefRecord)
            acRecords.add(result.acRecord)
        } else {
            logCallback?.invoke("Connection method $method not supported for NFC handover")
        }
    }

    return carrierRecords to acRecords
}
