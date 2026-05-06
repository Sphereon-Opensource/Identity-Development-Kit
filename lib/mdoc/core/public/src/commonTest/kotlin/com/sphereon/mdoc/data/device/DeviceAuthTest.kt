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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborString
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceAuthType, DeviceMac, and DeviceAuth.
 */
class DeviceAuthTest {
    @Test
    fun testDeviceAuthTypeValues() {
        assertEquals(2, DeviceAuthType.entries.size)
        assertEquals(DeviceAuthType.SIGNATURE, DeviceAuthType.valueOf("SIGNATURE"))
        assertEquals(DeviceAuthType.MAC, DeviceAuthType.valueOf("MAC"))
    }

    @Test
    fun testDeviceAuthTypeOrdinals() {
        assertEquals(0, DeviceAuthType.SIGNATURE.ordinal)
        assertEquals(1, DeviceAuthType.MAC.ordinal)
    }

    @Test
    fun testDeviceMacCreation() {
        val mac = DeviceMac("test-mac-value")
        assertEquals("test-mac-value", mac.toString())
    }

    @Test
    fun testDeviceMacToCborStructure() {
        val mac = DeviceMac("mac-123")
        val cbor = mac.toCborItem()
        assertEquals("mac-123", cbor.value)
    }

    @Test
    fun testDeviceMacFromCborStructure() {
        val cbor = CborString("decoded-mac")
        val mac = DeviceMac.fromCborItem(cbor)
        assertEquals("decoded-mac", mac.toString())
    }

    @Test
    fun testDeviceAuthWithSignature() {
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })
        val payload = CborByteString("payload".encodeToByteArray())

        val coseSign1 =
            COSE_Sign1<DeviceAuthentication>(
                protectedHeader = protectedHeader,
                unprotectedHeader = null,
                payload = payload,
                signature = signature,
            )

        val deviceAuth =
            DeviceAuth(
                deviceSignature = coseSign1,
                deviceMac = null,
                original = null,
            )

        assertEquals(DeviceAuthType.SIGNATURE, deviceAuth.getAuthType())
        assertNotNull(deviceAuth.deviceSignature)
    }

    @Test
    fun testDeviceAuthWithMacThrowsNotImplemented() {
        assertFailsWith<NotImplementedError> {
            DeviceAuth(
                deviceSignature = null,
                deviceMac = DeviceMac("test-mac"),
                original = null,
            )
        }
    }

    @Test
    fun testDeviceAuthWithBothThrowsIllegalState() {
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })
        val payload = CborByteString("payload".encodeToByteArray())

        val coseSign1 =
            COSE_Sign1<DeviceAuthentication>(
                protectedHeader = protectedHeader,
                unprotectedHeader = null,
                payload = payload,
                signature = signature,
            )

        assertFailsWith<IllegalStateException> {
            DeviceAuth(
                deviceSignature = coseSign1,
                deviceMac = DeviceMac("test-mac"),
                original = null,
            )
        }
    }

    @Test
    fun testDeviceAuthWithNeitherThrowsIllegalState() {
        assertFailsWith<IllegalStateException> {
            DeviceAuth(
                deviceSignature = null,
                deviceMac = null,
                original = null,
            )
        }
    }

    @Test
    fun testDeviceAuthToString() {
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })
        val payload = CborByteString("payload".encodeToByteArray())

        val coseSign1 =
            COSE_Sign1<DeviceAuthentication>(
                protectedHeader = protectedHeader,
                unprotectedHeader = null,
                payload = payload,
                signature = signature,
            )

        val deviceAuth =
            DeviceAuth(
                deviceSignature = coseSign1,
                deviceMac = null,
                original = null,
            )

        val str = deviceAuth.toString()
        assertNotNull(str)
        assertTrue(str.contains("DeviceAuth"))
    }

    @Test
    fun testDeviceAuthCompanionLabels() {
        assertEquals("deviceSignature", DeviceAuth.DEVICE_SIGNATURE.value)
        assertEquals("deviceMac", DeviceAuth.DEVICE_MAC.value)
    }

    // DeviceAuthType tests

    @Test
    fun testDeviceAuthTypeEntries() {
        val entries = DeviceAuthType.entries
        assertEquals(2, entries.size)
        assertTrue(entries.contains(DeviceAuthType.SIGNATURE))
        assertTrue(entries.contains(DeviceAuthType.MAC))
    }

    @Test
    fun testDeviceAuthTypeName() {
        assertEquals("SIGNATURE", DeviceAuthType.SIGNATURE.name)
        assertEquals("MAC", DeviceAuthType.MAC.name)
    }

    // DeviceMac tests

    @Test
    fun testDeviceMacEquality() {
        val mac1 = DeviceMac("test-mac")
        val mac2 = DeviceMac("test-mac")
        assertEquals(mac1, mac2)
    }

    @Test
    fun testDeviceMacRoundTrip() {
        val original = DeviceMac("round-trip-mac")
        val cbor = original.toCborItem()
        val decoded = DeviceMac.fromCborItem(cbor)
        assertEquals(original.toString(), decoded.toString())
    }

    // DeviceAuthentication tests

    @Test
    fun testDeviceAuthenticationFromOid4vp() {
        val docType = DocType("org.iso.18013.5.1.mDL")
        val deviceNamespaces = DeviceNameSpaces()

        val deviceAuth =
            DeviceAuthentication.fromOid4vp(
                clientId = "https://client.example.com",
                nonce = "test-auth-nonce",
                jwkThumbprint = null,
                responseUri = "https://response.example.com/callback",
                docType = docType,
                deviceNamespaces = deviceNamespaces,
            )

        assertNotNull(deviceAuth)
        assertEquals(docType, deviceAuth.docType)
        assertEquals(deviceNamespaces, deviceAuth.deviceNamespaces)
        assertNotNull(deviceAuth.sessionTranscript)
    }

    @Test
    fun testDeviceAuthenticationOriginalRetention() {
        val docType = DocType("org.iso.18013.5.1.mDL")
        val deviceNamespaces = DeviceNameSpaces()

        val deviceAuth =
            DeviceAuthentication.fromOid4vp(
                clientId = "https://client.example.com",
                nonce = "test-auth-nonce",
                jwkThumbprint = null,
                responseUri = "https://response.example.com/callback",
                docType = docType,
                deviceNamespaces = deviceNamespaces,
            )

        assertEquals(null, deviceAuth.original)
    }
}
