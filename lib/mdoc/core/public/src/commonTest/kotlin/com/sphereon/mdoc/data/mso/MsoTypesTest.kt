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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Tests for MSO type value classes: MsoVersion, DigestAlgorithm, DigestID.
 */
class MsoTypesTest {

    // MsoVersion tests

    @Test
    fun testMsoVersionCreation() {
        val version = MsoVersion("1.0")
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testMsoVersionInvalidVersionThrows() {
        assertFailsWith<IllegalArgumentException> {
            MsoVersion("2.0")
        }
    }

    @Test
    fun testMsoVersionInvalidVersionThrowsEmpty() {
        assertFailsWith<IllegalArgumentException> {
            MsoVersion("")
        }
    }

    @Test
    fun testMsoVersionToCborStructure() {
        val version = MsoVersion("1.0")
        val cbor = version.toCborStructure()
        assertEquals("1.0", cbor.value)
    }

    @Test
    fun testMsoVersionFromCborStructure() {
        val cborString = CborString("1.0")
        val version = MsoVersion.Decoder.fromCborStructure(cborString)
        assertEquals("1.0", version.toString())
    }

    @Test
    fun testMsoVersionFromCborStructureInvalid() {
        val cborString = CborString("2.0")
        assertFailsWith<IllegalArgumentException> {
            MsoVersion.Decoder.fromCborStructure(cborString)
        }
    }

    @Test
    fun testMsoVersionRoundTrip() {
        val original = MsoVersion("1.0")
        val cbor = original.toCborStructure()
        val decoded = MsoVersion.Decoder.fromCborStructure(cbor)
        assertEquals(original.toString(), decoded.toString())
    }

    // DigestAlgorithm tests

    @Test
    fun testDigestAlgorithmCreation() {
        val alg = DigestAlgorithm("SHA-256")
        assertEquals("SHA-256", alg.toString())
    }

    @Test
    fun testDigestAlgorithmSha384() {
        val alg = DigestAlgorithm("SHA-384")
        assertEquals("SHA-384", alg.toString())
    }

    @Test
    fun testDigestAlgorithmSha512() {
        val alg = DigestAlgorithm("SHA-512")
        assertEquals("SHA-512", alg.toString())
    }

    @Test
    fun testDigestAlgorithmToCborStructure() {
        val alg = DigestAlgorithm("SHA-256")
        val cbor = alg.toCborStructure()
        assertEquals("SHA-256", cbor.value)
    }

    @Test
    fun testDigestAlgorithmFromCborStructure() {
        val cborString = CborString("SHA-256")
        val alg = DigestAlgorithm.Decoder.fromCborStructure(cborString)
        assertEquals("SHA-256", alg.toString())
    }

    @Test
    fun testDigestAlgorithmRoundTrip() {
        val original = DigestAlgorithm("SHA-256")
        val cbor = original.toCborStructure()
        val decoded = DigestAlgorithm.Decoder.fromCborStructure(cbor)
        assertEquals(original.toString(), decoded.toString())
    }

    // DigestID tests

    @Test
    fun testDigestIDCreation() {
        val id = DigestID(0u)
        assertEquals("0", id.toString())
    }

    @Test
    fun testDigestIDCreationNonZero() {
        val id = DigestID(42u)
        assertEquals("42", id.toString())
    }

    @Test
    fun testDigestIDCreationMaxValue() {
        val id = DigestID(UInt.MAX_VALUE)
        assertEquals(UInt.MAX_VALUE.toString(), id.toString())
    }

    @Test
    fun testDigestIDToCborStructure() {
        val id = DigestID(123u)
        val cbor = id.toCborStructure()
        assertEquals(123L, cbor.value)
    }

    @Test
    fun testDigestIDFromCborStructure() {
        val cborUInt = CborUInt(456L)
        val id = DigestID.Decoder.fromCborStructure(cborUInt)
        assertEquals("456", id.toString())
    }

    @Test
    fun testDigestIDRoundTrip() {
        val original = DigestID(789u)
        val cbor = original.toCborStructure()
        val decoded = DigestID.Decoder.fromCborStructure(cbor)
        assertEquals(original.toString(), decoded.toString())
    }

    @Test
    fun testDigestIDEquality() {
        val id1 = DigestID(100u)
        val id2 = DigestID(100u)
        val id3 = DigestID(200u)
        assertEquals(id1, id2)
        assertNotEquals(id1, id3)
    }

    @Test
    fun testDigestIDHashCode() {
        val id1 = DigestID(100u)
        val id2 = DigestID(100u)
        assertEquals(id1.hashCode(), id2.hashCode())
    }
}
