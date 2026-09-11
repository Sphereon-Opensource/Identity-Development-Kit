/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for kid-based key resolution in the JVM software (PKCS12) keystore.
 *
 * Production symptom: an HMAC_SHA256 SecretKeyEntry stored under alias
 * "idfr:bi:<tenantId>" is retrievable via [KeyInfo.alias], but a lookup with the same
 * value carried ONLY as [KeyInfo.kid] (the shape GenerateMacCommand produces from
 * GenerateMacArgs.keyId) fails with "Need to provide a alias".
 */
class SoftwareKeyStoreServiceKidResolutionTest {
    private val hmacAlias = "idfr:bi:application"

    private fun newPkcs12Service(): SoftwareKeyStoreService {
        val dir = Files.createTempDirectory("sks-kid-test").toFile()
        dir.deleteOnExit()
        val config =
            Pkcs12KeyStoreConfig(
                id = "test-pkcs12",
                password = "test-password",
                path = dir.resolve("test-keystore.p12").absolutePath,
                persist = true,
                overwriteAlias = true,
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
            )
        return SoftwareKeyStoreService(config)
    }

    private fun hmacResolvedKeyInfo(alias: String): ResolvedKeyInfo<Jwk> {
        val keyBytes = Random.nextBytes(32)
        val jwk =
            Jwk(
                kty = JwaKeyType.oct,
                k = keyBytes.encodeToBase64Url(),
                alg = JwaAlgorithm.HS256,
                kid = alias,
            )
        return ResolvedKeyInfo(
            key = jwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.Symmetric,
            alias = alias,
            providerId = "test-pkcs12",
            kid = alias,
            signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
        )
    }

    @Test
    fun hmacSecretKeyEntryIsListedByListKeys() =
        runTest {
            val service = newPkcs12Service()
            service.storeKey(hmacResolvedKeyInfo(hmacAlias), "test-pkcs12", hmacAlias)

            val refs = service.listKeys()
            val match = refs.find { it.alias == hmacAlias }
            assertNotNull(match, "listKeys() must enumerate the HMAC SecretKeyEntry stored under alias $hmacAlias; got aliases: ${refs.map { it.alias }}")
        }

    @Test
    fun certificateCatalogIgnoresSecretKeyEntriesWithoutCertificateChains() =
        runTest {
            val service = newPkcs12Service()
            service.storeKey(hmacResolvedKeyInfo(hmacAlias), "test-pkcs12", hmacAlias)

            assertTrue(
                service.listCertificateAliases().isEmpty(),
                "A key-only PKCS12 entry must not fail or appear in the trusted-certificate catalog",
            )
        }

    @Test
    fun hmacKeyStoredByAliasIsResolvableByAlias() =
        runTest {
            val service = newPkcs12Service()
            val stored = service.storeKey(hmacResolvedKeyInfo(hmacAlias), "test-pkcs12", hmacAlias)
            assertEquals(hmacAlias, stored.alias)

            val resolved = service.getKey(KeyInfo<Jwk>(alias = hmacAlias))
            assertEquals(hmacAlias, resolved.alias)
            assertEquals(hmacAlias, resolved.kid)
            assertNotNull((resolved.key as? JwkType)?.k, "Resolved HMAC key must carry symmetric key material")
        }

    /**
     * The GenerateMacCommand path: SoftwareKmsProvider.resolveHmacKeyBytes() looks the key up
     * with `KeyInfo(kid = keyId)` where keyId is in fact the alias the key was provisioned
     * under. The keystore must resolve this, since a direct alias lookup with the same value
     * demonstrably succeeds.
     */
    @Test
    fun hmacKeyStoredByAliasIsResolvableByKidOnly() =
        runTest {
            val service = newPkcs12Service()
            val originalK =
                hmacResolvedKeyInfo(hmacAlias).let { keyInfo ->
                    service.storeKey(keyInfo, "test-pkcs12", hmacAlias)
                    keyInfo.key.k
                }

            val resolved = service.getKey(KeyInfo<Jwk>(kid = hmacAlias))
            assertEquals(hmacAlias, resolved.alias)
            val resolvedK = (resolved.key as? JwkType)?.k
            assertEquals(originalK, resolvedK, "Key material resolved by kid must match the stored HMAC key")
        }

    /**
     * Kid-based resolution must not be stricter than alias-based resolution. PKCS12 alias
     * lookups are case-insensitive on the JVM, so a direct alias get with different casing
     * succeeds; the same value carried as kid must resolve too. Before the direct
     * alias-shaped fallback in matchKey() this failed with "Need to provide a alias"
     * because the listKeys() metadata scan compares aliases with exact string equality.
     */
    @Test
    fun hmacKeyIsResolvableByKidWithDifferentAliasCase() =
        runTest {
            val service = newPkcs12Service()
            service.storeKey(hmacResolvedKeyInfo(hmacAlias), "test-pkcs12", hmacAlias)

            val mixedCase = "IDFR:BI:Application"

            // Direct alias get with different casing succeeds (PKCS12 is case-insensitive)
            val byAlias = service.getKey(KeyInfo<Jwk>(alias = mixedCase))
            assertNotNull((byAlias.key as? JwkType)?.k, "Alias lookup must be case-insensitive on PKCS12")

            // The same value carried only as kid must resolve as well
            val byKid = service.getKey(KeyInfo<Jwk>(kid = mixedCase))
            assertNotNull((byKid.key as? JwkType)?.k, "Kid lookup must resolve when a direct alias get with the same value succeeds")
        }

    @Test
    fun hmacKeyLookupFailsClosedWhenAliasIsMissingEvenIfKidExists() =
        runTest {
            val service = newPkcs12Service()
            val fallbackKid = "qa-license-recipient"
            service.storeKey(hmacResolvedKeyInfo(fallbackKid), "test-pkcs12", fallbackKid)

            val exception =
                assertFailsWith<Exception> {
                    service.getKey(KeyInfo<Jwk>(alias = "license-recipient", kid = fallbackKid))
                }

            assertTrue(exception.message?.contains("license-recipient") == true)
        }

    /**
     * Same as above but across a persistence round trip: the key is written to disk by one
     * service instance and resolved by kid through a fresh instance over the same file,
     * mirroring a service restart in production.
     */
    @Test
    fun hmacKeyIsResolvableByKidAfterReload() =
        runTest {
            KeyStoreLoaderFactory.clearCache()
            SoftwareKeyStoreStateCache.clear()
            val dir = Files.createTempDirectory("sks-kid-reload-test").toFile()
            dir.deleteOnExit()
            val path = dir.resolve("reload-keystore.p12").absolutePath
            val config =
                Pkcs12KeyStoreConfig(
                    id = "test-pkcs12",
                    password = "test-password",
                    path = path,
                    persist = true,
                    overwriteAlias = true,
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                )

            try {
                val originalKeyInfo = hmacResolvedKeyInfo(hmacAlias)
                val originalK = originalKeyInfo.key.k
                val writer = SoftwareKeyStoreService(config)
                writer.storeKey(originalKeyInfo, "test-pkcs12", hmacAlias)
                writer.awaitPendingPersistenceCompletion()
                assertTrue(java.io.File(path).exists(), "Keystore file must have been persisted")

                // Model a process restart rather than another service instance in the same process:
                // neither the loaded KeyStore nor its resolved-key cache may satisfy the read.
                KeyStoreLoaderFactory.clearCache()
                SoftwareKeyStoreStateCache.clear()

                val reader = SoftwareKeyStoreService(config)
                val resolved = reader.getKey(KeyInfo<Jwk>(kid = hmacAlias))
                assertEquals(hmacAlias, resolved.alias)
                val resolvedK = (resolved.key as? JwkType)?.k
                assertTrue(
                    originalK != null && resolvedK != null && originalK == resolvedK,
                    "Reloaded HMAC key material must exactly match the value persisted by the writer",
                )
            } finally {
                KeyStoreLoaderFactory.clearCache()
                SoftwareKeyStoreStateCache.clear()
            }
        }
}
