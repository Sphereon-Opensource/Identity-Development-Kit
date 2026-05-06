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

package com.sphereon.core.api.random

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ByteArrayResult
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.session.CommandAdapter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for the [SecureRandom] service facade's convenience methods
 * ([SecureRandom.newToken], [SecureRandom.randomBytes]) and command delegation,
 * using deterministic stub commands so we can assert on exact outputs.
 */
class SecureRandomContractTest {
    /** Emits a fixed byte pattern so contract assertions are reproducible. */
    private class FixedByteGenerateTokenCommand(
        private val fillByte: Byte
    ) : CommandAdapter<GenerateTokenArgs, StringResult, IdkError>(id = GenerateTokenCommand.COMMAND_ID),
        GenerateTokenCommand {
        override val id: String get() = GenerateTokenCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is GenerateTokenArgs

        override suspend fun doExecute(
            args: GenerateTokenArgs,
            applyDuring: (GenerateTokenArgs) -> GenerateTokenArgs,
        ): IdkResult<StringResult, IdkError> {
            val applied = applyDuring(args)
            val bytes = ByteArray(applied.lengthBytes) { fillByte }
            return Ok(StringResult(bytes.encodeTo(applied.encoding)))
        }
    }

    private class FixedByteNextBytesCommand(
        private val fillByte: Byte
    ) : CommandAdapter<NextBytesArgs, ByteArrayResult, IdkError>(id = NextBytesCommand.COMMAND_ID),
        NextBytesCommand {
        override val id: String get() = NextBytesCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is NextBytesArgs

        override suspend fun doExecute(
            args: NextBytesArgs,
            applyDuring: (NextBytesArgs) -> NextBytesArgs,
        ): IdkResult<ByteArrayResult, IdkError> {
            val applied = applyDuring(args)
            return Ok(ByteArrayResult(ByteArray(applied.length) { fillByte }))
        }
    }

    /** Minimal facade for unit-testing convenience methods without DI. */
    private class TestSecureRandom(
        override val commands: SecureRandom.Commands,
    ) : SecureRandom {
        override suspend fun generateToken(args: GenerateTokenArgs): IdkResult<StringResult, IdkError> = commands.generateToken.execute(args)

        override suspend fun nextBytes(args: NextBytesArgs): IdkResult<ByteArrayResult, IdkError> = commands.nextBytes.execute(args)
    }

    private fun testServiceFilledWith(fillByte: Byte): SecureRandom {
        val generate = FixedByteGenerateTokenCommand(fillByte)
        val bytes = FixedByteNextBytesCommand(fillByte)
        return TestSecureRandom(
            object : SecureRandom.Commands {
                override val generateToken: GenerateTokenCommand = generate
                override val nextBytes: NextBytesCommand = bytes
            },
        )
    }

    @Test
    fun serviceIdIsCoreRandom() {
        assertEquals("core.random", testServiceFilledWith(0x00).serviceId)
    }

    @Test
    fun newTokenDefaultLengthIs32Bytes() =
        runTest {
            val token = testServiceFilledWith(0x2A).newToken()
            val decoded = token.decodeFromBase64Url()
            assertEquals(DEFAULT_TOKEN_BYTES, decoded.size)
            assertEquals(32, decoded.size)
        }

    @Test
    fun newTokenRequestedLengthIsHonoured() =
        runTest {
            val token = testServiceFilledWith(0x01).newToken(lengthBytes = 48)
            assertEquals(48, token.decodeFromBase64Url().size)
        }

    @Test
    fun newTokenUsesUnpaddedBase64UrlByDefault() =
        runTest {
            val token = testServiceFilledWith(0x00).newToken()
            assertFalse(token.contains('='), "token must not contain padding: $token")
            assertFalse(token.contains('+'), "token must not contain standard-base64 chars: $token")
            assertFalse(token.contains('/'), "token must not contain standard-base64 chars: $token")
        }

    @Test
    fun newTokenRespectsExplicitEncoding() =
        runTest {
            val hexToken = testServiceFilledWith(0xAB.toByte()).newToken(lengthBytes = 4, encoding = Encoding.HEX)
            assertEquals("abababab", hexToken)
        }

    @Test
    fun newTokenZeroLengthReturnsEmptyString() =
        runTest {
            assertEquals("", testServiceFilledWith(0x00).newToken(lengthBytes = 0))
        }

    @Test
    fun randomBytesReturnsRequestedLength() =
        runTest {
            val bytes = testServiceFilledWith(0x7F).randomBytes(64)
            assertEquals(64, bytes.size)
            assertTrue(bytes.all { it == 0x7F.toByte() })
        }

    @Test
    fun generateTokenReturnsIdkResultOk() =
        runTest {
            val result = testServiceFilledWith(0x00).generateToken(GenerateTokenArgs(lengthBytes = 8))
            assertTrue(result.isOk)
            assertEquals(
                8,
                result.value.value
                    .decodeFromBase64Url()
                    .size
            )
        }

    @Test
    fun nextBytesReturnsIdkResultOk() =
        runTest {
            val result = testServiceFilledWith(0x7F).nextBytes(NextBytesArgs(length = 16))
            assertTrue(result.isOk)
            assertEquals(16, result.value.bytes.size)
        }

    @Test
    fun commandsPropertyExposesUnderlyingCommands() =
        runTest {
            val service = testServiceFilledWith(0x00)
            val direct = service.commands.generateToken.execute(GenerateTokenArgs(lengthBytes = 4))
            assertTrue(direct.isOk)
            assertEquals(GenerateTokenCommand.COMMAND_ID, service.commands.generateToken.id)
            assertEquals(NextBytesCommand.COMMAND_ID, service.commands.nextBytes.id)
        }
}
