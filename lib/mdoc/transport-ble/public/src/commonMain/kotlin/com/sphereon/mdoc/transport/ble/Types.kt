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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generic BLE GATT service and characteristic constants.
 */
object GenericAttributeServiceChars {
    object Command {
        const val LAST_PART = "0x00"
        const val DISCONNECT = "0x01"
    }

    object ByUuid {
        const val GENERIC_ACCESS = "00001800-0000-1000-8000-00805f9b34fb"
        const val GENERIC_ATTRIBUTE = "00001801-0000-1000-8000-00805f9b34fb"
    }
}

/**
 * mDoc holder (device) BLE service characteristics.
 * These UUIDs are defined in ISO/IEC 18013-5 for the holder's GATT service.
 */
object MdocHolderServiceChars {
    object Command {
        const val END = "0x02"
    }

    object ByUuid {
        val STATE = Uuid.parse("00000001-A123-48CE-896B-4C76973373E6")
        val CLIENT_2_SERVER = Uuid.parse("00000002-A123-48CE-896B-4C76973373E6")
        val SERVER_2_CLIENT = Uuid.parse("00000003-A123-48CE-896B-4C76973373E6")
    }
}

/**
 * mDoc reader (verifier) BLE service characteristics.
 * These UUIDs are defined in ISO/IEC 18013-5 for the reader's GATT service.
 */
object MdocReaderServiceChars {
    object Command {
        const val LAST_PART: Int = 0x00
        const val START: Int = 0x01
        const val END: Int = 0x02
    }

    object ByUuid {
        val STATE = Uuid.parse("00000005-A123-48CE-896B-4C76973373E6")
        val CLIENT_2_SERVER = Uuid.parse("00000006-A123-48CE-896B-4C76973373E6")
        val SERVER_2_CLIENT = Uuid.parse("00000007-A123-48CE-896B-4C76973373E6")
        val IDENT = Uuid.parse("00000008-A123-48CE-896B-4C76973373E6")
    }
}

/**
 * Interface defining the GATT characteristics used for mDoc BLE communication.
 *
 * Different characteristics are used for holder and reader roles.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocBleServiceCharacteristics", exact = true)
interface MdocBleServiceCharacteristics {
    /**
     * State characteristic UUID - used to notify the peer of state changes.
     */
    val state: Uuid

    /**
     * Client to Server characteristic UUID - central device writes to this.
     */
    val client2Server: Uuid

    /**
     * Server to Client characteristic UUID - peripheral device notifies on this.
     */
    val server2Client: Uuid

    /**
     * Identification characteristic UUID (reader only) - used for HKDF-based identification.
     */
    val ident: Uuid?
}

/**
 * BLE service characteristics for mDoc holder (device) role.
 */
object MdocHolderBleServiceCharacteristics : MdocBleServiceCharacteristics {
    override val state: Uuid = MdocHolderServiceChars.ByUuid.STATE
    override val client2Server: Uuid = MdocHolderServiceChars.ByUuid.CLIENT_2_SERVER
    override val server2Client: Uuid = MdocHolderServiceChars.ByUuid.SERVER_2_CLIENT
    override val ident: Uuid? = null
}

/**
 * BLE service characteristics for mDoc reader (verifier) role.
 */
object MdocReaderBleServiceCharacteristics : MdocBleServiceCharacteristics {
    override val state: Uuid = MdocReaderServiceChars.ByUuid.STATE
    override val client2Server: Uuid = MdocReaderServiceChars.ByUuid.CLIENT_2_SERVER
    override val server2Client: Uuid = MdocReaderServiceChars.ByUuid.SERVER_2_CLIENT
    override val ident: Uuid = MdocReaderServiceChars.ByUuid.IDENT
}
