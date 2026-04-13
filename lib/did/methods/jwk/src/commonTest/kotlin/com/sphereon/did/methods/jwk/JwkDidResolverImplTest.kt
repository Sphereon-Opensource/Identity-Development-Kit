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

package com.sphereon.did.methods.jwk

import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidResolutionOptions
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the did:jwk resolver implementation.
 *
 * did:jwk encodes a JWK directly in the DID using base64url encoding.
 */
class JwkDidResolverImplTest {
    private val resolver = JwkDidResolverImpl()

    /**
     * Test resolving a did:jwk with P-256 EC key.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun shouldResolveP256DidJwk(): TestResult =
        runTest {
            // Encode JWK to JSON and then base64url
            val jwkJson = """{"kty":"EC","crv":"P-256","x":"WKn-ZIGevcwGFOMJ0GeEei2HiO3I-oMECoZQZ0KdLJ0","y":"Xch1SqgK8B0K3w6V3a9RbU4HlB1pdP_EEcyLpRZ4Mds"}"""
            val encoded = Base64.UrlSafe.encode(jwkJson.encodeToByteArray()).trimEnd('=')
            val did = "did:jwk:$encoded"

            val result = resolver.resolve(did, DidResolutionOptions())

            assertTrue(result.isOk, "Resolution should succeed")
            val resolution = result.getOrThrow()

            // Verify document ID matches
            assertEquals(did, resolution.didDocument?.id)

            // Verify verification method exists
            val verificationMethods = resolution.didDocument?.verificationMethod
            assertNotNull(verificationMethods)
            assertTrue(verificationMethods.isNotEmpty())

            // Verify the VM has correct JWK
            val vm = verificationMethods.first()
            assertEquals("JsonWebKey2020", vm.type)
            assertNotNull(vm.publicKeyJwk)

            // Verify JWK properties match
            val resolvedJwk = vm.publicKeyJwk!!
            assertEquals("EC", resolvedJwk.kty.value)
            assertEquals("P-256", resolvedJwk.crv?.value)
        }

    /**
     * Test resolving a did:jwk with Ed25519 OKP key.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun shouldResolveEd25519DidJwk(): TestResult =
        runTest {
            // Create an Ed25519 JWK
            val jwkJson = """{"kty":"OKP","crv":"Ed25519","x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"}"""
            val encoded = Base64.UrlSafe.encode(jwkJson.encodeToByteArray()).trimEnd('=')
            val did = "did:jwk:$encoded"

            val result = resolver.resolve(did, DidResolutionOptions())

            assertTrue(result.isOk, "Resolution should succeed")
            val resolution = result.getOrThrow()

            assertEquals(did, resolution.didDocument?.id)

            val vm = resolution.didDocument?.verificationMethod?.first()
            assertNotNull(vm)
            assertEquals("OKP", vm.publicKeyJwk?.kty?.value)
            assertEquals("Ed25519", vm.publicKeyJwk?.crv?.value)

            // Ed25519 keys should have authentication and assertion relationships
            val authMethods = resolution.verificationMethodsByPurpose[VerificationPurpose.AUTHENTICATION]
            assertNotNull(authMethods)
            assertTrue(authMethods.isNotEmpty())
        }

    /**
     * Test that invalid DIDs are rejected.
     */
    @Test
    fun shouldRejectInvalidDid(): TestResult =
        runTest {
            val invalidDid = "not-a-did"

            val result = resolver.resolve(invalidDid, DidResolutionOptions())

            assertTrue(result.isErr, "Should reject invalid DID format")
        }

    /**
     * Test that DIDs with wrong method are rejected.
     */
    @Test
    fun shouldRejectWrongMethod(): TestResult =
        runTest {
            val wrongMethodDid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            val result = resolver.resolve(wrongMethodDid, DidResolutionOptions())

            assertTrue(result.isErr, "Should reject DID with wrong method")
        }

    /**
     * Test that invalid base64 encoding is rejected.
     */
    @Test
    fun shouldRejectInvalidBase64(): TestResult =
        runTest {
            val invalidDid = "did:jwk:not-valid-base64!@#"

            val result = resolver.resolve(invalidDid, DidResolutionOptions())

            assertTrue(result.isErr, "Should reject invalid base64 encoding")
        }

    /**
     * Test that resolver reports correct capabilities.
     */
    @Test
    fun shouldReportCorrectCapabilities(): TestResult =
        runTest {
            val capabilities = resolver.capabilities

            assertEquals("jwk", capabilities.method)

            // did:jwk supports creation but not updates
            assertTrue(capabilities.lifecycle.create, "Should support create")
            assertTrue(!capabilities.lifecycle.update, "Should not support update")
            assertTrue(!capabilities.lifecycle.deactivate, "Should not support deactivate")
        }

    /**
     * Test that supported methods list is correct.
     */
    @Test
    fun shouldReportSupportedMethods(): TestResult =
        runTest {
            val methods = resolver.supportedMethods

            assertEquals(listOf("jwk"), methods)
        }
}
