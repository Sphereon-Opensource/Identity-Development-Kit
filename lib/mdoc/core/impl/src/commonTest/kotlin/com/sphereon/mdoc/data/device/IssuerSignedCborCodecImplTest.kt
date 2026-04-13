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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssuerSignedCborCodecImplTest {
    private val codec = IssuerSignedCborCodecImpl()
    private val mobileSecurityObjectCodec = MobileSecurityObjectCborCodecImpl()

    @Test
    fun issuerSigned_round_trips_and_preserves_original_bytes() {
        val original = encodeOutOfOrderIssuerSigned()

        val decoded = codec.decode(original).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertIssuerSignedEquals(createTestIssuerSigned(), decoded.value.copy(original = null))
        assertContentEquals(original, decoded.originalBytes)
        assertContentEquals(original, decoded.value.original)
        assertContentEquals(original, reEncoded)
    }

    @Test
    fun issuerSigned_codec_rejects_invalid_cbor_item() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(Cbor.encode(CborString("invalid"))).getOrThrow()
        }
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

    private fun encodeOutOfOrderIssuerSigned(): ByteArray {
        val issuerSigned = createTestIssuerSigned()
        return Cbor.encode(
            CborMap(
                mutableMapOf(
                    IssuerSigned.ISSUER_AUTH to coseSign1AsCborArray(issuerSigned.issuerAuth),
                    IssuerSigned.NAME_SPACES to
                        CborMap(
                            mutableMapOf(
                                StringLabel("org.iso.18013.5.1") to
                                    CborArray(
                                        issuerSigned.nameSpaces!!.getValue(NameSpace("org.iso.18013.5.1")).toMutableList() as MutableList<CborEncodedItem<CborItem<*>>>,
                                    ),
                            ),
                        ),
                ),
            ),
        )
    }

    private fun assertIssuerSignedEquals(
        expected: IssuerSigned,
        actual: IssuerSigned,
    ) {
        assertEquals(expected.issuerAuth.protectedHeader, actual.issuerAuth.protectedHeader)
        assertEquals(expected.issuerAuth.unprotectedHeader, actual.issuerAuth.unprotectedHeader)
        assertEquals(expected.issuerAuth.payload, actual.issuerAuth.payload)
        assertEquals(expected.issuerAuth.signature, actual.issuerAuth.signature)
        assertEquals(expected.nameSpaces?.keys, actual.nameSpaces?.keys)

        expected.nameSpaces?.forEach { (nameSpace, expectedItems) ->
            val actualItems = actual.nameSpaces!!.getValue(nameSpace)
            assertEquals(expectedItems.size, actualItems.size)
            expectedItems.indices.forEach { index ->
                val expectedItem = expectedItems[index].data()
                val actualItem = actualItems[index].data()

                assertEquals(expectedItem.digestID, actualItem.digestID)
                assertEquals(expectedItem.elementIdentifier, actualItem.elementIdentifier)
                assertEquals(expectedItem.elementValue, actualItem.elementValue)
                assertContentEquals(expectedItem.random.value, actualItem.random.value)
                assertContentEquals(expectedItems[index].value.value, actualItems[index].value.value)
            }
        }
    }
}
