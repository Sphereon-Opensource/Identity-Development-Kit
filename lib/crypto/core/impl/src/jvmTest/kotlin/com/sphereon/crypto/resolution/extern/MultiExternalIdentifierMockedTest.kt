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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.di.context.createAnonymousSessionContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for MultiExternalIdentifierResolutionServiceImpl using mocks
 * to cover edge case branches that are hard to reach with integration tests.
 */
class MultiExternalIdentifierMockedTest {

    private val mockSessionContext = createAnonymousSessionContext("mock-multi-external-test")

    // ========================================================================
    // Branch Coverage Tests for doExecute Error Path
    // ========================================================================

    @Test
    fun testResolveWithNullMethod() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        // Mock external service that doesn't support the opts
        val mockExternalService = mockk<ExternalIdentifierService>()
        coEvery { mockExternalService.isSupportedOpts(any()) } returns false
        every { mockExternalService.supportedIdentifierMethods } returns listOf()

        val service = MultiExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            external = setOf(mockExternalService)
        )

        // Create opts with null method by using a mock that returns null
        val mockOpts = mockk<ExternalIdentifierOptsOrResult>(relaxed = true)
        every { mockOpts.method } returns null
        every { mockOpts.identifier } returns "test-identifier"

        val result = service.resolve(mockOpts)

        // Should fail since no service supports the opts
        assertTrue(result.isErr, "Should fail when no service supports the opts with null method")
        assertTrue(
            result.error.message.defaultMessage.contains("not specified"),
            "Error message should mention 'not specified' for null method"
        )
    }

    @Test
    fun testResolveWithSpecifiedMethod() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        // Mock external service that doesn't support the opts
        val mockExternalService = mockk<ExternalIdentifierService>()
        coEvery { mockExternalService.isSupportedOpts(any()) } returns false
        every { mockExternalService.supportedIdentifierMethods } returns listOf()

        val service = MultiExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            external = setOf(mockExternalService)
        )

        // Create opts with a method specified
        val mockMethod = mockk<IIdentifierMethod>()
        every { mockMethod.methodName } returns "test-method"

        val mockOpts = mockk<ExternalIdentifierOptsOrResult>(relaxed = true)
        every { mockOpts.method } returns mockMethod
        every { mockOpts.identifier } returns "test-identifier"

        val result = service.resolve(mockOpts)

        // Should fail since no service supports the opts
        assertTrue(result.isErr, "Should fail when no service supports the opts")
        assertTrue(
            result.error.message.defaultMessage.contains("test-method"),
            "Error message should contain the method name"
        )
    }

    // ========================================================================
    // Branch Coverage Tests for isSupportedIdentifier
    // ========================================================================

    @Test
    fun testIsSupportedIdentifierReturnsTrueWhenAtLeastOneServiceSupports() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        // First service doesn't support, second does
        val mockService1 = mockk<ExternalIdentifierService>()
        coEvery { mockService1.isSupportedIdentifier(any()) } returns false
        every { mockService1.supportedIdentifierMethods } returns listOf()

        val mockService2 = mockk<ExternalIdentifierService>()
        coEvery { mockService2.isSupportedIdentifier(any()) } returns true
        every { mockService2.supportedIdentifierMethods } returns listOf()

        val service = MultiExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            external = setOf(mockService1, mockService2)
        )

        val supported = service.isSupportedIdentifier("test-identifier")
        assertTrue(supported, "Should return true when at least one service supports the identifier")
    }

    @Test
    fun testIsSupportedIdentifierReturnsFalseWhenNoServiceSupports() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        // Both services don't support
        val mockService1 = mockk<ExternalIdentifierService>()
        coEvery { mockService1.isSupportedIdentifier(any()) } returns false
        every { mockService1.supportedIdentifierMethods } returns listOf()

        val mockService2 = mockk<ExternalIdentifierService>()
        coEvery { mockService2.isSupportedIdentifier(any()) } returns false
        every { mockService2.supportedIdentifierMethods } returns listOf()

        val service = MultiExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            external = setOf(mockService1, mockService2)
        )

        val supported = service.isSupportedIdentifier("test-identifier")
        assertFalse(supported, "Should return false when no service supports the identifier")
    }

    @Test
    fun testIsSupportedIdentifierWithEmptyExternalServices() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        val service = MultiExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            external = emptySet()
        )

        val supported = service.isSupportedIdentifier("test-identifier")
        assertFalse(supported, "Should return false when no external services are configured")
    }

    // ========================================================================
    // Branch Coverage Tests for Success Path
    // ========================================================================

    @Test
    fun testResolveWithMatchingServiceNullMethod() = runTest {
        val mockExecution = mockk<SessionExecution>(relaxed = true)
        every { mockExecution.sessionContext } returns mockSessionContext

        // Create mock result
        val mockResult = mockk<ExternalIdentifierResult>(relaxed = true)

        // Mock external service that supports the opts
        val mockExternalService = mockk<ExternalIdentifierService>()
        coEvery { mockExternalService.isSupportedOpts(any()) } returns true
        coEvery { mockExternalService.resolve(any()) } returns com.sphereon.core.api.IdkResult.ok(mockResult)
        every { mockExternalService.supportedIdentifierMethods } returns listOf()

        val service = MultiExternalIdentifierResolutionServiceImpl(
            execution = mockExecution,
            external = setOf(mockExternalService)
        )

        // Create opts with null method - this tests line 189 branch
        val mockOpts = mockk<ExternalIdentifierOptsOrResult>(relaxed = true)
        every { mockOpts.method } returns null
        every { mockOpts.identifier } returns "test-identifier"

        val result = service.resolve(mockOpts)

        // Should succeed since the service supports the opts
        assertTrue(result.isOk, "Should succeed when a service supports the opts")
    }
}
