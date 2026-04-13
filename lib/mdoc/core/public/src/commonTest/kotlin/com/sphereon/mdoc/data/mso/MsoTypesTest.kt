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

package com.sphereon.mdoc.data.mso

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MsoTypesTest {
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
    fun testDigestAlgorithmCreation() {
        val alg = DigestAlgorithm("SHA-256")
        assertEquals("SHA-256", alg.toString())
    }

    @Test
    fun testDigestAlgorithmRoundTripViaEquality() {
        val original = DigestAlgorithm("SHA-256")
        val decoded = DigestAlgorithm(original.toString())
        assertEquals(original, decoded)
    }

    @Test
    fun testDigestIDCreation() {
        val id = DigestID(0u)
        assertEquals("0", id.toString())
    }

    @Test
    fun testDigestIDCreationMaxValue() {
        val id = DigestID(UInt.MAX_VALUE)
        assertEquals(UInt.MAX_VALUE.toString(), id.toString())
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
