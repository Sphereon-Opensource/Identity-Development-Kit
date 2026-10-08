/*
 * Ã‚Â© 2026 Sphereon International B.V.
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
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.StringLabel
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
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.ServerRetrievalInfo
import com.sphereon.mdoc.transfer.device.ServerRetrievalMethods
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DeviceEngagementCborCodecImplTest {
    private val codec = DeviceEngagementCborCodecImpl()

    @Test
    fun deviceEngagement_round_trips_and_preserves_original_bytes() {
        val engagement =
            DeviceEngagement.V1_0(
                security =
                    DeviceEngagementSecurity(
                        cipherSuite = 1u,
                        eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                    ),
                deviceRetrievalMethods = null,
                serverRetrievalMethod = null,
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
    fun deviceEngagement_encode_prefers_original_bytes_over_reencoding() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceEngagement.SECURITY to CborArrayBuilder.createSecurityArray(createTestCoseKey()),
                        DeviceEngagement.VERSION to CborString("1.0"),
                    ),
                ),
            )

        val decoded = codec.decode(original).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
    }

    @Test
    fun deviceEngagement_decode_unwraps_tag24_encoded_bytes() {
        val raw =
            codec
                .encode(
                    DeviceEngagement.V1_0(
                        security =
                            DeviceEngagementSecurity(
                                cipherSuite = 1u,
                                eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                            ),
                        original = null,
                    ),
                ).getOrThrow()
        val wrapped = Cbor.encode(CborEncodedItem<ByteArray>(raw))

        val decoded = codec.decode(wrapped).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertContentEquals(raw, decoded.originalBytes)
        assertContentEquals(raw, decoded.value.original)
        assertContentEquals(raw, reEncoded)
    }

    @Test
    fun deviceEngagement_encodeMessageItem_wraps_encoded_item_in_message_map() {
        val encodedItem =
            codec
                .encodeItem(
                    DeviceEngagement.V1_0(
                        security =
                            DeviceEngagementSecurity(
                                cipherSuite = 1u,
                                eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                            ),
                        original = null,
                    ),
                ).getOrThrow()

        val messageBytes = codec.encodeMessageItem(encodedItem).getOrThrow()
        val message =
            com.sphereon.cbor.Cbor
                .decode<CborMap<CborItem<*>, CborItem<*>>>(messageBytes)
        val engagementBytes =
            message.value.entries
                .first { (key, _) ->
                    StringLabel.fromCborItem(key) == DeviceEngagementMessage.DEVICE_ENGAGEMENT_BYTES
                }.value

        assertEquals(encodedItem, engagementBytes)
    }

    @Test
    fun deviceEngagement_decodeMessage_extracts_original_engagement_bytes() {
        val engagement =
            DeviceEngagement.V1_0(
                security =
                    DeviceEngagementSecurity(
                        cipherSuite = 1u,
                        eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                    ),
                original = null,
            )

        val encoded = codec.encode(engagement).getOrThrow()
        val messageBytes = codec.encodeMessage(engagement).getOrThrow()
        val decoded = codec.decodeMessage(messageBytes).getOrThrow()

        assertEquals(engagement.version, decoded.value.version)
        assertEquals(engagement.security, decoded.value.security)
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun deviceEngagement_v10_decode_preserves_nested_retrieval_methods_without_public_decoder_helpers() {
        val engagement =
            DeviceEngagement.V1_0(
                security =
                    DeviceEngagementSecurity(
                        cipherSuite = 1u,
                        eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                    ),
                deviceRetrievalMethods =
                    arrayOf(
                        DeviceRetrievalMethod(
                            type = DeviceRetrievalMethodType.WEBSITE,
                            retrievalOptions = RestApiOptions("https://reader.example.com/mdoc/session/123"),
                        ),
                    ),
                serverRetrievalMethod =
                    ServerRetrievalMethods(
                        Oidc =
                            ServerRetrievalInfo(
                                version = 1u,
                                issuerUrl = "https://issuer.example.com",
                                serverRetrievalToken = "oidc-token",
                            ),
                        WebApi =
                            ServerRetrievalInfo(
                                version = 1u,
                                issuerUrl = "https://api.example.com",
                                serverRetrievalToken = "api-token",
                            ),
                    ),
                original = null,
            )

        val decoded = codec.decode(codec.encode(engagement).getOrThrow()).getOrThrow().value as DeviceEngagement.V1_0

        assertEquals(DeviceRetrievalMethodType.WEBSITE, decoded.deviceRetrievalMethods?.single()?.type)
        assertEquals(
            "https://reader.example.com/mdoc/session/123",
            (decoded.deviceRetrievalMethods?.single()?.retrievalOptions as RestApiOptions).uri,
        )
        assertEquals(engagement.serverRetrievalMethod, decoded.serverRetrievalMethod)
    }

    @Test
    fun deviceEngagement_v11_decode_preserves_origin_info_and_capabilities_without_public_decoder_helpers() {
        val engagement =
            DeviceEngagement.V1_1(
                security =
                    DeviceEngagementSecurity(
                        cipherSuite = 1u,
                        eDeviceKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(encodeCoseKey(it), it) },
                    ),
                deviceRetrievalMethods =
                    arrayOf(
                        DeviceRetrievalMethod(
                            type = DeviceRetrievalMethodType.WEBSITE,
                            retrievalOptions = RestApiOptions("https://reader.example.com/mdoc/session/456"),
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
                original = null,
            )

        val decoded = codec.decode(codec.encode(engagement).getOrThrow()).getOrThrow().value as DeviceEngagement.V1_1

        assertEquals(
            "reader.example.com",
            decoded.originInfos
                ?.single()
                ?.details
                ?.get("domain"),
        )
        assertEquals(arrayOf(CoseCurve.P_256).toList(), decoded.capabilities?.macKeyCurves?.toList())
        assertEquals(true, decoded.capabilities?.macKeysSupport)
        assertEquals(true, decoded.capabilities?.handoverSessionEstablishmentSupport)
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
