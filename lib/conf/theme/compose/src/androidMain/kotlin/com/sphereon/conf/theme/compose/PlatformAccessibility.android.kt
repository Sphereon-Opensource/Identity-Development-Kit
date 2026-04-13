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

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android implementation of platform accessibility detection.
 *
 * Reads Android accessibility settings:
 * - `TRANSITION_ANIMATION_SCALE == 0` → [AccessibilityState.prefersReducedMotion]
 * - [AccessibilityManager.isEnabled] + high text contrast → [AccessibilityState.isHighContrast]
 */
@Composable
actual fun rememberPlatformAccessibilityState(): AccessibilityState {
    val context = LocalContext.current
    return remember {
        try {
            val animScale =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1f,
                )
            val reducedMotion = animScale == 0f

            val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val highContrast =
                a11yManager?.isEnabled == true &&
                    Settings.Secure.getInt(
                        context.contentResolver,
                        "high_text_contrast_enabled",
                        0,
                    ) == 1

            AccessibilityState(
                isHighContrast = highContrast,
                prefersReducedMotion = reducedMotion,
            )
        } catch (_: Exception) {
            AccessibilityState()
        }
    }
}
