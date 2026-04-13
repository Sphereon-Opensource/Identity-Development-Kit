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

import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaAlgorithm
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
 * Tests for EdDSA (Edwards-curve Digital Signature Algorithm) structures and mappings.
 *
 * Note: Actual cryptographic signing with EdDSA requires platform-specific support.
 * Currently, the SoftwareKmsProvider does not support EdDSA key generation.
 * These tests verify the structural components work correctly.
 *
 * When EdDSA signing support is added:
 * - Add integration tests similar to CoseSigningIntegrationTest for EdDSA
 * - Add JWS integration tests with EdDSA algorithm
 */
class EdDsaStructureTest {

    @Test
    fun testEdDsaSignatureAlgorithmProperties(): TestResult = runTest {
        val edDsa = SignatureAlgorithm.ED25519

        // Verify JOSE properties
        assertNotNull(edDsa.jose)
        assertEquals(JwaAlgorithm.EdDSA, edDsa.jose)
        assertEquals("EdDSA", edDsa.jose?.value)

        // Verify COSE properties
        assertNotNull(edDsa.cose)
        assertEquals(CoseAlgorithm.EdDSA, edDsa.cose)
        assertEquals(-8, edDsa.cose?.value)

        // Verify curve
        assertNotNull(edDsa.curve)
        assertEquals(Curve.Ed25519, edDsa.curve)
    }

    @Test
    fun testEdDsaCurveMapping(): TestResult = runTest {
        val curve = Curve.Ed25519

        // JOSE curve
        assertNotNull(curve.jose)
        assertEquals(JwaCurve.Ed25519, curve.jose)
        assertEquals("Ed25519", curve.jose?.value)

        // COSE curve
        assertNotNull(curve.cose)
        assertEquals(CoseCurve.Ed25519, curve.cose)
        assertEquals(6, curve.cose?.value)
    }

    @Test
    fun testCoseEdDsaAlgorithmProperties(): TestResult = runTest {
        val edDsa = CoseAlgorithm.EdDSA

        assertEquals(-8, edDsa.value)
        assertEquals("EdDSA", edDsa.id)
        assertEquals(CoseKeyTypeEnum.OKP, edDsa.keyType)
        assertNotNull(edDsa.curve)
        assertEquals(CoseCurve.Ed25519, edDsa.curve)
    }

    @Test
    fun testCoseEdDsaCurveProperties(): TestResult = runTest {
        val ed25519 = CoseCurve.Ed25519
        assertEquals(6, ed25519.value)
        assertEquals("Ed25519", ed25519.curveName)

        val ed448 = CoseCurve.Ed448
        assertEquals(7, ed448.value)
        assertEquals("Ed448", ed448.curveName)
    }

    @Test
    fun testJwaEdDsaAlgorithmProperties(): TestResult = runTest {
        val edDsa = JwaAlgorithm.EdDSA

        assertEquals("EdDSA", edDsa.value)
        assertEquals(com.sphereon.crypto.core.jose.AlgorithmType.SIGNATURE, edDsa.type)
        assertEquals(JwaKeyType.OKP, edDsa.keyType)
    }

    @Test
    fun testJwaEdDsaCurveProperties(): TestResult = runTest {
        val ed25519 = JwaCurve.Ed25519
        assertEquals("Ed25519", ed25519.value)
    }

    @Test
    fun testOkpKeyTypeMapping(): TestResult = runTest {
        val okp = KeyTypeMapping.OKP

        // JOSE key type
        assertNotNull(okp.jose)
        assertEquals(JwaKeyType.OKP, okp.jose)
        assertEquals("OKP", okp.jose?.value)

        // COSE key type
        assertNotNull(okp.cose)
        assertEquals(CoseKeyTypeEnum.OKP, okp.cose)
        assertEquals(1, okp.cose?.value)
    }

    @Test
    fun testEdDsaJwkSerialization(): TestResult = runTest {
        // Create an Ed25519 JWK (public key only - no actual crypto operations)
        val jwk = Jwk(
            kty = JwaKeyType.OKP,
            crv = JwaCurve.Ed25519,
            // Example X coordinate (base64url encoded)
            x = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"
        )

        assertNotNull(jwk)
        assertEquals(JwaKeyType.OKP, jwk.kty)
        assertEquals(JwaCurve.Ed25519, jwk.crv)

        // Verify serialization
        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(jwk)
        assertNotNull(serialized)
        assertTrue(serialized.contains("\"kty\":\"OKP\""))
        assertTrue(serialized.contains("\"crv\":\"Ed25519\""))
    }

    @Test
    fun testEdDsaJwkDeserialization(): TestResult = runTest {
        val jwkJson = """
        {
            "kty": "OKP",
            "crv": "Ed25519",
            "x": "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            "kid": "test-ed25519-key"
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val jwk = json.decodeFromString<Jwk>(jwkJson)

        assertNotNull(jwk)
        assertEquals(JwaKeyType.OKP, jwk.kty)
        assertEquals(JwaCurve.Ed25519, jwk.crv)
        assertEquals("test-ed25519-key", jwk.kid)
        assertEquals("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo", jwk.x)
    }

    @Test
    fun testEdDsaJwkWithPrivateKey(): TestResult = runTest {
        // Test JWK with private key component (d)
        val jwkJson = """
        {
            "kty": "OKP",
            "crv": "Ed25519",
            "x": "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            "d": "nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A"
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val jwk = json.decodeFromString<Jwk>(jwkJson)

        assertNotNull(jwk)
        assertEquals(JwaKeyType.OKP, jwk.kty)
        assertEquals(JwaCurve.Ed25519, jwk.crv)
        assertNotNull(jwk.d)
        assertEquals("nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A", jwk.d)
    }

    @Test
    fun testEdDsaSignatureAlgorithmFromCose(): TestResult = runTest {
        // Test conversion from COSE algorithm to generic SignatureAlgorithm
        val coseAlg = CoseAlgorithm.EdDSA
        val sigAlg = SignatureAlgorithm.fromCose(coseAlg)

        assertEquals(SignatureAlgorithm.ED25519, sigAlg)
    }

    @Test
    fun testEdDsaSignatureAlgorithmFromJose(): TestResult = runTest {
        // Test conversion from JOSE algorithm to generic SignatureAlgorithm
        val joseAlg = JwaAlgorithm.EdDSA
        val sigAlg = SignatureAlgorithm.fromJose(joseAlg)

        assertEquals(SignatureAlgorithm.ED25519, sigAlg)
    }

    @Test
    fun testOkpKeyTypeFromCose(): TestResult = runTest {
        val keyType = KeyTypeMapping.fromCose(CoseKeyTypeEnum.OKP)
        assertEquals(KeyTypeMapping.OKP, keyType)
    }

    @Test
    fun testOkpKeyTypeFromJose(): TestResult = runTest {
        val keyType = KeyTypeMapping.fromJose(JwaKeyType.OKP)
        assertEquals(KeyTypeMapping.OKP, keyType)
    }

    @Test
    fun testCoseKeyTypeOkpValue(): TestResult = runTest {
        assertEquals(1, CoseKeyTypeEnum.OKP.value)
        assertEquals("Octet Key Pair", CoseKeyTypeEnum.OKP.explanation)
    }

    @Test
    fun testEdDsaInSignatureAlgorithmAll(): TestResult = runTest {
        // Verify EdDSA is included in the list of all signature algorithms
        val allAlgorithms = SignatureAlgorithm.asList
        assertTrue(allAlgorithms.contains(SignatureAlgorithm.ED25519))
    }

    @Test
    fun testEdDsaCurveInAllCurves(): TestResult = runTest {
        // Verify Ed25519 is included in the list of all curves
        val allCurves = Curve.asList
        assertTrue(allCurves.contains(Curve.Ed25519))
    }

    @Test
    fun testX25519CurveProperties(): TestResult = runTest {
        // X25519 is used for key agreement (ECDH), not signing
        // But it shares the OKP key type with Ed25519
        val x25519 = Curve.X25519

        assertNotNull(x25519.jose)
        assertEquals("X25519", x25519.jose?.value)

        assertNotNull(x25519.cose)
        assertEquals(CoseCurve.X25519, x25519.cose)
    }
}
