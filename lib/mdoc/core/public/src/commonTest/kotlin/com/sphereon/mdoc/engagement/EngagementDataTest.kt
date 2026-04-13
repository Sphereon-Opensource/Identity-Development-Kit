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
 *
 */

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
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
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodecImpl
import com.sphereon.mdoc.transfer.reader.ReaderEngagementSecurity
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
    private val deviceEngagementCborCodec = DeviceEngagementCborCodecImpl()
    private val coseKeyCborCodec = CoseKeyCborCodecImpl()
    private val readerEngagementCborCodec = ReaderEngagementCborCodecImpl()

    private fun holderBuilder() =
        EngagementData.holderBuilder(
            coseKeyCborCodec = coseKeyCborCodec,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            readerEngagementCborCodec = readerEngagementCborCodec,
        )

    private fun readerBuilder() =
        EngagementData.readerBuilder(
            coseKeyCborCodec = coseKeyCborCodec,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            readerEngagementCborCodec = readerEngagementCborCodec,
        )

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun createResolvedKeyInfo(): ResolvedKeyInfo<CoseKeyType> =
        ResolvedKeyInfo(
            key = createTestCoseKey(),
            keyVisibility = KeyVisibility.PUBLIC,
        )

    private fun createBleRetrievalMethod(
        peripheralServerMode: Boolean = true,
        centralClientMode: Boolean = false,
        uuid: Uuid = Uuid.random(),
    ): DeviceRetrievalMethod {
        val bleOptions =
            BleOptions(
                peripheralServerMode = peripheralServerMode,
                centralClientMode = centralClientMode,
                peripheralServerModeUuid = if (peripheralServerMode) uuid else null,
                centralClientModeUuid = if (centralClientMode) uuid else null,
            )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = bleOptions,
        )
    }

    private fun createRestApiRetrievalMethod(): DeviceRetrievalMethod =
        DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.WEBSITE,
            retrievalOptions = RestApiOptions(uri = "https://example.com/api"),
        )

    private fun createReaderEngagement(): ReaderEngagement {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(coseKeyCborCodec.encode(testKey).getOrThrow(), testKey)
        return ReaderEngagement.V1_0(
            security = ReaderEngagementSecurity(cipherSuite = 1u, eReaderKeyBytes = eReaderKeyBytes),
            deviceRetrievalMethods = arrayOf(createRestApiRetrievalMethod()),
            original = null,
        )
    }

    private fun withEncodedDeviceEngagement(engagementData: EngagementData): EngagementData {
        val currentEngagement = engagementData.getDeviceEngagement().data { deviceEngagementCborCodec.decode(it).getOrThrow().value }
        val publicDeviceKey = CoseKey.fromDTO(engagementData.getEphemeralKey().key).toPublicKey()
        val encodedDeviceKeyBytes = coseKeyCborCodec.encode(publicDeviceKey).getOrThrow()
        val encodedDeviceKey: CborEncodedItem<CoseKeyType> =
            CborEncodedItem(
                encodedDeviceKeyBytes,
                publicDeviceKey.copy(original = encodedDeviceKeyBytes),
            )
        val engagementWithEncodedKey =
            when (currentEngagement) {
                is DeviceEngagement.V1_0 -> {
                    currentEngagement.copy(
                        security = currentEngagement.security.copy(eDeviceKeyBytes = encodedDeviceKey),
                        original = null,
                    )
                }

                is DeviceEngagement.V1_1 -> {
                    currentEngagement.copy(
                        security = currentEngagement.security.copy(eDeviceKeyBytes = encodedDeviceKey),
                        original = null,
                    )
                }
            }

        return engagementData.withDeviceEngagement(deviceEngagementCborCodec.encodeItem(engagementWithEncodedKey).getOrThrow())
    }

    // HolderBuilder tests

    @Test
    fun testHolderBuilderCreate() {
        val builder = holderBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testReaderBuilderCreate() {
        val builder = readerBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testBuilderWithEphemeralKeyAndRetrievalMethods() {
        val key = createResolvedKeyInfo()
        val uuid = Uuid.random()
        val method = createBleRetrievalMethod(uuid = uuid)

        val engagementData =
            holderBuilder()
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
            holderBuilder()
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()
        }
    }

    @Test
    fun testBuilderFailsWithoutRetrievalMethods() {
        val key = createResolvedKeyInfo()

        assertFailsWith<IllegalArgumentException> {
            holderBuilder()
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
            holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .build()
        }
    }

    @Test
    fun testBuilderWithQrEngagement() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData =
            holderBuilder()
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
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()

        val deviceEngagement = engagementData.getDeviceEngagement()
        assertNotNull(deviceEngagement)
    }

    @Test
    fun testGetDeviceEngagementWithCodecRetainsOriginalBytes() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData =
            holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()
                .let(::withEncodedDeviceEngagement)

        val deviceEngagement = engagementData.getDeviceEngagement()
        val decodedDeviceEngagement = deviceEngagement.data { deviceEngagementCborCodec.decode(it).getOrThrow().value }

        assertNotNull(decodedDeviceEngagement.original)
        assertContentEquals(deviceEngagement.value.taggedItem.value, decodedDeviceEngagement.original)
    }

    @Test
    fun testGenerateEngagementUri() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()

        val uri = engagementData.generateEngagementUri("custom:")
        assertNotNull(uri)
        assertTrue(uri.startsWith("custom:"))
    }

    @Test
    fun testGenerateDeviceEngagementUriUsesEncodedItemBytesWhenCodecIsPresent() {
        val key = createResolvedKeyInfo()
        val method = createBleRetrievalMethod()

        val engagementData =
            holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()
                .let(::withEncodedDeviceEngagement)

        val deviceEngagement = engagementData.getDeviceEngagement()
        val uri = engagementData.generateEngagementUri()

        assertContentEquals(deviceEngagement.value.taggedItem.value, uri.substring(5).decodeFromBase64Url())
    }

    @Test
    fun testGenerateReaderEngagementUriRetainsOriginalBytes() {
        val key = createResolvedKeyInfo()
        val readerEngagement = createReaderEngagement()

        val engagementData =
            readerBuilder()
                .withEphemeralKey(key)
                .withReaderEngagement(readerEngagement)
                .build()

        val uri = engagementData.generateEngagementUri()
        val retainedReaderEngagement = engagementData.getReaderEngagement()

        assertNotNull(uri)
        assertTrue(uri.startsWith("mdoc://"))
        assertNotNull(retainedReaderEngagement)
        assertNotNull(retainedReaderEngagement.original)
        assertContentEquals(uri.substring(7).decodeFromBase64Url(), retainedReaderEngagement.original)
    }

    @Test
    fun testReaderBuilderWithStoredReaderEngagementUriReusesStoredUri() {
        val key = createResolvedKeyInfo()
        val readerEngagement = createReaderEngagement()
        val codec = ReaderEngagementCborCodecImpl()
        val encodedReaderEngagement = codec.encode(readerEngagement).getOrThrow()
        val retainedReaderEngagement = readerEngagement.copyWithOriginal(encodedReaderEngagement)
        val uri = codec.encodeUri(retainedReaderEngagement, "mdoc://").getOrThrow()

        val engagementData =
            readerBuilder()
                .withEphemeralKey(key)
                .withReaderEngagement(retainedReaderEngagement, uri)
                .build()

        assertEquals(uri, engagementData.generateEngagementUri())
        assertNotNull(engagementData.getReaderEngagement())
        assertContentEquals(encodedReaderEngagement, engagementData.getReaderEngagement()!!.original)
    }

    @Test
    fun testReaderBuilderWithReaderEngagementUriMaterializesReaderEngagement() {
        val key = createResolvedKeyInfo()
        val readerEngagement = createReaderEngagement()
        val uri = readerEngagementCborCodec.encodeUri(readerEngagement, "mdoc://").getOrThrow()

        val engagementData =
            readerBuilder()
                .withEphemeralKey(key)
                .withReaderEngagementUri(uri)
                .build()

        val retainedReaderEngagement = engagementData.getReaderEngagement()

        assertNotNull(retainedReaderEngagement)
        assertContentEquals(uri.substring(7).decodeFromBase64Url(), retainedReaderEngagement.original)
        assertTrue(engagementData.isReaderEngagement())
        assertTrue(engagementData.isRestApiRetrievalSupported())
    }

    // getUuid tests

    @Test
    fun testGetUuidWithBlePeripheralMode() {
        val key = createResolvedKeyInfo()
        val uuid = Uuid.random()
        val method = createBleRetrievalMethod(peripheralServerMode = true, uuid = uuid)

        val engagementData =
            holderBuilder()
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

        val holderEngagement =
            holderBuilder()
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

        val engagementData =
            holderBuilder()
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
        val method =
            DeviceRetrievalMethod(
                type = DeviceRetrievalMethodType.BLE,
                retrievalOptions =
                    BleOptions(
                        peripheralServerMode = true,
                        centralClientMode = true,
                        peripheralServerModeUuid = uuid,
                        centralClientModeUuid = uuid,
                    ),
            )

        val engagementData =
            holderBuilder()
                .withEphemeralKey(key)
                .withRetrievalMethods(setOf(method))
                .withQrEngagement()
                .build()

        // The builder should split this into 2 methods
        assertEquals(2, engagementData.getRetrievalMethods().size)
    }
}
