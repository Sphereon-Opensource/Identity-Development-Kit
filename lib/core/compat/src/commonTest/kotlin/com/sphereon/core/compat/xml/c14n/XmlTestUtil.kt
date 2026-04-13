package com.sphereon.core.compat.xml.c14n

import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.dom2.*

/**
 * Ensures a DOM implementation is available on the current platform.
 * On JVM and Native, this is a no-op (DOM is always available).
 * On JS/Node.js, this polyfills globalThis.document using jsdom.
 */
expect fun ensureDomAvailable()

/**
 * Parse an XML string into a DOM Document.
 * Handles platform-specific DOM initialization automatically.
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun parseXmlToDocument(xml: String): Document {
    ensureDomAvailable()
    val reader = xmlStreaming.newReader(xml)
    val writer = DomWriter()
    while (reader.hasNext()) {
        reader.next()
        reader.writeCurrent(writer)
    }
    return writer.target
}
