/*
 * (c) 2026 Sphereon International B.V.
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyAuthorizationsTest {
    @Test
    fun testKeyAuthorizationsCreationWithNulls() {
        val keyAuth =
            KeyAuthorizations(
                nameSpaces = null,
                dataElements = null,
            )

        assertNull(keyAuth.nameSpaces)
        assertNull(keyAuth.dataElements)
    }

    @Test
    fun testKeyAuthorizationsEqualityAndHashCode() {
        val left = KeyAuthorizations(null, null)
        val right = KeyAuthorizations(null, null)

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun testKeyAuthorizationsInequalityDifferentType() {
        assertNotEquals<Any>(KeyAuthorizations(null, null), "not a KeyAuthorizations")
    }

    @Test
    fun testKeyAuthorizationsToString() {
        val text = KeyAuthorizations(null, null).toString()

        assertTrue(text.contains("KeyAuthorizations"))
        assertTrue(text.contains("nameSpaces"))
        assertTrue(text.contains("dataElements"))
    }

    @Test
    fun testKeyAuthorizationsCompanionLabels() {
        assertEquals("nameSpaces", KeyAuthorizations.NAME_SPACES.value)
        assertEquals("dataElements", KeyAuthorizations.DATA_ELEMENTS.value)
    }
}
