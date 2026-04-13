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
 */

package com.sphereon.mdoc.transfer.device

import com.sphereon.cbor.NumberLabel
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalOptions", exact = true)
sealed class DeviceRetrievalOptions

data class RestApiOptions(
    val uri: String,
) : DeviceRetrievalOptions() {
    override fun toString(): String = "RestApiOptions(uri='$uri')"

    companion object {
        @JsStatic
        val URI = NumberLabel(0)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("WifiAwareOptions", exact = true)
data class WifiAwareOptions(
    val passPhrase: String?,
    val channelInfoOperatingClass: UInt? = null,
    val channelInfoChannelNumber: UInt? = null,
    val supportedBands: ByteArray? = null,
) : DeviceRetrievalOptions() {
    override fun toString(): String =
        "WifiAwareOptions(passPhrase=$passPhrase, channelInfoOperatingClass=$channelInfoOperatingClass, channelInfoChannelNumber=$channelInfoChannelNumber, supportedBands=${supportedBands?.contentToString()})"

    companion object {
        @JsStatic
        val PASS_PHRASE = NumberLabel(0)

        @JsStatic
        val CHANNEL_INFO_OPERATING_CLASS = NumberLabel(1)

        @JsStatic
        val CHANNEL_INFO_CHANNEL_NUMBER = NumberLabel(2)

        @JsStatic
        val SUPPORTED_BANDS = NumberLabel(3)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleOptions", exact = true)
data class BleOptions(
    val peripheralServerMode: Boolean,
    val centralClientMode: Boolean,
    val peripheralServerModeUuid: Uuid? = null,
    val centralClientModeUuid: Uuid? = null,
    val peripheralServerModeDeviceAddress: ByteArray? = null,
) : DeviceRetrievalOptions() {
    override fun toString(): String =
        "BleOptions(peripheralServerMode=$peripheralServerMode, centralClientMode=$centralClientMode, peripheralServerModeUuid=$peripheralServerModeUuid, centralClientModeUuid=$centralClientModeUuid, peripheralServerModeDeviceAddress=$peripheralServerModeDeviceAddress)"

    companion object {
        @JsStatic
        val PERIPHERAL_SERVER_MODE = NumberLabel(0)

        @JsStatic
        val CENTRAL_CLIENT_MODE = NumberLabel(1)

        @JsStatic
        val PERIPHERAL_SERVER_MODE_UUID = NumberLabel(10)

        @JsStatic
        val CENTRAL_CLIENT_MODE_UUID = NumberLabel(11)

        @JsStatic
        val PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS = NumberLabel(20)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcOptions", exact = true)
data class NfcOptions(
    val maxCommandDataFieldLength: UInt,
    val maxResponseDataFieldLength: UInt,
) : DeviceRetrievalOptions() {
    override fun toString(): String = "NfcOptions(maxCommandDataFieldLength=$maxCommandDataFieldLength, maxResponseDataFieldLength=$maxResponseDataFieldLength)"

    companion object {
        @JsStatic
        val MAX_COMMAND_DATA_FIELD_LENGTH = NumberLabel(0)

        @JsStatic
        val MAX_RESPONSE_DATA_FIELD_LENGTH = NumberLabel(1)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpOptions", exact = true)
data class Oid4vpOptions(
    val clientId: String,
    val requestUri: String? = null,
    val responseUri: String? = null,
    val nonce: String? = null,
    val presentationDefinitionUri: String? = null,
) : DeviceRetrievalOptions() {
    init {
        requestUri?.let {
            require(it.startsWith("https://")) {
                "OID4VP request_uri must use HTTPS per ISO 18013-7 B.4.2.2"
            }
        }

        responseUri?.let {
            if (it.isNotEmpty()) {
                require(it.startsWith("https://")) {
                    "OID4VP response_uri must use HTTPS per ISO 18013-7 B.4.2.3.2"
                }
            }
        }

        nonce?.let {
            if (it.isNotEmpty()) {
                require(it.length >= 16) {
                    "OID4VP nonce must be at least 16 bytes per ISO 18013-7 B.5.3"
                }
            }
        }

        presentationDefinitionUri?.let {
            require(it.startsWith("https://")) {
                "OID4VP presentation_definition_uri must use HTTPS"
            }
        }
    }

    override fun toString(): String = "Oid4vpOptions(clientId='$clientId', requestUri=$requestUri, responseUri='$responseUri', nonce='***', presentationDefinitionUri=$presentationDefinitionUri)"

    companion object {
        @JsStatic
        val CLIENT_ID = NumberLabel(0)

        @JsStatic
        val RESPONSE_URI = NumberLabel(1)

        @JsStatic
        val NONCE = NumberLabel(2)

        @JsStatic
        val REQUEST_URI = NumberLabel(3)

        @JsStatic
        val PRESENTATION_DEFINITION_URI = NumberLabel(4)
    }
}
