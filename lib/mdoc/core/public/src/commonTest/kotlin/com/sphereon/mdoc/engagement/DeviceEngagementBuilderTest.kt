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
import com.sphereon.cbor.CborInt
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.testutil.encodeCoseKey
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for DeviceEngagementBuilder and related DSL builders.
 */
@OptIn(ExperimentalUuidApi::class)
class DeviceEngagementBuilderTest {
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

    private fun createTestEDeviceKeyBytes(): CborEncodedItem<CoseKeyType> {
        val key = createTestCoseKey()
        return CborEncodedItem(encodeCoseKey(key), key)
    }

    // DeviceEngagementBuilder tests

    @Test
    fun testBuildVersion10WithSecurity() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_0)
        assertEquals("1.0", engagement.version.toString())
    }

    @Test
    fun testBuildVersion11WithSecurityAndOriginInfo() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.1"
        builder.security {}
        builder.originInfo {
            cat =
                com.sphereon.mdoc.transfer
                    .OriginInfoCategory(0u)
            type =
                com.sphereon.mdoc.transfer
                    .OriginInfoType(1u)
        }

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_1)
        assertEquals("1.1", engagement.version.toString())
    }

    @Test
    fun testBuildAutoSelectsVersion10WhenNoOriginInfoOrCapabilities() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.security {}

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_0)
        assertEquals("1.0", engagement.version.toString())
    }

    @Test
    fun testBuildAutoSelectsVersion11WhenOriginInfoPresent() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.security {}
        builder.originInfo {
            cat =
                com.sphereon.mdoc.transfer
                    .OriginInfoCategory(0u)
            type =
                com.sphereon.mdoc.transfer
                    .OriginInfoType(1u)
        }

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_1)
    }

    @Test
    fun testBuildAutoSelectsVersion11WhenCapabilitiesPresent() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.security {}
        builder.capabilities {}

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_1)
    }

    @Test
    fun testBuildWithBleRetrievalMethod() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.retrievalMethods {
            ble {
                peripheralServerMode = true
                peripheralServerModeUuid = Uuid.random()
            }
        }

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_0)
        assertNotNull(engagement.deviceRetrievalMethods)
        assertEquals(1, engagement.deviceRetrievalMethods?.size)
        assertEquals(DeviceRetrievalMethodType.BLE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testBuildWithNfcRetrievalMethod() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.retrievalMethods {
            nfc {
                maxCommandDataFieldLength = 256u
                maxResponseDataFieldLength = 256u
            }
        }

        val engagement = builder.build()

        assertNotNull(engagement.deviceRetrievalMethods)
        assertEquals(DeviceRetrievalMethodType.NFC, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testBuildWithWifiAwareRetrievalMethod() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.retrievalMethods {
            wifiAware {
                passPhrase = "testpassphrase"
            }
        }

        val engagement = builder.build()

        assertNotNull(engagement.deviceRetrievalMethods)
        assertEquals(DeviceRetrievalMethodType.WIFI_WARE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testBuildWithWebsiteRetrievalMethod() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.retrievalMethods {
            website {
                uri = "https://example.com"
            }
        }

        val engagement = builder.build()

        assertNotNull(engagement.deviceRetrievalMethods)
        assertEquals(DeviceRetrievalMethodType.WEBSITE, engagement.deviceRetrievalMethods?.first()?.type)
    }

    @Test
    fun testBuildWithMultipleRetrievalMethods() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.retrievalMethods {
            ble {
                peripheralServerMode = true
                peripheralServerModeUuid = Uuid.random()
            }
            nfc {
                maxCommandDataFieldLength = 256u
                maxResponseDataFieldLength = 256u
            }
        }

        val engagement = builder.build()

        assertNotNull(engagement.deviceRetrievalMethods)
        assertEquals(2, engagement.deviceRetrievalMethods?.size)
    }

    @Test
    fun testBuildWithAdditionalItem() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.additionalItem(100L, CborInt(42))

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_0)
        assertNotNull((engagement as DeviceEngagement.V1_0).additionalItems)
    }

    @Test
    fun testBuildWithMultipleOriginInfos() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.1"
        builder.security {}
        builder.originInfos(
            arrayOf(
                {
                    cat =
                        com.sphereon.mdoc.transfer
                            .OriginInfoCategory(0u)
                    type =
                        com.sphereon.mdoc.transfer
                            .OriginInfoType(1u)
                },
                {
                    cat =
                        com.sphereon.mdoc.transfer
                            .OriginInfoCategory(1u)
                    type =
                        com.sphereon.mdoc.transfer
                            .OriginInfoType(0u)
                },
            ),
        )

        val engagement = builder.build()

        assertTrue(engagement is DeviceEngagement.V1_1)
        assertEquals(2, (engagement as DeviceEngagement.V1_1).originInfos?.size)
    }

    @Test
    fun testBuildFailsWithInvalidVersion() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "2.0"
        builder.security {}

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testBuildFailsWithoutSecurity() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testBuildFailsVersion10WithOriginInfo() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.originInfo {
            cat =
                com.sphereon.mdoc.transfer
                    .OriginInfoCategory(0u)
            type =
                com.sphereon.mdoc.transfer
                    .OriginInfoType(1u)
        }

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testBuildFailsVersion10WithCapabilities() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}
        builder.capabilities {}

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testBuildFailsVersion11WithoutOriginInfoOrCapabilities() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.1"
        builder.security {}

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testEncodeCbor() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = DeviceEngagementBuilder(eDeviceKeyBytes)
        builder.version = "1.0"
        builder.security {}

        val encoded = deviceEngagementCborCodec.encode(builder.build()).getOrThrow()

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    // SecurityBuilder tests

    @Test
    fun testSecurityBuilderWithEDeviceKey() {
        val builder = SecurityBuilder()
        builder.eDeviceKeyBytes = createTestEDeviceKeyBytes()
        val security = builder.build()

        assertNotNull(security)
        assertEquals(1u, security.cipherSuite)
    }

    @Test
    fun testSecurityBuilderWithDeviceKeyBytesInConstructor() {
        val eDeviceKeyBytes = createTestEDeviceKeyBytes()

        val builder = SecurityBuilder(eDeviceKeyBytes)
        val security = builder.build()

        assertNotNull(security)
        assertEquals(1u, security.cipherSuite)
    }

    // OriginInfoBuilder tests

    @Test
    fun testOriginInfoBuilderWithDetails() {
        val builder = OriginInfoBuilder()
        builder.cat =
            com.sphereon.mdoc.transfer
                .OriginInfoCategory(0u)
        builder.type =
            com.sphereon.mdoc.transfer
                .OriginInfoType(1u)
        builder.details["url"] = "https://example.com"

        val originInfo = builder.build()

        assertNotNull(originInfo)
        assertEquals(
            com.sphereon.mdoc.transfer
                .OriginInfoCategory(0u),
            originInfo.cat,
        )
        assertEquals(
            com.sphereon.mdoc.transfer
                .OriginInfoType(1u),
            originInfo.type,
        )
        assertNotNull(originInfo.details)
    }

    @Test
    fun testOriginInfoBuilderWithoutDetails() {
        val builder = OriginInfoBuilder()
        builder.cat =
            com.sphereon.mdoc.transfer
                .OriginInfoCategory(0u)
        builder.type =
            com.sphereon.mdoc.transfer
                .OriginInfoType(0u)

        val originInfo = builder.build()

        assertNull(originInfo.details)
    }

    @Test
    fun testOriginInfoBuilderFailsWithoutCategory() {
        val builder = OriginInfoBuilder()
        builder.type =
            com.sphereon.mdoc.transfer
                .OriginInfoType(1u)

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testOriginInfoBuilderFailsWithoutType() {
        val builder = OriginInfoBuilder()
        builder.cat =
            com.sphereon.mdoc.transfer
                .OriginInfoCategory(0u)

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    // CapabilitiesBuilder tests

    @Test
    fun testCapabilitiesBuilder() {
        val builder = CapabilitiesBuilder()
        val capabilities = builder.build()

        assertNotNull(capabilities)
    }

    // BleOptionsBuilder tests

    @Test
    fun testBleOptionsBuilder() {
        val uuid = Uuid.random()
        val builder = BleOptionsBuilder()
        builder.peripheralServerMode = true
        builder.peripheralServerModeUuid = uuid
        builder.centralClientMode = false

        val options = builder.build()

        assertEquals(true, options.peripheralServerMode)
        assertEquals(uuid, options.peripheralServerModeUuid)
        assertEquals(false, options.centralClientMode)
    }

    @Test
    fun testBleOptionsBuilderFromOptions() {
        val uuid = Uuid.random()
        val originalOptions =
            BleOptions(
                peripheralServerMode = true,
                peripheralServerModeUuid = uuid,
                centralClientMode = false,
            )

        val builder = BleOptionsBuilder()
        builder.fromOptions(originalOptions)
        val options = builder.build()

        assertEquals(originalOptions.peripheralServerMode, options.peripheralServerMode)
        assertEquals(originalOptions.peripheralServerModeUuid, options.peripheralServerModeUuid)
        assertEquals(originalOptions.centralClientMode, options.centralClientMode)
    }

    // NfcOptionsBuilder tests

    @Test
    fun testNfcOptionsBuilder() {
        val builder = NfcOptionsBuilder()
        builder.maxCommandDataFieldLength = 512u
        builder.maxResponseDataFieldLength = 1024u

        val options = builder.build()

        assertEquals(512u, options.maxCommandDataFieldLength)
        assertEquals(1024u, options.maxResponseDataFieldLength)
    }

    // WifiAwareOptionsBuilder tests

    @Test
    fun testWifiAwareOptionsBuilder() {
        val builder = WifiAwareOptionsBuilder()
        builder.passPhrase = "mypassphrase"

        val options = builder.build()

        assertEquals("mypassphrase", options.passPhrase)
    }

    // WebsiteOptionsBuilder tests

    @Test
    fun testWebsiteOptionsBuilder() {
        val builder = WebsiteOptionsBuilder()
        builder.uri = "https://example.com/api"

        val options = builder.build()

        assertEquals("https://example.com/api", options.uri)
    }

    // RetrievalMethodsBuilder tests

    @Test
    fun testRetrievalMethodsBuilderEmpty() {
        val builder = RetrievalMethodsBuilder()
        val methods = builder.build()

        assertNull(methods)
    }
}
