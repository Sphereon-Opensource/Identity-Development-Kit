/*
 * (c) 2026 Sphereon International B.V.
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

import com.sphereon.cbor.TDate
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MobileSecurityObjectTest {
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

    private fun createTestMso(
        digestAlgorithm: String = "SHA-256",
        valueDigests: Map<NameSpace, Map<DigestID, ByteArray>> = emptyMap(),
        original: ByteArray? = null,
    ): MobileSecurityObject =
        MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm(digestAlgorithm),
            valueDigests = valueDigests,
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = DocType("org.iso.18013.5.1.mDL"),
            validityInfo = createTestValidityInfo(),
            original = original,
        )

    @Test
    fun testMobileSecurityObjectCreation() {
        val mso = createTestMso()

        assertEquals("1.0", mso.version.toString())
        assertEquals("SHA-256", mso.digestAlgorithm.toString())
        assertEquals("org.iso.18013.5.1.mDL", mso.docType.toString())
        assertNotNull(mso.deviceKeyInfo)
        assertNotNull(mso.validityInfo)
    }

    @Test
    fun testMobileSecurityObjectWithValueDigests() {
        val digest = byteArrayOf(0x01, 0x02, 0x03)
        val mso =
            createTestMso(
                valueDigests =
                    mapOf(
                        NameSpace("org.iso.18013.5.1") to
                            mapOf(
                                DigestID(0u) to digest,
                                DigestID(1u) to digest,
                            ),
                    ),
            )

        assertEquals(1, mso.valueDigests.size)
        assertEquals(2, mso.valueDigests[NameSpace("org.iso.18013.5.1")]?.size)
    }

    @Test
    fun testMobileSecurityObjectGetKeyInfo() {
        val keyInfo = createTestMso().getKeyInfo()

        assertNotNull(keyInfo)
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
    fun testMobileSecurityObjectRetainsOriginalBytes() {
        val original = byteArrayOf(0x01, 0x02, 0x03)
        val mso = createTestMso(original = original)

        assertTrue(original.contentEquals(mso.original))
    }

    @Test
    fun testMobileSecurityObjectEqualityIgnoresOriginal() {
        val left = createTestMso(original = byteArrayOf(0x01))
        val right = createTestMso(original = byteArrayOf(0x02))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun testMobileSecurityObjectInequalityDifferentDigestAlgorithm() {
        assertNotEquals(createTestMso(), createTestMso(digestAlgorithm = "SHA-384"))
    }

    @Test
    fun testMobileSecurityObjectInequalityDifferentDeviceKeyInfo() {
        val differentDeviceKeyInfo =
            DeviceKeyInfo(
                deviceKey =
                    CoseKeyJson
                        .Builder()
                        .withKty(CoseKeyTypeEnum.EC2)
                        .withCrv(CoseCurve.P_256)
                        .withX("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
                        .withY("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
                        .build()
                        .toCbor(),
                keyAuthorizations = null,
                keyInfo = null,
                original = null,
            )
        val base = createTestMso()
        val different = base.copy(deviceKeyInfo = differentDeviceKeyInfo)

        assertNotEquals(base, different)
    }

    @Test
    fun testMobileSecurityObjectToString() {
        val text = createTestMso().toString()

        assertTrue(text.contains("MobileSecurityObject"))
        assertTrue(text.contains("digestAlgorithm"))
        assertTrue(text.contains("docType"))
    }

    @Test
    fun testMobileSecurityObjectEqualsDifferentType() {
        assertFalse(createTestMso().equals("not an MSO"))
    }
}
