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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DeviceRetrievalOptionsTest {
    @Test
    fun restApiOptions_exposes_uri_and_label() {
        val options = RestApiOptions(uri = "https://example.com/api")

        assertEquals("https://example.com/api", options.uri)
        assertEquals(0, RestApiOptions.URI.value)
        assertTrue(options.toString().contains("https://example.com/api"))
    }

    @Test
    fun bleOptions_exposes_modes_and_labels() {
        val peripheralUuid = Uuid.random()
        val centralUuid = Uuid.random()
        val options =
            BleOptions(
                peripheralServerMode = true,
                centralClientMode = true,
                peripheralServerModeUuid = peripheralUuid,
                centralClientModeUuid = centralUuid,
                peripheralServerModeDeviceAddress = byteArrayOf(1, 2, 3, 4, 5, 6),
            )

        assertTrue(options.peripheralServerMode)
        assertTrue(options.centralClientMode)
        assertEquals(peripheralUuid, options.peripheralServerModeUuid)
        assertEquals(centralUuid, options.centralClientModeUuid)
        assertEquals(0, BleOptions.PERIPHERAL_SERVER_MODE.value)
        assertEquals(20, BleOptions.PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS.value)
    }

    @Test
    fun nfcOptions_exposes_lengths_and_labels() {
        val options =
            NfcOptions(
                maxCommandDataFieldLength = 256u,
                maxResponseDataFieldLength = 512u,
            )

        assertEquals(256u, options.maxCommandDataFieldLength)
        assertEquals(512u, options.maxResponseDataFieldLength)
        assertEquals(0, NfcOptions.MAX_COMMAND_DATA_FIELD_LENGTH.value)
        assertEquals(1, NfcOptions.MAX_RESPONSE_DATA_FIELD_LENGTH.value)
    }

    @Test
    fun wifiAwareOptions_exposes_fields_and_labels() {
        val options =
            WifiAwareOptions(
                passPhrase = "my-passphrase",
                channelInfoOperatingClass = 1u,
                channelInfoChannelNumber = 6u,
                supportedBands = byteArrayOf(1, 2),
            )

        assertEquals("my-passphrase", options.passPhrase)
        assertEquals(1u, options.channelInfoOperatingClass)
        assertEquals(6u, options.channelInfoChannelNumber)
        assertEquals(3, WifiAwareOptions.SUPPORTED_BANDS.value)
    }

    @Test
    fun oid4vpOptions_validates_https_and_nonce_constraints() {
        val options =
            Oid4vpOptions(
                clientId = "example.com",
                requestUri = "https://example.com/request",
                responseUri = "https://example.com/response",
                nonce = "1234567890123456",
                presentationDefinitionUri = "https://example.com/pd",
            )

        assertEquals("example.com", options.clientId)
        assertEquals("https://example.com/request", options.requestUri)
        assertEquals(4, Oid4vpOptions.PRESENTATION_DEFINITION_URI.value)
    }

    @Test
    fun oid4vpOptions_rejects_invalid_inputs() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(clientId = "example.com", requestUri = "http://example.com/request")
        }
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(clientId = "example.com", responseUri = "http://example.com/response")
        }
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(clientId = "example.com", nonce = "short")
        }
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(clientId = "example.com", presentationDefinitionUri = "http://example.com/pd")
        }
    }

    @Test
    fun oid4vpOptions_allows_empty_optional_values() {
        val options =
            Oid4vpOptions(
                clientId = "example.com",
                responseUri = "",
                nonce = "",
            )

        assertEquals("", options.responseUri)
        assertEquals("", options.nonce)
        assertNull(options.requestUri)
    }
}
