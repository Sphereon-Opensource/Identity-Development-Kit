/*
 * Copyright 2023-2026 Sphereon International B.V.
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

import nl.adaptivity.xmlutil.DomWriter
import nl.adaptivity.xmlutil.dom2.Document
import nl.adaptivity.xmlutil.writeCurrent
import nl.adaptivity.xmlutil.xmlStreaming

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
