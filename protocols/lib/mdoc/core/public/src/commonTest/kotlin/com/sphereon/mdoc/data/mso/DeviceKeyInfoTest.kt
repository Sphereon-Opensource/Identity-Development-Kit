/*
 * (c) 2026 Sphereon International B.V.
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

import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceKeyInfoTest {
    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    @Test
    fun testDeviceKeyInfoCreation() {
        val info =
            DeviceKeyInfo(
                deviceKey = createTestCoseKey(),
                keyAuthorizations = null,
                keyInfo = null,
                original = null,
            )

        assertNotNull(info.deviceKey)
        assertNull(info.keyAuthorizations)
        assertNull(info.keyInfo)
        assertNull(info.original)
    }

    @Test
    fun testDeviceKeyInfoWithKeyAuthorizations() {
        val keyAuth = KeyAuthorizations(null, null)
        val info =
            DeviceKeyInfo(
                deviceKey = createTestCoseKey(),
                keyAuthorizations = keyAuth,
                keyInfo = null,
                original = null,
            )

        assertEquals(keyAuth, info.keyAuthorizations)
    }

    @Test
    fun testDeviceKeyInfoToStringIncludesOriginalWhenPresent() {
        val info =
            DeviceKeyInfo(
                deviceKey = createTestCoseKey(),
                keyAuthorizations = null,
                keyInfo = null,
                original = byteArrayOf(0x01, 0x02, 0x03),
            )

        val text = info.toString()
        assertTrue(text.contains("DeviceKeyInfo"))
        assertTrue(text.contains("original"))
    }

    @Test
    fun testDeviceKeyInfoToKeyInfo() {
        val keyInfo =
            DeviceKeyInfo(
                deviceKey = createTestCoseKey(),
                keyAuthorizations = null,
                keyInfo = null,
                original = null,
            ).toKeyInfo()

        assertNotNull(keyInfo)
    }

    @Test
    fun testDeviceKeyInfoFromKeyInfo() {
        val keyInfo = ResolvedKeyInfo.fromKey<CoseKeyType>(createTestCoseKey())
        val deviceKeyInfo = DeviceKeyInfo.fromKeyInfo(keyInfo)

        assertNotNull(deviceKeyInfo.deviceKey)
        assertNull(deviceKeyInfo.original)
    }

    @Test
    fun testDeviceKeyInfoCompanionLabels() {
        assertEquals("deviceKey", DeviceKeyInfo.DEVICE_KEY.value)
        assertEquals("keyAuthorizations", DeviceKeyInfo.KEY_AUTHORIZATIONS.value)
        assertEquals("keyInfo", DeviceKeyInfo.KEY_INFO.value)
    }
}
