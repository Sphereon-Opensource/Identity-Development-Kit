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

package com.sphereon.mdoc.integration

import app.cash.turbine.test
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.engagement.mocks.TestEngagementInstance
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DataRetrievalTransmissionType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Integration tests for concurrent engagement scenarios.
 *
 * Tests verify that:
 * - Multiple engagement types (QR + NFC) can coexist
 * - Active engagement suspends others
 * - Suspended engagements resume when active completes
 * - No interference between concurrent engagements
 *
 * This implements the Engagement Suspension Logic from the v3.0 architecture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrentEngagementIntegrationTest : IntegrationTestBase() {

    // ========================================
    // CONCURRENT CREATION TESTS
    // ========================================

    @Test
    fun `should allow creating both QR and NFC engagements simultaneously`() = runIntegrationTest {
        // Given - Empty manager
        assertEquals(0, manager.engagementsByType.value.size)

        // When - Create both types
        val qrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        val nfcResult = manager.createEngagement {
            engagement { nfc { } }
            retrieval { ble { peripheralServerMode = true } }
        }

        // Then - Both succeed
        assertTrue(qrResult.isOk, "QR engagement should succeed")
        assertTrue(nfcResult.isOk, "NFC engagement should succeed")

        // Verify both are tracked
        assertNotNull(manager.qrEngagement.value, "QR engagement should be set")
        assertNotNull(manager.nfcEngagement.value, "NFC engagement should be set")
        assertEquals(2, manager.engagementsByType.value.size, "Should have 2 engagement types")

        // Verify they're different instances
        val qrId = manager.qrEngagement.value?.id
        val nfcId = manager.nfcEngagement.value?.id
        assertTrue(qrId != nfcId, "QR and NFC should have different IDs")
    }

    @Test
    fun `should allow duplicate engagement types while allowing different types`() = runIntegrationTest {
        // Given - Existing QR engagement
        val qrResult1 = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        assertTrue(qrResult1.isOk)
        assertEquals(1, manager.engagementsByType.value.size, "Should have 1 engagement types")

        val qr1 = manager.nfcEngagement.value

        val qrResult2 = manager.createEngagement {
            engagement {
                qr { scheme = "mdoc:" }
                nfc {}
            }
            retrieval { ble { centralClientMode = true } }
        }
        val nfc1 = manager.nfcEngagement.value
        val qr2 = manager.qrEngagement.value

        assertTrue(qrResult2.isOk, "2nd engagement should succeed")
        assertEquals(2, manager.engagementsByType.value.size, "Should have 2 engagement types")
        assertNotEquals(qrResult1.value, qrResult2.value)
        assertNotEquals(qrResult1.value.id, qrResult2.value.id)
        assertNotEquals(qr1, qr2)

        val nfcResult = manager.createEngagement {
            engagement { nfc { } }
        }
        val nfc2 = manager.nfcEngagement.value
        assertTrue(nfcResult.isOk, "NFC should succeed even with existing QR")
        assertNotEquals(nfc1, nfc2)
        assertEquals(qr2, manager.qrEngagement.value, "QR should still be active adm the same as before")

        manager.closeAll()
        assertEquals(0, manager.engagementsByType.value.size, "Engagement types should be cleared")
        assertNull(manager.qrEngagement.value, "QR engagement should be closed")
        assertNull(manager.nfcEngagement.value, "NFC engagement should be closed")
    }

    // ========================================
    // ACTIVE ENGAGEMENT TESTS
    // ========================================

    @Test
    fun `active engagement should be set when engagement transitions to CONNECTING`() = runIntegrationTest {
        // Given - Multiple engagements
        val qrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        assertTrue(qrResult.isOk)
        val qrEngagement = qrResult.value as? TestEngagementInstance
        assertNotNull(qrEngagement)

        manager.createEngagement {
            engagement { nfc { } }
            retrieval { ble { peripheralServerMode = true } }
        }

        // Initially no active engagement
        assertNull(manager.activeEngagement.value, "No active engagement initially")

        // When - QR transitions to CONNECTING, observe the state flow for changes
        manager.activeEngagement.test(timeout = 3.seconds) {
            // Should start with null
            assertNull(awaitItem(), "Should start with no active engagement")

            // Emit CONNECTING event
            qrEngagement.emitEvent(
                MdocEngagementEvent.Connecting(
                    role = MdocRole.MDOC,
                    engagementId = qrEngagement.id,
                    deviceRetrievalMethods = arrayOf(DeviceRetrievalMethod(type = DeviceRetrievalMethodType.BLE, retrievalOptions = BleOptions(peripheralServerMode = false, centralClientMode = true))),
                )
            )

            // Then - Should receive the active engagement update
            val activeEngagement = awaitItem()
            assertNotNull(activeEngagement, "Should have active engagement")
            assertEquals(qrEngagement.id, activeEngagement.id, "QR should be active")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only one engagement can be active at a time`() = runIntegrationTest {
        // Given - Two engagements
        val qrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        val nfcResult = manager.createEngagement {
            engagement { nfc { } }
            retrieval { ble { peripheralServerMode = true } }
        }

        assertTrue(qrResult.isOk)
        assertTrue(nfcResult.isOk)

        val qrEngagement = qrResult.value as? TestEngagementInstance
        val nfcEngagement = nfcResult.value as? TestEngagementInstance
        assertNotNull(qrEngagement)
        assertNotNull(nfcEngagement)

        // When - QR becomes active, observe NFC's isActive state
        nfcEngagement.isActive.test(timeout = 3.seconds) {
            // Should start as active
            assertTrue(awaitItem(), "NFC should start active")

            // Emit CONNECTING event to make QR active
            qrEngagement.emitEvent(
                MdocEngagementEvent.Connecting(
                    role = MdocRole.MDOC,
                    engagementId = qrEngagement.id,
                    deviceRetrievalMethods = arrayOf(DeviceRetrievalMethod(type = DeviceRetrievalMethodType.BLE, retrievalOptions = BleOptions(peripheralServerMode = false, centralClientMode = true))),
                )
            )

            // Then - NFC should be suspended (isActive = false)
            assertFalse(awaitItem(), "NFC should be suspended when QR is active")
            assertTrue(qrEngagement.isActive.value, "QR should be active")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // SUSPENSION AND RESUMPTION TESTS
    // ========================================

    @Test
    fun `suspended engagement should resume when active engagement completes`() = runIntegrationTest {
        // Given - Two engagements with QR active
        val qrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        val nfcResult = manager.createEngagement {
            engagement { nfc { } }
            retrieval { ble { peripheralServerMode = true } }
        }

        assertTrue(qrResult.isOk)
        assertTrue(nfcResult.isOk)

        val qrEngagement = qrResult.value as? TestEngagementInstance
        val nfcEngagement = nfcResult.value as? TestEngagementInstance
        assertNotNull(qrEngagement)
        assertNotNull(nfcEngagement)

        // Observe NFC's isActive state to verify suspension and resumption
        nfcEngagement.isActive.test(timeout = 3.seconds) {
            // Should start as active
            assertTrue(awaitItem(), "NFC should start active")

            // Make QR active (suspends NFC)
            qrEngagement.emitEvent(
                MdocEngagementEvent.Connecting(
                    role = MdocRole.MDOC,
                    engagementId = qrEngagement.id,
                    deviceRetrievalMethods = arrayOf(DeviceRetrievalMethod(type = DeviceRetrievalMethodType.BLE, retrievalOptions = BleOptions(peripheralServerMode = false, centralClientMode = true))),
                )
            )

            // Verify NFC is suspended
            assertFalse(awaitItem(), "NFC should be suspended")

            // When - QR completes (terminal state)
            qrEngagement.emitEvent(
                MdocEngagementEvent.Canceled(
                    role = MdocRole.MDOC,
                    engagementId = qrEngagement.id
                )
            )

            // Then - NFC should resume
            assertTrue(awaitItem(), "NFC should resume after QR completes")
            assertNull(manager.activeEngagement.value, "No active engagement after completion")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // CLEANUP TESTS
    // ========================================

    @Test
    fun `closing one engagement should not affect other engagement state`() = runIntegrationTest {
        // Given - Two active engagements
        manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        manager.createEngagement {
            engagement { nfc { } }
            retrieval { ble { peripheralServerMode = true } }
        }

        val nfcBefore = manager.nfcEngagement.value
        assertNotNull(nfcBefore)

        // When - Close QR
        val closeResult = manager.closeQrEngagement()
        assertTrue(closeResult.isOk)

        // Then - NFC unchanged
        val nfcAfter = manager.nfcEngagement.value
        assertNotNull(nfcAfter, "NFC should still exist")
        assertEquals(nfcBefore.id, nfcAfter.id, "NFC should be same instance")
        assertNull(manager.qrEngagement.value, "QR should be closed")
    }

    @Test
    fun `closeAll should terminate all engagements and clear active engagement`() = runIntegrationTest {
        // Given - Multiple engagements with one active
        val qrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        manager.createEngagement {
            engagement { nfc { } }
            retrieval { ble { peripheralServerMode = true } }
        }

        val qrEngagement = qrResult.value as? TestEngagementInstance
        assertNotNull(qrEngagement)

        // Observe activeEngagement to wait for it to be set
        manager.activeEngagement.test(timeout = 3.seconds) {
            // Should start with null
            assertNull(awaitItem(), "Should start with no active engagement")

            // Make QR active
            qrEngagement.emitEvent(
                MdocEngagementEvent.Connecting(
                    role = MdocRole.MDOC,
                    engagementId = qrEngagement.id,
                    deviceRetrievalMethods = arrayOf(DeviceRetrievalMethod(type = DeviceRetrievalMethodType.BLE, retrievalOptions = BleOptions(peripheralServerMode = false, centralClientMode = true))),
                )
            )

            // Wait for active engagement to be set
            assertNotNull(awaitItem(), "Should have active engagement")

            // When - Close all
            val result = manager.closeAll()
            assertTrue(result.isOk)

            // Then - Everything cleared
            assertNull(manager.qrEngagement.value, "QR should be null")
            assertNull(manager.nfcEngagement.value, "NFC should be null")
            assertNull(manager.activeEngagement.value, "Active engagement should be null")
            assertTrue(manager.engagementsByType.value.isEmpty(), "Type map should be empty")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // EVENT HUB INTEGRATION TESTS
    // ========================================

    @Test
    fun `event hub should receive events from all concurrent engagements`() = runIntegrationTest {
        // Given - Start collecting events BEFORE creating engagements
        // This matches real-world usage where UI sets up listeners before operations
        val receivedEngagementIds = mutableSetOf<Uuid>()

        manager.eventHub.engagementEvents.test(timeout = 3.seconds) {
            // When - Create two engagements while collecting
            val qrResult = manager.createEngagement {
                engagement { qr { scheme = "mdoc:" } }
                retrieval { ble { centralClientMode = true } }
            }
            val nfcResult = manager.createEngagement {
                engagement { nfc { } }
                retrieval { ble { peripheralServerMode = true } }
            }

            assertTrue(qrResult.isOk)
            assertTrue(nfcResult.isOk)

            // Should receive Initializing events from both engagements
            repeat(2) {
                val event = awaitItem()
                receivedEngagementIds.add(event.engagementId)
            }

            // Then - Verify we got events from both engagement IDs
            assertTrue(
                receivedEngagementIds.contains(qrResult.value.id),
                "Should receive QR events"
            )
            assertTrue(
                receivedEngagementIds.contains(nfcResult.value.id),
                "Should receive NFC events"
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `engagementsByType map should reactively update for all engagement lifecycle changes`() = runIntegrationTest {
        // Track all map states
        val mapStates = mutableListOf<Int>()

        manager.engagementsByType.test(timeout = 5.seconds) {
            // Initial empty
            mapStates.add(awaitItem().size)

            // Create QR
            manager.createEngagement {
                engagement { qr { scheme = "mdoc:" } }
                retrieval { ble { centralClientMode = true } }
            }
            mapStates.add(awaitItem().size)

            // Create NFC
            manager.createEngagement {
                engagement { nfc { } }
                retrieval { ble { peripheralServerMode = true } }
            }
            mapStates.add(awaitItem().size)

            // Close QR
            manager.closeQrEngagement()
            mapStates.add(awaitItem().size)

            // Close all
            manager.closeAll()
            mapStates.add(awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }

        // Verify state progression
        assertEquals(0, mapStates[0], "Initial: empty")
        assertEquals(1, mapStates[1], "After QR: 1 engagement")
        assertEquals(2, mapStates[2], "After NFC: 2 engagements")
        assertEquals(1, mapStates[3], "After close QR: 1 engagement")
        assertEquals(0, mapStates[4], "After close all: empty")
    }
}
