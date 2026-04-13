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

package com.sphereon.did.methods.key

import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidResolutionOptions
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the did:key resolver implementation.
 *
 * Test vectors are based on the did:key specification:
 * https://w3c-ccg.github.io/did-method-key/
 */
class KeyDidResolverImplTest {

    private val resolver = KeyDidResolverImpl()

    /**
     * Test resolving a did:key with Ed25519 key.
     * Test vector from the did:key specification.
     */
    @Test
    fun shouldResolveEd25519DidKey(): TestResult = runTest {
        // This is the example Ed25519 did:key from the spec
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

        val result = resolver.resolve(did, DidResolutionOptions())

        assertTrue(result.isOk, "Resolution should succeed")
        val resolution = result.getOrThrow()

        // Verify document ID matches the DID
        assertEquals(did, resolution.didDocument?.id)

        // Verify verification method exists
        val verificationMethods = resolution.didDocument?.verificationMethod
        assertNotNull(verificationMethods, "Should have verification methods")
        assertTrue(verificationMethods.isNotEmpty(), "Should have at least one verification method")

        // Verify the primary VM has the correct structure
        val primaryVm = verificationMethods.first()
        assertEquals("JsonWebKey2020", primaryVm.type)
        assertEquals(did, primaryVm.controller)
        assertNotNull(primaryVm.publicKeyJwk, "Should have JWK")

        // Verify JWK properties
        val jwk = primaryVm.publicKeyJwk!!
        assertEquals("OKP", jwk.kty.value)
        assertEquals("Ed25519", jwk.crv?.value)
        assertNotNull(jwk.x, "JWK should have x coordinate")

        // Verify authentication relationship
        val authMethods = resolution.verificationMethodsByPurpose[VerificationPurpose.AUTHENTICATION]
        assertNotNull(authMethods, "Should have authentication methods")
        assertTrue(authMethods.isNotEmpty(), "Should have at least one authentication method")

        // Verify assertion method relationship
        val assertMethods = resolution.verificationMethodsByPurpose[VerificationPurpose.ASSERTION_METHOD]
        assertNotNull(assertMethods, "Should have assertion methods")
        assertTrue(assertMethods.isNotEmpty(), "Should have at least one assertion method")

        // Ed25519 keys should also derive X25519 for key agreement
        val keyAgreementMethods = resolution.verificationMethodsByPurpose[VerificationPurpose.KEY_AGREEMENT]
        assertNotNull(keyAgreementMethods, "Ed25519 should derive X25519 for key agreement")
    }

    /**
     * Test resolving did:key and checking metadata.
     */
    @Test
    fun shouldReturnCorrectMetadata(): TestResult = runTest {
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

        val result = resolver.resolve(did, DidResolutionOptions())

        assertTrue(result.isOk, "Resolution should succeed")
        val resolution = result.getOrThrow()

        // Verify resolution metadata
        assertEquals("application/did+ld+json", resolution.didResolutionMetadata.contentType)

        // did:key is immutable, so document metadata should be minimal
        assertNotNull(resolution.didDocumentMetadata)
    }

    /**
     * Test that invalid DIDs are rejected.
     */
    @Test
    fun shouldRejectInvalidDid(): TestResult = runTest {
        val invalidDid = "not-a-did"

        val result = resolver.resolve(invalidDid, DidResolutionOptions())

        assertTrue(result.isErr, "Should reject invalid DID format")
    }

    /**
     * Test that DIDs with wrong method are rejected.
     */
    @Test
    fun shouldRejectWrongMethod(): TestResult = runTest {
        val wrongMethodDid = "did:web:example.com"

        val result = resolver.resolve(wrongMethodDid, DidResolutionOptions())

        assertTrue(result.isErr, "Should reject DID with wrong method")
    }

    /**
     * Test dereferencing a verification method by fragment.
     */
    @Test
    fun shouldDereferenceVerificationMethod(): TestResult = runTest {
        val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

        // First resolve to get the VM ID
        val resolveResult = resolver.resolve(did, DidResolutionOptions())
        assertTrue(resolveResult.isOk, "Resolution should succeed")
        val vmId = resolveResult.getOrThrow().didDocument?.verificationMethod?.first()?.id

        assertNotNull(vmId, "Should have verification method ID")

        // Now dereference the specific VM
        val derefResult = resolver.dereference(vmId)

        assertTrue(derefResult.isOk, "Dereference should succeed")
        val deref = derefResult.getOrThrow()

        assertNotNull(deref.verificationMethod, "Should return verification method")
        assertEquals(vmId, deref.verificationMethod?.id)
    }

    /**
     * Test that resolver reports correct capabilities.
     */
    @Test
    fun shouldReportCorrectCapabilities(): TestResult = runTest {
        val capabilities = resolver.capabilities

        assertEquals("key", capabilities.method)

        // did:key supports creation but not updates
        assertTrue(capabilities.lifecycle.create, "Should support create")
        assertTrue(!capabilities.lifecycle.update, "Should not support update")
        assertTrue(!capabilities.lifecycle.deactivate, "Should not support deactivate")

        // Check supported key types
        assertTrue(capabilities.keyManagement.supportedKeyTypes.isNotEmpty(), "Should have supported key types")
    }

    /**
     * Test that supported methods list is correct.
     */
    @Test
    fun shouldReportSupportedMethods(): TestResult = runTest {
        val methods = resolver.supportedMethods

        assertEquals(listOf("key"), methods)
    }
}
