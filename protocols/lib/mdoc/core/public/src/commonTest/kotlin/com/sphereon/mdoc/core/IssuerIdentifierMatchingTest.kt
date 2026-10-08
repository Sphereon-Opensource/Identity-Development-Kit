/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.mdoc.core

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.kms.X509CertificateExtensionSpec
import com.sphereon.crypto.core.x509.X509ExtensionOids
import com.sphereon.mdoc.core.testutil.MdocTestContext
import com.sphereon.mdoc.data.device.matchesIssuerIdentifiers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerIdentifierMatchingTest {
    private lateinit var context: MdocTestContext

    @BeforeTest
    fun setUp() {
        context = MdocTestContext(this)
    }

    @Test
    fun issuerIdentifier_matchesAuthorityKeyIdentifier_notCertificateDer() =
        runTest {
            val aki = ByteArray(20) { index -> (index + 1).toByte() }
            val keyPair =
                context.kms.generateKeyAsync(
                    alias = "issuer-identifier-test",
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                    providerId = null,
                )
            val issuerKeyInfo = keyPair.toManagedKeyInfo<CoseKey>(KeyVisibility.PRIVATE, KeyEncoding.COSE)
            val dn = X509DistinguishedNameElements(commonName = "Issuer Identifier Test")
            val certificate =
                context.certificateService.createCertificate(
                    issuerKeyInfo = issuerKeyInfo,
                    issuer = dn,
                    subjectKeyInfo = issuerKeyInfo,
                    subject = dn,
                    serialNumber = 1,
                    extensions =
                        listOf(
                            X509CertificateExtensionSpec(
                                oid = X509ExtensionOids.AUTHORITY_KEY_IDENTIFIER,
                                valueDer = byteArrayOf(0x30, 0x16, 0x80.toByte(), 0x14) + aki,
                            ),
                        ),
                ).certificate
            val sign1: COSE_Sign1<Any> =
                CoseSign1(
                    protectedHeader =
                        CoseHeaderCbor(
                            x5chain = CborArray(mutableListOf(CborByteString(certificate.der))),
                        ),
                    unprotectedHeader = null,
                    payload = null,
                    signature = CborByteString(byteArrayOf()),
                )

            assertTrue(sign1.matchesIssuerIdentifiers(listOf(aki)))
            assertFalse(sign1.matchesIssuerIdentifiers(listOf(ByteArray(20) { 0x7f })))
            assertFalse(sign1.matchesIssuerIdentifiers(listOf(certificate.der)))
        }

    @Test
    fun issuerIdentifier_match_failsClosed_withoutCertificateChain() {
        val sign1 =
            CoseSign1<Any>(
                protectedHeader = CoseHeaderCbor(),
                unprotectedHeader = null,
                payload = null,
                signature = CborByteString(byteArrayOf()),
            )

        assertFalse(sign1.matchesIssuerIdentifiers(listOf(byteArrayOf(1, 2, 3))))
    }
}
