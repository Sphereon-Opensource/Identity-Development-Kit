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

package com.sphereon.mdoc.transfer.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for DeviceRetrievalOptions sealed class and its implementations.
 */
@OptIn(ExperimentalUuidApi::class)
class DeviceRetrievalOptionsTest {

    // RestApiOptions tests

    @Test
    fun testRestApiOptionsCreation() {
        val options = RestApiOptions(uri = "https://example.com/api")
        assertEquals("https://example.com/api", options.uri)
    }

    @Test
    fun testRestApiOptionsToString() {
        val options = RestApiOptions(uri = "https://example.com/api")
        val str = options.toString()
        assertTrue(str.contains("RestApiOptions"))
        assertTrue(str.contains("https://example.com/api"))
    }

    @Test
    fun testRestApiOptionsCborBuilder() {
        val options = RestApiOptions(uri = "https://example.com/api")
        val builder = options.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testRestApiOptionsEncodeDecode() {
        val options = RestApiOptions(uri = "https://example.com/api")
        val bytes = options.encodeCbor()
        val decoded = RestApiOptions.decodeCbor(bytes)
        assertEquals(options.uri, decoded.uri)
    }

    @Test
    fun testRestApiOptionsCompanionLabels() {
        assertEquals(0, RestApiOptions.URI.value)
    }

    // BleOptions tests

    @Test
    fun testBleOptionsPeripheralServerMode() {
        val uuid = Uuid.random()
        val options = BleOptions(
            peripheralServerMode = true,
            centralClientMode = false,
            peripheralServerModeUuid = uuid
        )

        assertTrue(options.peripheralServerMode)
        assertEquals(false, options.centralClientMode)
        assertEquals(uuid, options.peripheralServerModeUuid)
        assertNull(options.centralClientModeUuid)
    }

    @Test
    fun testBleOptionsCentralClientMode() {
        val uuid = Uuid.random()
        val options = BleOptions(
            peripheralServerMode = false,
            centralClientMode = true,
            centralClientModeUuid = uuid
        )

        assertEquals(false, options.peripheralServerMode)
        assertTrue(options.centralClientMode)
        assertNull(options.peripheralServerModeUuid)
        assertEquals(uuid, options.centralClientModeUuid)
    }

    @Test
    fun testBleOptionsBothModes() {
        val peripheralUuid = Uuid.random()
        val centralUuid = Uuid.random()
        val options = BleOptions(
            peripheralServerMode = true,
            centralClientMode = true,
            peripheralServerModeUuid = peripheralUuid,
            centralClientModeUuid = centralUuid
        )

        assertTrue(options.peripheralServerMode)
        assertTrue(options.centralClientMode)
        assertEquals(peripheralUuid, options.peripheralServerModeUuid)
        assertEquals(centralUuid, options.centralClientModeUuid)
    }

    @Test
    fun testBleOptionsWithDeviceAddress() {
        val deviceAddress = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val options = BleOptions(
            peripheralServerMode = true,
            centralClientMode = false,
            peripheralServerModeUuid = Uuid.random(),
            peripheralServerModeDeviceAddress = deviceAddress
        )

        assertNotNull(options.peripheralServerModeDeviceAddress)
        assertTrue(deviceAddress.contentEquals(options.peripheralServerModeDeviceAddress!!))
    }

    @Test
    fun testBleOptionsToString() {
        val options = BleOptions(
            peripheralServerMode = true,
            centralClientMode = false
        )
        val str = options.toString()
        assertTrue(str.contains("BleOptions"))
        assertTrue(str.contains("peripheralServerMode=true"))
        assertTrue(str.contains("centralClientMode=false"))
    }

    @Test
    fun testBleOptionsCborBuilder() {
        val options = BleOptions(
            peripheralServerMode = true,
            centralClientMode = false,
            peripheralServerModeUuid = Uuid.random()
        )
        val builder = options.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testBleOptionsEncodeDecode() {
        val peripheralUuid = Uuid.random()
        val centralUuid = Uuid.random()
        val options = BleOptions(
            peripheralServerMode = true,
            centralClientMode = true,
            peripheralServerModeUuid = peripheralUuid,
            centralClientModeUuid = centralUuid
        )
        val bytes = options.encodeCbor()
        val decoded = BleOptions.decodeCbor(bytes)

        assertEquals(options.peripheralServerMode, decoded.peripheralServerMode)
        assertEquals(options.centralClientMode, decoded.centralClientMode)
        assertEquals(options.peripheralServerModeUuid, decoded.peripheralServerModeUuid)
        assertEquals(options.centralClientModeUuid, decoded.centralClientModeUuid)
    }

    @Test
    fun testBleOptionsCompanionLabels() {
        assertEquals(0, BleOptions.PERIPHERAL_SERVER_MODE.value)
        assertEquals(1, BleOptions.CENTRAL_CLIENT_MODE.value)
        assertEquals(10, BleOptions.PERIPHERAL_SERVER_MODE_UUID.value)
        assertEquals(11, BleOptions.CENTRAL_CLIENT_MODE_UUID.value)
        assertEquals(20, BleOptions.PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS.value)
    }

    // NfcOptions tests

    @Test
    fun testNfcOptionsCreation() {
        val options = NfcOptions(
            maxCommandDataFieldLength = 256u,
            maxResponseDataFieldLength = 512u
        )

        assertEquals(256u, options.maxCommandDataFieldLength)
        assertEquals(512u, options.maxResponseDataFieldLength)
    }

    @Test
    fun testNfcOptionsToString() {
        val options = NfcOptions(
            maxCommandDataFieldLength = 256u,
            maxResponseDataFieldLength = 512u
        )
        val str = options.toString()
        assertTrue(str.contains("NfcOptions"))
        assertTrue(str.contains("256"))
        assertTrue(str.contains("512"))
    }

    @Test
    fun testNfcOptionsCborBuilder() {
        val options = NfcOptions(
            maxCommandDataFieldLength = 256u,
            maxResponseDataFieldLength = 512u
        )
        val builder = options.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testNfcOptionsEncodeDecode() {
        val options = NfcOptions(
            maxCommandDataFieldLength = 256u,
            maxResponseDataFieldLength = 512u
        )
        val bytes = options.encodeCbor()
        val decoded = NfcOptions.decodeCbor(bytes)

        assertEquals(options.maxCommandDataFieldLength, decoded.maxCommandDataFieldLength)
        assertEquals(options.maxResponseDataFieldLength, decoded.maxResponseDataFieldLength)
    }

    @Test
    fun testNfcOptionsCompanionLabels() {
        assertEquals(0, NfcOptions.MAX_COMMAND_DATA_FIELD_LENGTH.value)
        assertEquals(1, NfcOptions.MAX_RESPONSE_DATA_FIELD_LENGTH.value)
    }

    // WifiAwareOptions tests

    @Test
    fun testWifiAwareOptionsWithPassPhrase() {
        val options = WifiAwareOptions(passPhrase = "my-passphrase")

        assertEquals("my-passphrase", options.passPhrase)
        assertNull(options.channelInfoOperatingClass)
        assertNull(options.channelInfoChannelNumber)
        assertNull(options.supportedBands)
    }

    @Test
    fun testWifiAwareOptionsWithAllFields() {
        val supportedBands = byteArrayOf(0x01, 0x02)
        val options = WifiAwareOptions(
            passPhrase = "my-passphrase",
            channelInfoOperatingClass = 1u,
            channelInfoChannelNumber = 6u,
            supportedBands = supportedBands
        )

        assertEquals("my-passphrase", options.passPhrase)
        assertEquals(1u, options.channelInfoOperatingClass)
        assertEquals(6u, options.channelInfoChannelNumber)
        assertNotNull(options.supportedBands)
    }

    @Test
    fun testWifiAwareOptionsToString() {
        val options = WifiAwareOptions(passPhrase = "test")
        val str = options.toString()
        assertTrue(str.contains("WifiAwareOptions"))
        assertTrue(str.contains("passPhrase=test"))
    }

    @Test
    fun testWifiAwareOptionsCborBuilder() {
        val options = WifiAwareOptions(passPhrase = "test")
        val builder = options.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testWifiAwareOptionsEncodeDecode() {
        val options = WifiAwareOptions(
            passPhrase = "my-passphrase",
            channelInfoOperatingClass = 1u,
            channelInfoChannelNumber = 6u
        )
        val bytes = options.encodeCbor()
        val decoded = WifiAwareOptions.decodeCbor(bytes)

        assertEquals(options.passPhrase, decoded.passPhrase)
        assertEquals(options.channelInfoOperatingClass, decoded.channelInfoOperatingClass)
        assertEquals(options.channelInfoChannelNumber, decoded.channelInfoChannelNumber)
    }

    @Test
    fun testWifiAwareOptionsCompanionLabels() {
        assertEquals(0, WifiAwareOptions.PASS_PHRASE.value)
        assertEquals(1, WifiAwareOptions.CHANNEL_INFO_OPERATING_CLASS.value)
        assertEquals(2, WifiAwareOptions.CHANNEL_INFO_CHANNEL_NUMBER.value)
        assertEquals(3, WifiAwareOptions.SUPPORTED_BANDS.value)
    }

    // Oid4vpOptions tests

    @Test
    fun testOid4vpOptionsMinimal() {
        val options = Oid4vpOptions(clientId = "example.com")
        assertEquals("example.com", options.clientId)
        assertNull(options.requestUri)
        assertNull(options.responseUri)
        assertNull(options.nonce)
        assertNull(options.presentationDefinitionUri)
    }

    @Test
    fun testOid4vpOptionsWithRequestUri() {
        val options = Oid4vpOptions(
            clientId = "example.com",
            requestUri = "https://example.com/request"
        )
        assertEquals("https://example.com/request", options.requestUri)
    }

    @Test
    fun testOid4vpOptionsWithAllFields() {
        val options = Oid4vpOptions(
            clientId = "example.com",
            requestUri = "https://example.com/request",
            responseUri = "https://example.com/response",
            nonce = "1234567890123456",  // 16 bytes minimum
            presentationDefinitionUri = "https://example.com/pd"
        )

        assertEquals("example.com", options.clientId)
        assertEquals("https://example.com/request", options.requestUri)
        assertEquals("https://example.com/response", options.responseUri)
        assertEquals("1234567890123456", options.nonce)
        assertEquals("https://example.com/pd", options.presentationDefinitionUri)
    }

    @Test
    fun testOid4vpOptionsInvalidRequestUriThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(
                clientId = "example.com",
                requestUri = "http://example.com/request"  // Must be HTTPS
            )
        }
    }

    @Test
    fun testOid4vpOptionsInvalidResponseUriThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(
                clientId = "example.com",
                responseUri = "http://example.com/response"  // Must be HTTPS
            )
        }
    }

    @Test
    fun testOid4vpOptionsInvalidNonceThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(
                clientId = "example.com",
                nonce = "short"  // Must be at least 16 bytes
            )
        }
    }

    @Test
    fun testOid4vpOptionsInvalidPresentationDefinitionUriThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4vpOptions(
                clientId = "example.com",
                presentationDefinitionUri = "http://example.com/pd"  // Must be HTTPS
            )
        }
    }

    @Test
    fun testOid4vpOptionsEmptyResponseUriIsAllowed() {
        // Empty string should be allowed (but not non-HTTPS)
        val options = Oid4vpOptions(
            clientId = "example.com",
            responseUri = ""
        )
        assertEquals("", options.responseUri)
    }

    @Test
    fun testOid4vpOptionsEmptyNonceIsAllowed() {
        // Empty string should be allowed (but not short non-empty)
        val options = Oid4vpOptions(
            clientId = "example.com",
            nonce = ""
        )
        assertEquals("", options.nonce)
    }

    @Test
    fun testOid4vpOptionsToString() {
        val options = Oid4vpOptions(clientId = "example.com")
        val str = options.toString()
        assertTrue(str.contains("Oid4vpOptions"))
        assertTrue(str.contains("example.com"))
    }

    @Test
    fun testOid4vpOptionsCborBuilder() {
        val options = Oid4vpOptions(clientId = "example.com")
        val builder = options.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testOid4vpOptionsEncodeDecode() {
        val options = Oid4vpOptions(
            clientId = "example.com",
            requestUri = "https://example.com/request",
            responseUri = "https://example.com/response",
            nonce = "1234567890123456"
        )
        val bytes = options.encodeCbor()
        val decoded = Oid4vpOptions.decodeCbor(bytes)

        assertEquals(options.clientId, decoded.clientId)
        assertEquals(options.requestUri, decoded.requestUri)
        assertEquals(options.responseUri, decoded.responseUri)
        assertEquals(options.nonce, decoded.nonce)
    }

    @Test
    fun testOid4vpOptionsCompanionLabels() {
        assertEquals(0, Oid4vpOptions.CLIENT_ID.value)
        assertEquals(1, Oid4vpOptions.RESPONSE_URI.value)
        assertEquals(2, Oid4vpOptions.NONCE.value)
        assertEquals(3, Oid4vpOptions.REQUEST_URI.value)
        assertEquals(4, Oid4vpOptions.PRESENTATION_DEFINITION_URI.value)
    }
}
