/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.mdoc.data.device

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Primitive
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.encoding.parse
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.interop.x509CertificateFromDer
import com.sphereon.crypto.core.x509.X509ExtensionOids

/**
 * Matches the second-edition `issuerIdentifiers` request values.
 *
 * ISO/IEC 18013-5 defines an IssuerIdentifier as the keyIdentifier from the
 * AuthorityKeyIdentifier extension, not as the DER encoding of an arbitrary
 * certificate in the issuer-auth `x5chain`. Keeping this in the mdoc core
 * prevents the wallet and generic transfer selector from silently applying
 * different (and incorrect) certificate matching rules.
 */
fun COSE_Sign1<*>.matchesIssuerIdentifiers(issuerIdentifiers: List<ByteArray>): Boolean {
    if (issuerIdentifiers.isEmpty()) return true
    val x5chain = protectedHeader.x5chain ?: unprotectedHeader?.x5chain ?: return false
    return x5chain.value.any { certificate ->
        authorityKeyIdentifier(certificate.value)?.let { authorityKeyIdentifier ->
            issuerIdentifiers.any { requested -> requested.contentEquals(authorityKeyIdentifier) }
        } == true
    }
}

/**
 * Extract the keyIdentifier from an AuthorityKeyIdentifier extension.
 *
 * The extension value exposed by awesn1 is the DER encoding of the extension's
 * inner value. The standard uses IMPLICIT context-specific tag [0] (0x80),
 * while accepting a constructed wrapper as a narrow interoperability measure
 * is harmless and keeps this matcher usable with older certificate encoders.
 */
private fun authorityKeyIdentifier(certificateDer: ByteArray): ByteArray? =
    runCatching {
        val extensions = x509CertificateFromDer(certificateDer).tbsCertificate.extensions ?: return null
        val extension = extensions.firstOrNull { it.oid.toString() == X509ExtensionOids.AUTHORITY_KEY_IDENTIFIER } ?: return null
        val sequence = Asn1Element.parse(extension.value) as? Asn1Sequence ?: return null
        sequence.children.firstNotNullOfOrNull { child ->
            // awesn1 exposes the context-specific [0] tag as tag number 0;
            // some encoders/parsers preserve the full DER tag octet instead.
            when (child) {
                is Asn1Primitive ->
                    when (child.tag.tagValue.toIntExact("AuthorityKeyIdentifier tag")) {
                        0, 0x80 -> child.content
                        else -> null
                    }
                is Asn1Sequence ->
                    when (child.tag.tagValue.toIntExact("AuthorityKeyIdentifier tag")) {
                        0, 0xA0 -> (child.children.firstOrNull() as? Asn1Primitive)?.content
                        else -> null
                    }
                else -> null
            }
        }
    }.getOrNull()

private fun ULong.toIntExact(field: String): Int {
    require(this <= Int.MAX_VALUE.toULong()) { "$field is outside the signed 32-bit range" }
    return toInt()
}
