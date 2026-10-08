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

package com.sphereon.mdoc.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/**
 * Tests for DataChannels interfaces.
 */
class DataChannelsTest {
    // IncomingDataChannel tests

    /**
     * Test implementation of IncomingDataChannel using default implementations.
     */
    private class TestIncomingDataChannel : IncomingDataChannel {
        var closeCalled = false
        var lastDispatcher: DataChannelEventDispatcher? = null

        override suspend fun receiveRaw(): ByteArray = byteArrayOf(0x01, 0x02, 0x03)

        override fun close() {
            closeCalled = true
        }

        // Override setEventDispatcher to track calls
        override fun setEventDispatcher(dispatcher: DataChannelEventDispatcher?) {
            lastDispatcher = dispatcher
        }
    }

    /**
     * Test implementation using only default implementations.
     */
    private class DefaultIncomingDataChannel : IncomingDataChannel {
        override suspend fun receiveRaw(): ByteArray = byteArrayOf()

        override fun close() {}
    }

    @Test
    fun testIncomingDataChannelReceiveRaw() =
        runTest {
            val channel = TestIncomingDataChannel()
            val data = channel.receiveRaw()

            assertEquals(3, data.size)
            assertEquals(0x01.toByte(), data[0])
            assertEquals(0x02.toByte(), data[1])
            assertEquals(0x03.toByte(), data[2])
        }

    @Test
    fun testIncomingDataChannelDefaultIsClosed() {
        val channel = DefaultIncomingDataChannel()
        assertFalse(channel.isClosed)
    }

    @Test
    fun testIncomingDataChannelDefaultSetEventDispatcher() {
        val channel = DefaultIncomingDataChannel()
        // Should not throw - default implementation is no-op
        channel.setEventDispatcher(null)
    }

    @Test
    fun testIncomingDataChannelClose() =
        runTest {
            val channel = TestIncomingDataChannel()
            channel.close()
            kotlin.test.assertTrue(channel.closeCalled)
        }

    @Test
    fun testIncomingDataChannelSetEventDispatcher() {
        val channel = TestIncomingDataChannel()
        val dispatcher =
            object : DataChannelEventDispatcher {
                override fun dispatchSessionEstablishmentReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchTerminationReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataSent(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}
            }

        channel.setEventDispatcher(dispatcher)
        assertEquals(dispatcher, channel.lastDispatcher)
    }

    // OutgoingDataChannel tests

    /**
     * Test implementation of OutgoingDataChannel.
     */
    private class TestOutgoingDataChannel : OutgoingDataChannel {
        var closeCalled = false
        var lastSentData: ByteArray? = null

        override suspend fun sendRaw(data: ByteArray): Int {
            lastSentData = data
            return data.size
        }

        override fun close() {
            closeCalled = true
        }
    }

    /**
     * Test implementation using only default implementations.
     */
    private class DefaultOutgoingDataChannel : OutgoingDataChannel {
        override suspend fun sendRaw(data: ByteArray): Int = data.size

        override fun close() {}
    }

    @Test
    fun testOutgoingDataChannelSendRaw() =
        runTest {
            val channel = TestOutgoingDataChannel()
            val testData = byteArrayOf(0x04, 0x05, 0x06)

            val bytesSent = channel.sendRaw(testData)

            assertEquals(3, bytesSent)
            kotlin.test.assertTrue(testData.contentEquals(channel.lastSentData))
        }

    @Test
    fun testOutgoingDataChannelDefaultSendEndMessage() =
        runTest {
            val channel = DefaultOutgoingDataChannel()
            val result = channel.sendEndMessage()

            assertEquals(0, result) // Default returns 0 (success)
        }

    @Test
    fun testOutgoingDataChannelDefaultIsClosed() {
        val channel = DefaultOutgoingDataChannel()
        assertFalse(channel.isClosed)
    }

    @Test
    fun testOutgoingDataChannelClose() =
        runTest {
            val channel = TestOutgoingDataChannel()
            channel.close()
            kotlin.test.assertTrue(channel.closeCalled)
        }

    // DataChannelEventDispatcher tests

    @Test
    fun testDataChannelEventDispatcherSessionEstablishment() {
        var receivedInstanceId: Uuid? = null
        var receivedData: ByteArray? = null

        val dispatcher =
            object : DataChannelEventDispatcher {
                override fun dispatchSessionEstablishmentReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {
                    receivedInstanceId = instanceId
                    receivedData = data
                }

                override fun dispatchSessionDataReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchTerminationReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataSent(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}
            }

        val testId = Uuid.random()
        val testData = byteArrayOf(0x01, 0x02)

        dispatcher.dispatchSessionEstablishmentReceived(testId, testData)

        assertEquals(testId, receivedInstanceId)
        kotlin.test.assertTrue(testData.contentEquals(receivedData))
    }

    @Test
    fun testDataChannelEventDispatcherSessionData() {
        var receivedInstanceId: Uuid? = null
        var receivedData: ByteArray? = null

        val dispatcher =
            object : DataChannelEventDispatcher {
                override fun dispatchSessionEstablishmentReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {
                    receivedInstanceId = instanceId
                    receivedData = data
                }

                override fun dispatchTerminationReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataSent(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}
            }

        val testId = Uuid.random()
        val testData = byteArrayOf(0x03, 0x04)

        dispatcher.dispatchSessionDataReceived(testId, testData)

        assertEquals(testId, receivedInstanceId)
        kotlin.test.assertTrue(testData.contentEquals(receivedData))
    }

    @Test
    fun testDataChannelEventDispatcherTermination() {
        var receivedInstanceId: Uuid? = null
        var receivedData: ByteArray? = null

        val dispatcher =
            object : DataChannelEventDispatcher {
                override fun dispatchSessionEstablishmentReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchTerminationReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {
                    receivedInstanceId = instanceId
                    receivedData = data
                }

                override fun dispatchSessionDataSent(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}
            }

        val testId = Uuid.random()
        val testData = byteArrayOf(0x05, 0x06)

        dispatcher.dispatchTerminationReceived(testId, testData)

        assertEquals(testId, receivedInstanceId)
        kotlin.test.assertTrue(testData.contentEquals(receivedData))
    }

    @Test
    fun testDataChannelEventDispatcherSessionDataSent() {
        var receivedInstanceId: Uuid? = null
        var receivedData: ByteArray? = null

        val dispatcher =
            object : DataChannelEventDispatcher {
                override fun dispatchSessionEstablishmentReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchTerminationReceived(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {}

                override fun dispatchSessionDataSent(
                    instanceId: Uuid,
                    data: ByteArray,
                ) {
                    receivedInstanceId = instanceId
                    receivedData = data
                }
            }

        val testId = Uuid.random()
        val testData = byteArrayOf(0x07, 0x08)

        dispatcher.dispatchSessionDataSent(testId, testData)

        assertEquals(testId, receivedInstanceId)
        kotlin.test.assertTrue(testData.contentEquals(receivedData))
    }
}
