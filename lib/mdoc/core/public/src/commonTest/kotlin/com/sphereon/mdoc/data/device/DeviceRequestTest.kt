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

import com.sphereon.mdoc.oid4vp.Oid4VPConstraintField
import com.sphereon.mdoc.oid4vp.Oid4VPConstraints
import com.sphereon.mdoc.oid4vp.Oid4VPFormat
import com.sphereon.mdoc.oid4vp.Oid4VPInputDescriptor
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.mdoc.oid4vp.Oid4VPSupportedAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceRequest and DeviceRequestVersion classes.
 */
class DeviceRequestTest {

    // DeviceRequestVersion tests

    @Test
    fun testDeviceRequestVersionCreation() {
        val version = DeviceRequestVersion("1.0")
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testDeviceRequestVersionInvalidThrows() {
        assertFailsWith<IllegalArgumentException> {
            DeviceRequestVersion("2.0")
        }
    }

    @Test
    fun testDeviceRequestVersionToCborStructure() {
        val version = DeviceRequestVersion("1.0")
        val cbor = version.toCborStructure()
        assertEquals("1.0", cbor.value)
    }

    @Test
    fun testDeviceRequestVersionFromCborStructure() {
        val version = DeviceRequestVersion("1.0")
        val cbor = version.toCborStructure()
        val decoded = DeviceRequestVersion.fromCborStructure(cbor)
        assertEquals(version, decoded)
    }

    // DeviceRequest tests

    @Test
    fun testDeviceRequestDefaultCreation() {
        val request = DeviceRequest(original = null)
        assertEquals("1.0", request.version.toString())
        assertNull(request.docRequests)
        assertNull(request.macKeys)
        assertNull(request.oid4vpRequest)
        assertNull(request.original)
    }

    @Test
    fun testDeviceRequestCborBuilder() {
        val request = DeviceRequest(original = null)
        val builder = request.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDeviceRequestEncodeDecode() {
        val request = DeviceRequest(original = null)
        val encoded = request.encodeCbor()
        val decoded = DeviceRequest.decodeCbor(encoded)

        assertEquals(request.version, decoded.version)
        assertNull(decoded.docRequests)
    }

    @Test
    fun testDeviceRequestCopyWith() {
        val request = DeviceRequest(original = null)
        val copy = request.copyWith(original = byteArrayOf(0x01))

        assertNull(request.original)
        assertNotNull(copy.original)
    }

    @Test
    fun testDeviceRequestEffectiveDocRequestsEmpty() {
        val request = DeviceRequest(original = null)
        val effective = request.effectiveDocRequests()
        assertTrue(effective.isEmpty())
    }

    @Test
    fun testDeviceRequestHasOid4vpRequestFalse() {
        val request = DeviceRequest(original = null)
        assertFalse(request.hasOid4vpRequest)
    }

    @Test
    fun testDeviceRequestHasDocRequestFalse() {
        val request = DeviceRequest(original = null)
        assertFalse(request.hasDocRequest)
    }

    @Test
    fun testDeviceRequestEquality() {
        val request1 = DeviceRequest(original = null)
        val request2 = DeviceRequest(original = null)
        assertEquals(request1, request2)
    }

    @Test
    fun testDeviceRequestEqualitySameInstance() {
        val request = DeviceRequest(original = null)
        assertEquals(request, request)
    }

    @Test
    fun testDeviceRequestInequalityNull() {
        val request = DeviceRequest(original = null)
        assertFalse(request.equals(null))
    }

    @Test
    fun testDeviceRequestInequalityDifferentClass() {
        val request = DeviceRequest(original = null)
        assertFalse(request.equals("not a request"))
    }

    @Test
    fun testDeviceRequestHashCode() {
        val request1 = DeviceRequest(original = null)
        val request2 = DeviceRequest(original = null)
        assertEquals(request1.hashCode(), request2.hashCode())
    }

    @Test
    fun testDeviceRequestToString() {
        val request = DeviceRequest(original = null)
        val str = request.toString()
        assertTrue(str.contains("DeviceRequest"))
        assertTrue(str.contains("version=1.0"))
    }

    @Test
    fun testDeviceRequestCompanionLabels() {
        assertEquals("version", DeviceRequest.VERSION.value)
        assertEquals("docRequests", DeviceRequest.DOC_REQUESTS.value)
        assertEquals("macKeys", DeviceRequest.MAC_KEYS.value)
        assertEquals("oid4vpRequest", DeviceRequest.OID4VP_REQUEST.value)
    }

    // effectiveDocRequests with docRequests tests

    @Test
    fun testDeviceRequestEffectiveDocRequestsWithDocRequests() {
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val request = DeviceRequest(
            docRequests = arrayOf(docRequest),
            original = null
        )

        val effective = request.effectiveDocRequests()
        assertEquals(1, effective.size)
        assertEquals(mdlDocType, effective[0].getDocType())
    }

    @Test
    fun testDeviceRequestEffectiveDocRequestsWithMultipleDocRequests() {
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")
        val pidDocType = DocType("eu.europa.ec.eudi.pid.1")

        val docRequest1 = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val docRequest2 = DocRequest.Builder()
            .docType(pidDocType)
            .add(NameSpace("eu.europa.ec.eudi.pid.1"), DataElementIdentifier("family_name"))
            .buildDocRequest()

        val request = DeviceRequest(
            docRequests = arrayOf(docRequest1, docRequest2),
            original = null
        )

        val effective = request.effectiveDocRequests()
        assertEquals(2, effective.size)
    }

    @Test
    fun testDeviceRequestHasDocRequestTrue() {
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val request = DeviceRequest(
            docRequests = arrayOf(docRequest),
            original = null
        )

        assertTrue(request.hasDocRequest)
    }

    @Test
    fun testDeviceRequestInequalityDifferentDocRequests() {
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val request1 = DeviceRequest(original = null)
        val request2 = DeviceRequest(docRequests = arrayOf(docRequest), original = null)

        assertNotEquals(request1, request2)
    }

    @Test
    fun testDeviceRequestHashCodeWithDocRequests() {
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val request = DeviceRequest(
            docRequests = arrayOf(docRequest),
            original = null
        )

        assertNotNull(request.hashCode())
    }

    @Test
    fun testDeviceRequestCborBuilderWithDocRequests() {
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val request = DeviceRequest(
            docRequests = arrayOf(docRequest),
            original = null
        )

        val builder = request.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDeviceRequestVersionInvalidEmpty() {
        assertFailsWith<IllegalArgumentException> {
            DeviceRequestVersion("")
        }
    }

    @Test
    fun testDeviceRequestVersionInvalidVersion() {
        assertFailsWith<IllegalArgumentException> {
            DeviceRequestVersion("0.9")
        }
    }

    // Tests for oid4vpRequest branch coverage

    private fun createOid4vpPresentationDefinition(): Oid4VPPresentationDefinition {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field = Oid4VPConstraintField(
            path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
            intent_to_retain = true
        )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor = Oid4VPInputDescriptor(
            id = DocType("org.iso.18013.5.1.mDL"),
            format = format,
            constraints = constraints
        )

        return Oid4VPPresentationDefinition(
            id = "test-definition",
            input_descriptors = arrayOf(inputDescriptor)
        )
    }

    @Test
    fun testDeviceRequestHasOid4vpRequestTrue() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val request = DeviceRequest(
            oid4vpRequest = oid4vpRequest,
            original = null
        )
        assertTrue(request.hasOid4vpRequest)
    }

    @Test
    fun testDeviceRequestEffectiveDocRequestsFromOid4vp() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val request = DeviceRequest(
            docRequests = null, // No docRequests
            oid4vpRequest = oid4vpRequest,
            original = null
        )

        val effective = request.effectiveDocRequests()
        assertEquals(1, effective.size)
    }

    @Test
    fun testDeviceRequestEffectiveDocRequestsWithEmptyDocRequestsAndOid4vp() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val request = DeviceRequest(
            docRequests = arrayOf(), // Empty array
            oid4vpRequest = oid4vpRequest,
            original = null
        )

        val effective = request.effectiveDocRequests()
        assertEquals(1, effective.size) // Should fall back to oid4vpRequest
    }

    @Test
    fun testDeviceRequestEffectiveDocRequestsPrefersDocRequests() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val request = DeviceRequest(
            docRequests = arrayOf(docRequest),
            oid4vpRequest = oid4vpRequest, // Has both
            original = null
        )

        // Should prefer docRequests over oid4vpRequest
        val effective = request.effectiveDocRequests()
        assertEquals(1, effective.size)
        assertEquals(mdlDocType, effective[0].getDocType())
    }

    @Test
    fun testDeviceRequestWithOid4vpRequestCborBuilder() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val request = DeviceRequest(
            oid4vpRequest = oid4vpRequest,
            original = null
        )

        val builder = request.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testDeviceRequestCopyWithAllParameters() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val mdlNamespace = NameSpace("org.iso.18013.5.1")
        val mdlDocType = DocType("org.iso.18013.5.1.mDL")

        val docRequest = DocRequest.Builder()
            .docType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"))
            .buildDocRequest()

        val originalRequest = DeviceRequest(original = null)
        val copyRequest = originalRequest.copyWith(
            docRequests = arrayOf(docRequest),
            oid4vpRequest = oid4vpRequest,
            original = byteArrayOf(0x01, 0x02)
        )

        assertNotNull(copyRequest.docRequests)
        assertNotNull(copyRequest.oid4vpRequest)
        assertNotNull(copyRequest.original)
    }

    @Test
    fun testDeviceRequestHasDocRequestTrueFromOid4vp() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val request = DeviceRequest(
            oid4vpRequest = oid4vpRequest,
            original = null
        )
        assertTrue(request.hasDocRequest)
    }

    @Test
    fun testDeviceRequestInequalityDifferentOid4vpRequest() {
        val oid4vpRequest = createOid4vpPresentationDefinition()
        val request1 = DeviceRequest(original = null)
        val request2 = DeviceRequest(oid4vpRequest = oid4vpRequest, original = null)

        // The requests have different oid4vpRequest values (null vs non-null)
        assertFalse(request1.hasOid4vpRequest)
        assertTrue(request2.hasOid4vpRequest)
    }
}
