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

import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Represents the lifecycle state of a prompt.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptState", exact = true)
enum class PromptState {
    /**
     * Prompt is queued but not yet presented to the user.
     */
    PENDING,

    /**
     * Prompt is currently being displayed to the user.
     */
    ACTIVE,

    /**
     * Prompt completed successfully with a response.
     */
    SUCCESS,

    /**
     * Prompt was cancelled by the user or system.
     */
    CANCELLED,

    /**
     * Prompt timed out without a response.
     */
    TIMED_OUT,

    /**
     * Prompt failed due to an error.
     */
    ERROR;

    /**
     * Returns true if the prompt is in a terminal state (cannot change further).
     */
    val isTerminal: Boolean
        get() = when (this) {
            PENDING, ACTIVE -> false
            SUCCESS, CANCELLED, TIMED_OUT, ERROR -> true
        }

    /**
     * Returns true if the prompt is still active (can receive responses).
     */
    val isActive: Boolean
        get() = this == ACTIVE

    /**
     * Returns true if the prompt is waiting to be presented.
     */
    val isPending: Boolean
        get() = this == PENDING
}
