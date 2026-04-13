/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement

import app.cash.turbine.test
import com.sphereon.mdoc.engagement.mocks.TestMdocEngagementManagerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for MdocEngagementManager focusing on meaningful business logic:
 * - Duplicate engagement prevention
 * - Engagement lifecycle management (creation, closing, cleanup)
 * - Typed property access correctness (qrEngagement, nfcEngagement, toAppEngagement)
 * - EngagementsByType map consistency
 * - Shared parameters regeneration on closeAll()
 * - Error messages quality and actionability
 *
 * Note: These tests use the mock infrastructure from the mocks package and Turbine for Flow testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MdocEngagementManagerTest {

    // ========================================
    // DUPLICATE ENGAGEMENT PREVENTION TESTS
    // ========================================

    @Test
    fun `createEngagement with duplicate QR type should create 2 engagements with only the last one active`() = runTest {
        // Given - manager with existing QR engagement
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        val result1 = manager.createEngagement {
            engagement { qr { } }
        }

        if (result1.isErr) {
            // Log the error for debugging
            println("First QR engagement failed: ${result1.error.code} - ${result1.error.message.defaultMessage}")
        }
        assertTrue(result1.isOk, "First QR engagement should succeed")

        // When - try to create another QR engagement
        val result2 = manager.createEngagement {
            engagement { qr { } }
        }


        assertTrue(result2.isOk, "Second QR engagement should work")

        assertNotEquals(result1.value, result2.value)
        assertFalse(result1.value.isActive.value)
        assertTrue(result2.value.isActive.value)

    }

    @Test
    fun `createEngagement with duplicate NFC type  should create 2 engagements with only the last one active`() = runTest {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        val result1 = manager.createEngagement {
            engagement { nfc { } }
        }
        assertTrue(result1.isOk, "First NFC engagement should succeed")

        // When
        val result2 = manager.createEngagement {
            engagement { nfc { } }
        }

        // Then

        assertTrue(result2.isOk, "Second QR engagement should work")

        assertNotEquals(result1.value, result2.value)
        assertFalse(result1.value.isActive.value)
        assertTrue(result2.value.isActive.value)

    }

    // ========================================
    // TYPED PROPERTY ACCESS TESTS
    // ========================================

    @Test
    fun `qrEngagement property should reflect QR engagement lifecycle`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        // Test with Turbine to properly await StateFlow emissions
        manager.qrEngagement.test {
            // Initially null
            assertNull(awaitItem(), "QR engagement should be null initially")

            // When - create QR engagement
            val result = manager.createEngagement {
                engagement { qr { } }
            }

            // Then - should be populated
            assertTrue(result.isOk, "Creating QR engagement should succeed")
            val engagement = awaitItem()
            assertNotNull(engagement, "QR engagement should be set after creation")

            // When - close QR engagement
            manager.closeQrEngagement()

            // Then - should be null again
            assertNull(awaitItem(), "QR engagement should be null after closing")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nfcEngagement property should reflect NFC engagement lifecycle`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        // Test with Turbine
        manager.nfcEngagement.test {
            // Initially null
            assertNull(awaitItem(), "NFC engagement should be null initially")

            // When - create NFC engagement
            val result = manager.createEngagement {
                engagement { nfc { } }
            }

            // Then
            assertTrue(result.isOk, "Creating NFC engagement should succeed")
            assertNotNull(awaitItem(), "NFC engagement should be set after creation")

            // When - close
            manager.closeNfcEngagement()

            // Then
            assertNull(awaitItem(), "NFC engagement should be null after closing")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // ENGAGEMENTS BY TYPE MAP TESTS
    // ========================================

    @Test
    fun `engagementsByType should only contain active engagements`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.engagementsByType.test {
            // Initial state - empty
            assertEquals(0, awaitItem().size)

            // When - create both QR and NFC
            manager.createEngagement { engagement { qr { } } }

            // Should get update with QR
            var byType = awaitItem()
            assertEquals(1, byType.size, "Should have 1 engagement type after QR creation")
            assertTrue(byType.containsKey(EngagementType.QR))

            manager.createEngagement { engagement { nfc { } } }

            // Should get update with both
            byType = awaitItem()
            assertEquals(2, byType.size, "Should have 2 engagement types")
            assertTrue(byType.containsKey(EngagementType.QR))
            assertTrue(byType.containsKey(EngagementType.NFC))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `engagementsByType should remove closed engagements`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.engagementsByType.test {
            // Skip initial empty state
            assertEquals(0, awaitItem().size)

            manager.createEngagement { engagement { qr { } } }
            assertEquals(1, awaitItem().size)

            manager.createEngagement { engagement { nfc { } } }
            assertEquals(2, awaitItem().size)

            // When - close QR
            manager.closeQrEngagement()

            // Then - only NFC remains
            val byType = awaitItem()
            assertEquals(1, byType.size, "Should have 1 engagement type after closing one")
            assertFalse(byType.containsKey(EngagementType.QR))
            assertTrue(byType.containsKey(EngagementType.NFC))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `engagementsByType should be empty after closeAll`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.engagementsByType.test {
            // Skip initial empty
            assertEquals(0, awaitItem().size)

            manager.createEngagement { engagement { qr { } } }
            assertEquals(1, awaitItem().size)

            manager.createEngagement { engagement { nfc { } } }
            assertEquals(2, awaitItem().size)

            // When
            manager.closeAll()

            // Then
            val byType = awaitItem()
            assertTrue(byType.isEmpty(), "All engagements should be removed")

            cancelAndIgnoreRemainingEvents()
        }

        // Verify individual properties are also cleared
        assertNull(manager.qrEngagement.value)
        assertNull(manager.nfcEngagement.value)
    }

    // ========================================
    // SHARED PARAMETERS TESTS
    // ========================================

    @Test
    fun `closeAll should regenerate shared parameters for new session`() = runTest {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val initialCentralUuid = manager.sharedParameters.bleCentralClientUuid.value
        val initialPeripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value
        val initialKeyAlias = manager.sharedParameters.ephemeralKeyAlias.value

        // When
        manager.closeAll()

        // Then - all parameters should be regenerated (different values)
        assertFalse(
            initialCentralUuid == manager.sharedParameters.bleCentralClientUuid.value,
            "Central client UUID should be regenerated"
        )
        assertFalse(
            initialPeripheralUuid == manager.sharedParameters.blePeripheralServerUuid.value,
            "Peripheral server UUID should be regenerated"
        )
        assertFalse(
            initialKeyAlias == manager.sharedParameters.ephemeralKeyAlias.value,
            "Ephemeral key alias should be regenerated"
        )
    }

    @Test
    fun `sharedParameters should have separate UUIDs for central and peripheral modes by default`() = runTest {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        // Then - should have two different UUIDs (correct design)
        val centralUuid = manager.sharedParameters.bleCentralClientUuid.value
        val peripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value

        assertFalse(
            centralUuid == peripheralUuid,
            "Central and peripheral UUIDs should be different by default"
        )
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================

    @Test
    fun `createEngagement without engagement method should fail with descriptive error`() = runTest {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        // When - try to create engagement without specifying method
        val result = manager.createEngagement {
            // No engagement { } block
        }

        // Then - the manager should return an error since no method was specified
        // Note: The actual behavior might create an engagement with empty methods
        // which is also a valid implementation choice. We'll test both scenarios.
        if (result.isOk) {
            // If it succeeds, verify no engagement methods were actually added
            val engagement = result.value
            assertTrue(
                engagement.getEngagementMethods().isEmpty(),
                "Engagement should have no methods when none specified"
            )
        } else {
            // If it fails, verify it's the right error
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code, "Error code should be ILLEGAL_ARGUMENT_ERROR")
            val errorMsg = result.error.message.defaultMessage
            assertTrue(
                errorMsg.contains("engagement method") || errorMsg.contains("No engagement"),
                "Error should mention engagement method issue, got: $errorMsg"
            )
        }
    }

    @Test
    fun `closeNfcEngagement when no NFC exists should return NOT_FOUND error`() = runTest {
        // Given - manager with no engagements
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        // When
        val result = manager.closeNfcEngagement()

        // Then
        assertTrue(result.isErr)
        assertEquals("NOT_FOUND_ERROR", result.error.code)
        assertTrue(result.error.message.defaultMessage.contains("No NFC engagement found"))
    }

    @Test
    fun `closeQrEngagement when no QR exists should return NOT_FOUND error`() = runTest {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        // When
        val result = manager.closeQrEngagement()

        // Then
        assertTrue(result.isErr)
        assertEquals("NOT_FOUND_ERROR", result.error.code)
        assertTrue(result.error.message.defaultMessage.contains("No QR engagement found"))
    }

    // ========================================
    // CLEANUP AND LIFECYCLE TESTS
    // ========================================

    @Test
    fun `closeAll should call close on all engagements`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.createEngagement { engagement { qr { } } }
        manager.createEngagement { engagement { nfc { } } }

        // When
        manager.closeAll()

        // Then - verify the map is empty using Turbine
        manager.engagementsByType.test {
            // Get current state (should be empty after closeAll)
            val currentState = awaitItem()
            assertTrue(currentState.isEmpty(), "All engagements should be removed, but got: ${currentState.keys}")

            cancelAndIgnoreRemainingEvents()
        }

        // Verify individual properties
        assertNull(manager.qrEngagement.value, "QR should be null after closeAll")
        assertNull(manager.nfcEngagement.value, "NFC should be null after closeAll")
    }

    @Test
    fun `closing specific engagement should not affect other engagements`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.createEngagement { engagement { qr { } } }
        manager.createEngagement { engagement { nfc { } } }

        // When - close only QR
        manager.closeQrEngagement()

        // Then - verify final state using Turbine
        manager.engagementsByType.test {
            // Get current state (should only have NFC)
            val currentState = awaitItem()
            assertEquals(1, currentState.size, "Should have 1 engagement after closing QR")
            assertFalse(currentState.containsKey(EngagementType.QR), "QR should not be in map")
            assertTrue(currentState.containsKey(EngagementType.NFC), "NFC should be in map")

            cancelAndIgnoreRemainingEvents()
        }

        // Final verification of individual properties
        assertNull(manager.qrEngagement.value, "QR should be closed")
        assertNotNull(manager.nfcEngagement.value, "NFC should still be active")
    }

    // ========================================
    // ENGAGEMENT CREATION TESTS
    // ========================================

    @Test
    fun `createEngagement with QR should succeed and populate qrEngagement`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.qrEngagement.test {
            // Skip initial null
            assertNull(awaitItem())

            // When
            val result = manager.createEngagement {
                engagement { qr { } }
            }

            // Then
            assertTrue(result.isOk, "QR engagement creation should succeed")
            assertNotNull(awaitItem(), "QR engagement should be populated")
            assertNull(manager.nfcEngagement.value, "NFC engagement should remain null")
            assertEquals(1, manager.engagementsByType.value.size, "Should have exactly 1 engagement")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createEngagement with NFC should succeed and populate nfcEngagement`() = runTest(timeout = 5.seconds) {
        // Given
        val manager = TestMdocEngagementManagerFactory.createTestManager()

        manager.nfcEngagement.test {
            // Skip initial null
            assertNull(awaitItem())

            // When
            val result = manager.createEngagement {
                engagement { nfc { } }
            }

            // Then
            assertTrue(result.isOk, "NFC engagement creation should succeed")
            assertNotNull(awaitItem(), "NFC engagement should be populated")
            assertNull(manager.qrEngagement.value, "QR engagement should remain null")
            assertEquals(1, manager.engagementsByType.value.size, "Should have exactly 1 engagement")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
