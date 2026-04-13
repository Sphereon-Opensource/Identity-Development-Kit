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

package com.sphereon.core.api

import com.sphereon.core.api.error.IdkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateManagerTest {

    @Test
    fun initialStateIsSet() {
        val manager = StateManager("initial")
        assertEquals("initial", manager.currentState)
    }

    @Test
    fun stateFlowReflectsInitialState() {
        val manager = StateManager("initial")
        assertEquals("initial", manager.state.value)
    }

    @Test
    fun updateStateChangesCurrentState() {
        val manager = StateManager("initial")
        manager.updateState("updated")
        assertEquals("updated", manager.currentState)
    }

    @Test
    fun updateStateWithTransformAppliesTransform() {
        val manager = StateManager(10)
        manager.updateState { it * 2 }
        assertEquals(20, manager.currentState)
    }

    @Test
    fun updateStateIfUpdatesWhenConditionIsTrue() {
        val manager = StateManager("initial")
        val updated = manager.updateStateIf({ it == "initial" }, "updated")
        assertTrue(updated)
        assertEquals("updated", manager.currentState)
    }

    @Test
    fun updateStateIfSkipsWhenConditionIsFalse() {
        val manager = StateManager("initial")
        val updated = manager.updateStateIf({ it == "other" }, "updated")
        assertFalse(updated)
        assertEquals("initial", manager.currentState)
    }
}

class CommonStateTest {

    @Test
    fun idleStateExists() {
        val state: CommonState = CommonState.Idle
        assertEquals(CommonState.Idle, state)
    }

    @Test
    fun initializingStateExists() {
        val state: CommonState = CommonState.Initializing
        assertEquals(CommonState.Initializing, state)
    }

    @Test
    fun readyStateExists() {
        val state: CommonState = CommonState.Ready
        assertEquals(CommonState.Ready, state)
    }

    @Test
    fun activeStateExists() {
        val state: CommonState = CommonState.Active
        assertEquals(CommonState.Active, state)
    }

    @Test
    fun completedStateExists() {
        val state: CommonState = CommonState.Completed
        assertEquals(CommonState.Completed, state)
    }

    @Test
    fun closedStateExists() {
        val state: CommonState = CommonState.Closed
        assertEquals(CommonState.Closed, state)
    }

    @Test
    fun errorStateContainsError() {
        val error = IdkError.UNKNOWN_ERROR(message = "test error")
        val state = CommonState.Error(error)
        assertEquals(error, state.error)
    }
}

class StateTransitionValidatorTest {

    private class TestValidator : StateTransitionValidator<String>() {
        override fun isTransitionAllowed(currentState: String, newState: String): Boolean {
            return currentState != "locked"
        }
    }

    @Test
    fun isTransitionAllowedReturnsTrueForValidTransition() {
        val validator = TestValidator()
        assertTrue(validator.isTransitionAllowed("initial", "updated"))
    }

    @Test
    fun isTransitionAllowedReturnsFalseForInvalidTransition() {
        val validator = TestValidator()
        assertFalse(validator.isTransitionAllowed("locked", "updated"))
    }

    @Test
    fun getTransitionErrorReturnsDefaultMessage() {
        val validator = TestValidator()
        val error = validator.getTransitionError("current", "new")
        assertTrue(error.contains("current"))
        assertTrue(error.contains("new"))
    }
}

class ValidatedStateManagerTest {

    private class AllowAllValidator : StateTransitionValidator<String>() {
        override fun isTransitionAllowed(currentState: String, newState: String): Boolean = true
    }

    private class BlockAllValidator : StateTransitionValidator<String>() {
        override fun isTransitionAllowed(currentState: String, newState: String): Boolean = false
        override fun getTransitionError(currentState: String, newState: String): String =
            "Blocked: $currentState -> $newState"
    }

    @Test
    fun updatesStateWhenValidationPasses() {
        val manager = ValidatedStateManager("initial", AllowAllValidator())
        manager.updateState("updated")
        assertEquals("updated", manager.currentState)
    }

    @Test
    fun throwsWhenValidationFails() {
        val manager = ValidatedStateManager("initial", BlockAllValidator())
        val exception = assertFailsWith<IllegalStateException> {
            manager.updateState("updated")
        }
        assertTrue(exception.message!!.contains("Blocked"))
    }

    @Test
    fun worksWithNoValidator() {
        val manager = ValidatedStateManager<String>("initial", null)
        manager.updateState("updated")
        assertEquals("updated", manager.currentState)
    }
}

class StateManagersTest {

    @Test
    fun createReturnsStateManager() {
        val manager = StateManagers.create("initial")
        assertEquals("initial", manager.currentState)
    }

    @Test
    fun createValidatedReturnsValidatedStateManager() {
        val validator = object : StateTransitionValidator<String>() {
            override fun isTransitionAllowed(currentState: String, newState: String): Boolean = true
        }
        val manager = StateManagers.createValidated("initial", validator)
        assertEquals("initial", manager.currentState)
    }

    @Test
    fun createCommonStateDefaultsToIdle() {
        val manager = StateManagers.createCommonState()
        assertEquals(CommonState.Idle, manager.currentState)
    }

    @Test
    fun createCommonStateAcceptsInitialState() {
        val manager = StateManagers.createCommonState(CommonState.Ready)
        assertEquals(CommonState.Ready, manager.currentState)
    }
}
