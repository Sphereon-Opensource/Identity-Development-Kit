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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.OriginInfoCategory
import com.sphereon.mdoc.transfer.OriginInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.WifiAwareOptions
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
 * Tests for ReaderEngagement sealed class and its versions.
 */
@OptIn(ExperimentalUuidApi::class)
class ReaderEngagementTest {

    private fun createTestCoseKey(): CoseKey {
        return CoseKeyJson.Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
    }

    private fun createTestReaderEngagementSecurity(): ReaderEngagementSecurity {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem.fromData<CoseKeyType>(testKey)
        return ReaderEngagementSecurity(cipherSuite = 1u, eReaderKeyBytes = eReaderKeyBytes)
    }

    private fun createBleRetrievalMethod(): DeviceRetrievalMethod {
        val bleOptions = BleOptions(
            peripheralServerMode = true,
            centralClientMode = false,
            peripheralServerModeUuid = Uuid.random()
        )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = bleOptions
        )
    }

    private fun createNfcRetrievalMethod(): DeviceRetrievalMethod {
        val nfcOptions = NfcOptions(
            maxCommandDataFieldLength = 256u,
            maxResponseDataFieldLength = 256u
        )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.NFC,
            retrievalOptions = nfcOptions
        )
    }

    private fun createWifiAwareRetrievalMethod(): DeviceRetrievalMethod {
        val wifiOptions = WifiAwareOptions(
            passPhrase = "testpassphrase"
        )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.WIFI_WARE,
            retrievalOptions = wifiOptions
        )
    }

    private fun createWebsiteRetrievalMethod(): DeviceRetrievalMethod {
        val restApiOptions = RestApiOptions(
            uri = "https://example.com/api"
        )
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.WEBSITE,
            retrievalOptions = restApiOptions
        )
    }

    // ReaderEngagement.V1_0 tests

    @Test
    fun testReaderEngagementV10Creation() {
        val security = createTestReaderEngagementSecurity()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        assertEquals("1.0", engagement.version.toString())
        assertEquals(security.cipherSuite, engagement.security.cipherSuite)
        assertNull(engagement.deviceRetrievalMethods)
        assertNull(engagement.protocolInfo)
        assertNull(engagement.original)
    }

    @Test
    fun testReaderEngagementV10WithRetrievalMethods() {
        val security = createTestReaderEngagementSecurity()
        val retrievalMethod = createBleRetrievalMethod()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(retrievalMethod),
            original = null
        )

        assertEquals(1, engagement.deviceRetrievalMethods?.size)
        assertEquals(DeviceRetrievalMethodType.BLE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testReaderEngagementV10CborBuilder() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val builder = engagement.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testReaderEngagementV10EncodeDecode() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val encoded = engagement.encodeCbor()
        val decoded = ReaderEngagement.decodeCbor(encoded)

        assertTrue(decoded is ReaderEngagement.V1_0)
        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testReaderEngagementV10CopyWithOriginal() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val originalBytes = byteArrayOf(0x01, 0x02, 0x03)
        val copy = engagement.copyWithOriginal(originalBytes)

        assertTrue(copy is ReaderEngagement.V1_0)
        assertTrue(originalBytes.contentEquals(copy.original))
    }

    @Test
    fun testReaderEngagementV10Equality() {
        val security = createTestReaderEngagementSecurity()
        val engagement1 = ReaderEngagement.V1_0(security = security, original = null)
        val engagement2 = ReaderEngagement.V1_0(security = security, original = null)

        assertEquals(engagement1, engagement2)
        assertEquals(engagement1.hashCode(), engagement2.hashCode())
    }

    @Test
    fun testReaderEngagementV10EqualitySameInstance() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(security = security, original = null)

        assertEquals(engagement, engagement)
    }

    @Test
    fun testReaderEngagementV10InequalityDifferentClass() {
        val security = createTestReaderEngagementSecurity()
        val engagementV10 = ReaderEngagement.V1_0(security = security, original = null)
        val engagementV11 = ReaderEngagement.V1_1(security = security, original = null)

        assertNotEquals<Any>(engagementV10, engagementV11)
    }

    @Test
    fun testReaderEngagementV10InequalityDifferentRetrievalMethods() {
        val security = createTestReaderEngagementSecurity()
        val engagement1 = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(createBleRetrievalMethod()),
            original = null
        )
        val engagement2 = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = null,
            original = null
        )

        assertNotEquals(engagement1, engagement2)
    }

    // ReaderEngagement.V1_1 tests

    @Test
    fun testReaderEngagementV11Creation() {
        val security = createTestReaderEngagementSecurity()

        val engagement = ReaderEngagement.V1_1(
            security = security,
            original = null
        )

        assertEquals("1.1", engagement.version.toString())
        assertEquals(security.cipherSuite, engagement.security.cipherSuite)
        assertNull(engagement.deviceRetrievalMethods)
        assertNull(engagement.originInfos)
        assertNull(engagement.capabilities)
        assertNull(engagement.original)
    }

    @Test
    fun testReaderEngagementV11WithRetrievalMethods() {
        val security = createTestReaderEngagementSecurity()
        val retrievalMethod = createBleRetrievalMethod()

        val engagement = ReaderEngagement.V1_1(
            security = security,
            deviceRetrievalMethods = arrayOf(retrievalMethod),
            original = null
        )

        assertEquals(1, engagement.deviceRetrievalMethods?.size)
        assertEquals(DeviceRetrievalMethodType.BLE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testReaderEngagementV11WithOriginInfos() {
        val security = createTestReaderEngagementSecurity()
        val originInfo = OriginInfo(
            cat = OriginInfoCategory(0u),
            type = OriginInfoType(1u),
            original = null
        )

        val engagement = ReaderEngagement.V1_1(
            security = security,
            originInfos = arrayOf(originInfo),
            original = null
        )

        assertEquals(1, engagement.originInfos?.size)
    }

    @Test
    fun testReaderEngagementV11WithCapabilities() {
        val security = createTestReaderEngagementSecurity()
        val capabilities = Capabilities()

        val engagement = ReaderEngagement.V1_1(
            security = security,
            capabilities = capabilities,
            original = null
        )

        assertNotNull(engagement.capabilities)
    }

    @Test
    fun testReaderEngagementV11CborBuilder() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_1(
            security = security,
            original = null
        )

        val builder = engagement.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testReaderEngagementV11EncodeDecode() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_1(
            security = security,
            original = null
        )

        val encoded = engagement.encodeCbor()
        val decoded = ReaderEngagement.decodeCbor(encoded)

        assertTrue(decoded is ReaderEngagement.V1_1)
        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testReaderEngagementV11CopyWithOriginal() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_1(
            security = security,
            original = null
        )

        val originalBytes = byteArrayOf(0x04, 0x05, 0x06)
        val copy = engagement.copyWithOriginal(originalBytes)

        assertTrue(copy is ReaderEngagement.V1_1)
        assertTrue(originalBytes.contentEquals(copy.original))
    }

    @Test
    fun testReaderEngagementV11Equality() {
        val security = createTestReaderEngagementSecurity()
        val engagement1 = ReaderEngagement.V1_1(security = security, original = null)
        val engagement2 = ReaderEngagement.V1_1(security = security, original = null)

        assertEquals(engagement1, engagement2)
        assertEquals(engagement1.hashCode(), engagement2.hashCode())
    }

    @Test
    fun testReaderEngagementV11EqualitySameInstance() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_1(security = security, original = null)

        assertEquals(engagement, engagement)
    }

    @Test
    fun testReaderEngagementV11InequalityNull() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_1(security = security, original = null)

        assertFalse(engagement.equals(null))
    }

    // Retrieval method helper tests

    @Test
    fun testHasBleRetrievalMethod() {
        val security = createTestReaderEngagementSecurity()
        val bleMethod = createBleRetrievalMethod()

        val engagementWithBle = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(bleMethod),
            original = null
        )
        val engagementWithoutBle = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        assertTrue(engagementWithBle.hasBleRetrievalMethod)
        assertFalse(engagementWithoutBle.hasBleRetrievalMethod)
    }

    @Test
    fun testHasNfcRetrievalMethod() {
        val security = createTestReaderEngagementSecurity()
        val nfcMethod = createNfcRetrievalMethod()

        val engagementWithNfc = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(nfcMethod),
            original = null
        )
        val engagementWithoutNfc = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        assertTrue(engagementWithNfc.hasNfcRetrievalMethod)
        assertFalse(engagementWithoutNfc.hasNfcRetrievalMethod)
    }

    @Test
    fun testHasWifiAwareRetrievalMethod() {
        val security = createTestReaderEngagementSecurity()
        val wifiMethod = createWifiAwareRetrievalMethod()

        val engagementWithWifi = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(wifiMethod),
            original = null
        )
        val engagementWithoutWifi = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        assertTrue(engagementWithWifi.hasWifiAwareRetrievalMethod)
        assertFalse(engagementWithoutWifi.hasWifiAwareRetrievalMethod)
    }

    @Test
    fun testHasWebsiteRetrievalMethod() {
        val security = createTestReaderEngagementSecurity()
        val websiteMethod = createWebsiteRetrievalMethod()

        val engagementWithWebsite = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(websiteMethod),
            original = null
        )
        val engagementWithoutWebsite = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        assertTrue(engagementWithWebsite.hasWebsiteRetrievalMethod)
        assertFalse(engagementWithoutWebsite.hasWebsiteRetrievalMethod)
    }

    @Test
    fun testGetBleRetrievalOptions() {
        val security = createTestReaderEngagementSecurity()
        val bleMethod = createBleRetrievalMethod()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(bleMethod),
            original = null
        )

        val options = engagement.getBleRetrievalOptions()
        assertNotNull(options)
        assertTrue(options.peripheralServerMode == true)
    }

    @Test
    fun testGetBleRetrievalOptionsWhenNotPresent() {
        val security = createTestReaderEngagementSecurity()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val options = engagement.getBleRetrievalOptions()
        assertNull(options)
    }

    @Test
    fun testGetNfcRetrievalOptions() {
        val security = createTestReaderEngagementSecurity()
        val nfcMethod = createNfcRetrievalMethod()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(nfcMethod),
            original = null
        )

        val options = engagement.getNfcRetrievalOptions()
        assertNotNull(options)
        assertEquals(256u, options.maxCommandDataFieldLength)
    }

    @Test
    fun testGetWifiAwareRetrievalOptions() {
        val security = createTestReaderEngagementSecurity()
        val wifiMethod = createWifiAwareRetrievalMethod()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(wifiMethod),
            original = null
        )

        val options = engagement.getWifiAwareRetrievalOptions()
        assertNotNull(options)
        assertEquals("testpassphrase", options.passPhrase)
    }

    @Test
    fun testGetWebsiteRetrievalOptions() {
        val security = createTestReaderEngagementSecurity()
        val websiteMethod = createWebsiteRetrievalMethod()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(websiteMethod),
            original = null
        )

        val options = engagement.getWebsiteRetrievalOptions()
        assertNotNull(options)
        assertEquals("https://example.com/api", options.uri)
    }

    // Engagement URI tests

    @Test
    fun testToEngagementUriDefaultScheme() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val uri = engagement.toEngagementUri()
        assertTrue(uri.startsWith("mdoc://"))
    }

    @Test
    fun testToEngagementUriCustomScheme() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val classicUri = engagement.toEngagementUri("mdoc:")
        assertTrue(classicUri.startsWith("mdoc:"))

        val oid4vpUri = engagement.toEngagementUri("mdoc-openid4vp://")
        assertTrue(oid4vpUri.startsWith("mdoc-openid4vp://"))
    }

    @Test
    fun testFromEngagementUriClassicScheme() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val uri = engagement.toEngagementUri("mdoc:")
        val decoded = ReaderEngagement.fromEngagementUri(uri)

        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testFromEngagementUriWebsiteScheme() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val uri = engagement.toEngagementUri("mdoc://")
        val decoded = ReaderEngagement.fromEngagementUri(uri)

        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testFromEngagementUriOid4vpScheme() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val uri = engagement.toEngagementUri("mdoc-openid4vp://")
        val decoded = ReaderEngagement.fromEngagementUri(uri)

        assertEquals(engagement.version, decoded.version)
    }

    @Test
    fun testFromEngagementUriInvalidScheme() {
        assertFailsWith<IllegalArgumentException> {
            ReaderEngagement.fromEngagementUri("invalid://test")
        }
    }

    @Test
    fun testFromEngagementUriOid4vpWithQueryParams() {
        assertFailsWith<IllegalArgumentException> {
            ReaderEngagement.fromEngagementUri("mdoc-openid4vp://?client_id=test")
        }
    }

    // Companion object labels tests

    @Test
    fun testReaderEngagementCompanionLabels() {
        assertEquals(0, ReaderEngagement.VERSION.value)
        assertEquals(1, ReaderEngagement.SECURITY.value)
        assertEquals(2, ReaderEngagement.DEVICE_RETRIEVAL_METHODS.value)
        assertEquals(4, ReaderEngagement.PROTOCOL_INFO.value)
        assertEquals(5, ReaderEngagement.ORIGIN_INFOS.value)
        assertEquals(6, ReaderEngagement.CAPABILITIES.value)
    }

    // EncodeCbor with empty additionalItems tests

    @Test
    fun testEncodeCborRemovesEmptyAdditionalItems() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_0(
            security = security,
            original = null
        )

        val encoded = engagement.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testV11EncodeCborRemovesEmptyAdditionalItems() {
        val security = createTestReaderEngagementSecurity()
        val engagement = ReaderEngagement.V1_1(
            security = security,
            original = null
        )

        val encoded = engagement.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    // Multiple retrieval methods test

    @Test
    fun testMultipleRetrievalMethods() {
        val security = createTestReaderEngagementSecurity()
        val bleMethod = createBleRetrievalMethod()
        val nfcMethod = createNfcRetrievalMethod()

        val engagement = ReaderEngagement.V1_0(
            security = security,
            deviceRetrievalMethods = arrayOf(bleMethod, nfcMethod),
            original = null
        )

        assertEquals(2, engagement.deviceRetrievalMethods?.size)
        assertTrue(engagement.hasBleRetrievalMethod)
        assertTrue(engagement.hasNfcRetrievalMethod)
        assertFalse(engagement.hasWifiAwareRetrievalMethod)
        assertFalse(engagement.hasWebsiteRetrievalMethod)
    }
}
