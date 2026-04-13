/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.did.resolver.impl.testutil.DidResolverTestContext
import com.sphereon.did.models.VerificationPurpose
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E2E integration tests for DID resolution through the full DI component hierarchy.
 *
 * These tests verify that:
 * 1. DID method resolvers are properly discovered via multibinding
 * 2. The DidResolverRegistry correctly routes to the appropriate resolver
 * 3. Method-specific interfaces can be injected directly
 * 4. Resolution works correctly for did:key and did:jwk methods
 */
class DidResolverRegistryE2ETest {

    private lateinit var ctx: DidResolverTestContext

    @BeforeTest
    fun setUp() {
        ctx = DidResolverTestContext("did-resolver-test", this)
    }

    @Test
    fun testResolverRegistryDiscoversMethods() = runTest {
        // Verify that the registry has discovered the DID method resolvers via multibinding
        val supportedMethods = ctx.resolverRegistry.getSupportedMethods()

        assertTrue(supportedMethods.contains("key"), "Registry should support did:key method")
        assertTrue(supportedMethods.contains("jwk"), "Registry should support did:jwk method")
    }

    @Test
    fun testResolveDidKeyViaRegistry() = runTest {
        // Test vector: Ed25519 did:key from the specification
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

        val result = ctx.resolverRegistry.resolve(did)

        assertTrue(result.isOk, "Resolution via registry should succeed")
        val resolution = result.getOrThrow()

        // Verify document structure
        assertEquals(did, resolution.didDocument?.id)
        assertNotNull(resolution.didDocument?.verificationMethod)
        assertTrue(resolution.didDocument!!.verificationMethod!!.isNotEmpty())

        // Verify verification methods by purpose
        assertNotNull(resolution.verificationMethodsByPurpose[VerificationPurpose.AUTHENTICATION])
        assertNotNull(resolution.verificationMethodsByPurpose[VerificationPurpose.ASSERTION_METHOD])
    }

    @Test
    fun testResolveDidKeyViaSpecificInterface() = runTest {
        // Test that we can use the method-specific interface directly
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

        val result = ctx.keyDidResolver.resolve(did)

        assertTrue(result.isOk, "Resolution via KeyDidResolver interface should succeed")
        val resolution = result.getOrThrow()

        assertEquals(did, resolution.didDocument?.id)

        // Verify JWK is present
        val vm = resolution.didDocument?.verificationMethod?.first()
        assertNotNull(vm?.publicKeyJwk, "Verification method should have JWK")
        assertEquals("OKP", vm?.publicKeyJwk?.kty?.value)
        assertEquals("Ed25519", vm?.publicKeyJwk?.crv?.value)
    }

    @Test
    fun testResolveDidJwkViaRegistry() = runTest {
        // Create a did:jwk from a simple JWK
        // This is the base64url encoding of {"kty":"OKP","crv":"Ed25519","x":"..."}
        val did = "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IlFfX0xNZXVFUjZNMjRUNFREYkdBSTZoTFlnbXFKSlB2Y1RfODdyNFlXcXMifQ"

        val result = ctx.resolverRegistry.resolve(did)

        assertTrue(result.isOk, "Resolution of did:jwk via registry should succeed")
        val resolution = result.getOrThrow()

        assertEquals(did, resolution.didDocument?.id)
        assertNotNull(resolution.didDocument?.verificationMethod)
    }

    @Test
    fun testResolveDidJwkViaSpecificInterface() = runTest {
        val did = "did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IlFfX0xNZXVFUjZNMjRUNFREYkdBSTZoTFlnbXFKSlB2Y1RfODdyNFlXcXMifQ"

        val result = ctx.jwkDidResolver.resolve(did)

        assertTrue(result.isOk, "Resolution via JwkDidResolver interface should succeed")
        val resolution = result.getOrThrow()

        assertEquals(did, resolution.didDocument?.id)
    }

    @Test
    fun testRegistryRejectsUnsupportedMethod() = runTest {
        // Try to resolve a DID with an unsupported method
        val did = "did:unsupported:12345"

        val result = ctx.resolverRegistry.resolve(did)

        assertTrue(result.isErr, "Resolution of unsupported method should fail")
        result.fold(
            success = { fail("Should not succeed") },
            failure = { error ->
                val msg = error.message.defaultMessage.lowercase()
                assertTrue(
                    "unsupported" in msg || "no resolver" in msg,
                    "Error should indicate unsupported method, but was: $msg"
                )
            }
        )
    }

    @Test
    fun testDereferenceVerificationMethod() = runTest {
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

        // First resolve to get the VM ID
        val resolution = ctx.resolverRegistry.resolve(did).getOrThrow()
        val vmId = resolution.didDocument?.verificationMethod?.first()?.id
        assertNotNull(vmId, "Should have verification method ID")

        // Now dereference the specific verification method
        val derefResult = ctx.resolverRegistry.dereference(vmId)

        assertTrue(derefResult.isOk, "Dereference should succeed")
        val deref = derefResult.getOrThrow()

        assertNotNull(deref.verificationMethod, "Should return verification method")
        assertEquals(vmId, deref.verificationMethod?.id)
    }

    @Test
    fun testResolverCapabilities() = runTest {
        // Test that capabilities are properly reported via the registry
        val keyCapabilities = ctx.resolverRegistry.getCapabilities("key")
        val jwkCapabilities = ctx.resolverRegistry.getCapabilities("jwk")

        assertNotNull(keyCapabilities, "Should have capabilities for did:key")
        assertNotNull(jwkCapabilities, "Should have capabilities for did:jwk")

        assertEquals("key", keyCapabilities.method)
        assertEquals("jwk", jwkCapabilities.method)

        // did:key and did:jwk are immutable - they support create but not update
        assertTrue(keyCapabilities.lifecycle.create, "did:key should support create")
        assertTrue(!keyCapabilities.lifecycle.update, "did:key should not support update")

        assertTrue(jwkCapabilities.lifecycle.create, "did:jwk should support create")
        assertTrue(!jwkCapabilities.lifecycle.update, "did:jwk should not support update")
    }
}
