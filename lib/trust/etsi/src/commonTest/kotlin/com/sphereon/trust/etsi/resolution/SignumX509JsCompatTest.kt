/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.resolution

import at.asitplus.awesn1.crypto.pki.X509Certificate
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.crypto.core.interop.x509CertificateFromBase64
import com.sphereon.crypto.core.interop.x509CertificateFromDer
import com.sphereon.crypto.core.x509.x509DerOrPemToPem
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.FIDES_TL_URL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Targeted test for signum X509Certificate parsing on JS/wasmJs.
 *
 * Signum 3.16.3 has an ASN.1 parsing bug on JS where X509Certificate.decodeFromByteArray()
 * fails with "Asn1Exception: Illegal length" or "Invalid certificate data".
 * This test extracts a real certificate from FIDES trust list test data and verifies parsing.
 *
 * When signum is upgraded past 3.16.3, these tests should pass on all platforms.
 */
class SignumX509JsCompatTest {
    private val ctx = EtsiTestContext("signum-x509-compat-test", this)

    private suspend fun extractCertBase64(): String {
        val tlXml = ctx.fetchUrl(FIDES_TL_URL)
        val parser =
            com.sphereon.trust.etsi.parser
                .StreamingETSITrustListParser()
        val tl = parser.parseFromString(tlXml)
        return tl.trustedEntities
            .first()
            .trustedEntityServices
            .first()
            .serviceInformation.serviceDigitalIdentity.x509Certificates
            .first()
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    @Test
    fun base64DecodingMatchesStdlib() =
        runTest {
            val certBase64 = extractCertBase64()
            val ourResult = certBase64.decodeFromBase64()
            val stdResult =
                kotlin.io.encoding.Base64
                    .decode(certBase64)

            assertEquals(
                stdResult.size,
                ourResult.size,
                "Decoded sizes differ: stdlib=${stdResult.size}, ours=${ourResult.size}",
            )

            for (i in ourResult.indices) {
                assertEquals(
                    stdResult[i],
                    ourResult[i],
                    "Byte mismatch at index $i: stdlib=0x${(stdResult[i].toInt() and 0xFF).toString(16)}, " +
                        "ours=0x${(ourResult[i].toInt() and 0xFF).toString(16)}",
                )
            }
        }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    @Test
    fun signumCanParseWithStdlibBase64() =
        runTest {
            // Test with stdlib Base64 decoder to isolate whether the issue is
            // in our base64 decoding or in signum's ASN.1 parser
            val certBase64 = extractCertBase64()
            val der =
                kotlin.io.encoding.Base64
                    .decode(certBase64)
            val parsed = x509CertificateFromDer(der)
            assertNotNull(parsed, "awesn1 should parse cert decoded with stdlib Base64")
        }

    @Test
    fun signumCanParseExtractedCertificate() =
        runTest {
            val certBase64 = extractCertBase64()
            val parsed = x509CertificateFromBase64(certBase64)
            assertNotNull(parsed, "Signum should parse the X509 certificate from base64")
            assertNotNull(parsed.tbsCertificate, "Parsed certificate should have TBS data")
        }

    @Test
    fun x509DerOrPemToPemRoundTrip() =
        runTest {
            val certBase64 = extractCertBase64()

            // x509DerOrPemToPem internally calls x509CertificateFromBase64 + encodeToPem
            val pem = x509DerOrPemToPem(certBase64)
            assertNotNull(pem, "Should convert DER base64 to PEM")
            assertTrue(pem.contains("-----BEGIN CERTIFICATE-----"), "PEM should have header")
            assertTrue(pem.contains("-----END CERTIFICATE-----"), "PEM should have footer")
        }
}
