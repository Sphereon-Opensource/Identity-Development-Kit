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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PromptPresentationHintTest {
    @Test
    fun defaultHintHasNormalPriority() {
        val hint = PromptPresentationHint()
        assertEquals(PromptPriority.NORMAL, hint.priority)
    }

    @Test
    fun defaultHintDoesNotDeferUntilForeground() {
        val hint = PromptPresentationHint()
        assertFalse(hint.deferUntilForeground)
    }

    @Test
    fun defaultHintHasNoTimeout() {
        val hint = PromptPresentationHint()
        assertNull(hint.timeout)
    }

    @Test
    fun defaultHintDoesNotDismissOnBackground() {
        val hint = PromptPresentationHint()
        assertFalse(hint.dismissOnBackground)
    }

    @Test
    fun defaultHintDoesNotShowOverLockScreen() {
        val hint = PromptPresentationHint()
        assertFalse(hint.showOverLockScreen)
    }

    @Test
    fun defaultConstantMatchesDefaultConstructor() {
        assertEquals(PromptPresentationHint(), PromptPresentationHint.DEFAULT)
    }

    @Test
    fun criticalConstantHasCriticalPriority() {
        assertEquals(PromptPriority.CRITICAL, PromptPresentationHint.CRITICAL.priority)
    }

    @Test
    fun criticalConstantShowsOverLockScreen() {
        assertTrue(PromptPresentationHint.CRITICAL.showOverLockScreen)
    }

    @Test
    fun deferredConstantHasLowPriority() {
        assertEquals(PromptPriority.LOW, PromptPresentationHint.DEFERRED.priority)
    }

    @Test
    fun deferredConstantDefersUntilForeground() {
        assertTrue(PromptPresentationHint.DEFERRED.deferUntilForeground)
    }

    @Test
    fun customHintWithTimeout() {
        val hint = PromptPresentationHint(timeout = 30.seconds)
        assertEquals(30.seconds, hint.timeout)
    }

    @Test
    fun customHintWithAllProperties() {
        val hint =
            PromptPresentationHint(
                priority = PromptPriority.HIGH,
                deferUntilForeground = true,
                timeout = 60.seconds,
                dismissOnBackground = true,
                showOverLockScreen = true,
            )

        assertEquals(PromptPriority.HIGH, hint.priority)
        assertTrue(hint.deferUntilForeground)
        assertEquals(60.seconds, hint.timeout)
        assertTrue(hint.dismissOnBackground)
        assertTrue(hint.showOverLockScreen)
    }

    @Test
    fun copyWithModifiedPriority() {
        val original = PromptPresentationHint.DEFAULT
        val modified = original.copy(priority = PromptPriority.HIGH)

        assertEquals(PromptPriority.HIGH, modified.priority)
        assertEquals(original.deferUntilForeground, modified.deferUntilForeground)
    }

    @Test
    fun equalityWorks() {
        val hint1 = PromptPresentationHint(priority = PromptPriority.HIGH)
        val hint2 = PromptPresentationHint(priority = PromptPriority.HIGH)
        val hint3 = PromptPresentationHint(priority = PromptPriority.LOW)

        assertEquals(hint1, hint2)
        assertFalse(hint1 == hint3)
    }
}
