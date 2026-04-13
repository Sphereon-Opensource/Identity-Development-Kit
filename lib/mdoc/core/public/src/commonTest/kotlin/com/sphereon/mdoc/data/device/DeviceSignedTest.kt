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

import com.sphereon.cbor.CborByteString
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceSigned, DeviceNameSpaces, and DeviceSignedItems classes.
 */
class DeviceSignedTest {
    private fun createDeviceAuth(): DeviceAuth {
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })
        val payload = CborByteString("device-auth-payload".encodeToByteArray())

        val coseSign1 =
            COSE_Sign1<DeviceAuthentication>(
                protectedHeader = protectedHeader,
                unprotectedHeader = null,
                payload = payload,
                signature = signature,
            )

        return DeviceAuth(
            deviceSignature = coseSign1,
            deviceMac = null,
            original = null,
        )
    }

    // DeviceNameSpaces tests

    @Test
    fun testDeviceNameSpacesDefaultCreation() {
        val nameSpaces = DeviceNameSpaces()
        assertTrue(nameSpaces.value.isEmpty())
    }

    @Test
    fun testDeviceNameSpacesWithValue() {
        val items = DeviceSignedItems(mapOf(DataElementIdentifier("key") to "value"))
        val nameSpaces = DeviceNameSpaces(mapOf(NameSpace("ns1") to items))

        assertEquals(1, nameSpaces.value.size)
        assertTrue(nameSpaces.value.containsKey(NameSpace("ns1")))
    }

    @Test
    fun testDeviceNameSpacesVarargConstructor() {
        val items1 = DeviceSignedItems(mapOf(DataElementIdentifier("key1") to "value1"))
        val items2 = DeviceSignedItems(mapOf(DataElementIdentifier("key2") to "value2"))

        val nameSpaces =
            DeviceNameSpaces(
                NameSpace("ns1") to items1,
                NameSpace("ns2") to items2,
            )

        assertEquals(2, nameSpaces.value.size)
    }

    @Test
    fun testDeviceNameSpacesToString() {
        val nameSpaces = DeviceNameSpaces()
        val str = nameSpaces.toString()
        assertTrue(str.contains("DeviceNameSpaces"))
    }

    // DeviceSignedItems tests

    @Test
    fun testDeviceSignedItemsDefaultCreation() {
        val items = DeviceSignedItems()
        assertTrue(items.value.isEmpty())
    }

    @Test
    fun testDeviceSignedItemsWithValue() {
        val items =
            DeviceSignedItems(
                mapOf(
                    DataElementIdentifier("key1") to "value1",
                    DataElementIdentifier("key2") to 42,
                ),
            )

        assertEquals(2, items.value.size)
        assertEquals("value1", items.value[DataElementIdentifier("key1")])
        assertEquals(42, items.value[DataElementIdentifier("key2")])
    }

    @Test
    fun testDeviceSignedItemsVarargConstructor() {
        val items =
            DeviceSignedItems(
                DataElementIdentifier("key1") to "value1",
                DataElementIdentifier("key2") to "value2",
            )

        assertEquals(2, items.value.size)
    }

    @Test
    fun testDeviceSignedItemsToString() {
        val items = DeviceSignedItems()
        val str = items.toString()
        assertTrue(str.contains("DeviceSignedItems"))
    }

    // DeviceSigned tests

    @Test
    fun testDeviceSignedCreation() {
        val deviceAuth = createDeviceAuth()
        val nameSpaces = DeviceNameSpaces()

        val deviceSigned =
            DeviceSigned(
                nameSpaces = nameSpaces,
                deviceAuth = deviceAuth,
                original = null,
            )

        assertNotNull(deviceSigned)
        assertEquals(nameSpaces, deviceSigned.nameSpaces)
        assertEquals(deviceAuth, deviceSigned.deviceAuth)
        assertEquals(null, deviceSigned.original)
    }

    @Test
    fun testDeviceSignedWithOriginal() {
        val deviceAuth = createDeviceAuth()
        val original = byteArrayOf(0x01, 0x02, 0x03)

        val deviceSigned =
            DeviceSigned(
                nameSpaces = DeviceNameSpaces(),
                deviceAuth = deviceAuth,
                original = original,
            )

        assertNotNull(deviceSigned.original)
        assertTrue(original.contentEquals(deviceSigned.original!!))
    }

    @Test
    fun testDeviceSignedToString() {
        val deviceAuth = createDeviceAuth()

        val deviceSigned =
            DeviceSigned(
                nameSpaces = DeviceNameSpaces(),
                deviceAuth = deviceAuth,
                original = null,
            )

        val str = deviceSigned.toString()
        assertTrue(str.contains("DeviceSigned"))
        assertTrue(str.contains("nameSpaces"))
        assertTrue(str.contains("deviceAuth"))
    }

    @Test
    fun testDeviceSignedWithNameSpaces() {
        val deviceAuth = createDeviceAuth()
        val items =
            DeviceSignedItems(
                mapOf(
                    DataElementIdentifier("self_asserted") to "my-value",
                ),
            )
        val nameSpaces =
            DeviceNameSpaces(
                mapOf(
                    NameSpace("my.namespace") to items,
                ),
            )

        val deviceSigned =
            DeviceSigned(
                nameSpaces = nameSpaces,
                deviceAuth = deviceAuth,
                original = null,
            )

        assertEquals(1, deviceSigned.nameSpaces.value.size)
        assertTrue(deviceSigned.nameSpaces.value.containsKey(NameSpace("my.namespace")))
    }

    // DeviceSigned companion labels tests

    @Test
    fun testDeviceSignedCompanionLabels() {
        assertEquals("nameSpaces", DeviceSigned.NAME_SPACES.value)
        assertEquals("deviceAuth", DeviceSigned.DEVICE_AUTH.value)
    }
}
