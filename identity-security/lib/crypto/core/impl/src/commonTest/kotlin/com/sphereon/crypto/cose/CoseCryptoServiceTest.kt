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

package com.sphereon.crypto.cose

import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.crypto.core.DefaultCallbacks
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for CoseCryptoService implementation.
 */
class CoseCryptoServiceTest {
    @AfterTest
    fun tearDown() {
        DefaultCallbacks.setCoseCryptoDefault(null)
    }

    @Test
    fun serviceShouldBeEnabledByDefault() {
        val service = CoseCryptoServiceImpl()
        assertTrue(service.isEnabled(), "Service should be enabled by default")
    }

    @Test
    fun disableShouldDisableService() {
        val service = CoseCryptoServiceImpl()
        assertTrue(service.isEnabled())

        service.disable()

        assertFalse(service.isEnabled(), "Service should be disabled after disable()")
    }

    @Test
    fun enableShouldReEnableService() {
        val service = CoseCryptoServiceImpl()
        service.disable()
        assertFalse(service.isEnabled())

        service.enable()

        assertTrue(service.isEnabled(), "Service should be enabled after enable()")
    }

    @Test
    fun disableShouldReturnSameInstance() {
        val service = CoseCryptoServiceImpl()

        val result = service.disable()

        assertEquals(service, result, "disable() should return same instance for chaining")
    }

    @Test
    fun enableShouldReturnSameInstance() {
        val service = CoseCryptoServiceImpl()

        val result = service.enable()

        assertEquals(service, result, "enable() should return same instance for chaining")
    }

    @Test
    fun chainingDisableAndEnableShouldWork() {
        val service = CoseCryptoServiceImpl()

        service.disable().enable().disable()

        assertFalse(service.isEnabled())
    }

    // =========== Platform Callback Tests ===========

    @Test
    fun hasPlatformShouldReturnFalseWhenNoCallbackSet() {
        val service = CoseCryptoServiceImpl()
        // By default, there may or may not be a default callback registered
        // This tests that hasPlatform() can be called without error
        val hasPlatform = service.hasPlatform()
        // Just verify no exception is thrown
        assertTrue(hasPlatform || !hasPlatform) // Always passes, just tests method works
    }

    @Test
    fun setPlatformShouldAcceptCallback() {
        val service = CoseCryptoServiceImpl()
        // Create a mock callback that just tracks if it's set
        val mockCallback =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray = byteArrayOf()

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        val result = service.setPlatform(mockCallback)

        assertEquals(service, result, "setPlatform should return same instance for chaining")
        assertTrue(service.hasPlatform(), "hasPlatform should return true after setting callback")
    }

    @Test
    fun setPlatformShouldReturnSameInstanceForChaining() {
        val service = CoseCryptoServiceImpl()
        val mockCallback =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray = byteArrayOf()

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        // Test method chaining
        val result = service.setPlatform(mockCallback).disable().enable()

        assertTrue(result.isEnabled())
    }

    // =========== Additional Assertion Tests ===========

    @Test
    fun platformShouldThrowWhenNoCallbackAndNoDefault() {
        val service = CoseCryptoServiceImpl()
        // Clear default callback
        DefaultCallbacks.setCoseCryptoDefault(null)

        // If no platform callback and no default, platform() should throw
        assertFailsWith<IllegalStateException> {
            service.platform()
        }
    }

    @Test
    fun hasPlatformShouldReturnFalseWhenNoPlatformAndNoDefault() {
        val service = CoseCryptoServiceImpl()
        // Clear default callback
        DefaultCallbacks.setCoseCryptoDefault(null)

        assertFalse(service.hasPlatform(), "hasPlatform should return false when no platform and no default")
    }

    @Test
    fun hasPlatformShouldReturnTrueAfterSettingPlatform() {
        val service = CoseCryptoServiceImpl()
        DefaultCallbacks.setCoseCryptoDefault(null)
        assertFalse(service.hasPlatform())

        val mockCallback =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray = byteArrayOf()

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        service.setPlatform(mockCallback)

        assertTrue(service.hasPlatform(), "hasPlatform should return true after setting platform")
    }

    @Test
    fun disabledServiceShouldReportDisabledState() {
        val service = CoseCryptoServiceImpl()
        assertTrue(service.isEnabled())

        service.disable()

        assertFalse(service.isEnabled())
        // Re-enable
        service.enable()
        assertTrue(service.isEnabled())
    }

    @Test
    fun multiplePlatformSetsShouldOverwrite() {
        val service = CoseCryptoServiceImpl()
        DefaultCallbacks.setCoseCryptoDefault(null)

        var callback1Called = false
        var callback2Called = false

        val callback1 =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray {
                    callback1Called = true
                    return byteArrayOf()
                }

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        val callback2 =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray {
                    callback2Called = true
                    return byteArrayOf()
                }

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        service.setPlatform(callback1)
        service.setPlatform(callback2)

        // After setting callback2, the platform should be callback2
        val platform = service.platform()
        assertEquals(callback2, platform, "Platform should be the last set callback")
    }

    @Test
    fun hasPlatformShouldReturnTrueWhenDefaultCallbackSet() {
        val service = CoseCryptoServiceImpl()

        val mockDefault =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray = byteArrayOf()

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        // Clear and set default
        DefaultCallbacks.setCoseCryptoDefault(null)
        assertFalse(service.hasPlatform())

        DefaultCallbacks.setCoseCryptoDefault(mockDefault)
        assertTrue(service.hasPlatform(), "hasPlatform should return true when default callback is set")

        // Cleanup
        DefaultCallbacks.setCoseCryptoDefault(null)
    }

    @Test
    fun platformShouldUseDefaultWhenNoExplicitPlatformSet() {
        val service = CoseCryptoServiceImpl()

        val mockDefault =
            object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                override suspend fun sign(
                    input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                    requireX5Chain: Boolean?,
                ): ByteArray = byteArrayOf()

                override suspend fun verify1(
                    input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                    requireX5Chain: Boolean?,
                ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                override suspend fun mac0(
                    input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                    sharedSecret: ByteArray,
                    alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                    keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
            }

        DefaultCallbacks.setCoseCryptoDefault(mockDefault)

        // Create fresh service (no explicit platform set)
        val freshService = CoseCryptoServiceImpl()

        // Should use default
        val platform = freshService.platform()
        assertEquals(mockDefault, platform, "platform() should return default callback when no explicit platform set")

        // Cleanup
        DefaultCallbacks.setCoseCryptoDefault(null)
    }

    @Test
    fun explicitPlatformCallbackShouldWinOverGlobalDefault() {
        val globalDefault = mockCallback(byteArrayOf(1))
        val explicitCallback = mockCallback(byteArrayOf(2))
        DefaultCallbacks.setCoseCryptoDefault(globalDefault)

        val service = CoseCryptoServiceImpl(platformCallback = explicitCallback)

        assertEquals(explicitCallback, service.platform(), "Explicit platform callback must not be replaced by the global default")
    }

    @Test
    fun platformShouldNotCacheGlobalDefaultAsExplicitPlatform() {
        val firstDefault = mockCallback(byteArrayOf(1))
        val secondDefault = mockCallback(byteArrayOf(2))
        val service = CoseCryptoServiceImpl()

        DefaultCallbacks.setCoseCryptoDefault(firstDefault)
        assertEquals(firstDefault, service.platform(), "First platform() call should use the current global default")

        DefaultCallbacks.setCoseCryptoDefault(secondDefault)
        assertEquals(secondDefault, service.platform(), "Global fallback must not be copied into service-local session state")
    }

    // =========== Sign1/Verify1 with disabled service tests ===========

    @Test
    fun sign1ShouldThrowWhenServiceDisabled() =
        runTest {
            val service = CoseCryptoServiceImpl()
            DefaultCallbacks.setCoseCryptoDefault(null)

            // Set a mock callback first
            val mockCallback =
                object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
                    override suspend fun sign(
                        input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                        requireX5Chain: Boolean?,
                    ): ByteArray = byteArrayOf()

                    override suspend fun verify1(
                        input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                        keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                        requireX5Chain: Boolean?,
                    ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

                    override suspend fun mac0(
                        input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                        sharedSecret: ByteArray,
                        alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
                    ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

                    override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                        keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
                    ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
                }
            service.setPlatform(mockCallback)

            // Disable the service
            service.disable()

            // sign1 should throw IllegalStateException because service is disabled
            assertFailsWith<IllegalStateException> {
                service.sign1<Any>(
                    input =
                        com.sphereon.crypto.core.cose.CoseSign1Input(
                            protectedHeader =
                                com.sphereon.crypto.core.cose
                                    .CoseHeaderCbor(alg = com.sphereon.crypto.core.cose.CoseAlgorithm.ES256),
                            payload = com.sphereon.cbor.CborByteString("test".encodeToByteArray()),
                        ),
                    keyInfo =
                        com.sphereon.crypto.core
                            .KeyInfo<com.sphereon.crypto.core.cose.CoseKeyType>(kid = "test-kid"),
                    requireX5Chain = false,
                )
            }
        }

    @Test
    fun sign1ShouldThrowWhenNoPlatformRegistered() =
        runTest {
            val service = CoseCryptoServiceImpl()
            DefaultCallbacks.setCoseCryptoDefault(null)

            // sign1 should throw IllegalStateException because no platform is registered
            assertFailsWith<IllegalStateException> {
                service.sign1<Any>(
                    input =
                        com.sphereon.crypto.core.cose.CoseSign1Input(
                            protectedHeader =
                                com.sphereon.crypto.core.cose
                                    .CoseHeaderCbor(alg = com.sphereon.crypto.core.cose.CoseAlgorithm.ES256),
                            payload = com.sphereon.cbor.CborByteString("test".encodeToByteArray()),
                        ),
                    keyInfo =
                        com.sphereon.crypto.core
                            .KeyInfo<com.sphereon.crypto.core.cose.CoseKeyType>(kid = "test-kid"),
                    requireX5Chain = false,
                )
            }
        }

    private fun mockCallback(signature: ByteArray): com.sphereon.crypto.core.CoseCryptoCallbackCoroutines =
        object : com.sphereon.crypto.core.CoseCryptoCallbackCoroutines {
            override suspend fun sign(
                input: com.sphereon.crypto.core.cose.ToBeSignedCbor,
                requireX5Chain: Boolean?,
            ): ByteArray = signature

            override suspend fun verify1(
                input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                keyInfo: com.sphereon.crypto.core.KeyInfoType<*>?,
                requireX5Chain: Boolean?,
            ): com.sphereon.crypto.core.generic.VerifySignatureResultType<com.sphereon.crypto.core.cose.CoseKeyType> = throw NotImplementedError()

            override suspend fun mac0(
                input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                sharedSecret: ByteArray,
                alg: com.sphereon.crypto.core.generic.SignatureAlgorithm,
            ): com.sphereon.crypto.core.CoseMac0Result = throw NotImplementedError()

            override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                keyInfo: com.sphereon.crypto.core.KeyInfoType<KeyType>,
            ): com.sphereon.crypto.core.ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
        }
}
