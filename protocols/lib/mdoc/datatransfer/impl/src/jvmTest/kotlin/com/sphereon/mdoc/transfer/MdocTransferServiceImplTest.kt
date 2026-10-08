/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.DeviceEngagementSecurity
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MdocTransferServiceImplTest {
    @Test
    fun `asVerifierFromQrEngagement decodes through supplied codec boundary`() {
        val engagementBytes = byteArrayOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte())
        val codec =
            FakeDeviceEngagementCborCodec(
                decodeResult = Ok(DecodedMdoc(createDeviceEngagement(), engagementBytes)),
            )

        val service =
            MdocTransferServiceImpl.asVerifierFromQrEngagement(
                engagementUri = "mdoc:${engagementBytes.encodeToBase64Url()}",
                keyManager = unusedKeyManager(),
                deviceEngagementCborCodec = codec,
            )

        assertContentEquals(engagementBytes, requireNotNull(codec.lastDecodedBytes))
        assertTrue(service.isStarted())
        assertEquals(MdocRole.MDOC_READER, service.getMdocRole())
    }

    @Test
    fun `asVerifierFromQrEngagement rejects invalid scheme before codec decode`() {
        val codec =
            FakeDeviceEngagementCborCodec(
                decodeResult = Ok(DecodedMdoc(createDeviceEngagement(), byteArrayOf(0x01.toByte()))),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                MdocTransferServiceImpl.asVerifierFromQrEngagement(
                    engagementUri = "https://example.org/not-mdoc",
                    keyManager = unusedKeyManager(),
                    deviceEngagementCborCodec = codec,
                )
            }

        assertTrue(exception.message!!.contains("Device Engagement URI must start with 'mdoc:'"))
        assertFalse(codec.decodeCalled)
    }

    @Test
    fun `asVerifierFromQrEngagement keeps strict decode failures`() {
        val codec =
            FakeDeviceEngagementCborCodec(
                decodeResult =
                    Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Malformed device engagement",
                            throwable = IllegalArgumentException("Malformed device engagement"),
                        ),
                    ),
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                MdocTransferServiceImpl.asVerifierFromQrEngagement(
                    engagementUri = "mdoc:${byteArrayOf(0x00.toByte()).encodeToBase64Url()}",
                    keyManager = unusedKeyManager(),
                    deviceEngagementCborCodec = codec,
                )
            }

        assertEquals("Malformed device engagement", exception.message)
        assertTrue(codec.decodeCalled)
    }

    private fun createDeviceEngagement(): DeviceEngagement =
        DeviceEngagement.V1_0(
            security =
                DeviceEngagementSecurity(
                    cipherSuite = 1u,
                    eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(byteArrayOf(0x11, 0x22, 0x33), it) },
                ),
            deviceRetrievalMethods =
                arrayOf(
                    DeviceRetrievalMethod(
                        type = DeviceRetrievalMethodType.BLE,
                        retrievalOptions =
                            BleOptions(
                                peripheralServerMode = true,
                                centralClientMode = false,
                                peripheralServerModeUuid = Uuid.random(),
                            ),
                    ),
                ),
            original = null,
        )

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun unusedKeyManager(): KeyManagerService =
        Proxy.newProxyInstance(
            KeyManagerService::class.java.classLoader,
            arrayOf(KeyManagerService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "unused-key-manager"
                "hashCode" -> 0
                "equals" -> false
                else -> throw UnsupportedOperationException("KeyManagerService should not be used in verifier construction tests")
            }
        } as KeyManagerService

    private class FakeDeviceEngagementCborCodec(
        private val decodeResult: IdkResult<DecodedMdoc<DeviceEngagement>, IdkError>,
    ) : DeviceEngagementCborCodec {
        var decodeCalled: Boolean = false
            private set
        var lastDecodedBytes: ByteArray? = null
            private set

        override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceEngagement>, IdkError> {
            decodeCalled = true
            lastDecodedBytes = bytes
            return decodeResult
        }

        override fun encode(value: DeviceEngagement): IdkResult<ByteArray, IdkError> = unsupported()

        override fun encodeItem(value: DeviceEngagement): IdkResult<CborEncodedItem<DeviceEngagement>, IdkError> = unsupported()

        override fun encodeTag24(value: DeviceEngagement): IdkResult<ByteArray, IdkError> = unsupported()

        override fun encodeMessage(value: DeviceEngagement): IdkResult<ByteArray, IdkError> = unsupported()

        override fun encodeMessageItem(value: CborEncodedItem<DeviceEngagement>): IdkResult<ByteArray, IdkError> = unsupported()

        override fun decodeMessage(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceEngagement>, IdkError> = unsupported()

        private fun <T> unsupported(): IdkResult<T, IdkError> = throw UnsupportedOperationException("Only decode(bytes) is needed in this test")
    }
}
