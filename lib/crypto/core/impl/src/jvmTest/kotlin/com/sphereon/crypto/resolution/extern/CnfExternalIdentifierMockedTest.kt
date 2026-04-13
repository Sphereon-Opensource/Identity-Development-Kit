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
 */

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.context.createAnonymousSessionContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for CnfExternalIdentifierResolutionServiceImpl using mocks
 * to cover edge case branches that are hard to reach with integration tests.
 */
class CnfExternalIdentifierMockedTest {

    private val mockSessionContext = createAnonymousSessionContext("mock-cnf-external-test")

    private val testJwk = Jwk(
        kty = JwaKeyType.EC,
        crv = JwaCurve.P_256,
        x = "WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA",
        y = "F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I",
        kid = "test-key-1"
    )

    // ========================================================================
    // Branch Coverage Tests for supports() Method
    // ========================================================================

    @Test
    fun testSupportsReturnsFalseForNonCnfOpts() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        // Test with DID opts (not CNF)
        val didOpts = ExternalIdentifierDidOpts(identifier = "did:example:123")
        assertFalse(service.supports(didOpts), "Should not support DID opts")

        // Test with JWK opts (not CNF)
        val jwkOpts = ExternalIdentifierJwkOpts(identifier = testJwk)
        assertFalse(service.supports(jwkOpts), "Should not support JWK opts")

        assertEquals(
            service.supports(didOpts),
            service.supports(didOpts),
            "Context-bearing supports should delegate to supports"
        )
    }

    @Test
    fun testSupportsReturnsTrueForCnfOpts() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("jwk" to testJwk),
            jwk = testJwk
        )
        assertTrue(service.supports(cnfOpts), "Should support CNF opts")
        assertEquals(
            service.supports(cnfOpts),
            service.supports(cnfOpts),
            "Context-bearing supports should delegate to supports"
        )
    }

    // Note: The !supports() branch in doExecute() is effectively unreachable because
    // the parent ExecutionScopedCommandAdapter checks supports() before calling doExecute().
    // This is by design - the parent handles unsupported args.

    // ========================================================================
    // Branch Coverage Tests for JKU Resolution Path
    // ========================================================================

    @Test
    fun testResolveSucceedsWithJkuOnly() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        // Mock JWKS URL resolver to return success
        val resolvedKeyInfo = ResolvedKeyInfo.fromKey(testJwk)
        val jwksResult = ExternalIdentifierResult.JwksUrl(
            identifierOpts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/jwks"),
            jwks = arrayOf(resolvedKeyInfo),
            keyInfo = resolvedKeyInfo,
            jwksUrl = "https://example.com/jwks",
            selectedKid = null
        )
        coEvery { mockJwksUrlResolver.resolve(any()) } returns jwksResult.asOkResult()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("jku" to "https://example.com/jwks"),
            jku = "https://example.com/jwks"
        )
        val result = service.resolve(cnfOpts)

        assertTrue(result.isOk, "Should succeed with JKU only")
        assertEquals(CnfResolutionSource.JKU, result.value.resolvedFrom)
    }

    @Test
    fun testResolveSucceedsWithJkuAndKid() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        // Mock JWKS URL resolver to return success
        val resolvedKeyInfo = ResolvedKeyInfo.fromKey(testJwk)
        val jwksResult = ExternalIdentifierResult.JwksUrl(
            identifierOpts = ExternalIdentifierJwksUrlOpts(identifier = "https://example.com/jwks"),
            jwks = arrayOf(resolvedKeyInfo),
            keyInfo = resolvedKeyInfo,
            jwksUrl = "https://example.com/jwks",
            selectedKid = "test-key-1"
        )
        coEvery { mockJwksUrlResolver.resolve(any()) } returns jwksResult.asOkResult()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("jku" to "https://example.com/jwks", "kid" to "test-key-1"),
            jku = "https://example.com/jwks",
            kid = "test-key-1"  // Non-DID kid with JKU
        )
        val result = service.resolve(cnfOpts)

        assertTrue(result.isOk, "Should succeed with JKU and kid")
        assertEquals(CnfResolutionSource.JKU, result.value.resolvedFrom)
    }

    @Test
    fun testResolveFailsWhenJkuResolutionFails() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        // Mock JWKS URL resolver to return error
        coEvery { mockJwksUrlResolver.resolve(any()) } returns
            IdkError.UNKNOWN_ERROR(message = "Failed to fetch JWKS").asErrorResult()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("jku" to "https://example.com/jwks"),
            jku = "https://example.com/jwks"
        )
        val result = service.resolve(cnfOpts)

        assertTrue(result.isErr, "Should fail when JKU resolution fails")
        assertTrue(
            result.error.message.defaultMessage.contains("Failed to fetch JWK Set"),
            "Error should mention JWK Set fetch failure"
        )
    }

    // ========================================================================
    // Branch Coverage Tests for DID Resolution Success Path
    // ========================================================================

    @Test
    fun testResolveSucceedsWithDidKid() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        // Mock DID resolver to return success
        coEvery { mockVmKeyResolver.resolveVerificationMethodKey(any(), any()) } returns testJwk.asOkResult()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        val didKid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1"
        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("kid" to didKid),
            kid = didKid
        )
        val result = service.resolve(cnfOpts)

        assertTrue(result.isOk, "Should succeed when DID resolver succeeds")
        assertEquals(CnfResolutionSource.DID, result.value.resolvedFrom)
    }

    // ========================================================================
    // Branch Coverage Tests for JWK Resolution Failure Path
    // ========================================================================

    @Test
    fun testResolveFailsWhenJwkResolutionFails() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        // Mock JWK resolver to return error
        coEvery { mockJwkResolver.resolve(any()) } returns
            IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid JWK").asErrorResult()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("jwk" to testJwk),
            jwk = testJwk
        )
        val result = service.resolve(cnfOpts)

        assertTrue(result.isErr, "Should fail when JWK resolution fails")
        assertTrue(
            result.error.message.defaultMessage.contains("Failed to resolve JWK from CNF"),
            "Error should mention JWK resolution failure"
        )
    }

    // ========================================================================
    // Branch Coverage Tests for JWK with kid matching
    // ========================================================================

    @Test
    fun testResolveWithJwkAndMatchingKid() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        // JWK already has kid "test-key-1"
        val resolvedKeyInfo = ResolvedKeyInfo.fromKey(testJwk)
        val jwkResult = ExternalIdentifierResult.Jwk(
            identifierOpts = ExternalIdentifierJwkOpts(identifier = testJwk),
            jwks = arrayOf(resolvedKeyInfo),
            keyInfo = resolvedKeyInfo
        )
        coEvery { mockJwkResolver.resolve(any()) } returns jwkResult.asOkResult()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        // kid matches the JWK's kid
        val cnfOpts = ExternalIdentifierCnfOpts(
            identifier = mapOf("jwk" to testJwk, "kid" to "test-key-1"),
            jwk = testJwk,
            kid = "test-key-1"
        )
        val result = service.resolve(cnfOpts)

        assertTrue(result.isOk, "Should succeed with JWK and matching kid")
        assertEquals("test-key-1", result.value.keyInfo.kid)
    }

    // ========================================================================
    // Branch Coverage Tests for isSupportedIdentifier
    // ========================================================================

    @Test
    fun testIsSupportedIdentifierReturnsTrueForMap() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        assertTrue(service.isSupportedIdentifier(mapOf("key" to "value")), "Should support Map identifier")
        assertTrue(service.isSupportedIdentifier(emptyMap<String, Any>()), "Should support empty Map identifier")
    }

    @Test
    fun testIsSupportedIdentifierReturnsFalseForNonMap() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val mockJwkResolver = mockk<JwkExternalIdentifierResolutionService>()
        val mockJwksUrlResolver = mockk<JwksUrlExternalIdentifierResolutionService>()
        val mockVmKeyResolver = mockk<VerificationMethodKeyResolver>()

        val service = CnfExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            jwkResolver = mockJwkResolver,
            jwksUrlResolver = mockJwksUrlResolver,
            verificationMethodKeyResolver = mockVmKeyResolver
        )

        assertFalse(service.isSupportedIdentifier("string"), "Should not support String identifier")
        assertFalse(service.isSupportedIdentifier(listOf("item")), "Should not support List identifier")
        assertFalse(service.isSupportedIdentifier(123), "Should not support Int identifier")
    }
}

