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

package com.sphereon.did.methods.key

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidResolutionOptions
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    fun shouldResolveEd25519DidKey(): TestResult =
        runTest {
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
    fun shouldReturnCorrectMetadata(): TestResult =
        runTest {
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
            val wrongMethodDid = "did:web:example.com"

            val result = resolver.resolve(wrongMethodDid, DidResolutionOptions())

            assertTrue(result.isErr, "Should reject DID with wrong method")
        }

    /**
     * Test dereferencing a verification method by fragment.
     */
    @Test
    fun shouldDereferenceVerificationMethod(): TestResult =
        runTest {
            val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"

            // First resolve to get the VM ID
            val resolveResult = resolver.resolve(did, DidResolutionOptions())
            assertTrue(resolveResult.isOk, "Resolution should succeed")
            val vmId =
                resolveResult
                    .getOrThrow()
                    .didDocument
                    ?.verificationMethod
                    ?.first()
                    ?.id

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
    fun shouldReportCorrectCapabilities(): TestResult =
        runTest {
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
    fun shouldReportSupportedMethods(): TestResult =
        runTest {
            val methods = resolver.supportedMethods

            assertEquals(listOf("key"), methods)
        }

    /**
     * Test resolving a did:key with a P-256 compressed public key.
     * Verifies that EC point decompression produces valid x and y coordinates.
     */
    @Test
    fun shouldResolveP256DidKey(): TestResult =
        runTest {
            // Known P-256 compressed public key (prefix 0x02 = even y)
            // This is the NIST P-256 test vector from did:key spec:
            // did:key:zDnaeUKTWUXc1HDpGfKbEK31nKLN1BHiJU2a7s37yfX65pX6H
            val compressedKey =
                byteArrayOf(
                    0x02.toByte(),
                    0x65.toByte(),
                    0xEE.toByte(),
                    0x98.toByte(),
                    0x50.toByte(),
                    0x1F.toByte(),
                    0xDE.toByte(),
                    0x82.toByte(),
                    0x40.toByte(),
                    0x82.toByte(),
                    0x83.toByte(),
                    0xC1.toByte(),
                    0xC8.toByte(),
                    0x42.toByte(),
                    0x2E.toByte(),
                    0x03.toByte(),
                    0xEB.toByte(),
                    0x51.toByte(),
                    0xDB.toByte(),
                    0xBB.toByte(),
                    0xCF.toByte(),
                    0x3F.toByte(),
                    0x28.toByte(),
                    0xB3.toByte(),
                    0x82.toByte(),
                    0x58.toByte(),
                    0xE3.toByte(),
                    0x6E.toByte(),
                    0xAD.toByte(),
                    0x6D.toByte(),
                    0x7B.toByte(),
                    0xEB.toByte(),
                    0x7E.toByte(),
                )

            val did = KeyDidProviderImpl.didFromP256PublicKey(compressedKey)
            val result = resolver.resolve(did, DidResolutionOptions())

            assertTrue(result.isOk, "Resolution should succeed for P-256 key")
            val resolution = result.getOrThrow()

            val jwk =
                resolution.didDocument
                    ?.verificationMethod
                    ?.first()
                    ?.publicKeyJwk
            assertNotNull(jwk, "Should have JWK")
            assertEquals("EC", jwk.kty.value)
            assertEquals("P-256", jwk.crv?.value)
            assertNotNull(jwk.x, "JWK should have x coordinate")
            assertNotNull(jwk.y, "JWK should have y coordinate")

            // x and y should be distinct (the old placeholder returned x as both)
            assertNotEquals(jwk.x, jwk.y, "x and y coordinates must be different")
        }

    /**
     * Test resolving a did:key with a Secp256k1 compressed public key.
     * Verifies that pure-math point decompression works correctly.
     */
    @Test
    fun shouldResolveSecp256k1DidKey(): TestResult =
        runTest {
            // Known Secp256k1 compressed public key (Bitcoin-style)
            // Using a well-known test vector: compressed form of the secp256k1 generator point G
            // Gx = 0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798
            // Gy = 0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8
            val compressedG =
                byteArrayOf(
                    0x02.toByte(), // prefix for even y
                    0x79.toByte(),
                    0xBE.toByte(),
                    0x66.toByte(),
                    0x7E.toByte(),
                    0xF9.toByte(),
                    0xDC.toByte(),
                    0xBB.toByte(),
                    0xAC.toByte(),
                    0x55.toByte(),
                    0xA0.toByte(),
                    0x62.toByte(),
                    0x95.toByte(),
                    0xCE.toByte(),
                    0x87.toByte(),
                    0x0B.toByte(),
                    0x07.toByte(),
                    0x02.toByte(),
                    0x9B.toByte(),
                    0xFC.toByte(),
                    0xDB.toByte(),
                    0x2D.toByte(),
                    0xCE.toByte(),
                    0x28.toByte(),
                    0xD9.toByte(),
                    0x59.toByte(),
                    0xF2.toByte(),
                    0x81.toByte(),
                    0x5B.toByte(),
                    0x16.toByte(),
                    0xF8.toByte(),
                    0x17.toByte(),
                    0x98.toByte(),
                )

            val did = KeyDidProviderImpl.didFromSecp256k1PublicKey(compressedG)
            val result = resolver.resolve(did, DidResolutionOptions())

            assertTrue(result.isOk, "Resolution should succeed for Secp256k1 key")
            val resolution = result.getOrThrow()

            val jwk =
                resolution.didDocument
                    ?.verificationMethod
                    ?.first()
                    ?.publicKeyJwk
            assertNotNull(jwk, "Should have JWK")
            assertEquals("EC", jwk.kty.value)
            assertEquals("secp256k1", jwk.crv?.value)
            assertNotNull(jwk.x, "JWK should have x coordinate")
            assertNotNull(jwk.y, "JWK should have y coordinate")
            assertNotEquals(jwk.x, jwk.y, "x and y coordinates must be different")

            // Verify the known y coordinate of the generator point
            // Gy (big-endian hex) = 483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8
            // Gy is even (last byte 0xB8, LSB=0), so prefix 0x02 should give the original y
            val yBytes = jwk.y?.let { it.decodeFromBase64Url() }
            assertNotNull(yBytes, "y should decode")
            assertEquals(32, yBytes.size, "y coordinate should be 32 bytes")

            // Check the known Gy value: first byte should be 0x48
            assertEquals(0x48.toByte(), yBytes[0], "First byte of Gy should be 0x48")
            // Last byte should be 0xB8
            assertEquals(0xB8.toByte(), yBytes[31], "Last byte of Gy should be 0xB8")
        }

    /**
     * Test that Secp256k1 decompression handles odd y (prefix 0x03) correctly.
     */
    @Test
    fun shouldResolveSecp256k1DidKeyWithOddY(): TestResult =
        runTest {
            // Use the same x coordinate as the generator but with prefix 0x03 (odd y)
            // This should give p - Gy as the y coordinate
            val compressedOddY =
                byteArrayOf(
                    0x03.toByte(), // prefix for odd y
                    0x79.toByte(),
                    0xBE.toByte(),
                    0x66.toByte(),
                    0x7E.toByte(),
                    0xF9.toByte(),
                    0xDC.toByte(),
                    0xBB.toByte(),
                    0xAC.toByte(),
                    0x55.toByte(),
                    0xA0.toByte(),
                    0x62.toByte(),
                    0x95.toByte(),
                    0xCE.toByte(),
                    0x87.toByte(),
                    0x0B.toByte(),
                    0x07.toByte(),
                    0x02.toByte(),
                    0x9B.toByte(),
                    0xFC.toByte(),
                    0xDB.toByte(),
                    0x2D.toByte(),
                    0xCE.toByte(),
                    0x28.toByte(),
                    0xD9.toByte(),
                    0x59.toByte(),
                    0xF2.toByte(),
                    0x81.toByte(),
                    0x5B.toByte(),
                    0x16.toByte(),
                    0xF8.toByte(),
                    0x17.toByte(),
                    0x98.toByte(),
                )

            val did = KeyDidProviderImpl.didFromSecp256k1PublicKey(compressedOddY)
            val result = resolver.resolve(did, DidResolutionOptions())

            assertTrue(result.isOk, "Resolution should succeed for Secp256k1 key with odd y")
            val resolution = result.getOrThrow()

            val jwk =
                resolution.didDocument
                    ?.verificationMethod
                    ?.first()
                    ?.publicKeyJwk
            assertNotNull(jwk, "Should have JWK")

            // y should be p - Gy, which is odd (last bit = 1)
            val yBytes = jwk.y?.let { it.decodeFromBase64Url() }
            assertNotNull(yBytes, "y should decode")
            // Last byte of p - Gy: the last byte should be odd
            assertTrue((yBytes[31].toInt() and 0x01) == 1, "y should be odd for prefix 0x03")
        }
}
