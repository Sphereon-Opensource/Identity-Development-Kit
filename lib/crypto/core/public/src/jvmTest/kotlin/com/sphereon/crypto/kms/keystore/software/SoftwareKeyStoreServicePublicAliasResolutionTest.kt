/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.interop.derPublicKeyToJwk
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import kotlinx.coroutines.test.runTest
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoftwareKeyStoreServicePublicAliasResolutionTest {
    private val providerId = "public-alias-test"
    private val storePassword = "store-password"
    private val entryPassword = "different-entry-password"

    private fun ecPrivateKey(alias: String): ResolvedKeyInfo<Jwk> {
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

    private fun rsaPrivateKey(alias: String): ResolvedKeyInfo<Jwk> {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privateJwk = derPrivateKeyToJwk(pair.private.encoded)
        val publicJwk = derPublicKeyToJwk(pair.public.encoded)
        return ResolvedKeyInfo(
            key = privateJwk.copy(n = publicJwk.n, e = publicJwk.e, kid = alias),
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.RSA,
            alias = alias,
            providerId = providerId,
            kid = alias,
            signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
        )
    }

    private fun hmacKey(alias: String): ResolvedKeyInfo<Jwk> =
        ResolvedKeyInfo(
            key =
                Jwk(
                    kty = JwaKeyType.oct,
                    k = Random.nextBytes(32).encodeToBase64Url(),
                    alg = JwaAlgorithm.HS256,
                    kid = alias,
                ),
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.Symmetric,
            alias = alias,
            providerId = providerId,
            kid = alias,
            signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
        )

    @Test
    fun publicAliasResolutionDoesNotExposeThePrivateEntryOrStorageWrapper() =
        runTest {
            val alias = "issuer-signing"
            val directory = Files.createTempDirectory("sks-public-alias").toFile().also { it.deleteOnExit() }
            val path = directory.resolve("software.jks").absolutePath
            val config =
                JksKeyStoreConfig(
                    id = providerId,
                    password = storePassword,
                    path = path,
                    persist = true,
                    overwriteAlias = true,
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                )

            KeyStoreLoaderFactory.clearCache()
            try {
                val writer = SoftwareKeyStoreService(config)
                writer.storeKey(ecPrivateKey(alias), providerId, alias)
                writer.awaitPendingPersistenceCompletion()

                val keyStore = KeyStore.getInstance("JKS")
                FileInputStream(path).use { keyStore.load(it, storePassword.toCharArray()) }
                val privateEntry =
                    keyStore.getEntry(alias, KeyStore.PasswordProtection(storePassword.toCharArray())) as KeyStore.PrivateKeyEntry
                keyStore.setKeyEntry(alias, privateEntry.privateKey, entryPassword.toCharArray(), privateEntry.certificateChain)
                FileOutputStream(path).use { keyStore.store(it, storePassword.toCharArray()) }
                KeyStoreLoaderFactory.clearCache()

                val reader = SoftwareKeyStoreService(config)
                assertFailsWith<Exception> {
                    reader.getKey(KeyInfo<Jwk>(alias = alias, keyVisibility = KeyVisibility.PUBLIC))
                }

                val resolved = reader.resolvePublicKeyByAlias(alias)
                val publicJwk = resolved.key as JwkType
                assertEquals(alias, resolved.alias)
                assertEquals(providerId, resolved.providerId)
                assertEquals(KeyVisibility.PUBLIC, resolved.keyVisibility)
                assertEquals(JwaKeyType.EC, publicJwk.kty)
                assertNull(publicJwk.d)
                assertNull(publicJwk.k)
                assertNotNull(publicJwk.x)
                assertNotNull(publicJwk.y)
                assertNull(publicJwk.x5c, "The internal storage wrapper must never become a logical certificate chain")
            } finally {
                KeyStoreLoaderFactory.clearCache()
            }
        }

    @Test
    fun missingAliasFailsWithTheExactAliasInTheMessage() =
        runTest {
            val directory = Files.createTempDirectory("sks-public-missing").toFile().also { it.deleteOnExit() }
            val service =
                SoftwareKeyStoreService(
                    JksKeyStoreConfig(
                        id = providerId,
                        password = storePassword,
                        path = directory.resolve("software.jks").absolutePath,
                    ),
                )
            val alias = "missing-verification-key"

            val exception = assertFailsWith<Exception> { service.resolvePublicKeyByAlias(alias) }

            assertTrue(exception.message?.contains(alias) == true, "Missing-key error must identify alias $alias")
        }

    @Test
    fun symmetricAliasIsRejectedAsHavingNoPublicVerificationMaterial() =
        runTest {
            val alias = "oauth-hmac"
            val directory = Files.createTempDirectory("sks-public-symmetric").toFile().also { it.deleteOnExit() }
            val service =
                SoftwareKeyStoreService(
                    Pkcs12KeyStoreConfig(
                        id = providerId,
                        password = storePassword,
                        path = directory.resolve("software.p12").absolutePath,
                        overwriteAlias = true,
                    ),
                )
            service.storeKey(hmacKey(alias), providerId, alias)

            val exception = assertFailsWith<Exception> { service.resolvePublicKeyByAlias(alias) }

            assertTrue(
                exception.message?.contains(alias) == true,
                "Symmetric-key rejection must identify alias $alias",
            )
        }

    @Test
    fun bareRsaKeyUsesALogicalCertificateChainWrapperInJksAndPkcs12() =
        runTest {
            listOf("jks", "p12").forEach { format ->
                val alias = "rsa-wrapper-$format"
                val directory = Files.createTempDirectory("sks-rsa-wrapper-$format").toFile().also { it.deleteOnExit() }
                val config =
                    if (format == "jks") {
                        JksKeyStoreConfig(
                            id = providerId,
                            password = storePassword,
                            path = directory.resolve("software.jks").absolutePath,
                            persist = true,
                            overwriteAlias = true,
                            keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                        )
                    } else {
                        Pkcs12KeyStoreConfig(
                            id = providerId,
                            password = storePassword,
                            path = directory.resolve("software.p12").absolutePath,
                            persist = true,
                            overwriteAlias = true,
                            keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                        )
                    }

                KeyStoreLoaderFactory.clearCache()
                try {
                    val writer = SoftwareKeyStoreService(config)
                    writer.storeKey(rsaPrivateKey(alias), providerId, alias)
                    writer.awaitPendingPersistenceCompletion()
                    assertFalse(writer.deleteCertificateChain(alias), "A storage wrapper is not a logical certificate chain")
                    assertFalse(writer.listCertificateChainAliases().contains(alias))
                    assertFailsWith<Exception> { writer.getCertificateChain(alias) }

                    KeyStoreLoaderFactory.clearCache()
                    val reader = SoftwareKeyStoreService(config)
                    val resolved = reader.getKey(KeyInfo<Jwk>(alias = alias))
                    assertEquals(JwaKeyType.RSA, (resolved.key as JwkType).kty)
                    assertTrue(reader.deleteKey(KeyInfo<Jwk>(alias = alias)))
                    reader.awaitPendingPersistenceCompletion()
                    assertFailsWith<Exception> { reader.getKey(KeyInfo<Jwk>(alias = alias)) }
                } finally {
                    KeyStoreLoaderFactory.clearCache()
                }
            }
        }
}
