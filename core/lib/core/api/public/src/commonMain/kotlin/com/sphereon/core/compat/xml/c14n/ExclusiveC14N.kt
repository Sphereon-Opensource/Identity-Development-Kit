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

package com.sphereon.core.compat.xml.c14n

import nl.adaptivity.xmlutil.dom2.Attr
import nl.adaptivity.xmlutil.dom2.Document
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.Node
import nl.adaptivity.xmlutil.dom2.NodeType
import nl.adaptivity.xmlutil.dom2.ProcessingInstruction

/**
 * Exclusive XML Canonicalization (exc-c14n) without comments.
 *
 * Implements W3C Exclusive XML Canonicalization 1.0:
 * https://www.w3.org/TR/xml-exc-c14n/
 *
 * Used for XML Signature verification — canonicalizes SignedInfo and
 * Reference content before digest/signature computation.
 */
object ExclusiveC14N {
    private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"
    private const val XML_NS_URI = "http://www.w3.org/XML/1998/namespace"

    /**
     * Canonicalize a DOM element using Exclusive XML Canonicalization (exc-c14n).
     *
     * @param element The element to canonicalize
     * @param inclusiveNamespacePrefixes Optional prefixes to treat as inclusive
     *        (from InclusiveNamespaces PrefixList). Use "#default" for the default namespace.
     * @param excludeNode Optional node to exclude from output (used for enveloped-signature transform)
     * @return UTF-8 encoded canonical form
     */
    fun canonicalize(
        element: Element,
        inclusiveNamespacePrefixes: Set<String> = emptySet(),
        excludeNode: Node? = null,
    ): ByteArray {
        val output = StringBuilder()
        processElement(element, output, mutableMapOf(), inclusiveNamespacePrefixes, excludeNode)
        return output.toString().encodeToByteArray()
    }

    /**
     * Canonicalize a DOM subtree. If the node is a Document, canonicalizes
     * the document element. If it's an Element, canonicalizes that element.
     */
    fun canonicalizeSubtree(
        node: Node,
        inclusiveNamespacePrefixes: Set<String> = emptySet(),
        excludeNode: Node? = null,
    ): ByteArray {
        return when (node) {
            is Document -> {
                val docElem =
                    node.getDocumentElement()
                        ?: return ByteArray(0)
                canonicalize(docElem, inclusiveNamespacePrefixes, excludeNode)
            }

            is Element -> {
                canonicalize(node, inclusiveNamespacePrefixes, excludeNode)
            }

            else -> {
                node.getTextContent()?.let { escapeTextContent(it).encodeToByteArray() }
                    ?: ByteArray(0)
            }
        }
    }

    private fun processElement(
        element: Element,
        output: StringBuilder,
        renderedNamespaces: Map<String, String>,
        inclusivePrefixes: Set<String>,
        excludeNode: Node?,
    ) {
        val elemPrefix = element.getPrefix() ?: ""
        val elemNsUri = element.getNamespaceURI() ?: ""

        // 1. Collect visibly utilized namespace prefix-URI pairs
        val utilizedNs = mutableMapOf<String, String>()

        // Element's own namespace is visibly utilized
        if (elemPrefix != "xml") {
            utilizedNs[elemPrefix] = elemNsUri
        }

        // Collect non-namespace attributes and their namespace prefixes
        val regularAttrs = mutableListOf<Attr>()
        for (attrNode in element.getAttributes()) {
            val attr = attrNode as Attr
            if (isNamespaceDeclaration(attr)) {
                continue
            }
            regularAttrs.add(attr)
            val attrPrefix = attr.getPrefix()
            if (!attrPrefix.isNullOrEmpty() && attrPrefix != "xml") {
                utilizedNs[attrPrefix] = attr.getNamespaceURI() ?: ""
            }
        }

        // Handle inclusive namespace prefixes (from InclusiveNamespaces PrefixList)
        for (inclPrefix in inclusivePrefixes) {
            val p =
                if (inclPrefix == "#default") {
                    ""
                } else {
                    inclPrefix
                }
            if (p !in utilizedNs && p != "xml") {
                val uri = element.lookupNamespaceURI(p)
                if (uri != null) {
                    utilizedNs[p] = uri
                }
            }
        }

        // 2. Determine which namespace declarations to output
        val nsToOutput = mutableListOf<Pair<String, String>>()
        for ((prefix, uri) in utilizedNs) {
            val renderedUri = renderedNamespaces[prefix] ?: ""
            if (renderedUri != uri) {
                nsToOutput.add(prefix to uri)
            }
        }

        // Sort: default namespace first (empty prefix sorts first), then by prefix
        nsToOutput.sortBy { it.first }

        // 3. Build qualified element name
        val qName =
            if (elemPrefix.isNotEmpty()) {
                "$elemPrefix:${element.getLocalName()}"
            } else {
                element.getLocalName()
            }

        output.append('<').append(qName)

        // 4. Output namespace declarations
        for ((prefix, uri) in nsToOutput) {
            if (prefix.isEmpty()) {
                output.append(" xmlns=\"").append(escapeAttributeValue(uri)).append('"')
            } else {
                output
                    .append(" xmlns:")
                    .append(prefix)
                    .append("=\"")
                    .append(escapeAttributeValue(uri))
                    .append('"')
            }
        }

        // 5. Output attributes sorted by namespace URI then local name
        regularAttrs.sortWith(
            compareBy<Attr> { it.getNamespaceURI() ?: "" }
                .thenBy { it.getLocalName() ?: it.getName() },
        )
        for (attr in regularAttrs) {
            val attrPrefix = attr.getPrefix()
            val attrQName =
                if (!attrPrefix.isNullOrEmpty()) {
                    "$attrPrefix:${attr.getLocalName()}"
                } else {
                    attr.getLocalName() ?: attr.getName()
                }
            output
                .append(' ')
                .append(attrQName)
                .append("=\"")
                .append(escapeAttributeValue(attr.getValue()))
                .append('"')
        }

        output.append('>')

        // 6. Update namespace context for children
        val childNs = renderedNamespaces.toMutableMap()
        for ((prefix, uri) in nsToOutput) {
            childNs[prefix] = uri
        }

        // 7. Process children in document order
        for (child in element.getChildNodes()) {
            if (excludeNode != null && isSameNode(child, excludeNode)) {
                continue
            }
            when (child.nodetype) {
                NodeType.ELEMENT_NODE -> {
                    processElement(
                        child as Element,
                        output,
                        childNs,
                        inclusivePrefixes,
                        excludeNode,
                    )
                }

                NodeType.TEXT_NODE, NodeType.CDATA_SECTION_NODE -> {
                    child.getTextContent()?.let { output.append(escapeTextContent(it)) }
                }

                NodeType.PROCESSING_INSTRUCTION_NODE -> {
                    val pi = child as ProcessingInstruction
                    val piData = pi.getData()
                    output.append("<?").append(pi.getTarget())
                    if (piData.isNotEmpty()) {
                        output.append(' ').append(piData)
                    }
                    output.append("?>")
                }

                // COMMENT_NODE: omitted in c14n without comments
                else -> { /* skip */ }
            }
        }

        // 8. End tag (never self-closing in canonical XML)
        output.append("</").append(qName).append('>')
    }

    /**
     * Check if two nodes represent the same DOM node.
     * Uses reference equality first, then falls back to structural comparison
     * for DOM implementations that use wrapper objects.
     */
    private fun isSameNode(
        a: Node,
        b: Node,
    ): Boolean {
        if (a === b) {
            return true
        }
        if (a == b) {
            return true
        }
        // Structural comparison for wrapped DOM nodes
        if (a.nodetype != b.nodetype) {
            return false
        }
        if (a is Element && b is Element) {
            return a.getNamespaceURI() == b.getNamespaceURI() &&
                a.getLocalName() == b.getLocalName() &&
                a.getTextContent() == b.getTextContent() &&
                a.getParentNode()?.getNodeName() == b.getParentNode()?.getNodeName()
        }
        return false
    }

    private fun isNamespaceDeclaration(attr: Attr): Boolean {
        val ns = attr.getNamespaceURI()
        if (ns == XMLNS_URI) {
            return true
        }
        val name = attr.getName()
        return name == "xmlns" || name.startsWith("xmlns:")
    }

    private fun escapeTextContent(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '\r' -> sb.append("&#xD;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeAttributeValue(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '"' -> sb.append("&quot;")
                '\t' -> sb.append("&#x9;")
                '\n' -> sb.append("&#xA;")
                '\r' -> sb.append("&#xD;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
