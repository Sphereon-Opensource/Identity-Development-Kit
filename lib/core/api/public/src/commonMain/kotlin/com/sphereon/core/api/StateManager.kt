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

import com.sphereon.core.api.error.IdkErrorType
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enhanced state management for consistent StateFlow usage across all platforms.
 *
 * Provides:
 * - Type-safe state transitions
 * - Reactive state observation
 * - Cross-platform state management patterns
 * - Thread-safe state updates
 *
 * ## Platform Benefits:
 *
 * ### Android/Kotlin/JVM:
 * ```kotlin
 * // Seamless ViewModel integration
 * class TransferViewModel : ViewModel() {
 *     val transferState by transferManager.state.collectAsState()
 * }
 *
 * // Compose UI integration
 * @Composable
 * fun TransferScreen(manager: TransferManager) {
 *     val state by manager.state.collectAsState()
 *     when (state) {
 *         is TransferState.Active -> ShowActiveTransfer()
 *         is TransferState.Error -> ShowError(state.error)
 *     }
 * }
 * ```
 *
 * ### iOS (Swift via KMP bridges):
 * ```swift
 * // SwiftUI state binding
 * @StateObject var transferManager: TransferManager
 *
 * var body: some View {
 *     TransferView()
 *         .onReceive(transferManager.state) { state in
 *             handleStateChange(state)
 *         }
 * }
 * ```
 *
 * ### JS/WASM/Native:
 * ```kotlin
 * // Universal reactive state management
 * transferManager.state
 *     .onEach { state -> updateUI(state) }
 *     .launchIn(scope)
 * ```
 */

/**
 * Thread-safe state manager for managing state transitions.
 *
 * @param T The state type
 * @param initialState The initial state value
 */
open class StateManager<T : Any>(initialState: T) {
    private val _state = MutableStateFlow(initialState)

    /**
     * Current state as a StateFlow for reactive observation.
     */
    val state: StateFlow<T> = _state.asStateFlow()

    /**
     * Current state value (synchronous access).
     */
    val currentState: T get() = _state.value

    /**
     * Updates the state to a new value.
     * Thread-safe and will notify all observers.
     *
     * @param newState The new state value
     */
    open fun updateState(newState: T) {
        _state.value = newState
    }

    /**
     * Updates the state based on the current state.
     * Thread-safe atomic operation.
     *
     * @param transform Function to transform current state to new state
     */
    open fun updateState(transform: (T) -> T) {
        _state.value = transform(_state.value)
    }

    /**
     * Conditionally updates the state only if the condition is met.
     *
     * @param condition Predicate based on current state
     * @param newState The new state value if condition is true
     * @return true if state was updated, false otherwise
     */
    fun updateStateIf(condition: (T) -> Boolean, newState: T): Boolean {
        return if (condition(_state.value)) {
            _state.value = newState
            true
        } else {
            false
        }
    }
}

/**
 * Common state types that can be used across different managers.
 */
sealed class CommonState {
    object Idle : CommonState()
    object Initializing : CommonState()
    object Ready : CommonState()
    object Active : CommonState()
    object Completed : CommonState()
    data class Error(val error: IdkErrorType) : CommonState()
    object Closed : CommonState()
}

/**
 * State transition validator to ensure legal state transitions.
 *
 * @param T The state type
 */
abstract class StateTransitionValidator<T : Any> {
    /**
     * Validates if a transition from current state to new state is allowed.
     *
     * @param currentState The current state
     * @param newState The proposed new state
     * @return true if transition is allowed, false otherwise
     */
    abstract fun isTransitionAllowed(currentState: T, newState: T): Boolean

    /**
     * Gets the error message for an invalid transition.
     *
     * @param currentState The current state
     * @param newState The attempted new state
     * @return Error message describing why the transition is not allowed
     */
    open fun getTransitionError(currentState: T, newState: T): String =
        "Invalid state transition from $currentState to $newState"
}

/**
 * Enhanced state manager with transition validation.
 *
 * @param T The state type
 * @param initialState The initial state value
 * @param validator Optional validator for state transitions
 */
class ValidatedStateManager<T : Any>(
    initialState: T,
    private val validator: StateTransitionValidator<T>? = null
) : StateManager<T>(initialState) {

    /**
     * Updates the state with validation.
     *
     * @param newState The new state value
     * @throws IllegalStateException if transition is not allowed
     */
    override fun updateState(newState: T) {
        validator?.let { v ->
            if (!v.isTransitionAllowed(currentState, newState)) {
                throw IllegalStateException(v.getTransitionError(currentState, newState))
            }
        }
        super.updateState(newState)
    }
}

/**
 * Factory functions for creating commonly used state managers.
 */
object StateManagers {
    /**
     * Creates a basic state manager with no validation.
     */
    fun <T : Any> create(initialState: T): StateManager<T> = StateManager(initialState)

    /**
     * Creates a validated state manager with custom transition validation.
     */
    fun <T : Any> createValidated(
        initialState: T,
        validator: StateTransitionValidator<T>
    ): ValidatedStateManager<T> = ValidatedStateManager(initialState, validator)

    /**
     * Creates a state manager for common engagement/transfer states.
     */
    fun createCommonState(initialState: CommonState = CommonState.Idle): StateManager<CommonState> =
        StateManager(initialState)
}