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

package com.sphereon.ui.prompt.presenter

import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptPresentationHint
import com.sphereon.ui.prompt.core.PromptPriority
import com.sphereon.ui.prompt.core.SinglePromptRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptPresenterTest {

    // Test request implementation
    data class TestRequest(
        override val id: PromptId = PromptId.random(),
        override val title: String = "Test",
        override val subtitle: String? = null,
        override val presentationHint: PromptPresentationHint = PromptPresentationHint.DEFAULT
    ) : SinglePromptRequest

    @Test
    fun initialStateIsUnknown() {
        val presenter = PromptPresenterImpl()

        assertEquals(ForegroundState.UNKNOWN, presenter.foregroundState.value)
    }

    @Test
    fun onForegroundChangesForegroundState() {
        val presenter = PromptPresenterImpl()

        presenter.onForeground()

        assertEquals(ForegroundState.FOREGROUND, presenter.foregroundState.value)
    }

    @Test
    fun onBackgroundChangesBackgroundState() {
        val presenter = PromptPresenterImpl()

        presenter.onBackground()

        assertEquals(ForegroundState.BACKGROUND, presenter.foregroundState.value)
    }

    @Test
    fun canPresentPromptsWhenForeground() {
        val presenter = PromptPresenterImpl()
        presenter.onForeground()

        assertTrue(presenter.canPresentPrompts)
    }

    @Test
    fun canPresentPromptsWhenUnknown() {
        val presenter = PromptPresenterImpl()

        assertTrue(presenter.canPresentPrompts)
    }

    @Test
    fun cannotPresentPromptsWhenBackground() {
        val presenter = PromptPresenterImpl()
        presenter.onBackground()

        assertFalse(presenter.canPresentPrompts)
    }

    @Test
    fun criticalPromptsAreNeverDeferred() {
        val presenter = PromptPresenterImpl()
        presenter.onBackground()

        val request = TestRequest(
            presentationHint = PromptPresentationHint(priority = PromptPriority.CRITICAL)
        )

        assertFalse(presenter.shouldDeferPrompt(request))
    }

    @Test
    fun normalPromptsDeferredWhenBackgroundAndDeferUntilForeground() {
        val presenter = PromptPresenterImpl()
        presenter.onBackground()

        val request = TestRequest(
            presentationHint = PromptPresentationHint(deferUntilForeground = true)
        )

        assertTrue(presenter.shouldDeferPrompt(request))
    }

    @Test
    fun normalPromptsNotDeferredWhenForeground() {
        val presenter = PromptPresenterImpl()
        presenter.onForeground()

        val request = TestRequest(
            presentationHint = PromptPresentationHint(deferUntilForeground = true)
        )

        assertFalse(presenter.shouldDeferPrompt(request))
    }

    @Test
    fun promptsWithoutShowOverLockScreenDeferredInBackground() {
        val presenter = PromptPresenterImpl()
        presenter.onBackground()

        val request = TestRequest(
            presentationHint = PromptPresentationHint(showOverLockScreen = false)
        )

        assertTrue(presenter.shouldDeferPrompt(request))
    }

    @Test
    fun promptsWithShowOverLockScreenNotDeferredInBackground() {
        val presenter = PromptPresenterImpl()
        presenter.onBackground()

        val request = TestRequest(
            presentationHint = PromptPresentationHint(
                priority = PromptPriority.CRITICAL,
                showOverLockScreen = true
            )
        )

        assertFalse(presenter.shouldDeferPrompt(request))
    }

    @Test
    fun normalPromptsNotDeferredWhenUnknownState() {
        val presenter = PromptPresenterImpl()
        // Leave in UNKNOWN state

        val request = TestRequest()

        assertFalse(presenter.shouldDeferPrompt(request))
    }

    @Test
    fun foregroundStateTransitions() {
        val presenter = PromptPresenterImpl()

        assertEquals(ForegroundState.UNKNOWN, presenter.foregroundState.value)

        presenter.onForeground()
        assertEquals(ForegroundState.FOREGROUND, presenter.foregroundState.value)

        presenter.onBackground()
        assertEquals(ForegroundState.BACKGROUND, presenter.foregroundState.value)

        presenter.onForeground()
        assertEquals(ForegroundState.FOREGROUND, presenter.foregroundState.value)
    }
}

class ForegroundStateTest {

    @Test
    fun allStatesExist() {
        val states = ForegroundState.entries
        assertEquals(3, states.size)
        assertTrue(states.contains(ForegroundState.FOREGROUND))
        assertTrue(states.contains(ForegroundState.BACKGROUND))
        assertTrue(states.contains(ForegroundState.UNKNOWN))
    }

    @Test
    fun foregroundStateName() {
        assertEquals("FOREGROUND", ForegroundState.FOREGROUND.name)
    }

    @Test
    fun backgroundStateName() {
        assertEquals("BACKGROUND", ForegroundState.BACKGROUND.name)
    }

    @Test
    fun unknownStateName() {
        assertEquals("UNKNOWN", ForegroundState.UNKNOWN.name)
    }
}
