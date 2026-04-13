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

package com.sphereon.core.api.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCategoryBasicTest {

    @Test
    fun eventCategoryHasValue() {
        val category = EventCategory("lifecycle")
        assertEquals("lifecycle", category.value)
    }

    @Test
    fun eventCategoryToStringReturnsValue() {
        val category = EventCategory("lifecycle")
        assertEquals("lifecycle", category.toString())
    }

    @Test
    fun eventCategoryEqualsWorks() {
        val category1 = EventCategory("lifecycle")
        val category2 = EventCategory("lifecycle")
        assertEquals(category1, category2)
    }

    @Test
    fun eventCategoryNotEqualsWorks() {
        val category1 = EventCategory("lifecycle")
        val category2 = EventCategory("error")
        assertFalse(category1 == category2)
    }
}

class EventCategoryMatchesExactTest {

    @Test
    fun matchesExactValueReturnsTrue() {
        val category = EventCategory("lifecycle")
        assertTrue(category.matches("lifecycle"))
    }

    @Test
    fun matchesDifferentValueReturnsFalse() {
        val category = EventCategory("lifecycle")
        assertFalse(category.matches("error"))
    }
}

class EventCategoryMatchesWildcardTest {

    @Test
    fun matchesSingleStarReturnsTrue() {
        val category = EventCategory("lifecycle")
        assertTrue(category.matches("*"))
    }

    @Test
    fun matchesDoubleStarReturnsTrue() {
        val category = EventCategory("lifecycle")
        assertTrue(category.matches("**"))
    }

    @Test
    fun matchesPrefixWildcard() {
        val category = EventCategory("lifecycle")
        assertTrue(category.matches("life*"))
    }

    @Test
    fun matchesSuffixWildcard() {
        val category = EventCategory("lifecycle")
        assertTrue(category.matches("*cycle"))
    }

    @Test
    fun matchesMiddleWildcard() {
        val category = EventCategory("lifecycle")
        assertTrue(category.matches("life*cycle"))
    }

    @Test
    fun matchesMultipleWildcards() {
        val category = EventCategory("lifecycle-manager")
        assertTrue(category.matches("*cycle*"))
    }
}

class EventCategoryMatchesPatternTest {

    @Test
    fun matchesPartialPatternReturnsFalse() {
        val category = EventCategory("lifecycle")
        assertFalse(category.matches("life"))
    }

    @Test
    fun matchesLongerPatternReturnsFalse() {
        val category = EventCategory("lifecycle")
        assertFalse(category.matches("lifecycles"))
    }

    @Test
    fun matchesCaseSensitive() {
        val category = EventCategory("lifecycle")
        assertFalse(category.matches("LIFECYCLE"))
    }
}

class EventCategoriesObjectTest {

    @Test
    fun lifecycleHasCorrectValue() {
        assertEquals("lifecycle", EventCategories.LIFECYCLE.value)
    }

    @Test
    fun operationHasCorrectValue() {
        assertEquals("operation", EventCategories.OPERATION.value)
    }

    @Test
    fun errorHasCorrectValue() {
        assertEquals("error", EventCategories.ERROR.value)
    }

    @Test
    fun verboseHasCorrectValue() {
        assertEquals("verbose", EventCategories.VERBOSE.value)
    }

    @Test
    fun securityHasCorrectValue() {
        assertEquals("security", EventCategories.SECURITY.value)
    }
}

class EventCategoryEdgeCasesTest {

    @Test
    fun matchesEmptyPatternReturnsFalse() {
        val category = EventCategory("lifecycle")
        assertFalse(category.matches(""))
    }

    @Test
    fun matchesEmptyCategoryWithStar() {
        val category = EventCategory("")
        assertTrue(category.matches("*"))
    }

    @Test
    fun matchesEmptyCategoryWithDoubleStar() {
        val category = EventCategory("")
        assertTrue(category.matches("**"))
    }

    @Test
    fun matchesCategoryWithSpecialChars() {
        val category = EventCategory("my-category")
        assertTrue(category.matches("my-category"))
    }

    @Test
    fun matchesCategoryWithDots() {
        val category = EventCategory("my.category")
        assertTrue(category.matches("my.category"))
    }

    @Test
    fun matchesCategoryWithUnderscore() {
        val category = EventCategory("my_category")
        assertTrue(category.matches("my_category"))
    }
}
