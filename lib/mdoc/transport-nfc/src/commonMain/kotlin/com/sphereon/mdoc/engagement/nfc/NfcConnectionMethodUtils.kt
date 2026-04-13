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

import com.sphereon.data.link.nfc.model.NdefRecord
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transport.ConnectionMethod
import kotlin.uuid.Uuid

/**
 * Utility functions for converting between ConnectionMethods and NDEF records
 * for NFC handover according to ISO/IEC 18013-5.
 *
 * These functions bridge the NEW transport system with NFC handover requirements.
 */

/**
 * Result of converting a ConnectionMethod to NDEF format.
 */
data class NdefConversionResult(
    val supported: Boolean,
    val ndefRecord: NdefRecord?,
    val acRecord: NdefRecord?,
    val role: MdocRole
)

/**
 * Converts a ConnectionMethod to NDEF records for NFC handover.
 *
 * @param alternativeCarrierReferences List of alternative carrier reference strings
 * @param role The device role (MDOC or MDOC_READER)
 * @param skipUuids Whether to skip including UUIDs in the NDEF record
 * @return NdefConversionResult containing the NDEF records or null if not supported
 */
fun ConnectionMethod.toNdef(
    alternativeCarrierReferences: List<String>,
    role: MdocRole,
    skipUuids: Boolean,
    bleHandoverMapper: BleHandoverMapper? = null
): NdefConversionResult {
    val result = bleHandoverMapper?.toNdef(this, alternativeCarrierReferences, role, skipUuids)
    return result
        ?: NdefConversionResult(
            supported = false,
            ndefRecord = null,
            acRecord = null,
            role = role
        )
}

/**
 * Result of parsing a ConnectionMethod from NDEF.
 */
data class ConnectionMethodResult(
    val supported: Boolean,
    val connectionMethod: ConnectionMethod?,
    val role: MdocRole? = null
)

/**
 * Creates a ConnectionMethod from an NDEF record.
 *
 * @param record The NDEF record to parse
 * @param role The device role
 * @param uuid Optional UUID for BLE connections
 * @return ConnectionMethodResult containing the parsed method or indicating unsupported
 */
fun connectionMethodFromNdef(
    record: NdefRecord,
    role: MdocRole,
    uuid: Uuid? = null,
    bleHandoverMapper: BleHandoverMapper? = null
): ConnectionMethodResult {
    val result = bleHandoverMapper?.fromNdef(record, role, uuid)
    return result ?: ConnectionMethodResult(
        supported = false,
        connectionMethod = null,
        role = role
    )
}

/**
 * Disambiguates connection methods that support multiple modes.
 *
 * For connection methods that support multiple modes (like BLE with both central
 * and peripheral), this splits them into separate instances.
 *
 * @param connectionMethods List of connection methods to disambiguate
 * @param role Device role (affects how properties are distributed)
 * @return Disambiguated list where each method supports exactly one mode
 */
fun disambiguateConnectionMethods(
    connectionMethods: List<ConnectionMethod>,
    role: MdocRole,
    bleHandoverMapper: BleHandoverMapper? = null
): List<ConnectionMethod> {
    return bleHandoverMapper?.disambiguate(connectionMethods, role) ?: connectionMethods
}

/**
 * Combines similar connection methods into single instances.
 *
 * This is the reverse of disambiguation. For BLE methods with the same UUID
 * but different modes, combines them into a single method supporting both modes.
 *
 * @param connectionMethods List of connection methods to combine
 * @return Combined list where similar methods are merged
 */
fun combineConnectionMethods(
    connectionMethods: List<ConnectionMethod>,
    bleHandoverMapper: BleHandoverMapper? = null
): List<ConnectionMethod> {
    return bleHandoverMapper?.combine(connectionMethods) ?: connectionMethods
}

/**
 * Extension function to check if a ConnectionMethod has a connectionMethod property.
 * This is for compatibility with code expecting the OLD ConnectionMethodResult wrapper.
 */
val ConnectionMethod.connectionMethod: ConnectionMethod
    get() = this

/**
 * Extension function for compatibility - ConnectionMethod is always "supported" in NEW system.
 */
val ConnectionMethod.supported: Boolean
    get() = true
