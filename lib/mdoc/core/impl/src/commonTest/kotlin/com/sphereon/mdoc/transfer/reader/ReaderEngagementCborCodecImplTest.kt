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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.toCborBool
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.testutil.encodeCoseKey
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.OriginInfoCategory
import com.sphereon.mdoc.transfer.OriginInfoDetails
import com.sphereon.mdoc.transfer.OriginInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodVersion
import com.sphereon.mdoc.transfer.device.RestApiOptions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReaderEngagementCborCodecImplTest {
    private val codec = ReaderEngagementCborCodecImpl()

    @Test
    fun readerEngagement_round_trips_and_preserves_original_bytes() {
        val engagement =
            ReaderEngagement.V1_0(
                security =
                    ReaderEngagementSecurity(
                        cipherSuite = 1u,
                        eReaderKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                    ),
                deviceRetrievalMethods =
                    arrayOf(
                        DeviceRetrievalMethod(
                            type = DeviceRetrievalMethodType.WEBSITE,
                            retrievalOptions = RestApiOptions("https://reader.example.com/session/123"),
                        ),
                    ),
                protocolInfo = null,
                additionalItems = null,
                original = null,
            )

        val encoded = codec.encode(engagement).getOrThrow()
        val decoded = codec.decode(encoded).getOrThrow()

        assertEquals(engagement, decoded.value.copyWithOriginal(original = null))
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun readerEngagement_encode_prefers_original_bytes_over_reencoding() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        ReaderEngagement.SECURITY to CborArrayBuilder.createSecurityArray(createTestCoseKey()),
                        ReaderEngagement.VERSION to CborString("1.0"),
                    ),
                ),
            )

        val decoded = codec.decode(original).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
    }

    @Test
    fun readerEngagement_decode_preserves_tag24_wrapped_original_bytes() {
        val raw =
            codec
                .encode(
                    ReaderEngagement.V1_0(
                        security =
                            ReaderEngagementSecurity(
                                cipherSuite = 1u,
                                eReaderKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                            ),
                        original = null,
                    ),
                ).getOrThrow()
        val wrapped = Cbor.encode(CborEncodedItem<ByteArray>(raw))

        val decoded = codec.decode(wrapped).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertContentEquals(wrapped, decoded.originalBytes)
        assertContentEquals(wrapped, decoded.value.original)
        assertContentEquals(wrapped, reEncoded)
    }

    @Test
    fun readerEngagement_encodeTag24_wraps_retained_unwrapped_original_bytes_without_reencoding() {
        val raw =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        ReaderEngagement.SECURITY to CborArrayBuilder.createSecurityArray(createTestCoseKey()),
                        ReaderEngagement.VERSION to CborString("1.0"),
                        ReaderEngagement.PROTOCOL_INFO to CborString("non-canonical-order"),
                    ),
                ),
            )

        val decoded = codec.decode(raw).getOrThrow()
        val tag24Encoded = codec.encodeTag24(decoded.value).getOrThrow()
        val expected =
            Cbor.encode(
                CborEncodedItem(
                    value = raw,
                    data = decoded.value.copyWithOriginal(raw),
                ),
            )

        assertContentEquals(expected, tag24Encoded)
    }

    @Test
    fun readerEngagement_encodeTag24_preserves_existing_tag24_original_bytes() {
        val raw =
            codec
                .encode(
                    ReaderEngagement.V1_0(
                        security =
                            ReaderEngagementSecurity(
                                cipherSuite = 1u,
                                eReaderKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                            ),
                        original = null,
                    ),
                ).getOrThrow()
        val wrapped = Cbor.encode(CborEncodedItem<ByteArray>(raw))

        val decoded = codec.decode(wrapped).getOrThrow()
        val tag24Encoded = codec.encodeTag24(decoded.value).getOrThrow()

        assertContentEquals(wrapped, tag24Encoded)
    }

    @Test
    fun readerEngagement_decodeUri_round_trips_and_preserves_uri_payload_bytes() {
        val engagement =
            ReaderEngagement.V1_1(
                security =
                    ReaderEngagementSecurity(
                        cipherSuite = 1u,
                        eReaderKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                    ),
                deviceRetrievalMethods =
                    arrayOf(
                        DeviceRetrievalMethod(
                            type = DeviceRetrievalMethodType.WEBSITE,
                            retrievalOptions = RestApiOptions("https://reader.example.com/session/456"),
                        ),
                    ),
                originInfos =
                    arrayOf(
                        OriginInfo(
                            cat = OriginInfoCategory(1u),
                            type = OriginInfoType(1u),
                            details = OriginInfoDetails(mapOf("domain" to "reader.example.com")),
                            original = null,
                        ),
                    ),
                capabilities =
                    Capabilities(
                        macKeysSupport = true,
                        macKeyCurves = arrayOf(CoseCurve.P_256),
                        handoverSessionEstablishmentSupport = true,
                        readerAuthAllSupport = false,
                        extendedRequestSupport = true,
                        additionalItems = null,
                    ),
                additionalItems = null,
                original = null,
            )

        val encoded = codec.encode(engagement).getOrThrow()
        val uri = codec.encodeUri(engagement, "mdoc://").getOrThrow()
        val decodedResult = codec.decodeUri(uri).getOrThrow()
        val decoded = decodedResult.value as ReaderEngagement.V1_1

        assertEquals("https://reader.example.com/session/456", (decoded.deviceRetrievalMethods?.single()?.retrievalOptions as RestApiOptions).uri)
        assertEquals(
            "reader.example.com",
            decoded.originInfos
                ?.single()
                ?.details
                ?.get("domain"),
        )
        assertEquals(true, decoded.capabilities?.macKeysSupport)
        assertEquals(arrayOf(CoseCurve.P_256).toList(), decoded.capabilities?.macKeyCurves?.toList())
        assertContentEquals(encoded, decodedResult.originalBytes)
        assertContentEquals(encoded, decoded.original)
    }

    @Test
    fun readerEngagement_decodeUri_rejects_oid4vp_query_parameter_format() {
        val result = codec.decodeUri("mdoc-openid4vp://?client_id=test")

        assertEquals(true, result.isErr)
        assertEquals(true, result.error.toString().contains("query parameters"))
    }

    @Test
    fun readerEngagement_decode_rejects_non_map_payload() {
        val result = codec.decode(byteArrayOf(0x81.toByte(), 0x01))

        assertEquals(true, result.isErr)
        assertEquals(true, result.error.toString().contains("ReaderEngagement must be encoded as a CBOR map"))
    }

    @Test
    fun readerEngagement_decode_rejects_capabilities_with_wrong_boolean_type() {
        val result =
            codec.decode(
                encodeReaderEngagementMap(
                    version = "1.1",
                    overrides =
                        mapOf(
                            ReaderEngagement.CAPABILITIES to
                                CborMap(
                                    mutableMapOf(
                                        Capabilities.MAC_KEYS_SUPPORT to CborString("true"),
                                    ),
                                ),
                        ),
                ),
            )

        assertEquals(true, result.isErr)
        assertEquals(true, result.error.toString().contains("Capabilities.macKeysSupport must be encoded as a CBOR boolean"))
    }

    @Test
    fun readerEngagement_decode_rejects_ble_uuid_with_wrong_type() {
        val retrievalMethod =
            CborArray(
                mutableListOf(
                    CborUInt(DeviceRetrievalMethodType.BLE.type.toLong()),
                    CborUInt(1),
                    CborMap(
                        mutableMapOf(
                            BleOptions.PERIPHERAL_SERVER_MODE to true.toCborBool(),
                            BleOptions.CENTRAL_CLIENT_MODE to false.toCborBool(),
                            BleOptions.PERIPHERAL_SERVER_MODE_UUID to CborString("not-bytes"),
                        ),
                    ),
                ),
            )

        val result =
            codec.decode(
                encodeReaderEngagementMap(
                    overrides =
                        mapOf(
                            ReaderEngagement.DEVICE_RETRIEVAL_METHODS to CborArray(mutableListOf(retrievalMethod)),
                        ),
                ),
            )

        assertEquals(true, result.isErr)
        assertEquals(true, result.error.toString().contains("BleOptions.peripheralServerModeUuid must be encoded as a CBOR byte string"))
    }

    @Test
    fun readerEngagement_decodeUri_rejects_invalid_base64_payload() {
        val result = codec.decodeUri("mdoc://%%%")

        assertEquals(true, result.isErr)
        assertEquals(true, result.error.toString().contains("Failed to decode ReaderEngagement URI"))
    }

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun encodeReaderEngagementMap(
        version: String = "1.0",
        overrides: Map<com.sphereon.cbor.NumberLabel, com.sphereon.cbor.CborItem<*>> = emptyMap(),
    ): ByteArray {
        val entries =
            mutableMapOf<com.sphereon.cbor.NumberLabel, com.sphereon.cbor.CborItem<*>>(
                ReaderEngagement.VERSION to CborString(version),
                ReaderEngagement.SECURITY to CborArrayBuilder.createSecurityArray(createTestCoseKey()),
            )
        entries.putAll(overrides)
        return Cbor.encode(CborMap(entries))
    }

    private object CborArrayBuilder {
        fun createSecurityArray(coseKey: CoseKey) =
            com.sphereon.cbor.CborArray(
                mutableListOf(
                    CborUInt(1),
                    CborEncodedItem<CoseKeyType>(encodeCoseKey(coseKey), coseKey).value,
                ),
            )
    }
}
