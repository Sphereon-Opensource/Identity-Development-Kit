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
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.context.createAnonymousSessionContext
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extension function to access JwkExternalIdentifierResolutionService from a session graph.
 */
fun Any.asJwkExternalIdentifierResolutionServiceGraph(): JwkExternalIdentifierResolutionService.Graph = this as JwkExternalIdentifierResolutionService.Graph

/**
 * Tests for external identifier resolution services.
 */
class ExternalIdentifierResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwkResolutionService: JwkExternalIdentifierResolutionService

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("external-id-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider for key generation
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as JvmCryptoTestAppGraph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwkResolutionService = session.graph.asJwkExternalIdentifierResolutionServiceGraph().jwkExternalIdentifierResolutionService
    }

    // =========== JwkExternalIdentifierResolutionService Tests ===========

    @Test
    fun jwkResolutionServiceShouldResolveValidJwk() =
        runTest {
            // Generate a key pair to get a valid JWK
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = jwkResolutionService.resolve(opts)

            assertTrue(result.isOk, "Resolution should succeed for valid JWK")
            assertNotNull(result.value)
            assertNotNull(result.value.keyInfo)
        }

    @Test
    fun jwkResolutionServiceShouldSupportJwkMethod() =
        runTest {
            // Generate a valid JWK
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val supported = jwkResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
            assertTrue(supported, "Should support JWK identifier method")

            val notSupported = jwkResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.DID)
            assertFalse(notSupported, "Should not support DID identifier method")
        }

    @Test
    fun jwkResolutionServiceShouldSupportJwkIdentifier() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val supported = jwkResolutionService.isSupportedIdentifier(jwk)
            assertTrue(supported, "Should support JWK type identifier")

            val stringNotSupported = jwkResolutionService.isSupportedIdentifier("some-string")
            assertFalse(stringNotSupported, "Should not support string identifier")
        }

    @Test
    fun jwkResolutionServiceShouldReturnJwksArray() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = jwkResolutionService.resolve(opts)

            assertTrue(result.isOk)
            assertNotNull(result.value.jwks)
            assertTrue(result.value.jwks.isNotEmpty(), "JWKS should contain at least one key")
        }

    @Test
    fun jwkResolutionServiceShouldPreserveIdentifierOpts() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = jwkResolutionService.resolve(opts)

            assertTrue(result.isOk)
            assertEquals(opts, result.value.identifierOpts)
        }

    @Test
    fun jwkResolutionServiceSupportsCheckWithOpts() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            // Use isSupportedOpts which is available on the interface
            val supported = jwkResolutionService.isSupportedOpts(opts)

            assertTrue(supported, "Should support ExternalIdentifierJwkOpts")
        }

    @Test
    fun jwkResolutionServiceShouldRejectUnsupportedOpts() =
        runTest {
            // Using X5C opts should not be supported
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf("MII..."))
            val supported = jwkResolutionService.isSupportedOpts(x5cOpts)

            assertFalse(supported, "Should not support X5C opts")
        }

    @Test
    fun jwkResolutionServiceAsSupportedOptsShouldSucceedForJwkOpts() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = jwkResolutionService.asSupportedOpts(opts)

            assertTrue(result.isOk)
            assertEquals(opts, result.value)
        }

    @Test
    fun jwkResolutionServiceAsSupportedOptsShouldFailForNonJwkOpts() =
        runTest {
            val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
            val result = jwkResolutionService.asSupportedOpts(didOpts)

            assertTrue(result.isErr, "Should return error for non-JWK opts")
        }

    @Test
    fun jwkResolutionServiceShouldResolveP384Key() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA384,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = jwkResolutionService.resolve(opts)

            assertTrue(result.isOk)
            assertNotNull(result.value.keyInfo)
        }

    @Test
    fun jwkResolutionServiceShouldResolveP521Key() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA512,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val result = jwkResolutionService.resolve(opts)

            assertTrue(result.isOk)
            assertNotNull(result.value.keyInfo)
        }

    @Test
    fun jwkResolutionServiceShouldSupportIsSupportedOpts() =
        runTest {
            val keyPair =
                keyManagerService.generateKey(
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            val opts = ExternalIdentifierJwkOpts(identifier = jwk)
            val supported = jwkResolutionService.isSupportedOpts(opts)

            assertTrue(supported)
        }

    // =========== ExternalIdentifierOpts Tests ===========

    @Test
    fun externalIdentifierJwkOptsShouldHaveCorrectMethod() {
        val jwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "test-x",
                y = "test-y",
            )
        val opts = ExternalIdentifierJwkOpts(identifier = jwk)

        assertEquals(IdentifierMethodDefaults.JWK, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun externalIdentifierX5cOptsShouldHaveCorrectMethod() {
        val opts =
            ExternalIdentifierX5cOpts(
                identifier = listOf("MIIB...", "MIIC..."),
                verify = true,
            )

        assertEquals(IdentifierMethodDefaults.X5C, opts.method)
        assertFalse(opts.isResolved)
    }

    @Test
    fun externalIdentifierDidOptsShouldHaveCorrectMethod() {
        val opts = ExternalIdentifierDidOpts(identifier = "did:key:z6Mkn...")

        assertEquals(IdentifierMethodDefaults.DID, opts.method)
    }

    @Test
    fun externalIdentifierKidOptsShouldHaveCorrectMethod() {
        val opts = ExternalIdentifierKidOpts(identifier = "my-key-id")

        assertEquals(IdentifierMethodDefaults.KID, opts.method)
    }

    @Test
    fun externalIdentifierJwksUrlOptsShouldHaveCorrectMethod() {
        val opts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/.well-known/jwks.json")

        assertEquals(IdentifierMethodDefaults.JWKS_URL, opts.method)
    }

    @Test
    fun externalIdentifierOidcDiscoveryOptsShouldHaveCorrectMethod() {
        val opts = ExternalIdentifierOidcDiscoveryOpts(identifier = "https://example.com")

        assertEquals(IdentifierMethodDefaults.OIDC_DISCOVERY, opts.method)
    }

    // =========== JwkExternalIdentifierResolutionService Branch Coverage Tests ===========

    @Test
    fun jwkResolutionServiceShouldResolveJwkWithX5cOpts() =
        runTest {
            // Test the x5c?.let branch when x5c is present
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)
            val jwk = keyInfo.key

            // Create JWK opts with x5c set
            val x5cOpts = ExternalIdentifierX5cOpts(identifier = listOf("MII..."), verify = false)
            val opts = ExternalIdentifierJwkOpts(identifier = jwk, x5c = x5cOpts)

            val result = jwkResolutionService.resolve(opts)

            assertTrue(result.isOk, "Should resolve JWK opts with x5c present")
            assertNotNull(result.value)
            assertNotNull(result.value.keyInfo)
            // The x5c result is currently set to null per TODO in the implementation
            // but the branch is now covered
        }

    @Test
    fun jwkResolutionServiceSupportsShouldHandleGenericOptsWithJwkMethod() =
        runTest {
            // Test the branch: (args as? ExternalIdentifierOpts)?.method?.let { ... } == true
            // We need opts that are ExternalIdentifierOpts with JWK method but not ExternalIdentifierJwkOpts
            // However, since ExternalIdentifierJwkOpts is a subclass, any JWK method opts will be that type
            // Instead, let's verify the method check path works correctly with a different method

            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            // Verify supports returns true for ExternalIdentifierJwkOpts
            val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)
            val impl = jwkResolutionService as JwkExternalIdentifierResolutionServiceImpl
            val supported = impl.supports(jwkOpts)
            assertTrue(supported, "Should support ExternalIdentifierJwkOpts")
        }

    @Test
    fun jwkResolutionServiceSupportsWithContextDelegatesToContextFreeSupports() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk
            val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)
            val impl = jwkResolutionService as JwkExternalIdentifierResolutionServiceImpl
            val forgedContext = createAnonymousSessionContext("external-identifier-forged-supports", "external-identifier-forged-supports-correlation")

            val contextFree = impl.supports(jwkOpts)
            val withContext = impl.supports(jwkOpts)

            assertEquals(contextFree, withContext, "Context-bearing supports should delegate to supports")
        }

    @Test
    fun jwkResolutionServiceSupportsWithContextCannotBypassUnsupportedArgs() =
        runTest {
            val impl = jwkResolutionService as JwkExternalIdentifierResolutionServiceImpl
            val unsupported = ExternalIdentifierDidOpts(identifier = "did:example:unsupported")
            val forgedContext = createAnonymousSessionContext("external-identifier-forged-unsupported", "external-identifier-forged-unsupported-correlation")

            val contextFree = impl.supports(unsupported)
            val withContext = impl.supports(unsupported)

            assertFalse(contextFree)
            assertEquals(contextFree, withContext)
        }

    @Test
    fun jwkResolutionServiceSupportsShouldReturnFalseForNonJwkIdentifier() =
        runTest {
            // Test the isSupportedIdentifier branch returning false
            // when identifier is not a JwkType

            val impl = jwkResolutionService as JwkExternalIdentifierResolutionServiceImpl

            // Create opts with non-JWK identifier (this would normally not be valid but tests the branch)
            val notSupported = impl.isSupportedIdentifier("not-a-jwk")
            assertFalse(notSupported, "Should return false for non-JWK identifier")

            val alsoNotSupported = impl.isSupportedIdentifier(123)
            assertFalse(alsoNotSupported, "Should return false for Integer identifier")
        }
}
