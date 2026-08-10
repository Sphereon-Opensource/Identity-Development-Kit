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

package com.sphereon.crypto.resolution

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import dev.whyoleg.cryptography.CryptographyProvider
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extension function to access MultiIdentifierResolutionService from a session graph.
 */
fun Any.asMultiIdentifierResolutionServiceGraph(): IdentifierService.Graph = this as IdentifierService.Graph

/**
 * Tests for MultiIdentifierResolutionService which aggregates both managed and external identifier services.
 */
class MultiIdentifierResolutionServiceTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var multiIdentifierService: IdentifierService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("multi-identifier-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        multiIdentifierService = session.graph.asMultiIdentifierResolutionServiceGraph().identifierService
    }

    // =========== MultiIdentifierResolutionService Tests ===========

    @Test
    fun multiIdentifierServiceShouldSupportKeyMethod() =
        runTest {
            val supported = multiIdentifierService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KEY)
            assertTrue(supported, "Should support KEY method")
        }

    @Test
    fun multiIdentifierServiceShouldSupportJwkMethod() =
        runTest {
            val supported = multiIdentifierService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
            assertTrue(supported, "Should support JWK method")
        }

    @Test
    fun multiIdentifierServiceShouldSupportX5cMethod() =
        runTest {
            val supported = multiIdentifierService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
            assertTrue(supported, "Should support X5C method")
        }

    @Test
    fun multiIdentifierServiceShouldSupportKidMethod() =
        runTest {
            val supported = multiIdentifierService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KID)
            assertTrue(supported, "Should support KID method")
        }

    @Test
    fun multiIdentifierServiceShouldSupportKeyAliasMethod() =
        runTest {
            val supported = multiIdentifierService.isSupportedIdentifierMethod(IdentifierMethodDefaults.KEY_ALIAS)
            assertTrue(supported, "Should support KEY_ALIAS method")
        }

    @Test
    fun multiIdentifierServiceShouldSupportCoseKeyMethod() =
        runTest {
            val supported = multiIdentifierService.isSupportedIdentifierMethod(IdentifierMethodDefaults.COSE_KEY)
            assertTrue(supported, "Should support COSE_KEY method")
        }

    @Test
    fun multiIdentifierServiceSupportedMethodsShouldIncludeMultipleMethods() =
        runTest {
            val supportedMethods = multiIdentifierService.supportedIdentifierMethods
            assertTrue(supportedMethods.isNotEmpty(), "Should have supported methods")
            assertTrue(supportedMethods.contains(IdentifierMethodDefaults.KEY), "Should contain KEY")
            assertTrue(supportedMethods.contains(IdentifierMethodDefaults.JWK), "Should contain JWK")
        }

    @Test
    fun multiIdentifierServiceShouldSupportKeyTypeIdentifier() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val key = keyPair.jose.publicJwk!!

            val supported = multiIdentifierService.isSupportedIdentifier(key)
            assertTrue(supported, "Should support KeyType identifier")
        }

    @Test
    fun multiIdentifierServiceShouldSupportManagedOptsKeyInfo() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val opts =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(),
                )

            val supported = multiIdentifierService.isSupportedOpts(opts)
            assertTrue(supported, "Should support ManagedOptsKeyInfo")
        }

    @Test
    fun multiIdentifierServiceShouldSupportExternalIdentifierJwkOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk!!

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val supported = multiIdentifierService.isSupportedOpts(opts)
            assertTrue(supported, "Should support ExternalIdentifierJwkOpts")
        }

    @Test
    fun multiIdentifierServiceShouldResolveManagedOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = keyPair.jose.privateJwk!!,
                    alias = null,
                    providerId = null,
                )

            val storedKey =
                keyManagerService.storeKey(
                    keyInfo = resolvedKeyInfo,
                    providerId = "test-software-provider",
                    alias = "multi-test-key",
                    certChain = null,
                )

            val opts =
                ManagedOptsKeyInfo(
                    identifier = storedKey,
                    context = IdentifierContext(),
                )

            val result = multiIdentifierService.resolve(opts)
            assertTrue(result.isOk, "Should resolve managed opts")
            assertNotNull(result.value)
            assertTrue(result.value.isResolved, "Result should be resolved")
        }

    @Test
    fun multiIdentifierServiceShouldResolveExternalJwkOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk!!

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = multiIdentifierService.resolve(opts)
            assertTrue(result.isOk, "Should resolve external JWK opts")
            assertNotNull(result.value)
            assertTrue(result.value.isResolved, "Result should be resolved")
        }

    @Test
    fun multiIdentifierServiceAsSupportedOptsShouldSucceedForManagedOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

            val opts =
                ManagedOptsKeyInfo(
                    identifier = keyInfo,
                    context = IdentifierContext(),
                )

            val result = multiIdentifierService.asSupportedOpts(opts)
            assertTrue(result.isOk, "asSupportedOpts should succeed for managed opts")
        }

    @Test
    fun multiIdentifierServiceAsSupportedOptsShouldSucceedForExternalOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk!!

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = multiIdentifierService.asSupportedOpts(opts)
            assertTrue(result.isOk, "asSupportedOpts should succeed for external opts")
        }
}
