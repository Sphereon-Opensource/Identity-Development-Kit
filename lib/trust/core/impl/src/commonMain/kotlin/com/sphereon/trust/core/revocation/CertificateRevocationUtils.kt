/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.revocation

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.Asn1Sequence
import at.asitplus.signum.indispensable.asn1.encoding.parse
import com.sphereon.crypto.core.x509.X509ExtensionOids
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.extractUriFromTaggedObject
import com.sphereon.crypto.core.x509.isAccessMethodOid
import com.sphereon.crypto.core.interop.toSignumX509Certificate

/**
 * Cross-platform utilities for extracting revocation-related information from certificates.
 */
object CertificateRevocationUtils {

    /** OID for OCSP access method (1.3.6.1.5.5.7.48.1) */
    private const val OCSP_ACCESS_METHOD = "1.3.6.1.5.5.7.48.1"

    /**
     * Extracts the OCSP responder URL from the Authority Information Access extension.
     */
    fun extractOcspUrl(certificateDer: ByteArray): String? {
        return try {
            val cert = certificateFromDer(certificateDer)
            val signum = cert.toSignumX509Certificate()
            val exts = signum.tbsCertificate.extensions ?: return null
            val aiaExt = exts.firstOrNull {
                it.oid.toString() == X509ExtensionOids.AUTHORITY_INFORMATION_ACCESS
            } ?: return null

            val octet = aiaExt.value as? Asn1EncapsulatingOctetString ?: return null
            val seq = Asn1Element.parse(octet.content) as? Asn1Sequence ?: return null

            for (accessDesc in seq.children) {
                val adSeq = accessDesc as? Asn1Sequence ?: continue
                if (adSeq.children.size < 2) continue

                if (isAccessMethodOid(adSeq.children.first(), listOf(OCSP_ACCESS_METHOD))) {
                    return extractUriFromTaggedObject(adSeq.children[1])
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts CRL Distribution Point URLs from the CDP extension.
     */
    fun extractCdpUrls(certificateDer: ByteArray): List<String> {
        return try {
            val cert = certificateFromDer(certificateDer)
            val signum = cert.toSignumX509Certificate()
            val exts = signum.tbsCertificate.extensions ?: return emptyList()
            val cdpExt = exts.firstOrNull {
                it.oid.toString() == X509ExtensionOids.CRL_DISTRIBUTION_POINTS
            } ?: return emptyList()

            val octet = cdpExt.value as? Asn1EncapsulatingOctetString ?: return emptyList()
            val seq = Asn1Element.parse(octet.content) as? Asn1Sequence ?: return emptyList()

            val urls = mutableListOf<String>()
            collectUris(seq, urls)
            urls
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extracts the serial number from a DER-encoded certificate.
     */
    fun extractSerialNumber(certificateDer: ByteArray): String? {
        return try {
            certificateFromDer(certificateDer).serialNumber
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recursively collect URI strings from ASN.1 elements.
     * CDP structure: SEQUENCE of DistributionPoint, each containing fullName GeneralNames.
     */
    private fun collectUris(element: Asn1Element, urls: MutableList<String>) {
        when (element) {
            is Asn1Sequence -> element.children.forEach { collectUris(it, urls) }
            else -> {
                val uri = extractUriFromTaggedObject(element)
                if (uri != null && (uri.startsWith("http://") || uri.startsWith("https://"))) {
                    urls.add(uri)
                }
            }
        }
    }
}
