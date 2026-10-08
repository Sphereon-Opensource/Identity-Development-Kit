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
 */

package com.sphereon.trust.etsi.signature.xmldsig

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.core.compat.xml.c14n.ExclusiveC14N
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.trust.etsi.testutil.parseXmlToDocument
import nl.adaptivity.xmlutil.dom2.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for XML Signature Reference validation.
 */
class ReferenceValidatorTest {
    @Test
    fun testReferenceWithFragmentUri() {
        // Create a document with a signed element and a reference to it
        val targetContent = """<Target Id="target1">Some content</Target>"""
        val targetDoc = parseXmlToDocument(targetContent)
        val targetElement = targetDoc.getDocumentElement()!!

        // Compute the expected digest of the canonicalized target
        val canonicalTarget = ExclusiveC14N.canonicalize(targetElement)
        val expectedDigest = hash(canonicalTarget, DigestAlg.SHA256)
        val digestB64 = expectedDigest.encodeTo(Encoding.BASE64)

        // Build a full signed document
        val xml = """<root xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <Target Id="target1">Some content</Target>
            <ds:Signature>
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                    <ds:Reference URI="#target1">
                        <ds:Transforms>
                            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                        </ds:Transforms>
                        <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                        <ds:DigestValue>$digestB64</ds:DigestValue>
                    </ds:Reference>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
            </ds:Signature>
        </root>"""

        val doc = parseXmlToDocument(xml)
        val root = doc.getDocumentElement()!!
        val signatureElement = root.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature")[0] as Element
        val signedInfo = signatureElement.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignedInfo")[0] as Element

        val results = ReferenceValidator.validateReferences(signedInfo, doc, signatureElement)

        assertEquals(1, results.size)
        assertTrue(results[0].valid, "Reference should be valid: ${results[0].errorMessage}")
        assertEquals("#target1", results[0].uri)
    }

    @Test
    fun testReferenceWithInvalidDigest() {
        val xml = """<root xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <Target Id="target1">Some content</Target>
            <ds:Signature>
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                    <ds:Reference URI="#target1">
                        <ds:Transforms>
                            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                        </ds:Transforms>
                        <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                        <ds:DigestValue>AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</ds:DigestValue>
                    </ds:Reference>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
            </ds:Signature>
        </root>"""

        val doc = parseXmlToDocument(xml)
        val root = doc.getDocumentElement()!!
        val signatureElement = root.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature")[0] as Element
        val signedInfo = signatureElement.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignedInfo")[0] as Element

        val results = ReferenceValidator.validateReferences(signedInfo, doc, signatureElement)

        assertEquals(1, results.size)
        assertFalse(results[0].valid, "Reference with wrong digest should be invalid")
        assertTrue(results[0].errorMessage?.contains("Digest mismatch") == true)
    }

    @Test
    fun testMissingReference() {
        val xml = """<root xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <ds:Signature>
                <ds:SignedInfo>
                    <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                    <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
                    <ds:Reference URI="#nonexistent">
                        <ds:Transforms>
                            <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
                        </ds:Transforms>
                        <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                        <ds:DigestValue>AAAA</ds:DigestValue>
                    </ds:Reference>
                </ds:SignedInfo>
                <ds:SignatureValue>AAAA</ds:SignatureValue>
            </ds:Signature>
        </root>"""

        val doc = parseXmlToDocument(xml)
        val root = doc.getDocumentElement()!!
        val signatureElement = root.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature")[0] as Element
        val signedInfo = signatureElement.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignedInfo")[0] as Element

        val results = ReferenceValidator.validateReferences(signedInfo, doc, signatureElement)

        assertEquals(1, results.size)
        assertFalse(results[0].valid)
        assertTrue(results[0].errorMessage?.contains("Could not resolve") == true)
    }
}
