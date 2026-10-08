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

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.eu.impl.publish

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.xml.c14n.ExclusiveC14N
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.di.session.SessionScope
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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A signing key of a catalogue publisher. [keyInfo] selects the key in the KMS (a managed alias); the private key never
 * leaves the KMS. [certificateChain] holds DER certificates, leaf first; when empty, the chain bound to the KMS key is
 * used. [signatureAlgorithm] defaults to RSA-SHA512, or the ECDSA algorithm that matches the curve of the key.
 */
class CatalogueSigningKey(
    val keyInfo: KeyInfoType<*>,
    val certificateChain: List<ByteArray> = emptyList(),
    val signatureAlgorithm: SignatureAlgorithm? = null,
)

class SignedCatalogueXml(
    val xml: ByteArray,
    /** DER certificates that were embedded in the signature, leaf first. */
    val signerCertificates: List<ByteArray>,
    val signingTime: Instant,
    val signatureMethod: String,
)

/**
 * Adds an enveloped XAdES baseline B signature to a catalogue document.
 */
interface CatalogueXmlSigner {
    suspend fun sign(
        xml: ByteArray,
        key: CatalogueSigningKey,
        signingTime: Instant = Clock.System.now(),
    ): IdkResult<SignedCatalogueXml, IdkError>
}

/**
 * The enveloped signature of the catalogue specifications: exclusive canonicalization, SHA-512 digests, RSA-SHA512 (or
 * the ECDSA equivalent of the key) and XAdES `SigningCertificateV2`. The document reference is `URI=""` with the
 * enveloped-signature and exc-c14n transforms; the signed properties are a second reference. The signature value comes
 * from [KeyManagerService], so the tenant key stays inside its KMS.
 *
 * The signature element is inserted directly before the closing tag of the root, without surrounding whitespace, so
 * removing it (the enveloped transform) leaves exactly the bytes that were digested.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CatalogueXmlSigner>())
class XAdESCatalogueXmlSigner(
    private val keyManagerService: KeyManagerService,
    private val createRawSignatureCommand: CreateRawSignatureCommand,
) : CatalogueXmlSigner {
    override suspend fun sign(
        xml: ByteArray,
        key: CatalogueSigningKey,
        signingTime: Instant,
    ): IdkResult<SignedCatalogueXml, IdkError> {
        val unsigned = xml.decodeToString().removePrefix("﻿")
        val closing = unsigned.trimEnd().lastIndexOf("</")
        if (closing < 0) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The catalogue document has no root element to sign"))
        if (unsigned.contains("<ds:Signature") || unsigned.contains("Signature xmlns=\"${EuCatalogueConstants.XMLDSIG_NAMESPACE}\"")) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The catalogue document already carries a signature"))
        }

        val givenCertificates =
            key.certificateChain.ifEmpty { key.keyInfo.x5c?.map { it.filterNot(Char::isWhitespace).decodeFromBase64() }.orEmpty() }
        val givenAlgorithm = key.signatureAlgorithm ?: key.keyInfo.signatureAlgorithm
        // The KMS key is always resolved: the certificate that is embedded in the signature must belong to it.
        val resolved =
            try {
                keyManagerService.getKeyResult(key.keyInfo).getOrElse {
                    return Err(
                        IdkError.fromString(
                            message = "The catalogue signing key could not be resolved: ${it.message.defaultMessage}",
                            code = "CATALOGUE_SIGNING_KEY_UNRESOLVED",
                            category = ErrorCategory.UNAVAILABLE,
                        ),
                    )
                }.key
            } catch (e: Exception) {
                return Err(IdkError.fromString("The catalogue signing key could not be resolved: ${e.message}", "CATALOGUE_SIGNING_KEY_UNRESOLVED", e))
            } ?: return Err(
                IdkError.fromString(
                    message = "The catalogue signing key could not be resolved",
                    code = "CATALOGUE_SIGNING_KEY_UNRESOLVED",
                    category = ErrorCategory.UNAVAILABLE,
                ),
            )
        val certificates = givenCertificates.ifEmpty { resolved.x5c?.map { it.filterNot(Char::isWhitespace).decodeFromBase64() }.orEmpty() }
        if (certificates.isEmpty()) {
            return Err(
                IdkError.fromString(
                    message = "The catalogue signing key has no certificate. A XAdES signature needs the signer certificate; bind a key with a certificate chain",
                    code = "CATALOGUE_SIGNING_CERTIFICATE_MISSING",
                    category = ErrorCategory.VALIDATION,
                ),
            )
        }
        val algorithm = givenAlgorithm ?: defaultAlgorithm(resolved.key as? JwkType)
        val keyIsEc = (resolved.key as? JwkType)?.kty == JwaKeyType.EC
        val algorithmIsEc = algorithm in ECDSA_ALGORITHMS
        if (resolved.key is JwkType && keyIsEc != algorithmIsEc) {
            return Err(
                IdkError.fromString(
                    message = "Signature algorithm $algorithm does not match the type of the catalogue signing key",
                    code = "CATALOGUE_SIGNING_ALGORITHM_MISMATCH",
                    category = ErrorCategory.VALIDATION,
                ),
            )
        }
        val signatureMethod =
            SIGNATURE_METHODS[algorithm]
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Signature algorithm $algorithm is not an XML signature algorithm"))

        val documentDigest =
            try {
                hash(canonicalRoot(unsigned), DigestAlg.SHA512).encodeToBase64()
            } catch (e: Exception) {
                return Err(IdkError.fromString("The catalogue document is not well-formed XML: ${e.message}", "CATALOGUE_XML_MALFORMED", e))
            }
        val ids = SignatureIds(hexId(documentDigest))
        val certificateDigest = hash(certificates.first(), DigestAlg.SHA512).encodeToBase64()

        val placeholder = "PLACEHOLDER-DIGEST-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val signatureValuePlaceholder = "PLACEHOLDER-SIGNATURE-VALUE"
        fun assemble(
            propertiesDigest: String,
            signatureValue: String,
        ): String =
            unsigned.substring(0, closing) +
                signatureElement(ids, signatureMethod, documentDigest, propertiesDigest, signatureValue, certificates, certificateDigest, signingTime) +
                unsigned.substring(closing)

        val propertiesDigest =
            try {
                val draft = assemble(placeholder, signatureValuePlaceholder)
                hash(canonicalById(draft, ids.signedProperties), DigestAlg.SHA512).encodeToBase64()
            } catch (e: Exception) {
                return Err(IdkError.fromString("The XAdES signed properties could not be canonicalized: ${e.message}", "CATALOGUE_XML_MALFORMED", e))
            }
        val toSign =
            try {
                canonicalSignedInfo(assemble(propertiesDigest, signatureValuePlaceholder))
            } catch (e: Exception) {
                return Err(IdkError.fromString("The XML signature SignedInfo could not be canonicalized: ${e.message}", "CATALOGUE_XML_MALFORMED", e))
            }
        // The public command binding honors the configured KMS signature route, so a service whose keys live in a
        // remote tenant KMS signs there instead of probing its own in-process providers. The selector is pinned to the
        // resolved kid and provider, so the KMS that signs does not have to search its providers by alias alone.
        val signature =
            try {
                createRawSignatureCommand.execute(
                    CreateRawSignatureArgs(keyInfo = key.keyInfo.pinnedTo(resolved).withAlgorithm(algorithm), input = toSign, requireX5Chain = false),
                ).getOrElse {
                    return Err(IdkError.fromString("The KMS could not sign the catalogue: ${it.message.defaultMessage}", "CATALOGUE_SIGNING_FAILED"))
                }.signature
            } catch (e: Exception) {
                return Err(IdkError.fromString("The KMS could not sign the catalogue: ${e.message}", "CATALOGUE_SIGNING_FAILED", e))
            }
        val certificateMatchesKey =
            try {
                keyManagerService.isValidRawSignature(
                    keyInfo = KeyInfo<KeyType>(key = null, x5c = certificates.map { it.encodeToBase64() }.toTypedArray(), signatureAlgorithm = algorithm),
                    input = toSign,
                    signature = signature,
                )
            } catch (e: Exception) {
                false
            }
        if (!certificateMatchesKey) {
            return Err(
                IdkError.fromString(
                    message = "The signature made with the catalogue signing key does not verify against the supplied certificate: the certificate does not belong to the key or the algorithm does not match",
                    code = "CATALOGUE_SIGNING_CERTIFICATE_MISMATCH",
                    category = ErrorCategory.VALIDATION,
                ),
            )
        }
        val signed = assemble(propertiesDigest, signature.encodeToBase64())
        return Ok(SignedCatalogueXml(signed.encodeToByteArray(), certificates, signingTime, signatureMethod))
    }

    private fun KeyInfoType<*>.pinnedTo(resolved: KeyInfoType<*>): KeyInfoType<*> =
        KeyInfo<KeyType>(
            kid = resolved.kid ?: kid,
            key = key,
            opts = opts,
            keyVisibility = keyVisibility,
            signatureAlgorithm = signatureAlgorithm,
            x5c = x5c,
            alias = alias ?: resolved.alias,
            providerId = resolved.providerId ?: providerId,
            keyType = keyType,
            keyEncoding = keyEncoding,
            noCache = noCache,
        )

    private fun KeyInfoType<*>.withAlgorithm(algorithm: SignatureAlgorithm): KeyInfoType<*> =
        if (signatureAlgorithm == algorithm) {
            this
        } else {
            KeyInfo<KeyType>(
                kid = kid,
                key = key,
                opts = opts,
                keyVisibility = keyVisibility,
                signatureAlgorithm = algorithm,
                x5c = x5c,
                alias = alias,
                providerId = providerId,
                keyType = keyType,
                keyEncoding = keyEncoding,
                noCache = noCache,
            )
        }

    private fun defaultAlgorithm(jwk: JwkType?): SignatureAlgorithm =
        when {
            jwk?.kty == JwaKeyType.EC ->
                when (jwk.crv) {
                    JwaCurve.P_256 -> SignatureAlgorithm.ECDSA_SHA256
                    JwaCurve.P_384 -> SignatureAlgorithm.ECDSA_SHA384
                    else -> SignatureAlgorithm.ECDSA_SHA512
                }
            else -> SignatureAlgorithm.RSA_SHA512
        }

    private fun signatureElement(
        ids: SignatureIds,
        signatureMethod: String,
        documentDigest: String,
        propertiesDigest: String,
        signatureValue: String,
        certificates: List<ByteArray>,
        certificateDigest: String,
        signingTime: Instant,
    ): String {
        val ds = EuCatalogueConstants.XMLDSIG_NAMESPACE
        val digestMethod = "<ds:DigestMethod Algorithm=\"$DIGEST_SHA512\"/>"
        val x509 = certificates.joinToString("") { "<ds:X509Certificate>${it.encodeToBase64()}</ds:X509Certificate>" }
        return buildString {
            append("<ds:Signature xmlns:ds=\"$ds\" Id=\"${ids.signature}\">")
            append("<ds:SignedInfo>")
            append("<ds:CanonicalizationMethod Algorithm=\"${EuCatalogueConstants.EXC_C14N_TRANSFORM}\"/>")
            append("<ds:SignatureMethod Algorithm=\"$signatureMethod\"/>")
            append("<ds:Reference URI=\"\"><ds:Transforms>")
            append("<ds:Transform Algorithm=\"$ENVELOPED_TRANSFORM\"/><ds:Transform Algorithm=\"${EuCatalogueConstants.EXC_C14N_TRANSFORM}\"/>")
            append("</ds:Transforms>$digestMethod<ds:DigestValue>$documentDigest</ds:DigestValue></ds:Reference>")
            append("<ds:Reference Type=\"$SIGNED_PROPERTIES_TYPE\" URI=\"#${ids.signedProperties}\"><ds:Transforms>")
            append("<ds:Transform Algorithm=\"${EuCatalogueConstants.EXC_C14N_TRANSFORM}\"/>")
            append("</ds:Transforms>$digestMethod<ds:DigestValue>$propertiesDigest</ds:DigestValue></ds:Reference>")
            append("</ds:SignedInfo>")
            append("<ds:SignatureValue>$signatureValue</ds:SignatureValue>")
            append("<ds:KeyInfo><ds:X509Data>$x509</ds:X509Data></ds:KeyInfo>")
            append("<ds:Object><xades:QualifyingProperties xmlns:xades=\"$XADES\" Target=\"#${ids.signature}\">")
            append("<xades:SignedProperties Id=\"${ids.signedProperties}\"><xades:SignedSignatureProperties>")
            append("<xades:SigningTime>$signingTime</xades:SigningTime>")
            append("<xades:SigningCertificateV2><xades:Cert><xades:CertDigest>$digestMethod")
            append("<ds:DigestValue>$certificateDigest</ds:DigestValue></xades:CertDigest></xades:Cert></xades:SigningCertificateV2>")
            append("</xades:SignedSignatureProperties></xades:SignedProperties></xades:QualifyingProperties></ds:Object>")
            append("</ds:Signature>")
        }
    }

    private fun canonicalRoot(xml: String): ByteArray {
        val root = parse(xml).getDocumentElement() ?: throw IllegalArgumentException("No root element")
        return ExclusiveC14N.canonicalize(root)
    }

    private fun canonicalById(
        xml: String,
        id: String,
    ): ByteArray {
        val root = parse(xml).getDocumentElement() ?: throw IllegalArgumentException("No root element")
        val element = findById(root, id) ?: throw IllegalArgumentException("No element with Id $id")
        return ExclusiveC14N.canonicalize(element)
    }

    private fun canonicalSignedInfo(xml: String): ByteArray {
        val root = parse(xml).getDocumentElement() ?: throw IllegalArgumentException("No root element")
        val signature = root.getElementsByTagNameNS(EuCatalogueConstants.XMLDSIG_NAMESPACE, "SignedInfo")
        val signedInfo = (if (signature.length > 0) signature[0] as? Element else null) ?: throw IllegalArgumentException("No SignedInfo")
        return ExclusiveC14N.canonicalize(signedInfo)
    }

    private fun findById(
        element: Element,
        id: String,
    ): Element? {
        if (element.getAttribute("Id") == id) return element
        val children = element.getChildNodes()
        for (i in 0 until children.length) {
            val child = children[i] as? Element ?: continue
            findById(child, id)?.let { return it }
        }
        return null
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun parse(xml: String): Document {
        val reader = xmlStreaming.newReader(xml)
        val writer = DomWriter()
        while (reader.hasNext()) {
            reader.next()
            reader.writeCurrent(writer)
        }
        return writer.target
    }

    private fun hexId(documentDigest: String): String = documentDigest.filter { it.isLetterOrDigit() }.take(16)

    private class SignatureIds(
        seed: String,
    ) {
        val signature = "id-$seed"
        val signedProperties = "xades-$seed"
    }

    private companion object {
        const val DIGEST_SHA512 = "http://www.w3.org/2001/04/xmlenc#sha512"
        const val ENVELOPED_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
        const val XADES = "http://uri.etsi.org/01903/v1.3.2#"
        const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"

        val SIGNATURE_METHODS: Map<SignatureAlgorithm, String> =
            mapOf(
                SignatureAlgorithm.RSA_SHA256 to "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
                SignatureAlgorithm.RSA_SHA384 to "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384",
                SignatureAlgorithm.RSA_SHA512 to "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512",
                SignatureAlgorithm.ECDSA_SHA256 to "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256",
                SignatureAlgorithm.ECDSA_SHA384 to "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384",
                SignatureAlgorithm.ECDSA_SHA512 to "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512",
            )

        private val ECDSA_ALGORITHMS: Set<SignatureAlgorithm> =
            setOf(SignatureAlgorithm.ECDSA_SHA256, SignatureAlgorithm.ECDSA_SHA384, SignatureAlgorithm.ECDSA_SHA512)
    }
}
