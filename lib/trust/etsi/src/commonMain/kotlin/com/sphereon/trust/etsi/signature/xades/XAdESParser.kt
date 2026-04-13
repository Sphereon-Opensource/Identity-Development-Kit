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

package com.sphereon.trust.etsi.signature.xades

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.length
import kotlin.time.Instant

/**
 * Parser for XAdES QualifyingProperties from XML Signature `<ds:Object>` elements.
 *
 * Supports both XAdES 1.3.2 and 1.4.1 namespaces. Parses
 * SignedProperties (SigningTime, SigningCertificateV2, DataObjectFormat)
 * and UnsignedProperties (SignatureTimestamps for XAdES-T).
 */
object XAdESParser {
    /** All recognized XAdES namespaces */
    private val XADES_NAMESPACES =
        setOf(
            XADES_NS,
            XADES_141_NS,
            "http://uri.etsi.org/01903/v1.1.1#",
            "http://uri.etsi.org/01903/v1.2.2#",
        )

    /**
     * Parse QualifyingProperties from a Signature element.
     *
     * Searches `<ds:Object>` children for `<xades:QualifyingProperties>`.
     *
     * @param signatureElement The `<ds:Signature>` element
     * @return Parsed QualifyingProperties, or null if none found
     */
    fun parse(signatureElement: Element): QualifyingProperties? {
        // Look for Object elements in the Signature
        val objectNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "Object")

        for (objNode in objectNodes) {
            if (objNode !is Element) {
                continue
            }

            // Look for QualifyingProperties in any recognized XAdES namespace
            for (xadesNs in XADES_NAMESPACES) {
                val qpNodes = objNode.getElementsByTagNameNS(xadesNs, "QualifyingProperties")
                if (qpNodes.length > 0) {
                    val qpElement = qpNodes[0] as? Element ?: continue
                    return parseQualifyingProperties(qpElement, xadesNs)
                }
            }
        }

        return null
    }

    /**
     * Find the SignedProperties element within a Signature.
     * Used for Reference validation (the SignedProperties Reference).
     */
    fun findSignedPropertiesElement(signatureElement: Element): Element? {
        val objectNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "Object")
        for (objNode in objectNodes) {
            if (objNode !is Element) {
                continue
            }
            for (xadesNs in XADES_NAMESPACES) {
                val spNodes = objNode.getElementsByTagNameNS(xadesNs, "SignedProperties")
                if (spNodes.length > 0) {
                    return spNodes[0] as? Element
                }
            }
        }
        return null
    }

    private fun parseQualifyingProperties(
        element: Element,
        ns: String,
    ): QualifyingProperties {
        val target = element.getAttribute("Target")
        val signedProperties =
            findChildElement(element, ns, "SignedProperties")?.let {
                parseSignedProperties(it, ns)
            }
        val unsignedProperties =
            findChildElement(element, ns, "UnsignedProperties")?.let {
                parseUnsignedProperties(it, ns)
            }

        return QualifyingProperties(
            target = target,
            signedProperties = signedProperties,
            unsignedProperties = unsignedProperties,
        )
    }

    private fun parseSignedProperties(
        element: Element,
        ns: String,
    ): SignedProperties {
        val id = element.getAttribute("Id")
        val signedSigProps =
            findChildElement(element, ns, "SignedSignatureProperties")?.let {
                parseSignedSignatureProperties(it, ns)
            }
        val signedDataObjProps =
            findChildElement(element, ns, "SignedDataObjectProperties")?.let {
                parseSignedDataObjectProperties(it, ns)
            }

        return SignedProperties(
            id = id,
            signedSignatureProperties = signedSigProps,
            signedDataObjectProperties = signedDataObjProps,
        )
    }

    private fun parseSignedSignatureProperties(
        element: Element,
        ns: String,
    ): SignedSignatureProperties {
        // Signing time
        val signingTime =
            findChildElement(element, ns, "SigningTime")?.let { el ->
                el.getTextContent()?.trim()?.let { text ->
                    try {
                        Instant.parse(text)
                    } catch (_: Exception) {
                        // Ignored: SigningTime value is not a valid ISO instant
                        null
                    }
                }
            }

        // SigningCertificateV2 (XAdES 1.3.2+)
        val signingCertV2 =
            findChildElement(element, ns, "SigningCertificateV2")?.let {
                parseCertDigests(it, ns, v2 = true)
            }

        // SigningCertificate (legacy v1)
        val signingCert =
            findChildElement(element, ns, "SigningCertificate")?.let {
                parseCertDigests(it, ns, v2 = false)
            }

        return SignedSignatureProperties(
            signingTime = signingTime,
            signingCertificateV2 = signingCertV2,
            signingCertificate = signingCert,
        )
    }

    private fun parseCertDigests(
        element: Element,
        ns: String,
        v2: Boolean,
    ): List<CertDigest> {
        val certs = mutableListOf<CertDigest>()
        val certNodes = element.getElementsByTagNameNS(ns, "Cert")

        for (certNode in certNodes) {
            if (certNode !is Element) {
                continue
            }

            val certDigestEl = findChildElement(certNode, ns, "CertDigest") ?: continue
            val digestMethodEl = findChildElement(certDigestEl, XMLDSIG_NS, "DigestMethod")
            val digestValueEl = findChildElement(certDigestEl, XMLDSIG_NS, "DigestValue")

            val digestAlgorithm = digestMethodEl?.getAttribute("Algorithm") ?: continue
            val digestValueText =
                digestValueEl
                    ?.getTextContent()
                    ?.trim()
                    ?.replace("\\s".toRegex(), "") ?: continue
            val digestValue = digestValueText.decodeFrom(Encoding.BASE64)

            var issuerSerialV2: ByteArray? = null
            var issuerName: String? = null
            var serialNumber: String? = null

            if (v2) {
                val issuerSerialEl = findChildElement(certNode, ns, "IssuerSerialV2")
                issuerSerialV2 =
                    issuerSerialEl
                        ?.getTextContent()
                        ?.trim()
                        ?.replace("\\s".toRegex(), "")
                        ?.let { it.decodeFrom(Encoding.BASE64) }
            } else {
                val issuerSerialEl = findChildElement(certNode, ns, "IssuerSerial")
                if (issuerSerialEl != null) {
                    issuerName =
                        findChildElement(issuerSerialEl, XMLDSIG_NS, "X509IssuerName")
                            ?.getTextContent()
                            ?.trim()
                    serialNumber =
                        findChildElement(issuerSerialEl, XMLDSIG_NS, "X509SerialNumber")
                            ?.getTextContent()
                            ?.trim()
                }
            }

            certs.add(
                CertDigest(
                    digestAlgorithm = digestAlgorithm,
                    digestValue = digestValue,
                    issuerSerialV2 = issuerSerialV2,
                    issuerName = issuerName,
                    serialNumber = serialNumber,
                ),
            )
        }

        return certs
    }

    private fun parseSignedDataObjectProperties(
        element: Element,
        ns: String,
    ): SignedDataObjectProperties {
        val formats = mutableListOf<DataObjectFormat>()
        val formatNodes = element.getElementsByTagNameNS(ns, "DataObjectFormat")

        for (formatNode in formatNodes) {
            if (formatNode !is Element) {
                continue
            }
            val objectRef = formatNode.getAttribute("ObjectReference")
            val mimeType =
                findChildElement(formatNode, ns, "MimeType")
                    ?.getTextContent()
                    ?.trim()

            formats.add(DataObjectFormat(objectReference = objectRef, mimeType = mimeType))
        }

        return SignedDataObjectProperties(dataObjectFormats = formats)
    }

    private fun parseUnsignedProperties(
        element: Element,
        ns: String,
    ): UnsignedProperties {
        val unsignedSigProps =
            findChildElement(element, ns, "UnsignedSignatureProperties")?.let {
                parseUnsignedSignatureProperties(it, ns)
            }
        return UnsignedProperties(unsignedSignatureProperties = unsignedSigProps)
    }

    private fun parseUnsignedSignatureProperties(
        element: Element,
        ns: String,
    ): UnsignedSignatureProperties {
        val timestamps = mutableListOf<SignatureTimestamp>()
        val tsNodes = element.getElementsByTagNameNS(ns, "SignatureTimeStamp")

        for (tsNode in tsNodes) {
            if (tsNode !is Element) {
                continue
            }
            val encapsulated = findChildElement(tsNode, ns, "EncapsulatedTimeStamp")
            val tsValue =
                encapsulated
                    ?.getTextContent()
                    ?.trim()
                    ?.replace("\\s".toRegex(), "")
            if (tsValue != null) {
                timestamps.add(SignatureTimestamp(tsValue.decodeFrom(Encoding.BASE64)))
            }
        }

        return UnsignedSignatureProperties(signatureTimestamps = timestamps)
    }

    private fun findChildElement(
        parent: Element,
        nsUri: String,
        localName: String,
    ): Element? {
        // Search direct children first, then fall back to getElementsByTagNameNS
        for (child in parent.getChildNodes()) {
            if (child is Element &&
                child.getLocalName() == localName &&
                child.getNamespaceURI() == nsUri
            ) {
                return child
            }
        }
        // Fallback: search all descendants
        val nodes = parent.getElementsByTagNameNS(nsUri, localName)
        return if (nodes.length > 0) {
            nodes[0] as? Element
        } else {
            null
        }
    }
}
