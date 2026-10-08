/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.interop.derPublicKeyToJwk
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromBase64Der
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.kms.keystore.software.JksKeyStoreConfig
import com.sphereon.crypto.kms.keystore.software.SoftwareKeyStoreService
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * The software key store keeps the provider-issued self-signed certificate as the key entry's chain.
 * Storing a chain for that alias must replace it, so key reads and signing see the new `x5c`.
 */
class SoftwareKeyCertificateChainReplacementTest {
    private val providerId = "chain-replacement-test"
    private val password = "store-password"
    private val alias = "generated-signing-key"

    @Test
    fun storedChainReplacesTheSelfSignedCertificateInTheKeyX5c() =
        runTest {
            val directory = Files.createTempDirectory("kms-chain-replacement").toFile().also { it.deleteOnExit() }
            val service =
                SoftwareKeyStoreService(
                    JksKeyStoreConfig(
                        id = providerId,
                        password = password,
                        path = directory.resolve("software.jks").absolutePath,
                        persist = true,
                        overwriteAlias = true,
                        keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    ),
                )
            val keyPair = ecKeyPair()
            val selfSigned = certificate(keyPair, alias, keyPair, alias)
            service.storeKey(privateKeyInfo(keyPair), providerId, alias, arrayOf(selfSigned))
            assertX5c(listOf(selfSigned), service)

            val firstCa = ecKeyPair()
            val firstChain =
                listOf(
                    certificate(keyPair, "$alias-1", firstCa, "first-ca"),
                    certificate(firstCa, "first-ca", firstCa, "first-ca"),
                )
            service.storeCertificateChain(alias, firstChain.toTypedArray(), keyInfo = null)
            assertX5c(firstChain, service)

            val secondCa = ecKeyPair()
            val secondChain =
                listOf(
                    certificate(keyPair, "$alias-2", secondCa, "second-ca"),
                    certificate(secondCa, "second-ca", secondCa, "second-ca"),
                )
            service.storeCertificateChain(alias, secondChain.toTypedArray(), keyInfo = null)
            assertX5c(secondChain, service)
            service.awaitPendingPersistenceCompletion()
        }

    private suspend fun assertX5c(
        expected: List<Certificate>,
        service: SoftwareKeyStoreService,
    ) {
        val stored = service.getCertificateChain(alias)
        assertEquals(expected.size, stored.size)
        expected.zip(stored).forEach { (certificate, actual) -> assertContentEquals(certificate.der, actual.der) }

        val key = service.getKey(KeyInfo<Jwk>(alias = alias, providerId = providerId))
        val x5c = assertNotNull(key.x5c)
        assertEquals(expected.size, x5c.size)
        expected.zip(x5c.toList()).forEach { (certificate, encoded) ->
            assertContentEquals(certificate.der, certificateFromBase64Der(encoded).der)
        }
    }

    private fun privateKeyInfo(keyPair: KeyPair): ResolvedKeyInfo<Jwk> {
        val privateJwk = derPrivateKeyToJwk(keyPair.private.encoded)
        val publicJwk = derPublicKeyToJwk(keyPair.public.encoded)
        return ResolvedKeyInfo(
            key = privateJwk.copy(x = publicJwk.x, y = publicJwk.y, crv = privateJwk.crv ?: publicJwk.crv, kid = alias),
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            providerId = providerId,
            kid = alias,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        )
    }

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun certificate(
        subjectKeyPair: KeyPair,
        subjectName: String,
        issuerKeyPair: KeyPair,
        issuerName: String,
    ): Certificate {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
        val now = Clock.System.now()
        val holder = JcaX509v3CertificateBuilder(
            X500Name("CN=$issuerName"),
            BigInteger.valueOf(now.toEpochMilliseconds()),
            Date.from(java.time.Instant.ofEpochSecond((now - 1.days).epochSeconds)),
            Date.from(java.time.Instant.ofEpochSecond((now + 30.days).epochSeconds)),
            X500Name("CN=$subjectName"),
            subjectKeyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(issuerKeyPair.private))
        return certificateFromDer(JcaX509CertificateConverter().setProvider("BC").getCertificate(holder).encoded)
    }
}
