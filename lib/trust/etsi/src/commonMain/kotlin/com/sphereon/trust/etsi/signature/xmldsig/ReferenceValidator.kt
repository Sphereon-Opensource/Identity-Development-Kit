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
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.compat.xml.c14n.ExclusiveC14N
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.trust.etsi.signature.xades.SIGNED_PROPERTIES_TYPE
import com.sphereon.trust.etsi.signature.xades.XMLDSIG_NS
import nl.adaptivity.xmlutil.dom2.Document
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.Node
import nl.adaptivity.xmlutil.dom2.length

/**
 * Validates XML Signature References within a SignedInfo element.
 *
 * For each `<ds:Reference>`, resolves the URI, applies transforms
 * (enveloped-signature, exc-c14n), computes the digest, and compares
 * against the declared DigestValue.
 */
object ReferenceValidator {
    data class ReferenceResult(
        val uri: String,
        val type: String?,
        val digestAlgorithm: String,
        val valid: Boolean,
        val errorMessage: String? = null,
    )

    private val DIGEST_ALG_MAP =
        mapOf(
            "http://www.w3.org/2001/04/xmlenc#sha256" to DigestAlg.SHA256,
            "http://www.w3.org/2001/04/xmldsig-more#sha256" to DigestAlg.SHA256,
            "http://www.w3.org/2001/04/xmldsig-more#sha384" to DigestAlg.SHA384,
            "http://www.w3.org/2001/04/xmlenc#sha512" to DigestAlg.SHA512,
            "http://www.w3.org/2001/04/xmldsig-more#sha512" to DigestAlg.SHA512,
            "http://www.w3.org/2000/09/xmldsig#sha1" to DigestAlg.SHA256, // Fallback: SHA-1 mapped to SHA-256 (unsupported but attempt)
        )

    private const val TRANSFORM_ENVELOPED = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
    private const val TRANSFORM_EXC_C14N = "http://www.w3.org/2001/10/xml-exc-c14n#"
    private const val TRANSFORM_C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
    private const val TRANSFORM_C14N_COMMENTS = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments"
    private const val TRANSFORM_XPATH_FILTER2 = "http://www.w3.org/2002/06/xmldsig-filter2"

    /**
     * Validate all `<ds:Reference>` elements in a SignedInfo.
     *
     * @param signedInfo The `<ds:SignedInfo>` element
     * @param document The full XML document
     * @param signatureElement The `<ds:Signature>` element (needed for enveloped-signature transform)
     * @return List of validation results, one per Reference
     */
    fun validateReferences(
        signedInfo: Element,
        document: Document,
        signatureElement: Element,
    ): List<ReferenceResult> {
        val references = signedInfo.getElementsByTagNameNS(XMLDSIG_NS, "Reference")
        val results = mutableListOf<ReferenceResult>()

        for (refNode in references) {
            if (refNode !is Element) {
                continue
            }
            results.add(validateReference(refNode, document, signatureElement))
        }

        return results
    }

    private fun validateReference(
        reference: Element,
        document: Document,
        signatureElement: Element,
    ): ReferenceResult {
        val uri = reference.getAttribute("URI") ?: ""
        val type = reference.getAttribute("Type")

        try {
            // Extract expected digest
            val digestMethodEl =
                firstChildElement(reference, XMLDSIG_NS, "DigestMethod")
                    ?: return ReferenceResult(uri, type, "", false, "Missing DigestMethod")
            val digestAlgUri =
                digestMethodEl.getAttribute("Algorithm")
                    ?: return ReferenceResult(uri, type, "", false, "Missing DigestMethod Algorithm")

            val digestValueEl =
                firstChildElement(reference, XMLDSIG_NS, "DigestValue")
                    ?: return ReferenceResult(uri, type, digestAlgUri, false, "Missing DigestValue")
            val expectedDigest =
                digestValueEl
                    .getTextContent()
                    ?.trim()
                    ?.replace("\\s".toRegex(), "")
                    ?: return ReferenceResult(uri, type, digestAlgUri, false, "Empty DigestValue")

            val expectedBytes = expectedDigest.decodeFrom(Encoding.BASE64)

            // Resolve the referenced content
            val content =
                resolveReference(uri, type, document, signatureElement)
                    ?: return ReferenceResult(uri, type, digestAlgUri, false, "Could not resolve reference URI: $uri")

            // Apply transforms
            val transforms = extractTransforms(reference)
            val transformedContent = applyTransforms(content, transforms, document, signatureElement)

            // Compute digest
            val digestAlg =
                DIGEST_ALG_MAP[digestAlgUri]
                    ?: return ReferenceResult(uri, type, digestAlgUri, false, "Unsupported digest algorithm: $digestAlgUri")

            val computedDigest = hash(transformedContent, digestAlg)

            // Compare
            val valid = computedDigest.contentEquals(expectedBytes)
            return if (valid) {
                ReferenceResult(uri, type, digestAlgUri, true)
            } else {
                ReferenceResult(
                    uri,
                    type,
                    digestAlgUri,
                    false,
                    "Digest mismatch: expected ${expectedBytes.encodeTo(Encoding.BASE64)}, " +
                        "computed ${computedDigest.encodeTo(Encoding.BASE64)}",
                )
            }
        } catch (expected: Exception) {
            return ReferenceResult(uri, type, "", false, "Reference validation error: ${expected.message}")
        }
    }

    private fun resolveReference(
        uri: String,
        type: String?,
        document: Document,
        signatureElement: Element,
    ): Node? =
        when {
            // Empty URI = entire document (for enveloped signatures)
            uri.isEmpty() -> {
                document.getDocumentElement()
            }

            // Fragment reference — find element by Id
            uri.startsWith("#") -> {
                val id = uri.substring(1)
                findElementById(document, id, type)
            }

            else -> {
                null
            } // External references not supported
        }

    private fun findElementById(
        document: Document,
        id: String,
        type: String?,
    ): Element? {
        val root = document.getDocumentElement() ?: return null
        return findElementByIdRecursive(root, id)
    }

    private fun findElementByIdRecursive(
        element: Element,
        id: String,
    ): Element? {
        // Check common id attributes
        val elemId =
            element.getAttribute("Id")
                ?: element.getAttribute("id")
                ?: element.getAttribute("ID")
        if (elemId == id) {
            return element
        }

        // Recurse into children
        for (child in element.getChildNodes()) {
            if (child is Element) {
                val found = findElementByIdRecursive(child, id)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private data class Transform(
        val algorithm: String,
        val inclusiveNamespacePrefixes: Set<String> = emptySet(),
        /** For XPath Filter 2.0: the filter mode (subtract, intersect, union) */
        val xpathFilterMode: String? = null,
        /** For XPath Filter 2.0: the XPath expression */
        val xpathExpression: String? = null,
    )

    private fun extractTransforms(reference: Element): List<Transform> {
        val transformsEl =
            firstChildElement(reference, XMLDSIG_NS, "Transforms")
                ?: return emptyList()
        val transformList = transformsEl.getElementsByTagNameNS(XMLDSIG_NS, "Transform")
        val transforms = mutableListOf<Transform>()

        for (node in transformList) {
            if (node !is Element) {
                continue
            }
            val algorithm = node.getAttribute("Algorithm") ?: continue

            // Check for InclusiveNamespaces PrefixList (used with exc-c14n)
            val inclPrefixes = mutableSetOf<String>()
            val inclNsEl = firstChildElement(node, TRANSFORM_EXC_C14N, "InclusiveNamespaces")
            if (inclNsEl != null) {
                val prefixList = inclNsEl.getAttribute("PrefixList")
                if (!prefixList.isNullOrBlank()) {
                    inclPrefixes.addAll(prefixList.split("\\s+".toRegex()))
                }
            }

            // Check for XPath Filter 2.0 elements
            var xpathFilterMode: String? = null
            var xpathExpression: String? = null
            if (algorithm == TRANSFORM_XPATH_FILTER2) {
                val xpathEl = firstChildElement(node, TRANSFORM_XPATH_FILTER2, "XPath")
                if (xpathEl != null) {
                    xpathFilterMode = xpathEl.getAttribute("Filter")
                    xpathExpression = xpathEl.getTextContent()?.trim()
                }
            }

            transforms.add(Transform(algorithm, inclPrefixes, xpathFilterMode, xpathExpression))
        }

        return transforms
    }

    private fun applyTransforms(
        content: Node,
        transforms: List<Transform>,
        document: Document,
        signatureElement: Element,
    ): ByteArray {
        var hasEnvelopedTransform = false
        var hasCanonicalization = false
        var inclusivePrefixes = emptySet<String>()

        for (transform in transforms) {
            when (transform.algorithm) {
                TRANSFORM_ENVELOPED -> {
                    hasEnvelopedTransform = true
                }

                TRANSFORM_XPATH_FILTER2 -> {
                    // XPath Filter 2.0 with Filter="subtract" and XPath "/descendant::ds:Signature"
                    // is functionally equivalent to the enveloped-signature transform — it excludes
                    // the Signature element from the canonicalized content.
                    if (transform.xpathFilterMode == "subtract" &&
                        transform.xpathExpression?.contains("Signature") == true
                    ) {
                        hasEnvelopedTransform = true
                    }
                }

                TRANSFORM_EXC_C14N -> {
                    hasCanonicalization = true
                    inclusivePrefixes = transform.inclusiveNamespacePrefixes
                }

                TRANSFORM_C14N, TRANSFORM_C14N_COMMENTS -> {
                    hasCanonicalization = true
                }
            }
        }

        return if (content is Element) {
            val excludeNode =
                if (hasEnvelopedTransform) {
                    signatureElement
                } else {
                    null
                }
            if (hasCanonicalization) {
                ExclusiveC14N.canonicalize(content, inclusivePrefixes, excludeNode)
            } else if (hasEnvelopedTransform) {
                // Enveloped without explicit c14n — still apply exc-c14n
                ExclusiveC14N.canonicalize(content, emptySet(), excludeNode)
            } else {
                ExclusiveC14N.canonicalize(content, emptySet())
            }
        } else if (content is Document) {
            val root = content.getDocumentElement()
            if (root != null) {
                val excludeNode =
                    if (hasEnvelopedTransform) {
                        signatureElement
                    } else {
                        null
                    }
                ExclusiveC14N.canonicalize(root, inclusivePrefixes, excludeNode)
            } else {
                ByteArray(0)
            }
        } else {
            content.getTextContent()?.encodeToByteArray() ?: ByteArray(0)
        }
    }

    private fun firstChildElement(
        parent: Element,
        nsUri: String,
        localName: String,
    ): Element? {
        val nodes = parent.getElementsByTagNameNS(nsUri, localName)
        return if (nodes.length > 0) {
            nodes[0] as? Element
        } else {
            null
        }
    }
}
