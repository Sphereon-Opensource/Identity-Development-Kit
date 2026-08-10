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

package com.sphereon.did.resolver.impl

import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.extern.CnfExternalIdentifierResolutionService
import com.sphereon.crypto.resolution.extern.CnfResolutionSource
import com.sphereon.crypto.resolution.extern.ExternalIdentifierCnfOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.did.resolver.impl.testutil.createDidResolverTestAppGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E integration tests for CNF (Confirmation) claim resolution and DID resolution
 * via the External Identifier resolution system.
 *
 * These tests verify that:
 * 1. CNF claims with embedded JWKs are properly resolved
 * 2. CNF claims with DID-based kid values use the DID resolver
 * 3. CNF claims with non-DID kid and no JWK/JKU fail appropriately
 * 4. DIDs can be resolved via the ExternalIdentifier system
 */
class CnfAndDidIdentifierResolutionE2ETest {
    private lateinit var cnfResolver: CnfExternalIdentifierResolutionService
    private lateinit var didResolver: DidExternalIdentifierResolutionService
    private lateinit var didResolverImpl: DidExternalIdentifierResolutionServiceImpl
    private lateinit var didResolverImplForgedSession: DidExternalIdentifierResolutionServiceImpl

    @BeforeTest
    fun setUp() {
        // Create the full graph hierarchy via DI
        val app = createDidResolverTestAppGraph(testInstance = this)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("cnf-did-resolution-test", principalType = com.sphereon.di.context.PrincipalType.USER)

        val sessionGraph = session.graph

        // Access via the Graph interfaces contributed by the impl classes via @ContributesTo
        cnfResolver = (sessionGraph as CnfExternalIdentifierResolutionService.Graph).cnfExternalIdentifierResolutionService
        didResolver = (sessionGraph as DidExternalIdentifierResolutionServiceImpl.Graph).didExternalIdentifierResolutionService
        didResolverImpl = didResolver as DidExternalIdentifierResolutionServiceImpl

        val forgedSession = userContext.sessionContextManager.createOrGetFromId("cnf-did-resolution-test-forged", principalType = com.sphereon.di.context.PrincipalType.USER)
        val forgedSessionGraph = forgedSession.graph
        didResolverImplForgedSession =
            (forgedSessionGraph as DidExternalIdentifierResolutionServiceImpl.Graph).didExternalIdentifierResolutionService
                as DidExternalIdentifierResolutionServiceImpl
    }

    // ==================== CNF Resolution with Embedded JWK ====================

    @Test
    fun testCnfResolutionWithEmbeddedJwk() =
        runTest {
            // Create a CNF with an embedded JWK (Ed25519 key)
            val jwk =
                Jwk(
                    kty = JwaKeyType.OKP,
                    crv = JwaCurve.Ed25519,
                    x = "Q__LMeuER6M24T4DDbGAI6hLYgmqJJPvcT_87r4YWqs",
                )

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                    kid = "test-key-1",
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isOk, "CNF resolution with embedded JWK should succeed")
            val cnfResult = result.getOrThrow()

            // Verify the resolution source is JWK
            assertEquals(CnfResolutionSource.JWK, cnfResult.resolvedFrom, "Should resolve from embedded JWK")

            // Verify key info is present
            assertNotNull(cnfResult.keyInfo, "KeyInfo should be present")
            assertNotNull(cnfResult.keyInfo.key, "Key should be present in KeyInfo")

            // Verify the JWK is returned correctly
            assertEquals(1, cnfResult.jwks.size, "Should have exactly one JWK")
        }

    @Test
    fun testCnfResolutionWithEmbeddedEcJwk() =
        runTest {
            // Create a CNF with an embedded EC P-256 JWK
            val jwk =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                    y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
                )

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("jwk" to jwk),
                    jwk = jwk,
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isOk, "CNF resolution with EC JWK should succeed")
            val cnfResult = result.getOrThrow()

            assertEquals(CnfResolutionSource.JWK, cnfResult.resolvedFrom)
            assertNotNull(cnfResult.keyInfo.key)
        }

    // ==================== CNF Resolution with DID-based kid ====================

    @Test
    fun testCnfResolutionWithDidKeyKid() =
        runTest {
            // Use a did:key as the kid - this should trigger DID resolution
            val didKey = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to didKey),
                    kid = didKey,
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isOk, "CNF resolution with DID kid should succeed")
            val cnfResult = result.getOrThrow()

            // Verify the resolution source is DID
            assertEquals(CnfResolutionSource.DID, cnfResult.resolvedFrom, "Should resolve from DID")

            // Verify key info is present
            assertNotNull(cnfResult.keyInfo, "KeyInfo should be present")
            assertNotNull(cnfResult.keyInfo.key, "Key should be present in KeyInfo")
        }

    @Test
    fun testCnfResolutionWithDidJwkKid() =
        runTest {
            // Use a did:jwk as the kid
            val didJwk = "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IlFfX0xNZXVFUjZNMjRUNFREYkdBSTZoTFlnbXFKSlB2Y1RfODdyNFlXcXMifQ"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to didJwk),
                    kid = didJwk,
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isOk, "CNF resolution with did:jwk kid should succeed")
            val cnfResult = result.getOrThrow()

            assertEquals(CnfResolutionSource.DID, cnfResult.resolvedFrom)
            assertNotNull(cnfResult.keyInfo.key)
        }

    @Test
    fun testCnfResolutionWithDidKeyAndFragment() =
        runTest {
            // DID with verification method fragment
            val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
            val didWithFragment = "$did#z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to didWithFragment),
                    kid = didWithFragment,
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isOk, "CNF resolution with DID fragment should succeed")
            val cnfResult = result.getOrThrow()

            assertEquals(CnfResolutionSource.DID, cnfResult.resolvedFrom)
            assertNotNull(cnfResult.keyInfo.key)
        }

    // ==================== CNF Resolution Failure Cases ====================

    @Test
    fun testCnfResolutionFailsWithNonDidKidOnly() =
        runTest {
            // Non-DID kid without JWK or JKU should fail
            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to "just-a-key-id-12345"),
                    kid = "just-a-key-id-12345",
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isErr, "CNF resolution with non-DID kid only should fail")
            result.fold(
                success = { throw AssertionError("Should not succeed") },
                failure = { error ->
                    val msg = error.message.defaultMessage.lowercase()
                    assertTrue(
                        "non-did" in msg || "cannot be resolved" in msg || "cannot resolve" in msg,
                        "Error should indicate non-DID kid cannot be resolved, but was: $msg",
                    )
                },
            )
        }

    @Test
    fun testCnfResolutionFailsWithEmptyCnf() =
        runTest {
            // Empty CNF (no kid, jwk, or jku) should fail
            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = emptyMap(),
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isErr, "CNF resolution with empty CNF should fail")
            result.fold(
                success = { throw AssertionError("Should not succeed") },
                failure = { error ->
                    val msg = error.message.defaultMessage.lowercase()
                    assertTrue(
                        "at least one" in msg || "must contain" in msg,
                        "Error should indicate CNF must contain kid, jwk, or jku, but was: $msg",
                    )
                },
            )
        }

    @Test
    fun testCnfResolutionFailsWithUnsupportedDidMethod() =
        runTest {
            // DID with unsupported method should fail
            val unsupportedDid = "did:unsupported:12345"

            val cnfOpts =
                ExternalIdentifierCnfOpts(
                    identifier = mapOf("kid" to unsupportedDid),
                    kid = unsupportedDid,
                )

            val result = cnfResolver.resolve(cnfOpts)

            assertTrue(result.isErr, "CNF resolution with unsupported DID method should fail")
        }

    // ==================== DID Resolution via ExternalIdentifier System ====================

    @Test
    fun testDidResolutionViaExternalIdentifierSystem() =
        runTest {
            val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val didOpts = ExternalIdentifierDidOpts(identifier = did)

            val result = didResolver.resolve(didOpts)

            assertTrue(result.isOk, "DID resolution via ExternalIdentifier system should succeed")
            val didResult = result.getOrThrow()

            // Verify DID is correct
            assertEquals(did, didResult.did, "Resolved DID should match input")

            // Verify DID document is present
            assertNotNull(didResult.didDocument, "DID document should be present")
            assertEquals(did, didResult.didDocument!!.id, "DID document ID should match")

            // Verify verification methods are present
            assertNotNull(didResult.didDocument!!.verificationMethod, "Verification methods should be present")
            assertTrue(didResult.didDocument!!.verificationMethod!!.isNotEmpty(), "Should have verification methods")

            // Verify JWKs are extracted
            assertTrue(didResult.jwks.isNotEmpty(), "JWKs should be extracted from DID document")
            assertNotNull(didResult.keyInfo, "KeyInfo should be present")

            // Verify didJwks categorized by purpose
            assertNotNull(didResult.didJwks, "didJwks should be present")
            assertTrue(
                didResult.didJwks!!.containsKey("authentication") ||
                    didResult.didJwks!!.containsKey("assertionMethod"),
                "Should have authentication or assertion method JWKs",
            )

            // Verify parsed DID
            assertNotNull(didResult.didParsed, "Parsed DID should be present")
            assertEquals("key", didResult.didParsed!!.method, "DID method should be 'key'")
        }

    @Test
    fun testDidResolverSupportsWithContextDelegatesToContextFreeSupports() =
        runTest {
            val didOpts =
                ExternalIdentifierDidOpts(
                    identifier = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
                )
            val unsupportedDidOpts = ExternalIdentifierDidOpts(identifier = "did:unsupported:12345")

            assertTrue(didResolverImpl.supports(didOpts))
            assertEquals(didResolverImpl.supports(didOpts), didResolverImplForgedSession.supports(didOpts))

            assertFalse(didResolverImpl.supports(unsupportedDidOpts))
            assertEquals(didResolverImpl.supports(unsupportedDidOpts), didResolverImplForgedSession.supports(unsupportedDidOpts))

            assertFalse(didResolverImpl.supports("not-a-did-opts"))
            assertFalse(didResolverImplForgedSession.supports("not-a-did-opts"))
        }

    @Test
    fun testDidResolverExecuteIgnoresForgedSessionContext() =
        runTest {
            val didOpts =
                ExternalIdentifierDidOpts(
                    identifier = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
                )

            val result = didResolverImpl.execute(didOpts)
            val forgedSessionResult = didResolverImplForgedSession.execute(didOpts)

            assertTrue(result.isOk, "Execution should succeed for the runtime-owned session context")
            assertTrue(forgedSessionResult.isOk, "Execution should succeed for the forged-session resolver as well")
            assertEquals(didOpts.identifier, result.value.did)
            assertEquals(result.value.did, forgedSessionResult.value.did)
        }

    @Test
    fun testDidJwkResolutionViaExternalIdentifierSystem() =
        runTest {
            val did = "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IlFfX0xNZXVFUjZNMjRUNFREYkdBSTZoTFlnbXFKSlB2Y1RfODdyNFlXcXMifQ"

            val didOpts = ExternalIdentifierDidOpts(identifier = did)

            val result = didResolver.resolve(didOpts)

            assertTrue(result.isOk, "did:jwk resolution via ExternalIdentifier system should succeed")
            val didResult = result.getOrThrow()

            assertEquals(did, didResult.did)
            assertNotNull(didResult.didDocument)
            assertNotNull(didResult.didParsed)
            assertEquals("jwk", didResult.didParsed!!.method)
        }

    @Test
    fun testDidJwkFragmentResolvesBaseDocumentAndExactMethod() =
        runTest {
            val did = "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IlFfX0xNZXVFUjZNMjRUNFREYkdBSTZoTFlnbXFKSlB2Y1RfODdyNFlXcXMifQ"
            val didUrl = "$did#0"

            val result = didResolver.resolve(ExternalIdentifierDidOpts(identifier = didUrl))

            assertTrue(result.isOk, "did:jwk verification-method URL should resolve")
            val didResult = result.getOrThrow()
            assertEquals(didUrl, didResult.did)
            assertEquals(did, didResult.didDocument?.id)
            assertEquals(didUrl, didResult.keyInfo.kid)
        }

    @Test
    fun testDidResolutionFailsForUnsupportedMethod() =
        runTest {
            val did = "did:unsupported:12345"

            val didOpts = ExternalIdentifierDidOpts(identifier = did)

            val result = didResolver.resolve(didOpts)

            assertTrue(result.isErr, "Resolution of unsupported DID method should fail")
            result.fold(
                success = { throw AssertionError("Should not succeed") },
                failure = { error ->
                    val msg = error.message.defaultMessage.lowercase()
                    assertTrue(
                        "no resolver" in msg || "unsupported" in msg || "not found" in msg,
                        "Error should indicate no resolver for method, but was: $msg",
                    )
                },
            )
        }

    @Test
    fun testDidResolutionFailsForInvalidDid() =
        runTest {
            val invalidDid = "not-a-valid-did"

            val didOpts = ExternalIdentifierDidOpts(identifier = invalidDid)

            val result = didResolver.resolve(didOpts)

            assertTrue(result.isErr, "Resolution of invalid DID should fail")
        }
}
