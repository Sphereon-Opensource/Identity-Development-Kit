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

package com.sphereon.crypto.cose

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.CoseMac0Result
import com.sphereon.crypto.core.testutil.createCryptoTestAppComponent
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseMac0InputCbor
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.core.defaultCreateMac0
import com.sphereon.crypto.core.defaultCreateMac0UsingKeys
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for COSE MAC0 functionality.
 */
class CoseMac0Test {

    private val testPayload = "Hello, MAC0!".encodeToByteArray()
    private val sharedSecret = ByteArray(32) { it.toByte() } // 32-byte shared secret

    // =========== defaultCreateMac0 Tests ===========

    @Test
    fun defaultCreateMac0WithHmacSha256ShouldSucceed() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            alg = SignatureAlgorithm.HMAC_SHA256,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        assertNotNull(result.coseMac0)
        assertNotNull(result.coseMac0.tag)
        assertTrue(result.coseMac0.tag.value.isNotEmpty(), "MAC tag should not be empty")
        assertEquals(CoseAlgorithm.HMAC256_256, result.coseMac0.protectedHeader.alg)
    }

    @Test
    fun defaultCreateMac0WithHmacSha384ShouldSucceed() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC384_384),
            payload = testPayload
        )
        val secret384 = ByteArray(48) { it.toByte() } // HMAC-SHA384 needs larger key

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = secret384,
            alg = SignatureAlgorithm.HMAC_SHA384,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        assertNotNull(result.coseMac0)
        assertNotNull(result.coseMac0.tag)
        assertTrue(result.coseMac0.tag.value.isNotEmpty(), "MAC tag should not be empty")
    }

    @Test
    fun defaultCreateMac0WithHmacSha512ShouldSucceed() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC512_512),
            payload = testPayload
        )
        val secret512 = ByteArray(64) { it.toByte() } // HMAC-SHA512 needs larger key

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = secret512,
            alg = SignatureAlgorithm.HMAC_SHA512,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        assertNotNull(result.coseMac0)
        assertNotNull(result.coseMac0.tag)
        assertTrue(result.coseMac0.tag.value.isNotEmpty(), "MAC tag should not be empty")
    }

    @Test
    fun defaultCreateMac0ShouldUseAlgorithmFromHeaderWhenNotSpecified() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        // Don't specify alg - should use default HMAC_SHA256
        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        assertEquals(CoseAlgorithm.HMAC256_256, result.coseMac0.protectedHeader.alg)
    }

    @Test
    fun defaultCreateMac0ShouldSetAlgorithmInHeader() = runTest {
        // Input has no algorithm in header
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(),
            payload = testPayload
        )

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            alg = SignatureAlgorithm.HMAC_SHA256,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        // Algorithm should be set in the output
        assertNotNull(result.coseMac0.protectedHeader.alg)
    }

    @Test
    fun defaultCreateMac0ShouldPreservePayload() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result.coseMac0.payload)
        assertTrue(result.coseMac0.payload!!.value.contentEquals(testPayload))
    }

    @Test
    fun defaultCreateMac0ShouldPreserveUnprotectedHeader() = runTest {
        val unprotectedHeader = CoseHeaderCbor(
            contentType = com.sphereon.cbor.CborString("application/json")
        )
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            unprotectedHeader = unprotectedHeader,
            payload = testPayload
        )

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result.coseMac0.unprotectedHeader)
    }

    @Test
    fun defaultCreateMac0ShouldFailWithNullProvider() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        assertFailsWith<IllegalArgumentException> {
            defaultCreateMac0(
                input = input,
                sharedSecret = sharedSecret,
                provider = null
            )
        }
    }

    @Test
    fun defaultCreateMac0ShouldProduceDeterministicOutput() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        val result1 = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        val result2 = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        // Same input and secret should produce same MAC
        assertTrue(result1.coseMac0.tag.value.contentEquals(result2.coseMac0.tag.value))
    }

    @Test
    fun defaultCreateMac0ShouldProduceDifferentTagsForDifferentSecrets() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        val result1 = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        val differentSecret = ByteArray(32) { (it + 1).toByte() }
        val result2 = defaultCreateMac0(
            input = input,
            sharedSecret = differentSecret,
            provider = CryptographyProvider.Default
        )

        // Different secrets should produce different MACs
        assertTrue(!result1.coseMac0.tag.value.contentEquals(result2.coseMac0.tag.value))
    }

    @Test
    fun defaultCreateMac0ShouldProduceDifferentTagsForDifferentPayloads() = runTest {
        val input1 = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = "Payload 1".encodeToByteArray()
        )

        val input2 = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = "Payload 2".encodeToByteArray()
        )

        val result1 = defaultCreateMac0(
            input = input1,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        val result2 = defaultCreateMac0(
            input = input2,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        // Different payloads should produce different MACs
        assertTrue(!result1.coseMac0.tag.value.contentEquals(result2.coseMac0.tag.value))
    }

    @Test
    fun defaultCreateMac0ShouldWorkWithExternalAad() = runTest {
        val externalAad = "Additional authenticated data".encodeToByteArray()
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload,
            externalAad = externalAad
        )

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        assertNotNull(result.coseMac0.tag)
    }

    @Test
    fun defaultCreateMac0WithDifferentAadShouldProduceDifferentTags() = runTest {
        val input1 = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload,
            externalAad = "AAD 1".encodeToByteArray()
        )

        val input2 = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload,
            externalAad = "AAD 2".encodeToByteArray()
        )

        val result1 = defaultCreateMac0(
            input = input1,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        val result2 = defaultCreateMac0(
            input = input2,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        // Different AAD should produce different MACs
        assertTrue(!result1.coseMac0.tag.value.contentEquals(result2.coseMac0.tag.value))
    }

    @Test
    fun defaultCreateMac0WithDetachedPayloadShouldSucceed() = runTest {
        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            detachedPayload = testPayload
        )

        val result = defaultCreateMac0(
            input = input,
            sharedSecret = sharedSecret,
            provider = CryptographyProvider.Default
        )

        assertNotNull(result)
        assertNotNull(result.coseMac0.tag)
    }

    // =========== defaultCreateMac0UsingKeys Tests (ECDH) ===========

    @Test
    fun defaultCreateMac0UsingKeysShouldSucceedWithEcdhKeys() = runTest {
        // Setup DI components for key generation
        val app = createCryptoTestAppComponent(this@CoseMac0Test)
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("mac0-ecdh-test")

        val config = SoftwareKmsProviderConfig(
            id = "mac0-ecdh-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        val keyManagerService: KeyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        // Generate two EC key pairs for ECDH
        val selfKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val otherKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

        // Get key infos - need private key for self, public key for other
        val selfKeyInfo = selfKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val otherKeyInfo = otherKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        // Convert to resolved COSE key infos
        val selfPrivateKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
            CoseJoseKeyMappingService.toResolvedKeyInfo(selfKeyInfo, selfKeyInfo.key)
        )
        val otherPublicKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
            CoseJoseKeyMappingService.toResolvedKeyInfo(otherKeyInfo, otherKeyInfo.key)
        )

        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        // Create MAC using ECDH key agreement
        val result = defaultCreateMac0UsingKeys(
            provider = CryptographyProvider.Default,
            input = input,
            selfPrivateKey = selfPrivateKeyInfo,
            otherPublicKey = otherPublicKeyInfo,
            alg = SignatureAlgorithm.HMAC_SHA256,
            info = "EMacKey",
            salt = byteArrayOf(),
            macCallback = { provider, macInput, sharedSecret, alg ->
                defaultCreateMac0(
                    input = macInput,
                    sharedSecret = sharedSecret,
                    alg = alg,
                    provider = provider
                )
            }
        )

        assertNotNull(result)
        assertNotNull(result.coseMac0)
        assertNotNull(result.coseMac0.tag)
        assertTrue(result.coseMac0.tag.value.isNotEmpty(), "MAC tag should not be empty")
    }

    @Test
    fun defaultCreateMac0UsingKeysShouldFailWithNullProvider() = runTest {
        // Setup DI components for key generation
        val app = createCryptoTestAppComponent(this@CoseMac0Test)
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("mac0-ecdh-null-provider")

        val config = SoftwareKmsProviderConfig(
            id = "mac0-ecdh-null-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        val keyManagerService: KeyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        val selfKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val otherKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)

        val selfKeyInfo = selfKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val otherKeyInfo = otherKeyPair.joseToManagedKeyInfo(KeyVisibility.PUBLIC)

        val selfPrivateKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
            CoseJoseKeyMappingService.toResolvedKeyInfo(selfKeyInfo, selfKeyInfo.key)
        )
        val otherPublicKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
            CoseJoseKeyMappingService.toResolvedKeyInfo(otherKeyInfo, otherKeyInfo.key)
        )

        val input = CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = testPayload
        )

        // Should fail because provider is null
        assertFailsWith<IllegalArgumentException> {
            defaultCreateMac0UsingKeys(
                provider = null,
                input = input,
                selfPrivateKey = selfPrivateKeyInfo,
                otherPublicKey = otherPublicKeyInfo,
                macCallback = { _, _, _, _ -> throw NotImplementedError() }
            )
        }
    }
}
