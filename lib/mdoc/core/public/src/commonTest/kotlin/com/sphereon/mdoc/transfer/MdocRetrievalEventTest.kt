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

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborNull
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for MdocRetrievalEvent sealed interface and its implementations.
 */
@OptIn(ExperimentalUuidApi::class)
class MdocRetrievalEventTest {

    private val testEngagementId = Uuid.random()

    // Initializing tests

    @Test
    fun testInitializingEvent() {
        val event = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.INIT, event.state)
        assertTrue(event.data.isEmpty())
        assertNotNull(event.time)
        assertTrue(event.isActive)
        assertNull(event.engagementEvent)
    }

    @Test
    fun testInitializingEventToCbor() {
        val event = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    @Test
    fun testInitializingEventToString() {
        val event = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val str = event.toString()
        assertNotNull(str)
        assertTrue(str.contains("Initializing"))
    }

    // TransmissionTypeSelected tests

    @Test
    fun testTransmissionTypeSelectedEvent() {
        val bleOptions = BleOptions(
            peripheralServerMode = true,
            centralClientMode = false,
            peripheralServerModeUuid = Uuid.random()
        )
        val retrievalMethod = DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = bleOptions
        )

        val event = MdocRetrievalEvent.TransmissionTypeSelected(
            engagementId = testEngagementId,
            deviceRetrievalMethod = retrievalMethod
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.TRANSMISSION_TYPE_SELECTED, event.state)
        assertEquals(retrievalMethod, event.deviceRetrievalMethod)
        assertNotNull(event.time)
    }

    @Test
    fun testTransmissionTypeSelectedEventToCbor() {
        val bleOptions = BleOptions(peripheralServerMode = true, centralClientMode = false)
        val retrievalMethod = DeviceRetrievalMethod(type = DeviceRetrievalMethodType.BLE, retrievalOptions = bleOptions)
        val event = MdocRetrievalEvent.TransmissionTypeSelected(engagementId = testEngagementId, deviceRetrievalMethod = retrievalMethod)
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    // SessionEstablishmentReceived tests

    @Test
    fun testSessionEstablishmentReceivedEvent() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val event = MdocRetrievalEvent.SessionEstablishmentReceived(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED, event.state)
        assertTrue(data.contentEquals(event.data))
        assertNotNull(event.time)
    }

    @Test
    fun testSessionEstablishmentReceivedEquality() {
        val data = byteArrayOf(0x01, 0x02)
        val event1 = MdocRetrievalEvent.SessionEstablishmentReceived(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.SessionEstablishmentReceived(engagementId = testEngagementId, data = data)

        // Data class equality
        assertEquals(event1.engagementId, event2.engagementId)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testSessionEstablishmentReceivedInequalityNull() {
        val event = MdocRetrievalEvent.SessionEstablishmentReceived(engagementId = testEngagementId, data = byteArrayOf())
        assertFalse(event.equals(null))
    }

    @Test
    fun testSessionEstablishmentReceivedInequalityDifferentClass() {
        val event = MdocRetrievalEvent.SessionEstablishmentReceived(engagementId = testEngagementId, data = byteArrayOf())
        assertFalse(event.equals("not an event"))
    }

    @Test
    fun testSessionEstablishmentReceivedHashCode() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocRetrievalEvent.SessionEstablishmentReceived(engagementId = testEngagementId, data = data)
        val hash = event.hashCode()
        assertNotNull(hash)
    }

    // DeviceRequestReady tests

    @Test
    fun testDeviceRequestReadyEvent() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocRetrievalEvent.DeviceRequestReady(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testDeviceRequestReadyEquality() {
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.DeviceRequestReady(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.DeviceRequestReady(engagementId = testEngagementId, data = data)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testDeviceRequestReadyHashCode() {
        val event = MdocRetrievalEvent.DeviceRequestReady(engagementId = testEngagementId, data = byteArrayOf())
        assertNotNull(event.hashCode())
    }

    // DocumentsSelectionProcessStart tests

    @Test
    fun testDocumentsSelectionProcessStartEvent() {
        val data = byteArrayOf(0x01)
        val event = MdocRetrievalEvent.DocumentsSelectionProcessStart(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_START, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testDocumentsSelectionProcessStartEquality() {
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.DocumentsSelectionProcessStart(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.DocumentsSelectionProcessStart(engagementId = testEngagementId, data = data)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testDocumentsSelectionProcessStartHashCode() {
        val event = MdocRetrievalEvent.DocumentsSelectionProcessStart(engagementId = testEngagementId, data = byteArrayOf())
        assertNotNull(event.hashCode())
    }

    // DocumentsSelectionProcessAccepted tests

    @Test
    fun testDocumentsSelectionProcessAcceptedEvent() {
        val data = byteArrayOf(0x01)
        val event = MdocRetrievalEvent.DocumentsSelectionProcessAccepted(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_ACCEPTED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testDocumentsSelectionProcessAcceptedEquality() {
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.DocumentsSelectionProcessAccepted(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.DocumentsSelectionProcessAccepted(engagementId = testEngagementId, data = data)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testDocumentsSelectionProcessAcceptedHashCode() {
        val event = MdocRetrievalEvent.DocumentsSelectionProcessAccepted(engagementId = testEngagementId, data = byteArrayOf())
        assertNotNull(event.hashCode())
    }

    // DocumentsSelectionProcessDeclined tests

    @Test
    fun testDocumentsSelectionProcessDeclinedEvent() {
        val data = byteArrayOf(0x01)
        val event = MdocRetrievalEvent.DocumentsSelectionProcessDeclined(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_DECLINED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testDocumentsSelectionProcessDeclinedToCbor() {
        val event = MdocRetrievalEvent.DocumentsSelectionProcessDeclined(engagementId = testEngagementId, data = byteArrayOf())
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    @Test
    fun testDocumentsSelectionProcessDeclinedEquality() {
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.DocumentsSelectionProcessDeclined(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.DocumentsSelectionProcessDeclined(engagementId = testEngagementId, data = data)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testDocumentsSelectionProcessDeclinedHashCode() {
        val event = MdocRetrievalEvent.DocumentsSelectionProcessDeclined(engagementId = testEngagementId, data = byteArrayOf())
        assertNotNull(event.hashCode())
    }

    // SessionDataSend tests

    @Test
    fun testSessionDataSendEvent() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocRetrievalEvent.SessionDataSend(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.SESSION_DATA_SEND, event.state)
        assertNull(event.deviceResponse)
        assertNull(event.toDeviceResponse())
        assertNotNull(event.time)
    }

    @Test
    fun testSessionDataSendEquality() {
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.SessionDataSend(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.SessionDataSend(engagementId = testEngagementId, data = data)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testSessionDataSendHashCode() {
        val event = MdocRetrievalEvent.SessionDataSend(engagementId = testEngagementId, data = byteArrayOf())
        assertNotNull(event.hashCode())
    }

    // SessionDataReceived tests

    @Test
    fun testSessionDataReceivedEvent() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocRetrievalEvent.SessionDataReceived(
            engagementId = testEngagementId,
            data = data
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.SESSION_DATA_SEND, event.state)
        assertNull(event.deviceRequest)
        assertNull(event.toDeviceRequest())
        assertNotNull(event.time)
    }

    @Test
    fun testSessionDataReceivedEquality() {
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.SessionDataReceived(engagementId = testEngagementId, data = data)
        val event2 = MdocRetrievalEvent.SessionDataReceived(engagementId = testEngagementId, data = data)
        assertTrue(event1.data.contentEquals(event2.data))
    }

    @Test
    fun testSessionDataReceivedHashCode() {
        val event = MdocRetrievalEvent.SessionDataReceived(engagementId = testEngagementId, data = byteArrayOf())
        assertNotNull(event.hashCode())
    }

    // SessionTerminationSend tests

    @Test
    fun testSessionTerminationSendEvent() {
        val event = MdocRetrievalEvent.SessionTerminationSend(engagementId = testEngagementId)

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.TERMINATED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testSessionTerminationSendToCbor() {
        val event = MdocRetrievalEvent.SessionTerminationSend(engagementId = testEngagementId)
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    @Test
    fun testSessionTerminationSendEquality() {
        val event1 = MdocRetrievalEvent.SessionTerminationSend(engagementId = testEngagementId)
        val event2 = MdocRetrievalEvent.SessionTerminationSend(engagementId = testEngagementId)
        assertEquals(event1.engagementId, event2.engagementId)
    }

    @Test
    fun testSessionTerminationSendHashCode() {
        val event = MdocRetrievalEvent.SessionTerminationSend(engagementId = testEngagementId)
        assertNotNull(event.hashCode())
    }

    // SessionTerminationReceived tests

    @Test
    fun testSessionTerminationReceivedEvent() {
        val event = MdocRetrievalEvent.SessionTerminationReceived(engagementId = testEngagementId)

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.TERMINATED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testSessionTerminationReceivedToCbor() {
        val event = MdocRetrievalEvent.SessionTerminationReceived(engagementId = testEngagementId)
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    @Test
    fun testSessionTerminationReceivedEquality() {
        val event1 = MdocRetrievalEvent.SessionTerminationReceived(engagementId = testEngagementId)
        val event2 = MdocRetrievalEvent.SessionTerminationReceived(engagementId = testEngagementId)
        assertEquals(event1.engagementId, event2.engagementId)
    }

    @Test
    fun testSessionTerminationReceivedHashCode() {
        val event = MdocRetrievalEvent.SessionTerminationReceived(engagementId = testEngagementId)
        assertNotNull(event.hashCode())
    }

    // Terminated tests

    @Test
    fun testTerminatedEvent() {
        val event = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.TERMINATED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testTerminatedToCbor() {
        val event = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    @Test
    fun testTerminatedEquality() {
        val event1 = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)
        val event2 = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)
        assertEquals(event1.engagementId, event2.engagementId)
    }

    @Test
    fun testTerminatedHashCode() {
        val event = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)
        assertNotNull(event.hashCode())
    }

    // Error tests

    @Test
    fun testErrorEvent() {
        val error = RuntimeException("Test error")
        val data = byteArrayOf(0x01)
        val event = MdocRetrievalEvent.Error(
            engagementId = testEngagementId,
            data = data,
            error = error
        )

        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocRetrievalState.ERROR, event.state)
        assertEquals(error, event.error)
        assertNotNull(event.time)
    }

    @Test
    fun testErrorToCbor() {
        val event = MdocRetrievalEvent.Error(
            engagementId = testEngagementId,
            data = byteArrayOf(),
            error = RuntimeException("Test")
        )
        val cbor = event.toCbor()
        assertTrue(cbor is CborNull)
    }

    @Test
    fun testErrorEquality() {
        val error = RuntimeException("Test")
        val data = byteArrayOf(0x01)
        val event1 = MdocRetrievalEvent.Error(engagementId = testEngagementId, data = data, error = error)
        val event2 = MdocRetrievalEvent.Error(engagementId = testEngagementId, data = data, error = error)
        assertTrue(event1.data.contentEquals(event2.data))
        assertEquals(event1.error, event2.error)
    }

    @Test
    fun testErrorHashCode() {
        val event = MdocRetrievalEvent.Error(
            engagementId = testEngagementId,
            data = byteArrayOf(),
            error = RuntimeException("Test")
        )
        assertNotNull(event.hashCode())
    }

    // MdocRetrievalEventWithEngagement tests

    @Test
    fun testMdocRetrievalEventWithEngagement() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        val wrapper = MdocRetrievalEventWithEngagement(
            delegate = delegate,
            engagementEvent = engagementEvent
        )

        assertEquals(delegate, wrapper.delegate)
        assertEquals(engagementEvent, wrapper.engagementEvent)
        assertEquals(testEngagementId, wrapper.engagementId)
    }

    @Test
    fun testMdocRetrievalEventWithEngagementEquality() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        val wrapper1 = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)
        val wrapper2 = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)

        assertEquals(wrapper1, wrapper2)
    }

    @Test
    fun testMdocRetrievalEventWithEngagementEqualitySameInstance() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = testEngagementId)
        val wrapper = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)

        assertEquals(wrapper, wrapper)
    }

    @Test
    fun testMdocRetrievalEventWithEngagementInequalityNull() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = testEngagementId)
        val wrapper = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)

        assertFalse(wrapper.equals(null))
    }

    @Test
    fun testMdocRetrievalEventWithEngagementInequalityDifferentClass() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = testEngagementId)
        val wrapper = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)

        assertFalse(wrapper.equals("not a wrapper"))
    }

    @Test
    fun testMdocRetrievalEventWithEngagementHashCode() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = testEngagementId)
        val wrapper = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)

        assertNotNull(wrapper.hashCode())
    }

    @Test
    fun testMdocRetrievalEventWithEngagementToString() {
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = testEngagementId)
        val wrapper = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)

        val str = wrapper.toString()
        assertTrue(str.contains("MdocRetrievalEventWithEngagement"))
    }

    // isFinal tests

    @Test
    fun testTerminatedIsFinal() {
        val event = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)
        assertTrue(event.isFinal)
    }

    @Test
    fun testErrorIsFinal() {
        val event = MdocRetrievalEvent.Error(
            engagementId = testEngagementId,
            data = byteArrayOf(),
            error = RuntimeException("Test")
        )
        assertTrue(event.isFinal)
    }

    @Test
    fun testInitializingIsNotFinal() {
        val event = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        assertFalse(event.isFinal)
    }

    // onRetrievalEvent extension function tests

    @Test
    fun testOnRetrievalEventInitializing() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) { called = true }
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventTransmissionTypeSelected() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) { called = true }
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val bleOptions = BleOptions(peripheralServerMode = true, centralClientMode = false)
        val retrievalMethod = DeviceRetrievalMethod(type = DeviceRetrievalMethodType.BLE, retrievalOptions = bleOptions)
        val event = MdocRetrievalEvent.TransmissionTypeSelected(engagementId = testEngagementId, deviceRetrievalMethod = retrievalMethod)
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventSessionEstablishmentReceived() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) { called = true }
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.SessionEstablishmentReceived(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventDeviceRequestReady() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) { called = true }
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.DeviceRequestReady(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventDocumentsSelectionProcessStart() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) { called = true }
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.DocumentsSelectionProcessStart(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventDocumentsSelectionProcessAccepted() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) { called = true }
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.DocumentsSelectionProcessAccepted(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventDocumentsSelectionProcessDeclined() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) { called = true }
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.DocumentsSelectionProcessDeclined(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventSessionDataSend() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) { called = true }
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.SessionDataSend(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventSessionDataReceived() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) { called = true }
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.SessionDataReceived(engagementId = testEngagementId, data = byteArrayOf())
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventSessionTerminationSend() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) { called = true }
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.SessionTerminationSend(engagementId = testEngagementId)
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventSessionTerminationReceived() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) { called = true }
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.SessionTerminationReceived(engagementId = testEngagementId)
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventTerminated() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) { called = true }
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.Terminated(engagementId = testEngagementId)
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventError() = runTest {
        var called = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) {}
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) { called = true }
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val event = MdocRetrievalEvent.Error(engagementId = testEngagementId, data = byteArrayOf(), error = RuntimeException("Test"))
        listeners.onRetrievalEvent(event)
        assertTrue(called)
    }

    @Test
    fun testOnRetrievalEventWithEngagementWrapper() = runTest {
        var initializingCalled = false
        val listener = object : MdocRetrievalEvent.Listener {
            override suspend fun onInitializing(event: MdocRetrievalEvent.Initializing) { initializingCalled = true }
            override suspend fun onTransmissionTypeSelected(event: MdocRetrievalEvent.TransmissionTypeSelected) {}
            override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {}
            override suspend fun onDeviceRequestReady(event: MdocRetrievalEvent.DeviceRequestReady) {}
            override suspend fun onDocumentsSelectionProcessStart(event: MdocRetrievalEvent.DocumentsSelectionProcessStart) {}
            override suspend fun onDocumentsSelectionProcessAccepted(event: MdocRetrievalEvent.DocumentsSelectionProcessAccepted) {}
            override suspend fun onDocumentsSelectionProcessDeclined(event: MdocRetrievalEvent.DocumentsSelectionProcessDeclined) {}
            override suspend fun onSessionDataSend(event: MdocRetrievalEvent.SessionDataSend) {}
            override suspend fun onSessionDataReceived(event: MdocRetrievalEvent.SessionDataReceived) {}
            override suspend fun onSessionTerminationSend(event: MdocRetrievalEvent.SessionTerminationSend) {}
            override suspend fun onSessionTerminationReceived(event: MdocRetrievalEvent.SessionTerminationReceived) {}
            override suspend fun onError(event: MdocRetrievalEvent.Error) {}
            override suspend fun onTerminated(event: MdocRetrievalEvent.Terminated) {}
        }
        val listeners = listOf(listener)
        val delegate = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        val engagementEvent = MdocEngagementEvent.Initializing(role = MdocRole.MDOC, engagementId = testEngagementId)
        val wrappedEvent = MdocRetrievalEventWithEngagement(delegate = delegate, engagementEvent = engagementEvent)
        listeners.onRetrievalEvent(wrappedEvent)
        assertTrue(initializingCalled)
    }

    @Test
    fun testOnRetrievalEventWithEmptyListeners() = runTest {
        val listeners = emptyList<MdocRetrievalEvent.Listener>()
        val event = MdocRetrievalEvent.Initializing(engagementId = testEngagementId)
        // Should not throw
        listeners.onRetrievalEvent(event)
    }
}
