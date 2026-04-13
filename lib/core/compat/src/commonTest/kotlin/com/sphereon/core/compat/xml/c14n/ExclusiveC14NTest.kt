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

package com.sphereon.core.compat.xml.c14n

import nl.adaptivity.xmlutil.dom2.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Exclusive XML Canonicalization (exc-c14n).
 */
class ExclusiveC14NTest {

    private fun parseXml(xml: String): Document = parseXmlToDocument(xml)

    @Test
    fun simpleElementCanonicalization() {
        val xml = "<root><child>text</child></root>"
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        assertEquals("<root><child>text</child></root>", result)
    }

    @Test
    fun emptyElementUsesStartEndTags() {
        // In canonical XML, empty elements must use start+end tag pair, not self-closing
        val xml = "<root><empty></empty></root>"
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        assertEquals("<root><empty></empty></root>", result)
    }

    @Test
    fun namespacedElementOutputsDeclaration() {
        val xml = """<ds:SignedInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#"><ds:Reference></ds:Reference></ds:SignedInfo>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()

        // The ds namespace should appear on the root element
        assertTrue(result.contains("xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\""))
        // Child element should NOT redeclare the ds namespace (already rendered by ancestor)
        val afterRoot = result.substringAfter("<ds:Reference")
        assertTrue(!afterRoot.startsWith(" xmlns:ds="), "Child should not redeclare parent namespace")
    }

    @Test
    fun attributesSortedByNamespaceUriThenLocalName() {
        val xml = """<root b="2" a="1" c="3"></root>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        // Attributes should be sorted: a, b, c (all have empty namespace URI)
        assertEquals("""<root a="1" b="2" c="3"></root>""", result)
    }

    @Test
    fun textContentEscaping() {
        val xml = "<root>a &amp; b &lt; c &gt; d</root>"
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        assertEquals("<root>a &amp; b &lt; c &gt; d</root>", result)
    }

    @Test
    fun attributeValueEscaping() {
        val xml = """<root attr="a&amp;b&lt;c&quot;d"></root>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        assertTrue(result.contains("attr=\"a&amp;b&lt;c&quot;d\""))
    }

    @Test
    fun exclusiveCanonicalizationOmitsUnusedAncestorNamespaces() {
        // In exclusive C14N, only visibly utilized namespaces are output
        val xml = """<root xmlns:unused="http://unused" xmlns:used="http://used"><used:child></used:child></root>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!
        // Canonicalize just the child element
        val child = root.getElementsByTagNameNS("http://used", "child")[0] as Element

        val result = ExclusiveC14N.canonicalize(child).decodeToString()

        // Should include "used" namespace but NOT "unused"
        assertTrue(result.contains("xmlns:used=\"http://used\""))
        assertTrue(!result.contains("xmlns:unused"))
    }

    @Test
    fun defaultNamespaceHandling() {
        val xml = """<root xmlns="http://example.com"><child></child></root>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        // Default namespace should be declared on the root
        assertTrue(result.contains("xmlns=\"http://example.com\""))
    }

    @Test
    fun idempotency() {
        val xml = """<ds:SignedInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#"><ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"></ds:SignatureMethod></ds:SignedInfo>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val first = ExclusiveC14N.canonicalize(root).decodeToString()
        val doc2 = parseXml(first)
        val root2 = doc2.getDocumentElement()!!
        val second = ExclusiveC14N.canonicalize(root2).decodeToString()

        assertEquals(first, second, "Canonicalization should be idempotent")
    }

    @Test
    fun subtreeCanonicalization() {
        val xml = """<doc xmlns:ds="http://www.w3.org/2000/09/xmldsig#"><ds:Signature><ds:SignedInfo><ds:Reference URI=""></ds:Reference></ds:SignedInfo></ds:Signature></doc>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val signedInfo = root.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignedInfo")[0] as Element
        val result = ExclusiveC14N.canonicalize(signedInfo).decodeToString()

        // SignedInfo should include ds namespace declaration (not inherited from parent in exc-c14n)
        assertTrue(result.startsWith("<ds:SignedInfo"))
        assertTrue(result.contains("xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\""))
    }

    @Test
    fun excludeNodeOmitsFromOutput() {
        val xml = """<root><keep>data</keep><remove>hidden</remove></root>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val removeEl = root.getElementsByTagName("remove")[0]

        val result = ExclusiveC14N.canonicalize(root, excludeNode = removeEl).decodeToString()
        assertTrue(result.contains("<keep>data</keep>"))
        assertTrue(!result.contains("remove"))
        assertTrue(!result.contains("hidden"))
    }

    @Test
    fun whitespacePreservation() {
        val xml = "<root>\n  <child>text</child>\n</root>"
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        // Whitespace between elements should be preserved
        assertTrue(result.contains("\n  <child>"))
        assertTrue(result.contains("</child>\n"))
    }

    @Test
    fun multipleNamespaces() {
        val xml = """<a:root xmlns:a="http://a" xmlns:b="http://b"><a:child b:attr="val"></a:child></a:root>"""
        val doc = parseXml(xml)
        val root = doc.getDocumentElement()!!

        val result = ExclusiveC14N.canonicalize(root).decodeToString()
        // Root should declare "a" namespace
        assertTrue(result.contains("xmlns:a=\"http://a\""))
        // Child should declare "b" namespace (visibly utilized by attribute)
        assertTrue(result.contains("xmlns:b=\"http://b\""))
    }
}
