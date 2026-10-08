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

package com.sphereon.mdoc.transfer.reader

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
 * Tests for ReaderEngagementSecurity data class.
 */
class ReaderEngagementSecurityTest {
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
    fun testReaderEngagementSecurityCreation() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            ReaderEngagementSecurity(
                cipherSuite = 1u,
                eReaderKeyBytes = eReaderKeyBytes,
            )

        assertEquals(1u, security.cipherSuite)
        assertNotNull(security.eReaderKeyBytes)
    }

    @Test
    fun testReaderEngagementSecurityDefaultCipherSuite() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security = ReaderEngagementSecurity(eReaderKeyBytes = eReaderKeyBytes)
        assertEquals(1u, security.cipherSuite)
    }

    @Test
    fun testReaderEngagementSecurityCborItem() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            ReaderEngagementSecurity(
                cipherSuite = 1u,
                eReaderKeyBytes = eReaderKeyBytes,
            )

        val item = security.toCborItem()
        assertNotNull(item)
    }

    @Test
    fun testReaderEngagementSecurityEncodeDecode() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            ReaderEngagementSecurity(
                cipherSuite = 1u,
                eReaderKeyBytes = eReaderKeyBytes,
            )

        val encoded = Cbor.encode(security.toCborItem())
        val decoded =
            ReaderEngagementSecurity.fromCborItem(
                com.sphereon.cbor.Cbor
                    .decode(encoded),
                coseKeyCodec,
            )

        assertEquals(security.cipherSuite, decoded.cipherSuite)
        assertNotNull(decoded.eReaderKeyBytes)
    }

    @Test
    fun testReaderEngagementSecurityEquality() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes1 = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)
        val eReaderKeyBytes2 = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security1 = ReaderEngagementSecurity(cipherSuite = 1u, eReaderKeyBytes = eReaderKeyBytes1)
        val security2 = ReaderEngagementSecurity(cipherSuite = 1u, eReaderKeyBytes = eReaderKeyBytes2)

        // Data class equality check
        assertEquals(security1.cipherSuite, security2.cipherSuite)
    }

    @Test
    fun testReaderEngagementSecurityHashCode() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security =
            ReaderEngagementSecurity(
                cipherSuite = 1u,
                eReaderKeyBytes = eReaderKeyBytes,
            )

        val hash = security.hashCode()
        assertNotNull(hash)
    }

    @Test
    fun testReaderEngagementSecurityDifferentCipherSuites() {
        val testKey = createTestCoseKey()
        val eReaderKeyBytes = CborEncodedItem<CoseKeyType>(encodeCoseKey(testKey), testKey)

        val security1 = ReaderEngagementSecurity(cipherSuite = 1u, eReaderKeyBytes = eReaderKeyBytes)
        val security2 = ReaderEngagementSecurity(cipherSuite = 2u, eReaderKeyBytes = eReaderKeyBytes)

        // Different cipher suites should result in different objects
        assertNotNull(security1)
        assertNotNull(security2)
        assertEquals(1u, security1.cipherSuite)
        assertEquals(2u, security2.cipherSuite)
    }
}
