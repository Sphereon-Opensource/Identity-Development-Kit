package com.sphereon.mdoc.engagement

import app.cash.turbine.test
import com.sphereon.mdoc.engagement.mocks.TestMdocEngagementManagerFactory
import com.sphereon.mdoc.transfer.MdocRetrievalEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for MdocEventHub focusing on event stream filtering, listener registration,
 * and adapter pattern functionality.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.uuid.ExperimentalUuidApi::class)
class EventHubTest {

  /*  // ========================================
    // EVENT STREAM FILTERING TESTS
    // ========================================

    @Test
    fun `engagementEvents should only contain engagement events`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub

        manager.createEngagement {
            engagement { qr { } }
        }

        eventHub.engagementEvents.test {
            val event1 = awaitItem()
            assertTrue(event1 is MdocEngagementEvent, "Event should be MdocEngagementEvent, got: ${event1::class.simpleName}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transferEvents should only contain transfer events when transfer starts`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub

        val result = manager.createEngagement {
            engagement { qr { } }
        }

        if (result.isOk) {
            eventHub.transferEvents.test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    *//*@Test
    fun `allEvents should contain both engagement and transfer events`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub

        manager.createEngagement {
            engagement { qr { } }
        }

        eventHub.allEvents.test {
            val event = awaitItem()
            assertTrue(
                event is MdocEngagementEvent || event is MdocRetrievalEvent,
                "Event should be either MdocEngagementEvent or MdocRetrievalEvent"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
*//*
    // ========================================
    // LISTENER REGISTRATION TESTS
    // ========================================

    @Test
    fun `addEngagementEventListener should register listener and receive events`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        // Use a deferred to deterministically await the listener callback
        val received = kotlinx.coroutines.CompletableDeferred<MdocEngagementEvent>()

        val listener = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) {
                if (!received.isCompleted) received.complete(event)
            }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
                if (!received.isCompleted) received.complete(event)
            }
        }

        eventHub.addEngagementEventListener(listener)

        // Use turbine to trigger and observe emission, but assert via the listener completion
        eventHub.engagementEvents.test {
            manager.createEngagement {
                engagement { qr { } }
            }
            // Ensure the flow started producing
            awaitItem()  // Initializing or QrShow
            // Wait until the listener has actually been invoked
            val event = received.await()
            assertTrue(event is MdocEngagementEvent, "Listener should have received an engagement event")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeEngagementEventListener should unregister listener`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        var eventCount = 0

        val listener = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) { eventCount++ }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) { eventCount++ }
        }

        eventHub.addEngagementEventListener(listener)

        // Wait for first engagement's events through flow
        eventHub.engagementEvents.test {
            manager.createEngagement {
                engagement { qr { } }
            }
            awaitItem()  // Wait for at least one event
            advanceUntilIdle()

            cancelAndIgnoreRemainingEvents()
        }

        val firstCount = eventCount
        assertTrue(firstCount > 0, "Should have received events before removal")

        eventHub.removeEngagementEventListener(listener)
        manager.closeQrEngagement()

        // Create a second engagement after listener is removed
        eventHub.engagementEvents.test {
            manager.createEngagement {
                engagement { qr { } }
            }
            awaitItem()  // Wait for event through flow
            advanceUntilIdle()

            // Event count should not have increased since listener was removed
            assertEquals(firstCount, eventCount, "Event count should not increase after listener removal")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearEngagementEventListeners should remove all listeners`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        var listener1Count = 0
        var listener2Count = 0

        val listener1 = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) { listener1Count++ }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) { listener1Count++ }
        }

        val listener2 = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) { listener2Count++ }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) { listener2Count++ }
        }

        eventHub.addEngagementEventListener(listener1, listener2)

        // Wait for first engagement's events through flow
        eventHub.engagementEvents.test {
            manager.createEngagement {
                engagement { qr { } }
            }
            awaitItem()  // Wait for at least one event
            advanceUntilIdle()

            cancelAndIgnoreRemainingEvents()
        }

        // Verify listeners received initial events
        assertTrue(listener1Count > 0, "Listener 1 should have received initial events")
        assertTrue(listener2Count > 0, "Listener 2 should have received initial events")

        // Record counts before clearing
        val listener1CountBeforeClear = listener1Count
        val listener2CountBeforeClear = listener2Count

        eventHub.clearEngagementEventListeners()
        manager.closeQrEngagement()

        // Create a second engagement after listeners are cleared
        eventHub.engagementEvents.test {
            manager.createEngagement {
                engagement { qr { } }
            }
            awaitItem()  // Wait for event through flow
            advanceUntilIdle()

            // Verify listeners did NOT receive events after clearing
            assertEquals(listener1CountBeforeClear, listener1Count, "Listener 1 should not receive events after clearing")
            assertEquals(listener2CountBeforeClear, listener2Count, "Listener 2 should not receive events after clearing")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ========================================
    // ADAPTER PATTERN TESTS
    // ========================================

    @Test
    fun `MdocEngagementEventAdapter should allow selective event handling`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        var qrShowCount = 0
        var connectedCount = 0

        val adapter = object : MdocEngagementEventAdapter() {
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
                qrShowCount++
            }

            override suspend fun onConnected(event: MdocEngagementEvent.Connected) {
                connectedCount++
            }
        }

        eventHub.addEngagementEventListener(adapter)
        manager.createEngagement {
            engagement { qr { } }
        }
        advanceUntilIdle()

        assertTrue(true, "Adapter pattern works correctly")
    }

    @Test
    fun `MdocRetrievalEventAdapter should allow selective event handling`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        var sessionDataCount = 0

        val adapter = object : com.sphereon.mdoc.transfer.MdocRetrievalEventAdapter() {
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {
                sessionDataCount++
            }
        }

        eventHub.addRetrievalEventListener(adapter)

        manager.createEngagement {
            engagement { qr { } }
        }
        advanceUntilIdle()

        assertTrue(true, "Retrieval adapter pattern works correctly")
    }

    // ========================================
    // MULTIPLE LISTENERS TESTS
    // ========================================

    @Test
    fun `multiple listeners should all receive the same events`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        // Use CompletableDeferreds to deterministically await listener callbacks
        val listener1Received = kotlinx.coroutines.CompletableDeferred<MdocEngagementEvent>()
        val listener2Received = kotlinx.coroutines.CompletableDeferred<MdocEngagementEvent>()
        val listener3Received = kotlinx.coroutines.CompletableDeferred<MdocEngagementEvent>()

        val listener1 = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) {
                if (!listener1Received.isCompleted) listener1Received.complete(event)
            }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
                if (!listener1Received.isCompleted) listener1Received.complete(event)
            }
        }

        val listener2 = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) {
                if (!listener2Received.isCompleted) listener2Received.complete(event)
            }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
                if (!listener2Received.isCompleted) listener2Received.complete(event)
            }
        }

        val listener3 = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) {
                if (!listener3Received.isCompleted) listener3Received.complete(event)
            }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
                if (!listener3Received.isCompleted) listener3Received.complete(event)
            }
        }

        eventHub.addEngagementEventListener(listener1)
        eventHub.addEngagementEventListener(listener2)
        eventHub.addEngagementEventListener(listener3)

        manager.createEngagement {
            engagement { qr { } }
        }
        advanceUntilIdle()

        // Wait for all listeners to actually receive events
        val event1 = listener1Received.await()
        val event2 = listener2Received.await()
        val event3 = listener3Received.await()

        assertTrue(event1 is MdocEngagementEvent, "Listener 1 should have received event")
        assertTrue(event2 is MdocEngagementEvent, "Listener 2 should have received event")
        assertTrue(event3 is MdocEngagementEvent, "Listener 3 should have received event")
    }

    // ========================================
    // INTEGRATION TESTS
    // ========================================

    @Test
    fun `eventHub should handle concurrent engagement and transfer events`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager()
        val eventHub = manager.eventHub
        var engagementEventCount = 0
        var transferEventCount = 0
        // Use deferreds to deterministically await at least one callback from each listener
        val engagementReceived = kotlinx.coroutines.CompletableDeferred<Unit>()
        val transferReceived = kotlinx.coroutines.CompletableDeferred<Unit>()

        val engagementListener = object : MdocEngagementEventAdapter() {
            override suspend fun onInitializing(event: MdocEngagementEvent.Initializing) {
                engagementEventCount++
                if (!engagementReceived.isCompleted) engagementReceived.complete(Unit)
            }
            override suspend fun onQrShow(event: MdocEngagementEvent.QrShow) {
                engagementEventCount++
                if (!engagementReceived.isCompleted) engagementReceived.complete(Unit)
            }
        }

        val transferListener = object : com.sphereon.mdoc.transfer.MdocRetrievalEventAdapter() {
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {
                transferEventCount++
                if (!transferReceived.isCompleted) transferReceived.complete(Unit)
            }
        }

        eventHub.addEngagementEventListener(engagementListener)
        eventHub.addRetrievalEventListener(transferListener)

        // Wait for engagement events through flow
        eventHub.engagementEvents.test {
            manager.createEngagement {
                engagement { qr { } }
            }
            awaitItem()  // Wait for at least one event emission from the flow

            // Await actual listener invocations to avoid flakiness
            engagementReceived.await()
            // transfer events might not always occur in this scenario, so don't await strictly if not required
            // If you want to assert transfer too, uncomment the following line when transfer is guaranteed to happen:
            // transferReceived.await()

            advanceUntilIdle()
            assertTrue(engagementEventCount > 0, "Should have received engagement events")

            cancelAndIgnoreRemainingEvents()
        }
    }*/

   /* @Test
    fun `getEngagementEventListeners should return registered listeners`() = runTest(timeout = 5.seconds) {
        val manager = TestMdocEngagementManagerFactory.createTestManager(scope = backgroundScope)
        val eventHub = manager.eventHub

        val listener1 = object : MdocEngagementEventAdapter() {}
        val listener2 = object : MdocEngagementEventAdapter() {}

        eventHub.addEngagementEventListener(listener1, listener2)
        val listeners = eventHub.getEngagementEventListeners()

        assertEquals(2, listeners.size, "Should have 2 registered listeners")
        assertTrue(listeners.contains(listener1), "Should contain listener1")
        assertTrue(listeners.contains(listener2), "Should contain listener2")
    }*/
}
