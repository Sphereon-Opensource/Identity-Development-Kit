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
 */

package com.sphereon.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderEnumTest {
    @Test
    fun enumHasFiveValues() {
        assertEquals(5, Order.entries.size)
    }

    @Test
    fun toValueReturnsOrderValue() {
        assertEquals(10, Order.HIGHEST.toValue())
        assertEquals(30, Order.HIGH.toValue())
        assertEquals(50, Order.MEDIUM.toValue())
        assertEquals(70, Order.LOW.toValue())
        assertEquals(90, Order.LOWEST.toValue())
    }

    @Test
    fun compareToIntReturnsNegativeWhenLess() {
        assertTrue(Order.HIGHEST.compareToInt(50) < 0)
        assertTrue(Order.HIGH.compareToInt(50) < 0)
    }

    @Test
    fun compareToIntReturnsZeroWhenEqual() {
        assertEquals(0, Order.HIGHEST.compareToInt(10))
        assertEquals(0, Order.MEDIUM.compareToInt(50))
    }

    @Test
    fun compareToIntReturnsPositiveWhenGreater() {
        assertTrue(Order.LOWEST.compareToInt(50) > 0)
        assertTrue(Order.LOW.compareToInt(50) > 0)
    }

    @Test
    fun isHigherThanReturnsTrueForHigherOrderValue() {
        assertTrue(Order.LOWEST.isHigherThan(Order.HIGHEST))
        assertTrue(Order.LOW.isHigherThan(Order.HIGH))
    }

    @Test
    fun isHigherThanReturnsFalseForLowerOrderValue() {
        assertFalse(Order.HIGHEST.isHigherThan(Order.LOWEST))
        assertFalse(Order.HIGH.isHigherThan(Order.LOW))
    }

    @Test
    fun isLowerThanReturnsTrueForLowerOrderValue() {
        assertTrue(Order.HIGHEST.isLowerThan(Order.LOWEST))
        assertTrue(Order.HIGH.isLowerThan(Order.LOW))
    }

    @Test
    fun isLowerThanReturnsFalseForHigherOrderValue() {
        assertFalse(Order.LOWEST.isLowerThan(Order.HIGHEST))
        assertFalse(Order.LOW.isLowerThan(Order.HIGH))
    }

    @Test
    fun isEqualOrHigherThanIncludesEquals() {
        assertTrue(Order.MEDIUM.isEqualOrHigherThan(Order.MEDIUM))
        assertTrue(Order.LOW.isEqualOrHigherThan(Order.MEDIUM))
    }

    @Test
    fun isEqualOrLowerThanIncludesEquals() {
        assertTrue(Order.MEDIUM.isEqualOrLowerThan(Order.MEDIUM))
        assertTrue(Order.HIGH.isEqualOrLowerThan(Order.MEDIUM))
    }
}

class SortOrderEnumTest {
    @Test
    fun enumHasThreeValues() {
        assertEquals(3, SortOrder.entries.size)
    }

    @Test
    fun ascExists() {
        assertEquals("ASC", SortOrder.ASC.name)
    }

    @Test
    fun descExists() {
        assertEquals("DESC", SortOrder.DESC.name)
    }

    @Test
    fun noneExists() {
        assertEquals("NONE", SortOrder.NONE.name)
    }
}
