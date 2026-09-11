/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DefaultProviderCertificateReferenceInspectorTest {
    @Test
    fun registryCancellationPreservesTheOriginalInstance() =
        runTest {
            val cancellation = CancellationException("cancel provider lookup")
            val inspector = DefaultProviderCertificateReferenceInspector(
                object : KmsProviderRegistry {
                    override fun defaultProviderId(): String = "provider-1"
                    override fun getProviderIds(): Array<String> = arrayOf("provider-1")
                    override suspend fun getProviderById(id: String): KmsProvider = throw cancellation
                    override suspend fun getProvider(providerId: String?, alg: SignatureAlgorithm?): KmsProvider = error("unused")
                    override suspend fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider = error("unused")
                    override fun registerProvider(provider: KmsProvider, makeDefaultKms: Boolean?) = error("unused")
                },
            )

            val actual = assertFailsWith<CancellationException> {
                inspector.inspect(
                    providerId = "provider-1",
                    alias = "certificate-1",
                    providerCertificateId = null,
                    kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                )
            }

            assertSame(cancellation, actual)
        }
}
