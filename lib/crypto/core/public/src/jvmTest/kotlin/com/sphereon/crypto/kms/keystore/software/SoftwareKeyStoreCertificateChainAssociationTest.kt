/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.interop.derPublicKeyToJwk
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import kotlinx.coroutines.test.runTest
import java.io.FileInputStream
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SoftwareKeyStoreCertificateChainAssociationTest {
    private val providerId = "certificate-chain-association-test"
    private val password = "store-password"
    private val alias = "issuer-signing"

    @Test
    fun associatesCertificateChainWithExistingAliasUsingPublicKeyInfo() =
        runTest {
            withExistingKey { service, path, certificate, originalPrivateKey ->
                val publicKeyInfo = service.resolvePublicKeyByAlias(alias)

                service.storeCertificateChain(alias, arrayOf(certificate), publicKeyInfo)

                assertContentEquals(originalPrivateKey, privateKeyBytes(service, path))
            }
        }

    @Test
    fun associatesCertificateChainWithExistingAliasWithoutKeyInfo() =
        runTest {
            withExistingKey { service, path, certificate, originalPrivateKey ->
                service.storeCertificateChain(alias, arrayOf(certificate), keyInfo = null)

                assertContentEquals(originalPrivateKey, privateKeyBytes(service, path))
            }
        }

    private suspend fun withExistingKey(
        block: suspend (SoftwareKeyStoreService, String, Certificate, ByteArray) -> Unit,
    ) {
        val directory = Files.createTempDirectory("sks-chain-association").toFile().also { it.deleteOnExit() }
        val path = directory.resolve("software.jks").absolutePath
        val config =
            JksKeyStoreConfig(
                id = providerId,
                password = password,
                path = path,
                persist = true,
                overwriteAlias = true,
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
            )

        KeyStoreLoaderFactory.clearCache()
        try {
            val service = SoftwareKeyStoreService(config)
            service.storeKey(ecPrivateKey(), providerId, alias)
            service.awaitPendingPersistenceCompletion()

            val keyStore = loadKeyStore(path)
            val entry =
                keyStore.getEntry(alias, KeyStore.PasswordProtection(password.toCharArray())) as KeyStore.PrivateKeyEntry
            val certificate = certificateFromDer(entry.certificate.encoded)
            block(service, path, certificate, entry.privateKey.encoded)
        } finally {
            KeyStoreLoaderFactory.clearCache()
        }
    }

    private suspend fun privateKeyBytes(service: SoftwareKeyStoreService, path: String): ByteArray {
        service.awaitPendingPersistenceCompletion()
        val entry =
            loadKeyStore(path).getEntry(
                alias,
                KeyStore.PasswordProtection(password.toCharArray()),
            ) as KeyStore.PrivateKeyEntry
        return entry.privateKey.encoded
    }

    private fun loadKeyStore(path: String): KeyStore =
        KeyStore.getInstance("JKS").apply {
            FileInputStream(path).use { load(it, password.toCharArray()) }
        }

    private fun ecPrivateKey(): ResolvedKeyInfo<Jwk> {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val pair = generator.generateKeyPair()
        val privateJwk = derPrivateKeyToJwk(pair.private.encoded)
        val publicJwk = derPublicKeyToJwk(pair.public.encoded)
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
}
