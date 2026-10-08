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
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborInt
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.TDate
import com.sphereon.cbor.toCborBool
import com.sphereon.cbor.toCborItem
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.mso.DeviceKeyInfo
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.data.mso.MsoVersion
import com.sphereon.mdoc.data.mso.ValidityInfo
import com.sphereon.mdoc.testutil.coseKeyAsCborMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceCborCodecsImplTest {
    private val deviceRequestCodec = DeviceRequestCborCodecImpl()
    private val deviceResponseCodec = DeviceResponseCborCodecImpl()
    private val deviceSignedCodec = DeviceSignedCborCodecImpl()
    private val issuerSignedCodec = IssuerSignedCborCodecImpl()
    private val mobileSecurityObjectCodec = MobileSecurityObjectCborCodecImpl()

    @Test
    fun deviceRequest_round_trips_and_preserves_original_bytes() {
        val request =
            DeviceRequest(
                version = DeviceRequestVersion("1.0"),
                docRequests = null,
                macKeys = null,
                oid4vpRequest = null,
                original = null,
            )

        val encoded = deviceRequestCodec.encode(request).getOrThrow()
        val decoded = deviceRequestCodec.decode(encoded).getOrThrow()

        assertEquals(request, decoded.value.copy(original = null))
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun deviceResponse_round_trips_and_preserves_original_bytes() {
        val response =
            DeviceResponse(
                version = DeviceResponseVersion("1.0"),
                documents = null,
                documentErrors = null,
                status = DeviceResponseStatus(0u),
                original = null,
            )

        val encoded = deviceResponseCodec.encode(response).getOrThrow()
        val decoded = deviceResponseCodec.decode(encoded).getOrThrow()

        assertEquals(response, decoded.value.copy(original = null))
        assertContentEquals(encoded, decoded.originalBytes)
        assertContentEquals(encoded, decoded.value.original)
    }

    @Test
    fun deviceResponse_status_error_omits_empty_documents_array() {
        val response =
            DeviceResponse.Builder()
                .withStatus(DeviceResponseStatus(10u))
                .build()

        val encoded = deviceResponseCodec.encode(response).getOrThrow()
        val structure = decodeCborMap(encoded)

        assertEquals(false, structure.value.containsKey(DeviceResponse.DOCUMENTS))
        assertEquals(DeviceResponseStatus(10u), deviceResponseCodec.decode(encoded).getOrThrow().value.status)
    }

    @Test
    fun deviceResponse_rejects_unsigned_status_values_that_overflow_uint() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.VERSION to CborString("1.0"),
                        DeviceResponse.STATUS to CborUInt(UInt.MAX_VALUE.toLong() + 10L),
                    ),
                ),
            )

        assertTrue(deviceResponseCodec.decode(original).isErr)
    }

    @Test
    fun deviceResponse_rejects_encrypted_document_request_ids_that_overflow_uint() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.VERSION to CborString("1.0"),
                        DeviceResponse.STATUS to CborUInt(0),
                        DeviceResponse.ENCRYPTED_DOCUMENTS to
                            CborArray(
                                mutableListOf(
                                    CborMap(
                                        mutableMapOf(
                                            StringLabel("enc") to CborByteString(byteArrayOf(1)),
                                            StringLabel("cipherText") to CborByteString(byteArrayOf(2)),
                                            StringLabel("docRequestID") to CborUInt(UInt.MAX_VALUE.toLong() + 1L),
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            )

        assertTrue(deviceResponseCodec.decode(original).isErr)
    }

    @Test
    fun deviceResponse_preserves_opaque_encrypted_document_envelopes_when_forwarded() {
        val encryptedDocument =
            CborMap(
                mutableMapOf(
                    StringLabel("enc") to CborByteString(byteArrayOf(1, 2)),
                    StringLabel("cipherText") to CborByteString(byteArrayOf(3, 4)),
                    StringLabel("docRequestID") to CborUInt(7),
                ),
            )
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.VERSION to CborString("1.0"),
                        DeviceResponse.STATUS to CborUInt(0),
                        DeviceResponse.ENCRYPTED_DOCUMENTS to
                            CborArray(
                                mutableListOf(
                                    encryptedDocument,
                                ),
                            ),
                    ),
                ),
            )

        val decoded = deviceResponseCodec.decode(original).getOrThrow()
        val reEncoded = deviceResponseCodec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
        val nestedOriginal = assertNotNull(decoded.value.encryptedDocuments).single().original
        assertContentEquals(
            Cbor.encode(encryptedDocument),
            nestedOriginal,
        )
    }

    @Test
    fun deviceRequest_encode_prefers_original_bytes_over_reencoding() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceRequest.MAC_KEYS to
                            CborArray(
                                mutableListOf(
                                    coseKeyAsCborMap(
                                        CoseKey(
                                            kty = CborUInt(2),
                                            original = null,
                                        ),
                                    ),
                                ),
                            ),
                        DeviceRequest.VERSION to CborString("1.0"),
                    ),
                ),
            )

        val decoded = deviceRequestCodec.decode(original).getOrThrow()
        val reEncoded = deviceRequestCodec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
    }

    @Test
    fun deviceRequest_decodes_mac_keys() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceRequest.VERSION to CborString("1.0"),
                        DeviceRequest.MAC_KEYS to
                            CborArray(
                                mutableListOf(
                                    coseKeyAsCborMap(
                                        CoseKey(
                                            kty = CborUInt(2),
                                            original = null,
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            )

        val decoded = deviceRequestCodec.decode(original).getOrThrow().value
        val macKeys = assertNotNull(decoded.macKeys)

        assertEquals(1, macKeys.size)
        assertEquals(CborUInt(2), macKeys.single().kty)
    }

    @Test
    fun deviceRequest_encode_promotes_mac_keys_to_second_edition_version() {
        val request =
            DeviceRequest(
                version = DeviceRequestVersion("1.0"),
                macKeys =
                    arrayOf(
                        CoseKey(
                            kty = CborUInt(2),
                            original = null,
                        ),
                    ),
                original = null,
            )

        val encoded = deviceRequestCodec.encode(request).getOrThrow()
        val structure = decodeCborMap(encoded)

        assertEquals("1.1", (requireEntry(structure, DeviceRequest.VERSION) as CborString).value)
        assertEquals(DeviceRequestVersion("1.1"), deviceRequestCodec.decode(encoded).getOrThrow().value.version)
    }

    @Test
    fun deviceRequest_decodes_doc_requests() {
        val request =
            DeviceRequest(
                version = DeviceRequestVersion("1.0"),
                docRequests =
                    arrayOf(
                        DocRequest(
                            itemsRequest =
                                DeviceItemsRequest(
                                    docType = DocType("org.iso.18013.5.1.mDL"),
                                    nameSpaces =
                                        mapOf(
                                            NameSpace("org.iso.18013.5.1") to
                                                mapOf(
                                                    DataElementIdentifier("given_name") to IntentToRetain(false),
                                                ),
                                        ),
                                    requestInfo = null,
                                    original = null,
                                ),
                            readerAuth = null,
                            original = null,
                        ),
                    ),
                macKeys = null,
                oid4vpRequest = null,
                original = null,
            )

        val encoded = deviceRequestCodec.encode(request).getOrThrow()
        val decoded = deviceRequestCodec.decode(encoded).getOrThrow().value
        val docRequests = assertNotNull(decoded.docRequests)

        assertEquals(1, docRequests.size)
        assertEquals(DocType("org.iso.18013.5.1.mDL"), docRequests.single().itemsRequest.docType)
        assertEquals(
            setOf(DataElementIdentifier("given_name")),
            docRequests
                .single()
                .itemsRequest
                .getIdentifiers(NameSpace("org.iso.18013.5.1"))
                .keys,
        )
    }

    @Test
    fun legacy_request_info_is_not_reinterpreted_as_second_edition_doc_request_info() {
        val requestInfo = mapOf<String, Any>("purpose" to "age_verification")
        val request =
            DeviceRequest(
                version = DeviceRequestVersion("1.0"),
                docRequests =
                    arrayOf(
                        DocRequest(
                            itemsRequest =
                                DeviceItemsRequest(
                                    docType = DocType("org.iso.18013.5.1.mDL"),
                                    nameSpaces = emptyMap(),
                                    requestInfo = requestInfo,
                                    original = null,
                                ),
                            readerAuth = null,
                            original = null,
                        ),
                    ),
                macKeys = null,
                oid4vpRequest = null,
                original = null,
            )

        val decoded = deviceRequestCodec.decode(deviceRequestCodec.encode(request).getOrThrow()).getOrThrow().value
        val decodedItems = assertNotNull(decoded.docRequests).single().itemsRequest
        assertEquals(requestInfo, decodedItems.requestInfo)
        assertEquals(null, decodedItems.docRequestInfo)
    }

    @Test
    fun deviceRequest_doc_request_info_does_not_change_top_level_wire_version() {
        val request =
            DeviceRequest(
                version = DeviceRequestVersion("1.0"),
                docRequests =
                    arrayOf(
                        DocRequest(
                            itemsRequest =
                                DeviceItemsRequest(
                                    docType = DocType("org.iso.18013.5.1.mDL"),
                                    nameSpaces = emptyMap(),
                                    docRequestInfo = DocRequestInfo(maximumResponseSize = 4096u),
                                ),
                        ),
                    ),
                original = null,
            )

        val encoded = deviceRequestCodec.encode(request).getOrThrow()
        val structure = decodeCborMap(encoded)

        assertEquals("1.0", (requireEntry(structure, DeviceRequest.VERSION) as CborString).value)
        assertEquals(DeviceRequestVersion("1.0"), deviceRequestCodec.decode(encoded).getOrThrow().value.version)
    }

    @Test
    fun deviceRequest_decodes_doc_request_items_request_with_original_bytes() {
        val itemsRequestBytes =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceItemsRequest.NAME_SPACES to
                            CborMap(
                                mutableMapOf(
                                    StringLabel("org.iso.18013.5.1") to
                                        CborMap(
                                            mutableMapOf(
                                                StringLabel("given_name") to false.toCborBool(),
                                            ),
                                        ),
                                ),
                            ),
                        DeviceItemsRequest.DOC_TYPE to CborString("org.iso.18013.5.1.mDL"),
                    ),
                ),
            )
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceRequest.VERSION to CborString("1.0"),
                        DeviceRequest.DOC_REQUESTS to
                            CborArray(
                                mutableListOf(
                                    CborMap(
                                        mutableMapOf(
                                            DocRequest.ITEMS_REQUEST to CborEncodedItem<CborMap<StringLabel, com.sphereon.cbor.CborItem<*>>>(itemsRequestBytes),
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            )

        val decoded = deviceRequestCodec.decode(original).getOrThrow().value
        val docRequest = assertNotNull(decoded.docRequests).single()

        assertContentEquals(itemsRequestBytes, assertNotNull(docRequest.itemsRequest.original))
        assertEquals(DocType("org.iso.18013.5.1.mDL"), docRequest.itemsRequest.docType)
        assertEquals(
            setOf(DataElementIdentifier("given_name")),
            docRequest.itemsRequest.getIdentifiers(NameSpace("org.iso.18013.5.1")).keys,
        )
    }

    @Test
    fun deviceRequest_encode_preserves_nested_items_request_original_bytes() {
        val itemsRequestBytes =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceItemsRequest.NAME_SPACES to
                            CborMap(
                                mutableMapOf(
                                    StringLabel("org.iso.18013.5.1") to
                                        CborMap(
                                            mutableMapOf(
                                                StringLabel("given_name") to false.toCborBool(),
                                            ),
                                        ),
                                ),
                            ),
                        DeviceItemsRequest.DOC_TYPE to CborString("org.iso.18013.5.1.mDL"),
                    ),
                ),
            )
        val request =
            DeviceRequest(
                version = DeviceRequestVersion("1.0"),
                docRequests =
                    arrayOf(
                        DocRequest(
                            itemsRequest =
                                DeviceItemsRequest(
                                    docType = DocType("org.iso.18013.5.1.mDL"),
                                    nameSpaces =
                                        mapOf(
                                            NameSpace("org.iso.18013.5.1") to
                                                mapOf(
                                                    DataElementIdentifier("given_name") to IntentToRetain(false),
                                                ),
                                        ),
                                    requestInfo = null,
                                    original = itemsRequestBytes,
                                ),
                            readerAuth = null,
                            original = null,
                        ),
                    ),
                macKeys = null,
                oid4vpRequest = null,
                original = null,
            )

        val encoded = deviceRequestCodec.encode(request).getOrThrow()
        val parsed = decodeCborMap(encoded)
        val docRequest = (requireEntry(parsed, DeviceRequest.DOC_REQUESTS) as CborArray<CborMap<CborItem<*>, CborItem<*>>>).value.single()
        val encodedItemsRequest = requireEntry(docRequest, DocRequest.ITEMS_REQUEST) as CborEncodedItem<CborMap<CborItem<*>, CborItem<*>>>

        assertContentEquals(itemsRequestBytes, encodedItemsRequest.value.taggedItem.value)
    }

    @Test
    fun deviceResponse_encode_prefers_original_bytes_over_reencoding() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.STATUS to CborUInt(0),
                        DeviceResponse.VERSION to CborString("1.0"),
                    ),
                ),
            )

        val decoded = deviceResponseCodec.decode(original).getOrThrow()
        val reEncoded = deviceResponseCodec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, reEncoded)
    }

    @Test
    fun deviceResponse_decodes_document_errors() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.VERSION to CborString("1.0"),
                        DeviceResponse.DOCUMENT_ERRORS to
                            CborArray(
                                mutableListOf(
                                    CborMap(
                                        mutableMapOf(
                                            CborString("org.iso.18013.5.1.mDL") to CborInt(12),
                                        ),
                                    ),
                                ),
                            ),
                        DeviceResponse.STATUS to CborUInt(0),
                    ),
                ),
            )

        val decoded = deviceResponseCodec.decode(original).getOrThrow().value
        val documentErrors = assertNotNull(decoded.documentErrors)
        val firstEntry = documentErrors.single().entries.single()

        assertEquals(DocType("org.iso.18013.5.1.mDL"), firstEntry.key)
        assertEquals(DocumentError(12), firstEntry.value)
    }

    @Test
    fun deviceResponse_decodes_documents() {
        val response =
            DeviceResponse(
                version = DeviceResponseVersion("1.0"),
                documents =
                    arrayOf(
                        Document(
                            docType = DocType("org.iso.18013.5.1.mDL"),
                            issuerSigned = createTestIssuerSigned(),
                            deviceSigned = null,
                            original = null,
                        ),
                    ),
                documentErrors = null,
                status = DeviceResponseStatus(0u),
                original = null,
            )

        val encoded = deviceResponseCodec.encode(response).getOrThrow()
        val decoded = deviceResponseCodec.decode(encoded).getOrThrow().value
        val documents = assertNotNull(decoded.documents)
        val decodedDocument = documents.single()

        assertEquals(1, documents.size)
        assertEquals(null, decodedDocument.original)
        assertEquals(DocType("org.iso.18013.5.1.mDL"), decodedDocument.docType)
        assertEquals(
            response.documents!!
                .single()
                .issuerSigned.issuerAuth.signature,
            decodedDocument.issuerSigned.issuerAuth.signature,
        )
        assertEquals(
            setOf(NameSpace("org.iso.18013.5.1")),
            decodedDocument.issuerSigned.nameSpaces?.keys,
        )
    }

    @Test
    fun deviceResponse_decodes_document_element_errors() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.VERSION to CborString("1.0"),
                        DeviceResponse.DOCUMENTS to
                            CborArray(
                                mutableListOf(
                                    CborMap(
                                        mutableMapOf(
                                            Document.DOC_TYPE to CborString("org.iso.18013.5.1.mDL"),
                                            Document.ISSUER_SIGNED to decodeItem(issuerSignedCodec.encode(createTestIssuerSigned()).getOrThrow()),
                                            Document.ERRORS to
                                                CborMap(
                                                    mutableMapOf(
                                                        CborString("org.iso.18013.5.1") to
                                                            CborMap(
                                                                mutableMapOf(
                                                                    CborString("given_name") to CborInt(7),
                                                                ),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            ),
                        DeviceResponse.STATUS to CborUInt(0),
                    ),
                ),
            )

        val decoded = deviceResponseCodec.decode(original).getOrThrow().value
        val document = assertNotNull(decoded.documents).single()
        val errors = assertNotNull(document.errors)

        assertEquals(7L, errors.getValue(NameSpace("org.iso.18013.5.1")).getValue(DataElementIdentifier("given_name")))
    }

    @Test
    fun deviceResponse_decodes_document_device_signed_via_typed_codec() {
        val original =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        DeviceResponse.VERSION to CborString("1.0"),
                        DeviceResponse.DOCUMENTS to
                            CborArray(
                                mutableListOf(
                                    CborMap(
                                        mutableMapOf(
                                            Document.DOC_TYPE to CborString("org.iso.18013.5.1.mDL"),
                                            Document.ISSUER_SIGNED to decodeItem(issuerSignedCodec.encode(createTestIssuerSigned()).getOrThrow()),
                                            Document.DEVICE_SIGNED to decodeItem(deviceSignedCodec.encode(createTestDeviceSigned()).getOrThrow()),
                                        ),
                                    ),
                                ),
                            ),
                        DeviceResponse.STATUS to CborUInt(0),
                    ),
                ),
            )

        val decoded = deviceResponseCodec.decode(original).getOrThrow().value
        val deviceSigned = assertNotNull(decoded.documents).single().deviceSigned

        assertNotNull(deviceSigned)
        assertEquals(null, deviceSigned.original)
        assertEquals(
            "opaque-self-asserted",
            deviceSigned.nameSpaces.value
                .getValue(NameSpace("org.iso.18013.5.1"))
                .value
                .getValue(DataElementIdentifier("portrait")),
        )
        assertEquals(
            true,
            deviceSigned.nameSpaces.value
                .getValue(NameSpace("org.iso.18013.5.1"))
                .value
                .getValue(DataElementIdentifier("age_over_18")),
        )
        assertEquals(
            CoseAlgorithm.ES256,
            deviceSigned.deviceAuth.deviceSignature
                ?.protectedHeader
                ?.alg,
        )
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

    @Suppress("UNCHECKED_CAST")
    private fun decodeCborMap(bytes: ByteArray): CborMap<CborItem<*>, CborItem<*>> =
        com.sphereon.cbor.Cbor
            .decode(bytes)

    private fun decodeItem(bytes: ByteArray): CborItem<*> =
        com.sphereon.cbor.Cbor
            .decode(bytes)

    private fun requireEntry(
        structure: CborMap<CborItem<*>, CborItem<*>>,
        label: StringLabel,
    ): CborItem<*> =
        structure.value.entries
            .firstOrNull { (key, _) -> StringLabel.fromCborItem(key) == label }
            ?.value
            ?: throw IllegalArgumentException("Key (${label.value}) not found in cbor map")

    private fun createTestDeviceKeyInfo(): DeviceKeyInfo =
        DeviceKeyInfo(
            deviceKey = createTestCoseKey(),
            keyAuthorizations = null,
            keyInfo = null,
            original = null,
        )

    private fun createTestValidityInfo(): ValidityInfo {
        val now = TDate("2025-01-20T12:00:00Z")
        return ValidityInfo(
            signed = now,
            validFrom = now,
            validUntil = now,
            expectedUpdate = null,
        )
    }

    private fun createTestMso(): MobileSecurityObject =
        MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null,
        )

    private fun createTestIssuerAuth(mso: MobileSecurityObject = createTestMso()): CoseSign1<MobileSecurityObject> {
        val encodedMso = mobileSecurityObjectCodec.encodeTag24(mso).getOrThrow()
        return CoseSign1(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = null,
            payload = CborByteString(encodedMso),
            signature = CborByteString(ByteArray(64) { it.toByte() }),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createIssuerSignedItem(
        digestId: UInt,
        elementId: String,
        value: Any,
    ): CborEncodedItem<IssuerSignedItem<Any>> {
        val item =
            IssuerSignedItem(
                digestID = DigestID(digestId),
                random = RandomValue(ByteArray(24) { 0x42.toByte() }),
                elementIdentifier = DataElementIdentifier(elementId),
                elementValue = value,
            )
        val encoded =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        IssuerSignedItem.DIGEST_ID to com.sphereon.cbor.CborUInt(digestId.toLong()),
                        IssuerSignedItem.RANDOM to item.random.toCborItem(),
                        IssuerSignedItem.ELEMENT_IDENTIFIER to CborString(elementId),
                        IssuerSignedItem.ELEMENT_VALUE to item.elementValue.toCborItem(),
                    ),
                ),
            )
        return CborEncodedItem(encoded, item as IssuerSignedItem<Any>)
    }

    private fun createTestIssuerSigned(): IssuerSigned =
        IssuerSigned(
            nameSpaces =
                mapOf(
                    NameSpace("org.iso.18013.5.1") to
                        arrayOf(
                            createIssuerSignedItem(1u, "given_name", "John"),
                            createIssuerSignedItem(2u, "family_name", "Doe"),
                        ),
                ),
            issuerAuth = createTestIssuerAuth(),
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
}
