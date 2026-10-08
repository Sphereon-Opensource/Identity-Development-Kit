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

import androidx.compose.runtime.Immutable
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Cubic bezier easing specification parsed from a token value like "cubic-bezier(0.2, 0.0, 0, 1.0)".
 */
@Immutable
data class EasingSpec(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/**
 * Structured motion tokens mapped from a resolved theme.
 */
@Immutable
data class MotionTokens(
    val durationShort1: Int = DEFAULT_SHORT1,
    val durationShort2: Int = DEFAULT_SHORT2,
    val durationShort3: Int = DEFAULT_SHORT3,
    val durationShort4: Int = DEFAULT_SHORT4,
    val durationMedium1: Int = DEFAULT_MEDIUM1,
    val durationMedium2: Int = DEFAULT_MEDIUM2,
    val durationMedium3: Int = DEFAULT_MEDIUM3,
    val durationMedium4: Int = DEFAULT_MEDIUM4,
    val durationLong1: Int = DEFAULT_LONG1,
    val durationLong2: Int = DEFAULT_LONG2,
    val durationLong3: Int = DEFAULT_LONG3,
    val durationLong4: Int = DEFAULT_LONG4,
    val easingStandard: EasingSpec = EASING_STANDARD,
    val easingStandardDecelerate: EasingSpec = EASING_STANDARD_DECELERATE,
    val easingStandardAccelerate: EasingSpec = EASING_STANDARD_ACCELERATE,
    val easingEmphasized: EasingSpec = EASING_EMPHASIZED,
    val easingEmphasizedDecelerate: EasingSpec = EASING_EMPHASIZED_DECELERATE,
    val easingEmphasizedAccelerate: EasingSpec = EASING_EMPHASIZED_ACCELERATE,
    val easingLinear: EasingSpec = EASING_LINEAR,
    val themeTransitionDuration: Int = DEFAULT_THEME_TRANSITION,
    val themeTransitionEasing: EasingSpec = EASING_THEME_TRANSITION,
) {
    companion object {
        val Default = MotionTokens()

        /** All durations set to 0 for reduced-motion preference. */
        val Zero =
            MotionTokens(
                durationShort1 = 0,
                durationShort2 = 0,
                durationShort3 = 0,
                durationShort4 = 0,
                durationMedium1 = 0,
                durationMedium2 = 0,
                durationMedium3 = 0,
                durationMedium4 = 0,
                durationLong1 = 0,
                durationLong2 = 0,
                durationLong3 = 0,
                durationLong4 = 0,
                themeTransitionDuration = 0,
            )
    }
}

// M3 motion default durations (ms)
private const val DEFAULT_SHORT1 = 50
private const val DEFAULT_SHORT2 = 100
private const val DEFAULT_SHORT3 = 150
private const val DEFAULT_SHORT4 = 200
private const val DEFAULT_MEDIUM1 = 250
private const val DEFAULT_MEDIUM2 = 300
private const val DEFAULT_MEDIUM3 = 350
private const val DEFAULT_MEDIUM4 = 400
private const val DEFAULT_LONG1 = 450
private const val DEFAULT_LONG2 = 500
private const val DEFAULT_LONG3 = 550
private const val DEFAULT_LONG4 = 600
private const val DEFAULT_THEME_TRANSITION = 500

// M3 motion easing presets
private val EASING_STANDARD = EasingSpec(0.2f, 0.0f, 0f, 1.0f)
private val EASING_STANDARD_DECELERATE = EasingSpec(0f, 0f, 0f, 1.0f)
private val EASING_STANDARD_ACCELERATE = EasingSpec(0.3f, 0f, 1f, 1f)
private val EASING_EMPHASIZED = EasingSpec(0.2f, 0.0f, 0f, 1.0f)
private val EASING_EMPHASIZED_DECELERATE = EasingSpec(0.05f, 0.7f, 0.1f, 1.0f)
private val EASING_EMPHASIZED_ACCELERATE = EasingSpec(0.3f, 0.0f, 0.8f, 0.15f)
private val EASING_LINEAR = EasingSpec(0f, 0f, 1f, 1f)
private val EASING_THEME_TRANSITION = EasingSpec(0.2f, 0.0f, 0f, 1.0f)

/**
 * Maps resolved theme tokens to [MotionTokens].
 */
object MotionTokenMapper {
    private const val SECONDS_TO_MS = 1000
    private const val CUBIC_BEZIER_PARAMS = 4
    private const val CUBIC_BEZIER_X2_INDEX = 2
    private const val CUBIC_BEZIER_Y2_INDEX = 3

    fun toMotionTokens(theme: ResolvedTheme): MotionTokens {
        val tokens = theme.tokens
        return MotionTokens(
            durationShort1 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT1]?.parseDurationMs() ?: DEFAULT_SHORT1,
            durationShort2 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT2]?.parseDurationMs() ?: DEFAULT_SHORT2,
            durationShort3 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT3]?.parseDurationMs() ?: DEFAULT_SHORT3,
            durationShort4 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT4]?.parseDurationMs() ?: DEFAULT_SHORT4,
            durationMedium1 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM1]?.parseDurationMs() ?: DEFAULT_MEDIUM1,
            durationMedium2 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM2]?.parseDurationMs() ?: DEFAULT_MEDIUM2,
            durationMedium3 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM3]?.parseDurationMs() ?: DEFAULT_MEDIUM3,
            durationMedium4 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM4]?.parseDurationMs() ?: DEFAULT_MEDIUM4,
            durationLong1 = tokens[TokenKeyConstants.MOTION_DURATION_LONG1]?.parseDurationMs() ?: DEFAULT_LONG1,
            durationLong2 = tokens[TokenKeyConstants.MOTION_DURATION_LONG2]?.parseDurationMs() ?: DEFAULT_LONG2,
            durationLong3 = tokens[TokenKeyConstants.MOTION_DURATION_LONG3]?.parseDurationMs() ?: DEFAULT_LONG3,
            durationLong4 = tokens[TokenKeyConstants.MOTION_DURATION_LONG4]?.parseDurationMs() ?: DEFAULT_LONG4,
            easingStandard =
                tokens[TokenKeyConstants.MOTION_EASING_STANDARD]?.parseEasing()
                    ?: EASING_STANDARD,
            easingStandardDecelerate =
                tokens[TokenKeyConstants.MOTION_EASING_STANDARD_DECELERATE]?.parseEasing()
                    ?: EASING_STANDARD_DECELERATE,
            easingStandardAccelerate =
                tokens[TokenKeyConstants.MOTION_EASING_STANDARD_ACCELERATE]?.parseEasing()
                    ?: EASING_STANDARD_ACCELERATE,
            easingEmphasized =
                tokens[TokenKeyConstants.MOTION_EASING_EMPHASIZED]?.parseEasing()
                    ?: EASING_EMPHASIZED,
            easingEmphasizedDecelerate =
                tokens[TokenKeyConstants.MOTION_EASING_EMPHASIZED_DECELERATE]?.parseEasing()
                    ?: EASING_EMPHASIZED_DECELERATE,
            easingEmphasizedAccelerate =
                tokens[TokenKeyConstants.MOTION_EASING_EMPHASIZED_ACCELERATE]?.parseEasing()
                    ?: EASING_EMPHASIZED_ACCELERATE,
            easingLinear =
                tokens[TokenKeyConstants.MOTION_EASING_LINEAR]?.parseEasing()
                    ?: EASING_LINEAR,
            themeTransitionDuration =
                tokens[TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION]?.parseDurationMs()
                    ?: DEFAULT_THEME_TRANSITION,
            themeTransitionEasing =
                tokens[TokenKeyConstants.MOTION_THEME_TRANSITION_EASING]?.parseEasing()
                    ?: EASING_THEME_TRANSITION,
        )
    }

    /**
     * Parses a duration string like "300ms" to an Int of milliseconds.
     */
    internal fun String.parseDurationMs(): Int? {
        val trimmed = trim()
        val numeric =
            when {
                trimmed.endsWith("ms") -> {
                    trimmed.removeSuffix("ms")
                }

                trimmed.endsWith("s") -> {
                    val seconds = trimmed.removeSuffix("s").toFloatOrNull() ?: return null
                    return (seconds * SECONDS_TO_MS).toInt()
                }

                else -> {
                    trimmed
                }
            }
        return numeric.toIntOrNull()
    }

    /**
     * Parses a cubic-bezier string like "cubic-bezier(0.2, 0.0, 0, 1.0)" to an [EasingSpec].
     */
    internal fun String.parseEasing(): EasingSpec? {
        val trimmed = trim()
        val inner =
            when {
                trimmed.startsWith("cubic-bezier(") && trimmed.endsWith(")") -> {
                    trimmed.removePrefix("cubic-bezier(").removeSuffix(")")
                }

                else -> {
                    return null
                }
            }
        val parts = inner.split(",").map { it.trim() }
        if (parts.size != CUBIC_BEZIER_PARAMS) {
            return null
        }
        val floats = parts.mapNotNull { it.toFloatOrNull() }
        if (floats.size != CUBIC_BEZIER_PARAMS) {
            return null
        }
        return EasingSpec(floats[0], floats[1], floats[CUBIC_BEZIER_X2_INDEX], floats[CUBIC_BEZIER_Y2_INDEX])
    }
}
