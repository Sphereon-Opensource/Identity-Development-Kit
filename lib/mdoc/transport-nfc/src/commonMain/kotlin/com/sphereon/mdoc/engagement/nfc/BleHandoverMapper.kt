package com.sphereon.mdoc.engagement.nfc

import com.sphereon.data.link.nfc.model.NdefRecord
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transport.ConnectionMethod
import kotlin.uuid.Uuid

/**
 * Optional BLE handover mapper used by NFC engagement to convert BLE
 * connection methods to/from NDEF records.
 */
interface BleHandoverMapper {
    fun toNdef(
        connectionMethod: ConnectionMethod,
        alternativeCarrierReferences: List<String>,
        role: MdocRole,
        skipUuids: Boolean
    ): NdefConversionResult?

    fun fromNdef(
        record: NdefRecord,
        role: MdocRole,
        uuid: Uuid? = null
    ): ConnectionMethodResult?

    fun disambiguate(connectionMethods: List<ConnectionMethod>, role: MdocRole): List<ConnectionMethod>

    fun combine(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod>

    fun shouldSkipUuids(connectionMethod: ConnectionMethod): Boolean
}

/**
 * Optional provider interface for DI components to supply a [BleHandoverMapper].
 */
interface BleHandoverMapperProvider {
    val bleHandoverMapper: BleHandoverMapper
}
