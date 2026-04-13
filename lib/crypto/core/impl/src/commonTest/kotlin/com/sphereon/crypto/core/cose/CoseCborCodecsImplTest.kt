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

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.CborByteString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoseCborCodecsImplTest {
    private val keyCodec = CoseKeyCborCodecImpl()
    private val headerCodec = CoseHeaderCborCodecImpl()
    private val sign1Codec = CoseSign1CborCodecImpl()
    private val mac0Codec = CoseMac0CborCodecImpl()

    @Test
    fun headerCodecRoundTripsBytes(): TestResult =
        runTest {
            val header =
                CoseHeaderCbor(
                    alg = CoseAlgorithm.ES256,
                    kid = CborByteString("kid-1".encodeToByteArray()),
                )

            val encoded = headerCodec.encode(header).getOrThrow()
            val decoded = headerCodec.decode(encoded).getOrThrow()

            assertEquals(CoseAlgorithm.ES256, decoded.value.alg)
            assertEquals(
                "kid-1",
                decoded.value.kid
                    ?.value
                    ?.decodeToString(),
            )
            assertTrue(decoded.originalBytes.contentEquals(encoded))
        }

    @Test
    fun sign1CodecRoundTripsBytes(): TestResult =
        runTest {
            val sign1 =
                CoseSign1<Any>(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    unprotectedHeader = CoseHeaderCbor(),
                    payload = CborByteString("payload".encodeToByteArray()),
                    signature = CborByteString(ByteArray(64) { it.toByte() }),
                )

            val encoded = sign1Codec.encode(sign1).getOrThrow()
            val decoded = sign1Codec.decode(encoded).getOrThrow()

            assertEquals(CoseAlgorithm.ES256, decoded.value.protectedHeader.alg)
            assertNotNull(decoded.value.unprotectedHeader)
            assertTrue(
                decoded.value.payload!!
                    .value
                    .contentEquals("payload".encodeToByteArray()),
            )
            assertTrue(decoded.originalBytes.contentEquals(encoded))
        }

    @Test
    fun mac0CodecRoundTripsBytes(): TestResult =
        runTest {
            val mac0 =
                CoseMac0Cbor(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
                    unprotectedHeader = CoseHeaderCbor(),
                    payload = CborByteString("payload".encodeToByteArray()),
                    tag = CborByteString(ByteArray(32) { it.toByte() }),
                )

            val encoded = mac0Codec.encode(mac0).getOrThrow()
            val decoded = mac0Codec.decode(encoded).getOrThrow()

            assertEquals(
                CoseAlgorithm.HMAC256_256.value,
                decoded.value.protectedHeader.alg
                    ?.value,
            )
            assertNotNull(decoded.value.unprotectedHeader)
            assertTrue(
                decoded.value.payload!!
                    .value
                    .contentEquals("payload".encodeToByteArray()),
            )
            assertTrue(decoded.originalBytes.contentEquals(encoded))
        }

    @Test
    fun keyCodecPreservesOriginalBytes(): TestResult =
        runTest {
            val coseKeyHex =
                "a5010202582b6c663872734d5371454f5138626d664f4c44526873414e5878667a5a4678725f64634c6e52496a787671452001215820bb11cddd6e9e869d1559729a30d89ed49f3631524215961271abbbe28d7b731f225820dbd639132e2ee561965b830530a6a024f1098888f313550515921184c86acac3"
            val encoded = coseKeyHex.decodeFrom(Encoding.HEX)

            val decoded = keyCodec.decode(encoded).getOrThrow()
            val reencoded = keyCodec.encode(decoded.value).getOrThrow()

            assertTrue(decoded.originalBytes.contentEquals(encoded))
            assertTrue(decoded.value.original!!.contentEquals(encoded))
            assertTrue(reencoded.contentEquals(encoded))
        }
}
