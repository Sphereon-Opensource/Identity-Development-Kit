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

package com.sphereon.ui.prompt.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptStateTest {

    @Test
    fun pendingIsNotTerminal() {
        assertFalse(PromptState.PENDING.isTerminal)
    }

    @Test
    fun activeIsNotTerminal() {
        assertFalse(PromptState.ACTIVE.isTerminal)
    }

    @Test
    fun successIsTerminal() {
        assertTrue(PromptState.SUCCESS.isTerminal)
    }

    @Test
    fun cancelledIsTerminal() {
        assertTrue(PromptState.CANCELLED.isTerminal)
    }

    @Test
    fun timedOutIsTerminal() {
        assertTrue(PromptState.TIMED_OUT.isTerminal)
    }

    @Test
    fun errorIsTerminal() {
        assertTrue(PromptState.ERROR.isTerminal)
    }

    @Test
    fun onlyActiveStateIsActive() {
        assertFalse(PromptState.PENDING.isActive)
        assertTrue(PromptState.ACTIVE.isActive)
        assertFalse(PromptState.SUCCESS.isActive)
        assertFalse(PromptState.CANCELLED.isActive)
        assertFalse(PromptState.TIMED_OUT.isActive)
        assertFalse(PromptState.ERROR.isActive)
    }

    @Test
    fun onlyPendingStateIsPending() {
        assertTrue(PromptState.PENDING.isPending)
        assertFalse(PromptState.ACTIVE.isPending)
        assertFalse(PromptState.SUCCESS.isPending)
        assertFalse(PromptState.CANCELLED.isPending)
        assertFalse(PromptState.TIMED_OUT.isPending)
        assertFalse(PromptState.ERROR.isPending)
    }

    @Test
    fun allStatesExist() {
        val states = PromptState.entries
        assertTrue(states.contains(PromptState.PENDING))
        assertTrue(states.contains(PromptState.ACTIVE))
        assertTrue(states.contains(PromptState.SUCCESS))
        assertTrue(states.contains(PromptState.CANCELLED))
        assertTrue(states.contains(PromptState.TIMED_OUT))
        assertTrue(states.contains(PromptState.ERROR))
    }
}
