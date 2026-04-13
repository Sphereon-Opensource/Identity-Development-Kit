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

package com.sphereon.crypto.core

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborUInt
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoseJoseKeyMappingServiceTest {
    private val coseKeyCodec = CoseKeyCborCodecImpl()

    private val testJwk =
        Jwk(
            generateKid = false,
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "uxHN3W6ehp0VWXKaMNie1J82MVJCFZYScau74o17cx8",
            y = "29Y5Ey4u5WGWW4MFMKagJPEJiIjzE1UFFZIRhMhqysM",
        )

    private val testJwkWithKid =
        Jwk(
            generateKid = false,
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "uxHN3W6ehp0VWXKaMNie1J82MVJCFZYScau74o17cx8",
            y = "29Y5Ey4u5WGWW4MFMKagJPEJiIjzE1UFFZIRhMhqysM",
            kid = "test-key-id",
        )

    // COSE key bytes from existing test
    private val coseKeyHex =
        "a5010202582b6c663872734d5371454f5138626d664f4c44526873414e5878667a5a4678725f64634c6e52496a787671452001215820bb11cddd6e9e869d1559729a30d89ed49f3631524215961271abbbe28d7b731f225820dbd639132e2ee561965b830530a6a024f1098888f313550515921184c86acac3"

    // =========== tryToJoseJwk / toJoseJwk Tests ===========

    @Test
    fun tryToJoseJwkShouldReturnSameJwkWhenInputIsJwk(): TestResult =
        runTest {
            val result = CoseJoseKeyMappingService.tryToJoseJwk(testJwk)

            assertTrue(result.isOk)
            assertEquals(testJwk, result.value)
        }

    @Test
    fun tryToJoseJwkShouldConvertCoseKeyToJwk(): TestResult =
        runTest {
            val coseKey = coseKeyCodec.decode(coseKeyHex.decodeFrom(Encoding.HEX)).getOrThrow().value
            val result = CoseJoseKeyMappingService.tryToJoseJwk(coseKey)

            assertTrue(result.isOk)
            assertEquals(JwaKeyType.EC, result.value.kty)
        }

    @Test
    fun tryToJoseJwkShouldConvertCoseKeyJsonToJwk(): TestResult =
        runTest {
            val coseKeyJson =
                CoseKeyJson(
                    kty = CoseKeyTypeEnum.EC2,
                    crv = CoseCurve.P_256,
                    x = "uxHN3W6ehp0VWXKaMNie1J82MVJCFZYScau74o17cx8",
                    y = "29Y5Ey4u5WGWW4MFMKagJPEJiIjzE1UFFZIRhMhqysM",
                )
            val result = CoseJoseKeyMappingService.tryToJoseJwk(coseKeyJson)

            assertTrue(result.isOk)
            assertEquals(JwaKeyType.EC, result.value.kty)
        }

    @Test
    fun toJoseJwkShouldReturnJwkWhenInputIsValid(): TestResult =
        runTest {
            val jwk = CoseJoseKeyMappingService.toJoseJwk(testJwk)
            assertEquals(testJwk, jwk)
        }

    // =========== tryToCoseKey / toCoseKey Tests ===========

    @Test
    fun tryToCoseKeyShouldReturnSameCoseKeyWhenInputIsCoseKey(): TestResult =
        runTest {
            val coseKey = coseKeyCodec.decode(coseKeyHex.decodeFrom(Encoding.HEX)).getOrThrow().value
            val result = CoseJoseKeyMappingService.tryToCoseKey(coseKey)

            assertTrue(result.isOk)
            assertEquals(
                coseKey.x?.value?.toList(),
                result.value.x
                    ?.value
                    ?.toList(),
            )
        }

    @Test
    fun tryToCoseKeyShouldConvertJwkToCoseKey(): TestResult =
        runTest {
            val result = CoseJoseKeyMappingService.tryToCoseKey(testJwk)

            assertTrue(result.isOk)
            assertEquals(CborUInt(CoseKeyTypeEnum.EC2.value.toLong()), result.value.kty)
            assertEquals(CborUInt(CoseCurve.P_256.value.toLong()), result.value.crv)
        }

    @Test
    fun tryToCoseKeyShouldConvertCoseKeyJsonToCoseKey(): TestResult =
        runTest {
            val coseKeyJson =
                CoseKeyJson(
                    kty = CoseKeyTypeEnum.EC2,
                    crv = CoseCurve.P_256,
                    x = "uxHN3W6ehp0VWXKaMNie1J82MVJCFZYScau74o17cx8",
                    y = "29Y5Ey4u5WGWW4MFMKagJPEJiIjzE1UFFZIRhMhqysM",
                )
            val result = CoseJoseKeyMappingService.tryToCoseKey(coseKeyJson)

            assertTrue(result.isOk)
            assertEquals(CborUInt(CoseKeyTypeEnum.EC2.value.toLong()), result.value.kty)
        }

    @Test
    fun toCoseKeyShouldReturnCoseKeyWhenInputIsValid(): TestResult =
        runTest {
            val coseKey = CoseJoseKeyMappingService.toCoseKey(testJwk)
            assertEquals(CborUInt(CoseKeyTypeEnum.EC2.value.toLong()), coseKey.kty)
        }

    // =========== X5c Conversion Tests ===========

    @Test
    fun tryToJoseX5cShouldReturnNullForNullInput(): TestResult =
        runTest {
            val result = CoseJoseKeyMappingService.tryToJoseX5c(null)
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun tryToJoseX5cShouldConvertStringArray(): TestResult =
        runTest {
            val x5c = arrayOf<Any>("cert1", "cert2")
            val result = CoseJoseKeyMappingService.tryToJoseX5c(x5c)

            assertTrue(result.isOk)
            assertEquals(2, result.value?.size)
            assertEquals("cert1", result.value?.get(0))
            assertEquals("cert2", result.value?.get(1))
        }

    @Test
    fun tryToJoseX5cShouldConvertCborByteStringArray(): TestResult =
        runTest {
            val certBytes = "test-cert".encodeToByteArray()
            val cborByteString = CborByteString(certBytes)
            val x5c = arrayOf<Any>(cborByteString)
            val result = CoseJoseKeyMappingService.tryToJoseX5c(x5c)

            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals(1, result.value?.size)
        }

    @Test
    fun tryToJoseX5cShouldReturnErrorForUnsupportedType(): TestResult =
        runTest {
            val x5c = arrayOf<Any>(123) // Integer is not supported
            val result = CoseJoseKeyMappingService.tryToJoseX5c(x5c)

            assertTrue(result.isErr)
        }

    @Test
    fun toJoseX5cShouldThrowForUnsupportedType(): TestResult =
        runTest {
            val x5c = arrayOf<Any>(123)
            assertFailsWith<IllegalArgumentException> {
                CoseJoseKeyMappingService.toJoseX5c(x5c)
            }
        }

    // =========== COSE X5chain Conversion Tests ===========

    @Test
    fun tryToCoseX5chainShouldReturnNullForNullInput(): TestResult =
        runTest {
            val result = CoseJoseKeyMappingService.tryToCoseX5chain(null)
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun tryToCoseX5chainShouldConvertCborByteStringArray(): TestResult =
        runTest {
            val certBytes = "test-cert".encodeToByteArray()
            val cborByteString = CborByteString(certBytes)
            val x5c = arrayOf<Any>(cborByteString)
            val result = CoseJoseKeyMappingService.tryToCoseX5chain(x5c)

            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals(1, result.value?.value?.size)
        }

    @Test
    fun tryToCoseX5chainShouldConvertBase64StringArray(): TestResult =
        runTest {
            // Base64 encoded "test"
            val x5c = arrayOf<Any>("dGVzdA==")
            val result = CoseJoseKeyMappingService.tryToCoseX5chain(x5c)

            assertTrue(result.isOk)
            assertNotNull(result.value)
            assertEquals(1, result.value?.value?.size)
        }

    @Test
    fun tryToCoseX5chainShouldReturnErrorForUnsupportedType(): TestResult =
        runTest {
            val x5c = arrayOf<Any>(123)
            val result = CoseJoseKeyMappingService.tryToCoseX5chain(x5c)

            assertTrue(result.isErr)
        }

    @Test
    fun toCoseX5chainShouldThrowForUnsupportedType(): TestResult =
        runTest {
            val x5c = arrayOf<Any>(123)
            assertFailsWith<IllegalArgumentException> {
                CoseJoseKeyMappingService.toCoseX5chain(x5c)
            }
        }

    // =========== KeyInfo Conversion Tests ===========

    @Test
    fun toJwkKeyInfoShouldConvertKeyInfoWithKey(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PUBLIC,
                )

            val result = CoseJoseKeyMappingService.toJwkKeyInfo(keyInfo)

            assertEquals("test-kid", result.kid)
            assertEquals(SignatureAlgorithm.ECDSA_SHA256, result.signatureAlgorithm)
            assertEquals(KeyVisibility.PUBLIC, result.keyVisibility)
            assertNotNull(result.key)
            assertEquals(JwaKeyType.EC, result.key?.kty)
        }

    @Test
    fun toJwkKeyInfoShouldHandleNullKey(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo<Jwk>(
                    key = null,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = CoseJoseKeyMappingService.toJwkKeyInfo(keyInfo)

            assertEquals("test-kid", result.kid)
            assertNull(result.key)
        }

    @Test
    fun toCoseKeyInfoShouldConvertKeyInfoWithKey(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PUBLIC,
                )

            val result = CoseJoseKeyMappingService.toCoseKeyInfo(keyInfo)

            assertEquals("test-kid", result.kid)
            assertEquals(SignatureAlgorithm.ECDSA_SHA256, result.signatureAlgorithm)
            assertNotNull(result.key)
        }

    // =========== ResolvedKeyInfo Conversion Tests ===========

    @Test
    fun toResolvedJwkKeyInfoShouldConvertResolvedKeyInfo(): TestResult =
        runTest {
            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PUBLIC,
                )

            val result = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(resolvedKeyInfo)

            assertEquals("test-kid", result.kid)
            assertEquals(SignatureAlgorithm.ECDSA_SHA256, result.signatureAlgorithm)
            assertEquals(JwaKeyType.EC, result.key.kty)
        }

    @Test
    fun toResolvedCoseKeyInfoShouldConvertResolvedKeyInfo(): TestResult =
        runTest {
            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PUBLIC,
                )

            val result = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(resolvedKeyInfo)

            assertEquals("test-kid", result.kid)
            assertEquals(SignatureAlgorithm.ECDSA_SHA256, result.signatureAlgorithm)
            assertEquals(CborUInt(CoseKeyTypeEnum.EC2.value.toLong()), result.key.kty)
        }

    // =========== isResolvedKeyInfo Tests ===========

    @Test
    fun isResolvedKeyInfoShouldReturnTrueWhenKeyIsPresent(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                )

            assertTrue(CoseJoseKeyMappingService.isResolvedKeyInfo(keyInfo))
        }

    @Test
    fun isResolvedKeyInfoShouldReturnFalseWhenKeyIsNull(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo<Jwk>(
                    key = null,
                    kid = "test-kid",
                )

            assertFalse(CoseJoseKeyMappingService.isResolvedKeyInfo(keyInfo))
        }

    // =========== toResolvedKeyInfo Tests ===========

    @Test
    fun toResolvedKeyInfoShouldCreateResolvedKeyInfoFromKeyInfo(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = CoseJoseKeyMappingService.toResolvedKeyInfo(keyInfo, testJwk)

            assertEquals("test-kid", result.kid)
            assertEquals(SignatureAlgorithm.ECDSA_SHA256, result.signatureAlgorithm)
            assertEquals(testJwk, result.key)
        }

    // =========== tryToResolvedKeyInfoWithResolver Tests ===========

    @Test
    fun tryToResolvedKeyInfoWithResolverShouldReturnKeyInfoIfAlreadyResolved(): TestResult =
        runTest {
            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = testJwk,
                    kid = "test-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result =
                CoseJoseKeyMappingService.tryToResolvedKeyInfoWithResolver(
                    keyInfo = resolvedKeyInfo,
                    resolveCallback = null,
                )

            assertTrue(result.isOk)
            assertEquals("test-kid", result.value.kid)
        }

    @Test
    fun tryToResolvedKeyInfoWithResolverShouldReturnErrorWhenUnresolvedAndNoCallback(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo<Jwk>(
                    key = null,
                    kid = "test-kid",
                )

            val result =
                CoseJoseKeyMappingService.tryToResolvedKeyInfoWithResolver<Jwk>(
                    keyInfo = keyInfo,
                    resolveCallback = null,
                )

            assertTrue(result.isErr)
        }

    @Test
    fun tryToResolvedKeyInfoWithResolverShouldUseCallbackWhenUnresolved(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo<Jwk>(
                    key = null,
                    kid = "test-kid",
                )

            val resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = testJwk,
                    kid = "resolved-kid",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result =
                CoseJoseKeyMappingService.tryToResolvedKeyInfoWithResolver(
                    keyInfo = keyInfo,
                    resolveCallback = { resolvedKeyInfo },
                )

            assertTrue(result.isOk)
            assertEquals("resolved-kid", result.value.kid)
        }

    @Test
    fun toResolvedKeyInfoWithResolverShouldThrowWhenUnresolvedAndNoCallback(): TestResult =
        runTest {
            val keyInfo =
                KeyInfo<Jwk>(
                    key = null,
                    kid = "test-kid",
                )

            assertFailsWith<IllegalArgumentException> {
                CoseJoseKeyMappingService.toResolvedKeyInfoWithResolver<Jwk>(
                    keyInfo = keyInfo,
                    resolveCallback = null,
                )
            }
        }

    // =========== getJoseX5c / getCoseX5chain Tests ===========

    @Test
    fun getJoseX5cShouldReturnNullWhenKeyHasNoX5c(): TestResult =
        runTest {
            val x5c = CoseJoseKeyMappingService.getJoseX5c(testJwk)
            assertNull(x5c)
        }

    @Test
    fun getJoseX5cShouldReturnX5cWhenKeyHasIt(): TestResult =
        runTest {
            // Create a JWK from X509 certificate PEM which has a valid x5c
            val certPem =
                """
-----BEGIN CERTIFICATE-----
MIIB3DCCAYMCFA6bjsh9CB8NbtINaWK8WNgBMx2iMAoGCCqGSM49BAMCMHExCzAJ
BgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0
ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQdGVz
dC5leGFtcGxlLmNvbTAeFw0yNTA1MDEwOTE5NDFaFw0yNjA1MDEwOTE5NDFaMHEx
CzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlB
bXN0ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQ
dGVzdC5leGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABP7W2xjU
4raapzyctjNDkRLGHP7RgAtVqAHRnS5LWz2oXhgKHyhCcwlLrfCOCEIHta+gajwz
2mxZ8j6ix1SNXvkwCgYIKoZIzj0EAwIDRwAwRAIgF9E2jWW+qMnmL3qpB5VvJ/8J
e/K96UVYWQ2T23OA1SYCIAaD8LNo+RgwA0rE7wKKOrogIfQUy+qFPVKjmcDTcHln
-----END CERTIFICATE-----
                """.trimIndent()
            val jwkWithX5c = Jwk.fromX509CertificatePem(certPem)
            assertNotNull(jwkWithX5c)

            val x5c = CoseJoseKeyMappingService.getJoseX5c(jwkWithX5c)

            assertNotNull(x5c)
            assertEquals(1, x5c.size)
        }

    @Test
    fun getCoseX5chainShouldReturnNullWhenKeyHasNoX5chain(): TestResult =
        runTest {
            val x5chain = CoseJoseKeyMappingService.getCoseX5chain(testJwk)
            assertNull(x5chain)
        }

    // =========== Round-trip Tests ===========

    @Test
    fun jwkToCoseKeyAndBackShouldPreserveKeyData(): TestResult =
        runTest {
            val coseKey = CoseJoseKeyMappingService.toCoseKey(testJwk)
            val roundTripped = CoseJoseKeyMappingService.toJoseJwk(coseKey)

            assertEquals(testJwk.kty, roundTripped.kty)
            assertEquals(testJwk.crv, roundTripped.crv)
            assertEquals(testJwk.x, roundTripped.x)
            assertEquals(testJwk.y, roundTripped.y)
        }

    @Test
    fun coseKeyToJwkAndBackShouldPreserveKeyData(): TestResult =
        runTest {
            val coseKey = coseKeyCodec.decode(coseKeyHex.decodeFrom(Encoding.HEX)).getOrThrow().value
            val jwk = CoseJoseKeyMappingService.toJoseJwk(coseKey)
            val roundTripped = CoseJoseKeyMappingService.toCoseKey(jwk)

            assertEquals(coseKey.kty, roundTripped.kty)
            assertEquals(coseKey.crv, roundTripped.crv)
            assertEquals(coseKey.x?.value?.toList(), roundTripped.x?.value?.toList())
            assertEquals(coseKey.y?.value?.toList(), roundTripped.y?.value?.toList())
        }
}
