/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.ProviderCertificateIdCanonicalizer
import com.sphereon.crypto.core.kms.ProviderCertificateLookup
import com.sphereon.crypto.core.kms.ProviderCertificateReference
import com.sphereon.crypto.core.kms.ProviderCertificateReferenceService
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultProviderCertificateReferenceInspectorTest {
    @Test
    fun registryCancellationPreservesTheOriginalInstance() =
        runTest {
            val cancellation = CancellationException("cancel provider lookup")
            val inspector = DefaultProviderCertificateReferenceInspector(registry { throw cancellation })

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

    @Test
    fun acceptedIdSpellingsAreNormalizedToTheProviderCanonicalId() =
        runTest {
            val inspector = DefaultProviderCertificateReferenceInspector(registry { provider() })

            listOf("$VAULT/certificates/certificate-1/v1", "v1", "certificate-1:v1").forEach { requested ->
                val result = inspector.inspect(
                    providerId = "provider-1",
                    alias = "certificate-1",
                    providerCertificateId = requested,
                    kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                )
                assertTrue(result.isOk, "expected $requested to be accepted")
                assertEquals("certificate-1:v1", result.value.id)
            }
        }

    @Test
    fun otherVaultOrOtherCertificateIsRefused() =
        runTest {
            val inspector = DefaultProviderCertificateReferenceInspector(registry { provider() })

            listOf(
                "https://other.example/certificates/certificate-1/v1",
                "$VAULT/certificates/certificate-2/v1",
                "certificate-2:v1",
                "certificate-1:v2",
            ).forEach { requested ->
                val result = inspector.inspect(
                    providerId = "provider-1",
                    alias = "certificate-1",
                    providerCertificateId = requested,
                    kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                )
                assertTrue(result.isErr, "expected $requested to be refused")
                assertEquals("KMS_PROVIDER_CERTIFICATE_IDENTITY_MISMATCH", result.error.code)
            }
        }

    private fun registry(lookup: suspend (String) -> KmsProvider): KmsProviderRegistry =
        object : KmsProviderRegistry {
            override fun defaultProviderId(): String = "provider-1"
            override fun getProviderIds(): Array<String> = arrayOf("provider-1")
            override suspend fun getProviderById(id: String): KmsProvider = lookup(id)
            override suspend fun getProvider(providerId: String?, alg: SignatureAlgorithm?): KmsProvider = error("unused")
            override suspend fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm): KmsProvider = error("unused")
            override fun registerProvider(provider: KmsProvider, makeDefaultKms: Boolean?) = error("unused")
        }

    /**
     * A provider whose id syntax accepts a vault URL, a bare version, or `alias:version`, always
     * resolving version `v1` of the requested alias.
     */
    private class UrlStyleCertificates(private val certificate: Certificate) :
        ProviderCertificateReferenceService,
        ProviderCertificateIdCanonicalizer {
        override fun canonicalCertificateId(lookup: ProviderCertificateLookup): IdkResult<String?, IdkError> {
            val id = lookup.id ?: return Ok<String?>(null).asResult()
            val canonical =
                when {
                    id.startsWith("$VAULT/certificates/") -> id.removePrefix("$VAULT/certificates/").replace('/', ':')
                    id.startsWith("https://") -> null
                    ':' in id -> id
                    else -> "${lookup.alias}:$id"
                }
            return if (canonical == null || canonical.substringBefore(':') != lookup.alias) {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "invalid")).asResult()
            } else {
                Ok<String?>(canonical).asResult()
            }
        }

        override suspend fun getCertificate(
            lookup: ProviderCertificateLookup,
        ): IdkResult<ProviderCertificateReference, IdkError> =
            Ok(ProviderCertificateReference("provider-1", lookup.alias, "${lookup.alias}:v1", certificate)).asResult()
    }

    private fun provider(): KmsProvider {
        val certificates = UrlStyleCertificates(testCertificate())
        return Proxy.newProxyInstance(
            KmsProvider::class.java.classLoader,
            arrayOf(
                KmsProvider::class.java,
                ProviderCertificateReferenceService::class.java,
                ProviderCertificateIdCanonicalizer::class.java,
            ),
        ) { _, method, args ->
            when {
                method.declaringClass == ProviderCertificateReferenceService::class.java ||
                    method.declaringClass == ProviderCertificateIdCanonicalizer::class.java ->
                    try {
                        method.invoke(certificates, *(args ?: emptyArray()))
                    } catch (invocation: InvocationTargetException) {
                        throw invocation.targetException
                    }
                method.name == "getId" -> "provider-1"
                method.name == "hashCode" -> 1
                method.name == "equals" -> false
                method.name == "toString" -> "provider-1"
                else -> error("KmsProvider.${method.name} must not be called")
            }
        } as KmsProvider
    }

    private fun testCertificate(): Certificate {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val subject = X500Name("CN=certificate-1")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.ONE,
            Date(now - 86_400_000L),
            Date(now + 86_400_000L),
            subject,
            keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(keyPair.private))
        return certificateFromDer(JcaX509CertificateConverter().setProvider("BC").getCertificate(holder).encoded)
    }

    private companion object {
        const val VAULT = "https://vault.example"
    }
}
