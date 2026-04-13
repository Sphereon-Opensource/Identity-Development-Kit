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
 */

package com.sphereon.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderSelectTest {

    // Test implementations using Order values
    // Remember: SMALLER orderValue = HIGHER priority
    private class HighestPriority : HasOrder {
        override fun getOrder(): Int = Order.HIGHEST.orderValue
        override fun toString() = "HighestPriority"
    }

    private class MediumPriority : HasOrder {
        override fun getOrder(): Int = Order.MEDIUM.orderValue
        override fun toString() = "MediumPriority"
    }

    private class LowestPriority : HasOrder {
        override fun getOrder(): Int = Order.LOWEST.orderValue
        override fun toString() = "LowestPriority"
    }

    private class AnotherHighest : HasOrder {
        override fun getOrder(): Int = Order.HIGHEST.orderValue
        override fun toString() = "AnotherHighest"
    }

    @Test
    fun selectByOrder_returns_candidate_with_lowest_orderValue_highest_priority() {
        val highest = HighestPriority()
        val medium = MediumPriority()
        val lowest = LowestPriority()

        val selected = selectByOrder(setOf(highest, medium, lowest))

        assertEquals(highest, selected, "Should select HighestPriority with lowest orderValue")
    }

    @Test
    fun selectByOrder_returns_HIGHEST_when_MEDIUM_also_present() {
        val highest = HighestPriority()
        val medium = MediumPriority()

        val selected = selectByOrder(setOf(highest, medium))

        assertEquals(highest, selected, "Should select HighestPriority over MediumPriority")
    }

    @Test
    fun selectByOrder_returns_single_candidate_when_only_option() {
        val medium = MediumPriority()

        val selected = selectByOrder(setOf(medium))

        assertEquals(medium, selected, "Should return the only candidate")
    }

    @Test
    fun selectByOrder_throws_on_empty_set() {
        assertFailsWith<IllegalArgumentException> {
            selectByOrder(emptySet<HasOrder>())
        }
    }

    @Test
    fun selectByOrder_throws_on_ambiguous_order() {
        val highest1 = HighestPriority()
        val highest2 = AnotherHighest()

        val exception = assertFailsWith<IllegalStateException> {
            selectByOrder(setOf(highest1, highest2))
        }

        assertTrue(exception.message!!.contains("Ambiguous order selection"))
        assertTrue(exception.message!!.contains("2 candidates"))
    }

    @Test
    fun selectByOrderOrNull_returns_null_for_empty_set() {
        val result = selectByOrderOrNull(emptySet<HasOrder>())

        assertNull(result)
    }

    @Test
    fun selectByOrderOrNull_returns_highest_priority_when_non_empty() {
        val highest = HighestPriority()
        val medium = MediumPriority()

        val result = selectByOrderOrNull(setOf(highest, medium))

        assertEquals(highest, result)
    }

    @Test
    fun sortedByOrderAscending_orders_by_ascending_orderValue_highest_priority_first() {
        val highest = HighestPriority()
        val medium = MediumPriority()
        val lowest = LowestPriority()

        val sorted = listOf(lowest, highest, medium).sortedByOrderAscending()

        assertEquals(listOf(highest, medium, lowest), sorted)
    }

    @Test
    fun Order_enum_values_have_expected_order() {
        assertEquals(10, Order.HIGHEST.orderValue)
        assertEquals(30, Order.HIGH.orderValue)
        assertEquals(50, Order.MEDIUM.orderValue)
        assertEquals(70, Order.LOW.orderValue)
        assertEquals(90, Order.LOWEST.orderValue)
    }

    @Test
    fun Order_comparison_methods_work_correctly() {
        assertTrue(Order.HIGHEST.isLowerThan(Order.LOW))
        assertTrue(Order.LOWEST.isHigherThan(Order.HIGHEST))
        assertTrue(Order.MEDIUM.isEqualOrHigherThan(Order.MEDIUM))
        assertTrue(Order.MEDIUM.isEqualOrLowerThan(Order.MEDIUM))
    }

    @Test
    fun HasOrder_default_returns_MEDIUM_orderValue() {
        val defaultOrder = object : HasOrder {}

        assertEquals(Order.MEDIUM.orderValue, defaultOrder.getOrder())
    }
}
