/*
 * Copyright 2025 Sphereon International B.V.
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
 */

package com.sphereon.trust.etsi.signature.xades

import com.sphereon.trust.etsi.testutil.parseXmlToDocument
import nl.adaptivity.xmlutil.dom2.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for XAdES QualifyingProperties parser.
 */
class XAdESParserTest {

    @Test
    fun parseQualifyingPropertiesWithSigningTime() {
        val xml = """
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="sig1">
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
                <ds:Object>
                    <xades:QualifyingProperties xmlns:xades="http://uri.etsi.org/01903/v1.3.2#" Target="#sig1">
                        <xades:SignedProperties Id="sp1">
                            <xades:SignedSignatureProperties>
                                <xades:SigningTime>2024-06-15T12:00:00Z</xades:SigningTime>
                            </xades:SignedSignatureProperties>
                        </xades:SignedProperties>
                    </xades:QualifyingProperties>
                </ds:Object>
            </ds:Signature>
        """.trimIndent()

        val doc = parseXmlToDocument(xml)
        val signatureElement = doc.getDocumentElement()!!

        val qp = XAdESParser.parse(signatureElement)

        assertNotNull(qp)
        assertEquals("#sig1", qp.target)
        assertNotNull(qp.signedProperties)
        assertEquals("sp1", qp.signedProperties!!.id)
        assertNotNull(qp.signedProperties!!.signedSignatureProperties)
        assertNotNull(qp.signedProperties!!.signedSignatureProperties!!.signingTime)
        assertEquals("2024-06-15T12:00:00Z",
            qp.signedProperties!!.signedSignatureProperties!!.signingTime.toString())
    }

    @Test
    fun parseSigningCertificateV2() {
        val xml = """
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
                <ds:Object>
                    <xades:QualifyingProperties xmlns:xades="http://uri.etsi.org/01903/v1.3.2#">
                        <xades:SignedProperties>
                            <xades:SignedSignatureProperties>
                                <xades:SigningCertificateV2>
                                    <xades:Cert>
                                        <xades:CertDigest>
                                            <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                                            <ds:DigestValue>dGVzdA==</ds:DigestValue>
                                        </xades:CertDigest>
                                    </xades:Cert>
                                </xades:SigningCertificateV2>
                            </xades:SignedSignatureProperties>
                        </xades:SignedProperties>
                    </xades:QualifyingProperties>
                </ds:Object>
            </ds:Signature>
        """.trimIndent()

        val doc = parseXmlToDocument(xml)
        val signatureElement = doc.getDocumentElement()!!

        val qp = XAdESParser.parse(signatureElement)

        assertNotNull(qp)
        val certDigests = qp.signedProperties?.signedSignatureProperties?.signingCertificateV2
        assertNotNull(certDigests)
        assertEquals(1, certDigests.size)
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256", certDigests[0].digestAlgorithm)
        assertTrue(certDigests[0].digestValue.isNotEmpty())
    }

    @Test
    fun parseDataObjectFormat() {
        val xml = """
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
                <ds:Object>
                    <xades:QualifyingProperties xmlns:xades="http://uri.etsi.org/01903/v1.3.2#">
                        <xades:SignedProperties>
                            <xades:SignedSignatureProperties>
                                <xades:SigningTime>2024-01-01T00:00:00Z</xades:SigningTime>
                            </xades:SignedSignatureProperties>
                            <xades:SignedDataObjectProperties>
                                <xades:DataObjectFormat ObjectReference="#ref1">
                                    <xades:MimeType>text/xml</xades:MimeType>
                                </xades:DataObjectFormat>
                            </xades:SignedDataObjectProperties>
                        </xades:SignedProperties>
                    </xades:QualifyingProperties>
                </ds:Object>
            </ds:Signature>
        """.trimIndent()

        val doc = parseXmlToDocument(xml)
        val signatureElement = doc.getDocumentElement()!!

        val qp = XAdESParser.parse(signatureElement)

        assertNotNull(qp)
        val formats = qp.signedProperties?.signedDataObjectProperties?.dataObjectFormats
        assertNotNull(formats)
        assertEquals(1, formats.size)
        assertEquals("#ref1", formats[0].objectReference)
        assertEquals("text/xml", formats[0].mimeType)
    }

    @Test
    fun noQualifyingPropertiesReturnsNull() {
        val xml = """
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
            </ds:Signature>
        """.trimIndent()

        val doc = parseXmlToDocument(xml)
        val signatureElement = doc.getDocumentElement()!!

        val qp = XAdESParser.parse(signatureElement)
        assertNull(qp)
    }

    @Test
    fun findSignedPropertiesElement() {
        val xml = """
            <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
                <ds:Object>
                    <xades:QualifyingProperties xmlns:xades="http://uri.etsi.org/01903/v1.3.2#">
                        <xades:SignedProperties Id="sp1">
                            <xades:SignedSignatureProperties>
                                <xades:SigningTime>2024-01-01T00:00:00Z</xades:SigningTime>
                            </xades:SignedSignatureProperties>
                        </xades:SignedProperties>
                    </xades:QualifyingProperties>
                </ds:Object>
            </ds:Signature>
        """.trimIndent()

        val doc = parseXmlToDocument(xml)
        val signatureElement = doc.getDocumentElement()!!

        val spElement = XAdESParser.findSignedPropertiesElement(signatureElement)
        assertNotNull(spElement)
        assertEquals("SignedProperties", spElement.getLocalName())
        assertEquals("sp1", spElement.getAttribute("Id"))
    }
}
