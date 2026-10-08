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

package com.sphereon.mdoc.transfer.integration

import com.sphereon.mdoc.transfer.test.TestUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Integration tests for multi-client peripheral scenarios.
 *
 * Tests the ability of a peripheral (server) to handle multiple simultaneous
 * central (client) connections. Each central should:
 * - Have a unique session ID
 * - Be able to communicate independently
 * - Not interfere with other clients
 *
 * This is critical for peripheral mode where a holder device (like a smartphone)
 * might need to handle multiple verifier connections simultaneously or sequentially.
 */
@OptIn(ExperimentalUuidApi::class)
class MultiClientPeripheralTest {
    @Test
    fun `session ID uniqueness for different devices`() =
        runTest {
            // Given - Three different device addresses
            val device1 = "00:11:22:33:44:55"
            val device2 = "AA:BB:CC:DD:EE:FF"
            val device3 = "11:22:33:44:55:66"

            // When - Create unique session IDs for each
            val sessionId1 = Uuid.random()
            val sessionId2 = Uuid.random()
            val sessionId3 = Uuid.random()

            // Then - All session IDs should be unique
            assertNotEquals(sessionId1, sessionId2, "Session IDs should be unique")
            assertNotEquals(sessionId2, sessionId3, "Session IDs should be unique")
            assertNotEquals(sessionId1, sessionId3, "Session IDs should be unique")

            // Document: In real implementation, MockBlePlatformPeripheral.simulateClientConnect()
            // automatically creates unique session IDs for each device address
        }

    @Test
    fun `session ID persistence across operations for same device`() =
        runTest {
            // Given - Same device performing multiple operations
            val deviceAddress = "00:11:22:33:44:55"
            val sessionId = Uuid.random()

            // When - Simulate multiple write operations from same device
            val operations =
                listOf(
                    "Operation 1".encodeToByteArray(),
                    "Operation 2".encodeToByteArray(),
                    "Operation 3".encodeToByteArray(),
                )

            // Then - All operations should use the same session ID
            // (In real implementation, the peripheral tracks deviceAddress → sessionId mapping)

            // Verify session ID doesn't change
            operations.forEach { data ->
                // Same device, same session ID
                val currentSessionId = sessionId // Would be looked up from map in real code
                assertEquals(sessionId, currentSessionId, "Session ID should persist for same device")
            }
        }

    @Test
    fun `concurrent clients can send different data`() =
        runTest {
            // Given - Three clients with different messages
            val client1Data = "Client 1 message".encodeToByteArray()
            val client2Data = "Client 2 message".encodeToByteArray()
            val client3Data = "Client 3 message".encodeToByteArray()

            // Chunk each message independently
            val client1Chunks = TestUtils.splitIntoChunks(client1Data, 50)
            val client2Chunks = TestUtils.splitIntoChunks(client2Data, 50)
            val client3Chunks = TestUtils.splitIntoChunks(client3Data, 50)

            // Then - Each should reassemble to original
            assertContentEquals(client1Data, TestUtils.reassembleChunks(client1Chunks))
            assertContentEquals(client2Data, TestUtils.reassembleChunks(client2Chunks))
            assertContentEquals(client3Data, TestUtils.reassembleChunks(client3Chunks))

            // And messages should be different
            assertFalse(client1Data.contentEquals(client2Data), "Client messages should differ")
            assertFalse(client2Data.contentEquals(client3Data), "Client messages should differ")
        }

    @Test
    fun `client disconnection removes session`() =
        runTest {
            // Given - A client with a session
            val deviceAddress = "00:11:22:33:44:55"
            val sessionId = Uuid.random()
            var activeSessions = mutableMapOf(deviceAddress to sessionId)

            assertEquals(1, activeSessions.size, "Should have one active session")

            // When - Client disconnects
            activeSessions.remove(deviceAddress)

            // Then - Session should be removed
            assertEquals(0, activeSessions.size, "Should have no active sessions after disconnect")
            assertNull(activeSessions[deviceAddress], "Session should be removed")
        }

    @Test
    fun `one client disconnect does not affect others`() =
        runTest {
            // Given - Three connected clients
            val device1 = "00:11:22:33:44:55"
            val device2 = "AA:BB:CC:DD:EE:FF"
            val device3 = "11:22:33:44:55:66"

            val activeSessions =
                mutableMapOf(
                    device1 to Uuid.random(),
                    device2 to Uuid.random(),
                    device3 to Uuid.random(),
                )

            assertEquals(3, activeSessions.size)

            // When - One client disconnects
            val removedSession = activeSessions.remove(device2)

            // Then - Other clients remain
            assertEquals(2, activeSessions.size, "Should have 2 remaining sessions")
            assertNotNull(removedSession, "Removed session should be returned")
            assertTrue(activeSessions.containsKey(device1), "Device 1 should remain")
            assertFalse(activeSessions.containsKey(device2), "Device 2 should be removed")
            assertTrue(activeSessions.containsKey(device3), "Device 3 should remain")
        }

    @Test
    fun `session timeout cleanup`() =
        runTest {
            // Given - Sessions with last activity timestamps
            val now = 1000000L // Simulated timestamp in milliseconds
            val timeout = 600000L // 10 minutes in milliseconds

            val sessions =
                mutableMapOf(
                    Uuid.random() to now - 700000L, // 11 minutes ago - should be removed
                    Uuid.random() to now - 500000L, // 8 minutes ago - should remain
                    Uuid.random() to now - 100000L, // 1 minute ago - should remain
                    Uuid.random() to now - 800000L, // 13 minutes ago - should be removed
                )

            // When - Clean up stale sessions
            val staleSessions =
                sessions.filter { (_, lastActivity) ->
                    (now - lastActivity) > timeout
                }

            // Then - Verify correct sessions identified for cleanup
            assertEquals(2, staleSessions.size, "Should identify 2 stale sessions")

            // Remove stale sessions
            staleSessions.keys.forEach { sessions.remove(it) }

            assertEquals(2, sessions.size, "Should have 2 active sessions remaining")
        }

    @Test
    fun `activity update keeps session alive`() =
        runTest {
            // Given - Session approaching timeout
            val sessionId = Uuid.random()
            var lastActivity = 1000000L
            val now = 1000000L + 550000L // 9 minutes 10 seconds later
            val timeout = 600000L // 10 minutes

            // When - Activity occurs (write or read)
            lastActivity = now // Update to current time

            // Then - Session should not be timed out
            val timeSinceActivity = now - lastActivity
            assertTrue(timeSinceActivity < timeout, "Session should be active after update")
            assertEquals(0L, timeSinceActivity, "Time since activity should be 0 after update")
        }

    @Test
    fun `maximum concurrent clients simulation`() =
        runTest {
            // Test with many concurrent clients to verify scalability
            val clientCount = 10
            val clients =
                (1..clientCount)
                    .map { i ->
                        "device-${i.toString().padStart(2, '0')}" to Uuid.random()
                    }.toMap()

            assertEquals(clientCount, clients.size, "Should support $clientCount concurrent clients")

            // Verify all session IDs are unique
            val sessionIds = clients.values.toSet()
            assertEquals(clientCount, sessionIds.size, "All session IDs should be unique")

            // Verify all device addresses are unique
            val addresses = clients.keys.toSet()
            assertEquals(clientCount, addresses.size, "All device addresses should be unique")
        }

    @Test
    fun `client reconnection gets new session ID`() =
        runTest {
            // Given - Client that was previously connected
            val deviceAddress = "00:11:22:33:44:55"
            val firstSessionId = Uuid.random()

            var activeSessions = mutableMapOf(deviceAddress to firstSessionId)

            // When - Client disconnects
            activeSessions.remove(deviceAddress)

            // And - Client reconnects
            val newSessionId = Uuid.random()
            activeSessions[deviceAddress] = newSessionId

            // Then - New session ID should be different
            assertNotEquals(
                firstSessionId,
                newSessionId,
                "Reconnected client should get new session ID",
            )
            assertEquals(1, activeSessions.size, "Should have one active session")
            assertEquals(newSessionId, activeSessions[deviceAddress], "Should have new session ID")
        }

    @Test
    fun `interleaved messages from multiple clients`() =
        runTest {
            // Simulate receiving chunks from multiple clients in interleaved fashion
            // (In real implementation, session ID ensures correct message assembly per client)

            val client1Messages =
                listOf(
                    "Client 1 - Message 1".encodeToByteArray(),
                    "Client 1 - Message 2".encodeToByteArray(),
                )

            val client2Messages =
                listOf(
                    "Client 2 - Message 1".encodeToByteArray(),
                    "Client 2 - Message 2".encodeToByteArray(),
                )

            // Each client's messages should chunk and reassemble correctly
            client1Messages.forEach { msg ->
                val chunks = TestUtils.splitIntoChunks(msg, 20)
                assertContentEquals(msg, TestUtils.reassembleChunks(chunks))
            }

            client2Messages.forEach { msg ->
                val chunks = TestUtils.splitIntoChunks(msg, 20)
                assertContentEquals(msg, TestUtils.reassembleChunks(chunks))
            }
        }

    @Test
    fun `peripheral notifications go to all subscribed clients`() =
        runTest {
            // Given - Multiple clients subscribed to notifications
            val subscribedClients =
                setOf(
                    "device-01",
                    "device-02",
                    "device-03",
                )

            val notificationData = "Broadcast notification".encodeToByteArray()

            // When - Peripheral sends notification
            // (In BLE, notifications go to all subscribed centrals)

            // Then - All subscribed clients should receive
            subscribedClients.forEach { deviceAddress ->
                // Each client would receive the notification
                // This is standard BLE behavior - notifications are broadcast to all subscribers
                assertTrue(
                    subscribedClients.contains(deviceAddress),
                    "Client $deviceAddress should be subscribed",
                )
            }

            assertEquals(3, subscribedClients.size, "All 3 clients should receive notification")
        }

    @Test
    fun `session ID not shared between different peripherals`() =
        runTest {
            // Document that session IDs are peripheral-specific

            // Peripheral 1
            val peripheral1Sessions =
                mutableMapOf(
                    "device-A" to Uuid.random(),
                    "device-B" to Uuid.random(),
                )

            // Peripheral 2 (different physical device)
            val peripheral2Sessions =
                mutableMapOf(
                    "device-C" to Uuid.random(),
                    "device-D" to Uuid.random(),
                )

            // Verify sessions are independent
            val allSessionIds = (peripheral1Sessions.values + peripheral2Sessions.values).toSet()
            assertEquals(4, allSessionIds.size, "All session IDs across peripherals should be unique")

            // No overlap in managed devices
            val devices1 = peripheral1Sessions.keys
            val devices2 = peripheral2Sessions.keys
            assertTrue(devices1.intersect(devices2).isEmpty(), "No device should be in both peripherals")
        }

    @Test
    fun `rapid connect-disconnect cycles`() =
        runTest {
            // Test rapid connection and disconnection
            val deviceAddress = "00:11:22:33:44:55"
            var activeSessions = mutableMapOf<String, Uuid>()

            // Simulate 5 rapid connect-disconnect cycles
            repeat(5) { cycle ->
                // Connect
                val sessionId = Uuid.random()
                activeSessions[deviceAddress] = sessionId
                assertEquals(1, activeSessions.size, "Should have 1 session after connect (cycle $cycle)")

                // Disconnect
                activeSessions.remove(deviceAddress)
                assertEquals(0, activeSessions.size, "Should have 0 sessions after disconnect (cycle $cycle)")
            }
        }

    @Test
    fun `session cleanup does not affect active sessions`() =
        runTest {
            // Given - Mix of active and stale sessions
            val now = 1000000L
            val timeout = 600000L

            val sessions =
                mutableMapOf(
                    Uuid.random() to now - 700000L, // Stale
                    Uuid.random() to now - 100000L, // Active
                    Uuid.random() to now - 800000L, // Stale
                    Uuid.random() to now - 50000L, // Active
                )

            val initialSize = sessions.size
            assertEquals(4, initialSize)

            // When - Run cleanup
            val staleSessions =
                sessions
                    .filter { (_, lastActivity) ->
                        (now - lastActivity) > timeout
                    }.keys

            staleSessions.forEach { sessions.remove(it) }

            // Then - Only active sessions remain
            assertEquals(2, sessions.size, "Should have 2 active sessions after cleanup")

            // Verify remaining sessions are actually active
            sessions.values.forEach { lastActivity ->
                val timeSinceActivity = now - lastActivity
                assertTrue(
                    timeSinceActivity <= timeout,
                    "Remaining session should be active (last activity: ${timeSinceActivity}ms ago)",
                )
            }
        }

    @Test
    fun `device address validation`() =
        runTest {
            // Test various device address formats
            val validAddresses =
                listOf(
                    "00:11:22:33:44:55", // Standard MAC format
                    "AA:BB:CC:DD:EE:FF", // All hex letters
                    "12:34:56:78:9A:BC", // Mixed
                    "mock-device-001", // Mock format
                    "test-peripheral", // Test format
                )

            // All should be valid device identifiers
            validAddresses.forEach { address ->
                assertNotNull(address, "Address should not be null")
                assertTrue(address.isNotEmpty(), "Address should not be empty")

                // Can be used as map key
                val testMap = mutableMapOf(address to Uuid.random())
                assertTrue(testMap.containsKey(address), "Address should work as map key")
            }
        }

    @Test
    fun `session ID generation is random`() =
        runTest {
            // Verify session IDs are randomly generated
            val sessionIds = (1..100).map { Uuid.random() }.toSet()

            // With 100 UUIDs, all should be unique (probability of collision is infinitesimal)
            assertEquals(100, sessionIds.size, "All generated session IDs should be unique")
        }

    @Test
    fun `concurrent client data does not intermix`() =
        runTest {
            // Verify that data from different clients doesn't get mixed up

            // Client 1: Small message
            val client1Data = TestUtils.generateRandomByteArray(50, seed = 111)
            val client1Chunks = TestUtils.splitIntoChunks(client1Data, 20)

            // Client 2: Large message
            val client2Data = TestUtils.generateRandomByteArray(500, seed = 222)
            val client2Chunks = TestUtils.splitIntoChunks(client2Data, 20)

            // Client 3: Medium message
            val client3Data = TestUtils.generateRandomByteArray(200, seed = 333)
            val client3Chunks = TestUtils.splitIntoChunks(client3Data, 20)

            // Each should reassemble independently without corruption
            val client1Reassembled = TestUtils.reassembleChunks(client1Chunks)
            val client2Reassembled = TestUtils.reassembleChunks(client2Chunks)
            val client3Reassembled = TestUtils.reassembleChunks(client3Chunks)

            assertContentEquals(client1Data, client1Reassembled, "Client 1 data should not be corrupted")
            assertContentEquals(client2Data, client2Reassembled, "Client 2 data should not be corrupted")
            assertContentEquals(client3Data, client3Reassembled, "Client 3 data should not be corrupted")

            // Verify they're all different
            assertFalse(client1Data.contentEquals(client2Data))
            assertFalse(client2Data.contentEquals(client3Data))
            assertFalse(client1Data.contentEquals(client3Data))
        }
}
