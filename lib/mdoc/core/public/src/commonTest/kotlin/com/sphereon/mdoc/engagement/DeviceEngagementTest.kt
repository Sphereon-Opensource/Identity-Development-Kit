/*
 * Â© 2026 Sphereon International B.V.
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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.testutil.encodeCoseKey
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for DeviceEngagement sealed class and its versions.
 */
@OptIn(ExperimentalUuidApi::class)
class DeviceEngagementTest {
    private val deviceEngagementCborCodec = DeviceEngagementCborCodecImpl()

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun createTestDeviceEngagementSecurity(): DeviceEngagementSecurity {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)
        return DeviceEngagementSecurity(cipherSuite = 1u, eDeviceKeyBytes = eDeviceKeyBytes)
    }

    private fun createBleRetrievalMethod(): DeviceRetrievalMethod {
        val bleOptions =
            BleOptions(
                peripheralServerMode = true,
                centralClientMode = false,
                peripheralServerModeUuid = Uuid.random(),
            )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = bleOptions,
        )
    }

    // DeviceEngagement.V1_0 tests

    @Test
    fun testDeviceEngagementV10Creation() {
        val security = createTestDeviceEngagementSecurity()

        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        assertEquals("1.0", engagement.version.toString())
        assertEquals(security.cipherSuite, engagement.security.cipherSuite)
        assertNull(engagement.deviceRetrievalMethods)
        assertNull(engagement.serverRetrievalMethod)
        assertNull(engagement.original)
    }

    @Test
    fun testDeviceEngagementV10WithRetrievalMethods() {
        val security = createTestDeviceEngagementSecurity()
        val retrievalMethod = createBleRetrievalMethod()

        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                deviceRetrievalMethods = arrayOf(retrievalMethod),
                original = null,
            )

        assertEquals(1, engagement.deviceRetrievalMethods?.size)
        assertEquals(DeviceRetrievalMethodType.BLE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testDeviceEngagementV10EncodeDecode() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        val encoded = deviceEngagementCborCodec.encode(engagement).getOrThrow()
        val decoded = deviceEngagementCborCodec.decode(encoded).getOrThrow().value

        assertTrue(decoded is DeviceEngagement.V1_0)
        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testDeviceEngagementV10CopyWithOriginal() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        val originalBytes = byteArrayOf(0x01, 0x02, 0x03)
        val copy = engagement.copyWithOriginal(originalBytes)

        assertTrue(copy is DeviceEngagement.V1_0)
        assertTrue(originalBytes.contentEquals(copy.original))
    }

    @Test
    fun testDeviceEngagementV10Equality() {
        val security = createTestDeviceEngagementSecurity()
        val engagement1 = DeviceEngagement.V1_0(security = security, original = null)
        val engagement2 = DeviceEngagement.V1_0(security = security, original = null)

        assertEquals(engagement1, engagement2)
        assertEquals(engagement1.hashCode(), engagement2.hashCode())
    }

    @Test
    fun testDeviceEngagementV10EqualitySameInstance() {
        val security = createTestDeviceEngagementSecurity()
        val engagement = DeviceEngagement.V1_0(security = security, original = null)

        assertEquals(engagement, engagement)
    }

    @Test
    fun testDeviceEngagementV10InequalityDifferentClass() {
        val security = createTestDeviceEngagementSecurity()
        val engagementV10 = DeviceEngagement.V1_0(security = security, original = null)
        val engagementV11 = DeviceEngagement.V1_1(security = security, original = null)

        assertNotEquals<Any>(engagementV10, engagementV11)
    }

    // DeviceEngagement.V1_1 tests

    @Test
    fun testDeviceEngagementV11Creation() {
        val security = createTestDeviceEngagementSecurity()

        val engagement =
            DeviceEngagement.V1_1(
                security = security,
                original = null,
            )

        assertEquals("1.1", engagement.version.toString())
        assertEquals(security.cipherSuite, engagement.security.cipherSuite)
        assertNull(engagement.deviceRetrievalMethods)
        assertNull(engagement.originInfos)
        assertNull(engagement.capabilities)
        assertNull(engagement.original)
    }

    @Test
    fun testDeviceEngagementV11WithRetrievalMethods() {
        val security = createTestDeviceEngagementSecurity()
        val retrievalMethod = createBleRetrievalMethod()

        val engagement =
            DeviceEngagement.V1_1(
                security = security,
                deviceRetrievalMethods = arrayOf(retrievalMethod),
                original = null,
            )

        assertEquals(1, engagement.deviceRetrievalMethods?.size)
        assertEquals(DeviceRetrievalMethodType.BLE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testDeviceEngagementV11EncodeDecode() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_1(
                security = security,
                original = null,
            )

        val encoded = deviceEngagementCborCodec.encode(engagement).getOrThrow()
        val decoded = deviceEngagementCborCodec.decode(encoded).getOrThrow().value

        assertTrue(decoded is DeviceEngagement.V1_1)
        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testDeviceEngagementV11CopyWithOriginal() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_1(
                security = security,
                original = null,
            )

        val originalBytes = byteArrayOf(0x04, 0x05, 0x06)
        val copy = engagement.copyWithOriginal(originalBytes)

        assertTrue(copy is DeviceEngagement.V1_1)
        assertTrue(originalBytes.contentEquals(copy.original))
    }

    @Test
    fun testDeviceEngagementV11Equality() {
        val security = createTestDeviceEngagementSecurity()
        val engagement1 = DeviceEngagement.V1_1(security = security, original = null)
        val engagement2 = DeviceEngagement.V1_1(security = security, original = null)

        assertEquals(engagement1, engagement2)
        assertEquals(engagement1.hashCode(), engagement2.hashCode())
    }

    @Test
    fun testDeviceEngagementV11EqualitySameInstance() {
        val security = createTestDeviceEngagementSecurity()
        val engagement = DeviceEngagement.V1_1(security = security, original = null)

        assertEquals(engagement, engagement)
    }

    @Test
    fun testDeviceEngagementV11InequalityNull() {
        val security = createTestDeviceEngagementSecurity()
        val engagement = DeviceEngagement.V1_1(security = security, original = null)

        assertFalse(engagement.equals(null))
    }

    // Engagement URI tests

    @Test
    fun testDeviceEngagementUriRoundTripWithCodec() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        val uri = "mdoc:${deviceEngagementCborCodec.encode(engagement).getOrThrow().encodeToBase64Url()}"
        assertTrue(uri.startsWith("mdoc:"))
        val decoded = deviceEngagementCborCodec.decode(uri.substring(5).decodeFromBase64Url()).getOrThrow().value
        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testDeviceEngagementUriInvalidScheme() {
        assertFailsWith<IllegalStateException> {
            check("invalid:test".startsWith("mdoc:")) { "Device Engagement URI must start with 'mdoc:' per ISO 18013-5. We got: invalid:test" }
        }
    }

    @Test
    fun testEncodeTag24WithCodec() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        val bytes = deviceEngagementCborCodec.encodeTag24(engagement).getOrThrow()
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testEncodeMessageWithCodec() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        val message = deviceEngagementCborCodec.encodeMessage(engagement).getOrThrow()
        assertNotNull(message)
        assertTrue(message.isNotEmpty())
    }

    @Test
    fun testDecodeMessageWithCodec() {
        val security = createTestDeviceEngagementSecurity()
        val engagement =
            DeviceEngagement.V1_0(
                security = security,
                original = null,
            )

        val message = deviceEngagementCborCodec.encodeMessage(engagement).getOrThrow()
        val decoded = deviceEngagementCborCodec.decodeMessage(message).getOrThrow().value

        assertEquals(engagement.version, decoded.version)
    }

    // Companion object labels tests

    @Test
    fun testDeviceEngagementCompanionLabels() {
        assertEquals(0, DeviceEngagement.VERSION.value)
        assertEquals(1, DeviceEngagement.SECURITY.value)
        assertEquals(2, DeviceEngagement.DEVICE_RETRIEVAL_METHODS.value)
        assertEquals(3, DeviceEngagement.SERVER_RETRIEVAL_METHOD.value)
        assertEquals(4, DeviceEngagement.PROTOCOL_INFO.value)
        assertEquals(5, DeviceEngagement.ORIGIN_INFOS.value)
        assertEquals(6, DeviceEngagement.CAPABILITIES.value)
    }
}
