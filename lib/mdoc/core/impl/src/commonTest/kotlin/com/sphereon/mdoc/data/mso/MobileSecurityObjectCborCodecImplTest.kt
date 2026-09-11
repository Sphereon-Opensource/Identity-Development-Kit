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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.TDate
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.testutil.coseKeyAsCborMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MobileSecurityObjectCborCodecImplTest {
    private val codec = MobileSecurityObjectCborCodecImpl()

    @Test
    fun mobileSecurityObject_round_trips_and_preserves_original_bytes() {
        val original = encodeOutOfOrderMobileSecurityObject()

        val decoded = codec.decode(original).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertMobileSecurityObjectEquals(createMobileSecurityObject(), decoded.value.copy(original = null))
        assertContentEquals(original, decoded.originalBytes)
        assertContentEquals(original, decoded.value.original)
        assertContentEquals(original, reEncoded)
    }

    @Test
    fun mobileSecurityObject_decode_accepts_tag24_wrapper_and_preserves_inner_bytes() {
        val mso = createMobileSecurityObject()
        val original = codec.encode(mso).getOrThrow()
        val tagged = codec.encodeTag24(mso).getOrThrow()

        val decoded = codec.decode(tagged).getOrThrow()
        val reEncoded = codec.encode(decoded.value).getOrThrow()

        assertContentEquals(original, decoded.originalBytes)
        assertContentEquals(original, decoded.value.original)
        assertContentEquals(original, reEncoded)
    }

    @Test
    fun mobileSecurityObject_codec_rejects_invalid_cbor_item() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode(Cbor.encode(CborString("invalid"))).getOrThrow()
        }
    }

    @Test
    fun mobileSecurityObject_round_trips_second_edition_status_references() {
        val expectedStatus =
            Status(
                statusList = StatusListInfo(
                    idx = 1340u,
                    uri = "https://example.test/status-list",
                    certificate = byteArrayOf(0x05, 0x06),
                ),
            )
        val expected = createMobileSecurityObject().copy(status = expectedStatus)

        val decoded = codec.decode(codec.encode(expected).getOrThrow()).getOrThrow().value

        assertEquals(expectedStatus, decoded.status)
        assertEquals(expectedStatus.identifierList?.uri, decoded.status?.identifierList?.uri)
        assertContentEquals(expectedStatus.identifierList?.id, decoded.status?.identifierList?.id)
        assertContentEquals(expectedStatus.identifierList?.certificate, decoded.status?.identifierList?.certificate)
        assertEquals(expectedStatus.statusList?.idx, decoded.status?.statusList?.idx)
        assertEquals(expectedStatus.statusList?.uri, decoded.status?.statusList?.uri)
        assertContentEquals(expectedStatus.statusList?.certificate, decoded.status?.statusList?.certificate)
    }

    @Test
    fun mobileSecurityObject_rejects_status_with_both_revocation_mechanisms() {
        assertFailsWith<IllegalArgumentException> {
            Status(
                identifierList = IdentifierListInfo(byteArrayOf(0x01), "https://example.test/identifier-list"),
                statusList = StatusListInfo(1u, "https://example.test/status-list"),
            )
        }
    }

    @Test
    fun mobileSecurityObject_rejects_digest_ids_outside_the_uint_range() {
        val mso = createMobileSecurityObject()
        val invalidValueDigests =
            CborMap(
                mutableMapOf(
                    StringLabel("org.iso.18013.5.1") to
                        CborMap(
                            mutableMapOf(
                                com.sphereon.cbor.NumberLabel(UInt.MAX_VALUE.toLong() + 1) to
                                    CborByteString(byteArrayOf(0x01)),
                            ),
                        ),
                ),
            )
        val encoded =
            Cbor.encode(
                CborMap(
                    mutableMapOf(
                        MobileSecurityObject.VALIDITY_INFO to encodeValidityInfo(mso.validityInfo),
                        MobileSecurityObject.DOC_TYPE to CborString(mso.docType.toString()),
                        MobileSecurityObject.DEVICE_KEY_INFO to encodeDeviceKeyInfo(mso.deviceKeyInfo),
                        MobileSecurityObject.VALUE_DIGESTS to invalidValueDigests,
                        MobileSecurityObject.DIGEST_ALGORITHM to CborString(mso.digestAlgorithm.toString()),
                        MobileSecurityObject.VERSION to CborString(mso.version.toString()),
                    ),
                ),
            )

        assertFailsWith<IllegalArgumentException> {
            codec.decode(encoded).getOrThrow()
        }
    }

    private fun createMobileSecurityObject(): MobileSecurityObject =
        MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests =
                mapOf(
                    NameSpace("org.iso.18013.5.1") to
                        mapOf(
                            DigestID(0u) to byteArrayOf(0x01, 0x02, 0x03),
                            DigestID(1u) to byteArrayOf(0x04, 0x05, 0x06),
                        ),
                ),
            deviceKeyInfo = createDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createValidityInfo(),
            original = null,
        )

    private fun createDeviceKeyInfo(): DeviceKeyInfo =
        DeviceKeyInfo(
            deviceKey =
                CoseKeyJson
                    .Builder()
                    .withKty(CoseKeyTypeEnum.EC2)
                    .withCrv(CoseCurve.P_256)
                    .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                    .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                    .build()
                    .toCbor(),
            keyAuthorizations = null,
            keyInfo = null,
            original = null,
        )

    private fun createValidityInfo(): ValidityInfo {
        val now = TDate("2025-01-20T12:00:00Z")
        return ValidityInfo(
            signed = now,
            validFrom = now,
            validUntil = now,
            expectedUpdate = null,
        )
    }

    private fun encodeOutOfOrderMobileSecurityObject(): ByteArray {
        val mso = createMobileSecurityObject()
        val valueDigests =
            CborMap(
                mutableMapOf(
                    StringLabel("org.iso.18013.5.1") to
                        CborMap(
                            mutableMapOf(
                                com.sphereon.cbor.NumberLabel(1) to CborByteString(byteArrayOf(0x04, 0x05, 0x06)),
                                com.sphereon.cbor.NumberLabel(0) to CborByteString(byteArrayOf(0x01, 0x02, 0x03)),
                            ),
                        ),
                ),
            )

        return Cbor.encode(
            CborMap(
                mutableMapOf(
                    MobileSecurityObject.VALIDITY_INFO to encodeValidityInfo(mso.validityInfo),
                    MobileSecurityObject.DOC_TYPE to CborString(mso.docType.toString()),
                    MobileSecurityObject.DEVICE_KEY_INFO to encodeDeviceKeyInfo(mso.deviceKeyInfo),
                    MobileSecurityObject.VALUE_DIGESTS to valueDigests,
                    MobileSecurityObject.DIGEST_ALGORITHM to CborString(mso.digestAlgorithm.toString()),
                    MobileSecurityObject.VERSION to CborString(mso.version.toString()),
                ),
            ),
        )
    }

    private fun assertMobileSecurityObjectEquals(
        expected: MobileSecurityObject,
        actual: MobileSecurityObject,
    ) {
        assertEquals(expected.version, actual.version)
        assertEquals(expected.digestAlgorithm, actual.digestAlgorithm)
        assertEquals(expected.deviceKeyInfo, actual.deviceKeyInfo)
        assertEquals(expected.docType, actual.docType)
        assertEquals(expected.validityInfo, actual.validityInfo)
        assertEquals(expected.valueDigests.keys, actual.valueDigests.keys)

        expected.valueDigests.forEach { (nameSpace, expectedDigests) ->
            val actualDigests = actual.valueDigests.getValue(nameSpace)
            assertEquals(expectedDigests.keys, actualDigests.keys)
            expectedDigests.forEach { (digestId, expectedDigest) ->
                assertContentEquals(expectedDigest, actualDigests.getValue(digestId))
            }
        }
    }

    private fun encodeDeviceKeyInfo(value: DeviceKeyInfo): CborMap<StringLabel, CborItem<*>> {
        val entries =
            mutableMapOf<StringLabel, CborItem<*>>(
                DeviceKeyInfo.DEVICE_KEY to coseKeyAsCborMap(value.deviceKey),
            )
        value.keyAuthorizations?.let { entries[DeviceKeyInfo.KEY_AUTHORIZATIONS] = encodeKeyAuthorizations(it) }
        value.keyInfo?.let { entries[DeviceKeyInfo.KEY_INFO] = it }
        return CborMap(entries)
    }

    private fun encodeKeyAuthorizations(value: KeyAuthorizations): CborMap<StringLabel, CborItem<*>> {
        val entries = mutableMapOf<StringLabel, CborItem<*>>()
        value.nameSpaces?.let { entries[KeyAuthorizations.NAME_SPACES] = it }
        value.dataElements?.let { entries[KeyAuthorizations.DATA_ELEMENTS] = it }
        return CborMap(entries)
    }

    private fun encodeValidityInfo(value: ValidityInfo): CborMap<StringLabel, CborItem<*>> {
        val entries =
            mutableMapOf<StringLabel, CborItem<*>>(
                ValidityInfo.SIGNED to value.signed.toCborItem(),
                ValidityInfo.VALID_FROM to value.validFrom.toCborItem(),
                ValidityInfo.VALID_UNTIL to value.validUntil.toCborItem(),
            )
        value.expectedUpdate?.let { entries[ValidityInfo.EXPECTED_UPDATE] = it.toCborItem() }
        return CborMap(entries)
    }
}
