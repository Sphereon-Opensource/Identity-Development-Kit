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
 * Priority levels for prompt scheduling.
 * Lower order values indicate higher priority (processed first).
 *
 * @property order The numeric order for sorting. Lower values = higher priority.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptPriority", exact = true)
enum class PromptPriority(val order: Int) {
    /**
     * Critical prompts that must be shown immediately.
     * Examples: Security alerts, system errors requiring user action.
     */
    CRITICAL(0),

    /**
     * High priority prompts.
     * Examples: Authentication prompts, biometric unlock.
     */
    HIGH(100),

    /**
     * Normal priority prompts.
     * Examples: Standard user confirmations, NFC prompts.
     */
    NORMAL(200),

    /**
     * Low priority prompts.
     * Examples: Non-urgent notifications, optional confirmations.
     */
    LOW(300),

    /**
     * Background prompts that can be deferred.
     * Examples: Optional feature introductions.
     */
    BACKGROUND(400);

    /**
     * Checks if this priority is higher than another.
     */
    fun isHigherThan(other: PromptPriority): Boolean = this.order < other.order

    /**
     * Checks if this priority is at least as high as another.
     */
    fun isAtLeast(other: PromptPriority): Boolean = this.order <= other.order

    companion object {
        /**
         * Returns priorities sorted from highest to lowest priority.
         */
        fun sortedByPriority(): List<PromptPriority> = entries.sortedBy { it.order }
    }
}
