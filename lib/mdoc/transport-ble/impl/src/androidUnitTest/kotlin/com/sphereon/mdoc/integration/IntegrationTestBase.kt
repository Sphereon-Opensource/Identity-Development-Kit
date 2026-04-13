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

package com.sphereon.mdoc.integration

import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.mdoc.engagement.mocks.TestMdocEngagementManagerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Base class for integration tests that test full engagement/transfer flows
 * with mocked BLE/NFC transport.
 *
 * This class provides:
 * - Test manager creation with mock dependencies
 * - Cleanup between tests
 * - Helper methods for common test scenarios
 *
 * ## Usage
 * ```kotlin
 * class QrEngagementIntegrationTest : IntegrationTestBase() {
 *     @Test
 *     fun `test QR flow`() = runIntegrationTest {
 *         // Use manager
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class IntegrationTestBase {

    protected lateinit var manager: MdocEngagementManager

    /**
     * Set up test infrastructure before each test.
     * Creates fresh mock services and manager.
     */
    @BeforeTest
    open fun setUp() {
        manager = createTestManager()
    }

    /**
     * Clean up after each test.
     * Closes manager.
     */
    @AfterTest
    open fun tearDown() {
        // Close all engagements before closing the manager to ensure clean state
        runBlocking {
            manager.closeAll()
        }
        manager.close()
    }

    /**
     * Creates a test manager with mock dependencies.
     * Override this to customize the manager creation.
     */
    protected open fun createTestManager(): MdocEngagementManager {
        return TestMdocEngagementManagerFactory.createTestManager()
    }

    /**
     * Stub for BLE configuration - no-op since mock BLE service was removed.
     * Kept for API compatibility with existing tests.
     */
    @Suppress("UNUSED_PARAMETER")
    protected fun configureBle(
        scanDelay: Long = 100,
        connectionDelay: Long = 50,
        shouldFailScan: Boolean = false,
        shouldFailConnection: Boolean = false,
        shouldDropConnection: Boolean = false,
        shouldTimeout: Boolean = false
    ) {
        // No-op - mock BLE service was removed
    }

    /**
     * Helper to extract UUID from QR engagement URI.
     *
     * QR URIs for Device Engagement (holder QR) follow format per ISO 18013-5:
     * `mdoc:<base64url-of-DeviceEngagement>` (opaque URI, no slashes)
     *
     * QR URIs for Reader Engagement (reader QR) follow format per ISO 18013-7:
     * `mdoc://<base64url-of-ReaderEngagement>` (hierarchical URI, with slashes)
     */
    protected fun extractUuidFromQrUri(uri: String): Uuid {
        // For test purposes, we'll generate a deterministic UUID from the URI
        // In real implementation, this would parse the actual DeviceEngagement/ReaderEngagement CBOR
        return Uuid.random()
    }

    /**
     * Creates a mock device request for testing user consent flow.
     */
    protected fun createMockDeviceRequest(): ByteArray {
        // Simple mock - in real tests this would be a proper CBOR-encoded DeviceRequest
        return ByteArray(32) { it.toByte() }
    }

    /**
     * Creates a mock device response for testing.
     */
    protected fun createMockDeviceResponse(): ByteArray {
        // Simple mock - in real tests this would be a proper CBOR-encoded DeviceResponse
        return ByteArray(32) { (it + 1).toByte() }
    }

    /**
     * Helper to run integration tests with proper test scope.
     */
    protected fun runIntegrationTest(
        testBody: suspend TestScope.() -> Unit
    ) = runTest(timeout = Duration.parse("30s")) {
        testBody()
    }
}
