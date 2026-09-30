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

package com.sphereon.catalog.eu.parser

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

internal class XmlAttr(
    val ns: String,
    val prefix: String,
    val name: String,
    val value: String,
)

/**
 * A minimal element tree. [content] keeps text and child elements in document order with untrimmed text so a
 * subtree can be serialized back faithfully; [text] is the trimmed concatenation used for value elements.
 */
internal class XmlNode(
    val ns: String,
    val prefix: String,
    val name: String,
    val attrs: List<XmlAttr>,
    val nsDecls: List<Pair<String, String>>,
    val content: List<Any>,
) {
    val children: List<XmlNode> = content.filterIsInstance<XmlNode>()

    val text: String = content.filterIsInstance<String>().joinToString("").trim()

    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    fun child(
        ns: String,
        name: String,
    ): XmlNode? = children.firstOrNull { it.name == name && it.ns == ns }

    fun all(name: String): List<XmlNode> = children.filter { it.name == name }

    fun attr(name: String): String? = attrs.firstOrNull { it.name == name && it.ns.isEmpty() }?.value

    fun lang(): String? = attrs.firstOrNull { it.name == "lang" && (it.ns == XML_NAMESPACE || it.prefix == "xml") }?.value

    fun childText(name: String): String? = child(name)?.text

    fun toXml(): String {
        val sb = StringBuilder()
        write(sb)
        return sb.toString()
    }

    private fun qualified(
        prefix: String,
        name: String,
    ) = if (prefix.isEmpty()) name else "$prefix:$name"

    private fun write(sb: StringBuilder) {
        sb.append('<').append(qualified(prefix, name))
        for ((p, uri) in nsDecls) {
            sb.append(' ').append(if (p.isEmpty()) "xmlns" else "xmlns:$p").append("=\"").append(escape(uri)).append('"')
        }
        for (attr in attrs) {
            val p = if (attr.ns == XML_NAMESPACE && attr.prefix.isEmpty()) "xml" else attr.prefix
            sb.append(' ').append(qualified(p, attr.name)).append("=\"").append(escape(attr.value)).append('"')
        }
        if (content.isEmpty()) {
            sb.append("/>")
            return
        }
        sb.append('>')
        for (part in content) {
            if (part is XmlNode) part.write(sb) else sb.append(escape(part as String))
        }
        sb.append("</").append(qualified(prefix, name)).append('>')
    }

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"
    }
}

private class NodeBuilder(
    val ns: String,
    val prefix: String,
    val name: String,
    val attrs: List<XmlAttr>,
    val nsDecls: List<Pair<String, String>>,
) {
    val content = mutableListOf<Any>()

    fun addText(text: String) {
        val last = content.lastOrNull()
        if (last is String) content[content.lastIndex] = last + text else content.add(text)
    }

    fun build(): XmlNode = XmlNode(ns, prefix, name, attrs, nsDecls, content)
}

internal fun parseXmlDocument(xml: String): XmlNode {
    val reader = xmlStreaming.newReader(xml.removePrefix("﻿"))
    val stack = ArrayDeque<NodeBuilder>()
    var root: XmlNode? = null
    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> {
                val attrs =
                    (0 until reader.attributeCount).map { i ->
                        XmlAttr(reader.getAttributeNamespace(i), reader.getAttributePrefix(i), reader.getAttributeLocalName(i), reader.getAttributeValue(i))
                    }
                val decls = reader.namespaceDecls.map { it.prefix to it.namespaceURI }
                stack.addLast(NodeBuilder(reader.namespaceURI, reader.prefix, reader.localName, attrs, decls))
            }

            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> stack.lastOrNull()?.addText(reader.text)

            EventType.END_ELEMENT -> {
                val done = stack.removeLast().build()
                val parent = stack.lastOrNull()
                if (parent == null) root = done else parent.content.add(done)
            }

            else -> {}
        }
    }
    return root ?: throw IllegalArgumentException("Document has no root element")
}
