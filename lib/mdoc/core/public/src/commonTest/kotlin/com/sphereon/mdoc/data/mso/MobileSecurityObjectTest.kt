/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.TDate
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for MobileSecurityObject class.
 */
class MobileSecurityObjectTest {

    private fun createTestCoseKey(): CoseKey {
        return CoseKeyJson.Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
    }

    private fun createTestDeviceKeyInfo(): DeviceKeyInfoCbor {
        return DeviceKeyInfoCbor(
            deviceKey = createTestCoseKey(),
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )
    }

    private fun createTestValidityInfo(): ValidityInfo {
        // Use TDate with ISO 8601 format
        val now = TDate("2025-01-20T12:00:00Z")
        return ValidityInfo(
            signed = now,
            validFrom = now,
            validUntil = now,
            expectedUpdate = null
        )
    }

    @Test
    fun testMobileSecurityObjectCreation() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        assertEquals("1.0", mso.version.toString())
        assertEquals("SHA-256", mso.digestAlgorithm.toString())
        assertEquals("org.iso.18013.5.1.mDL", mso.docType.toString())
        assertNotNull(mso.deviceKeyInfo)
        assertNotNull(mso.validityInfo)
    }

    @Test
    fun testMobileSecurityObjectWithValueDigests() {
        val digest = byteArrayOf(0x01, 0x02, 0x03)
        val valueDigests = mapOf(
            NameSpace("org.iso.18013.5.1") to mapOf(
                DigestID(0u) to digest,
                DigestID(1u) to digest
            )
        )

        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = valueDigests,
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        assertEquals(1, mso.valueDigests.size)
        val nsDigests = mso.valueDigests[NameSpace("org.iso.18013.5.1")]
        assertNotNull(nsDigests)
        assertEquals(2, nsDigests.size)
    }

    @Test
    fun testMobileSecurityObjectGetKeyInfo() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        val keyInfo = mso.getKeyInfo()
        assertNotNull(keyInfo)
    }

    @Test
    fun testMobileSecurityObjectCborBuilder() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        val builder = mso.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testMobileSecurityObjectEncodeDecode() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        val encoded = mso.encodeCbor()
        val decoded = MobileSecurityObject.decodeCbor(encoded)

        assertEquals(mso.version.toString(), decoded.version.toString())
        assertEquals(mso.digestAlgorithm.toString(), decoded.digestAlgorithm.toString())
        assertEquals(mso.docType.toString(), decoded.docType.toString())
    }

    @Test
    fun testMobileSecurityObjectCompanionLabels() {
        assertEquals("version", MobileSecurityObject.VERSION.value)
        assertEquals("digestAlgorithm", MobileSecurityObject.DIGEST_ALGORITHM.value)
        assertEquals("valueDigests", MobileSecurityObject.VALUE_DIGESTS.value)
        assertEquals("deviceKeyInfo", MobileSecurityObject.DEVICE_KEY_INFO.value)
        assertEquals("docType", MobileSecurityObject.DOC_TYPE.value)
        assertEquals("validityInfo", MobileSecurityObject.VALIDITY_INFO.value)
    }

    @Test
    fun testMobileSecurityObjectWithOriginal() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        val encoded = mso.encodeCbor()
        val decoded = MobileSecurityObject.decodeCbor(encoded)

        // After decoding, original should be set
        assertNotNull(decoded.original)
        assertTrue(encoded.contentEquals(decoded.original))
    }

    @Test
    fun testMobileSecurityObjectWithMultipleNameSpaces() {
        val digest = byteArrayOf(0x01, 0x02, 0x03)
        val valueDigests = mapOf(
            NameSpace("org.iso.18013.5.1") to mapOf(
                DigestID(0u) to digest
            ),
            NameSpace("org.iso.18013.5.1.aamva") to mapOf(
                DigestID(0u) to digest
            )
        )

        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-384"),
            valueDigests = valueDigests,
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        assertEquals(2, mso.valueDigests.size)
        assertEquals("SHA-384", mso.digestAlgorithm.toString())
    }

    // Equality tests for branch coverage

    @Test
    fun testMobileSecurityObjectEqualitySameInstance() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertEquals(mso, mso)
    }

    @Test
    fun testMobileSecurityObjectEqualityNull() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertFalse(mso.equals(null))
    }

    @Test
    fun testMobileSecurityObjectEqualityDifferentType() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertFalse(mso.equals("not an MSO"))
    }

    @Test
    fun testMobileSecurityObjectEqualitySameValues() {
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertEquals(mso1, mso2)
    }

    @Test
    fun testMobileSecurityObjectInequalityDifferentVersion() {
        // Can't test different version since MsoVersion requires "1.0"
        // But we can test different digestAlgorithm
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-384"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertNotEquals(mso1, mso2)
    }

    @Test
    fun testMobileSecurityObjectInequalityDifferentValueDigests() {
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = mapOf(NameSpace("test") to mapOf(DigestID(0u) to byteArrayOf(1))),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertNotEquals(mso1, mso2)
    }

    @Test
    fun testMobileSecurityObjectInequalityDifferentDocType() {
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.example.test"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertNotEquals(mso1, mso2)
    }

    @Test
    fun testMobileSecurityObjectInequalityDifferentValidityInfo() {
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val differentValidityInfo = ValidityInfo(
            signed = TDate("2023-01-01T00:00:00Z"),
            validFrom = TDate("2023-01-01T00:00:00Z"),
            validUntil = TDate("2024-01-01T00:00:00Z"),
            expectedUpdate = null
        )
        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = differentValidityInfo,
            original = null
        )
        assertNotEquals(mso1, mso2)
    }

    @Test
    fun testMobileSecurityObjectHashCode() {
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        assertEquals(mso1.hashCode(), mso2.hashCode())
    }

    @Test
    fun testMobileSecurityObjectToString() {
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )
        val str = mso.toString()
        assertTrue(str.contains("MobileSecurityObject"))
        assertTrue(str.contains("version"))
        assertTrue(str.contains("digestAlgorithm"))
        assertTrue(str.contains("docType"))
    }

    // decodeCbor branch tests

    @Test
    fun testMobileSecurityObjectDecodeCborFromEncodedItem() {
        // Create MSO and encode it
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        // Wrap in CborEncodedItem (Tag 24)
        val encodedItem = CborEncodedItem.fromData(mso)
        val tag24Bytes = encodedItem.encodeCbor()

        // Decode from Tag 24 wrapped bytes
        val decoded = MobileSecurityObject.decodeCbor(tag24Bytes)
        assertEquals(mso.version.toString(), decoded.version.toString())
        assertEquals(mso.digestAlgorithm.toString(), decoded.digestAlgorithm.toString())
        assertEquals(mso.docType.toString(), decoded.docType.toString())
    }

    @Test
    fun testMobileSecurityObjectDecodeCborFromDirectMap() {
        // Create MSO and encode it directly (not Tag 24 wrapped)
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        // Encode directly as CborMap
        val directBytes = mso.encodeCbor()

        // Decode from direct bytes
        val decoded = MobileSecurityObject.decodeCbor(directBytes)
        assertEquals(mso.version.toString(), decoded.version.toString())
        assertEquals(mso.digestAlgorithm.toString(), decoded.digestAlgorithm.toString())
        assertEquals(mso.docType.toString(), decoded.docType.toString())
    }

    @Test
    fun testMobileSecurityObjectDecodeCborInvalidType() {
        // Try to decode from invalid CBOR type (e.g., a simple string)
        val invalidBytes = com.sphereon.cbor.CborString("invalid").encodeCbor()

        assertFailsWith<IllegalArgumentException> {
            MobileSecurityObject.decodeCbor(invalidBytes)
        }
    }

    // Additional equality tests for different deviceKeyInfo

    @Test
    fun testMobileSecurityObjectInequalityDifferentDeviceKeyInfo() {
        val mso1 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        // Create a different device key
        val differentKey = CoseKeyJson.Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
            .withY("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
            .build()
            .toCbor()

        val differentDeviceKeyInfo = DeviceKeyInfoCbor(
            deviceKey = differentKey,
            keyAuthorizations = null,
            keyInfo = null,
            original = null
        )

        val mso2 = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = differentDeviceKeyInfo,
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        assertNotEquals(mso1, mso2)
    }

    // decodeCoseSign1 tests

    @Test
    fun testMobileSecurityObjectDecodeCoseSign1WithValidPayload() {
        // Create MSO and encode it
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = null
        )

        // Wrap MSO in CborEncodedItem (Tag 24) as expected
        val encodedMso = CborEncodedItem.fromData(mso).encodeCbor()

        // Create CoseSign1 with the MSO payload
        val coseSign1 = CoseSign1<MobileSecurityObject>(
            protectedHeader = CoseHeaderCbor(
                alg = CoseAlgorithm.ES256
            ),
            unprotectedHeader = null,
            payload = CborByteString(encodedMso),
            signature = CborByteString(byteArrayOf(0x01, 0x02, 0x03))
        )

        // Decode MSO from CoseSign1
        val decoded = MobileSecurityObject.decodeCoseSign1(coseSign1)
        assertEquals(mso.version.toString(), decoded.version.toString())
        assertEquals(mso.digestAlgorithm.toString(), decoded.digestAlgorithm.toString())
        assertEquals(mso.docType.toString(), decoded.docType.toString())
    }

    @Test
    fun testMobileSecurityObjectDecodeCoseSign1WithNullPayload() {
        // Create CoseSign1 with null payload
        val coseSign1 = CoseSign1<MobileSecurityObject>(
            protectedHeader = CoseHeaderCbor(
                alg = CoseAlgorithm.ES256
            ),
            unprotectedHeader = null,
            payload = null,
            signature = CborByteString(byteArrayOf(0x01, 0x02, 0x03))
        )

        // Should throw IllegalArgumentException because payload is null
        assertFailsWith<IllegalArgumentException> {
            MobileSecurityObject.decodeCoseSign1(coseSign1)
        }
    }
}
