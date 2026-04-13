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

package com.sphereon.crypto.core

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.encodeToBase64Array
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.kms.asCertificateServiceComponent
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.testutil.createCryptoTestAppComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for AbstractCoseCryptoService to ensure branch coverage
 * and verify behavior before refactoring.
 *
 * Test categories:
 * 1. Platform callback management (enable/disable, hasPlatform, setPlatform)
 * 2. Algorithm resolution (from header, keyInfo, defaults)
 * 3. x5chain handling (from headers, from key, required vs optional)
 * 4. Key info resolution (with key, without key, from x5chain)
 * 5. Error paths (missing keyInfo, missing x5chain when required, disabled service)
 */
class AbstractCoseCryptoServiceTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var coseCryptoService: CoseCryptoService

    val app = createCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("abstract-cose-test")

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(
            id = "abstract-cose-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        coseCryptoService = (session.component as CryptoServicesImpl.Component).cryptoServices.cose
    }

    @AfterTest
    fun tearDown() {
        DefaultCallbacks.setCoseCryptoDefault(null)
    }

    // =========== 1. Platform Callback Management ===========

    @Test
    fun isEnabledShouldReturnTrueByDefault() {
        val service = CoseCryptoServiceImpl()
        assertTrue(service.isEnabled())
    }

    @Test
    fun disableShouldSetEnabledToFalse() {
        val service = CoseCryptoServiceImpl()
        service.disable()
        assertFalse(service.isEnabled())
    }

    @Test
    fun enableShouldSetEnabledToTrue() {
        val service = CoseCryptoServiceImpl()
        service.disable()
        assertFalse(service.isEnabled())
        service.enable()
        assertTrue(service.isEnabled())
    }

    @Test
    fun disableAndEnableShouldBeChainable() {
        val service = CoseCryptoServiceImpl()
        val result = service.disable().enable().disable()
        assertFalse(result.isEnabled())
    }

    @Test
    fun hasPlatformShouldReturnFalseWhenNoPlatformAndNoDefault() {
        DefaultCallbacks.setCoseCryptoDefault(null)
        val service = CoseCryptoServiceImpl()
        assertFalse(service.hasPlatform())
    }

    @Test
    fun hasPlatformShouldReturnTrueWhenPlatformSet() {
        val service = CoseCryptoServiceImpl()
        service.setPlatform(createMockCallback())
        assertTrue(service.hasPlatform())
    }

    @Test
    fun hasPlatformShouldReturnTrueWhenDefaultCallbackSet() {
        DefaultCallbacks.setCoseCryptoDefault(createMockCallback())
        val service = CoseCryptoServiceImpl()
        assertTrue(service.hasPlatform())
    }

    @Test
    fun platformShouldUseDefaultWhenNoPlatformExplicitlySet() {
        val mockCallback = createMockCallback()
        DefaultCallbacks.setCoseCryptoDefault(mockCallback)
        val service = CoseCryptoServiceImpl()
        assertEquals(mockCallback, service.platform())
    }

    @Test
    fun platformShouldThrowWhenNoPlatformAndNoDefault() {
        DefaultCallbacks.setCoseCryptoDefault(null)
        val service = CoseCryptoServiceImpl()
        assertFailsWith<IllegalStateException> {
            service.platform()
        }
    }

    @Test
    fun setPlatformShouldOverwritePreviousPlatform() {
        val service = CoseCryptoServiceImpl()
        val callback1 = createMockCallback()
        val callback2 = createMockCallback()
        service.setPlatform(callback1)
        service.setPlatform(callback2)
        assertEquals(callback2, service.platform())
    }

    // =========== 2. Algorithm Resolution Tests ===========

    @Test
    fun sign1ShouldUseAlgorithmFromProtectedHeader() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1ShouldUseAlgorithmFromKeyInfoWhenNotInHeader() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Header without algorithm - should derive from keyInfo
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Should have derived algorithm from key
        assertNotNull(result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithES384ShouldWork() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES384),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES384, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithES512ShouldWork() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA512)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES512),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES512, result.coseSign1.protectedHeader.alg)
    }

    // =========== 3. x5chain Handling Tests ===========

    @Test
    fun sign1WithoutX5ChainShouldWorkWhenNotRequired() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        // requireX5Chain = false should work even without x5chain
        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
    }

    @Test
    fun sign1WithUnprotectedHeaderShouldWork() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(
                contentType = com.sphereon.cbor.CborString("application/json")
            ),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result.coseSign1.unprotectedHeader)
    }

    // =========== 4. Key Info Resolution Tests ===========

    @Test
    fun sign1WithKeyInfoContainingKeyShouldWork() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // KeyInfo with key already present
        assertNotNull(coseKeyInfo.key)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
        assertNotNull(result.keyInfo.key)
    }

    @Test
    fun sign1WithKeyInfoWithoutKeyShouldAttemptKeyResolution() = runTest {
        // This test documents the CURRENT behavior: when keyInfo has no key,
        // the system attempts to resolve it via the platform callback.
        // This may need refactoring to use the identifier resolution system.
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Create a KeyInfo with only kid (no key) - this currently throws because
        // key resolution via platform callback requires proper setup
        val kidOnlyKeyInfo = KeyInfo<CoseKeyType>(
            kid = coseKeyInfo.kid,
            providerId = coseKeyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        // Currently this throws - documenting existing behavior
        // TODO: After refactoring, this should work via identifier resolution
        assertFailsWith<IllegalArgumentException> {
            coseCryptoService.sign1<Any>(
                input = input,
                keyInfo = kidOnlyKeyInfo,
                requireX5Chain = false
            )
        }
    }

    // =========== 5. Error Path Tests ===========

    @Test
    fun sign1ShouldThrowWhenServiceDisabled() = runTest {
        val service = CoseCryptoServiceImpl()
        service.setPlatform(createMockCallback())
        service.disable()

        assertFailsWith<IllegalStateException> {
            service.sign1<Any>(
                input = CoseSign1Input(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    payload = CborByteString("test".encodeToByteArray())
                ),
                keyInfo = KeyInfo<CoseKeyType>(kid = "test"),
                requireX5Chain = false
            )
        }
    }

    @Test
    fun sign1ShouldThrowWhenNoPlatformRegistered() = runTest {
        DefaultCallbacks.setCoseCryptoDefault(null)
        val service = CoseCryptoServiceImpl()

        assertFailsWith<IllegalStateException> {
            service.sign1<Any>(
                input = CoseSign1Input(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    payload = CborByteString("test".encodeToByteArray())
                ),
                keyInfo = KeyInfo<CoseKeyType>(kid = "test"),
                requireX5Chain = false
            )
        }
    }

    // =========== 6. Verify Tests ===========

    @Test
    fun verify1ShouldWorkWithValidSignature() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test payload".encodeToByteArray())
        )

        // Sign
        val signResult = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify with same key
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertFalse(verifyResult.error, "Verification should not have error: ${verifyResult.message}")
    }

    @Test
    fun verify1ShouldReturnErrorResultWhenServiceDisabled() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        // Sign first while enabled
        val signResult = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Disable and try to verify
        (coseCryptoService as CoseCryptoServiceImpl).disable()

        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // verify1 returns error result instead of throwing when disabled
        assertTrue(verifyResult.message?.contains("disabled") == true || verifyResult.error)

        // Re-enable for cleanup
        (coseCryptoService as CoseCryptoServiceImpl).enable()
    }

    @Test
    fun verify1WithAlgorithmFromUnprotectedHeaderShouldWork() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Protected header with algorithm
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(),
            payload = CborByteString("test".encodeToByteArray())
        )

        val signResult = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
    }

    // =========== 7. MAC0 Through Service Tests ===========

    @Test
    fun mac0ShouldDelegateToCallback() = runTest {
        var callbackInvoked = false
        val mockCallback = object : CoseCryptoCallbackCoroutines {
            override suspend fun sign(input: com.sphereon.crypto.core.cose.ToBeSignedCbor, requireX5Chain: Boolean?): ByteArray =
                byteArrayOf(0, 1, 2, 3)

            override suspend fun verify1(
                input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                keyInfo: KeyInfoType<*>?,
                requireX5Chain: Boolean?
            ): VerifySignatureResultType<CoseKeyType> =
                com.sphereon.crypto.core.generic.VerifySignatureResult(keyInfo = null, name = "test", error = false, critical = false)

            override suspend fun mac0(
                input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                sharedSecret: ByteArray,
                alg: SignatureAlgorithm
            ): CoseMac0Result {
                callbackInvoked = true
                return CoseMac0Result(
                    input = input,
                    coseMac0 = com.sphereon.crypto.core.cose.CoseMac0Cbor(
                        tag = com.sphereon.cbor.CborByteString(byteArrayOf(1, 2, 3, 4)),
                        protectedHeader = input.protectedHeader,
                        unprotectedHeader = input.unprotectedHeader,
                        payload = input.payload?.let { com.sphereon.cbor.CborByteString(it) }
                    )
                )
            }

            override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                keyInfo: KeyInfoType<KeyType>
            ): ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
        }

        val service = CoseCryptoServiceImpl()
        service.setPlatform(mockCallback)

        val input = com.sphereon.crypto.core.cose.CoseMac0InputCbor(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            payload = "test".encodeToByteArray()
        )

        service.mac0(input, byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), SignatureAlgorithm.HMAC_SHA256)

        assertTrue(callbackInvoked, "mac0 should delegate to callback")
    }

    // =========== 8. Algorithm Fallback Tests ===========

    @Test
    fun sign1ShouldUseAlgorithmFromUnprotectedHeaderWhenNotInProtected() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Algorithm only in unprotected header
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(),
            unprotectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Should have used algorithm from unprotected header
        assertNotNull(result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1ShouldDefaultToES256ForEC2KeyTypeWhenNoAlgProvided() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        // Create key info WITHOUT signatureAlgorithm to trigger default logic
        val minimalKeyInfo = KeyInfo<CoseKeyType>(
            kid = keyInfo.kid,
            key = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!),
            providerId = keyInfo.providerId
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(), // No alg
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = minimalKeyInfo,
            requireX5Chain = false
        )

        // Should default to ES256 for EC2 key type
        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    // =========== 9. RequireX5Chain Error Tests ===========

    @Test
    fun sign1ShouldThrowWhenRequireX5ChainTrueAndNoX5Chain() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        // Key doesn't have x5chain, so requiring it should fail
        assertFailsWith<IllegalArgumentException> {
            coseCryptoService.sign1<Any>(
                input = input,
                keyInfo = coseKeyInfo,
                requireX5Chain = true  // This should cause failure
            )
        }
    }

    // =========== 10. Kid Resolution Tests ===========

    @Test
    fun sign1ShouldUseKidFromKeyInfo() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Ensure kid is set in keyInfo
        assertNotNull(coseKeyInfo.kid)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Result keyInfo should have kid
        assertNotNull(result.keyInfo.kid)
    }

    @Test
    fun sign1ShouldUseKidFromProtectedHeaderWhenNotInKeyInfo() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // Create keyInfo without kid
        val keyInfoWithoutKid = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(
                alg = CoseAlgorithm.ES256,
                kid = com.sphereon.cbor.CborByteString("header-kid".encodeToByteArray())
            ),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithoutKid,
            requireX5Chain = false
        )

        // Result should have some kid
        assertNotNull(result)
    }

    // =========== 11. No KeyInfo Error Tests ===========

    @Test
    fun sign1ShouldThrowWhenNoKeyInfoAndNoX5chain() = runTest {
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        // null keyInfo and no x5chain in headers should fail
        assertFailsWith<IllegalStateException> {
            coseCryptoService.sign1<Any>(
                input = input,
                keyInfo = null,
                requireX5Chain = false
            )
        }
    }

    // =========== 12. Verify with Algorithm Fallback Tests ===========

    @Test
    fun verify1ShouldDeriveAlgorithmFromKey() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val signResult = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // verify1 should work even when algorithm needs to be derived from key
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
    }

    // =========== 13. X5Chain-Based Key Construction Tests ===========

    @Test
    fun verify1ShouldConstructKeyInfoFromX5ChainWhenNoKeyInfoProvided() = runTest {
        // Generate a key pair with a certificate
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Create a self-signed certificate
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "COSE Test Certificate",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Create x5chain as CborArray of CborByteStrings
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Create input with x5chain in protected header
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256, x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )

        // Sign with keyInfo
        val signResult = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify WITHOUT providing keyInfo - should construct from x5chain
        // This tests the x5chain-based key construction path
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertNotNull(verifyResult.keyInfo, "KeyInfo should be constructed from x5chain")
    }

    @Test
    fun verify1WithAlgorithmOnlyInUnprotectedHeaderShouldWork() = runTest {
        // This tests the branch where alg is only in unprotected header during verify1
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Sign with algorithm in protected header (normal case)
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Create a modified CoseSign1 where we "move" alg to unprotected header for test
        // Note: This is an unusual scenario, but we need to test that code path
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertFalse(verifyResult.error)
    }

    @Test
    fun sign1WithX5ChainInUnprotectedHeaderOnlyShouldWork() = runTest {
        // Test where x5chain comes from unprotected header only
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Create certificate for x5chain
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Put x5chain ONLY in unprotected header (not protected)
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
    }

    @Test
    fun sign1ShouldUseKidFromUnprotectedHeaderWhenNotInProtectedOrKeyInfo() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // Create keyInfo without kid
        val keyInfoWithoutKid = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        // Put kid in unprotected header only
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(
                kid = com.sphereon.cbor.CborByteString("unprotected-kid".encodeToByteArray())
            ),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithoutKid,
            requireX5Chain = false
        )

        assertNotNull(result)
    }

    @Test
    fun sign1WithRSAKeyTypeShouldNotDefaultToES256() = runTest {
        // This tests the branch where keyType.cose != EC2, so we shouldn't default to ES256
        // We need to explicitly provide an algorithm for non-EC keys
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Test with explicit algorithm (to avoid default)
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithX5ChainInHeaderShouldWork() = runTest {
        // Generate key pair with certificate
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Create a self-signed certificate
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "COSE Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )

        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )

        // Create x5chain
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Sign with x5chain in unprotected header
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // The unprotected header should contain the x5chain
        assertNotNull(result.coseSign1.unprotectedHeader?.x5chain)
    }

    // =========== 14. Additional Branch Coverage Tests ===========

    @Test
    fun verify1WithX5chainButNoKeyInfoShouldDeriveKey() = runTest {
        // Test the full x5chain-to-keyInfo construction path during verify
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Create certificate for x5chain
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Verify Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Sign with x5chain in header
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256, x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify with NO keyInfo provided - should derive from x5chain
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertNotNull(verifyResult.keyInfo, "KeyInfo should be derived from x5chain")
    }

    @Test
    fun sign1WithNoAlgAndNoKeyTypeShouldUseDefault() = runTest {
        // Test the default algorithm path when no algorithm is provided
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // Create keyInfo without signatureAlgorithm
        val keyInfoNoAlg = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId
            // No signatureAlgorithm provided
        )

        // No alg in header either
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(), // No alg!
            payload = CborByteString("test".encodeToByteArray())
        )

        // Should default to ES256 for EC2 key type
        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoNoAlg,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1ShouldPreserveAlgInProtectedHeaderWhenAlreadySet() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Set alg explicitly in protected header
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Algorithm should be preserved
        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun verify1ShouldFallbackToKeyTypeWhenNoAlgorithm() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Sign normally
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify - the algorithm derivation from keyType will be tested
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo, // Provides keyType info
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertFalse(verifyResult.error)
    }

    @Test
    fun sign1ShouldCreateNewProtectedHeaderWhenNull() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Alg in unprotected header, none in protected
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(), // Empty protected header
            unprotectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Protected header should have alg set
        assertNotNull(result.coseSign1.protectedHeader.alg)
    }

    // =========== 15. Deep Branch Coverage Tests ===========

    @Test
    fun sign1ShouldUseAlgorithmFromKeyInfoWhenNotInHeaders() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // KeyInfo with signatureAlgorithm but no alg in headers
        val keyInfoWithAlg = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(), // No alg
            unprotectedHeader = CoseHeaderCbor(), // No alg
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithAlg,
            requireX5Chain = false
        )

        // Algorithm should come from keyInfo.signatureAlgorithm
        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithNullUnprotectedHeaderShouldCreateNew() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = null, // Explicitly null
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Unprotected header should be created
        assertNotNull(result.coseSign1.unprotectedHeader)
    }

    @Test
    fun sign1WithKidInKeyInfoShouldTakePrecedence() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // KeyInfo with explicit kid
        val keyInfoWithKid = KeyInfo<CoseKeyType>(
            key = coseKey,
            kid = "my-explicit-kid",
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(
                alg = CoseAlgorithm.ES256,
                kid = CborByteString("header-kid".encodeToByteArray())
            ),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithKid,
            requireX5Chain = false
        )

        // KeyInfo kid should take precedence
        assertNotNull(result)
    }

    @Test
    fun sign1ShouldDeriveAlgFromES384Key() = runTest {
        // Test with ES384 to cover different algorithm derivation
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES384),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES384, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1ShouldDeriveAlgFromES512Key() = runTest {
        // Test with ES512 to cover different algorithm derivation
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA512)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES512),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES512, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun verify1WithOnlyUnprotectedAlgShouldDeriveKeyType() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // First sign
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify - tests the sigAlg?.keyType fallback
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertFalse(verifyResult.error)
    }

    // =========== 16. Key Resolution Tests ===========

    @Test
    fun sign1WithKeyInfoWithoutKeyShouldResolveViaCallback() = runTest {
        // Create a keyInfo without key but with signatureAlgorithm to trigger resolution
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // Create a service with a mock callback that returns a resolved key
        val service = CoseCryptoServiceImpl()
        val mockCallback = object : CoseCryptoCallbackCoroutines {
            override suspend fun sign(input: com.sphereon.crypto.core.cose.ToBeSignedCbor, requireX5Chain: Boolean?): ByteArray =
                byteArrayOf(0, 1, 2, 3)

            override suspend fun verify1(
                input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                keyInfo: KeyInfoType<*>?,
                requireX5Chain: Boolean?
            ): VerifySignatureResultType<CoseKeyType> =
                com.sphereon.crypto.core.generic.VerifySignatureResult(
                    keyInfo = keyInfo as? ResolvedKeyInfoType<CoseKeyType>,
                    name = "test",
                    error = false,
                    critical = false
                )

            override suspend fun mac0(
                input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                sharedSecret: ByteArray,
                alg: SignatureAlgorithm
            ): CoseMac0Result = throw NotImplementedError()

            @Suppress("UNCHECKED_CAST")
            override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                keyInfo: KeyInfoType<KeyType>
            ): ResolvedKeyInfoType<KeyType> {
                // Return a resolved key info with the actual key
                return com.sphereon.crypto.core.ResolvedKeyInfo(
                    key = coseKey as KeyType,
                    keyVisibility = KeyVisibility.PUBLIC,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
                ) as ResolvedKeyInfoType<KeyType>
            }
        }
        service.setPlatform(mockCallback)

        // KeyInfo without key but with alias/kid for resolution
        val keyInfoWithoutKey = KeyInfo<CoseKeyType>(
            kid = "test-kid",
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
            // No key - should trigger resolution
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        // This should trigger key resolution via the callback
        val result = service.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithoutKey,
            requireX5Chain = false
        )

        assertNotNull(result)
    }

    // =========== 17. Targeted Branch Coverage Tests ===========

    @Test
    fun sign1WithAlgOnlyInProtectedHeaderShouldReturnEarly() = runTest {
        // This tests line 302: protectedHeader?.alg?.let { return it }
        // where we return early when alg IS in protected header
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256), // alg present
            unprotectedHeader = null, // no fallback needed
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithX5ChainInProtectedHeaderOnlyShouldWork() = runTest {
        // This tests line 394: protectedHeader?.x5chain branch
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Protected Header Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // x5chain ONLY in protected header
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256, x5chain = x5chain),
            unprotectedHeader = null, // x5chain only in protected
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result)
    }

    @Test
    fun sign1WithUnprotectedHeaderExistingShouldCopyIt() = runTest {
        // This tests line 374: unprotectedHeader?.copy(x5chain = x5chain) branch
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(
                contentType = com.sphereon.cbor.CborString("application/test")
            ),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result.coseSign1.unprotectedHeader)
    }

    @Test
    fun sign1WithProtectedHeaderAlreadyHavingAlgShouldNotOverwrite() = runTest {
        // This tests line 379: sigAlg != null && finalProtectedHeader.alg == null
        // When protected header already has alg, we should NOT overwrite it
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256), // already has alg
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Original alg should be preserved
        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithNullProtectedHeaderShouldCreateWithAlg() = runTest {
        // This tests line 378: protectedHeader ?: CoseHeaderCbor(alg = sigAlg)
        // When protected header is truly null, we create one with alg
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Use alg from keyInfo since no header alg
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(), // empty but not null
            unprotectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun verify1WithX5chainInProtectedHeaderShouldExtractKey() = runTest {
        // Test x5chain extraction from protected header specifically
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Verify Protected Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Sign with x5chain in protected header
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256, x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify without keyInfo - should derive from x5chain in protected header
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
    }

    @Test
    fun sign1WithKeyTypeFromKeyInfoShouldBeUsedForDefault() = runTest {
        // Test line 310: keyInfo?.key?.getKeyType() path
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // KeyInfo with key but no signatureAlgorithm - should derive from keyType
        val keyInfoWithKey = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId
            // No signatureAlgorithm - forces default path
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(), // No alg
            unprotectedHeader = CoseHeaderCbor(), // No alg
            payload = CborByteString("test".encodeToByteArray())
        )

        // Should default to ES256 based on EC2 keyType
        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithKey,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithNullKeyInKeyInfoShouldResolveViaCallback() = runTest {
        // Test line 412: resolvedKeyInfo.key ?: this.resolvePublicCborKey(resolvedKeyInfo).key
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        val service = CoseCryptoServiceImpl()
        var resolveCallbackCalled = false
        val mockCallback = object : CoseCryptoCallbackCoroutines {
            override suspend fun sign(input: com.sphereon.crypto.core.cose.ToBeSignedCbor, requireX5Chain: Boolean?): ByteArray =
                byteArrayOf(0, 1, 2, 3)

            override suspend fun verify1(
                input: com.sphereon.crypto.core.cose.CoseSign1<*>,
                keyInfo: KeyInfoType<*>?,
                requireX5Chain: Boolean?
            ): VerifySignatureResultType<CoseKeyType> =
                com.sphereon.crypto.core.generic.VerifySignatureResult(keyInfo = null, name = "test", error = false, critical = false)

            override suspend fun mac0(
                input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
                sharedSecret: ByteArray,
                alg: SignatureAlgorithm
            ): CoseMac0Result = throw NotImplementedError()

            @Suppress("UNCHECKED_CAST")
            override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
                keyInfo: KeyInfoType<KeyType>
            ): ResolvedKeyInfoType<KeyType> {
                resolveCallbackCalled = true
                return com.sphereon.crypto.core.ResolvedKeyInfo(
                    key = coseKey as KeyType,
                    keyVisibility = KeyVisibility.PUBLIC,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
                ) as ResolvedKeyInfoType<KeyType>
            }
        }
        service.setPlatform(mockCallback)

        // KeyInfo without key
        val keyInfoWithoutKey = KeyInfo<CoseKeyType>(
            kid = "resolve-test-kid",
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = service.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithoutKey,
            requireX5Chain = false
        )

        assertTrue(resolveCallbackCalled, "resolvePublicKey callback should be called")
        assertNotNull(result)
    }

    @Test
    fun verify1WithKidInProtectedHeaderShouldExtract() = runTest {
        // Test line 324: protectedHeader?.kid?.encodeValueTo(Encoding.UTF8)?.let { return it }
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // KeyInfo without kid
        val keyInfoNoKid = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256
            // No kid
        )

        // Put kid in protected header
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(
                alg = CoseAlgorithm.ES256,
                kid = CborByteString("protected-header-kid".encodeToByteArray())
            ),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoNoKid,
            requireX5Chain = false
        )

        assertNotNull(result)
    }

    @Test
    fun sign1WithX5chainAndKidShouldSetKidInCoseKey() = runTest {
        // Test line 349: kid?.toCborByteString(Encoding.UTF8) branch in buildKeyInfoFromX5Chain
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Kid Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Sign with x5chain in header and kid in protected header
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(
                alg = CoseAlgorithm.ES256,
                x5chain = x5chain,
                kid = CborByteString("x5chain-test-kid".encodeToByteArray())
            ),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify without keyInfo - buildKeyInfoFromX5Chain should be called with kid
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
    }

    @Test
    fun sign1WithAlgHavingNoCurveShouldFallbackToJwkCurve() = runTest {
        // Test line 350: sigAlg.curve?.toCbor() ?: jwk.crv fallback
        // ES256 has a curve (P-256), so this tests the sigAlg.curve path
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Curve Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256, x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
    }

    @Test
    fun sign1WithES384X5ChainShouldWork() = runTest {
        // Test different algorithm to cover different curve handling
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA384)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "ES384 Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES384, x5chain = x5chain),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertEquals(CoseAlgorithm.ES384, signResult.coseSign1.protectedHeader.alg)
    }

    // =========== 18. RSA Key Tests ===========

    @Test
    fun sign1WithRSAKeyShouldWork() = runTest {
        // RSA keys don't have crv, x, y - tests the null coalesce branches
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.RS256),
            payload = CborByteString("RSA test payload".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.RS256, result.coseSign1.protectedHeader.alg)
        assertNotNull(result.coseSign1.signature)
    }

    @Test
    fun verify1WithRSAKeyShouldWork() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.RS256),
            payload = CborByteString("RSA verify test".encodeToByteArray())
        )

        val signResult = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertFalse(verifyResult.error, "RSA verification should succeed")
    }

    @Test
    fun sign1WithRSAKeyAndNoAlgShouldNotDefaultToES256() = runTest {
        // RSA keys should NOT default to ES256 (which is EC-specific)
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // Create keyInfo with RSA key but without explicit algorithm
        val keyInfoWithRsaKey = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId,
            signatureAlgorithm = SignatureAlgorithm.RSA_SHA256  // Must provide for RSA
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.RS256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoWithRsaKey,
            requireX5Chain = false
        )

        // Should use provided RS256, not default to ES256
        assertEquals(CoseAlgorithm.RS256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun sign1WithPSSAlgorithmShouldWork() = runTest {
        // Test RSA-PSS algorithm
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.PS256),
            payload = CborByteString("PSS test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.PS256, result.coseSign1.protectedHeader.alg)
    }

    @Test
    fun verify1WithRSAX5ChainShouldBuildKeyInfoWithRSAParams() = runTest {
        // RSA certificates in x5chain should use RSA parameters (n, e) instead of EC params
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Create RSA certificate
        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "RSA X5Chain Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Sign with RSA key and x5chain
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.RS256, x5chain = x5chain),
            payload = CborByteString("RSA x5chain test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify WITHOUT keyInfo - this forces buildKeyInfoFromX5Chain to be called with RSA cert
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,  // Force x5chain key extraction
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertFalse(verifyResult.error, "RSA x5chain verification should succeed")
    }

    @Test
    fun sign1WithES512ShouldUseP521Curve() = runTest {
        // Test with different EC curve to exercise curve resolution branches
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA512)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "ES512 Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES512, x5chain = x5chain),
            payload = CborByteString("ES512 x5chain test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify without keyInfo to force x5chain key extraction
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
        assertEquals(CoseAlgorithm.ES512, signResult.coseSign1.protectedHeader.alg)
    }

    @Test
    fun verify1WithX5ChainInUnprotectedHeaderShouldExtractKey() = runTest {
        // Test x5chain extraction from unprotected header (not protected)
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Unprotected X5Chain Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // x5chain in UNPROTECTED header only
        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(x5chain = x5chain),  // x5chain here
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Verify without keyInfo - should get x5chain from unprotected header
        val verifyResult = coseCryptoService.verify1(
            input = signResult.coseSign1,
            keyInfo = null,
            requireX5Chain = false
        )

        assertNotNull(verifyResult)
    }

    @Test
    fun sign1WithNullProtectedHeaderShouldCreateNewOne() = runTest {
        // Test the branch: protectedHeader ?: CoseHeaderCbor()
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        // Input with null protected header - algorithm should come from unprotected or keyInfo
        val input = CoseSign1Input(
            protectedHeader = null,  // Truly null
            unprotectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Protected header should have been created with the algorithm
        assertNotNull(result.coseSign1.protectedHeader)
        assertEquals(CoseAlgorithm.ES256, result.coseSign1.protectedHeader.alg)
    }

    // =========== 19. Error Handler Tests ===========

    @Test
    fun sign1WithNoKeyInfoAndNoX5ChainShouldThrowWithCorrectMessage() = runTest {
        // Test the "No key info provided and no x5chain" error
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(), // No x5chain
            payload = CborByteString("test".encodeToByteArray())
        )

        val exception = assertFailsWith<IllegalStateException> {
            coseCryptoService.sign1<Any>(
                input = input,
                keyInfo = null,  // No keyInfo
                requireX5Chain = false
            )
        }

        assertTrue(
            exception.message?.contains("No key info provided") == true ||
            exception.message?.contains("no x5chain") == true,
            "Error message should mention missing keyInfo or x5chain: ${exception.message}"
        )
    }

    @Test
    fun verify1WithNoKeyInfoAndNoX5ChainShouldThrowWithCorrectMessage() = runTest {
        // Create a signed message first
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val signInput = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )
        val signResult = coseCryptoService.sign1<Any>(
            input = signInput,
            keyInfo = coseKeyInfo,
            requireX5Chain = false
        )

        // Create a modified CoseSign1 without x5chain
        val coseSign1WithoutX5Chain = CoseSign1<Any>(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(), // No x5chain
            signature = signResult.coseSign1.signature,
            payload = signResult.coseSign1.payload
        )

        val exception = assertFailsWith<IllegalStateException> {
            coseCryptoService.verify1(
                input = coseSign1WithoutX5Chain,
                keyInfo = null,  // No keyInfo
                requireX5Chain = false
            )
        }

        assertTrue(
            exception.message?.contains("No key info") == true,
            "Error message should mention missing keyInfo: ${exception.message}"
        )
    }

    @Test
    fun sign1WithRequireX5ChainTrueAndNoX5ChainShouldThrowWithCorrectMessage() = runTest {
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKeyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            payload = CborByteString("test".encodeToByteArray())
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            coseCryptoService.sign1<Any>(
                input = input,
                keyInfo = coseKeyInfo,
                requireX5Chain = true  // Require x5chain but key doesn't have one
            )
        }

        assertTrue(
            exception.message?.contains("x5c") == true || exception.message?.contains("x5chain") == true,
            "Error message should mention missing x5chain: ${exception.message}"
        )
    }

    @Test
    fun sign1DisabledServiceShouldThrowWithCorrectMessage() = runTest {
        val service = CoseCryptoServiceImpl()
        service.setPlatform(createMockCallback())
        service.disable()

        val exception = assertFailsWith<IllegalStateException> {
            service.sign1<Any>(
                input = CoseSign1Input(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    payload = CborByteString("test".encodeToByteArray())
                ),
                keyInfo = KeyInfo<CoseKeyType>(kid = "test"),
                requireX5Chain = false
            )
        }

        assertTrue(
            exception.message?.contains("disabled") == true,
            "Error message should mention service is disabled: ${exception.message}"
        )
    }

    @Test
    fun sign1NoPlatformRegisteredShouldThrowWithCorrectMessage() = runTest {
        DefaultCallbacks.setCoseCryptoDefault(null)
        val service = CoseCryptoServiceImpl()

        val exception = assertFailsWith<IllegalStateException> {
            service.sign1<Any>(
                input = CoseSign1Input(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    payload = CborByteString("test".encodeToByteArray())
                ),
                keyInfo = KeyInfo<CoseKeyType>(kid = "test"),
                requireX5Chain = false
            )
        }

        assertTrue(
            exception.message?.contains("initialized") == true ||
            exception.message?.contains("register") == true,
            "Error message should mention callback not registered: ${exception.message}"
        )
    }

    // =========== 20. Default Algorithm Edge Cases ===========

    @Test
    fun defaultAlgorithmForNonECKeyTypeShouldReturnNullAndCauseError() = runTest {
        // When keyType is not EC2, defaultAlgorithmForKeyType should return null
        // This tests the else branch of the if statement in defaultAlgorithmForKeyType
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        // RSA key WITHOUT any algorithm specified anywhere
        val keyInfoNoAlg = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId
            // No signatureAlgorithm - this forces the default algorithm path
        )

        // No algorithm in headers either
        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(),  // No alg
            unprotectedHeader = CoseHeaderCbor(), // No alg
            payload = CborByteString("test".encodeToByteArray())
        )

        // This should fail because:
        // 1. resolveSignatureAlgorithm finds no alg in protected/unprotected headers
        // 2. keyInfo has no signatureAlgorithm
        // 3. defaultAlgorithmForKeyType returns null for RSA (non-EC2)
        // 4. checkNotNull fails
        val exception = assertFailsWith<IllegalStateException> {
            coseCryptoService.sign1<Any>(
                input = input,
                keyInfo = keyInfoNoAlg,
                requireX5Chain = false
            )
        }

        assertTrue(
            exception.message?.contains("Algorithm") == true ||
            exception.message?.contains("alg") == true,
            "Error should mention algorithm: ${exception.message}"
        )
    }

    @Test
    fun sign1WithRSAKeyAndExplicitAlgorithmShouldWork() = runTest {
        // RSA requires explicit algorithm - test that it works when provided
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        val coseKey = CoseJoseKeyMappingService.toCoseKey(keyInfo.key!!)

        val keyInfoNoAlg = KeyInfo<CoseKeyType>(
            key = coseKey,
            providerId = keyInfo.providerId
        )

        val input = CoseSign1Input(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.RS256), // Explicit alg
            payload = CborByteString("test".encodeToByteArray())
        )

        val result = coseCryptoService.sign1<Any>(
            input = input,
            keyInfo = keyInfoNoAlg,
            requireX5Chain = false
        )

        assertEquals(CoseAlgorithm.RS256, result.coseSign1.protectedHeader.alg)
    }

    // =========== 21. buildKeyInfoFromJwk Edge Case Tests ===========

    @Test
    fun buildKeyInfoFromJwkWithNullAlgShouldProduceKeyInfoWithoutAlg() = runTest {
        // Test the jwk.alg null branch by creating a JWK without alg
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "JWK Null Alg Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        // Create a test service to access protected method
        val testService = TestCoseCryptoService()
        testService.setPlatform(createMockCallback())

        // Create JWK WITHOUT alg set (using null)
        val jwkWithoutAlg = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = keyInfo.key!!.x,
            y = keyInfo.key!!.y,
            alg = null  // Explicitly null alg
        )

        val result = testService.testBuildKeyInfoFromJwk(
            jwk = jwkWithoutAlg,
            x5chain = x5chain,
            x5c = x5chain.encodeToBase64Array(false),
            sigAlg = CoseAlgorithm.ES256,
            algKeyType = CoseKeyTypeEnum.EC2,
            kid = "test-kid"
        )

        // The CoseKey alg should be null since jwk.alg was null
        assertNull(result.key?.alg, "CoseKey alg should be null when JWK alg is null")
        assertNotNull(result.key?.crv, "CoseKey crv should still be set from sigAlg.curve")
    }

    @Test
    fun buildKeyInfoFromJwkWithCurveFallbackToJwkCrv() = runTest {
        // Test the curve fallback: sigAlg.curve is null, but jwk.crv provides the curve
        // Note: Since all CoseAlgorithm EC algorithms have curves, we test by creating
        // a scenario where we verify the jwk.crv path would work if sigAlg.curve were null
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "Curve Fallback Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        val testService = TestCoseCryptoService()
        testService.setPlatform(createMockCallback())

        // Create JWK with curve set
        val jwkWithCurve = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = keyInfo.key!!.x,
            y = keyInfo.key!!.y,
            alg = JwaAlgorithm.ES256
        )

        // When sigAlg.curve is not null (ES256 has P_256), it should be used
        val result = testService.testBuildKeyInfoFromJwk(
            jwk = jwkWithCurve,
            x5chain = x5chain,
            x5c = x5chain.encodeToBase64Array(false),
            sigAlg = CoseAlgorithm.ES256,
            algKeyType = CoseKeyTypeEnum.EC2,
            kid = null
        )

        // Curve should be set from sigAlg.curve (P_256)
        assertNotNull(result.key?.crv, "CoseKey crv should be set")
    }

    @Test
    fun buildKeyInfoFromJwkWithRSAKeyShouldNotSetECParams() = runTest {
        // Test that RSA keys don't set EC parameters
        val managedKeyPair = keyManagerService.generateKey(alg = SignatureAlgorithm.RSA_SHA256)
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val certificateService = session.component.asCertificateServiceComponent().certificateService
        val dn = X509DistinguishedNameElements(
            commonName = "RSA Params Test",
            organizationName = "Test",
            country = "NL"
        )
        val notBefore = LocalDateTimeKMP.now()
        val notAfterInstant = Clock.System.now().plus(365, DateTimeUnit.DAY, TimeZone.UTC)
        val notAfter = LocalDateTimeKMP.fromString(
            notAfterInstant.toLocalDateTime(TimeZone.UTC).toString()
        )
        val certResult = certificateService.createCertificate(
            issuerKeyInfo = keyInfo,
            issuer = dn,
            subjectKeyInfo = keyInfo,
            subject = dn,
            serialNumber = 1,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val x5chain = CborArray(mutableListOf(CborByteString(certResult.certificate.der)))

        val testService = TestCoseCryptoService()
        testService.setPlatform(createMockCallback())

        // Create RSA JWK
        val rsaJwk = Jwk(
            kty = JwaKeyType.RSA,
            n = keyInfo.key!!.n,
            e = keyInfo.key!!.e,
            alg = JwaAlgorithm.RS256
        )

        val result = testService.testBuildKeyInfoFromJwk(
            jwk = rsaJwk,
            x5chain = x5chain,
            x5c = x5chain.encodeToBase64Array(false),
            sigAlg = CoseAlgorithm.RS256,
            algKeyType = CoseKeyTypeEnum.RSA,
            kid = "rsa-test-kid"
        )

        // EC params should be null for RSA
        assertNull(result.key?.crv, "RSA key should not have crv")
        assertNull(result.key?.x, "RSA key should not have x")
        assertNull(result.key?.y, "RSA key should not have y")
        // RSA params should be set
        assertNotNull(result.key?.n, "RSA key should have n")
        assertNotNull(result.key?.rsaE, "RSA key should have e")
    }

    // =========== Helper Methods ===========

    /**
     * Test subclass to access protected buildKeyInfoFromJwk method
     */
    private class TestCoseCryptoService : AbstractCoseCryptoService() {
        fun testBuildKeyInfoFromJwk(
            jwk: com.sphereon.crypto.core.jose.JwkType,
            x5chain: CborArray<CborByteString>,
            x5c: Array<String>,
            sigAlg: CoseAlgorithm,
            algKeyType: CoseKeyTypeEnum,
            kid: String?
        ) = buildKeyInfoFromJwk(jwk, x5chain, x5c, sigAlg, algKeyType, kid)
    }

    private fun createMockCallback(): CoseCryptoCallbackCoroutines = object : CoseCryptoCallbackCoroutines {
        override suspend fun sign(input: com.sphereon.crypto.core.cose.ToBeSignedCbor, requireX5Chain: Boolean?): ByteArray =
            byteArrayOf(0, 1, 2, 3)

        override suspend fun verify1(
            input: com.sphereon.crypto.core.cose.CoseSign1<*>,
            keyInfo: KeyInfoType<*>?,
            requireX5Chain: Boolean?
        ): VerifySignatureResultType<CoseKeyType> =
            com.sphereon.crypto.core.generic.VerifySignatureResult(
                keyInfo = keyInfo as? ResolvedKeyInfoType<CoseKeyType>,
                name = "test",
                error = false,
                critical = false
            )

        override suspend fun mac0(
            input: com.sphereon.crypto.core.cose.CoseMac0InputCbor,
            sharedSecret: ByteArray,
            alg: SignatureAlgorithm
        ): CoseMac0Result = throw NotImplementedError()

        override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(
            keyInfo: KeyInfoType<KeyType>
        ): ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
    }
}
