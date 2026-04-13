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

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.TDate
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Document, DocumentWithKeyAlias interface, and Document instance methods.
 */
class DocumentTest {

    private val mdlDocType = DocType("org.iso.18013.5.1.mDL")
    private val mdlNamespace = NameSpace("org.iso.18013.5.1")

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

    private fun createTestMso(): MobileSecurityObject {
        return MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = createTestDeviceKeyInfo(),
            docType = mdlDocType,
            validityInfo = createTestValidityInfo(),
            original = null
        )
    }

    private fun createTestIssuerAuth(mso: MobileSecurityObject = createTestMso()): CoseSign1<MobileSecurityObject> {
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

    private fun createTestIssuerSigned(): IssuerSigned {
        return IssuerSigned(
            nameSpaces = createNameSpaces(),
            issuerAuth = createTestIssuerAuth(),
            original = null
        )
    }

    private fun createDeviceAuth(): DeviceAuth {
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })
        val payload = CborByteString("device-auth-payload".encodeToByteArray())

        val coseSign1 = COSE_Sign1<DeviceAuthentication>(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload,
            signature = signature
        )

        return DeviceAuth(
            deviceSignature = coseSign1,
            deviceMac = null,
            original = null
        )
    }

    private fun createTestDeviceSigned(): DeviceSigned {
        return DeviceSigned(
            nameSpaces = DeviceNameSpaces(),
            deviceAuth = createDeviceAuth(),
            original = null
        )
    }

    // Companion object tests

    @Test
    fun testCompanionLabels() {
        assertEquals("docType", Document.DOC_TYPE.value)
        assertEquals("issuerSigned", Document.ISSUER_SIGNED.value)
        assertEquals("deviceSigned", Document.DEVICE_SIGNED.value)
        assertEquals("errors", Document.ERRORS.value)
    }

    // fromDeviceResponse tests

    @Test
    fun testFromDeviceResponseWithNull() {
        val result = Document.fromDeviceResponse(null)
        assertNull(result)
    }

    @Test
    fun testFromDeviceResponseWithEmptyArray() {
        val emptyArray = CborArray<CborItem<*>>(mutableListOf())
        val result = Document.fromDeviceResponse(emptyArray)
        assertNull(result)
    }

    // DocumentWithKeyAlias interface tests

    @Test
    fun testDocumentWithKeyAliasInterface() {
        // Create a mock implementation of DocumentWithKeyAlias
        val mockDoc = object : DocumentWithKeyAlias {
            override val providerId = "test-provider"
            override val keyAlias = "test-alias"
            override val document: Document
                get() = throw NotImplementedError("Document is not implemented for this test")
        }

        assertEquals("test-provider", mockDoc.providerId)
        assertEquals("test-alias", mockDoc.keyAlias)

        // Test createKeyInfo() method
        val keyInfo = mockDoc.createKeyInfo()
        assertNotNull(keyInfo)
        assertEquals("test-provider", keyInfo.providerId)
        assertEquals("test-alias", keyInfo.alias)
    }

    @Test
    fun testDocumentWithKeyAliasCreateKeyInfoProperties() {
        val mockDoc = object : DocumentWithKeyAlias {
            override val providerId = "my-kms-provider"
            override val keyAlias = "device-key-1"
            override val document: Document
                get() = throw NotImplementedError("Document is not implemented for this test")
        }

        val keyInfo = mockDoc.createKeyInfo()

        // Verify the KeyInfo has correct type
        assertNotNull(keyInfo)
        // KeyInfo should have the expected properties
        assertEquals("my-kms-provider", keyInfo.providerId)
        assertEquals("device-key-1", keyInfo.alias)
    }

    // Document instance tests

    @Test
    fun testDocumentCreation() {
        val issuerSigned = createTestIssuerSigned()
        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        assertNotNull(document)
        assertEquals(mdlDocType, document.docType)
        assertNotNull(document.issuerSigned)
        assertNull(document.deviceSigned)
        assertNull(document.errors)
    }

    @Test
    fun testDocumentWithDeviceSigned() {
        val issuerSigned = createTestIssuerSigned()
        val deviceSigned = createTestDeviceSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = deviceSigned,
            original = null
        )

        assertNotNull(document)
        assertNotNull(document.deviceSigned)
    }

    @Test
    fun testDocumentWithErrors() {
        val issuerSigned = createTestIssuerSigned()
        val errors = mapOf(
            mdlNamespace to mapOf(DataElementIdentifier("some_field") to 10L)
        )

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            errors = errors,
            original = null
        )

        assertNotNull(document)
        assertNotNull(document.errors)
        assertEquals(1, document.errors?.size)
    }

    @Test
    fun testDocumentMSO() {
        val issuerSigned = createTestIssuerSigned()
        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val mso = document.MSO
        assertNotNull(mso)
        assertEquals(mdlDocType, mso.docType)
    }

    @Test
    fun testDocumentDeviceKeyInfo() {
        val issuerSigned = createTestIssuerSigned()
        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val deviceKeyInfo = document.deviceKeyInfo
        assertNotNull(deviceKeyInfo)
        assertNotNull(deviceKeyInfo.deviceKey)
    }

    @Test
    fun testDocumentEncodeCborWithOriginal() {
        val issuerSigned = createTestIssuerSigned()
        val originalBytes = byteArrayOf(0x01, 0x02, 0x03)

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = originalBytes
        )

        val encoded = document.encodeCbor()
        assertTrue(originalBytes.contentEquals(encoded))
    }

    @Test
    fun testDocumentEncodeCborWithoutOriginal() {
        val issuerSigned = createTestIssuerSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val encoded = document.encodeCbor()
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testDocumentCborBuilder() {
        val issuerSigned = createTestIssuerSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val builder = document.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDocumentCborBuilderWithDeviceSigned() {
        val issuerSigned = createTestIssuerSigned()
        val deviceSigned = createTestDeviceSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = deviceSigned,
            original = null
        )

        val builder = document.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDocumentCborBuilderWithErrors() {
        val issuerSigned = createTestIssuerSigned()
        val errors = mapOf(
            mdlNamespace to mapOf(DataElementIdentifier("some_field") to 10L)
        )

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            errors = errors,
            original = null
        )

        val builder = document.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDocumentCborBuilderWithEmptyErrors() {
        val issuerSigned = createTestIssuerSigned()
        val errors: Map<NameSpace, Map<DataElementIdentifier, Long>> = emptyMap()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            errors = errors,
            original = null
        )

        val builder = document.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDocumentGetNameSpaces() {
        val issuerSigned = createTestIssuerSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val namespaces = document.getNameSpaces()
        assertNotNull(namespaces)
        assertEquals(1, namespaces.size)
        assertEquals(mdlNamespace, namespaces[0])
    }

    @Test
    fun testDocumentGetNameSpacesWithNullNamespaces() {
        val nullNameSpaces: IssuerSignedNameSpaces? = null
        val issuerSigned = IssuerSigned(
            nameSpaces = nullNameSpaces,
            issuerAuth = createTestIssuerAuth(),
            original = null
        )

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val namespaces = document.getNameSpaces()
        assertNotNull(namespaces)
        assertEquals(0, namespaces.size)
    }

    @Test
    fun testDocumentLimitDisclosures() {
        val issuerSigned = createTestIssuerSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val limited = document.limitDisclosures(docRequest)
        assertNotNull(limited)
        assertEquals(1, limited.nameSpaces?.get(mdlNamespace)?.size)
    }

    @Test
    fun testDocumentToString() {
        val issuerSigned = createTestIssuerSigned()

        val document = Document(
            docType = mdlDocType,
            issuerSigned = issuerSigned,
            deviceSigned = null,
            original = null
        )

        val str = document.toString()
        assertTrue(str.contains("Document"))
        assertTrue(str.contains("docType"))
        assertTrue(str.contains("issuerSigned"))
    }

    @Test
    fun testDocumentFromIssuerSigned() {
        val issuerSigned = createTestIssuerSigned()
        val document = Document.fromIssuerSigned(issuerSigned)

        assertNotNull(document)
        assertEquals(mdlDocType, document.docType)
        assertEquals(issuerSigned, document.issuerSigned)
    }


}
