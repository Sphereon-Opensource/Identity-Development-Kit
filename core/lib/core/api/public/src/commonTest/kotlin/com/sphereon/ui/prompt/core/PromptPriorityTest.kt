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

package com.sphereon.ui.prompt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptPriorityTest {
    @Test
    fun criticalHasHighestPriority() {
        assertTrue(PromptPriority.CRITICAL.isHigherThan(PromptPriority.HIGH))
        assertTrue(PromptPriority.CRITICAL.isHigherThan(PromptPriority.NORMAL))
        assertTrue(PromptPriority.CRITICAL.isHigherThan(PromptPriority.LOW))
        assertTrue(PromptPriority.CRITICAL.isHigherThan(PromptPriority.BACKGROUND))
    }

    @Test
    fun backgroundHasLowestPriority() {
        assertFalse(PromptPriority.BACKGROUND.isHigherThan(PromptPriority.LOW))
        assertFalse(PromptPriority.BACKGROUND.isHigherThan(PromptPriority.NORMAL))
        assertFalse(PromptPriority.BACKGROUND.isHigherThan(PromptPriority.HIGH))
        assertFalse(PromptPriority.BACKGROUND.isHigherThan(PromptPriority.CRITICAL))
    }

    @Test
    fun isAtLeastIncludesSamePriority() {
        assertTrue(PromptPriority.NORMAL.isAtLeast(PromptPriority.NORMAL))
        assertTrue(PromptPriority.HIGH.isAtLeast(PromptPriority.NORMAL))
        assertFalse(PromptPriority.LOW.isAtLeast(PromptPriority.NORMAL))
    }

    @Test
    fun sortedByPriorityReturnsCriticalFirst() {
        val sorted = PromptPriority.sortedByPriority()

        assertEquals(PromptPriority.CRITICAL, sorted.first())
        assertEquals(PromptPriority.BACKGROUND, sorted.last())
        assertEquals(5, sorted.size)
    }

    @Test
    fun orderValuesAreCorrect() {
        assertEquals(0, PromptPriority.CRITICAL.order)
        assertEquals(100, PromptPriority.HIGH.order)
        assertEquals(200, PromptPriority.NORMAL.order)
        assertEquals(300, PromptPriority.LOW.order)
        assertEquals(400, PromptPriority.BACKGROUND.order)
    }
}
