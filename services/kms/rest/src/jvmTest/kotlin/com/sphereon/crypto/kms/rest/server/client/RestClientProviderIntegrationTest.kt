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

package com.sphereon.crypto.kms.rest.server.client

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.kms.provider.rest.RestClientAuthConfig
import com.sphereon.crypto.kms.provider.rest.RestClientKmsProviderConfig
import com.sphereon.crypto.kms.provider.rest.RestClientKmsProviderImpl
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for RestClientKmsProvider that tests the client against a real Ktor server.
 * The server uses the software provider for actual key operations, so this is a full end-to-end test.
 *
 * This test also verifies tenant isolation - that tenant-specific keystores properly isolate keys
 * so that one tenant cannot access another tenant's keys.
 */
class RestClientProviderIntegrationTest {
    private lateinit var appGraph: TestApiAppGraph
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private lateinit var restClientKmsProvider: RestClientKmsProviderImpl

    @BeforeEach
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }

        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "$PROPERTY_PREFIX.type" to "software",
                "$PROPERTY_PREFIX.id" to "testsoftware",
                "$PROPERTY_PREFIX.persistKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.exposePrivateKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.keyStore.type" to "memory",
                "$PROPERTY_PREFIX.keyStore.id" to "test-memory-keystore",
                "$PROPERTY_PREFIX.keyStore.keyVisibility" to "private",
                "$PROPERTY_PREFIX.keyStore.overwriteAlias" to "true",
            ),
        )

        appGraph =
            createTestApiAppGraph(
                application = Unit,
                appId = "kms-rest-client-test",
                profile = "test",
                version = "1.0.0",
            )
        appGraph.userContextManager.destroyAll()

        server =
            embeddedServer(CIO, port = port) {
                install(KotlinInjectPlugin) {
                    this.appGraph = this@RestClientProviderIntegrationTest.appGraph
                    // The RestClient pushes tenant context as the X-Tenant-ID header (see
                    // RestClientAuthConfig.useTenantFromContext); this test resolver echoes the
                    // header so server-side context matches what the client put on the wire.
                    tenantResolver =
                        object : TenantResolver {
                            override fun resolve(call: ApplicationCall) = DefaultTenantInputString(call.request.header("X-Tenant-ID") ?: "default")
                        }
                }
                installUniversalHttpAdapters {
                    verboseLogging = true
                }
            }
        server.start(wait = false)
        runBlocking { delay(500) }

        val config =
            RestClientKmsProviderConfig(
                id = "testsoftware",
                restKmsUrl = "http://localhost:$port",
                authConfig =
                    RestClientAuthConfig(
                        usePrincipalFromContext = true,
                        useTenantFromContext = true,
                    ),
            )

        val userContextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString(TENANT_A_ID),
                DefaultPrincipalInputString(USER_A_ID),
            )
        val sessionGraph = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
        restClientKmsProvider =
            RestClientKmsProviderImpl(
                config,
                execution = sessionGraph.asCoreApiServiceGraph().serviceExecution,
            )
    }

    @AfterEach
    fun tearDown() {
        restClientKmsProvider.http.close()
        server.stop(1000, 2000)
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.type")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.id")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.persistKeysDuringGeneration")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.exposePrivateKeysDuringGeneration")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.type")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.id")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.keyVisibility")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.overwriteAlias")
    }

    @Test
    fun testSupportedCurves() {
        val curves = restClientKmsProvider.supportedCurves()
        assertContentEquals(
            arrayOf(Curve.P_256, Curve.P_384, Curve.P_521),
            curves,
        )
    }

    @Test
    fun testIsSupportedCurve() {
        assertTrue(restClientKmsProvider.isSupportedCurve(Curve.P_256))
        assertTrue(restClientKmsProvider.isSupportedCurve(Curve.P_384))
        assertTrue(restClientKmsProvider.isSupportedCurve(Curve.P_521))
        assertFalse(restClientKmsProvider.isSupportedCurve(Curve.X25519))
    }

    @Test
    fun testSupportedDigests() {
        val digests = restClientKmsProvider.supportedDigests()
        assertContentEquals(
            arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            digests,
        )
    }

    @Test
    fun testGenerateKeyAsync() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.cborToManagedKeyInfo().key.kid)
        }

    @Test
    fun testValidEcdsaRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(keyInfo)

            val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)

            val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidEcdsaRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(keyInfo)

            val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)

            val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test2".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testValidPSSRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            assertNotNull(keyInfo)

            val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)

            val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidPSSRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            assertNotNull(keyInfo)

            val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)

            val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testValidRsaPkcs1RawSignatureAndVerification() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.RSA_SHA256)
            assertNotNull(keyInfo)

            val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)

            val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidRsaPkcs1RawSignatureAndVerification() =
        runTest {
            val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.RSA_SHA256)
            assertNotNull(keyInfo)

            val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)

            val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testAllAlgSignaturesAndVerification() =
        runTest {
            restClientKmsProvider.supportedSignatureAlgorithms().forEach { alg ->
                val managedKeyPair = restClientKmsProvider.generateKeyAsync(alg = alg)
                val keyInfo = keyReference(managedKeyPair, alg)
                assertNotNull(keyInfo)

                val signature = restClientKmsProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
                assertNotNull(signature)

                val verification = restClientKmsProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
                assertTrue(verification)
            }
        }

    @Test
    fun testSupportedKeyInfoTypes() {
        val keyTypes = restClientKmsProvider.supportedKeyTypes()
        assertContentEquals(arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA), keyTypes)
    }

    @Test
    fun testSupportedAlg() {
        val algorithms = restClientKmsProvider.supportedSignatureAlgorithms()
        assertContentEquals(
            arrayOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_RAW,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
            ),
            algorithms,
        )
    }

    @Test
    fun shouldIsolateKeysBetweenTenantKeystores() =
        runTest {
            // Given - REST client for tenant A (set up in @BeforeEach as default)
            val tenantAClient = restClientKmsProvider

            // And - REST client for tenant B
            val tenantBConfig =
                RestClientKmsProviderConfig(
                    id = "testsoftware",
                    restKmsUrl = "http://localhost:$port",
                    authConfig =
                        RestClientAuthConfig(
                            usePrincipalFromContext = true,
                            useTenantFromContext = true,
                        ),
                )
            val tenantBContext =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString(TENANT_B_ID),
                    DefaultPrincipalInputString(USER_B_ID),
                )
            val tenantBSession = tenantBContext.sessionContextManager.createOrGetFromId("tenant-b-isolation-test-session")
            val tenantBClient =
                RestClientKmsProviderImpl(
                    tenantBConfig,
                    execution = tenantBSession.asCoreApiServiceGraph().serviceExecution,
                )

            try {
                // When - generate keys for both tenants
                val tenantAKey =
                    tenantAClient.generateKeyAsync(
                        alias = "tenant-a-isolation-key",
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    )
                assertNotNull(tenantAKey, "Tenant A key should be generated")

                val tenantBKey =
                    tenantBClient.generateKeyAsync(
                        alias = "tenant-b-isolation-key",
                        alg = SignatureAlgorithm.ECDSA_SHA384,
                    )
                assertNotNull(tenantBKey, "Tenant B key should be generated")

                // Then - tenant A only sees its own keys
                val tenantAKeys = tenantAClient.listKeys()
                assertTrue(tenantAKeys.any { it.alias == "tenant-a-isolation-key" }, "Tenant A should have its key")
                assertFalse(tenantAKeys.any { it.alias == "tenant-b-isolation-key" }, "Tenant A should not see tenant B's key")

                // And - tenant B only sees its own keys
                val tenantBKeys = tenantBClient.listKeys()
                assertTrue(tenantBKeys.any { it.alias == "tenant-b-isolation-key" }, "Tenant B should have its key")
                assertFalse(tenantBKeys.any { it.alias == "tenant-a-isolation-key" }, "Tenant B should not see tenant A's key")

                // And - keys have different KIDs
                assertTrue(tenantAKey.kid != tenantBKey.kid, "Keys from different tenants should have different KIDs")
            } finally {
                tenantBClient.http.close()
            }
        }

    @Test
    fun shouldMaintainKeyIsolationWithSameAliasAcrossTenants() =
        runTest {
            // Given - tenant A client (set up in @BeforeEach)
            val tenantAClient = restClientKmsProvider

            // And - tenant B client
            val tenantBConfig =
                RestClientKmsProviderConfig(
                    id = "testsoftware",
                    restKmsUrl = "http://localhost:$port",
                    authConfig =
                        RestClientAuthConfig(
                            usePrincipalFromContext = true,
                            useTenantFromContext = true,
                        ),
                )
            val tenantBContext =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString(TENANT_B_ID),
                    DefaultPrincipalInputString(USER_B_ID),
                )
            val tenantBSession = tenantBContext.sessionContextManager.createOrGetFromId("tenant-b-alias-session")
            val tenantBClient =
                RestClientKmsProviderImpl(
                    tenantBConfig,
                    execution = tenantBSession.asCoreApiServiceGraph().serviceExecution,
                )

            try {
                // When - both tenants generate keys with the SAME alias
                val sameAlias = "shared-alias-key"

                val tenantAKey =
                    tenantAClient.generateKeyAsync(
                        alias = sameAlias,
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    )
                val tenantBKey =
                    tenantBClient.generateKeyAsync(
                        alias = sameAlias,
                        alg = SignatureAlgorithm.ECDSA_SHA384,
                    )

                // Then - both keys exist but are different
                assertNotNull(tenantAKey)
                assertNotNull(tenantBKey)
                assertEquals(sameAlias, tenantAKey.alias)
                assertEquals(sameAlias, tenantBKey.alias)
                assertTrue(tenantAKey.kid != tenantBKey.kid, "Keys with same alias from different tenants should have different KIDs")

                // And - public key components are different
                assertTrue(
                    tenantAKey.jose.publicJwk.x != tenantBKey.jose.publicJwk.x,
                    "Keys from different tenants should have different public key components",
                )

                // And - each tenant only sees their own key
                val tenantAKeys = tenantAClient.listKeys()
                val tenantBKeys = tenantBClient.listKeys()
                assertEquals(1, tenantAKeys.size, "Tenant A should have 1 key")
                assertEquals(1, tenantBKeys.size, "Tenant B should have 1 key")
            } finally {
                tenantBClient.http.close()
            }
        }

    private fun keyReference(
        keyPair: ManagedKeyPair,
        signatureAlgorithm: SignatureAlgorithm? = null,
    ): KeyInfo<JwkType> =
        KeyInfo(
            kid = keyPair.kid,
            alias = keyPair.alias,
            providerId = keyPair.providerId,
            keyVisibility = KeyVisibility.PRIVATE,
            signatureAlgorithm = signatureAlgorithm,
            keyEncoding = KeyEncoding.JOSE,
        )

    companion object {
        const val TENANT_A_ID = "tenant-a"
        const val TENANT_B_ID = "tenant-b"
        const val USER_A_ID = "user-a"
        const val USER_B_ID = "user-b"
        private const val PROPERTY_PREFIX = "kms.providers.testsoftware"
    }
}
