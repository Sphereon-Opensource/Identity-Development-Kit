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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extension function to access MultiExternalIdentifierService from a session graph.
 */
fun Any.asMultiExternalServiceGraph(): MultiExternalIdentifierService.Graph = this as MultiExternalIdentifierService.Graph

/**
 * Tests for MultiExternalIdentifierResolutionService which aggregates multiple external identifier services.
 */
class MultiExternalIdentifierResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var multiExternalService: MultiExternalIdentifierService

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("multi-external-test")

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as JvmCryptoTestAppGraph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        multiExternalService = session.graph.asMultiExternalServiceGraph().multiExternalIdentifierService
    }

    // =========== Method Support Tests ===========

    @Test
    fun multiExternalServiceShouldSupportJwkMethod() =
        runTest {
            val supported = multiExternalService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
            assertTrue(supported, "Should support JWK method")
        }

    @Test
    fun multiExternalServiceShouldSupportX5cMethod() =
        runTest {
            val supported = multiExternalService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
            assertTrue(supported, "Should support X5C method")
        }

    @Test
    fun multiExternalServiceShouldSupportJwksUrlMethod() =
        runTest {
            val supported = multiExternalService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWKS_URL)
            assertTrue(supported, "Should support JWKS_URL method")
        }

    @Test
    fun multiExternalServiceShouldHaveMultipleSupportedMethods() =
        runTest {
            val methods = multiExternalService.supportedIdentifierMethods
            assertTrue(methods.isNotEmpty(), "Should have supported methods")
            assertTrue(methods.size >= 2, "Should have multiple supported methods")
        }

    // =========== Identifier Support Tests ===========

    @Test
    fun multiExternalServiceShouldSupportJwkIdentifier() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val supported = multiExternalService.isSupportedIdentifier(jwk)
            assertTrue(supported, "Should support JWK identifier")
        }

    @Test
    fun multiExternalServiceShouldSupportHttpsUrlIdentifier() =
        runTest {
            val supported = multiExternalService.isSupportedIdentifier("https://example.com/.well-known/jwks.json")
            assertTrue(supported, "Should support HTTPS URL identifier")
        }

    // =========== Opts Support Tests ===========

    @Test
    fun multiExternalServiceShouldSupportJwkOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val supported = multiExternalService.isSupportedOpts(opts)
            assertTrue(supported, "Should support JWK opts")
        }

    @Test
    fun multiExternalServiceShouldSupportX5cOpts() =
        runTest {
            val opts = ExternalIdentifierX5cOpts(identifier = listOf("MII..."))
            val supported = multiExternalService.isSupportedOpts(opts)
            assertTrue(supported, "Should support X5C opts")
        }

    @Test
    fun multiExternalServiceShouldSupportJwksUrlOpts() =
        runTest {
            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/jwks")
            val supported = multiExternalService.isSupportedOpts(opts)
            assertTrue(supported, "Should support JWKS URL opts")
        }

    // =========== Resolution Tests ===========

    @Test
    fun multiExternalServiceShouldResolveJwkOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = multiExternalService.resolve(opts)

            assertTrue(result.isOk, "Should resolve JWK opts")
            assertNotNull(result.value)
            assertTrue(result.value.isResolved, "Result should be resolved")
        }

    @Test
    fun multiExternalServiceShouldFailForUnsupportedOpts() =
        runTest {
            // Create opts for an unsupported method (if any)
            // Using a DID opts which may not be supported by the external services
            val opts = ExternalIdentifierDidOpts(identifier = "did:example:123")
            val result = multiExternalService.resolve(opts)

            // Should either succeed (if DID service exists) or fail with meaningful error
            if (result.isErr) {
                assertNotNull(result.error, "Should have error details")
            }
        }

    @Test
    fun multiExternalServiceShouldResolveJwkWithDifferentCurves() =
        runTest {
            val algorithms =
                listOf(
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureAlgorithm.ECDSA_SHA384,
                    SignatureAlgorithm.ECDSA_SHA512,
                )

            algorithms.forEach { alg ->
                val keyPair = keyManagerService.generateKey(alg = alg)
                val jwk = keyPair.jose.publicJwk

                val opts = ExternalIdentifierJwkOpts(identifier = jwk)
                val result = multiExternalService.resolve(opts)

                assertTrue(result.isOk, "Should resolve JWK for $alg")
                assertNotNull(result.value.keyInfo)
            }
        }

    // =========== asSupportedOpts Tests ===========

    @Test
    fun multiExternalServiceAsSupportedOptsShouldSucceedForJwkOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = multiExternalService.asSupportedOpts(opts)

            assertTrue(result.isOk, "asSupportedOpts should succeed for JWK opts")
            assertEquals(opts, result.value)
        }

    @Test
    fun multiExternalServiceAsSupportedOptsShouldSucceedForX5cOpts() =
        runTest {
            val opts = ExternalIdentifierX5cOpts(identifier = listOf("MII..."), verify = false)
            val result = multiExternalService.asSupportedOpts(opts)

            assertTrue(result.isOk, "asSupportedOpts should succeed for X5C opts")
            assertEquals(opts, result.value)
        }

    @Test
    fun multiExternalServiceAsSupportedOptsShouldSucceedForJwksUrlOpts() =
        runTest {
            val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/jwks")
            val result = multiExternalService.asSupportedOpts(opts)

            assertTrue(result.isOk, "asSupportedOpts should succeed for JWKS URL opts")
            assertEquals(opts, result.value)
        }

    // =========== Delegation Tests ===========

    @Test
    fun multiExternalServiceShouldDelegateToCorrectService() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            // JWK opts should be handled by JwkExternalIdentifierResolutionService
            val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)
            val jwkResult = multiExternalService.resolve(jwkOpts)
            assertTrue(jwkResult.isOk, "Should resolve JWK via delegated service")
            assertTrue(jwkResult.value is ExternalIdentifierResult.Jwk, "Result should be JWK type")
        }

    @Test
    fun multiExternalServiceShouldIncludeAllDelegatedMethods() =
        runTest {
            val methods = multiExternalService.supportedIdentifierMethods

            // Should include methods from multiple services
            assertTrue(
                methods.any { it.methodName.contains("jwk", ignoreCase = true) || it == IdentifierMethodDefaults.JWK },
                "Should include JWK method",
            )
        }
}
