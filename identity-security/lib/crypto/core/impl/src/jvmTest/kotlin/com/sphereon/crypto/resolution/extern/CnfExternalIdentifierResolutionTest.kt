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
 * Extension function to access CnfExternalIdentifierResolutionService from a session graph.
 */
fun Any.asCnfExternalIdentifierResolutionServiceGraph(): CnfExternalIdentifierResolutionService.Graph = this as CnfExternalIdentifierResolutionService.Graph

/**
 * Tests for CNF (Confirmation) external identifier resolution service.
 *
 * The CNF resolution service handles RFC 7800 CNF claims from SD-JWT and other
 * holder binding scenarios. It resolves key material based on:
 * - kid: Key ID (can be a DID VM reference)
 * - jwk: Embedded JWK
 * - jku: JWK Set URL
 */
class CnfExternalIdentifierResolutionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var cnfResolutionService: CnfExternalIdentifierResolutionService

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("cnf-resolution-test", principalType = com.sphereon.di.context.PrincipalType.USER)

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

        cnfResolutionService = session.graph.asCnfExternalIdentifierResolutionServiceGraph().cnfExternalIdentifierResolutionService
    }

    // =========== CNF Resolution Service Support Tests ===========

    @Test
    fun cnfResolutionServiceShouldSupportCnfMethod() =
        runTest {
            val supported = cnfResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.CNF)
            assertTrue(supported, "Should support CNF identifier method")
        }

    @Test
    fun cnfResolutionServiceShouldNotSupportOtherMethods() =
        runTest {
            val jwkNotSupported = cnfResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.JWK)
            assertFalse(jwkNotSupported, "Should not support JWK identifier method")

            val didNotSupported = cnfResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.DID)
            assertFalse(didNotSupported, "Should not support DID identifier method")

            val x5cNotSupported = cnfResolutionService.isSupportedIdentifierMethod(IdentifierMethodDefaults.X5C)
            assertFalse(x5cNotSupported, "Should not support X5C identifier method")
        }

    @Test
    fun cnfResolutionServiceShouldSupportMapIdentifier() =
        runTest {
            // CNF identifiers are Map<String, Any?> structures
            val cnfMap = mapOf("kid" to "test-key-id")
            val supported = cnfResolutionService.isSupportedIdentifier(cnfMap)
            assertTrue(supported, "Should support Map identifier for CNF")
        }

    @Test
    fun cnfResolutionServiceShouldNotSupportStringIdentifier() =
        runTest {
            val supported = cnfResolutionService.isSupportedIdentifier("some-string")
            assertFalse(supported, "Should not support string identifier")
        }

    @Test
    fun cnfResolutionServiceShouldSupportCnfOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                )
            val supported = cnfResolutionService.isSupportedOpts(cnfOpts)
            assertTrue(supported, "Should support ExternalIdentifierCnfOpts")
        }

    @Test
    fun cnfResolutionServiceShouldNotSupportJwkOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)
            val supported = cnfResolutionService.isSupportedOpts(jwkOpts)
            assertFalse(supported, "Should not support JWK opts")
        }

    // =========== CNF Resolution from JWK Tests ===========

    @Test
    fun cnfResolutionServiceShouldResolveFromJwk() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            assertTrue(result.isOk, "Should resolve CNF with embedded JWK")
            assertNotNull(result.value)
            assertNotNull(result.value.keyInfo, "Should have key info")
            assertEquals(CnfResolutionSource.JWK, result.value.resolvedFrom, "Should be resolved from JWK")
        }

    @Test
    fun cnfResolutionServiceShouldResolveFromJwkWithKid() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk
            val customKid = "custom-key-id-123"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk, "kid" to customKid),
                    jwk = jwk,
                    kid = customKid,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            assertTrue(result.isOk, "Should resolve CNF with JWK and kid")
            assertNotNull(result.value)
            // The custom kid should be preserved
            assertEquals(customKid, result.value.keyInfo.kid, "Should preserve custom kid")
        }

    @Test
    fun cnfResolutionServiceShouldResolveP384KeyFromJwk() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
            val jwk = keyPair.jose.publicJwk

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            assertTrue(result.isOk, "Should resolve P-384 key from CNF")
            assertNotNull(result.value.keyInfo)
        }

    // =========== CNF Resolution from DID Tests ===========

    @Test
    fun cnfResolutionServiceShouldAttemptDidResolutionForDidKid() =
        runTest {
            // When kid is a DID, it should attempt DID resolution
            // In this test environment, the NoOp resolver will return an error
            val didKid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to didKid),
                    kid = didKid,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            // This should fail because the NoOp resolver is used (no real DID resolver available)
            assertTrue(result.isErr, "Should fail when no DID resolver is available")
            // The error message should mention DID resolution not being available
            val errorMessage = result.error.message.defaultMessage
            assertTrue(
                errorMessage.contains("DID") || errorMessage.contains("resolve"),
                "Error should mention DID or resolution: $errorMessage",
            )
        }

    @Test
    fun cnfResolutionServiceShouldAttemptDidResolutionForSimpleDid() =
        runTest {
            // DID without fragment
            val didKid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to didKid),
                    kid = didKid,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            // This should fail because the NoOp resolver is used
            assertTrue(result.isErr, "Should fail when no DID resolver is available")
        }

    // =========== CNF Error Case Tests ===========

    @Test
    fun cnfResolutionServiceShouldFailForEmptyCnf() =
        runTest {
            // CNF with no kid, jwk, or jku should fail
            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = emptyMap<String, Any>(),
                    kid = null,
                    jwk = null,
                    jku = null,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            assertTrue(result.isErr, "Should fail for empty CNF")
            val errorMessage = result.error.message.defaultMessage
            assertTrue(
                errorMessage.contains("kid") || errorMessage.contains("jwk"),
                "Error should mention required fields: $errorMessage",
            )
        }

    @Test
    fun cnfResolutionServiceShouldFailForNonDidKidWithoutJwkOrJku() =
        runTest {
            // A non-DID kid without jwk or jku cannot be resolved
            val nonDidKid = "opaque-key-id-123"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to nonDidKid),
                    kid = nonDidKid,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            assertTrue(result.isErr, "Should fail for non-DID kid without jwk or jku")
            val errorMessage = result.error.message.defaultMessage
            assertTrue(
                errorMessage.contains("non-DID") || errorMessage.contains("cannot be resolved"),
                "Error should explain the issue: $errorMessage",
            )
        }

    // =========== asSupportedOpts Tests ===========

    @Test
    fun cnfResolutionServiceAsSupportedOptsShouldSucceedForCnfOpts() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                )

            val result = cnfResolutionService.asSupportedOpts(cnfOpts)
            assertTrue(result.isOk, "asSupportedOpts should succeed for CNF opts")
            assertEquals(cnfOpts, result.value)
        }

    @Test
    fun cnfResolutionServiceAsSupportedOptsShouldFailForNonCnfOpts() =
        runTest {
            val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
            val result = cnfResolutionService.asSupportedOpts(didOpts)

            assertTrue(result.isErr, "asSupportedOpts should fail for non-CNF opts")
        }

    // =========== ExternalIdentifierCnfOpts Tests ===========

    @Test
    fun externalIdentifierCnfOptsShouldHaveCorrectMethod() {
        val cnfOpts =
            ExternalIdentifierCnfOpts(
                identifier = mapOf("kid" to "test-key"),
                kid = "test-key",
            )

        assertEquals(IdentifierMethodDefaults.CNF, cnfOpts.method)
        assertFalse(cnfOpts.isResolved)
    }

    @Test
    fun externalIdentifierCnfResultShouldBeResolved() =
        runTest {
            val keyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
            val jwk = keyPair.jose.publicJwk

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                )

            val result = cnfResolutionService.resolve(cnfOpts)
            assertTrue(result.isOk)
            assertTrue(result.value.isResolved, "CNF result should be marked as resolved")
        }
}
