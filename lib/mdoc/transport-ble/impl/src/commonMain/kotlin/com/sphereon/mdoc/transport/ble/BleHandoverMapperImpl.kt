package com.sphereon.mdoc.transport.ble

import com.sphereon.data.link.nfc.model.NdefRecord
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.nfc.BleHandoverMapper
import com.sphereon.mdoc.engagement.nfc.BleHandoverMapperProvider
import com.sphereon.mdoc.engagement.nfc.ConnectionMethodResult
import com.sphereon.mdoc.engagement.nfc.NdefConversionResult
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.util.appendByteArray
import com.sphereon.util.appendByteString
import com.sphereon.util.appendUInt64Le
import com.sphereon.util.appendUInt8
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.Uuid

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BleHandoverMapper>())
class BleHandoverMapperImpl : BleHandoverMapper {

    /**
     * Component interface that contributes [BleHandoverMapperProvider] to the session scope.
     * This allows NFC engagement code to access the BLE handover mapper via the session component.
     */
    @ContributesTo(SessionScope::class)
    interface Component : BleHandoverMapperProvider {
        override val bleHandoverMapper: BleHandoverMapper
    }

    override fun toNdef(
        connectionMethod: ConnectionMethod,
        alternativeCarrierReferences: List<String>,
        role: MdocRole,
        skipUuids: Boolean
    ): NdefConversionResult? {
        val bleMethod = connectionMethod as? BleConnectionMethod ?: return null

        // Determine UUID and LE role based on connection mode
        val (uuid, leRole) = when {
            bleMethod.options.centralClientMode && bleMethod.options.peripheralServerMode -> {
                check(bleMethod.options.centralClientModeUuid == bleMethod.options.peripheralServerModeUuid) {
                    "UUIDs for both central client and peripheral server mode must be the same if both are enabled for BLE"
                }
                Pair(bleMethod.options.centralClientModeUuid, BleRole.BOTH_ROLES_CENTRAL_PREFERRED)
            }

            bleMethod.options.centralClientMode -> {
                Pair(
                    bleMethod.options.centralClientModeUuid,
                    when (role) {
                        MdocRole.MDOC -> BleRole.CENTRAL_CLIENT_ONLY
                        MdocRole.MDOC_READER -> BleRole.PERIPHERAL_ONLY
                    }
                )
            }

            bleMethod.options.peripheralServerMode -> {
                Pair(
                    bleMethod.options.peripheralServerModeUuid,
                    when (role) {
                        MdocRole.MDOC -> BleRole.PERIPHERAL_ONLY
                        MdocRole.MDOC_READER -> BleRole.CENTRAL_CLIENT_ONLY
                    }
                )
            }

            else -> {
                return NdefConversionResult(
                    supported = false,
                    ndefRecord = null,
                    acRecord = null,
                    role = role
                )
            }
        }

        // Build OOB data in AD format as defined in Bluetooth Core Specification
        // Volume 3, Part C, Section 11
        val oobData = buildByteString {
            // Mandatory: LE Role
            appendUInt8(0x02u)  // Length
            appendUInt8(BleAdType.LE_ROLE)
            appendUInt8(leRole)

            // Optional: UUID (skipped in negotiated handover for central client mode)
            if (uuid != null && !skipUuids) {
                appendUInt8(0x11u) // Length: 17 bytes (1 type + 16 UUID)
                appendUInt8(BleAdType.LE_COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS)
                // UUID in little-endian format
                uuid.toULongs { mostSignificantBits, leastSignificantBits ->
                    appendUInt64Le(leastSignificantBits)
                    appendUInt64Le(mostSignificantBits)
                }
            }

            // Optional: MAC Address
            val macAddress = bleMethod.options.peripheralServerModeDeviceAddress
            if (macAddress != null) {
                require(macAddress.size == 6) {
                    "MAC address should be six bytes, found ${macAddress.size}"
                }
                appendUInt8(0x07u) // Length: 7 bytes (1 type + 6 address)
                appendUInt8(BleAdType.LE_BLUETOOTH_MAC_ADDRESS)
                appendByteString(ByteString(macAddress))
            }
        }

        // Create carrier configuration record
        val ndefRecord = NdefRecord(
            tnf = NdefRecord.Tnf.MIME_MEDIA,
            type = MIME_TYPE_CONNECTION_HANDOVER_BLE.encodeToByteString(),
            id = "0".encodeToByteString(),
            payload = oobData
        )

        // Create Alternative Carrier Record
        // Reference: NFC Forum Connection Handover Technical Specification Section 7.1
        check(alternativeCarrierReferences.size < 0x100) {
            "Too many auxiliary references: ${alternativeCarrierReferences.size}"
        }

        val acRecordPayload = buildByteString {
            appendUInt8(0x01u) // CPS: active
            appendUInt8(0x01u) // Length of carrier data reference ("0")
            appendUInt8('0'.code.toUByte()) // Carrier data reference
            appendUInt8(alternativeCarrierReferences.size.toUByte()) // Number of auxiliary references
            for (auxRef in alternativeCarrierReferences) {
                val auxRefUtf8 = auxRef.encodeToByteArray()
                check(auxRefUtf8.size < 0x100) { "Auxiliary reference too long: ${auxRefUtf8.size}" }
                appendUInt8(auxRefUtf8.size.toUByte())
                appendByteArray(auxRefUtf8)
            }
        }

        val acRecord = NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = "ac".encodeToByteString(),
            payload = acRecordPayload
        )

        return NdefConversionResult(
            supported = true,
            ndefRecord = ndefRecord,
            acRecord = acRecord,
            role = role
        )
    }

    override fun fromNdef(record: NdefRecord, role: MdocRole, uuid: Uuid?): ConnectionMethodResult? {
        if (record.tnf == NdefRecord.Tnf.MIME_MEDIA &&
            record.type.decodeToString() == MIME_TYPE_CONNECTION_HANDOVER_BLE &&
            record.id.decodeToString() == "0"
        ) {
            // TODO: Implement proper NDEF to BLE parsing
            // This requires decoding the NDEF payload according to
            // NFC Forum Connection Handover spec and ISO 18013-5
            return ConnectionMethodResult(
                supported = false,
                connectionMethod = null,
                role = role
            )
        }

        return null
    }

    override fun disambiguate(connectionMethods: List<ConnectionMethod>, role: MdocRole): List<ConnectionMethod> {
        return BleConnectionMethod.disambiguate(connectionMethods, role)
    }

    override fun combine(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod> {
        return BleConnectionMethod.combine(connectionMethods)
    }

    override fun shouldSkipUuids(connectionMethod: ConnectionMethod): Boolean {
        val bleMethod = connectionMethod as? BleConnectionMethod ?: return false
        return bleMethod.options.centralClientMode
    }

    private object BleAdType {
        const val LE_ROLE: UByte = 0x1Cu
        const val LE_COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS: UByte = 0x07u
        const val LE_BLUETOOTH_MAC_ADDRESS: UByte = 0x1Bu
    }

    private object BleRole {
        const val PERIPHERAL_ONLY: UByte = 0x00u
        const val CENTRAL_CLIENT_ONLY: UByte = 0x01u
        const val BOTH_ROLES_PERIPHERAL_PREFERRED: UByte = 0x02u
        const val BOTH_ROLES_CENTRAL_PREFERRED: UByte = 0x03u
    }

    private companion object {
        const val MIME_TYPE_CONNECTION_HANDOVER_BLE = "application/vnd.bluetooth.le.oob"
    }
}
