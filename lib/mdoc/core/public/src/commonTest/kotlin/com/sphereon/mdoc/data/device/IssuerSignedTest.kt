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

package com.sphereon.mdoc.data.device

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
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.mdoc.data.mso.DeviceKeyInfoCbor
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MsoVersion
import com.sphereon.mdoc.data.mso.ValidityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for IssuerSigned, IssuerSignedItem, and related classes.
 */
class IssuerSignedTest {

    private val mdlNamespace = NameSpace("org.iso.18013.5.1")
    private val mdlDocType = DocType("org.iso.18013.5.1.mDL")

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
        val now = TDate("2025-01-20T12:00:00Z")
        return ValidityInfo(
            signed = now,
            validFrom = now,
            validUntil = now,
            expectedUpdate = null
        )
    }

    private fun createTestMso(
        valueDigests: Map<NameSpace, Map<DigestID, ByteArray>> = emptyMap()
    ): MobileSecurityObject {
        return MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = valueDigests,
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = mdlDocType,
            validityInfo = createTestValidityInfo(),
            original = null
        )
    }

    private fun createTestIssuerAuth(mso: MobileSecurityObject = createTestMso()): CoseSign1<MobileSecurityObject> {
        // Wrap MSO in CborEncodedItem (Tag 24) as expected by the decoder
        val encodedMso = CborEncodedItem.fromData(mso).encodeCbor()

        return CoseSign1(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = null,
            payload = CborByteString(encodedMso),
            signature = CborByteString(ByteArray(64) { it.toByte() })
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createIssuerSignedItem(digestId: UInt, elementId: String, value: Any): CborEncodedItem<IssuerSignedItem<Any>> {
        val item = IssuerSignedItem(
            digestID = DigestID(digestId),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier(elementId),
            elementValue = value
        )
        return CborEncodedItem.fromData(item as IssuerSignedItem<Any>)
    }

    private fun createNameSpaces(): IssuerSignedNameSpaces {
        val items = arrayOf(
            createIssuerSignedItem(1u, "given_name", "John"),
            createIssuerSignedItem(2u, "family_name", "Doe")
        )
        return mapOf(mdlNamespace to items)
    }

    // Companion object tests

    @Test
    fun testCompanionLabels() {
        assertEquals("nameSpaces", IssuerSigned.NAME_SPACES.value)
        assertEquals("issuerAuth", IssuerSigned.ISSUER_AUTH.value)
    }

    // toValueDigests tests

    @Test
    fun testToValueDigestsWithNull() {
        val digests = IssuerSigned.toValueDigests(null)
        assertTrue(digests.isEmpty())
    }

    @Test
    fun testToValueDigestsWithNamespaces() {
        val nameSpaces = createNameSpaces()

        val digests = IssuerSigned.toValueDigests(nameSpaces)
        assertNotNull(digests)
        assertEquals(1, digests.size)
        assertTrue(digests.containsKey(mdlNamespace))
        assertEquals(2, digests[mdlNamespace]?.size)
    }

    @Test
    fun testToValueDigestsWithCustomAlgorithm() {
        val items = arrayOf(createIssuerSignedItem(1u, "given_name", "John"))
        val nameSpaces: IssuerSignedNameSpaces = mapOf(mdlNamespace to items)

        val digestsSha256 = IssuerSigned.toValueDigests(nameSpaces, DigestAlg.SHA256)
        val digestsSha384 = IssuerSigned.toValueDigests(nameSpaces, DigestAlg.SHA384)

        assertNotNull(digestsSha256)
        assertNotNull(digestsSha384)
        // Digest lengths should be different for different algorithms
        val sha256Digest = digestsSha256[mdlNamespace]?.values?.first()
        val sha384Digest = digestsSha384[mdlNamespace]?.values?.first()
        assertNotNull(sha256Digest)
        assertNotNull(sha384Digest)
        assertEquals(32, sha256Digest.size)  // SHA-256 produces 32 bytes
        assertEquals(48, sha384Digest.size)  // SHA-384 produces 48 bytes
    }

    @Test
    fun testToValueDigestsWithMultipleNamespaces() {
        val euPidNamespace = NameSpace("eu.europa.ec.eudi.pid.1")
        val items1 = arrayOf(createIssuerSignedItem(1u, "given_name", "John"))
        val items2 = arrayOf(
            createIssuerSignedItem(1u, "family_name", "Doe"),
            createIssuerSignedItem(2u, "birth_date", "1990-01-01")
        )
        val nameSpaces: IssuerSignedNameSpaces = mapOf(
            mdlNamespace to items1,
            euPidNamespace to items2
        )

        val digests = IssuerSigned.toValueDigests(nameSpaces)
        assertNotNull(digests)
        assertEquals(2, digests.size)
        assertTrue(digests.containsKey(mdlNamespace))
        assertTrue(digests.containsKey(euPidNamespace))
        assertEquals(1, digests[mdlNamespace]?.size)
        assertEquals(2, digests[euPidNamespace]?.size)
    }

    @Test
    fun testToValueDigestsEmptyNamespaces() {
        val nameSpaces: IssuerSignedNameSpaces = emptyMap()
        val digests = IssuerSigned.toValueDigests(nameSpaces)
        assertTrue(digests.isEmpty())
    }

    // IssuerSignedItem equality tests for different random values

    @Test
    fun testIssuerSignedItemInequalityDifferentRandom() {
        val random1 = RandomValue(ByteArray(24) { 0x42.toByte() })
        val random2 = RandomValue(ByteArray(24) { 0x43.toByte() })

        val item1 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random1,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random2,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertNotEquals(item1, item2)
    }

    // CborEncodedItem digest extension tests

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testCborEncodedItemDigestWithSha256() {
        val item: IssuerSignedItem<Any> = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John" as Any
        )

        val encodedItem: CborEncodedItem<IssuerSignedItem<Any>> = CborEncodedItem.fromData(item)
        val digest = encodedItem.digest(DigestAlg.SHA256)

        assertNotNull(digest)
        assertEquals(32, digest.size)  // SHA-256 produces 32 bytes
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testCborEncodedItemDigestWithSha384() {
        val item: IssuerSignedItem<Any> = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John" as Any
        )

        val encodedItem: CborEncodedItem<IssuerSignedItem<Any>> = CborEncodedItem.fromData(item)
        val digest = encodedItem.digest(DigestAlg.SHA384)

        assertNotNull(digest)
        assertEquals(48, digest.size)  // SHA-384 produces 48 bytes
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testCborEncodedItemDigestConsistency() {
        val item: IssuerSignedItem<Any> = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John" as Any
        )

        val encodedItem: CborEncodedItem<IssuerSignedItem<Any>> = CborEncodedItem.fromData(item)
        val digest1 = encodedItem.digest()
        val digest2 = encodedItem.digest()

        assertTrue(digest1.contentEquals(digest2))
    }

    // IssuerSigned instance tests

    @Test
    fun testIssuerSignedCreationWithNamespaces() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertNotNull(issuerSigned)
        assertEquals(nameSpaces, issuerSigned.nameSpaces)
        assertNotNull(issuerSigned.MSO)
    }

    @Test
    fun testIssuerSignedCreationWithNullNamespaces() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertNotNull(issuerSigned)
        assertNull(issuerSigned.nameSpaces)
    }

    @Test
    fun testIssuerSignedFromCollection() {
        val items = arrayOf(createIssuerSignedItem(1u, "given_name", "John"))
        val nameSpace: IssuerSignedNameSpace = mdlNamespace to items
        val collection: Collection<IssuerSignedNameSpace> = listOf(nameSpace)
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = collection,
            issuerAuth = issuerAuth,
            original = null
        )

        assertNotNull(issuerSigned)
        assertEquals(1, issuerSigned.nameSpaces?.size)
    }

    @Test
    fun testIssuerSignedFromNullCollection() {
        val issuerAuth = createTestIssuerAuth()
        val nullCollection: Collection<IssuerSignedNameSpace>? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullCollection,
            issuerAuth = issuerAuth,
            original = null
        )

        assertNotNull(issuerSigned)
        assertNull(issuerSigned.nameSpaces)
    }

    @Test
    fun testIssuerSignedGetNameSpaces() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val keys = issuerSigned.getNameSpaces()
        assertNotNull(keys)
        assertTrue(keys.contains(mdlNamespace))
    }

    @Test
    fun testIssuerSignedGetNameSpacesWithNull() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val keys = issuerSigned.getNameSpaces()
        assertNull(keys)
    }

    @Test
    fun testIssuerSignedGetAllIssuerSignedItems() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val allItems = issuerSigned.getAllIssuerSignedItems()
        assertNotNull(allItems)
        assertEquals(1, allItems.size)
        assertEquals(2, allItems[mdlNamespace]?.size)
    }

    @Test
    fun testIssuerSignedGetAllIssuerSignedItemsWithNull() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val allItems = issuerSigned.getAllIssuerSignedItems()
        assertNull(allItems)
    }

    @Test
    fun testIssuerSignedGetNamespace() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val ns = issuerSigned.getNamespace("org.iso.18013.5.1")
        assertNotNull(ns)
        assertEquals(2, ns.size)
    }

    @Test
    fun testIssuerSignedGetNamespaceWithNull() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val ns = issuerSigned.getNamespace("org.iso.18013.5.1")
        assertNull(ns)
    }

    @Test
    fun testIssuerSignedGetNamespaceNotFound() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val ns = issuerSigned.getNamespace("nonexistent.namespace")
        assertNull(ns)
    }

    @Test
    fun testIssuerSignedGetIssuerSignedItem() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val item = issuerSigned.getIssuerSignedItem(mdlNamespace, DataElementIdentifier("given_name"))
        assertNotNull(item)
        assertEquals("John", item.elementValue)
    }

    @Test
    fun testIssuerSignedGetIssuerSignedItemWithNullNamespaces() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val item = issuerSigned.getIssuerSignedItem(mdlNamespace, DataElementIdentifier("given_name"))
        assertNull(item)
    }

    @Test
    fun testIssuerSignedGetIssuerSignedItemNotFound() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val item = issuerSigned.getIssuerSignedItem(mdlNamespace, DataElementIdentifier("nonexistent"))
        assertNull(item)
    }

    @Test
    fun testIssuerSignedGetIssuerSignedItems() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val items = issuerSigned.getIssuerSignedItems("org.iso.18013.5.1")
        assertNotNull(items)
        assertEquals(2, items.size)
    }

    @Test
    fun testIssuerSignedGetIssuerSignedItemsWithNull() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val items = issuerSigned.getIssuerSignedItems("org.iso.18013.5.1")
        assertNull(items)
    }

    @Test
    fun testIssuerSignedEncodeCborWithOriginal() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()
        val originalBytes = byteArrayOf(0x01, 0x02, 0x03)

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = originalBytes
        )

        val encoded = issuerSigned.encodeCbor()
        assertTrue(originalBytes.contentEquals(encoded))
    }

    @Test
    fun testIssuerSignedEncodeCborWithoutOriginal() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val encoded = issuerSigned.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testIssuerSignedDeviceKeyInfo() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val deviceKeyInfo = issuerSigned.deviceKeyInfo
        assertNotNull(deviceKeyInfo)
        assertNotNull(deviceKeyInfo.deviceKey)
    }

    @Test
    fun testIssuerSignedCborBuilder() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val builder = issuerSigned.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testIssuerSignedToDocument() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val document = issuerSigned.toDocument()
        assertNotNull(document)
        assertEquals(mdlDocType, document.docType)
        assertEquals(issuerSigned, document.issuerSigned)
        assertNull(document.deviceSigned)
    }

    @Test
    fun testIssuerSignedLimitDisclosuresWithNullNamespaces() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val limited = issuerSigned.limitDisclosures(docRequest)
        assertNull(limited.nameSpaces)
    }

    @Test
    fun testIssuerSignedLimitDisclosuresWithNamespaces() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val limited = issuerSigned.limitDisclosures(docRequest)
        assertNotNull(limited.nameSpaces)
        assertEquals(1, limited.nameSpaces?.get(mdlNamespace)?.size)
    }

    // Equality tests

    @Test
    fun testIssuerSignedEqualitySameInstance() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertEquals(issuerSigned, issuerSigned)
    }

    @Test
    fun testIssuerSignedEqualityNull() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertFalse(issuerSigned.equals(null))
    }

    @Test
    fun testIssuerSignedEqualityDifferentType() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertFalse(issuerSigned.equals("not an IssuerSigned"))
    }

    @Test
    fun testIssuerSignedInequalityDifferentNamespaces() {
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned1 = IssuerSigned(
            nameSpaces = createNameSpaces(),
            issuerAuth = issuerAuth,
            original = null
        )

        val differentItems = arrayOf(createIssuerSignedItem(3u, "other_field", "value"))
        val differentNameSpaces: IssuerSignedNameSpaces = mapOf(mdlNamespace to differentItems)

        val issuerSigned2 = IssuerSigned(
            nameSpaces = differentNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertNotEquals(issuerSigned1, issuerSigned2)
    }

    @Test
    fun testIssuerSignedHashCode() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        assertNotNull(issuerSigned.hashCode())
    }

    @Test
    fun testIssuerSignedHashCodeWithNullNamespaces() {
        val issuerAuth = createTestIssuerAuth()
        val nullNameSpaces: IssuerSignedNameSpaces? = null

        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        // hashCode should handle null nameSpaces
        assertNotNull(issuerSigned.hashCode())
    }

    @Test
    fun testIssuerSignedToString() {
        val nameSpaces = createNameSpaces()
        val issuerAuth = createTestIssuerAuth()

        val issuerSigned = IssuerSigned(
            nameSpaces = nameSpaces,
            issuerAuth = issuerAuth,
            original = null
        )

        val str = issuerSigned.toString()
        assertTrue(str.contains("IssuerSigned"))
        assertTrue(str.contains("nameSpaces"))
        assertTrue(str.contains("issuerAuth"))
    }

    // IssuerSignedItem tests

    @Test
    fun testIssuerSignedItemCreate() {
        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertNotNull(item)
        assertEquals(DigestID(1u), item.digestID)
        assertEquals(DataElementIdentifier("given_name"), item.elementIdentifier)
        assertEquals("John", item.elementValue)
        assertNotNull(item.random)
    }

    @Test
    fun testIssuerSignedItemCborBuilder() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        val builder = item.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testIssuerSignedItemCompanionLabels() {
        assertEquals("digestID", IssuerSignedItem.DIGEST_ID.value)
        assertEquals("random", IssuerSignedItem.RANDOM.value)
        assertEquals("elementIdentifier", IssuerSignedItem.ELEMENT_IDENTIFIER.value)
        assertEquals("elementValue", IssuerSignedItem.ELEMENT_VALUE.value)
    }

    @Test
    fun testIssuerSignedItemEqualitySameInstance() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertEquals(item, item)
    }

    @Test
    fun testIssuerSignedItemEqualityNull() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertFalse(item.equals(null))
    }

    @Test
    fun testIssuerSignedItemEqualityDifferentType() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertFalse(item.equals("not an item"))
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentDigestId() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem(
            digestID = DigestID(2u),
            random = random,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertNotEquals(item1, item2)
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentElementIdentifier() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random,
            elementIdentifier = DataElementIdentifier("family_name"),
            elementValue = "John"
        )

        assertNotEquals(item1, item2)
    }

    @Test
    fun testIssuerSignedItemInequalityDifferentElementValue() {
        val random = RandomValue(ByteArray(24) { 0x42.toByte() })
        val item1 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem(
            digestID = DigestID(1u),
            random = random,
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "Jane"
        )

        assertNotEquals(item1, item2)
    }

    @Test
    fun testIssuerSignedItemHashCode() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        assertNotNull(item.hashCode())
    }

    @Test
    fun testIssuerSignedItemToString() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        val str = item.toString()
        assertTrue(str.contains("IssuerSignedItem"))
        assertTrue(str.contains("digestID"))
        assertTrue(str.contains("random"))
        assertTrue(str.contains("elementIdentifier"))
        assertTrue(str.contains("elementValue"))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testIssuerSignedItemDecodeCbor() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John" as Any
        )

        val encoded = item.encodeCbor()
        val decoded = IssuerSignedItem.decodeCbor(encoded)

        assertNotNull(decoded)
        assertEquals(DigestID(1u), decoded.digestID)
        assertEquals(DataElementIdentifier("given_name"), decoded.elementIdentifier)
    }

    // RandomValue tests

    @Test
    fun testRandomValueCreation() {
        val randomValue = RandomValue()
        assertNotNull(randomValue.value)
        assertEquals(24, randomValue.value.size)
    }

    @Test
    fun testRandomValueWithSpecificBytes() {
        val bytes = ByteArray(24) { 0x42.toByte() }
        val randomValue = RandomValue(bytes)
        assertTrue(bytes.contentEquals(randomValue.value))
    }

    @Test
    fun testRandomValueToCborStructure() {
        val bytes = ByteArray(24) { 0x42.toByte() }
        val randomValue = RandomValue(bytes)
        val cbor = randomValue.toCborStructure()
        assertNotNull(cbor)
        assertTrue(bytes.contentEquals(cbor.value))
    }

    @Test
    fun testRandomValueToString() {
        val bytes = ByteArray(24) { 0x42.toByte() }
        val randomValue = RandomValue(bytes)
        val str = randomValue.toString()
        assertNotNull(str)
        assertTrue(str.isNotEmpty())
    }

    @Test
    fun testRandomValueFromCborStructure() {
        val bytes = ByteArray(24) { 0x42.toByte() }
        val cborByteString = CborByteString(bytes)
        val randomValue = RandomValue.fromCborStructure(cborByteString)
        assertTrue(bytes.contentEquals(randomValue.value))
    }

    // IssuerSignedItem CBOR round-trip tests

    @Test
    fun testIssuerSignedItemEncodeDecode() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        val encoded = item.encodeCbor()
        val decoded = IssuerSignedItem.decodeCbor(encoded)

        assertNotNull(decoded)
        assertEquals(item.digestID, decoded.digestID)
        assertEquals(item.elementIdentifier, decoded.elementIdentifier)
        assertEquals(item.elementValue, decoded.elementValue)
    }

    @Test
    fun testIssuerSignedItemFromCborStructure() {
        val item = IssuerSignedItem(
            digestID = DigestID(1u),
            random = RandomValue(ByteArray(24) { 0x42.toByte() }),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )

        val cborStructure = item.toCborStructure()
        val decoded = IssuerSignedItem.fromCborStructure(cborStructure)

        assertNotNull(decoded)
        assertEquals(item.digestID, decoded.digestID)
        assertEquals(item.elementIdentifier, decoded.elementIdentifier)
    }

    // MsoBuilder tests

    @Test
    fun testMsoBuilderWithDocType() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)

        assertEquals(mdlDocType, builder.docType)
    }

    @Test
    fun testMsoBuilderWithSigned() {
        val builder = IssuerSigned.MsoBuilder()
        val signedTime = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        builder.withSigned(signedTime)
        assertEquals(signedTime, builder.signed)
    }

    @Test
    fun testMsoBuilderWithValidFrom() {
        val builder = IssuerSigned.MsoBuilder()
        val validFromTime = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        builder.withValidFrom(validFromTime)
        assertEquals(validFromTime, builder.validFrom)
    }

    @Test
    fun testMsoBuilderWithValidUntil() {
        val builder = IssuerSigned.MsoBuilder()
        val validUntilTime = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        builder.withValidUntil(validUntilTime)
        assertEquals(validUntilTime, builder.validUntil)
    }

    @Test
    fun testMsoBuilderWithExpectedUpdate() {
        val builder = IssuerSigned.MsoBuilder()
        val expectedUpdateTime = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        builder.withExpectedUpdate(expectedUpdateTime)
        assertEquals(expectedUpdateTime, builder.expectedUpdate)
    }

    @Test
    fun testMsoBuilderWithExpectedUpdateNull() {
        val builder = IssuerSigned.MsoBuilder()

        builder.withExpectedUpdate(null)
        assertNull(builder.expectedUpdate)
    }

    @Test
    fun testMsoBuilderWithValidityInfo() {
        val builder = IssuerSigned.MsoBuilder()
        val signed = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validFrom = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val expectedUpdate = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        builder.withValidityInfo(
            signed = signed,
            validFrom = validFrom,
            validUntil = validUntil,
            expectedUpdate = expectedUpdate
        )

        assertEquals(signed, builder.signed)
        assertEquals(validFrom, builder.validFrom)
        assertEquals(validUntil, builder.validUntil)
        assertEquals(expectedUpdate, builder.expectedUpdate)
    }

    @Test
    fun testMsoBuilderWithValidityInfoNullExpectedUpdate() {
        val builder = IssuerSigned.MsoBuilder()
        val signed = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validFrom = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        builder.withValidityInfo(
            signed = signed,
            validFrom = validFrom,
            validUntil = validUntil,
            expectedUpdate = null
        )

        assertEquals(signed, builder.signed)
        assertEquals(validFrom, builder.validFrom)
        assertEquals(validUntil, builder.validUntil)
        assertNull(builder.expectedUpdate)
    }

    @Test
    fun testMsoBuilderAddNameSpace() {
        val builder = IssuerSigned.MsoBuilder()

        val item1 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem.create(
            digestID = DigestID(2u),
            elementIdentifier = DataElementIdentifier("family_name"),
            elementValue = "Doe"
        )

        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>, item2 as IssuerSignedItem<Any>)

        assertEquals(1, builder.nameSpaces.size)
        assertEquals(2, builder.nameSpaces[mdlNamespace]?.size)
    }

    @Test
    fun testMsoBuilderAddNameSpaceMultipleCalls() {
        val builder = IssuerSigned.MsoBuilder()

        val item1 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem.create(
            digestID = DigestID(2u),
            elementIdentifier = DataElementIdentifier("family_name"),
            elementValue = "Doe"
        )

        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>)
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item2 as IssuerSignedItem<Any>)

        assertEquals(1, builder.nameSpaces.size)
        assertEquals(2, builder.nameSpaces[mdlNamespace]?.size)
    }

    @Test
    fun testMsoBuilderWithDeviceKey() {
        val builder = IssuerSigned.MsoBuilder()
        val coseKey = createTestCoseKey()

        builder.withDeviceKey(coseKey)
        assertNotNull(builder.deviceKeyInfo)
    }

    @Test
    fun testMsoBuilderWithDeviceKeyInfo() {
        val builder = IssuerSigned.MsoBuilder()
        val coseKey = createTestCoseKey()
        val keyInfo = com.sphereon.crypto.core.ResolvedKeyInfo.fromKey(coseKey)

        builder.withDeviceKeyInfo(keyInfo)
        assertNotNull(builder.deviceKeyInfo)
    }

    @Test
    fun testMsoBuilderWithSigningKeyInfo() {
        val builder = IssuerSigned.MsoBuilder()
        val coseKey = createTestCoseKey()
        val keyInfo = com.sphereon.crypto.core.ManagedKeyInfo<CoseKey>(
            alias = "test-key",
            providerId = "test-provider",
            resolvedKeyInfo = com.sphereon.crypto.core.ResolvedKeyInfo.fromKey(coseKey)
        )

        builder.withSigningKeyInfo(keyInfo)
        assertNotNull(builder.issuerKeyInfo)
        assertEquals("test-key", builder.issuerKeyInfo?.alias)
    }

    @Test
    fun testMsoBuilderBuildRequiresDeviceKeyInfo() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
        assertTrue(exception.message?.contains("device key") == true)
    }

    @Test
    fun testMsoBuilderBuildRequiresValidUntil() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
        assertTrue(exception.message?.contains("valid until") == true)
    }

    @Test
    fun testMsoBuilderBuildRequiresDocType() {
        val builder = IssuerSigned.MsoBuilder()
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
        assertTrue(exception.message?.contains("doc type") == true)
    }

    @Test
    fun testMsoBuilderBuildRequiresNameSpaces() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
        assertTrue(exception.message?.contains("name space") == true)
    }

    @Test
    fun testMsoBuilderBuildSuccess() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val (mso, nameSpaces) = builder.build()

        assertNotNull(mso)
        assertNotNull(nameSpaces)
        assertEquals(mdlDocType, mso.docType)
        assertEquals("SHA-256", mso.digestAlgorithm.toString())
    }

    @Test
    fun testMsoBuilderBuildWithCustomAlgorithm() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val (mso, _) = builder.build(alg = DigestAlg.SHA384)

        assertNotNull(mso)
        assertEquals("SHA-384", mso.digestAlgorithm.toString())
    }

    @Test
    fun testMsoBuilderBuildWithNullAlgorithmDefaultsToSha256() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val (mso, _) = builder.build(alg = null)

        assertNotNull(mso)
        assertEquals("SHA-256", mso.digestAlgorithm.toString())
    }

    @Test
    fun testMsoBuilderBuildWithMultipleNamespaces() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item1 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("country"),
            elementValue = "US"
        )

        val euPidNamespace = NameSpace("eu.europa.ec.eudi.pid.1")

        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>)
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(euPidNamespace, item2 as IssuerSignedItem<Any>)

        val (mso, nameSpaces) = builder.build()

        assertNotNull(mso)
        assertEquals(2, nameSpaces.size)
        assertEquals(2, mso.valueDigests.size)
    }

    @Test
    fun testMsoBuilderBuildCreatesValidityInfo() {
        val signed = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validFrom = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val validUntil = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()
        val expectedUpdate = com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal()

        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidityInfo(
                signed = signed,
                validFrom = validFrom,
                validUntil = validUntil,
                expectedUpdate = expectedUpdate
            )

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val (mso, _) = builder.build()

        assertNotNull(mso.validityInfo)
        assertNotNull(mso.validityInfo.expectedUpdate)
    }

    @Test
    fun testMsoBuilderBuildCreatesDeviceKeyInfo() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val (mso, _) = builder.build()

        assertNotNull(mso.deviceKeyInfo)
        assertNotNull(mso.deviceKeyInfo.deviceKey)
    }

    @Test
    fun testMsoBuilderBuildCreatesValueDigests() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item1 = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        val item2 = IssuerSignedItem.create(
            digestID = DigestID(2u),
            elementIdentifier = DataElementIdentifier("family_name"),
            elementValue = "Doe"
        )

        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item1 as IssuerSignedItem<Any>, item2 as IssuerSignedItem<Any>)

        val (mso, _) = builder.build()

        assertNotNull(mso.valueDigests)
        assertEquals(1, mso.valueDigests.size)
        assertEquals(2, mso.valueDigests[mdlNamespace]?.size)
    }

    @Test
    fun testMsoBuilderChaining() {
        val builder = IssuerSigned.MsoBuilder()
            .withDocType(mdlDocType)
            .withDeviceKey(createTestCoseKey())
            .withSigned(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())
            .withValidFrom(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())
            .withValidUntil(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())
            .withExpectedUpdate(com.sphereon.core.compat.DateTimeUtils.DEFAULTS.dateTimeLocal())

        val item = IssuerSignedItem.create(
            digestID = DigestID(1u),
            elementIdentifier = DataElementIdentifier("given_name"),
            elementValue = "John"
        )
        @Suppress("UNCHECKED_CAST")
        builder.addNameSpace(mdlNamespace, item as IssuerSignedItem<Any>)

        val (mso, nameSpaces) = builder.build()

        assertNotNull(mso)
        assertNotNull(nameSpaces)
        assertNotNull(mso.validityInfo.expectedUpdate)
    }
}
