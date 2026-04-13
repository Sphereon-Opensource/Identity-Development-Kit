/*
 * Copyright 2025 Sphereon International B.V.
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
import com.sphereon.core.api.encodeTo
import com.sphereon.core.compat.xml.c14n.ExclusiveC14N
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.etsi.signature.xmldsig.ReferenceValidator
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.dom2.*
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Validates XAdES (XML Advanced Electronic Signatures) — Baseline-B profile.
 *
 * Performs:
 * 1. XML Signature cryptographic verification (SignedInfo → SignatureValue)
 * 2. Reference digest validation (all References in SignedInfo)
 * 3. XAdES QualifyingProperties parsing and validation
 * 4. Signing certificate digest verification (SigningCertificateV2)
 */
interface XAdESValidator {
    suspend fun validate(
        xmlData: ByteArray,
        options: XAdESValidationOptions = XAdESValidationOptions()
    ): XAdESValidationResult
}

@ContributesTo(SessionScope::class)
interface XAdESValidatorComponent {
    val xadesValidator: XAdESValidator
}

data class XAdESValidationOptions(
    val validateReferences: Boolean = true,
    val validateSigningCertificate: Boolean = true,
    val validateCertificateChain: Boolean = true,
    val requireXAdESProperties: Boolean = false,
    val trustedCertificates: List<ByteArray>? = null
)

data class XAdESValidationResult(
    val valid: Boolean,
    val signaturePresent: Boolean,
    val signatureValid: Boolean,
    val referencesValid: Boolean,
    val xadesPresent: Boolean,
    val signingCertificateValid: Boolean? = null,
    val signingTime: Instant? = null,
    val signingCertificate: ByteArray? = null,
    val certificateChain: List<ByteArray>? = null,
    val qualifyingProperties: QualifyingProperties? = null,
    val referenceResults: List<ReferenceValidator.ReferenceResult> = emptyList(),
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as XAdESValidationResult
        if (valid != other.valid) return false
        if (signaturePresent != other.signaturePresent) return false
        if (signatureValid != other.signatureValid) return false
        if (referencesValid != other.referencesValid) return false
        if (xadesPresent != other.xadesPresent) return false
        if (signingCertificateValid != other.signingCertificateValid) return false
        if (signingTime != other.signingTime) return false
        if (signingCertificate != null) {
            if (other.signingCertificate == null) return false
            if (!signingCertificate.contentEquals(other.signingCertificate)) return false
        } else if (other.signingCertificate != null) return false
        if (errors != other.errors) return false
        return true
    }

    override fun hashCode(): Int {
        var result = valid.hashCode()
        result = 31 * result + signaturePresent.hashCode()
        result = 31 * result + signatureValid.hashCode()
        result = 31 * result + referencesValid.hashCode()
        result = 31 * result + xadesPresent.hashCode()
        result = 31 * result + (signingCertificate?.contentHashCode() ?: 0)
        return result
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<XAdESValidator>())
class XAdESValidatorImpl(
    private val keyManagerService: KeyManagerService,
    private val execution: SessionExecution
) : XAdESValidator {

    private val logger = execution.log.logManager.withTagAsync("XAdESValidator")

    private val SIG_ALG_MAP = mapOf(
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" to DigestAlg.SHA256,
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384" to DigestAlg.SHA384,
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512" to DigestAlg.SHA512,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256" to DigestAlg.SHA256,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384" to DigestAlg.SHA384,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512" to DigestAlg.SHA512,
    )

    private val XML_SIG_ALG_TO_SIGNATURE_ALG = mapOf(
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" to SignatureAlgorithm.RSA_SHA256,
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384" to SignatureAlgorithm.RSA_SHA384,
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512" to SignatureAlgorithm.RSA_SHA512,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256" to SignatureAlgorithm.ECDSA_SHA256,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384" to SignatureAlgorithm.ECDSA_SHA384,
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512" to SignatureAlgorithm.ECDSA_SHA512,
    )

    private val DIGEST_ALG_MAP = mapOf(
        "http://www.w3.org/2001/04/xmlenc#sha256" to DigestAlg.SHA256,
        "http://www.w3.org/2001/04/xmldsig-more#sha256" to DigestAlg.SHA256,
        "http://www.w3.org/2001/04/xmldsig-more#sha384" to DigestAlg.SHA384,
        "http://www.w3.org/2001/04/xmlenc#sha512" to DigestAlg.SHA512,
        "http://www.w3.org/2001/04/xmldsig-more#sha512" to DigestAlg.SHA512,
    )

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    override suspend fun validate(
        xmlData: ByteArray,
        options: XAdESValidationOptions
    ): XAdESValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            val document = try {
                parseXml(xmlData.decodeToString())
            } catch (e: Throwable) {
                return errorResult("XML parsing failed: ${e.message}")
            }
            val root = document.getDocumentElement()
                ?: return errorResult("No root element in document")

            // Detect DOM parsererror (e.g. jsdom on JS returns a <parsererror> document for invalid XML)
            if (root.getLocalName() == "parsererror" || root.getLocalName() == "html") {
                return errorResult("XML parsing failed: document contains parse errors")
            }

            // Find Signature element
            val signatureNodes = root.getElementsByTagNameNS(XMLDSIG_NS, "Signature")
            if (signatureNodes.length == 0) {
                return XAdESValidationResult(
                    valid = !options.requireXAdESProperties,
                    signaturePresent = false,
                    signatureValid = false,
                    referencesValid = false,
                    xadesPresent = false,
                    errors = listOf("No XML signature found in document")
                )
            }

            val signatureElement = signatureNodes[0] as? Element
                ?: return errorResult("Invalid Signature element")

            // Extract components
            val signedInfo = firstChild(signatureElement, XMLDSIG_NS, "SignedInfo")
                ?: return errorResult("No SignedInfo in Signature")
            val signatureValueEl = firstChild(signatureElement, XMLDSIG_NS, "SignatureValue")
                ?: return errorResult("No SignatureValue in Signature")

            val signatureValueBase64 = signatureValueEl.getTextContent()
                ?.trim()?.replace("\\s".toRegex(), "")
                ?: return errorResult("Empty SignatureValue")
            val signatureValue = signatureValueBase64.decodeFrom(Encoding.BASE64)

            // Extract certificates
            val (certStrings, chainBytes) = extractCertificates(signatureElement)
            if (certStrings.isEmpty()) {
                return errorResult("No X.509 certificate found in signature")
            }

            val signingCertDer = certStrings.first().decodeFrom(Encoding.BASE64)

            // 1. Canonicalize SignedInfo and verify cryptographic signature
            val canonMethod = firstChild(signedInfo, XMLDSIG_NS, "CanonicalizationMethod")
            val canonicalSignedInfo = ExclusiveC14N.canonicalize(signedInfo)

            // Extract signature algorithm from SignedInfo
            val sigMethodEl = firstChild(signedInfo, XMLDSIG_NS, "SignatureMethod")
            val sigAlgUri = sigMethodEl?.getAttribute("Algorithm")
            val signatureAlgorithm = sigAlgUri?.let { XML_SIG_ALG_TO_SIGNATURE_ALG[it] }

            val keyInfo = KeyInfo<KeyType>(
                key = null,
                x5c = certStrings.toTypedArray(),
                signatureAlgorithm = signatureAlgorithm
            )

            val signatureValid = try {
                keyManagerService.isValidRawSignature(
                    keyInfo = keyInfo,
                    input = canonicalSignedInfo,
                    signature = signatureValue
                )
            } catch (e: Exception) {
                logger.error("Signature verification failed", exception = e)
                errors.add("Signature verification error: ${e.message}")
                false
            }

            if (!signatureValid) {
                errors.add("Cryptographic signature validation failed")
                // Diagnose the failure — check for common encoding issues
                diagnoseSignatureFailure(signatureValue, errors)
            }

            // 2. Validate references
            var referencesValid = true
            var referenceResults = emptyList<ReferenceValidator.ReferenceResult>()
            if (options.validateReferences) {
                referenceResults = ReferenceValidator.validateReferences(
                    signedInfo, document, signatureElement
                )
                val invalidRefs = referenceResults.filter { !it.valid }
                if (invalidRefs.isNotEmpty()) {
                    referencesValid = false
                    for (ref in invalidRefs) {
                        errors.add("Reference '${ref.uri}' invalid: ${ref.errorMessage}")
                    }
                }
            }

            // 3. Parse XAdES QualifyingProperties
            val qualifyingProperties = XAdESParser.parse(signatureElement)
            val xadesPresent = qualifyingProperties != null

            if (!xadesPresent && options.requireXAdESProperties) {
                errors.add("XAdES QualifyingProperties required but not found")
            }

            // 4. Validate signing certificate digest (if XAdES present)
            var signingCertValid: Boolean? = null
            var signingTime: Instant? = null

            if (qualifyingProperties != null) {
                signingTime = qualifyingProperties.signedProperties
                    ?.signedSignatureProperties?.signingTime

                if (options.validateSigningCertificate) {
                    val certDigests = qualifyingProperties.signedProperties
                        ?.signedSignatureProperties?.signingCertificateV2
                        ?: qualifyingProperties.signedProperties
                            ?.signedSignatureProperties?.signingCertificate

                    if (certDigests != null && certDigests.isNotEmpty()) {
                        signingCertValid = validateCertificateDigest(
                            certDigests.first(), signingCertDer
                        )
                        if (signingCertValid == false) {
                            errors.add("Signing certificate digest does not match")
                        }
                    } else {
                        warnings.add("No SigningCertificate(V2) in XAdES properties")
                    }
                }
            }

            val overallValid = signatureValid && referencesValid &&
                    (signingCertValid != false) &&
                    (!options.requireXAdESProperties || xadesPresent)

            return XAdESValidationResult(
                valid = overallValid,
                signaturePresent = true,
                signatureValid = signatureValid,
                referencesValid = referencesValid,
                xadesPresent = xadesPresent,
                signingCertificateValid = signingCertValid,
                signingTime = signingTime,
                signingCertificate = signingCertDer,
                certificateChain = chainBytes,
                qualifyingProperties = qualifyingProperties,
                referenceResults = referenceResults,
                errors = errors,
                warnings = warnings
            )

        } catch (e: Exception) {
            logger.error("XAdES validation failed", exception = e)
            return errorResult("XAdES validation failed: ${e.message}")
        }
    }

    /**
     * Diagnoses why a signature verification failed by checking for common encoding issues.
     * Appends diagnostic hints to the errors list.
     */
    private fun diagnoseSignatureFailure(signatureValue: ByteArray, errors: MutableList<String>) {
        // Check for double-base64 encoding: decoded bytes are all valid base64 characters + whitespace
        val isAsciiBase64 = signatureValue.all { b ->
            val c = b.toInt().toChar()
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                    c == '+' || c == '/' || c == '=' ||
                    c == '\n' || c == '\r' || c == ' ' || c == '\t'
        }

        if (isAsciiBase64 && signatureValue.size > 64) {
            try {
                val innerText = signatureValue.decodeToString().replace("\\s".toRegex(), "")
                val innerDecoded = innerText.decodeFrom(Encoding.BASE64)
                errors.add(
                    "SignatureValue appears double-base64 encoded: decoded ${signatureValue.size} bytes " +
                            "are valid base64 text that decodes to ${innerDecoded.size} bytes. " +
                            "This violates W3C XML Signature 1.1 section 4.4.2 (ds:SignatureValue extends " +
                            "base64Binary, requiring a single base64 encoding of the raw signature bytes). " +
                            "ETSI TS 119 612 and ETSI EN 319 132 (XAdES) normatively reference this requirement."
                )
            } catch (_: Exception) {
                // Not actually double-encoded
            }
        }
    }

    private fun validateCertificateDigest(certDigest: CertDigest, certDer: ByteArray): Boolean {
        val digestAlg = DIGEST_ALG_MAP[certDigest.digestAlgorithm] ?: return false
        val computed = hash(certDer, digestAlg)
        return computed.contentEquals(certDigest.digestValue)
    }

    private fun extractCertificates(signatureElement: Element): Pair<List<String>, List<ByteArray>?> {
        val keyInfoEl = firstChild(signatureElement, XMLDSIG_NS, "KeyInfo") ?: return Pair(emptyList(), null)
        val x509DataEl = firstChild(keyInfoEl, XMLDSIG_NS, "X509Data") ?: return Pair(emptyList(), null)
        val certNodes = x509DataEl.getElementsByTagNameNS(XMLDSIG_NS, "X509Certificate")

        val certs = mutableListOf<String>()
        val chainBytes = mutableListOf<ByteArray>()

        for (certNode in certNodes) {
            if (certNode !is Element) continue
            val certBase64 = certNode.getTextContent()
                ?.trim()?.replace("\\s".toRegex(), "") ?: continue
            certs.add(certBase64)
            chainBytes.add(certBase64.decodeFrom(Encoding.BASE64))
        }

        return Pair(certs, chainBytes.ifEmpty { null })
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun parseXml(xmlString: String): Document {
        val reader = xmlStreaming.newReader(xmlString)
        val writer = DomWriter()
        while (reader.hasNext()) {
            reader.next()
            reader.writeCurrent(writer)
        }
        return writer.target
    }

    private fun firstChild(parent: Element, nsUri: String, localName: String): Element? {
        for (child in parent.getChildNodes()) {
            if (child is Element &&
                child.getLocalName() == localName &&
                child.getNamespaceURI() == nsUri
            ) {
                return child
            }
        }
        return null
    }

    private fun errorResult(message: String) = XAdESValidationResult(
        valid = false,
        signaturePresent = false,
        signatureValid = false,
        referencesValid = false,
        xadesPresent = false,
        errors = listOf(message)
    )
}
