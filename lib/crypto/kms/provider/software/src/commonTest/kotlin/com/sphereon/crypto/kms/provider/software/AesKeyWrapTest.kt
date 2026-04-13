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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Isolated unit tests for AES Key Wrap (RFC 3394) implementation.
 *
 * Tests cover:
 * - Basic wrap/unwrap roundtrip for A128KW, A192KW, A256KW
 * - RFC 3394 test vectors
 * - Error handling for invalid key sizes
 * - Integrity check verification
 */
class AesKeyWrapTest {
    private lateinit var softwareKMSProvider: SoftwareKmsProvider

    val ctx = SoftwareKmsTestContext("aes-kw-test", this)

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "aes-kw-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        softwareKMSProvider = ctx.softwareKmsProviderFactory.create(config, ctx.session.sessionExecution)
    }

    private fun createSymmetricKeyInfo(keyBytes: ByteArray): KeyInfo<Jwk> {
        val jwk =
            Jwk(
                kty = JwaKeyType.oct,
                k = keyBytes.encodeTo(Encoding.BASE64URL),
            )
        return KeyInfo(key = jwk, keyVisibility = KeyVisibility.PRIVATE)
    }

    // ========================================================================
    // Basic Wrap/Unwrap Tests
    // ========================================================================

    @Test
    fun testA128KW_WrapUnwrap_16ByteKey() =
        runTest {
            val kek = CryptographyRandom.nextBytes(16)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(16)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyToWrap,
                    algorithm = KeyWrapAlgorithm.A128KW,
                )

            assertEquals(keyToWrap.size + 8, wrapped.size)

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A128KW,
                )

            assertContentEquals(keyToWrap, unwrapped)
        }

    @Test
    fun testA192KW_WrapUnwrap_24ByteKey() =
        runTest {
            val kek = CryptographyRandom.nextBytes(24)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(24)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyToWrap,
                    algorithm = KeyWrapAlgorithm.A192KW,
                )

            assertEquals(keyToWrap.size + 8, wrapped.size)

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A192KW,
                )

            assertContentEquals(keyToWrap, unwrapped)
        }

    @Test
    fun testA256KW_WrapUnwrap_32ByteKey() =
        runTest {
            val kek = CryptographyRandom.nextBytes(32)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(32)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyToWrap,
                    algorithm = KeyWrapAlgorithm.A256KW,
                )

            assertEquals(keyToWrap.size + 8, wrapped.size)

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A256KW,
                )

            assertContentEquals(keyToWrap, unwrapped)
        }

    @Test
    fun testA256KW_WrapUnwrap_VariousSizes() =
        runTest {
            val kek = CryptographyRandom.nextBytes(32)
            val kekInfo = createSymmetricKeyInfo(kek)
            val sizes = listOf(16, 24, 32, 40, 48, 64, 128)

            for (size in sizes) {
                val keyToWrap = CryptographyRandom.nextBytes(size)

                val wrapped =
                    softwareKMSProvider.wrapKey(
                        wrappingKeyInfo = kekInfo,
                        keyToWrap = keyToWrap,
                        algorithm = KeyWrapAlgorithm.A256KW,
                    )

                assertEquals(keyToWrap.size + 8, wrapped.size, "Wrapped key size for $size byte input")

                val unwrapped =
                    softwareKMSProvider.unwrapKey(
                        unwrappingKeyInfo = kekInfo,
                        wrappedKey = wrapped,
                        algorithm = KeyWrapAlgorithm.A256KW,
                    )

                assertContentEquals(keyToWrap, unwrapped, "Roundtrip for $size byte key")
            }
        }

    // ========================================================================
    // RFC 3394 Test Vectors
    // ========================================================================

    @Test
    fun testRfc3394_TestVector_128BitKek_128BitData() =
        runTest {
            val kek = hexToBytes("000102030405060708090A0B0C0D0E0F")
            val keyData = hexToBytes("00112233445566778899AABBCCDDEEFF")
            val expectedCiphertext = hexToBytes("1FA68B0A8112B447AEF34BD8FB5A7B829D3E862371D2CFE5")

            val kekInfo = createSymmetricKeyInfo(kek)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyData,
                    algorithm = KeyWrapAlgorithm.A128KW,
                )

            assertContentEquals(expectedCiphertext, wrapped, "RFC 3394 test vector: wrapped key mismatch")

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A128KW,
                )

            assertContentEquals(keyData, unwrapped, "RFC 3394 test vector: unwrapped key mismatch")
        }

    @Test
    fun testRfc3394_TestVector_192BitKek_128BitData() =
        runTest {
            val kek = hexToBytes("000102030405060708090A0B0C0D0E0F1011121314151617")
            val keyData = hexToBytes("00112233445566778899AABBCCDDEEFF")
            val expectedCiphertext = hexToBytes("96778B25AE6CA435F92B5B97C050AED2468AB8A17AD84E5D")

            val kekInfo = createSymmetricKeyInfo(kek)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyData,
                    algorithm = KeyWrapAlgorithm.A192KW,
                )

            assertContentEquals(expectedCiphertext, wrapped, "RFC 3394 test vector: wrapped key mismatch")

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A192KW,
                )

            assertContentEquals(keyData, unwrapped, "RFC 3394 test vector: unwrapped key mismatch")
        }

    @Test
    fun testRfc3394_TestVector_256BitKek_128BitData() =
        runTest {
            val kek = hexToBytes("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F")
            val keyData = hexToBytes("00112233445566778899AABBCCDDEEFF")
            val expectedCiphertext = hexToBytes("64E8C3F9CE0F5BA263E9777905818A2A93C8191E7D6E8AE7")

            val kekInfo = createSymmetricKeyInfo(kek)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyData,
                    algorithm = KeyWrapAlgorithm.A256KW,
                )

            assertContentEquals(expectedCiphertext, wrapped, "RFC 3394 test vector: wrapped key mismatch")

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A256KW,
                )

            assertContentEquals(keyData, unwrapped, "RFC 3394 test vector: unwrapped key mismatch")
        }

    @Test
    fun testRfc3394_TestVector_256BitKek_256BitData() =
        runTest {
            val kek = hexToBytes("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F")
            val keyData = hexToBytes("00112233445566778899AABBCCDDEEFF000102030405060708090A0B0C0D0E0F")
            val expectedCiphertext = hexToBytes("28C9F404C4B810F4CBCCB35CFB87F8263F5786E2D80ED326CBC7F0E71A99F43BFB988B9B7A02DD21")

            val kekInfo = createSymmetricKeyInfo(kek)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyData,
                    algorithm = KeyWrapAlgorithm.A256KW,
                )

            assertContentEquals(expectedCiphertext, wrapped, "RFC 3394 test vector: wrapped key mismatch")

            val unwrapped =
                softwareKMSProvider.unwrapKey(
                    unwrappingKeyInfo = kekInfo,
                    wrappedKey = wrapped,
                    algorithm = KeyWrapAlgorithm.A256KW,
                )

            assertContentEquals(keyData, unwrapped, "RFC 3394 test vector: unwrapped key mismatch")
        }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Test
    fun testWrap_InvalidKekSize_TooSmall() =
        runTest {
            val kek = CryptographyRandom.nextBytes(8)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(16)

            val exception =
                assertFailsWith<IllegalArgumentException> {
                    softwareKMSProvider.wrapKey(
                        wrappingKeyInfo = kekInfo,
                        keyToWrap = keyToWrap,
                        algorithm = KeyWrapAlgorithm.A128KW,
                    )
                }

            assertTrue(exception.message?.contains("KEK size") == true || exception.message?.contains("size") == true)
        }

    @Test
    fun testWrap_InvalidKekSize_WrongAlgorithm() =
        runTest {
            val kek = CryptographyRandom.nextBytes(16)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(16)

            val exception =
                assertFailsWith<IllegalArgumentException> {
                    softwareKMSProvider.wrapKey(
                        wrappingKeyInfo = kekInfo,
                        keyToWrap = keyToWrap,
                        algorithm = KeyWrapAlgorithm.A256KW,
                    )
                }

            assertTrue(exception.message?.contains("size") == true)
        }

    @Test
    fun testWrap_KeyTooSmall() =
        runTest {
            val kek = CryptographyRandom.nextBytes(16)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(8)

            val exception =
                assertFailsWith<IllegalArgumentException> {
                    softwareKMSProvider.wrapKey(
                        wrappingKeyInfo = kekInfo,
                        keyToWrap = keyToWrap,
                        algorithm = KeyWrapAlgorithm.A128KW,
                    )
                }

            assertTrue(exception.message?.contains("16 bytes") == true)
        }

    @Test
    fun testWrap_KeyNotMultipleOf8() =
        runTest {
            val kek = CryptographyRandom.nextBytes(16)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(17)

            val exception =
                assertFailsWith<IllegalArgumentException> {
                    softwareKMSProvider.wrapKey(
                        wrappingKeyInfo = kekInfo,
                        keyToWrap = keyToWrap,
                        algorithm = KeyWrapAlgorithm.A128KW,
                    )
                }

            assertTrue(exception.message?.contains("multiple of 8") == true)
        }

    @Test
    fun testUnwrap_IntegrityCheckFails_WrongKey() =
        runTest {
            val kek1 = CryptographyRandom.nextBytes(16)
            val kekInfo1 = createSymmetricKeyInfo(kek1)
            val keyToWrap = CryptographyRandom.nextBytes(16)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo1,
                    keyToWrap = keyToWrap,
                    algorithm = KeyWrapAlgorithm.A128KW,
                )

            val kek2 = CryptographyRandom.nextBytes(16)
            val kekInfo2 = createSymmetricKeyInfo(kek2)

            val exception =
                assertFailsWith<IllegalStateException> {
                    softwareKMSProvider.unwrapKey(
                        unwrappingKeyInfo = kekInfo2,
                        wrappedKey = wrapped,
                        algorithm = KeyWrapAlgorithm.A128KW,
                    )
                }

            assertTrue(exception.message?.contains("integrity check failed") == true)
        }

    @Test
    fun testUnwrap_IntegrityCheckFails_CorruptedData() =
        runTest {
            val kek = CryptographyRandom.nextBytes(16)
            val kekInfo = createSymmetricKeyInfo(kek)
            val keyToWrap = CryptographyRandom.nextBytes(16)

            val wrapped =
                softwareKMSProvider.wrapKey(
                    wrappingKeyInfo = kekInfo,
                    keyToWrap = keyToWrap,
                    algorithm = KeyWrapAlgorithm.A128KW,
                )

            val corrupted = wrapped.copyOf()
            corrupted[10] = (corrupted[10].toInt() xor 0xFF).toByte()

            val exception =
                assertFailsWith<IllegalStateException> {
                    softwareKMSProvider.unwrapKey(
                        unwrappingKeyInfo = kekInfo,
                        wrappedKey = corrupted,
                        algorithm = KeyWrapAlgorithm.A128KW,
                    )
                }

            assertTrue(exception.message?.contains("integrity check failed") == true)
        }

    @Test
    fun testUnwrap_WrappedKeyTooSmall() =
        runTest {
            val kek = CryptographyRandom.nextBytes(16)
            val kekInfo = createSymmetricKeyInfo(kek)
            val wrappedKey = CryptographyRandom.nextBytes(16)

            val exception =
                assertFailsWith<IllegalStateException> {
                    softwareKMSProvider.unwrapKey(
                        unwrappingKeyInfo = kekInfo,
                        wrappedKey = wrappedKey,
                        algorithm = KeyWrapAlgorithm.A128KW,
                    )
                }

            assertTrue(exception.message?.contains("24 bytes") == true)
        }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((hexDigit(hex[i]) shl 4) + hexDigit(hex[i + 1])).toByte()
            i += 2
        }
        return data
    }

    private fun hexDigit(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'F' -> c - 'A' + 10
            in 'a'..'f' -> c - 'a' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $c")
        }
}
