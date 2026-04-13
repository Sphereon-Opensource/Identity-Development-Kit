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
 *
 */

package com.sphereon.trust.etsi.signature

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.compat.xml.c14n.ExclusiveC14N
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.etsi.signature.xades.QualifyingProperties
import com.sphereon.trust.etsi.signature.xades.XAdESParser
import com.sphereon.trust.etsi.signature.xmldsig.ReferenceValidator
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import nl.adaptivity.xmlutil.DomWriter
import nl.adaptivity.xmlutil.dom2.Document
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.length
import nl.adaptivity.xmlutil.writeCurrent
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Cross-platform XML digital signature verifier using xmlutil and KeyManagerService.
 *
 * Verifies XML signatures according to W3C XML Signature standard,
 * which is used by ETSI trust lists to ensure integrity and authenticity.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<XmlSignatureVerifier>())
class XmlUtilSignatureVerifier(
    private val keyManagerService: KeyManagerService,
    private val execution: SessionExecution,
) : XmlSignatureVerifier {
    private companion object {
        const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
    }

    private val logger = execution.log.logManager.withTagAsync("XmlUtilSignatureVerifier")
    private val loggerSync = execution.log.logManager.withTag("XmlUtilSignatureVerifier")

    override suspend fun verifyFromBytes(
        xmlData: ByteArray,
        options: XmlSignatureVerificationOptions,
    ): XmlSignatureVerificationResult = verifyFromString(xmlData.decodeToString(), options)

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // Suppress warning for internal xmlutil API usage
    override suspend fun verifyFromString(xmlString: String, options: XmlSignatureVerificationOptions): XmlSignatureVerificationResult =
        try {
            val reader = xmlStreaming.newReader(xmlString)
            val writer = DomWriter()
            while (reader.hasNext()) {
                reader.next()
                reader.writeCurrent(writer)
            }
            val document = writer.target
            verifyDocument(document, xmlString, options)
        } catch (e: Exception) {
            logger.error("XML signature verification failed", exception = e)
            XmlSignatureVerificationResult(
                valid = false,
                signaturePresent = false,
                errorMessage = "XML signature verification failed: ${e.message}",
            )
        }

    private suspend fun verifyDocument(
        document: Document,
        originalXml: String,
        options: XmlSignatureVerificationOptions,
    ): XmlSignatureVerificationResult {
        // Find Signature element
        val root =
            document.getDocumentElement() ?: return XmlSignatureVerificationResult(
                valid = false,
                signaturePresent = false,
                errorMessage = "No root element in document",
            )
        val signatureNodes = root.getElementsByTagNameNS(XMLDSIG_NS, "Signature")

        if (signatureNodes.length == 0) {
            return if (options.requireSignature) {
                XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = false,
                    errorMessage = "No XML signature found in document",
                )
            } else {
                XmlSignatureVerificationResult(
                    valid = true,
                    signaturePresent = false,
                )
            }
        }

        val signatureElement =
            signatureNodes[0] as? Element
                ?: return XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = true,
                    errorMessage = "Invalid signature element",
                )

        return try {
            // Extract certificate and signature value
            val (x509Certificates, chain) = extractCertificatesFromSignature(signatureElement)
            val signatureValue = extractSignatureValue(signatureElement)
            val signedInfo = extractSignedInfo(signatureElement)

            if (x509Certificates.isEmpty()) {
                return XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = true,
                    errorMessage = "No X.509 certificate found in signature",
                )
            }

            if (signatureValue == null) {
                return XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = true,
                    errorMessage = "No signature value found",
                )
            }

            if (signedInfo == null) {
                return XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = true,
                    errorMessage = "No SignedInfo found",
                )
            }

            // Create KeyInfo with certificate
            val keyInfo =
                KeyInfo<KeyType>(
                    key = null,
                    x5c = x509Certificates.toTypedArray(),
                )

            // Canonicalize SignedInfo using proper Exclusive C14N
            val canonicalSignedInfo = ExclusiveC14N.canonicalize(signedInfo)

            // Verify signature using KeyManagerService
            val isValid =
                keyManagerService.isValidRawSignature(
                    keyInfo = keyInfo,
                    input = canonicalSignedInfo,
                    signature = signatureValue,
                )

            if (!isValid) {
                return XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = true,
                    signingCertificate = x509Certificates.firstOrNull()?.decodeFrom(Encoding.BASE64),
                    certificateChain = chain,
                    errorMessage = "Signature validation failed",
                )
            }

            // Validate References in SignedInfo
            val referenceResults =
                try {
                    ReferenceValidator.validateReferences(signedInfo, document, signatureElement)
                } catch (e: Exception) {
                    loggerSync.error("Reference validation failed", exception = e)
                    emptyList()
                }
            val invalidRefs = referenceResults.filter { !it.valid }
            if (invalidRefs.isNotEmpty()) {
                return XmlSignatureVerificationResult(
                    valid = false,
                    signaturePresent = true,
                    signingCertificate = x509Certificates.firstOrNull()?.decodeFrom(Encoding.BASE64),
                    certificateChain = chain,
                    errorMessage = "Reference validation failed: ${invalidRefs.first().errorMessage}",
                    referenceResults = referenceResults,
                )
            }

            // Parse XAdES QualifyingProperties if present
            val qualifyingProperties =
                try {
                    XAdESParser.parse(signatureElement)
                } catch (e: Exception) {
                    loggerSync.error("XAdES parsing failed", exception = e)
                    null
                }

            val signingTime =
                qualifyingProperties
                    ?.signedProperties
                    ?.signedSignatureProperties
                    ?.signingTime

            XmlSignatureVerificationResult(
                valid = true,
                signaturePresent = true,
                signingCertificate = x509Certificates.firstOrNull()?.decodeFrom(Encoding.BASE64),
                certificateChain = chain,
                xadesProperties = qualifyingProperties,
                signingTime = signingTime,
                referenceResults = referenceResults,
            )
        } catch (e: Exception) {
            logger.error("Signature validation failed", exception = e)
            XmlSignatureVerificationResult(
                valid = false,
                signaturePresent = true,
                errorMessage = "Signature validation error: ${e.message}",
            )
        }
    }

    private fun extractCertificatesFromSignature(signatureElement: Element): Pair<List<String>, List<ByteArray>?> {
        try {
            val keyInfoNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "KeyInfo")
            if (keyInfoNodes.length == 0) {
                return Pair(emptyList(), null)
            }

            val keyInfoElement = keyInfoNodes[0] as? Element ?: return Pair(emptyList(), null)
            val x509DataNodes = keyInfoElement.getElementsByTagNameNS(XMLDSIG_NS, "X509Data")
            if (x509DataNodes.length == 0) {
                return Pair(emptyList(), null)
            }

            val x509DataElement = x509DataNodes[0] as? Element ?: return Pair(emptyList(), null)
            val x509CertNodes = x509DataElement.getElementsByTagNameNS(XMLDSIG_NS, "X509Certificate")

            if (x509CertNodes.length == 0) {
                return Pair(emptyList(), null)
            }

            val certificates = mutableListOf<String>()
            val chainBytes = mutableListOf<ByteArray>()

            for (certNode in x509CertNodes) {
                if (certNode is Element) {
                    val certBase64 = certNode.getTextContent()?.trim()?.replace("\\s".toRegex(), "") ?: continue
                    certificates.add(certBase64)
                    chainBytes.add(certBase64.decodeFrom(Encoding.BASE64))
                }
            }

            return Pair(certificates, if (chainBytes.isNotEmpty()) chainBytes else null)
        } catch (e: Exception) {
            loggerSync.error("Failed to extract certificates from signature", exception = e)
            return Pair(emptyList(), null)
        }
    }

    private fun extractSignatureValue(signatureElement: Element): ByteArray? {
        try {
            val signatureValueNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "SignatureValue")
            if (signatureValueNodes.length == 0) {
                return null
            }

            val signatureValueText =
                signatureValueNodes[0]?.getTextContent()?.trim()?.replace("\\s".toRegex(), "")
                    ?: return null

            return signatureValueText.decodeFrom(Encoding.BASE64)
        } catch (e: Exception) {
            loggerSync.error("Failed to extract signature value", exception = e)
            return null
        }
    }

    private fun extractSignedInfo(signatureElement: Element): Element? {
        val signedInfoNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "SignedInfo")
        return if (signedInfoNodes.length > 0) {
            signedInfoNodes[0] as? Element
        } else {
            null
        }
    }
}

/**
 * Interface for XML digital signature verification.
 *
 * Verifies XML signatures according to W3C XML Signature standard,
 * which is used by ETSI trust lists to ensure integrity and authenticity.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("XmlSignatureVerifier", exact = true)
interface XmlSignatureVerifier {
    /**
     * Verifies the XML signature in the given XML data (bytes).
     *
     * @param xmlData The XML data containing the signature
     * @param options Verification options
     * @return Verification result
     */
    suspend fun verifyFromBytes(
        xmlData: ByteArray,
        options: XmlSignatureVerificationOptions = XmlSignatureVerificationOptions(),
    ): XmlSignatureVerificationResult

    /**
     * Verifies the XML signature in the given XML string.
     *
     * @param xmlString The XML string containing the signature
     * @param options Verification options
     * @return Verification result
     */
    suspend fun verifyFromString(
        xmlString: String,
        options: XmlSignatureVerificationOptions = XmlSignatureVerificationOptions(),
    ): XmlSignatureVerificationResult
}

/**
 * Options for XML signature verification.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("XmlSignatureVerificationOptions", exact = true)
data class XmlSignatureVerificationOptions(
    /**
     * Whether to validate the certificate chain of the signing certificate.
     */
    val validateCertificateChain: Boolean = true,
    /**
     * Whether to check certificate revocation status.
     */
    val checkRevocation: Boolean = false,
    /**
     * Trusted root certificates for chain validation (DER encoded).
     * If null, system trust store is used.
     */
    val trustedRoots: List<ByteArray>? = null,
    /**
     * Whether to require the signature to be present.
     */
    val requireSignature: Boolean = true,
)

/**
 * Result of XML signature verification.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("XmlSignatureVerificationResult", exact = true)
data class XmlSignatureVerificationResult(
    /**
     * Whether the signature is valid.
     */
    val valid: Boolean,
    /**
     * Whether a signature was present in the XML.
     */
    val signaturePresent: Boolean,
    /**
     * The signing certificate (DER encoded), if available.
     */
    val signingCertificate: ByteArray? = null,
    /**
     * Certificate chain (DER encoded), if available.
     */
    val certificateChain: List<ByteArray>? = null,
    /**
     * Error message if validation failed.
     */
    val errorMessage: String? = null,
    /**
     * Additional details about the verification.
     */
    val details: Map<String, String> = emptyMap(),
    /**
     * XAdES QualifyingProperties if present in the signature.
     */
    val xadesProperties: QualifyingProperties? = null,
    /**
     * Signing time from XAdES SignedSignatureProperties, if available.
     */
    val signingTime: Instant? = null,
    /**
     * Per-reference validation results from SignedInfo.
     */
    val referenceResults: List<ReferenceValidator.ReferenceResult> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as XmlSignatureVerificationResult

        if (valid != other.valid) return false
        if (signaturePresent != other.signaturePresent) return false
        if (signingCertificate != null) {
            if (other.signingCertificate == null) return false
            if (!signingCertificate.contentEquals(other.signingCertificate)) return false
        } else if (other.signingCertificate != null) {
            return false
        }
        if (certificateChain != other.certificateChain) return false
        if (errorMessage != other.errorMessage) return false
        if (details != other.details) return false

        return true
    }

    override fun hashCode(): Int {
        var result = valid.hashCode()
        result = 31 * result + signaturePresent.hashCode()
        result = 31 * result + (signingCertificate?.contentHashCode() ?: 0)
        result = 31 * result + (certificateChain?.hashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + details.hashCode()
        return result
    }
}

/**
 * Exception thrown when XML signature verification fails.
 */
class XmlSignatureVerificationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
