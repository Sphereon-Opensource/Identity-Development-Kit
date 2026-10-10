package com.sphereon.core.compat.xml

import com.sphereon.core.compat.xml.c14n.parseXmlToDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class XmlElementLookupTest {
    private val xml =
        """<root xmlns="http://default" xmlns:ds="http://www.w3.org/2000/09/xmldsig#">""" +
            """<ds:Signature Id="outer"><ds:SignedInfo/><ds:Object><ds:Signature Id="inner"/></ds:Object></ds:Signature>""" +
            """<item/><plain xmlns=""/></root>"""

    @Test
    fun prefixedNamespaceLookupFindsDescendantsInDocumentOrder() {
        val root = parseXmlToDocument(xml).getDocumentElement()!!

        val signatures = root.elementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature")

        assertEquals(listOf("outer", "inner"), signatures.map { it.getAttribute("Id") })
        assertEquals(1, signatures[0].elementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").length)
    }

    @Test
    fun defaultEmptyAndWildcardNamespacesAreDistinguished() {
        val document = parseXmlToDocument(xml)
        val root = document.getDocumentElement()!!

        assertEquals(1, root.elementsByTagNameNS("http://default", "item").length)
        assertEquals(0, root.elementsByTagNameNS(null, "item").length)
        assertEquals(1, root.elementsByTagNameNS(null, "plain").length)
        assertEquals(1, root.elementsByTagNameNS("*", "SignedInfo").length)
        assertEquals(6, root.elementsByTagNameNS("*", "*").length)
    }

    @Test
    fun documentLookupIncludesTheDocumentElementButElementLookupDoesNot() {
        val document = parseXmlToDocument(xml)

        assertEquals(1, document.elementsByTagNameNS("http://default", "root").length)
        assertEquals(0, document.getDocumentElement()!!.elementsByTagNameNS("http://default", "root").length)
    }
}
