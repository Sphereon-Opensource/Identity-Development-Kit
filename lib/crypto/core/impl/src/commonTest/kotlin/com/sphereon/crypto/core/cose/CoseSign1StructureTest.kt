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

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.CborByteString
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for COSE Sign1 structure creation and encoding.
 */
class CoseSign1StructureTest {

    @Test
    fun testCoseHeaderCborCreation(): TestResult = runTest {
        val header = CoseHeaderCbor(
            alg = CoseAlgorithm.ES256
        )

        assertNotNull(header)
        assertEquals(CoseAlgorithm.ES256, header.alg)
        assertNull(header.kid)
        assertNull(header.x5chain)
    }

    @Test
    fun testCoseHeaderCborWithKid(): TestResult = runTest {
        val kidBytes = "test-kid".encodeToByteArray()
        val header = CoseHeaderCbor(
            alg = CoseAlgorithm.ES256,
            kid = CborByteString(kidBytes)
        )

        assertNotNull(header)
        assertEquals(CoseAlgorithm.ES256, header.alg)
        assertNotNull(header.kid)
        assertEquals("test-kid", header.kid?.value?.decodeToString())
    }

    @Test
    fun testCoseHeaderCborEncodeDecode(): TestResult = runTest {
        val header = CoseHeaderCbor(
            alg = CoseAlgorithm.ES256
        )

        val encoded = header.encodeCbor()
        assertNotNull(encoded)

        val decoded = CoseHeaderCbor.decodeCbor(encoded)
        assertNotNull(decoded)
        assertEquals(header.alg, decoded.alg)
    }

    @Test
    fun testCoseSign1InputCreation(): TestResult = runTest {
        val payload = CborByteString("Hello World".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        assertNotNull(sign1Input)
        assertEquals(protectedHeader, sign1Input.protectedHeader)
        assertEquals(payload, sign1Input.payload)
        assertNull(sign1Input.unprotectedHeader)
    }

    @Test
    fun testCoseSign1InputBuilderWithHeader(): TestResult = runTest {
        // Test creating a header using the builder pattern
        val header = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val headerWithKid = header.copy(kid = CborByteString("test-key".encodeToByteArray()))

        assertNotNull(headerWithKid)
        assertEquals(CoseAlgorithm.ES256, headerWithKid.alg)
        assertNotNull(headerWithKid.kid)
    }

    @Test
    fun testCoseSign1InputToSignature1Structure(): TestResult = runTest {
        val payload = CborByteString("Test payload".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)

        val sign1Input = CoseSign1Input(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload
        )

        val sigStructure = sign1Input.toSignature1Structure()
        assertNotNull(sigStructure)
        assertEquals(payload, sigStructure.payload)
    }

    @Test
    fun testCoseSign1Creation(): TestResult = runTest {
        val payload = CborByteString("Signed data".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() }) // Mock signature

        val sign1 = CoseSign1<Any>(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload,
            signature = signature
        )

        assertNotNull(sign1)
        assertEquals(protectedHeader, sign1.protectedHeader)
        assertEquals(payload, sign1.payload)
        assertEquals(signature, sign1.signature)
    }

    @Test
    fun testCoseSign1DetachedPayloadCopy(): TestResult = runTest {
        val payload = CborByteString("Original payload".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })

        val sign1 = CoseSign1<Any>(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload,
            signature = signature
        )

        val detached = sign1.detachedPayloadCopy()
        assertNotNull(detached)
        assertNull(detached.payload)
        assertEquals(sign1.protectedHeader, detached.protectedHeader)
        assertEquals(sign1.signature, detached.signature)
    }

    @Test
    fun testCoseSign1EncodeDecode(): TestResult = runTest {
        val payload = CborByteString("Test data".encodeToByteArray())
        val protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256)
        val signature = CborByteString(ByteArray(64) { it.toByte() })

        val sign1 = CoseSign1<Any>(
            protectedHeader = protectedHeader,
            unprotectedHeader = null,
            payload = payload,
            signature = signature
        )

        val encoded = sign1.encodeCbor()
        assertNotNull(encoded)

        val decoded = CoseSign1.decodeCbor<Any>(encoded)
        assertNotNull(decoded)
        assertEquals(sign1.protectedHeader.alg, decoded.protectedHeader.alg)
        assertEquals(sign1.payload?.value?.size, decoded.payload?.value?.size)
    }

    @Test
    fun testCoseAlgorithmValues(): TestResult = runTest {
        // Test that algorithm constants are correct
        assertEquals(-7, CoseAlgorithm.ES256.value)
        assertEquals(-35, CoseAlgorithm.ES384.value)
        assertEquals(-36, CoseAlgorithm.ES512.value)
        assertEquals(-8, CoseAlgorithm.EdDSA.value)
    }

    @Test
    fun testCoseKeyTypeValues(): TestResult = runTest {
        // Test COSE key type values
        assertEquals(1, CoseKeyTypeEnum.OKP.value)
        assertEquals(2, CoseKeyTypeEnum.EC2.value)
        assertEquals(3, CoseKeyTypeEnum.RSA.value)
        assertEquals(4, CoseKeyTypeEnum.Symmetric.value)
    }

    @Test
    fun testCoseKeyTypeFromValue(): TestResult = runTest {
        assertEquals(CoseKeyTypeEnum.OKP, CoseKeyTypeEnum.fromValue(1))
        assertEquals(CoseKeyTypeEnum.EC2, CoseKeyTypeEnum.fromValue(2))
        assertEquals(CoseKeyTypeEnum.RSA, CoseKeyTypeEnum.fromValue(3))
        assertEquals(CoseKeyTypeEnum.Symmetric, CoseKeyTypeEnum.fromValue(4))
    }
}
