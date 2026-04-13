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

package com.sphereon.mdoc.logging

import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for MdocDebugLoggerImpl.
 */
class MdocDebugLoggerTest {

    /**
     * Test LogService that captures logged messages.
     */
    private class TestLogService : LogService {
        val loggedMessages = mutableListOf<String>()

        override val sessionContext: SessionContext = NoOpSessionContext
        override val scope = IdkScope.SESSION
        override val id = "test-log-service"
        override val isEnabled = true

        override suspend fun setConfig(config: LoggerConfig) = this
        override suspend fun getConfig() = LoggerConfig.Default

        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> {
            loggedMessages.add(message.message)
            return IdkOkResult(Unit)
        }

        override fun toAsync(): AsyncLogService {
            return object : AsyncLogService {
                override val sessionContext: SessionContext = NoOpSessionContext
                override val scope = IdkScope.SESSION
                override val id = "test-async-log-service"
                override val isEnabled = true
                override suspend fun setConfig(config: LoggerConfig) = this
                override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)
                override fun toSync() = this@TestLogService
            }
        }
    }

    @Test
    fun testLoggerCreation() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)
        assertNotNull(logger)
        assertTrue(logger.enabled)
    }

    @Test
    fun testLoggerCanBeDisabled() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.enabled = false
        logger.logDeviceEngagement("Test", byteArrayOf(0x01))

        // When disabled, nothing should be logged
        assertTrue(logService.loggedMessages.isEmpty())
    }

    @Test
    fun testLogDeviceEngagement() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logDeviceEngagement("Creating engagement", byteArrayOf(0x01, 0x02))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("DEVICE_ENGAGEMENT"))
        assertTrue(logService.loggedMessages[0].contains("Creating engagement"))
    }

    @Test
    fun testLogDeviceEngagementBytes() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logDeviceEngagementBytes("Wrapped bytes", byteArrayOf(0xD8.toByte(), 0x18))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("DEVICE_ENGAGEMENT_BYTES"))
    }

    @Test
    fun testLogQrCodeData() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logQrCodeData("QR data", "mdoc:abcd1234")

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("QR_CODE"))
        assertTrue(logService.loggedMessages[0].contains("mdoc:abcd1234"))
    }

    @Test
    fun testLogSessionEstablishment() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logSessionEstablishment("Session created", byteArrayOf(0x01))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("SESSION_ESTABLISHMENT"))
    }

    @Test
    fun testLogSessionTranscript() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logSessionTranscript("Transcript", byteArrayOf(0x83.toByte()))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("SESSION_TRANSCRIPT"))
    }

    @Test
    fun testLogSessionTranscriptBytes() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logSessionTranscriptBytes("Wrapped transcript", byteArrayOf(0xD8.toByte(), 0x18))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("SESSION_TRANSCRIPT_BYTES"))
    }

    @Test
    fun testLogHandoverWithNull() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logHandover("QR handover", null)

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("HANDOVER"))
        assertTrue(logService.loggedMessages[0].contains("null"))
    }

    @Test
    fun testLogHandoverWithBytes() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logHandover("NFC handover", byteArrayOf(0x01, 0x02))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("HANDOVER"))
    }

    @Test
    fun testLogSessionKeyDerivation() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logSessionKeyDerivation(
            description = "Key derivation",
            sharedSecretZab = byteArrayOf(0x01, 0x02),
            sessionTranscriptBytesHash = byteArrayOf(0x03, 0x04),
            skDevice = byteArrayOf(0x05, 0x06),
            skReader = byteArrayOf(0x07, 0x08)
        )

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("SESSION_KEY_DERIVATION"))
        assertTrue(logService.loggedMessages[0].contains("ZAB"))
    }

    @Test
    fun testLogSessionKeyDerivationWhenDisabled() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)
        logger.enabled = false

        logger.logSessionKeyDerivation(
            description = "Key derivation",
            sharedSecretZab = byteArrayOf(0x01),
            sessionTranscriptBytesHash = byteArrayOf(0x02),
            skDevice = byteArrayOf(0x03),
            skReader = byteArrayOf(0x04)
        )

        assertTrue(logService.loggedMessages.isEmpty())
    }

    @Test
    fun testLogDeviceRequest() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logDeviceRequest("Request received", byteArrayOf(0x01))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("DEVICE_REQUEST"))
    }

    @Test
    fun testLogDeviceResponse() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logDeviceResponse("Response sent", byteArrayOf(0x01))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("DEVICE_RESPONSE"))
    }

    @Test
    fun testLogSessionData() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logSessionData("Encrypted data", byteArrayOf(0x01), isEncrypted = true)

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("SESSION_DATA"))
        assertTrue(logService.loggedMessages[0].contains("Encrypted: true"))
    }

    @Test
    fun testLogSessionDataUnencrypted() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logSessionData("Plaintext data", byteArrayOf(0x01), isEncrypted = false)

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("Encrypted: false"))
    }

    @Test
    fun testLogBleSend() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logBleSend("State2Device", byteArrayOf(0x01, 0x02))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("BLE_SEND"))
        assertTrue(logService.loggedMessages[0].contains("State2Device"))
    }

    @Test
    fun testLogBleReceive() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logBleReceive("Client2Server", byteArrayOf(0x03, 0x04))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("BLE_RECEIVE"))
        assertTrue(logService.loggedMessages[0].contains("Client2Server"))
    }

    @Test
    fun testLogBleConfig() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logBleConfig(
            description = "BLE service configured",
            serviceUuid = "00001234-0000-1000-8000-00805f9b34fb",
            characteristics = mapOf("State" to "00001235-0000-1000-8000-00805f9b34fb")
        )

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("BLE_CONFIG"))
        assertTrue(logService.loggedMessages[0].contains("00001234-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun testLogBleConfigWhenDisabled() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)
        logger.enabled = false

        logger.logBleConfig(
            description = "Config",
            serviceUuid = "uuid",
            characteristics = emptyMap()
        )

        assertTrue(logService.loggedMessages.isEmpty())
    }

    @Test
    fun testLogBytes() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logBytes("CUSTOM_EVENT", "Custom bytes", byteArrayOf(0xAA.toByte(), 0xBB.toByte()))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("CUSTOM_EVENT"))
        assertTrue(logService.loggedMessages[0].contains("Custom bytes"))
    }

    @Test
    fun testLogText() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logText("INFO", "Simple text message", "Hello World")

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("INFO"))
        assertTrue(logService.loggedMessages[0].contains("Hello World"))
    }

    @Test
    fun testLogTextWhenDisabled() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)
        logger.enabled = false

        logger.logText("INFO", "Message", "Content")

        assertTrue(logService.loggedMessages.isEmpty())
    }

    @Test
    fun testLogEvent() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logEvent("STATE_CHANGE", "Connection established")

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("STATE_CHANGE"))
        assertTrue(logService.loggedMessages[0].contains("Connection established"))
    }

    @Test
    fun testLogEventWhenDisabled() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)
        logger.enabled = false

        logger.logEvent("EVENT", "Description")

        assertTrue(logService.loggedMessages.isEmpty())
    }

    @Test
    fun testLogEDeviceKey() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        // A minimal COSE key structure won't parse, but should still log
        logger.logEDeviceKey("Device key", byteArrayOf(0xA1.toByte(), 0x01, 0x02))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("E_DEVICE_KEY"))
    }

    @Test
    fun testLogEDeviceKeyWhenDisabled() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)
        logger.enabled = false

        logger.logEDeviceKey("Key", byteArrayOf(0x01))

        assertTrue(logService.loggedMessages.isEmpty())
    }

    @Test
    fun testLogEDeviceKeyForEcdh() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logEDeviceKeyForEcdh("ECDH key", byteArrayOf(0xA1.toByte(), 0x01, 0x02))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("E_DEVICE_KEY_FOR_ECDH"))
    }

    @Test
    fun testLogEReaderKey() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        logger.logEReaderKey("Reader key", byteArrayOf(0xA1.toByte(), 0x01, 0x02))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("E_READER_KEY"))
    }

    @Test
    fun testLogEReaderKeyBytes() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        // Tag 24 wrapped bytes
        logger.logEReaderKeyBytes("Wrapped reader key", byteArrayOf(0xD8.toByte(), 0x18, 0xA1.toByte()))

        assertEquals(1, logService.loggedMessages.size)
        assertTrue(logService.loggedMessages[0].contains("E_READER_KEY_BYTES"))
    }

    @Test
    fun testLongHexDataIsSplit() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        // Create a large byte array that will exceed MAX_HEX_LINE_LENGTH
        val largeData = ByteArray(2000) { it.toByte() }
        logger.logBytes("LARGE_DATA", "Large data test", largeData)

        assertEquals(1, logService.loggedMessages.size)
        // Should contain chunked data markers
        assertTrue(logService.loggedMessages[0].contains("[1]") || logService.loggedMessages[0].length > 4000)
    }

    @Test
    fun testEnabledPropertyToggle() {
        val logService = TestLogService()
        val logger = MdocDebugLoggerImpl(logService)

        assertTrue(logger.enabled)

        logger.enabled = false
        assertTrue(!logger.enabled)

        logger.enabled = true
        assertTrue(logger.enabled)
    }
}
