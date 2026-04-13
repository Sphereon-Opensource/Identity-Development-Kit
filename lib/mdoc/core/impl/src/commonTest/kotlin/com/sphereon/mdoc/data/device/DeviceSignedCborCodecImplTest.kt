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

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.toCborItem
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.testutil.coseSign1AsCborArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceSignedCborCodecImplTest {
    private val codec = DeviceSignedCborCodecImpl()

    @Test
    fun deviceSigned_round_trips_and_preserves_original_bytes() {
        val original = encodeOutOfOrderDeviceSigned()

        val decoded = codec.decode(original).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertDeviceSignedEquals(createTestDeviceSigned(), decoded.value.copy(original = null))
        assertContentEquals(original, decoded.originalBytes)
        assertContentEquals(original, decoded.value.original)
        assertContentEquals(original, reEncoded)
    }

    @Test
    fun deviceSigned_codec_rejects_invalid_cbor_item() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(Cbor.encode(CborString("invalid"))).getOrThrow()
        }
    }

    private fun createTestDeviceSigned(): DeviceSigned =
        DeviceSigned(
            nameSpaces =
                DeviceNameSpaces(
                    NameSpace("org.iso.18013.5.1") to
                        DeviceSignedItems(
                            DataElementIdentifier("portrait") to "opaque-self-asserted",
                            DataElementIdentifier("age_over_18") to true,
                        ),
                ),
            deviceAuth = createTestDeviceAuth(),
            original = null,
        )

    private fun createTestDeviceAuth(): DeviceAuth =
        DeviceAuth(
            deviceSignature =
                CoseSign1(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    unprotectedHeader = CoseHeaderCbor(),
                    payload = CborByteString("device-auth-payload".encodeToByteArray()),
                    signature = CborByteString(ByteArray(64) { it.toByte() }),
                ),
            deviceMac = null,
            original = null,
        )

    private fun encodeOutOfOrderDeviceSigned(): ByteArray {
        val deviceSigned = createTestDeviceSigned()
        return Cbor.encode(
            CborMap(
                mutableMapOf(
                    DeviceSigned.DEVICE_AUTH to encodeDeviceAuthMap(deviceSigned.deviceAuth),
                    DeviceSigned.NAME_SPACES to
                        CborEncodedItem<CborMap<CborString, CborMap<CborString, CborItem<*>>>>(
                            com.sphereon.cbor.Cbor
                                .encode(encodeDeviceNameSpacesMap(deviceSigned.nameSpaces)),
                        ),
                ),
            ),
        )
    }

    private fun encodeDeviceAuthMap(deviceAuth: DeviceAuth): CborMap<com.sphereon.cbor.StringLabel, CborItem<*>> =
        CborMap(
            mutableMapOf(
                DeviceAuth.DEVICE_SIGNATURE to coseSign1AsCborArray(requireNotNull(deviceAuth.deviceSignature) { "Test DeviceAuth must use signature mode" }),
            ),
        )

    private fun encodeDeviceNameSpacesMap(nameSpaces: DeviceNameSpaces): CborMap<CborString, CborMap<CborString, CborItem<*>>> =
        CborMap(
            nameSpaces.value.entries
                .associate { (nameSpace, items) ->
                    CborString(nameSpace.toString()) to
                        CborMap(
                            items.value.entries
                                .associate { (identifier, value) ->
                                    CborString(identifier.toString()) to value.toCborItem()
                                }.toMutableMap(),
                        )
                }.toMutableMap(),
        )

    private fun assertDeviceSignedEquals(
        expected: DeviceSigned,
        actual: DeviceSigned,
    ) {
        assertEquals(expected.nameSpaces, actual.nameSpaces)
        assertEquals(expected.deviceAuth.deviceMac, actual.deviceAuth.deviceMac)
        assertEquals(expected.deviceAuth.deviceSignature?.protectedHeader, actual.deviceAuth.deviceSignature?.protectedHeader)
        assertEquals(expected.deviceAuth.deviceSignature?.unprotectedHeader, actual.deviceAuth.deviceSignature?.unprotectedHeader)
        assertEquals(expected.deviceAuth.deviceSignature?.payload, actual.deviceAuth.deviceSignature?.payload)
        assertEquals(expected.deviceAuth.deviceSignature?.signature, actual.deviceAuth.deviceSignature?.signature)
    }
}
