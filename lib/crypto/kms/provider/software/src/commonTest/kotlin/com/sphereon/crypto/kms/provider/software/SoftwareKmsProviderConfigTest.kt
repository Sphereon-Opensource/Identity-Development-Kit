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

import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KmsProviderConfigBinderImpl
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.keystore.software.Pkcs12KeyStoreConfig
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import com.sphereon.di.Order
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoftwareKmsProviderConfigTest {
    private val defaultProviderName = CryptographyProvider.Default.name
    private val decodeConfig =
        """{"id":"test-software","enabled":false,"exposePrivateKeysDuringGeneration":true,""" +
            """"order":10,"cryptographyProvider":"JDK","type":"software","autoCreateCertificate":true,""" +
            """"keyStore":{"type":"pkcs12","id":"test-software","enabled":false,"order":10,""" +
            """"password":"password","path":"test/path","accessMode":"read_write",""" +
            """"keyVisibility":"private","persist":true,"overwriteAlias":true}}"""
    private val encodeConfig =
        """{"id":"test-software","enabled":false,"exposePrivateKeysDuringGeneration":true,""" +
            """"order":10,"cryptographyProvider":"$defaultProviderName","autoCreateCertificate":true,""" +
            """"keyStore":{"type":"pkcs12","id":"test-software","enabled":false,"order":10,""" +
            """"password":"password","path":"test/path","accessMode":"read_write",""" +
            """"keyVisibility":"private","persist":true,"overwriteAlias":true}}"""

    val ctx = SoftwareKmsTestContext("test", this)

    private fun clearConfigCache() {
        ctx.clearConfigCache()
    }

    @BeforeTest
    fun clearCacheBeforeEachTest() {
        DefaultAppMapPropertySource.getSource().clear()
        DefaultPrincipalMapPropertySource.getSource().clear()
        clearConfigCache()
    }

    @Test
    fun testSoftwareKmsConfigToJson() =
        runTest {
            val config =
                SoftwareKmsProviderConfig(
                    id = "test-software",
                    cryptographyProvider = CryptographyProvider.Default.name,
                    order = Order.HIGHEST.orderValue,
                    enabled = false,
                    autoCreateCertificate = true,
                    keyStore =
                        Pkcs12KeyStoreConfig(
                            id = "test-software",
                            enabled = false,
                            order = Order.HIGHEST.orderValue,
                            password = "password",
                            path = "test/path",
                        ),
                )
            val json = CryptoJsonSupport.serializer.encodeToString(config)
            assertEquals(
                encodeConfig,
                json,
            )
        }

    @Test
    fun testJsonToSoftwareKmsConfig() =
        runTest {
            val config = CryptoJsonSupport.decodeKmsProviderConfig(decodeConfig)

            assertEquals("test-software", config.id)
            assertTrue { config is SoftwareKmsProviderConfig }
            config as SoftwareKmsProviderConfig
            assertEquals(false, config.enabled)
            assertEquals(Order.HIGHEST.orderValue, config.order)
            assertEquals("JDK", config.cryptographyProvider)
            assertEquals("software", config.type)

            assertEquals("test-software", config.keyStore.id)
            assertTrue { config.keyStore is Pkcs12KeyStoreConfig }
            config.keyStore as Pkcs12KeyStoreConfig
            assertEquals("pkcs12", config.keyStore.type)
            assertEquals(Order.HIGHEST.orderValue, config.keyStore.order)
            assertEquals("test/path", config.keyStore.path)
            assertEquals("password", config.keyStore.password)
            assertEquals("read_write", config.keyStore.accessMode)
            assertTrue(config.keyStore.persist)
            assertFalse(config.keyStore.enabled)
        }

    @Test
    fun testProviderUsingConfigObject() =
        runTest {
            val softwareKmsProvider =
                ctx.softwareKmsProviderFactory.create(
                    SoftwareKmsProviderConfig(
                        id = "test-software",
                        cryptographyProvider = CryptographyProvider.Default.name,
                    ),
                    ctx.session.sessionExecution,
                )
            assertNotNull(softwareKmsProvider)
            assertEquals("test-software", softwareKmsProvider.id)
        }

    @Test
    fun testKmsProviderManagerUsingConfigObject() =
        runTest {
            val softwareKmsProvider =
                ctx.kmsProviderManager.createFromProviderConfig(
                    SoftwareKmsProviderConfig(
                        id = "test-software",
                        cryptographyProvider = CryptographyProvider.Default.name,
                    ),
                    ctx.session.sessionExecution,
                )
            assertNotNull(softwareKmsProvider)
            assertEquals("test-software", softwareKmsProvider.id)
        }

    @Test
    fun testConfigBinderUsingConfigObject() =
        runTest {
            val binder = KmsProviderConfigBinderImpl(ctx.appLogManager)
            clearConfigCache()
            DefaultAppMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-software.type" to "software",
                    "kms.providers.test-software.id" to "test-software",
                ),
            )

            val config = binder.getKmsProviderConfig(ctx.appConfigService, "test-software")
            val softwareKmsProvider = ctx.kmsProviderManager.createFromProviderConfig(config, ctx.session.sessionExecution)
            assertNotNull(softwareKmsProvider)
            assertEquals("test-software", softwareKmsProvider.id)
        }

    @Test
    fun testConfigBinderGettingAllKmses() =
        runTest {
            val binder = KmsProviderConfigBinderImpl(ctx.appLogManager)
            clearConfigCache()
            DefaultAppMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-software.type" to "software",
                    "kms.providers.test-software.id" to "test-software",
                    "kms.providers.test-software2.type" to "software",
                    "kms.providers.test-software2.id" to "test2",
                ),
            )

            val configs = binder.getKmsProviderConfigs(ctx.appConfigService)
            assertEquals(2, configs.size)
            val softwareKmsProvider1 = ctx.kmsProviderManager.createFromProviderConfig(configs.first(), ctx.session.sessionExecution)
            assertNotNull(softwareKmsProvider1)
            val softwareKmsProvider2 = ctx.kmsProviderManager.createFromProviderConfig(configs.last(), ctx.session.sessionExecution)
            assertNotNull(softwareKmsProvider2)
            assertNotEquals(softwareKmsProvider1.id, softwareKmsProvider2.id)
        }

    @Test
    fun testMultipleKmsesFromProviderManagerAndProperties() =
        runTest {
            clearConfigCache()
            DefaultAppMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-software.type" to "software",
                    "kms.providers.test-software.id" to "test-software",
                    "kms.providers.test-software2.type" to "software",
                    "kms.providers.test-software2.id" to "test2",
                ),
            )
            val providers = ctx.kmsProviderManager.createFromProperties(ctx.appConfigService, ctx.session.sessionExecution)
            assertEquals(2, providers.size)
            val softwareKmsProvider1 = providers.first()
            assertNotNull(softwareKmsProvider1)
            val softwareKmsProvider2 = providers.last()
            assertNotNull(softwareKmsProvider2)
            assertNotEquals(softwareKmsProvider1.id, softwareKmsProvider2.id)
        }

    @Test
    fun testMultipleKmsProvidersWithActualKms() =
        runTest {
            clearConfigCache()
            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-software.type" to "software",
                    "kms.providers.test-software.id" to "test-software",
                    "kms.providers.test-software.keystore.type" to "memory",
                    "kms.providers.test-software.keystore.id" to "test-memory-keystore1",
                    "kms.providers.test-software.keystore.keyVisibility" to "private",
                    "kms.providers.test-software.keystore.overwriteAlias" to "true",
                    "kms.providers.test-software2.type" to "software",
                    "kms.providers.test-software2.id" to "test-software2",
                    "kms.providers.test-software2.keystore.type" to "memory",
                    "kms.providers.test-software2.keystore.id" to "test-memory-keystore2",
                    "kms.providers.test-software2.keystore.keyVisibility" to "private",
                    "kms.providers.test-software2.keystore.overwriteAlias" to "false",
                ),
            )

            ctx.app.userContextManager.destroyAll()
            val contextInstance = ctx.app.userContextManager.getAnonymous()
            val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("test-2kms").graph
            val kms = sessionGraph.asKeyManagerServiceGraph().keyManagerService
            assertNotNull(kms.defaultProviderId())
            assertEquals(2, kms.getProviderIds().size)
            assertNotNull(kms.getProvider("test-software"))
            assertNotNull(kms.generateKeyAsync())
        }

    @Test
    fun testEcdhKeyAgreementBetweenTwoParties() =
        runTest {
            clearConfigCache()
            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.test-ecdh.type" to "software",
                    "kms.providers.test-ecdh.id" to "test-ecdh",
                    "kms.providers.test-ecdh.keystore.type" to "memory",
                    "kms.providers.test-ecdh.keystore.id" to "test-ecdh-keystore",
                    "kms.providers.test-ecdh.keystore.keyVisibility" to "private",
                ),
            )

            ctx.app.userContextManager.destroyAll()
            val contextInstance = ctx.app.userContextManager.getAnonymous()
            val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("test-ecdh").graph
            val kms = sessionGraph.asKeyManagerServiceGraph().keyManagerService

            val aliceKeyPair =
                kms.generateKeyAsync(
                    providerId = "test-ecdh",
                    alias = "alice-key",
                    use = JwkUse.enc,
                    keyOperations = arrayOf(KeyOperations.DERIVE_KEY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            assertNotNull(aliceKeyPair)

            val bobKeyPair =
                kms.generateKeyAsync(
                    providerId = "test-ecdh",
                    alias = "bob-key",
                    use = JwkUse.enc,
                    keyOperations = arrayOf(KeyOperations.DERIVE_KEY),
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PRIVATE,
                )
            assertNotNull(bobKeyPair)

            val alicePrivateKeyInfo = aliceKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val alicePublicKeyInfo = aliceKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val bobPrivateKeyInfo = bobKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val bobPublicKeyInfo = bobKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val provider = kms.getProvider("test-ecdh")
            assertNotNull(provider)

            val aliceSharedSecret =
                provider.performKeyAgreement(
                    privateKeyInfo = alicePrivateKeyInfo,
                    publicKeyInfo = bobPublicKeyInfo,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                )

            val bobSharedSecret =
                provider.performKeyAgreement(
                    privateKeyInfo = bobPrivateKeyInfo,
                    publicKeyInfo = alicePublicKeyInfo,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                )

            assertContentEquals(aliceSharedSecret, bobSharedSecret)
            assertTrue(aliceSharedSecret.isNotEmpty())
            assertEquals(32, aliceSharedSecret.size)
        }
}
