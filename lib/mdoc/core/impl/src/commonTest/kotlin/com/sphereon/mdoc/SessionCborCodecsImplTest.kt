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

package com.sphereon.mdoc

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.StringLabel
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceRequestCborCodecImpl
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.NfcHandover
import com.sphereon.mdoc.transfer.reader.OID4VPHandover
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.ReaderAuthentication
import com.sphereon.mdoc.transfer.reader.RestApiHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionCborCodecsImplTest {
    private val sessionEstablishmentCodec = SessionEstablishmentCborCodecImpl()
    private val sessionDataCodec = SessionDataCborCodecImpl()
    private val handoverCodec = HandoverCborCodecImpl()
    private val sessionTranscriptCodec = SessionTranscriptCborCodecImpl()
    private val readerAuthenticationCodec = ReaderAuthenticationCborCodecImpl()
    private val coseKeyCodec = CoseKeyCborCodecImpl()
    private val deviceRequestCodec = DeviceRequestCborCodecImpl()

    @Test
    fun sessionEstablishment_round_trips_and_preserves_original_bytes() {
        val establishment =
            SessionEstablishment(
                encodedReaderKey =
                    encodedCoseKey(
                        CoseKey(
                            kty = CborUInt(2),
                            crv = CborUInt(1),
                            x = CborByteString(byteArrayOf(0x01, 0x02, 0x03)),
                            y = CborByteString(byteArrayOf(0x04, 0x05, 0x06)),
                        ),
                    ),
                data = CborByteString(byteArrayOf(0x07, 0x08, 0x09)),
                original = null,
            )

        val encoded = sessionEstablishmentCodec.encode(establishment).getOrThrow()
        val decoded = sessionEstablishmentCodec.decode(encoded).getOrThrow()

        assertEquals(establishment, decoded.value.copyWith(original = null))
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun sessionData_round_trips_and_preserves_original_bytes() {
        val sessionData =
            SessionData(
                data = CborByteString(byteArrayOf(0x0A, 0x0B)),
                status = CborUInt(20),
                original = null,
            )

        val encoded = sessionDataCodec.encode(sessionData).getOrThrow()
        val decoded = sessionDataCodec.decode(encoded).getOrThrow()

        assertEquals(sessionData, decoded.value.copyWith(original = null))
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun sessionEstablishment_encode_prefers_original_bytes_over_reencoding() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        SessionEstablishment.DATA to CborByteString(byteArrayOf(0x07, 0x08, 0x09)),
                        SessionEstablishment.E_READER_KEY to
                            encodedCoseKey(
                                CoseKey(
                                    kty = CborUInt(2),
                                    crv = CborUInt(1),
                                    x = CborByteString(byteArrayOf(0x01, 0x02, 0x03)),
                                    y = CborByteString(byteArrayOf(0x04, 0x05, 0x06)),
                                ),
                            ),
                    ),
                ),
            )

        val decoded = sessionEstablishmentCodec.decode(original).getOrThrow()
        val reEncoded = sessionEstablishmentCodec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
    }

    @Test
    fun sessionData_encode_prefers_original_bytes_over_reencoding() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        SessionData.STATUS to CborUInt(20),
                        SessionData.DATA to CborByteString(byteArrayOf(0x0A, 0x0B)),
                    ),
                ),
            )

        val decoded = sessionDataCodec.decode(original).getOrThrow()
        val reEncoded = sessionDataCodec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
    }

    @Test
    fun sessionTranscript_round_trips_and_preserves_original_bytes() {
        @Suppress("UNCHECKED_CAST")
        val transcript =
            SessionTranscript(
                deviceEngagement = null,
                eReaderKey =
                    encodedCoseKey(
                        CoseKey(
                            kty = CborUInt(2),
                            crv = CborUInt(1),
                            x = CborByteString(byteArrayOf(0x01, 0x02, 0x03)),
                            y = CborByteString(byteArrayOf(0x04, 0x05, 0x06)),
                        ),
                    ),
                handover = QrHandover() as Handover<*, CborItem<*>>,
                original = null,
            )

        val encoded = sessionTranscriptCodec.encode(transcript).getOrThrow()
        val decoded = sessionTranscriptCodec.decode(encoded).getOrThrow()

        assertEquals(transcript.deviceEngagement, decoded.value.deviceEngagement)
        assertEquals(transcript.eReaderKey, decoded.value.eReaderKey)
        assertEquals(transcript.handover::class, decoded.value.handover::class)
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun sessionTranscript_decode_accepts_tag24_wrapper_and_preserves_inner_bytes() {
        @Suppress("UNCHECKED_CAST")
        val transcript =
            SessionTranscript(
                deviceEngagement = null,
                eReaderKey =
                    encodedCoseKey(
                        CoseKey(
                            kty = CborUInt(2),
                            crv = CborUInt(1),
                            x = CborByteString(byteArrayOf(0x0A, 0x0B, 0x0C)),
                            y = CborByteString(byteArrayOf(0x0D, 0x0E, 0x0F)),
                        ),
                    ),
                handover = QrHandover() as Handover<*, CborItem<*>>,
                original = null,
            )
        val original = sessionTranscriptCodec.encode(transcript).getOrThrow()
        val tagged = sessionTranscriptCodec.encodeTag24(transcript).getOrThrow()

        val decoded = sessionTranscriptCodec.decode(tagged).getOrThrow()
        val reEncoded = sessionTranscriptCodec.encode(decoded.value).getOrThrow()
        val reTagged = sessionTranscriptCodec.encodeTag24(decoded.value).getOrThrow()

        assertContentEquals(original, decoded.originalBytes)
        assertContentEquals(original, decoded.value.original)
        assertContentEquals(original, reEncoded)
        assertContentEquals(tagged, reTagged)
    }

    @Test
    fun sessionTranscript_round_trips_rest_api_handover() {
        @Suppress("UNCHECKED_CAST")
        val transcript =
            SessionTranscript(
                deviceEngagement = null,
                eReaderKey = null,
                handover = RestApiHandover(byteArrayOf(0x11, 0x22, 0x33)) as Handover<*, CborItem<*>>,
                original = null,
            )

        val encoded = sessionTranscriptCodec.encode(transcript).getOrThrow()
        val decoded = sessionTranscriptCodec.decode(encoded).getOrThrow()

        assertIs<RestApiHandover>(decoded.value.handover)
        assertEquals(transcript.handover, decoded.value.handover)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun handover_codec_round_trips_oid4vp() {
        @Suppress("UNCHECKED_CAST")
        val handover =
            OID4VPHandover(
                clientIdHash = byteArrayOf(0x01, 0x02),
                responseUriHash = byteArrayOf(0x03, 0x04),
                nonce = "nonce-123",
            ) as Handover<*, CborItem<*>>

        val encoded = handoverCodec.encode(handover).getOrThrow()
        val decoded = handoverCodec.decode(encoded).getOrThrow()

        assertEquals(handover, decoded)
    }

    @Test
    fun handover_codec_round_trips_qr_nfc_and_rest_api() {
        @Suppress("UNCHECKED_CAST")
        val qrHandover = QrHandover() as Handover<*, CborItem<*>>
        val encodedQr = handoverCodec.encode(qrHandover).getOrThrow()
        val decodedQr = handoverCodec.decode(encodedQr).getOrThrow()
        assertTrue(decodedQr is QrHandover)

        @Suppress("UNCHECKED_CAST")
        val nfcHandover =
            NfcHandover(
                handoverSelectMessage = byteArrayOf(0x21, 0x22),
                handoverRequestMessage = byteArrayOf(0x23, 0x24),
            ) as Handover<*, CborItem<*>>
        val encodedNfc = handoverCodec.encode(nfcHandover).getOrThrow()
        val decodedNfc = handoverCodec.decode(encodedNfc).getOrThrow()
        assertEquals(nfcHandover, decodedNfc)

        @Suppress("UNCHECKED_CAST")
        val restApiHandover = RestApiHandover(byteArrayOf(0x31, 0x32, 0x33)) as Handover<*, CborItem<*>>
        val encodedRestApi = handoverCodec.encode(restApiHandover).getOrThrow()
        val decodedRestApi = handoverCodec.decode(encodedRestApi).getOrThrow()
        assertEquals(restApiHandover, decodedRestApi)
    }

    @Test
    fun handover_codec_rejects_invalid_cbor_item() {
        assertFailsWith<IllegalArgumentException> {
            handoverCodec.decode(CborString("invalid")).getOrThrow()
        }
    }

    @Test
    fun readerAuthentication_round_trips_and_keeps_items_request_as_tag24_item() {
        @Suppress("UNCHECKED_CAST")
        val readerAuthentication =
            ReaderAuthentication(
                sessionTranscript =
                    SessionTranscript(
                        deviceEngagement = null,
                        eReaderKey = null,
                        handover = QrHandover() as Handover<*, CborItem<*>>,
                        original = null,
                    ),
                itemsRequestBytes =
                    encodedDeviceItemsRequest(
                        DeviceItemsRequest(
                            docType = DocType("org.iso.18013.5.1.mDL"),
                            nameSpaces =
                                mapOf(
                                    NameSpace("org.iso.18013.5.1") to
                                        mapOf(
                                            DataElementIdentifier("family_name") to IntentToRetain(true),
                                        ),
                                ),
                            original = null,
                        ),
                    ),
            )

        val encoded = readerAuthenticationCodec.encode(readerAuthentication).getOrThrow()
        val decoded = readerAuthenticationCodec.decode(encoded).getOrThrow()
        val reEncoded = readerAuthenticationCodec.encode(decoded.value).getOrThrow()
        val encodedArray: CborArray<CborItem<*>> =
            com.sphereon.cbor.Cbor
                .decode(encoded)
        val encodedItem = encodedArray.value[2]

        assertIs<CborEncodedItem<*>>(encodedItem)
        assertEquals(ReaderAuthentication.READER_AUTHENTICATION, encodedArray.value[0] as CborString)
        assertEquals(readerAuthentication.sessionTranscript.handover::class, decoded.value.sessionTranscript.handover::class)
        assertEquals(readerAuthentication.itemsRequestBytes.data(), decoded.value.itemsRequestBytes.data())
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, reEncoded)
    }

    private fun encodedCoseKey(value: CoseKey): CborEncodedItem<CoseKey> = CborEncodedItem(coseKeyCodec.encode(value).getOrThrow(), value)

    private fun encodedDeviceItemsRequest(value: DeviceItemsRequest): CborEncodedItem<DeviceItemsRequest> = CborEncodedItem(encodeDeviceItemsRequestBytes(value), value)

    @Suppress("UNCHECKED_CAST")
    private fun encodeDeviceItemsRequestBytes(value: DeviceItemsRequest): ByteArray {
        val encodedRequest =
            deviceRequestCodec
                .encode(
                    DeviceRequest(
                        docRequests = arrayOf(DocRequest(itemsRequest = value)),
                        original = null,
                    ),
                ).getOrThrow()
        val requestMap: CborMap<CborItem<*>, CborItem<*>> =
            com.sphereon.cbor.Cbor
                .decode(encodedRequest)
        val docRequests = requireEntry(requestMap, DeviceRequest.DOC_REQUESTS) as CborArray<CborMap<CborItem<*>, CborItem<*>>>
        val docRequest = docRequests.value.single()
        val encodedItemsRequest = requireEntry(docRequest, DocRequest.ITEMS_REQUEST) as CborEncodedItem<CborMap<CborItem<*>, CborItem<*>>>
        return encodedItemsRequest.value.taggedItem.value
    }

    private fun requireEntry(
        structure: CborMap<CborItem<*>, CborItem<*>>,
        label: StringLabel,
    ): CborItem<*> =
        structure.value.entries
            .firstOrNull { (key, _) -> StringLabel.fromCborItem(key) == label }
            ?.value
            ?: throw IllegalArgumentException("Key (${label.value}) not found in cbor map")
}
