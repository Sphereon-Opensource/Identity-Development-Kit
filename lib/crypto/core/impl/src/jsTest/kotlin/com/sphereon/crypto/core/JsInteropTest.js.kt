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

import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.toCoseKeyType
import com.sphereon.crypto.core.generic.toJoseKeyType
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for JavaScript interoperability with the crypto library.
 *
 * These tests verify that:
 * - Key types and algorithms are accessible from JS
 * - Serialization/deserialization works correctly in the JS environment
 * - Graph initialization works in JS
 */
class JsInteropTest {
    val app = createJsCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("js-interop-test")

    @Test
    fun testJwkCreationAndSerialization(): TestResult =
        runTest {
            // Create a JWK for EC P-256
            val jwk =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                    y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
                    kid = "test-key-js",
                )

            assertNotNull(jwk)
            assertEquals(JwaKeyType.EC, jwk.kty)
            assertEquals(JwaCurve.P_256, jwk.crv)
            assertEquals("test-key-js", jwk.kid)

            // Serialize to JSON
            val json = Json { ignoreUnknownKeys = true }
            val serialized = json.encodeToString(jwk)
            assertNotNull(serialized)
            assertTrue(serialized.contains("\"kty\":\"EC\""))
            assertTrue(serialized.contains("\"crv\":\"P-256\""))
            assertTrue(serialized.contains("\"kid\":\"test-key-js\""))
        }

    @Test
    fun testJwkDeserialization(): TestResult =
        runTest {
            val jwkJson =
                """
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
                    "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
                    "kid": "deserialized-key"
                }
                """.trimIndent()

            val json = Json { ignoreUnknownKeys = true }
            val jwk = json.decodeFromString<Jwk>(jwkJson)

            assertNotNull(jwk)
            assertEquals(JwaKeyType.EC, jwk.kty)
            assertEquals(JwaCurve.P_256, jwk.crv)
            assertEquals("deserialized-key", jwk.kid)
            assertEquals("f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU", jwk.x)
            assertEquals("x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0", jwk.y)
        }

    @Test
    fun testCoseAlgorithmValues(): TestResult =
        runTest {
            // Verify COSE algorithm values are accessible
            assertEquals(-7, CoseAlgorithm.ES256.value)
            assertEquals(-35, CoseAlgorithm.ES384.value)
            assertEquals(-36, CoseAlgorithm.ES512.value)
            assertEquals(-8, CoseAlgorithm.EdDSA.value)
        }

    @Test
    fun testCoseCurveValues(): TestResult =
        runTest {
            // Verify COSE curve values are accessible
            assertEquals(1, CoseCurve.P_256.value)
            assertEquals(2, CoseCurve.P_384.value)
            assertEquals(3, CoseCurve.P_521.value)
            assertEquals(6, CoseCurve.Ed25519.value)
        }

    @Test
    fun testSignatureAlgorithmMapping(): TestResult =
        runTest {
            // Verify signature algorithm mappings work in JS
            val es256 = SignatureAlgorithm.ECDSA_SHA256

            assertNotNull(es256.jose)
            assertEquals(JwaAlgorithm.ES256, es256.jose)
            assertEquals("ES256", es256.jose?.value)

            assertNotNull(es256.cose)
            assertEquals(CoseAlgorithm.ES256, es256.cose)
            assertEquals(-7, es256.cose?.value)
        }

    @Test
    fun testCryptoServicesAccessible(): TestResult =
        runTest {
            // Verify crypto services are accessible from the session graph
            val cryptoServices = (session.graph as CryptoServices.Graph).cryptoServices

            assertNotNull(cryptoServices)
            assertNotNull(cryptoServices.x509)
            assertNotNull(cryptoServices.cose)
        }

    @Test
    fun testKeyInfoSerialization(): TestResult =
        runTest {
            // Create a KeyInfo with a JWK
            val jwk =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "test-x",
                    y = "test-y",
                )

            val keyInfo =
                KeyInfo(
                    key = jwk,
                    kid = "key-info-test",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                )

            assertNotNull(keyInfo)
            assertNotNull(keyInfo.key)
            assertEquals("key-info-test", keyInfo.kid)
            assertEquals(SignatureAlgorithm.ECDSA_SHA256, keyInfo.signatureAlgorithm)
        }

    @Test
    fun testResolvedKeyInfoCreation(): TestResult =
        runTest {
            // Create a ResolvedKeyInfo
            val jwk =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "resolved-x",
                    y = "resolved-y",
                    kid = "resolved-kid",
                )

            val resolvedKeyInfo = ResolvedKeyInfo.fromKey(jwk)

            assertNotNull(resolvedKeyInfo)
            assertNotNull(resolvedKeyInfo.key)
            assertEquals("resolved-kid", resolvedKeyInfo.kid)
        }

    @Test
    fun testCoseJoseMappingService(): TestResult =
        runTest {
            // Test key type mapping via extension functions
            val joseKeyType =
                com.sphereon.crypto.core.cose.CoseKeyTypeEnum.EC2
                    .toJoseKeyType()
            assertEquals(JwaKeyType.EC, joseKeyType)

            val coseKeyType = JwaKeyType.EC.toCoseKeyType()
            assertEquals(com.sphereon.crypto.core.cose.CoseKeyTypeEnum.EC2, coseKeyType)
        }

    @Test
    fun testOkpJwkCreation(): TestResult =
        runTest {
            // Create an OKP (Ed25519) JWK
            val jwk =
                Jwk(
                    kty = JwaKeyType.OKP,
                    crv = JwaCurve.Ed25519,
                    x = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
                )

            assertNotNull(jwk)
            assertEquals(JwaKeyType.OKP, jwk.kty)
            assertEquals(JwaCurve.Ed25519, jwk.crv)

            // Serialize and deserialize
            val json = Json { ignoreUnknownKeys = true }
            val serialized = json.encodeToString(jwk)
            assertTrue(serialized.contains("\"kty\":\"OKP\""))

            val deserialized = json.decodeFromString<Jwk>(serialized)
            assertEquals(jwk.kty, deserialized.kty)
            assertEquals(jwk.crv, deserialized.crv)
            assertEquals(jwk.x, deserialized.x)
        }

    @Test
    fun testRsaJwkSerialization(): TestResult =
        runTest {
            // Create an RSA JWK (minimal public key components)
            val jwkJson =
                """
                {
                    "kty": "RSA",
                    "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
                    "e": "AQAB"
                }
                """.trimIndent()

            val json = Json { ignoreUnknownKeys = true }
            val jwk = json.decodeFromString<Jwk>(jwkJson)

            assertNotNull(jwk)
            assertEquals(JwaKeyType.RSA, jwk.kty)
            assertNotNull(jwk.n)
            assertNotNull(jwk.e)
        }
}
