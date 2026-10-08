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

import com.sphereon.mdoc.data.mso.createIssuerAuthForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DocRequest and DocRequest.Builder.
 */
class DocRequestTest {
    private val mdlNamespace = NameSpace("org.iso.18013.5.1")
    private val mdlDocType = DocType("org.iso.18013.5.1.mDL")

    @Test
    fun testDocRequestCreation() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .build()

        val docRequest = DocRequest(itemsRequest)

        assertEquals(itemsRequest, docRequest.itemsRequest)
        assertNull(docRequest.readerAuth)
    }

    @Test
    fun testDocRequestGetNameSpaces() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .build()

        val docRequest = DocRequest(itemsRequest)
        val namespaces = docRequest.getNameSpaces()

        assertEquals(1, namespaces.size)
        assertEquals(mdlNamespace, namespaces[0])
    }

    @Test
    fun testDocRequestGetDocType() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .build()

        val docRequest = DocRequest(itemsRequest)

        assertEquals(mdlDocType, docRequest.getDocType())
    }

    @Test
    fun testDocRequestGetIdentifiers() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"), IntentToRetain(true))
                .add(mdlNamespace, DataElementIdentifier("given_name"), IntentToRetain(false))
                .build()

        val docRequest = DocRequest(itemsRequest)
        val identifiers = docRequest.getIdentifiers(mdlNamespace)

        assertEquals(2, identifiers.size)
        assertTrue(identifiers.containsKey(DataElementIdentifier("family_name")))
        assertTrue(identifiers.containsKey(DataElementIdentifier("given_name")))
    }

    @Test
    fun testDocRequestToString() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .build()

        val docRequest = DocRequest(itemsRequest)
        val str = docRequest.toString()

        assertTrue(str.contains("DocRequest"))
        assertTrue(str.contains("itemsRequest"))
    }

    @Test
    fun testDocRequestCodecEncodeDecode() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .build()

        val docRequest = DocRequest(itemsRequest)
        val encoded = encodeDocRequestWithCodec(docRequest)
        val decoded = decodeDocRequestWithCodec(encoded)

        assertNotNull(encoded)
        assertEquals(mdlDocType, decoded.getDocType())
        assertTrue(decoded.getIdentifiers(mdlNamespace).containsKey(DataElementIdentifier("family_name")))
    }

    @Test
    fun testDocRequestCompanionLabels() {
        assertEquals("itemsRequest", DocRequest.ITEMS_REQUEST.value)
        assertEquals("readerAuth", DocRequest.READER_AUTH.value)
    }

    // DocRequest.Builder tests

    @Test
    fun testDocRequestBuilderCreation() {
        val builder = DocRequest.Builder()
        assertNotNull(builder)
        assertNotNull(builder.deviceItemsRequestBuilder)
    }

    @Test
    fun testDocRequestBuilderDocType() {
        val builder = DocRequest.Builder()
        val itemsBuilder = builder.docType(mdlDocType)

        assertNotNull(itemsBuilder)
    }

    @Test
    fun testDocRequestBuilderDocTypeWithRequestInfo() {
        val builder = DocRequest.Builder()
        val requestInfo = mapOf("key" to "value")
        val itemsBuilder = builder.docType(mdlDocType, requestInfo)

        assertNotNull(itemsBuilder)
    }

    @Test
    fun testDocRequestBuilderBuild() {
        val docRequest =
            DocRequest
                .Builder()
                .docType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .buildDocRequest()

        assertNotNull(docRequest)
        assertEquals(mdlDocType, docRequest.getDocType())
    }

    @Test
    fun testDocRequestBuilderToString() {
        val builder = DocRequest.Builder()
        val str = builder.toString()

        assertTrue(str.contains("Builder"))
        assertTrue(str.contains("deviceItemsRequestBuilder"))
    }

    @Test
    fun testDocRequestBuilderWithItemsRequestBuilder() {
        val itemsBuilder =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))

        val builder = DocRequest.Builder(deviceItemsRequestBuilder = itemsBuilder)
        val docRequest = builder.build()

        assertEquals(mdlDocType, docRequest.getDocType())
    }

    @Test
    fun testDocRequestBuilderChainedBuild() {
        val docRequest =
            DocRequest
                .Builder()
                .docType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .add(mdlNamespace, DataElementIdentifier("given_name"))
                .buildDocRequest()

        val identifiers = docRequest.getIdentifiers(mdlNamespace)
        assertEquals(2, identifiers.size)
    }

    @Test
    fun testDocRequestWithOriginal() {
        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .build()

        val original = byteArrayOf(1, 2, 3)
        val docRequest = DocRequest(itemsRequest, original = original)

        assertNotNull(docRequest.original)
        assertEquals(3, docRequest.original!!.size)
    }

    @Test
    fun testDocRequestMultipleNamespaces() {
        val euPidNamespace = NameSpace("eu.europa.ec.eudi.pid.1")

        val itemsRequest =
            DeviceItemsRequest
                .Builder()
                .withDocType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .add(euPidNamespace, DataElementIdentifier("given_name"))
                .build()

        val docRequest = DocRequest(itemsRequest)
        val namespaces = docRequest.getNameSpaces()

        assertEquals(2, namespaces.size)
    }

    // Additional tests for branch coverage

    @Test
    fun testDocRequestBuilderWithNullItemsRequestBuilderThrows() {
        val builder = DocRequest.Builder(deviceItemsRequestBuilder = null)
        // Accessing the builder() method should throw when deviceItemsRequestBuilder is null
        // but since init sets it, we need to manually set it to null
        builder.deviceItemsRequestBuilder = null

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testDocRequestLimitDisclosures() {
        // Create an IssuerSigned with some namespaces
        val coseKey =
            com.sphereon.crypto.core.cose.CoseKeyJson
                .Builder()
                .withKty(com.sphereon.crypto.core.cose.CoseKeyTypeEnum.EC2)
                .withCrv(com.sphereon.crypto.core.cose.CoseCurve.P_256)
                .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                .build()
                .toCbor()

        val deviceKeyInfo =
            com.sphereon.mdoc.data.mso.DeviceKeyInfo(
                deviceKey = coseKey,
                keyAuthorizations = null,
                keyInfo = null,
                original = null,
            )

        val now = com.sphereon.cbor.TDate("2025-01-20T12:00:00Z")
        val validityInfo =
            com.sphereon.mdoc.data.mso.ValidityInfo(
                signed = now,
                validFrom = now,
                validUntil = now,
                expectedUpdate = null,
            )

        val mso =
            com.sphereon.mdoc.data.mso.MobileSecurityObject(
                digestAlgorithm =
                    com.sphereon.mdoc.data.mso
                        .DigestAlgorithm("SHA-256"),
                valueDigests = emptyMap(),
                deviceKeyInfo = deviceKeyInfo,
                docType = mdlDocType,
                validityInfo = validityInfo,
                original = null,
            )

        val issuerAuth = createIssuerAuthForTest(mso)

        val nullNameSpaces: IssuerSignedNameSpaces? = null
        val issuerSigned =
            IssuerSigned(
                nameSpaces = nullNameSpaces,
                issuerAuth = issuerAuth,
                original = null,
            )

        val docRequest =
            DocRequest
                .Builder()
                .docType(mdlDocType)
                .add(mdlNamespace, DataElementIdentifier("family_name"))
                .buildDocRequest()

        val limited = docRequest.limitDisclosures(issuerSigned)
        assertNotNull(limited)
    }
}
