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

package com.sphereon.core.api.binary

import com.sphereon.core.api.http.GenericHttpBody
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingBodyByteStreamTest {
    @Test
    fun byteStreamIsNotEmpty() {
        val flow = flowOf(byteArrayOf(1, 2, 3))
        val body = StreamingBody.ByteStream(flow)

        assertFalse(body.isEmpty)
    }

    @Test
    fun asTextOrNullReturnsNull() {
        val flow = flowOf(byteArrayOf(1, 2, 3))
        val body = StreamingBody.ByteStream(flow)

        assertNull(body.asTextOrNull())
    }

    @Test
    fun asBytesOrNullReturnsNull() {
        val flow = flowOf(byteArrayOf(1, 2, 3))
        val body = StreamingBody.ByteStream(flow)

        assertNull(body.asBytesOrNull())
    }

    @Test
    fun collectSingleChunk() =
        runTest {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            val flow = flowOf(data)
            val body = StreamingBody.ByteStream(flow)

            val collected = body.collect()

            assertTrue(data.contentEquals(collected))
        }

    @Test
    fun collectMultipleChunks() =
        runTest {
            val chunk1 = byteArrayOf(1, 2, 3)
            val chunk2 = byteArrayOf(4, 5, 6)
            val chunk3 = byteArrayOf(7, 8, 9)
            val flow = flowOf(chunk1, chunk2, chunk3)
            val body = StreamingBody.ByteStream(flow)

            val collected = body.collect()

            val expected = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
            assertTrue(expected.contentEquals(collected))
        }

    @Test
    fun collectEmptyFlow() =
        runTest {
            val flow = emptyFlow<ByteArray>()
            val body = StreamingBody.ByteStream(flow)

            val collected = body.collect()

            assertTrue(collected.isEmpty())
        }

    @Test
    fun collectToText() =
        runTest {
            val text = "Hello, World!"
            val flow = flowOf(text.encodeToByteArray())
            val body = StreamingBody.ByteStream(flow)

            val collected = body.collectToText()

            assertEquals(text, collected)
        }

    @Test
    fun collectToTextMultipleChunks() =
        runTest {
            val flow =
                flowOf(
                    "Hello".encodeToByteArray(),
                    ", ".encodeToByteArray(),
                    "World!".encodeToByteArray(),
                )
            val body = StreamingBody.ByteStream(flow)

            val collected = body.collectToText()

            assertEquals("Hello, World!", collected)
        }

    @Test
    fun contentLengthIsStored() {
        val flow = flowOf(byteArrayOf(1, 2, 3))
        val body = StreamingBody.ByteStream(flow, contentLength = 100L)

        assertEquals(100L, body.contentLength)
    }

    @Test
    fun contentLengthDefaultsToNull() {
        val flow = flowOf(byteArrayOf(1, 2, 3))
        val body = StreamingBody.ByteStream(flow)

        assertNull(body.contentLength)
    }

    @Test
    fun ofFlowCreatesBodyStream() {
        val flow = flowOf(byteArrayOf(1, 2, 3))

        val body = StreamingBody.ofFlow(flow)

        assertTrue(body is StreamingBody.ByteStream)
    }

    @Test
    fun ofFlowWithContentLength() {
        val flow = flowOf(byteArrayOf(1, 2, 3))

        val body = StreamingBody.ofFlow(flow, contentLength = 50L) as StreamingBody.ByteStream

        assertEquals(50L, body.contentLength)
    }

    @Test
    fun toGenericHttpBodyThrowsForByteStream() {
        val flow = flowOf(byteArrayOf(1, 2, 3))
        val body = StreamingBody.ByteStream(flow)

        assertFailsWith<UnsupportedOperationException> {
            body.toGenericHttpBody()
        }
    }

    @Test
    fun toGenericHttpBodySuspendCollectsStream() =
        runTest {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            val flow = flowOf(data)
            val body = StreamingBody.ByteStream(flow)

            val genericBody = body.toGenericHttpBodySuspend()

            assertTrue(genericBody is GenericHttpBody.Bytes)
            assertTrue(data.contentEquals((genericBody as GenericHttpBody.Bytes).value))
        }

    @Test
    fun toGenericHttpBodySuspendHandlesEmptyStream() =
        runTest {
            val flow = emptyFlow<ByteArray>()
            val body = StreamingBody.ByteStream(flow)

            val genericBody = body.toGenericHttpBodySuspend()

            assertEquals(GenericHttpBody.Empty, genericBody)
        }

    @Test
    fun toGenericHttpBodySuspendWorksForOtherTypes() =
        runTest {
            val textBody = StreamingBody.Text("hello")
            val bytesBody = StreamingBody.Bytes(byteArrayOf(1, 2, 3))
            val emptyBody = StreamingBody.Empty

            assertEquals(GenericHttpBody.Text("hello", "UTF-8"), textBody.toGenericHttpBodySuspend())
            assertTrue((bytesBody.toGenericHttpBodySuspend() as GenericHttpBody.Bytes).value.contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(GenericHttpBody.Empty, emptyBody.toGenericHttpBodySuspend())
        }
}
