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
 */

package com.sphereon.crypto.kms.provider.software

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the key shape used by KmsBackedIdentifierProtector's lazy enc-key provisioning:
 * generating with `use=enc` and no signature algorithm must mint a key the provider can use
 * for AES-256-GCM content encryption when later referenced by alias only (the protector
 * calls encrypt with `KeyInfo(alias = "idfr:enc:<tenantId>", providerId = "software")`).
 */
class SoftwareKmsIdentifierEncKeyProvisioningTest {
    private lateinit var provider: SoftwareKmsProvider

    val ctx = SoftwareKmsTestContext("idfr-enc-key-test", this)

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "software",
                cryptographyProvider = CryptographyProvider.Default.name,
                persistKeysDuringGeneration = true,
                exposePrivateKeysDuringGeneration = true,
            )
        provider = ctx.softwareKmsProviderFactory.create(config, ctx.session.sessionExecution)
    }

    @Test
    fun useEncGeneratedKeyEncryptsAndDecryptsByAliasWithAad() =
        runTest {
            val alias = "idfr:enc:tenant-a"
            provider.generateKeyAsync(
                alias = alias,
                use = JwkUse.enc,
                keyOperations = null,
                alg = null,
                certificateOptions = null,
            )

            val plaintext = "owner@example.com"
            val aad = """{"purpose":"identifier-enc","tenant_id":"tenant-a"}""".encodeToByteArray()
            val keyInfo = KeyInfo<Nothing>(alias = alias, providerId = "software")

            val encrypted =
                provider.encrypt(
                    keyInfo = keyInfo,
                    plaintext = plaintext.encodeToByteArray(),
                    algorithm = ContentEncryptionAlgorithm.A256GCM,
                    additionalAuthenticatedData = aad,
                )
            assertEquals(12, encrypted.iv.size, "AES-GCM IV should be 12 bytes")
            assertEquals(16, encrypted.authTag.size, "AES-GCM auth tag should be 16 bytes")

            val decrypted =
                provider.decrypt(
                    keyInfo = keyInfo,
                    ciphertext = encrypted.ciphertext,
                    algorithm = ContentEncryptionAlgorithm.A256GCM,
                    iv = encrypted.iv,
                    authTag = encrypted.authTag,
                    additionalAuthenticatedData = aad,
                )
            assertEquals(plaintext, decrypted.decodeToString())
        }

    @Test
    fun encryptWithUnprovisionedAliasFailsNamingTheAlias() =
        runTest {
            val alias = "idfr:enc:never-provisioned"
            val exception =
                assertFailsWith<Exception> {
                    provider.encrypt(
                        keyInfo = KeyInfo<Nothing>(alias = alias, providerId = "software"),
                        plaintext = "x".encodeToByteArray(),
                        algorithm = ContentEncryptionAlgorithm.A256GCM,
                        additionalAuthenticatedData = null,
                    )
                }
            assertTrue(
                exception.message?.contains(alias) == true,
                "Encrypting with a missing key alias must fail with a message naming the alias; got ${exception::class.simpleName}: ${exception.message}",
            )
        }
}
