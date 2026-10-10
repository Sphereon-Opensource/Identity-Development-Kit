/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.core.compat.xml

import nl.adaptivity.xmlutil.dom2.Document
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.Node

/** Elements found by a namespace-aware lookup, in document order. */
class XmlElementList internal constructor(private val elements: List<Element>) : Iterable<Element> {
    val length: Int get() = elements.size

    operator fun get(index: Int): Element = elements[index]

    override fun iterator(): Iterator<Element> = elements.iterator()
}

/**
 * The descendant elements of this element (not the element itself) with [namespace] and [localName], in document
 * order, like DOM `getElementsByTagNameNS`. `"*"` matches any namespace or local name, and a null or empty
 * [namespace] matches elements without one.
 *
 * Use this instead of xmlutil's `getElementsByTagNameNS`: on Kotlin/Native (xmlutil 0.91.3) that lookup returns no
 * elements even when the parsed elements carry the requested namespace and local name.
 */
fun Element.elementsByTagNameNS(namespace: String?, localName: String): XmlElementList =
    XmlElementList(mutableListOf<Element>().also { collectDescendants(this, namespace, localName, it) })

/** Like [Element.elementsByTagNameNS], over the whole document including its document element. */
fun Document.elementsByTagNameNS(namespace: String?, localName: String): XmlElementList {
    val found = mutableListOf<Element>()
    getDocumentElement()?.let { root ->
        if (matches(root, namespace, localName)) found += root
        collectDescendants(root, namespace, localName, found)
    }
    return XmlElementList(found)
}

private fun collectDescendants(parent: Node, namespace: String?, localName: String, found: MutableList<Element>) {
    val children = parent.getChildNodes()
    for (index in 0 until children.getLength()) {
        val child = children.item(index) as? Element ?: continue
        if (matches(child, namespace, localName)) found += child
        collectDescendants(child, namespace, localName, found)
    }
}

private fun matches(element: Element, namespace: String?, localName: String): Boolean {
    val namespaceMatches = namespace == "*" || (element.getNamespaceURI() ?: "") == (namespace ?: "")
    val localNameMatches = localName == "*" || (element.getLocalName() ?: element.getTagName()) == localName
    return namespaceMatches && localNameMatches
}
