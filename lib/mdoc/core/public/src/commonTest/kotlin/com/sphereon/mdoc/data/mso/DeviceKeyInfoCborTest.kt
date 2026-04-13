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

package com.sphereon.mdoc.data.mso

import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.ResolvedKeyInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceKeyInfoCbor class.
 */
class DeviceKeyInfoCborTest {

    private fun createTestCoseKey(): CoseKey {
        return CoseKeyJson.Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
    }

    @Test
    fun testDeviceKeyInfoCborCreation() {
        val coseKey = createTestCoseKey()
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
        assertNotNull(info.deviceKey)
        assertNull(info.keyAuthorizations)
        assertNull(info.keyInfo)
        assertNull(info.original)
    }

    @Test
    fun testDeviceKeyInfoCborWithKeyAuthorizations() {
        val coseKey = createTestCoseKey()
        val keyAuth = KeyAuthorizationsCbor(null, null)
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = keyAuth,
            keyInfo = null,
            original = null
        )
        assertNotNull(info.keyAuthorizations)
        assertNull(info.keyInfo)
    }

    @Test
    fun testDeviceKeyInfoCborToString() {
        val coseKey = createTestCoseKey()
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
        val str = info.toString()
        assertTrue(str.contains("DeviceKeyInfoCbor"))
        assertTrue(str.contains("deviceKey"))
        assertTrue(str.contains("keyAuthorizations"))
        assertTrue(str.contains("keyInfo"))
    }

    @Test
    fun testDeviceKeyInfoCborToStringWithOriginal() {
        val coseKey = createTestCoseKey()
        val originalBytes = byteArrayOf(0x01, 0x02, 0x03)
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = originalBytes
        )
        val str = info.toString()
        assertTrue(str.contains("DeviceKeyInfoCbor"))
        assertTrue(str.contains("original"))
    }

    @Test
    fun testDeviceKeyInfoCborCborBuilder() {
        val coseKey = createTestCoseKey()
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
        val builder = info.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDeviceKeyInfoCborEncodeDecode() {
        val coseKey = createTestCoseKey()
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
        val encoded = info.encodeCbor()
        val decoded = DeviceKeyInfoCbor.decodeCbor(encoded)
        assertNotNull(decoded.deviceKey)
    }

    @Test
    fun testDeviceKeyInfoCborEncodeDecodeWithKeyAuthorizations() {
        val coseKey = createTestCoseKey()
        val keyAuth = KeyAuthorizationsCbor(null, null)
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = keyAuth,
            keyInfo = null,
            original = null
        )
        val encoded = info.encodeCbor()
        val decoded = DeviceKeyInfoCbor.decodeCbor(encoded)
        assertNotNull(decoded.deviceKey)
    }

    @Test
    fun testDeviceKeyInfoCborToKeyInfo() {
        val coseKey = createTestCoseKey()
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
        val keyInfo = info.toKeyInfo()
        assertNotNull(keyInfo)
    }

    @Test
    fun testDeviceKeyInfoCborFromKeyInfo() {
        val coseKey = createTestCoseKey()
        val keyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(coseKey)
        val deviceKeyInfo = DeviceKeyInfoCbor.fromKeyInfo(keyInfo)
        assertNotNull(deviceKeyInfo.deviceKey)
        assertNull(deviceKeyInfo.original)
    }

    @Test
    fun testDeviceKeyInfoCborCompanionLabels() {
        assertEquals("deviceKey", DeviceKeyInfoCbor.DEVICE_KEY.value)
        assertEquals("keyAuthorizations", DeviceKeyInfoCbor.KEY_AUTHORIZATIONS.value)
        assertEquals("keyInfo", DeviceKeyInfoCbor.KEY_INFO.value)
    }

    @Test
    fun testDeviceKeyInfoCborFromCborStructureWithOriginal() {
        val coseKey = createTestCoseKey()
        val info = DeviceKeyInfoCbor(
            deviceKey = coseKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
        val encoded = info.encodeCbor()
        val decoded = DeviceKeyInfoCbor.decodeCbor(encoded)
        // The decoded item should have original bytes set
        assertNotNull(decoded.original)
        assertTrue(encoded.contentEquals(decoded.original))
    }
}
