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

import com.sphereon.trust.etsi.signature.xades.SIGNED_PROPERTIES_TYPE
import com.sphereon.trust.etsi.signature.xades.XADES_NS
import com.sphereon.trust.etsi.signature.xades.XMLDSIG_NS
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.length

/**
 * Signature wrapping defence shared by every enveloped XML signature gate (trusted lists, LoTE, catalogues).
 *
 * A cryptographically valid signature only proves that some bytes were signed. For an enveloped document signature it
 * must also be the only signature, sit directly under the root element and cover the whole document, otherwise an
 * attacker can transplant a genuine signature (or the whole signed document) into a document they control.
 */
object EnvelopedSignatureCoverage {
    private const val DOCUMENT_NODE_TYPE: Short = 9
    private const val TRANSFORM_ENVELOPED = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
    private const val TRANSFORM_XPATH_FILTER2 = "http://www.w3.org/2002/06/xmldsig-filter2"

    data class Coverage(
        /** Number of `ds:Signature` elements anywhere below the root. */
        val signatureCount: Int,
        /** The validated `ds:Signature` is a direct child of the document root element. */
        val signatureIsRootChild: Boolean,
        /** A valid enveloped Reference with `URI=""` (or `#id` of the root's own Id) covers the whole document. */
        val documentReferenceValid: Boolean,
        /** Two elements carry the same `Id`/`id`/`ID`, which lets a `#id` Reference be redirected. */
        val duplicateIds: Boolean,
        /** A valid Reference of Type SignedProperties points at the signature's `xades:SignedProperties`. */
        val signedPropertiesCovered: Boolean,
    )

    fun inspect(
        root: Element,
        signatureElement: Element,
        signedInfo: Element,
        referenceResults: List<ReferenceValidator.ReferenceResult>,
        signatureCount: Int,
    ): Coverage {
        val parent = signatureElement.getParentNode()
        return Coverage(
            signatureCount = signatureCount,
            signatureIsRootChild = parent is Element && parent.getParentNode()?.getNodeType() == DOCUMENT_NODE_TYPE,
            documentReferenceValid = hasValidEnvelopedDocumentReference(signedInfo, referenceResults, root),
            duplicateIds = hasDuplicateIds(root),
            signedPropertiesCovered = isSignedPropertiesCovered(signatureElement, referenceResults),
        )
    }

    /**
     * The reasons the signature does not cover the whole document; empty when it does.
     *
     * @param requireSignedPropertiesCovered when the signature carries XAdES properties they must be signed too.
     */
    fun violations(
        coverage: Coverage,
        requireSignedPropertiesCovered: Boolean,
        subject: String = "document",
    ): List<String> {
        val errors = mutableListOf<String>()
        if (coverage.signatureCount != 1) {
            errors.add("The $subject must contain exactly one ds:Signature but contains ${coverage.signatureCount}")
        }
        if (!coverage.signatureIsRootChild) {
            errors.add("The ds:Signature must be a direct child of the $subject root element")
        }
        if (coverage.duplicateIds) {
            errors.add("The $subject contains duplicate Id attributes")
        }
        if (requireSignedPropertiesCovered && !coverage.signedPropertiesCovered) {
            errors.add("The xades:SignedProperties are not covered by a valid Reference of Type SignedProperties")
        }
        if (!coverage.documentReferenceValid) {
            errors.add("The ds:Signature has no valid enveloped ds:Reference with URI=\"\" covering the whole $subject")
        }
        return errors
    }

    /** The value of the `Id`, `id` or `ID` attribute; the DOM answers an empty string for an attribute that is absent. */
    fun idOf(element: Element): String? =
        listOf("Id", "id", "ID").firstNotNullOfOrNull { name -> element.getAttribute(name)?.takeIf { it.isNotEmpty() } }

    fun hasDuplicateIds(root: Element): Boolean {
        val seen = mutableSetOf<String>()

        fun walk(element: Element): Boolean {
            val id = idOf(element)
            if (id != null && !seen.add(id)) {
                return true
            }
            for (child in element.getChildNodes()) {
                if (child is Element && walk(child)) {
                    return true
                }
            }
            return false
        }
        return walk(root)
    }

    private fun hasValidEnvelopedDocumentReference(
        signedInfo: Element,
        referenceResults: List<ReferenceValidator.ReferenceResult>,
        root: Element,
    ): Boolean {
        if (referenceResults.isEmpty() || referenceResults.any { !it.valid }) {
            return false
        }
        val rootId = idOf(root)
        for (child in signedInfo.getChildNodes()) {
            if (child !is Element || child.getLocalName() != "Reference" || child.getNamespaceURI() != XMLDSIG_NS) {
                continue
            }
            if (!child.hasAttribute("URI")) {
                continue
            }
            val uri = child.getAttribute("URI") ?: continue
            val coversRoot = uri == "" || (uri.startsWith("#") && rootId != null && uri.substring(1) == rootId)
            if (!coversRoot) {
                continue
            }
            val transforms = directChild(child, "Transforms") ?: continue
            for (transform in transforms.getChildNodes()) {
                if (transform is Element && transform.getLocalName() == "Transform" && isEnvelopedTransform(transform)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isEnvelopedTransform(transform: Element): Boolean {
        val algorithm = transform.getAttribute("Algorithm")
        if (algorithm == TRANSFORM_ENVELOPED) {
            return true
        }
        if (algorithm != TRANSFORM_XPATH_FILTER2) {
            return false
        }
        val xpath = transform.getElementsByTagNameNS(TRANSFORM_XPATH_FILTER2, "XPath")
        val element = (if (xpath.length > 0) xpath[0] else null) as? Element ?: return false
        return element.getAttribute("Filter") == "subtract" && element.getTextContent()?.contains("Signature") == true
    }

    private fun isSignedPropertiesCovered(
        signatureElement: Element,
        referenceResults: List<ReferenceValidator.ReferenceResult>,
    ): Boolean {
        val nodes = signatureElement.getElementsByTagNameNS(XADES_NS, "SignedProperties")
        if (nodes.length != 1) {
            return false
        }
        val id = (nodes[0] as? Element)?.let { idOf(it) } ?: return false
        return referenceResults.any { it.valid && it.type == SIGNED_PROPERTIES_TYPE && it.uri == "#$id" }
    }

    private fun directChild(
        parent: Element,
        localName: String,
    ): Element? =
        parent.getChildNodes().firstOrNull {
            it is Element && it.getLocalName() == localName && it.getNamespaceURI() == XMLDSIG_NS
        } as? Element
}
