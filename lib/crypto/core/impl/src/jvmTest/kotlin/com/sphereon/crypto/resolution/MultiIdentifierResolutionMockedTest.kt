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
 */

package com.sphereon.crypto.resolution

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
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
 * Tests for MultiIdentifierResolutionServiceImpl using mocks
 * to cover edge case branches that are hard to reach with integration tests.
 */
class MultiIdentifierResolutionMockedTest {
    private val mockSessionContext = createAnonymousSessionContext("mock-multi-identifier-test")

    private val testJwk =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbFpp0eS8_rJlvpuX_qEyU1J2PNmXYnqPCBJTqqiBA",
            y = "F8kbfVPRQc5M9kJA1fy3c_0Q6vCqHy1X7CZQC6XQy9I",
        )

    // ========================================================================
    // Branch Coverage Tests for supports() with IIdentifierMethod
    // ========================================================================

    @Test
    fun testSupportsWithIdentifierMethod() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf(IdentifierMethodDefaults.KEY)

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf(IdentifierMethodDefaults.JWK)

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            // Test with IIdentifierMethod - covers the `is IIdentifierMethod` branch
            val supportedKey = service.supports(IdentifierMethodDefaults.KEY)
            assertTrue(supportedKey, "Should support KEY identifier method")

            val supportedJwk = service.supports(IdentifierMethodDefaults.JWK)
            assertTrue(supportedJwk, "Should support JWK identifier method")

            val notSupported = service.supports(IdentifierMethodDefaults.DID)
            assertFalse(notSupported, "Should not support DID identifier method")
        }

    // ========================================================================
    // Branch Coverage Tests for supports() with raw identifier
    // ========================================================================

    @Test
    fun testSupportsWithRawIdentifier() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns false

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns true

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            // Test with a raw identifier (string) - covers the `else` branch in supports()
            val supported = service.supports("some-raw-identifier")
            assertTrue(supported, "Should support raw identifier via external multi")
        }

    @Test
    fun testSupportsWithRawIdentifierReturnsFalseWhenNoServiceSupports() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns false

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns false

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val supported = service.supports("unsupported-identifier")
            val forgedContext = createAnonymousSessionContext("multi-identifier-forged-supports")
            val supportedWithContext = service.supports("unsupported-identifier")
            assertFalse(supported, "Should not support unsupported identifier")
            assertEquals(supported, supportedWithContext, "Context-bearing supports should not bypass unsupported results")
        }

    @Test
    fun testSupportsWithContextDelegatesToContextFreeSupports() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns false

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns true

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val forgedContext = createAnonymousSessionContext("multi-identifier-forged-context")
            val contextFree = service.supports("delegated-support")
            val withContext = service.supports("delegated-support")

            assertEquals(contextFree, withContext, "Context-bearing supports should delegate to supports")
        }

    // ========================================================================
    // Branch Coverage Tests for isSupportedIdentifier
    // ========================================================================

    @Test
    fun testIsSupportedIdentifierReturnsTrueViaManagedMulti() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns true

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns false

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            // First condition is true, so externalMulti shouldn't be called
            val supported = service.isSupportedIdentifier("identifier")
            assertTrue(supported, "Should return true via managed multi")
        }

    @Test
    fun testIsSupportedIdentifierReturnsTrueViaExternalMulti() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns false

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns true

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            // First condition is false, so externalMulti should be called
            val supported = service.isSupportedIdentifier("identifier")
            assertTrue(supported, "Should return true via external multi")
        }

    @Test
    fun testIsSupportedIdentifierReturnsFalseWhenBothReturnFalse() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns false

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns false

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val supported = service.isSupportedIdentifier("identifier")
            assertFalse(supported, "Should return false when both return false")
        }

    // ========================================================================
    // Branch Coverage Tests for isSupportedOpts
    // ========================================================================

    @Test
    fun testIsSupportedOptsWithExternalIdentifierOptsOrResult() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedOpts(any()) } returns true

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            // Create a mock ExternalIdentifierOptsOrResult
            val mockExternalOpts = mockk<ExternalIdentifierOptsOrResult>(relaxed = true)
            every { mockExternalOpts.identifier } returns testJwk

            val supported = service.isSupportedOpts(mockExternalOpts)
            assertTrue(supported, "Should support ExternalIdentifierOptsOrResult via externalMulti")
        }

    @Test
    fun testIsSupportedOptsWithManagedIdentifierOptsOrResult() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedOpts(any()) } returns true

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            // Create a mock ManagedIdentifierOptsOrResult
            val mockManagedOpts = mockk<ManagedIdentifierOptsOrResult>(relaxed = true)
            every { mockManagedOpts.identifier } returns testJwk

            val supported = service.isSupportedOpts(mockManagedOpts)
            assertTrue(supported, "Should support ManagedIdentifierOptsOrResult via managedMulti")
        }

    // ========================================================================
    // Branch Coverage Tests for asSupportedOpts
    // ========================================================================

    @Test
    fun testAsSupportedOptsWithExternalIdentifierOptsOrResult() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()

            val mockExternalOpts = mockk<ExternalIdentifierOpts>(relaxed = true)

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.asSupportedOpts(any()) } returns IdkResult.ok<ExternalIdentifierOpts, IdkErrorType>(mockExternalOpts)

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val mockInput = mockk<ExternalIdentifierOptsOrResult>(relaxed = true)
            every { mockInput.identifier } returns testJwk

            val result = service.asSupportedOpts(mockInput)
            assertTrue(result.isOk, "asSupportedOpts should succeed for ExternalIdentifierOptsOrResult")
        }

    @Test
    fun testAsSupportedOptsWithManagedIdentifierOptsOrResult() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedOpts = mockk<ManagedIdentifierOpts>(relaxed = true)

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.asSupportedOpts(any()) } returns IdkResult.ok<ManagedIdentifierOpts, IdkErrorType>(mockManagedOpts)

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val mockInput = mockk<ManagedIdentifierOptsOrResult>(relaxed = true)
            every { mockInput.identifier } returns testJwk

            val result = service.asSupportedOpts(mockInput)
            assertTrue(result.isOk, "asSupportedOpts should succeed for ManagedIdentifierOptsOrResult")
        }

    // ========================================================================
    // Branch Coverage Tests for resolve/doExecute
    // ========================================================================

    @Test
    fun testResolveWithExternalIdentifierOptsOrResult() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedOpts(any()) } returns false

            // Create mock result for external service
            val mockExternalResult = mockk<ExternalIdentifierResult>(relaxed = true)

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedOpts(any()) } returns true
            coEvery { mockExternalMulti.isSupportedIdentifier(any()) } returns true
            coEvery { mockExternalMulti.resolve(any()) } returns IdkResult.ok(mockExternalResult)

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val mockExternalOpts = mockk<ExternalIdentifierOptsOrResult>(relaxed = true)
            every { mockExternalOpts.identifier } returns testJwk
            every { mockExternalOpts.method } returns IdentifierMethodDefaults.JWK

            val result = service.resolve(mockExternalOpts)
            assertTrue(result.isOk, "resolve should succeed for ExternalIdentifierOptsOrResult")
        }

    @Test
    fun testResolveWithManagedIdentifierOptsOrResult() =
        runTest {
            val mockExecution = mockk<SessionExecution>(relaxed = true)
            every { mockExecution.sessionContext } returns mockSessionContext

            // Create mock result for managed service
            val mockManagedResult = mockk<ManagedIdentifierResult<KeyType>>(relaxed = true)

            val mockManagedMulti = mockk<MultiManagedIdentifierService>()
            every { mockManagedMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockManagedMulti.isSupportedOpts(any()) } returns true
            coEvery { mockManagedMulti.isSupportedIdentifier(any()) } returns true
            coEvery { mockManagedMulti.resolve(any()) } returns IdkResult.ok<ManagedIdentifierResult<KeyType>, IdkErrorType>(mockManagedResult)

            val mockExternalMulti = mockk<MultiExternalIdentifierService>()
            every { mockExternalMulti.supportedIdentifierMethods } returns listOf()
            coEvery { mockExternalMulti.isSupportedOpts(any()) } returns false

            val service =
                MultiIdentifierResolutionServiceImpl(
                    execution = mockExecution,
                    managedMulti = mockManagedMulti,
                    externalMulti = mockExternalMulti,
                )

            val mockManagedOpts = mockk<ManagedIdentifierOptsOrResult>(relaxed = true)
            every { mockManagedOpts.identifier } returns testJwk
            every { mockManagedOpts.method } returns IdentifierMethodDefaults.KEY

            val result = service.resolve(mockManagedOpts)
            assertTrue(result.isOk, "resolve should succeed for ManagedIdentifierOptsOrResult")
        }
}
