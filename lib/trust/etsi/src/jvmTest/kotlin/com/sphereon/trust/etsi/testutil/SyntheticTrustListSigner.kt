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

package com.sphereon.trust.etsi.testutil

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import javax.xml.crypto.dom.DOMStructure
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * A throw-away signer: a fresh RSA key and self-signed certificate that produce enveloped XAdES-BASELINE-B style
 * signatures (RSA-SHA512, exc-c14n, SHA-512 digests) like the live EU trusted lists. The certificate subject is a
 * random test value.
 */
class SyntheticTrustListSigner(
    commonName: String = "trust-list-test-signer-${System.nanoTime()}",
) {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certificate: X509Certificate

    init {
        val name = X500Name("CN=$commonName")
        val now = System.currentTimeMillis()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val builder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - DAY), Date(now + YEAR), name, keyPair.public)
        certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    val certificateDer: ByteArray get() = certificate.encoded
    val certificateBase64: String get() = Base64.getEncoder().encodeToString(certificate.encoded)

    /**
     * Replaces any existing `ds:Signature` of [xml] with a new enveloped signature over the whole document and over
     * the XAdES SignedProperties.
     */
    fun sign(
        xml: ByteArray,
        coverDocument: Boolean = true,
        documentReferenceUri: String = "",
        coverSignedProperties: Boolean = true,
        includeSigningCertificateV2: Boolean = true,
    ): ByteArray {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val root = document.documentElement
        val existing = document.getElementsByTagNameNS(DSIG, "Signature")
        while (existing.length > 0) {
            existing.item(0).parentNode.removeChild(existing.item(0))
        }

        val signatureId = "sig-test"
        val signedPropsId = "xades-signed-props"
        val qualifying = qualifyingProperties(document, signatureId, signedPropsId, includeSigningCertificateV2)
        val signedProps = qualifying.getElementsByTagNameNS(XADES, "SignedProperties").item(0) as Element

        val fac = XMLSignatureFactory.getInstance("DOM")
        val enveloped = fac.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?)
        val excC14n = fac.newTransform(CanonicalizationMethod.EXCLUSIVE, null as TransformParameterSpec?)
        val documentReference =
            fac.newReference(documentReferenceUri, fac.newDigestMethod(DigestMethod.SHA512, null), listOf(enveloped, excC14n), null, null)
        val propertiesReference =
            fac.newReference(
                "#$signedPropsId",
                fac.newDigestMethod(DigestMethod.SHA512, null),
                listOf(fac.newTransform(CanonicalizationMethod.EXCLUSIVE, null as TransformParameterSpec?)),
                SIGNED_PROPERTIES_TYPE,
                null,
            )
        val signedInfo =
            fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, null as C14NMethodParameterSpec?),
                fac.newSignatureMethod(RSA_SHA512, null),
                listOfNotNull<Reference>(documentReference.takeIf { coverDocument }, propertiesReference.takeIf { coverSignedProperties }),
            )
        val kif = KeyInfoFactory.getInstance("DOM")
        val keyInfo = kif.newKeyInfo(listOf(kif.newX509Data(listOf(certificate))))
        val xmlObject = fac.newXMLObject(listOf(DOMStructure(qualifying)), null, null, null)

        val context = DOMSignContext(keyPair.private, root)
        context.defaultNamespacePrefix = "ds"
        context.setIdAttributeNS(signedProps, null, "Id")
        if (documentReferenceUri.startsWith("#")) {
            val target = findById(root, documentReferenceUri.substring(1))
            if (target != null) context.setIdAttributeNS(target, null, "Id")
        }
        fac.newXMLSignature(signedInfo, keyInfo, listOf(xmlObject), signatureId, null).sign(context)

        val out = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(out))
        // The JDK serializer writes the platform line separator; XML parsers normalise it away, so keep the bytes LF-only.
        return out.toByteArray().decodeToString().replace("\r\n", "\n").encodeToByteArray()
    }

    private fun findById(
        element: Element,
        id: String,
    ): Element? {
        if (element.getAttribute("Id") == id) return element
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) findById(child, id)?.let { return it }
        }
        return null
    }

    private fun qualifyingProperties(
        document: Document,
        signatureId: String,
        signedPropsId: String,
        includeSigningCertificateV2: Boolean = true,
    ): Element {
        fun xades(name: String): Element = document.createElementNS(XADES, "xades:$name")

        fun ds(name: String): Element = document.createElementNS(DSIG, "ds:$name")

        val qualifying = xades("QualifyingProperties")
        qualifying.setAttribute("Target", "#$signatureId")
        val signedProperties = xades("SignedProperties")
        signedProperties.setAttribute("Id", signedPropsId)
        val signedSignature = xades("SignedSignatureProperties")
        val signingTime = xades("SigningTime")
        signingTime.textContent = "2026-09-29T10:00:00Z"
        val certV2 = xades("SigningCertificateV2")
        val cert = xades("Cert")
        val certDigest = xades("CertDigest")
        val method = ds("DigestMethod")
        method.setAttribute("Algorithm", DigestMethod.SHA512)
        val value = ds("DigestValue")
        value.textContent = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(certificate.encoded))
        certDigest.appendChild(method)
        certDigest.appendChild(value)
        cert.appendChild(certDigest)
        certV2.appendChild(cert)
        signedSignature.appendChild(signingTime)
        if (includeSigningCertificateV2) signedSignature.appendChild(certV2)
        signedProperties.appendChild(signedSignature)
        qualifying.appendChild(signedProperties)
        return qualifying
    }

    companion object {
        const val DSIG = "http://www.w3.org/2000/09/xmldsig#"
        const val XADES = "http://uri.etsi.org/01903/v1.3.2#"
        const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
        const val RSA_SHA512 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
        private const val DAY = 24L * 3600L * 1000L
        private const val YEAR = 365L * DAY
    }
}
