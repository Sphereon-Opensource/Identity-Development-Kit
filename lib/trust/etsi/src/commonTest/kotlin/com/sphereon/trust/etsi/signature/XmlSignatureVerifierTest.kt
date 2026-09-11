/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.trust.etsi.signature

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.x509.X509VerificationRequestType
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.ensureDomAvailable
import com.sphereon.trust.etsi.testutil.readTestResource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XmlSignatureVerifierTest {
    private val testContext = EtsiTestContext("xml-signature-test", this)

    @Test
    fun explicitlyPinnedEmbeddedSignerIsNotRejectedByChainPolicy() =
        runTest {
            ensureDomAvailable()
            val xml = readTestResource("eu-lotl/eu-lotl.xml")
            val signerCertificate =
                xml
                    .substringAfter("<ds:KeyInfo><ds:X509Data><ds:X509Certificate>")
                    .substringBefore("</ds:X509Certificate>")
                    .decodeFrom(Encoding.BASE64)
            val verifier = createVerifier()

            val result =
                verifier.verifyFromString(
                    xml,
                    XmlSignatureVerificationOptions(trustedRoots = listOf(signerCertificate)),
                )

            assertFalse(result.reasonCodes.contains(TrustDiagnosticReasonCodes.SIGNER_CHAIN_INVALID))
            assertFalse(result.reasonCodes.contains(TrustDiagnosticReasonCodes.EMBEDDED_CERTIFICATE_NOT_TRUSTED))
            assertTrue(result.errorMessage == "Signature validation failed")
        }

    @Test
    fun unrelatedConfiguredSignerRootIsRejected() =
        runTest {
            ensureDomAvailable()
            val xml = readTestResource("eu-lotl/eu-lotl.xml")
            val result =
                createVerifier().verifyFromString(
                    xml,
                    XmlSignatureVerificationOptions(trustedRoots = listOf(byteArrayOf(1, 2, 3))),
                )

            assertFalse(result.valid)
            assertTrue(result.reasonCodes.contains(TrustDiagnosticReasonCodes.SIGNER_CHAIN_INVALID))
        }

    private fun createVerifier(): XmlSignatureVerifier =
        XmlUtilSignatureVerifier(
            keyManagerService = testContext.keyManagerService,
            x509VerifyService = RejectingX509VerifyService,
            execution = testContext.session.asCoreApiServiceGraph().serviceExecution,
        )

    private object RejectingX509VerifyService : X509VerifyService {
        override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
            X509VerificationResult(
                certificateChain = emptyArray(),
                critical = false,
                message = "test chain rejected",
                error = true,
            )

        override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService = this

        override fun getTrustedCerts(): Array<String>? = null
    }
}
