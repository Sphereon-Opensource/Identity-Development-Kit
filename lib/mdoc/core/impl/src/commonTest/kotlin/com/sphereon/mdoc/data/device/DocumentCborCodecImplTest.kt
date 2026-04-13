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
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.TDate
import com.sphereon.cbor.toCborItem
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
import com.sphereon.mdoc.testutil.coseSign1AsCborArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DocumentCborCodecImplTest {
    private val codec = DocumentCborCodecImpl()
    private val deviceSignedCodec = DeviceSignedCborCodecImpl()
    private val issuerSignedCodec = IssuerSignedCborCodecImpl()
    private val mobileSecurityObjectCodec = MobileSecurityObjectCborCodecImpl()

    @Test
    fun document_round_trips_and_preserves_original_bytes() {
        val original = encodeOutOfOrderDocument()

        val decoded = codec.decode(original).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertDocumentEquals(createTestDocument(), decoded.value.copy(original = null))
        assertContentEquals(original, decoded.originalBytes)
        assertContentEquals(original, decoded.value.original)
        assertContentEquals(original, reEncoded)
    }

    @Test
    fun document_codec_rejects_invalid_cbor_item() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(Cbor.encode(CborString("invalid"))).getOrThrow()
        }
    }

    @Test
    fun document_encode_preserves_nested_issuer_signed_original_bytes() {
        val issuerSignedBytes = createOutOfOrderIssuerSignedBytes()
        val issuerSigned = IssuerSignedCborCodecImpl().decode(issuerSignedBytes).getOrThrow().value
        val document =
            Document(
                docType = DocType("org.iso.18013.5.1.mDL"),
                issuerSigned = issuerSigned,
                deviceSigned = null,
                original = null,
            )

        val encoded = codec.encode(document).getOrThrow()
        val parsed = decodeCborMap(encoded)
        val embeddedIssuerSigned = requireEntry(parsed, Document.ISSUER_SIGNED) as CborMap<CborItem<*>, CborItem<*>>

        assertContentEquals(
            issuerSignedBytes,
            com.sphereon.cbor.Cbor
                .encode(embeddedIssuerSigned),
        )
    }

    private fun createTestDocument(): Document =
        Document(
            docType = DocType("org.iso.18013.5.1.mDL"),
            issuerSigned = createTestIssuerSigned(),
            deviceSigned = createTestDeviceSigned(),
            errors =
                mapOf(
                    NameSpace("org.iso.18013.5.1") to
                        mapOf(
                            DataElementIdentifier("portrait") to 7L,
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
            unprotectedHeader = CoseHeaderCbor(),
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
            deviceAuth =
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
                ),
            original = null,
        )

    private fun encodeOutOfOrderDocument(): ByteArray {
        val document = createTestDocument()
        return Cbor.encode(
            CborMap(
                mutableMapOf(
                    Document.ERRORS to
                        CborMap(
                            mutableMapOf(
                                CborString("org.iso.18013.5.1") to
                                    CborMap(
                                        mutableMapOf(
                                            CborString("portrait") to CborInt(7),
                                        ),
                                    ),
                            ),
                        ),
                    Document.DEVICE_SIGNED to decodeItem(deviceSignedCodec.encode(document.deviceSigned!!).getOrThrow()),
                    Document.ISSUER_SIGNED to decodeItem(issuerSignedCodec.encode(document.issuerSigned).getOrThrow()),
                    Document.DOC_TYPE to CborString(document.docType.toString()),
                ),
            ),
        )
    }

    private fun createOutOfOrderIssuerSignedBytes(): ByteArray {
        val issuerSigned = createTestIssuerSigned()
        return Cbor.encode(
            CborMap(
                mutableMapOf(
                    IssuerSigned.ISSUER_AUTH to coseSign1AsCborArray(issuerSigned.issuerAuth),
                    IssuerSigned.NAME_SPACES to
                        CborMap(
                            mutableMapOf(
                                CborString("org.iso.18013.5.1") to
                                    CborArray(
                                        mutableListOf(
                                            createIssuerSignedItem(2u, "family_name", "Doe"),
                                            createIssuerSignedItem(1u, "given_name", "John"),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
        )
    }

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

    private fun assertDocumentEquals(
        expected: Document,
        actual: Document,
    ) {
        assertEquals(expected.docType, actual.docType)
        assertEquals(expected.errors, actual.errors)
        assertEquals(null, actual.issuerSigned.original)
        assertEquals(null, actual.deviceSigned?.original)

        val actualIssuerSigned = actual.issuerSigned
        assertEquals(expected.issuerSigned.issuerAuth.protectedHeader, actualIssuerSigned.issuerAuth.protectedHeader)
        assertEquals(expected.issuerSigned.issuerAuth.unprotectedHeader, actualIssuerSigned.issuerAuth.unprotectedHeader)
        assertEquals(expected.issuerSigned.issuerAuth.payload, actualIssuerSigned.issuerAuth.payload)
        assertEquals(expected.issuerSigned.issuerAuth.signature, actualIssuerSigned.issuerAuth.signature)
        assertEquals(expected.issuerSigned.nameSpaces?.keys, actualIssuerSigned.nameSpaces?.keys)
        val actualIssuerItems = assertNotNull(actualIssuerSigned.nameSpaces).getValue(NameSpace("org.iso.18013.5.1"))
        val expectedIssuerItems = assertNotNull(expected.issuerSigned.nameSpaces).getValue(NameSpace("org.iso.18013.5.1"))
        expectedIssuerItems.indices.forEach { index ->
            val expectedItem = expectedIssuerItems[index].data()
            val actualItem = actualIssuerItems[index].data()
            assertEquals(expectedItem.digestID, actualItem.digestID)
            assertEquals(expectedItem.elementIdentifier, actualItem.elementIdentifier)
            assertEquals(expectedItem.elementValue, actualItem.elementValue)
            assertContentEquals(expectedItem.random.value, actualItem.random.value)
        }

        val actualDeviceSigned = assertNotNull(actual.deviceSigned)
        val expectedDeviceSigned = assertNotNull(expected.deviceSigned)
        assertEquals(expectedDeviceSigned.nameSpaces, actualDeviceSigned.nameSpaces)
        assertEquals(expectedDeviceSigned.deviceAuth.deviceMac, actualDeviceSigned.deviceAuth.deviceMac)
        assertEquals(expectedDeviceSigned.deviceAuth.deviceSignature?.protectedHeader, actualDeviceSigned.deviceAuth.deviceSignature?.protectedHeader)
        assertEquals(expectedDeviceSigned.deviceAuth.deviceSignature?.unprotectedHeader, actualDeviceSigned.deviceAuth.deviceSignature?.unprotectedHeader)
        assertEquals(expectedDeviceSigned.deviceAuth.deviceSignature?.payload, actualDeviceSigned.deviceAuth.deviceSignature?.payload)
        assertEquals(expectedDeviceSigned.deviceAuth.deviceSignature?.signature, actualDeviceSigned.deviceAuth.deviceSignature?.signature)
    }
}
