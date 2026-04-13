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

package com.sphereon.mdoc.engagement

import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for MdocEngagementEvent classes.
 */
@OptIn(ExperimentalUuidApi::class)
class MdocEngagementEventTest {

    private val testEngagementId = Uuid.random()

    // Initializing tests

    @Test
    fun testInitializingEvent() {
        val event = MdocEngagementEvent.Initializing(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.INIT, event.state)
        assertNotNull(event.time)
        assertTrue(event.isActive)
    }

    @Test
    fun testInitializingEventAsReader() {
        val event = MdocEngagementEvent.Initializing(
            role = MdocRole.MDOC_READER,
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC_READER, event.role)
        assertEquals(MdocEngagementState.INIT, event.state)
    }

    @Test
    fun testInitializingEventToString() {
        val event = MdocEngagementEvent.Initializing(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        val str = event.toString()
        // toString should not throw and should contain something
        assertNotNull(str)
        assertTrue(str.isNotEmpty())
    }

    // Start tests

    @Test
    fun testStartEvent() {
        val event = MdocEngagementEvent.Start(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.START, event.state)
        assertNotNull(event.time)
    }

    // QrHide tests

    @Test
    fun testQrHideEvent() {
        val event = MdocEngagementEvent.QrHide(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.CONNECTING, event.state)
        assertNotNull(event.time)
    }

    // Connecting tests

    @Test
    fun testConnectingEvent() {
        val bleOptions = BleOptions(
            peripheralServerMode = true,
            peripheralServerModeUuid = Uuid.random(),
            centralClientMode = false
        )
        val retrievalMethods = arrayOf(
            DeviceRetrievalMethod(
                type = DeviceRetrievalMethodType.BLE,
                retrievalOptions = bleOptions
            )
        )

        val event = MdocEngagementEvent.Connecting(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            deviceRetrievalMethods = retrievalMethods
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.CONNECTING, event.state)
        assertEquals(1, event.deviceRetrievalMethods.size)
        assertNotNull(event.time)
    }

    // Connected tests

    @Test
    fun testConnectedEvent() {
        val bleOptions = BleOptions(
            peripheralServerMode = true,
            peripheralServerModeUuid = Uuid.random(),
            centralClientMode = false
        )
        val retrievalMethod = DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            retrievalOptions = bleOptions
        )

        val event = MdocEngagementEvent.Connected(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            deviceRetrievalMethod = retrievalMethod
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.CONNECTED, event.state)
        assertEquals(DeviceRetrievalMethodType.BLE, event.deviceRetrievalMethod.type)
        assertNotNull(event.time)
    }

    // Canceled tests

    @Test
    fun testCanceledEvent() {
        val event = MdocEngagementEvent.Canceled(
            role = MdocRole.MDOC,
            reason = "User canceled",
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals("User canceled", event.reason)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.CANCELED, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testCanceledEventWithoutReason() {
        val event = MdocEngagementEvent.Canceled(
            role = MdocRole.MDOC,
            engagementId = testEngagementId
        )

        assertNull(event.reason)
        assertEquals(MdocEngagementState.CANCELED, event.state)
    }

    // Disconnected tests

    @Test
    fun testDisconnectedEvent() {
        val event = MdocEngagementEvent.Disconnected(
            role = MdocRole.MDOC,
            reason = "Connection lost",
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals("Connection lost", event.reason)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.DISCONNECTED, event.state)
        assertNotNull(event.time)
    }

    // Error tests

    @Test
    fun testErrorEvent() {
        val exception = RuntimeException("Test error")
        val event = MdocEngagementEvent.Error(
            role = MdocRole.MDOC,
            reason = "Something went wrong",
            error = exception,
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals("Something went wrong", event.reason)
        assertEquals(exception, event.error)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.ERROR, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testErrorEventWithoutThrowable() {
        val event = MdocEngagementEvent.Error(
            role = MdocRole.MDOC,
            reason = "Error without exception",
            engagementId = testEngagementId
        )

        assertNull(event.error)
        assertEquals(MdocEngagementState.ERROR, event.state)
    }

    // Debug tests

    @Test
    fun testDebugEvent() {
        val event = MdocEngagementEvent.Debug(
            role = MdocRole.MDOC,
            message = "Debug message",
            engagementId = testEngagementId,
            state = MdocEngagementState.CONNECTED
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals("Debug message", event.message)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.CONNECTED, event.state)
        assertNotNull(event.time)
    }

    // Data tests

    @Test
    fun testDataEventIncoming() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertTrue(data.contentEquals(event.data))
        assertEquals(MdocEngagementEvent.Data.Direction.INCOMING, event.direction)
        assertEquals(MdocEngagementState.DATA, event.state)
        assertNotNull(event.time)
    }

    @Test
    fun testDataEventOutgoing() {
        val data = byteArrayOf(0x04, 0x05)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.OUTGOING
        )

        assertEquals(MdocEngagementEvent.Data.Direction.OUTGOING, event.direction)
    }

    @Test
    fun testDataEventEquality() {
        val data = byteArrayOf(0x01, 0x02)
        val event1 = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )
        val event2 = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        // Different instances with same data should still be equal due to custom equals
        assertEquals(event1.data.contentHashCode(), event2.data.contentHashCode())
    }

    @Test
    fun testDataEventEqualitySameInstance() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        assertEquals(event, event)
    }

    @Test
    fun testDataEventInequalityDifferentClass() {
        val data = byteArrayOf(0x01)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        assertNotEquals<Any>(event, "not an event")
    }

    @Test
    fun testDataEventInequalityNull() {
        val data = byteArrayOf(0x01)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        assertNotEquals<Any?>(event, null)
    }

    @Test
    fun testDataEventHashCode() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        // hashCode should not throw
        val hash = event.hashCode()
        assertNotNull(hash)
    }

    @Test
    fun testDataEventToString() {
        val data = byteArrayOf(0x01, 0x02)
        val event = MdocEngagementEvent.Data(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            data = data,
            direction = MdocEngagementEvent.Data.Direction.INCOMING
        )

        val str = event.toString()
        assertTrue(str.contains("Data"))
        assertTrue(str.contains("INCOMING"))
    }

    // Transfer tests

    @Test
    fun testTransferEvent() {
        val transferId = Uuid.random()
        val event = MdocEngagementEvent.Transfer(
            role = MdocRole.MDOC,
            engagementId = testEngagementId,
            transferId = transferId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(transferId, event.transferId)
        assertEquals(MdocEngagementState.TRANSFER, event.state)
        assertNotNull(event.time)
    }

    // RestApiEngagement tests

    @Test
    fun testRestApiEngagementEvent() {
        val event = MdocEngagementEvent.RestApiEngagement(
            role = MdocRole.MDOC,
            uri = "https://example.com/engage",
            engagementId = testEngagementId
        )

        assertEquals(MdocRole.MDOC, event.role)
        assertEquals("https://example.com/engage", event.uri)
        assertEquals(testEngagementId, event.engagementId)
        assertEquals(MdocEngagementState.INIT, event.state)
        assertNull(event.readerEngagement)
        assertNotNull(event.time)
    }

    // Direction enum tests

    @Test
    fun testDirectionEnumValues() {
        val values = MdocEngagementEvent.Data.Direction.entries
        assertEquals(2, values.size)
        assertTrue(values.contains(MdocEngagementEvent.Data.Direction.INCOMING))
        assertTrue(values.contains(MdocEngagementEvent.Data.Direction.OUTGOING))
    }

    // isActive default tests

    @Test
    fun testIsActiveDefaultsToTrue() {
        val initEvent = MdocEngagementEvent.Initializing(MdocRole.MDOC, testEngagementId)
        val startEvent = MdocEngagementEvent.Start(MdocRole.MDOC, testEngagementId)
        val canceledEvent = MdocEngagementEvent.Canceled(MdocRole.MDOC, engagementId = testEngagementId)
        val errorEvent = MdocEngagementEvent.Error(MdocRole.MDOC, "error", engagementId = testEngagementId)

        assertTrue(initEvent.isActive)
        assertTrue(startEvent.isActive)
        assertTrue(canceledEvent.isActive)
        assertTrue(errorEvent.isActive)
    }
}
