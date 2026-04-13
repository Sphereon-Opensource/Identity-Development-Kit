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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DigestAlgorithmTest {
    @Test
    fun testSha256Algorithm() {
        val alg = DigestAlgorithm("SHA-256")
        assertEquals("SHA-256", alg.toString())
    }

    @Test
    fun testSha384Algorithm() {
        val alg = DigestAlgorithm("SHA-384")
        assertEquals("SHA-384", alg.toString())
    }

    @Test
    fun testSha512Algorithm() {
        val alg = DigestAlgorithm("SHA-512")
        assertEquals("SHA-512", alg.toString())
    }

    @Test
    fun testEquality() {
        val alg1 = DigestAlgorithm("SHA-256")
        val alg2 = DigestAlgorithm("SHA-256")
        assertEquals(alg1, alg2)
    }

    @Test
    fun testHashCodeConsistency() {
        val alg1 = DigestAlgorithm("SHA-512")
        val alg2 = DigestAlgorithm("SHA-512")
        assertEquals(alg1.hashCode(), alg2.hashCode())
    }

    @Test
    fun testCustomAlgorithm() {
        val alg = DigestAlgorithm("CUSTOM-ALG")
        assertEquals("CUSTOM-ALG", alg.toString())
    }

    @Test
    fun testJsonSerialization() {
        val alg = DigestAlgorithm("SHA-256")
        val json = Json.encodeToString(DigestAlgorithm.serializer(), alg)
        assertTrue(json.contains("SHA-256"))
    }

    @Test
    fun testJsonDeserialization() {
        val json = "\"SHA-384\""
        val alg = Json.decodeFromString(DigestAlgorithm.serializer(), json)
        assertEquals("SHA-384", alg.toString())
    }

    @Test
    fun testJsonRoundTrip() {
        val original = DigestAlgorithm("SHA-512")
        val json = Json.encodeToString(DigestAlgorithm.serializer(), original)
        val decoded = Json.decodeFromString(DigestAlgorithm.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInequality() {
        val alg1 = DigestAlgorithm("SHA-256")
        val alg2 = DigestAlgorithm("SHA-384")
        assertNotEquals(alg1, alg2)
    }
}
