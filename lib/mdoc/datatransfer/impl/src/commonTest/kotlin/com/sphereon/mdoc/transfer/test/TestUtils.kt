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

package com.sphereon.mdoc.transfer.test

import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogManager
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test utilities for BLE data channel testing.
 * Provides mock object factories, test data generators, and common assertions.
 */
@OptIn(ExperimentalUuidApi::class)
object TestUtils {

    /**
     * Test UUIDs for consistent testing
     */
    object TestUuids {
        val TEST_SERVICE_UUID = Uuid.parse("00000001-A123-48CE-896B-4C76973373E6")
        val TEST_CHAR_UUID = Uuid.parse("00000002-A123-48CE-896B-4C76973373E6")
        val TEST_STATE_CHAR_UUID = Uuid.parse("00000003-A123-48CE-896B-4C76973373E6")
        val TEST_OUTGOING_CHAR_UUID = Uuid.parse("00000004-A123-48CE-896B-4C76973373E6")
        val HOLDER_SERVICE_UUID = Uuid.parse("0000000A-A123-48CE-896B-4C76973373E6")
        val READER_SERVICE_UUID = Uuid.parse("0000000B-A123-48CE-896B-4C76973373E6")
    }

    /**
     * Generates random byte array of specified size.
     *
     * @param size The size of the array
     * @param seed Optional seed for reproducibility
     * @return A ByteArray with random data
     */
    fun generateRandomByteArray(size: Int, seed: Int? = null): ByteArray {
        val random = if (seed != null) Random(seed) else Random
        return ByteArray(size) { random.nextInt(256).toByte() }
    }

    /**
     * Creates a simple mock log manager for testing.
     * Logs are printed to console when level is INFO or higher.
     */
    fun createMockLogManager(): LogManager {
        return MockLogManager()
    }

    /**
     * Mock LogManager implementation for testing.
     */
    private class MockLogManager : LogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig) = this
        override suspend fun getGlobalConfig() = LoggerConfig.Default
        override fun withTagAsync(tag: String, config: LoggerConfig?) = MockAsyncLogService()
        override fun withTag(tag: String, config: LoggerConfig?) = MockLogService()
    }

    /**
     * Mock LogService implementation for testing.
     */
    private class MockLogService : LogService {
        override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
        override val scope = com.sphereon.core.api.context.IdkScope.SESSION
        override val id = "mock-log-service"
        override val isEnabled = true

        override suspend fun setConfig(config: LoggerConfig) = this
        override suspend fun getConfig() = LoggerConfig.Default

        override fun executeAsync(message: LogMessage) =
            com.sphereon.core.api.IdkOkResult(Unit)

        override fun toAsync() = MockAsyncLogService()
    }

    /**
     * Mock AsyncLogService implementation for testing.
     */
    private class MockAsyncLogService : AsyncLogService {
        override val sessionContext: com.sphereon.di.session.SessionContext = com.sphereon.di.context.NoOpSessionContext
        override val scope = com.sphereon.core.api.context.IdkScope.SESSION
        override val id = "mock-async-log-service"
        override val isEnabled = true

        override suspend fun setConfig(config: LoggerConfig) = this
        override suspend fun getConfig() = LoggerConfig.Default

        override suspend fun execute(args: LogMessage) =
            com.sphereon.core.api.IdkOkResult(Unit)

        override fun toSync() = MockLogService()
    }

    /**
     * Asserts that two byte arrays have the same content.
     * Provides more detailed error message than default assertEquals.
     *
     * @param expected The expected byte array
     * @param actual The actual byte array
     * @param message Optional message to include in assertion failure
     */
    fun assertBytesEqual(expected: ByteArray, actual: ByteArray, message: String? = null) {
        val prefix = if (message != null) "$message: " else ""

        if (expected.size != actual.size) {
            throw AssertionError("${prefix}Array sizes differ. Expected: ${expected.size}, Actual: ${actual.size}")
        }

        expected.forEachIndexed { index, byte ->
            if (byte != actual[index]) {
                throw AssertionError(
                    "${prefix}Arrays differ at index $index. " +
                            "Expected: 0x${byte.toString(16).padStart(2, '0')}, " +
                            "Actual: 0x${actual[index].toString(16).padStart(2, '0')}"
                )
            }
        }
    }

    /**
     * Asserts that a byte array starts with a specific prefix.
     *
     * @param prefix The expected prefix
     * @param actual The actual byte array
     * @param message Optional message
     */
    fun assertStartsWith(prefix: ByteArray, actual: ByteArray, message: String? = null) {
        val msg = message ?: "ByteArray should start with expected prefix"

        assertTrue(actual.size >= prefix.size, "$msg - actual size ${actual.size} < prefix size ${prefix.size}")

        val actualPrefix = actual.copyOfRange(0, prefix.size)
        assertContentEquals(prefix, actualPrefix, msg)
    }

    /**
     * Creates a chunked message with 0x00 (end) or 0x01 (continuation) prefix.
     *
     * @param data The data to chunk
     * @param isLastChunk Whether this is the last chunk
     * @return ByteArray with prefix
     */
    fun createChunk(data: ByteArray, isLastChunk: Boolean): ByteArray {
        val prefix = if (isLastChunk) 0x00.toByte() else 0x01.toByte()
        return byteArrayOf(prefix) + data
    }

    /**
     * Splits a message into chunks of specified size with appropriate prefixes.
     *
     * @param message The message to split
     * @param chunkSize The maximum chunk payload size (not including prefix)
     * @return List of chunked ByteArrays with prefixes
     */
    fun splitIntoChunks(message: ByteArray, chunkSize: Int): List<ByteArray> {
        if (message.isEmpty()) {
            return listOf(byteArrayOf(0x00))  // Empty message = just end marker
        }

        val chunks = mutableListOf<ByteArray>()
        var offset = 0

        while (offset < message.size) {
            val remainingSize = message.size - offset
            val currentChunkSize = minOf(chunkSize, remainingSize)
            val isLastChunk = (offset + currentChunkSize >= message.size)

            val chunkData = message.copyOfRange(offset, offset + currentChunkSize)
            chunks.add(createChunk(chunkData, isLastChunk))

            offset += currentChunkSize
        }

        return chunks
    }

    /**
     * Reassembles chunks back into original message (removes prefixes).
     *
     * @param chunks The list of chunks to reassemble
     * @return The reassembled message
     */
    fun reassembleChunks(chunks: List<ByteArray>): ByteArray {
        return chunks.flatMap { chunk ->
            if (chunk.isEmpty()) {
                emptyList()
            } else {
                chunk.drop(1) // Remove the 0x00 or 0x01 prefix
            }
        }.toByteArray()
    }

}
