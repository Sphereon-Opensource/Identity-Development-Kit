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

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DataRetrievalTransmissionType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for EngagementData class and its Builder.
 */
@OptIn(ExperimentalUuidApi::class)
class EngagementDataTest {

    private fun createTestCoseKey(): CoseKey {
        return CoseKeyJson.Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
    }

    private fun createResolvedKeyInfo(): ResolvedKeyInfo<CoseKeyType> {
        return ResolvedKeyInfo(
            key = createTestCoseKey(),
            keyVisibility = KeyVisibility.PUBLIC
        )
    }

    private fun createBleRetrievalMethod(
        peripheralServerMode: Boolean = true,
        centralClientMode: Boolean = false,
        uuid: Uuid = Uuid.random()
    ): DeviceRetrievalMethod {
        val bleOptions = BleOptions(
            peripheralServerMode = peripheralServerMode,
            centralClientMode = centralClientMode,
            peripheralServerModeUuid = if (peripheralServerMode) uuid else null,
            centralClientModeUuid = if (centralClientMode) uuid else null
        )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = bleOptions
        )
    }

    private fun createRestApiRetrievalMethod(): DeviceRetrievalMethod {
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.WEBSITE,
            retrievalOptions = RestApiOptions(uri = "https://example.com/api")
        )
    }

    // HolderBuilder tests

    @Test
    fun testHolderBuilderCreate() {
        val builder = EngagementData.holderBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testReaderBuilderCreate() {
        val builder = EngagementData.readerBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithEphemeralKeyAndRetrievalMethods() {
        val key = createResolvedKeyInfo()
        val uuid = Uuid.random()
        val method = createBleRetrievalMethod(uuid = uuid)

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertNotNull(engagementData)
        assertEquals(MdocRole.MDOC, engagementData.getRole())
    }

    @Test
    fun testBuilderFailsWithoutEphemeralKey() {
        val method = createBleRetrievalMethod()

        assertFailsWith<IllegalArgumentException> {
            EngagementData.holderBuilder()
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()
        }
    }

    @Test
    fun testBuilderFailsWithoutRetrievalMethods() {
        val key = createResolvedKeyInfo()

        assertFailsWith<IllegalArgumentException> {
            EngagementData.holderBuilder()
                .withEphemeralKey(key)
                .withQrEngagement()
                .build()
        }
    }

    @Test
    fun testBuilderFailsWithoutEngagementMethods() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        assertFailsWith<IllegalArgumentException> {
            EngagementData.holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .build()
        }
    }

    @Test
    fun testBuilderWithQrEngagement() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertTrue(engagementData.isQrEngagementSupported())
    }

    @Test
    fun testBuilderWithNfcEngagementThrows() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        assertFailsWith<IllegalArgumentException> {
            EngagementData.holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .withNfcEngagement()
                .build()
        }
    }

    // EngagementData property tests

    @Test
    fun testIsDeviceEngagement() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertTrue(engagementData.isDeviceEngagement())
        assertFalse(engagementData.isReaderEngagement())
    }

    @Test
    fun testGetEphemeralKey() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertNotNull(engagementData.getEphemeralKey())
    }

    @Test
    fun testGetRetrievalMethods() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertEquals(1, engagementData.getRetrievalMethods().size)
    }

    @Test
    fun testGetCurve() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key, Curve.P_256)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertEquals(Curve.P_256, engagementData.getCurve())
    }

    // BLE retrieval tests

    @Test
    fun testIsBleRetrievalSupported() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertTrue(engagementData.isBleRetrievalSupported())
    }

    @Test
    fun testIsRestApiRetrievalSupportedFalse() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertFalse(engagementData.isRestApiRetrievalSupported())
    }

    @Test
    fun testIsNfcRetrievalNotSupported() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertFalse(engagementData.isNfcRetrievalSupported())
    }

    @Test
    fun testIsWifiAwareRetrievalNotSupported() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertFalse(engagementData.isWifiAwareRetrievalSupported())
    }

    @Test
    fun testRetrievalTransmissionTypesSupported() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val types = engagementData.retrievalTransmissionTypesSupported()
        assertTrue(types.contains(DataRetrievalTransmissionType.BLE))
        assertFalse(types.contains(DataRetrievalTransmissionType.NFC))
        assertFalse(types.contains(DataRetrievalTransmissionType.WIFI_AWARE))
    }

    // Engagement method tests

    @Test
    fun testIsQrEngagementSupported() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertTrue(engagementData.isQrEngagementSupported())
        assertFalse(engagementData.isNfcEngagementSupported())
    }

    @Test
    fun testGetEngagementMethods() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val methods = engagementData.getEngagementMethods()
        assertEquals(1, methods.size)
        assertTrue(methods.first() is QREngagementMethod)
    }

    // BLE UUID tests

    @Test
    fun testGetBlePeripheralServerModeUuid() {
        val key = createResolvedKeyInfo()
        val uuid = Uuid.random()
        val method = createBleRetrievalMethod(peripheralServerMode = true, uuid = uuid)

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertEquals(uuid, engagementData.getBlePeripheralServerModeUuid())
    }

    @Test
    fun testGetBleRetrievalMethod() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertNotNull(engagementData.getBleRetrievalMethod())
    }

    @Test
    fun testGetBleRetrievalMethodOptions() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        assertNotNull(engagementData.getBleRetrievalMethodOptions())
    }

    // DeviceEngagement tests

    @Test
    fun testGetDeviceEngagement() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val deviceEngagement = engagementData.getDeviceEngagement()
        assertNotNull(deviceEngagement)
    }

    @Test
    fun testGenerateEngagementUri() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val uri = engagementData.generateEngagementUri()
        assertNotNull(uri)
        assertTrue(uri.startsWith("mdoc:"))
    }

    @Test
    fun testGenerateEngagementUriWithCustomScheme() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val uri = engagementData.generateEngagementUri("custom:")
        assertNotNull(uri)
        assertTrue(uri.startsWith("custom:"))
    }

    // getUuid tests

    @Test
    fun testGetUuidWithBlePeripheralMode() {
        val key = createResolvedKeyInfo()
        val uuid = Uuid.random()
        val method = createBleRetrievalMethod(peripheralServerMode = true, uuid = uuid)

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val returnedUuid = engagementData.getUuid()
        assertEquals(uuid, returnedUuid)
    }

    // Static factory tests

    @Test
    fun testHolderFromDeviceKey() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        assertFailsWith<IllegalArgumentException> {
            // This will fail because no engagement methods are provided
            EngagementData.holderFromDeviceKey(key, setOf(method))
        }
    }

    @Test
    fun testVerifierFromDeviceEngagement() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val holderEngagement = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val deviceEngagement = holderEngagement.getDeviceEngagement()
        val verifierEngagement = EngagementData.verifierFromDeviceEngagement(deviceEngagement)

        assertNotNull(verifierEngagement)
        assertEquals(MdocRole.MDOC_READER, verifierEngagement.getRole())
    }

    // toString test

    @Test
    fun testToString() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        val str = engagementData.toString()
        assertTrue(str.contains("MdocEngagementData"))
    }

    // Builder with both BLE modes test

    @Test
    fun testBuilderSplitsBothBleModes() {
        val key = createResolvedKeyInfo()
        val uuid = Uuid.random()
        val method = DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = BleOptions(
                peripheralServerMode = true,
                centralClientMode = true,
                peripheralServerModeUuid = uuid,
                centralClientModeUuid = uuid
            )
        )

        val engagementData = EngagementData.holderBuilder()
            .withEphemeralKey(key)
            .withRetrievalMethods(setOf(method))
            .withQrEngagement()
            .build()

        // The builder should split this into 2 methods
        assertEquals(2, engagementData.getRetrievalMethods().size)
    }
}
