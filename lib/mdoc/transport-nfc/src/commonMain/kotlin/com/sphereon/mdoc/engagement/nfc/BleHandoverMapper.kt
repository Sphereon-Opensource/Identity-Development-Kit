/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

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
        skipUuids: Boolean,
    ): NdefConversionResult?

    fun fromNdef(
        record: NdefRecord,
        role: MdocRole,
        uuid: Uuid? = null,
    ): ConnectionMethodResult?

    fun disambiguate(
        connectionMethods: List<ConnectionMethod>,
        role: MdocRole,
    ): List<ConnectionMethod>

    fun combine(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod>

    fun shouldSkipUuids(connectionMethod: ConnectionMethod): Boolean
}

/**
 * Optional provider interface for DI components to supply a [BleHandoverMapper].
 */
interface BleHandoverMapperProvider {
    val bleHandoverMapper: BleHandoverMapper
}
