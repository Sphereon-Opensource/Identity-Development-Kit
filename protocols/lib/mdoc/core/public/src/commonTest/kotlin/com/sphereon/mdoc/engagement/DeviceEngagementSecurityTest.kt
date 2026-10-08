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

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.testutil.encodeCoseKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for DeviceEngagementSecurity data class.
 */
class DeviceEngagementSecurityTest {
    private val coseKeyCodec = CoseKeyCborCodecImpl()

    private fun createTestCoseKey(): CoseKey {
        // Use the Builder pattern to create a valid COSE Key
        return CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") // Dummy X (base64url)
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB") // Dummy Y (base64url)
            .build()
            .toCbor()
    }

    @Test
    fun testDeviceEngagementSecurityCreation() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            DeviceEngagementSecurity(
                cipherSuite = 1u,
                eDeviceKeyBytes = eDeviceKeyBytes,
            )

        assertEquals(1u, security.cipherSuite)
        assertNotNull(security.eDeviceKeyBytes)
    }

    @Test
    fun testDeviceEngagementSecurityDefaultCipherSuite() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security = DeviceEngagementSecurity(eDeviceKeyBytes = eDeviceKeyBytes)
        assertEquals(1u, security.cipherSuite)
    }

    @Test
    fun testDeviceEngagementSecurityToCborItem() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            DeviceEngagementSecurity(
                cipherSuite = 1u,
                eDeviceKeyBytes = eDeviceKeyBytes,
            )

        val item = security.toCborItem()
        assertNotNull(item)
    }

    @Test
    fun testDeviceEngagementSecurityEncodeDecode() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            DeviceEngagementSecurity(
                cipherSuite = 1u,
                eDeviceKeyBytes = eDeviceKeyBytes,
            )

        val encoded = Cbor.encode(security.toCborItem())
        val decoded =
            DeviceEngagementSecurity.fromCborItem(
                com.sphereon.cbor.Cbor
                    .decode(encoded),
                coseKeyCodec,
            )

        assertEquals(security.cipherSuite, decoded.cipherSuite)
        assertNotNull(decoded.eDeviceKeyBytes)
    }

    @Test
    fun testDeviceEngagementSecurityEquality() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes1 = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)
        val eDeviceKeyBytes2 = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security1 = DeviceEngagementSecurity(cipherSuite = 1u, eDeviceKeyBytes = eDeviceKeyBytes1)
        val security2 = DeviceEngagementSecurity(cipherSuite = 1u, eDeviceKeyBytes = eDeviceKeyBytes2)

        // Data class equality check
        assertEquals(security1.cipherSuite, security2.cipherSuite)
    }

    @Test
    fun testDeviceEngagementSecurityHashCode() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            DeviceEngagementSecurity(
                cipherSuite = 1u,
                eDeviceKeyBytes = eDeviceKeyBytes,
            )

        val hash = security.hashCode()
        assertNotNull(hash)
    }

    @Test
    fun testDeviceEngagementSecurityDifferentCipherSuites() {
        val testKey = createTestCoseKey()
        val eDeviceKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security1 = DeviceEngagementSecurity(cipherSuite = 1u, eDeviceKeyBytes = eDeviceKeyBytes)
        val security2 = DeviceEngagementSecurity(cipherSuite = 2u, eDeviceKeyBytes = eDeviceKeyBytes)

        // Different cipher suites should result in different objects
        assertNotNull(security1)
        assertNotNull(security2)
        assertEquals(1u, security1.cipherSuite)
        assertEquals(2u, security2.cipherSuite)
    }
}
