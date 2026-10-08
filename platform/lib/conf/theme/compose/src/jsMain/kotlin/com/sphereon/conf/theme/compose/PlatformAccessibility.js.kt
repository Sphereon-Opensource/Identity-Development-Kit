/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

/**
 * JS (Browser) implementation of platform accessibility detection.
 *
 * Uses CSS media queries via `window.matchMedia`:
 * - `prefers-reduced-motion: reduce` → [AccessibilityState.prefersReducedMotion]
 * - `prefers-contrast: more` → [AccessibilityState.isHighContrast]
 */
@Composable
actual fun rememberPlatformAccessibilityState(): AccessibilityState =
    remember {
        try {
            val reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
            val highContrast = window.matchMedia("(prefers-contrast: more)").matches
            AccessibilityState(
                isHighContrast = highContrast,
                prefersReducedMotion = reducedMotion,
            )
        } catch (_: Throwable) {
            AccessibilityState()
        }
    }
