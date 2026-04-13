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

package com.sphereon.di

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Enum class representing priority levels for ordering items or tasks.
 *
 * Each priority level is associated with an integer value that determines its precedence,
 * with smaller values indicating higher priority. For example, `HIGHEST` has the smallest value
 * and is considered the highest priority, while `LOWEST` has the largest value and is the lowest priority.
 *
 * The reason why the numerical values might be counter intuitive is natural ordering. We want higher order items in collections to come first so we do not have to reverse
 *
 * This class can be used to define and compare priorities in a consistent and expandable manner.
 *
 * @property orderValue The numerical value representing the priority level.
 * @constructor Creates an enum constant with the specified priority value.
 */
enum class Order(val orderValue: Int) {
    HIGHEST(10),
    HIGH(30),
    MEDIUM(50),
    LOW(70),
    LOWEST(90);
    // We allow going below 10 and above 90 mainly for system-related assurances above or below user-defined priorities when ordering is required

    fun isHigherThan(other: Order): Boolean = orderValue > other.orderValue
    fun isLowerThan(other: Order): Boolean = orderValue < other.orderValue
    fun isEqualOrHigherThan(other: Order): Boolean = orderValue >= other.orderValue
    fun isEqualOrLowerThan(other: Order): Boolean = orderValue <= other.orderValue
    fun toValue(): Int = orderValue

    /**
     * Compares this order's priority value to an integer value.
     *
     * Note: Enum classes automatically implement Comparable<Order> for comparing enum constants.
     * This method provides an additional way to compare the order value against integers.
     */
    fun compareToInt(other: Int): Int {
        return orderValue.compareTo(other)
    }
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("HasOrder", exact = true)
interface HasOrder {
    fun getOrder(): Int = Order.MEDIUM.orderValue
}

/**
 * Selects the highest-order (lowest orderValue) implementation from a set of [HasOrder] candidates.
 *
 * This is the standard way to resolve "which implementation wins" when multiple
 * implementations are contributed via multibinding.
 *
 * **Usage:**
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * class MyComponent(
 *     private val allServices: Set<MyService>
 * ) {
 *     // MyService implements HasOrder
 *     private val selectedService: MyService = selectByOrder(allServices)
 * }
 * ```
 *
 * @param T The type of candidates (must implement [HasOrder])
 * @param candidates The set of candidates to select from
 * @return The candidate with the lowest [HasOrder.getOrder] value (highest priority)
 * @throws IllegalArgumentException if [candidates] is empty
 * @throws IllegalStateException if multiple candidates have the same highest order
 */
fun <T : HasOrder> selectByOrder(candidates: Set<T>): T {
    require(candidates.isNotEmpty()) { "Cannot select from empty candidate set" }

    val minOrder = candidates.minOf { it.getOrder() }
    val winners = candidates.filter { it.getOrder() == minOrder }

    check(winners.size == 1) {
        "Ambiguous order selection: ${winners.size} candidates have order $minOrder: " +
                winners.joinToString(", ") { it::class.simpleName ?: it.toString() }
    }

    return winners.single()
}

/**
 * Selects the highest-order (lowest orderValue) implementation from a set, or null if empty.
 *
 * @param T The type of candidates (must implement [HasOrder])
 * @param candidates The set of candidates to select from
 * @return The candidate with the lowest order value, or null if the set is empty
 * @throws IllegalStateException if multiple candidates have the same highest order
 */
fun <T : HasOrder> selectByOrderOrNull(candidates: Set<T>): T? {
    if (candidates.isEmpty()) return null
    return selectByOrder(candidates)
}

/**
 * Extension to sort a collection by ascending order (highest priority first).
 * Since lower orderValue = higher priority, this sorts ascending.
 */
fun <T : HasOrder> Collection<T>.sortedByOrderAscending(): List<T> =
    sortedBy { it.getOrder() }

enum class SortOrder {
    ASC, DESC, NONE
}
