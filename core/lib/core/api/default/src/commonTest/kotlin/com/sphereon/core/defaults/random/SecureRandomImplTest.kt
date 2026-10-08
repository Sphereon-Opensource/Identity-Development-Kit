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

package com.sphereon.core.defaults.random

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.random.GenerateTokenArgs
import com.sphereon.core.api.random.NextBytesArgs
import com.sphereon.core.api.random.SecureRandom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavior tests for the real CSPRNG-backed [SecureRandom] wiring.
 *
 * No statistical entropy testing — we assert length, alphabet, and uniqueness-across-draws.
 */
class SecureRandomImplTest {
    private val secureRandom: SecureRandom =
        SecureRandomImpl(
            generateTokenCommand = GenerateTokenCommandImpl(),
            nextBytesCommand = NextBytesCommandImpl(),
        )

    @Test
    fun randomBytesReturnsRequestedLength() =
        runTest {
            assertEquals(0, secureRandom.randomBytes(0).size)
            assertEquals(1, secureRandom.randomBytes(1).size)
            assertEquals(32, secureRandom.randomBytes(32).size)
            assertEquals(1024, secureRandom.randomBytes(1024).size)
        }

    @Test
    fun newTokenDefaultDecodesTo32Bytes() =
        runTest {
            val token = secureRandom.newToken()
            assertEquals(32, token.decodeFromBase64Url().size)
        }

    @Test
    fun newTokenDefaultUsesUrlSafeUnpaddedAlphabet() =
        runTest {
            repeat(32) {
                val token = secureRandom.newToken()
                assertFalse(token.contains('='), "token contained padding: $token")
                assertFalse(token.contains('+'), "token contained non-url-safe char '+': $token")
                assertFalse(token.contains('/'), "token contained non-url-safe char '/': $token")
                assertTrue(
                    token.all { it.isLetterOrDigit() || it == '-' || it == '_' },
                    "token contained invalid char: $token",
                )
            }
        }

    @Test
    fun newTokenHexEncodingReturnsLowercaseHex() =
        runTest {
            repeat(32) {
                val token = secureRandom.newToken(lengthBytes = 16, encoding = Encoding.HEX)
                assertEquals(32, token.length, "16 bytes should encode to 32 hex chars")
                assertTrue(
                    token.all { it in '0'..'9' || it in 'a'..'f' },
                    "hex token contained invalid char: $token",
                )
            }
        }

    @Test
    fun newTokenProducesDistinctValuesAcrossManyDraws() =
        runTest {
            val draws = 1_000
            val tokens = HashSet<String>(draws)
            repeat(draws) { tokens += secureRandom.newToken() }
            assertEquals(draws, tokens.size, "expected $draws distinct tokens, got ${tokens.size}")
        }

    @Test
    fun randomBytesProducesDistinctArraysAcrossManyDraws() =
        runTest {
            val draws = 1_000
            val seen = HashSet<String>(draws)
            repeat(draws) { seen += secureRandom.randomBytes(32).joinToString(",") { it.toString() } }
            assertEquals(draws, seen.size)
        }

    @Test
    fun generateTokenCommandReturnsOkForValidInput() =
        runTest {
            val result = secureRandom.generateToken(GenerateTokenArgs(lengthBytes = 16, encoding = Encoding.BASE64URL))
            assertTrue(result.isOk)
            assertEquals(
                16,
                result.value.value
                    .decodeFromBase64Url()
                    .size
            )
        }

    @Test
    fun nextBytesCommandReturnsOkForValidInput() =
        runTest {
            val result = secureRandom.nextBytes(NextBytesArgs(length = 16))
            assertTrue(result.isOk)
            assertEquals(16, result.value.bytes.size)
        }

    @Test
    fun serviceIdIsCoreRandom() {
        assertEquals("core.random", secureRandom.serviceId)
    }
}
