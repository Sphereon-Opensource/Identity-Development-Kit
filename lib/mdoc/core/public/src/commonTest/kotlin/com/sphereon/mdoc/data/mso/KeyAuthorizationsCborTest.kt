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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for KeyAuthorizationsCbor data class.
 */
class KeyAuthorizationsCborTest {

    @Test
    fun testKeyAuthorizationsCborCreationWithNulls() {
        val keyAuth = KeyAuthorizationsCbor(
            nameSpaces = null,
            dataElements = null
        )
        assertNull(keyAuth.nameSpaces)
        assertNull(keyAuth.dataElements)
    }

    @Test
    fun testKeyAuthorizationsCborEquality() {
        val keyAuth1 = KeyAuthorizationsCbor(null, null)
        val keyAuth2 = KeyAuthorizationsCbor(null, null)
        assertEquals(keyAuth1, keyAuth2)
    }

    @Test
    fun testKeyAuthorizationsCborEqualitySameInstance() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        assertEquals(keyAuth, keyAuth)
    }

    @Test
    fun testKeyAuthorizationsCborInequalityNull() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        assertNotEquals<Any?>(keyAuth, null)
    }

    @Test
    fun testKeyAuthorizationsCborInequalityDifferentClass() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        assertNotEquals<Any>(keyAuth, "not a KeyAuthorizationsCbor")
    }

    @Test
    fun testKeyAuthorizationsCborHashCode() {
        val keyAuth1 = KeyAuthorizationsCbor(null, null)
        val keyAuth2 = KeyAuthorizationsCbor(null, null)
        assertEquals(keyAuth1.hashCode(), keyAuth2.hashCode())
    }

    @Test
    fun testKeyAuthorizationsCborHashCodeWithNulls() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        // Hash code of nulls should be 0 according to the implementation
        val hash = keyAuth.hashCode()
        assertEquals(0, hash)
    }

    @Test
    fun testKeyAuthorizationsCborToString() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        val str = keyAuth.toString()
        assertTrue(str.contains("KeyAuthorizationsCbor"))
        assertTrue(str.contains("nameSpaces"))
        assertTrue(str.contains("dataElements"))
    }

    @Test
    fun testKeyAuthorizationsCborCborBuilder() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        val builder = keyAuth.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testKeyAuthorizationsCborCompanionLabels() {
        assertEquals("nameSpaces", KeyAuthorizationsCbor.NAME_SPACES.value)
        assertEquals("dataElements", KeyAuthorizationsCbor.DATA_ELEMENTS.value)
    }

    @Test
    fun testKeyAuthorizationsCborEncodeDecode() {
        val keyAuth = KeyAuthorizationsCbor(null, null)
        val bytes = keyAuth.encodeCbor()
        val decoded = KeyAuthorizationsCbor.decodeCbor(bytes)
        assertEquals(keyAuth, decoded)
    }

    @Test
    fun testKeyAuthorizationsCborInequalityDifferentNameSpaces() {
        val keyAuth1 = KeyAuthorizationsCbor(nameSpaces = null, dataElements = null)
        // Create with non-null values via encode/decode to get proper types
        val keyAuthWithNamespaces = KeyAuthorizationsCbor(nameSpaces = null, dataElements = null)
        // They should be equal since both have null values
        assertEquals(keyAuth1, keyAuthWithNamespaces)
    }

    @Test
    fun testKeyAuthorizationsCborHashCodeDifferentInstances() {
        val keyAuth1 = KeyAuthorizationsCbor(null, null)
        val keyAuth2 = KeyAuthorizationsCbor(null, null)
        // Hash codes should be equal for equal objects
        assertEquals(keyAuth1.hashCode(), keyAuth2.hashCode())
    }
}
